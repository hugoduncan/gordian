# Design: Markdown Output (`--markdown`)

## Goal

A shareable, PR-attachable, editor-renderable format between terminal
output (ephemeral) and EDN/JSON (machine). Every gordian command gets a
markdown rendering.

## Target output

### `gordian --markdown` (analyze)

```markdown
# Gordian — Namespace Coupling Report

**Source:** `src/`

## Summary

| Metric | Value |
|--------|-------|
| Propagation cost | 5.5% |
| Namespaces | 17 |
| Cycles | 0 |

## Namespace Metrics

| Namespace | Reach | Fan-in | Ce | Ca | I | Role |
|-----------|-------|--------|----|----|---|------|
| gordian.main | 92.9% | 0.0% | 13 | 0 | 1.00 | peripheral |
| gordian.aggregate | 0.0% | 7.1% | 0 | 1 | 0.00 | core |

## Cycles

(none)

## Conceptual Coupling (score ≥ 0.20)

| Namespace A | Namespace B | Score | Structural | Shared Concepts |
|-------------|-------------|-------|------------|-----------------|
| gordian.aggregate | gordian.close | 0.35 | no ← | reach, transitive, node |

## Change Coupling (Jaccard ≥ 0.30)

| Namespace A | Namespace B | Jaccard | Co | Conf A | Conf B | Structural |
|-------------|-------------|---------|-----|--------|--------|------------|
| gordian.main | gordian.output | 0.4000 | 6 | 42.9% | 85.7% | yes |
```

### `gordian diagnose --markdown`

```markdown
# Gordian Diagnose — 11 Findings

**Source:** `src/`

## Health

| Metric | Value |
|--------|-------|
| Propagation cost | 5.5% (healthy) |
| Cycles | none |
| Namespaces | 17 |

## 🔴 HIGH

### gordian.conceptual ↔ gordian.scan

Hidden coupling in 2 lenses:

- **Conceptual** score=0.29 — shared terms: term, per, extract
- **Change** score=0.33 — 3 co-changes
- → No structural edge

## 🟡 MEDIUM

### gordian.aggregate ↔ gordian.close

Hidden conceptual coupling — score=0.38

- Shared terms: reach, transitive, node
- → No structural edge

## 🟢 LOW

### gordian.main

High-reach hub — 92.9% of project reachable

- Ce=13, I=1.00, role=peripheral

---

**11 findings** (1 high, 2 medium, 8 low)
```

### `gordian explain --markdown`

```markdown
# Gordian Explain — gordian.scan

| Metric | Value |
|--------|-------|
| Role | core |
| Ca | 1 |
| Ce | 0 |
| I | 0.00 |
| Reach | 0.0% |
| Fan-in | 5.9% |

## Direct Dependencies

- **Project:** (none)
- **External:** `babashka.fs`, `edamame.core`

## Direct Dependents

- `gordian.main`

## Conceptual Coupling (3 pairs)

| Namespace | Score | Edge | Shared Terms |
|-----------|-------|------|--------------|
| gordian.conceptual | 0.30 | hidden | term, per, extract |
| gordian.git | 0.18 | hidden | file, clj, src |
| gordian.main | 0.16 | structural | file, scan, read |

## Change Coupling (0 pairs)

(none)

## Cycles

none
```

### `gordian explain-pair --markdown`

```markdown
# Gordian Explain-Pair — gordian.aggregate ↔ gordian.close

## Structural

| Property | Value |
|----------|-------|
| Direct edge | no |
| Shortest path | (none) |

## Conceptual

| Property | Value |
|----------|-------|
| Score | 0.37 |
| Shared terms | reach, transitive, node |
| Hidden | yes |

## Change Coupling

(no data)

## Diagnosis

🟡 **MEDIUM** — hidden conceptual coupling — score=0.37
```

## Design decisions

### 1. `--markdown` is a third output mode

