# F5 — Façade-Aware Interpretation

## The Problem

After refactoring `psi.agent-session.core` to delegate to focused submodules
(`psi.agent-session.mutations`, `.ui`, `.services`, etc.), gordian still
flags it as a god-module. The namespace has high Ca (many dependents) and
high fan-in, so it lands in the `:shared` role and triggers the finding.

But the namespace is now architecturally *intended* — it's a thin entry
point that delegates inward. Flagging it as a god-module is technically
correct but not actionable. It creates triage cost.

## Do We Need a New Metric?

**Yes.** Current Ca/Ce are project-global — they count all direct
dependents/dependencies equally. But a façade's coupling is structurally
different from a god-module's:

| | God module | Façade |
|---|---|---|
| Ca (total) | High | High |
| Ce (total) | High | Low–moderate |
| *Who* depends on it | Diverse, cross-family | Diverse, cross-family |
| *What* it depends on | Diverse, cross-family | Mostly same-family siblings |

The distinguishing signal is **where the edges point relative to namespace
family boundaries**. Ca and Ce don't capture this.

### Family-scoped coupling metrics

Split Ca and Ce by family membership:

```
Ca-family   = # direct dependents in the same namespace family
Ca-external = # direct dependents outside the family
Ce-family   = # direct dependencies in the same namespace family
Ce-external = # direct dependencies outside the family

Ca = Ca-family + Ca-external   (identity — existing metric)
Ce = Ce-family + Ce-external   (identity)
```

These four numbers tell a richer story:

| Pattern | Ca-ext | Ca-fam | Ce-ext | Ce-fam | Interpretation |
|---------|--------|--------|--------|--------|----------------|
| **Façade** | high | low | low | moderate | Entry point into family |
| **God module** | high | any | high | any | Coupled in all directions |
| **Internal hub** | low | high | low | moderate | Family-internal coordinator |
| **Leaf** | low | low | any | any | Consumer, not depended upon |

A façade is specifically: **high Ca-external, low Ce-external, moderate
Ce-family.** It's the namespace that the outside world talks to, and it
delegates inward.

### Reuse for F3 and F7

These same family-scoped metrics directly enable:

- **F3 (family-noise suppression):** Two namespaces in the same family
  sharing vocabulary is expected. The family membership test needed for F3
  is the same one needed here.

- **F7 (explain-pair verdict):** "These are siblings in the same family"
  vs "these are unrelated namespaces with hidden coupling" — requires
  knowing whether both namespaces are in the same family.

Building family detection as shared infrastructure pays for itself three
times.

## Design

### What Is a Family?

A **namespace family** is a set of project namespaces sharing a common
prefix. The prefix is the organizational unit.

For `psi.agent-session.core`:
- Segments: `[psi, agent-session, core]`
- Possible families: `psi`, `psi.agent-session`
- Useful family: `psi.agent-session` (groups the sibling submodules)

**Algorithm: longest common prefix with ≥ 2 project members.**

For each namespace, consider all its prefixes (drop 1 segment, drop 2, etc).
The **family prefix** for a namespace is the longest prefix shared by at
least one other project namespace. If no prefix is shared, the namespace is
a singleton family.

```
Project namespaces:
  psi.agent-session.core
  psi.agent-session.mutations
  psi.agent-session.ui
  psi.background.runner
  psi.background.scheduler
  psi.graph.core

Family assignments:
  psi.agent-session.core       → psi.agent-session
  psi.agent-session.mutations  → psi.agent-session
  psi.agent-session.ui         → psi.agent-session
  psi.background.runner        → psi.background
  psi.background.scheduler     → psi.background
  psi.graph.core               → psi.graph (singleton — only member)
```

Edge case: `gordian.scan`, `gordian.main`, etc. — all share prefix `gordian`.
That's fine. In a flat project, everyone is in one family, so Ca-family ≈ Ca
and Ca-external ≈ 0. The façade heuristic won't fire because there's no
cross-family pressure. Correct behavior.

Edge case: deeply nested namespaces like `psi.agent-session.mutations.extensions`.
The algorithm finds the longest shared prefix. If both
`psi.agent-session.mutations.extensions` and `psi.agent-session.mutations.base`
exist, they form family `psi.agent-session.mutations`. But
`psi.agent-session.mutations.extensions` also shares prefix `psi.agent-session`
with `psi.agent-session.core`. **Use the longest prefix** — it gives the
tightest family grouping.

Actually, this creates a problem: `psi.agent-session.mutations.extensions`
gets family `psi.agent-session.mutations`, while `psi.agent-session.core`
gets family `psi.agent-session`. They're in *different* families even though
they're clearly related.

**Better approach: hierarchical families.** Each namespace belongs to
*every* prefix it matches. Family membership is then a predicate:
`same-family?(a, b, depth)` — do a and b share a prefix of at least
`depth` segments?

But for metrics, we need a single family assignment per namespace. The
cleanest solution:

**Family = parent prefix (drop last segment).** Simple, predictable,
doesn't require global analysis.

