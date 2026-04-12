# 013 — Actionability Ranking

## Problem

Severity is necessary but insufficient.
A medium-severity finding hidden in multiple lenses and spanning namespace
families is often more actionable than a high-severity but expected or
low-leverage issue. Users need Gordian to answer:

> what should I work on first?

Current diagnose output sorts by severity, then score. That preserves danger
signal, but not actionability.

## Goal

Add ranking modes to `diagnose`:

```bash
gordian diagnose . --rank severity
gordian diagnose . --rank actionability
```

and attach an `:actionability-score` to findings.

## Scope

### In scope
- derive actionability score from finding properties
- expose `--rank severity|actionability`
- preserve existing severity ordering as default
- use cluster context when available
- include actionability score in EDN/JSON and human output

### Out of scope
- ML/statistical ranking
- historical stability weighting from compare mode
- per-team custom weight configuration

## Design

### New module
`prioritize.clj` — pure ranking logic.

### Inputs
- finding
- full finding set / cluster context

### Outputs
- finding enriched with `:actionability-score`
- ranked finding vector

## Heuristics

Positive contributions:
- cross-lens hidden coupling: +4
- cross-family hidden pair: +3
- hidden pair (no structural edge): +2
- medium/high severity: +2 / +3
- finding belongs to cluster of size ≥ 2: + cluster bonus
- larger conceptual/change score: + scaled bonus

Negative contributions:
- family naming noise: -4
- façade: -2
- hub: -1
- same-family hidden conceptual only: -1

This produces a relative score, not an absolute guarantee.

## Formula sketch

```clojure
score =
  severity-weight
  + lens-weight
  + family-weight
  + hidden-weight
  + cluster-weight
  + evidence-weight
  - noise-penalties
```

### Suggested weights
- severity: `high=3`, `medium=2`, `low=1`
- `cross-lens-hidden`: +4
- `hidden-change`: +2
- `hidden-conceptual`: +2
- `same-family? false`: +3 (for pair findings)
- hidden pair categories: +2
- cluster bonus: `min(3, cluster-size - 1)`
- evidence `:score`: `+ 2 * score`
- family naming noise (`same-family? && empty independent-terms`): `-4`
- `facade`: `-2`
- `hub`: `-1`

## Cluster context

Phase C introduced finding clusters.
Actionability should leverage them:
- findings in clusters are often part of a broader architectural knot
- singleton findings may be less leverageful

Context shape passed to prioritizer:

```clojure
{:cluster-size-by-finding {finding-key -> n}}
```

## Public API

```clojure
(finding-key finding) -> identity
(cluster-context clusters unclustered) -> context
(actionability-score finding context) -> double
(annotate-actionability findings context) -> findings
(rank-findings findings mode context) -> findings
```

## CLI

New flag:

```text
--rank <severity|actionability>
```

Defaults to `severity` for backward compatibility.

## Output

### EDN/JSON
Each finding gains:

```clojure
{:actionability-score 8.4}
```

Diagnose envelope also includes:

```clojure
:rank-by :severity | :actionability
```

### Text
Flat and clustered diagnose output can show:

```text
● MEDIUM  gordian.aggregate ↔ gordian.close  [act=7.8]
```

### Markdown
Append score in headings or bullet metadata.

## Implementation plan

1. `prioritize.clj` pure scoring + sorting
2. `main.clj` parse `--rank`, wire into diagnose
3. `output.clj` show actionability score
4. tests
5. schema/state/doc updates

## Success criteria

- `gordian diagnose --rank actionability` changes ordering meaningfully
- same input under default mode preserves old ordering behavior
- all finding records in machine output include `:actionability-score`
- full test suite remains green