`--markdown` is mutually exclusive with `--json` and `--edn`. All three
suppress the default human-readable terminal output.

Mutual exclusion: at most one of `--json`, `--edn`, `--markdown` may be
specified. Error otherwise.

### 2. Four `format-*-md` functions, same data

Each command gets a markdown formatter that takes the same data as the
terminal formatter. No intermediate representation, no refactor of the
existing text formatters.

```
format-report-md       [report]                → [string]
format-diagnose-md     [report health findings] → [string]
format-explain-ns-md   [data]                  → [string]
format-explain-pair-md [data]                  → [string]
```

### 3. Markdown tables for structured data

Namespace metrics, coupling pairs, explain properties — all as markdown
tables. GitHub, GitLab, and most editors render these natively.

### 4. Severity emoji in diagnose

`🔴 HIGH`, `🟡 MEDIUM`, `🟢 LOW` — visually scannable in rendered
markdown. Each finding is an `###` heading under its severity `##` group.

### 5. Backtick namespaces in explain

Namespace names in explain output use `backticks` for readability in
rendered markdown. Table cells don't need backticks (they're already
in a code-like context).

### 6. Sections omitted when empty

Same rule as terminal output: conceptual section omitted if no pairs,
change section omitted if no pairs, cycles omitted if none. Keeps
markdown clean.

## Architecture

All changes are in `output.clj` (new functions) and `main.clj`
(new flag + routing). No new modules.

## Files changed

| File | Change |
|------|--------|
| modify `src/gordian/output.clj` | Four `format-*-md` functions |
| modify `test/gordian/output_test.clj` | Tests for each md formatter |
| modify `src/gordian/main.clj` | `--markdown` flag, mutual exclusion, routing |
| modify `test/gordian/main_test.clj` | Parse + integration tests |

## Step-wise implementation plan

### Step 1: `output.clj` — `format-report-md`

Markdown rendering of the analyze report: summary table, namespace
metrics table, optional cycles, conceptual, change sections.

Tests:
- Header contains `# Gordian`
- Summary table contains propagation cost
- Namespace table has correct column count
- All fixture namespace names appear
- Conceptual section present when pairs provided
- Conceptual section absent when no pairs
- Change section present when pairs provided
- Cycles section shows members when cycles present
- Cycles section shows "(none)" when no cycles

### Step 2: `output.clj` — `format-diagnose-md`

Markdown rendering of findings: health table, findings grouped by
severity with emoji markers.

Tests:
- Header contains finding count
- Health table shows PC and cycle count
- HIGH finding has `🔴` marker
- MEDIUM finding has `🟡` marker
- LOW finding has `🟢` marker
- Cross-lens finding shows both scores
- Summary line at bottom shows counts
- Empty findings → health only + "0 findings"

### Step 3: `output.clj` — `format-explain-ns-md`, `format-explain-pair-md`

Markdown rendering of explain output.

Tests for `format-explain-ns-md`:
- Header contains namespace name
- Metrics table has role, Ca, Ce, I
- Dependencies listed with backtick formatting
- Conceptual pairs as table with score and terms
- Empty sections show "(none)"
- Error data → error message

Tests for `format-explain-pair-md`:
- Header contains both namespace names
- Structural table shows edge status
- Conceptual section shows score and terms
- Diagnosis section shows severity emoji
- Missing data → "(no data)"

### Step 4: `main.clj` — `--markdown` flag + routing

Add `--markdown` to cli-spec. Mutual exclusion with json/edn.
Each command function gets a `markdown` branch.

Tests:
- `--markdown` flag parsed correctly
- `--markdown` + `--json` → error
- `--markdown` + `--edn` → error
- `analyze --markdown` produces markdown
- `diagnose --markdown` produces markdown
- `explain --markdown` produces markdown
- `explain-pair --markdown` produces markdown
- Help text mentions `--markdown`

### Step 5: Docs + PLAN