```
psi.agent-session.core              → psi.agent-session
psi.agent-session.mutations         → psi.agent-session
psi.agent-session.mutations.extras  → psi.agent-session.mutations
gordian.scan                        → gordian
gordian.main                        → gordian
```

This means `psi.agent-session.mutations.extras` is in a different
family than `psi.agent-session.core`. That's a valid design — the extra
nesting level implies a sub-unit. If the user wants coarser grouping,
that's F8 (family views with configurable depth).

For façade detection this works well: `psi.agent-session.core` delegates to
`psi.agent-session.mutations` (same family), and `psi.agent-session.core`
is depended on by `psi.background.runner` (different family). That's the
façade signal.

**Decision: family = parent prefix (drop last segment).** Simple, no global
analysis, predictable. Single-segment namespaces (`alpha`, `beta`) have
family `""` (the root family).

### New Module: `family.clj`

Pure. Small. Provides:

```
family-prefix  : sym → string
  "gordian.scan" → "gordian"
  "psi.agent-session.core" → "psi.agent-session"
  "alpha" → ""

same-family?   : sym × sym → bool
  Uses family-prefix equality.

family-metrics : graph → {ns → {:ca-family int :ca-external int
                                 :ce-family int :ce-external int
                                 :family string}}
  Computes family-scoped coupling for all nodes.
```

### Façade Detection

In `diagnose.clj` (or `classify.clj` — see below), add a façade check:

```
facade?  ≡  Ca-external ≥ 2
          ∧ Ce-external ≤ 1
          ∧ Ce-family   ≥ 1
```

Meaning: the outside world depends on this namespace (entry point), it
doesn't reach outside its family (thin), and it does delegate to siblings
(not just a leaf that happens to be imported).

### Where Does Façade Go: Role or Finding?

**Option A: New role `:facade` in classify.clj.**
Replaces `:shared` when façade conditions met. Changes the 2×2 grid to
include a third axis (family boundary).

**Option B: Finding annotation in diagnose.clj.**
Keep role as-is. When a god-module finding would fire, check façade
conditions. If met, either suppress the finding or downgrade severity
and add `:likely-facade true` to evidence.

**Decision: Option B (finding annotation).** Rationale:
- Role classification is a well-defined 2×2 from the literature. Adding
  façade to it conflates two different analyses.
- The user's actual pain is the *finding*, not the role label.
- Façade detection requires family metrics which aren't always computed
  (only when family.clj is wired in).
- Option B is additive — doesn't change existing behavior.

Concretely:
- `find-god-modules` checks façade conditions before emitting a finding
- If façade: emit `:category :facade` at `:low` severity instead of
  `:category :god-module` at `:medium`
- Evidence includes `:ca-family`, `:ca-external`, `:ce-family`, `:ce-external`

### Node Enrichment

Family metrics should be available on nodes (for explain, for output):

```clojure
{:ns gordian.scan
 :reach 0.0  :fan-in 0.05
 :ca 1  :ce 0  :instability 0.0  :role :core
 ;; new:
 :family "gordian"
 :ca-family 1  :ca-external 0
 :ce-family 0  :ce-external 0}
```

Added in `build-report` alongside existing `merge-node-metrics`.

### Output Changes

**Human-readable / markdown:** No change to table columns (too wide
already). Family metrics visible via `explain <ns>`.

**explain output:** Show family and family-scoped metrics in the metrics
section.

**diagnose output:** Façade findings show the family-scoped evidence.

**EDN/JSON:** Family metrics on nodes, façade findings in findings list.

## Implementation Steps

### Step 1: `family.clj` — family-prefix, same-family?, family-metrics

New pure module. ~30 lines. Test: family-prefix for various ns depths,
same-family? pairs, family-metrics on a small graph.

### Step 2: Wire family-metrics into build-report

`main.clj` calls `family/family-metrics` and merges onto nodes. Always
computed (cheap — one pass over graph). Adds `:family`, `:ca-family`,
`:ca-external`, `:ce-family`, `:ce-external` to each node.

### Step 3: Façade detection in diagnose.clj

Modify `find-god-modules` to check façade conditions. When met: emit
`:category :facade` at `:low` severity instead of `:god-module` at
`:medium`. Evidence includes family-scoped metrics.

### Step 4: Surface in explain output

`format-explain-ns` shows family and family-scoped metrics. Minimal
output change.

### Step 5: Schema doc update

Add family metrics to node shape in `doc/schema.md`. Add `:facade`
finding category.

## Step Dependency Graph

```
Step 1 (family.clj) → Step 2 (wire) → Step 3 (diagnose) → Step 5 (docs)
                                    → Step 4 (explain)   ↗
```

## What This Enables for F3 and F7

- **F3:** `same-family?` predicate is the core of family-noise suppression.
  Conceptual pairs where both namespaces are in the same family get tagged
  `:family-noise? true`. The infrastructure from Step 1 is reused directly.

- **F7:** Explain-pair verdict uses family membership + lens data:
  - Same family + conceptual only → "likely naming similarity"
  - Same family + structural → "expected family coupling"
  - Different family + hidden in both lenses → "likely missing abstraction"
  - The `same-family?` function from Step 1 is the classifier input.
