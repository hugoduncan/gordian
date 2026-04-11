# F7 — Explain-Pair Verdict

## The Problem

`gordian explain-pair` shows raw facts: structural edge, conceptual score,
change coupling, and optionally a diagnosis finding. The user has to
mentally combine these into an interpretation. The feedback says:

> I usually want not just raw facts but a summary like:
> "missing abstraction" / "naming noise" / "expected" / "vestigial"

## Available Signals

At the point where `explain-pair-data` builds its result, we have:

| Signal | Source | Key |
|--------|--------|-----|
| Direct structural edge | graph | `:structural :direct-edge?` |
| Transitive path | BFS | `:structural :shortest-path` |
| Conceptual score | TF-IDF | `:conceptual :score` |
| Same family | family annotation | `:conceptual :same-family?` |
| Independent terms | family annotation | `:conceptual :independent-terms` |
| Family terms | family annotation | `:conceptual :family-terms` |
| Change coupling | git co-change | `:change :score` |
| Finding | diagnose | `:finding` |

## Verdict Rules

The verdict is a keyword + short explanation. Rules are evaluated top-down;
first match wins.

### 1. `:expected-structural` — Direct structural dependency

```
structural edge exists
```

These namespaces explicitly require each other. The coupling is intentional
and visible in the code. Conceptual overlap (if any) confirms what the
dependency is about.

Verdict: *"Expected — direct structural dependency."*

### 2. `:family-naming-noise` — Same family, only prefix terms

```
¬structural-edge ∧ conceptual ∧ same-family ∧ independent-terms = []
```

All shared vocabulary comes from the namespace naming convention. No
genuine conceptual overlap beyond the family prefix.

Verdict: *"Likely naming similarity — shared terms are from namespace prefix."*

### 3. `:family-siblings` — Same family, has independent terms

```
¬structural-edge ∧ conceptual ∧ same-family ∧ independent-terms ≠ []
```

Sibling namespaces with genuine shared domain vocabulary beyond the
prefix. May warrant a shared abstraction within the family, or may be
expected specialization of a common concept.

Verdict: *"Family siblings with shared domain vocabulary — consider
whether a shared abstraction is warranted."*

### 4. `:likely-missing-abstraction` — Hidden in both lenses

```
¬structural-edge ∧ conceptual ∧ change ∧ ¬same-family
```

Different families, no structural edge, but both conceptually similar
and co-changing. Strongest signal of implicit coupling that should be
made explicit.

Verdict: *"Likely missing abstraction — hidden in both conceptual and
change lenses."*

### 5. `:hidden-conceptual` — Conceptual only, cross-family

```
¬structural-edge ∧ conceptual ∧ ¬change ∧ ¬same-family
```

Different families share vocabulary but don't co-change. The shared
concepts may warrant a named abstraction, or may reflect parallel
implementations of the same pattern.

Verdict: *"Hidden conceptual coupling — shared vocabulary with no
structural link."*

### 6. `:hidden-change` — Change coupling only

```
¬structural-edge ∧ ¬conceptual ∧ change
```

Co-change in git without conceptual or structural connection. Could be
a vestigial dependency from a past refactor, or an implicit contract
(e.g., configuration changes that affect both).

Verdict: *"Hidden change coupling — co-changing without structural or
conceptual link. Possible vestigial dependency."*

### 7. `:transitive-only` — No direct edge but transitive path exists

```
¬direct-edge ∧ shortest-path ∧ ¬conceptual ∧ ¬change
```

Connected through intermediaries but no direct or hidden coupling. Low
concern.

Verdict: *"Transitively connected only — no direct coupling signal."*

### 8. `:unrelated` — No connection at all

```
¬direct-edge ∧ ¬shortest-path ∧ ¬conceptual ∧ ¬change
```

Verdict: *"No coupling detected."*

## Where the Verdict Lives

### In the data

Add `:verdict` to the map returned by `explain-pair-data`:

```clojure
{:ns-a ...
 :ns-b ...
 :structural ...
 :conceptual ...
 :change ...
 :finding ...
 :verdict {:category    :likely-missing-abstraction
           :explanation "Likely missing abstraction — hidden in both
                         conceptual and change lenses."}}
```

The verdict is a **pure function of the other fields** in the result map.
It doesn't require any additional data or computation.

### In output

**Text:** New `VERDICT` section at the bottom of explain-pair:

```
VERDICT
  likely missing abstraction
  Hidden in both conceptual and change lenses.
```

**Markdown:** New `## Verdict` section with emoji severity cue:

```markdown
## Verdict

🔴 **Likely missing abstraction** — hidden in both conceptual and change lenses.
```

**EDN/JSON:** The `:verdict` key on the explain-pair data, passed through
the envelope as-is.

### Verdict severity hint

Each verdict category maps to a visual severity for output rendering:

| Category | Severity hint |
|----------|--------------|
| `expected-structural` | — (neutral, no marker) |
| `family-naming-noise` | — (neutral) |
| `family-siblings` | 🟡 (worth noting) |
| `likely-missing-abstraction` | 🔴 (actionable) |
| `hidden-conceptual` | 🟡 (investigate) |
| `hidden-change` | 🟡 (investigate) |
| `transitive-only` | 🟢 (low concern) |
| `unrelated` | — (neutral) |

This is advisory, not a formal severity — the existing `:finding` already
carries the diagnose severity.

## Implementation

### Step 1: `verdict` function in explain.clj

Pure function: `(verdict structural conceptual change) → {:category kw :explanation str}`

Takes the three data sections from explain-pair-data (structural, conceptual,
change maps). Applies rules top-down, first match. ~30 lines.

### Step 2: Wire into explain-pair-data

Add `:verdict (verdict structural c-pair x-pair)` to the returned map.

### Step 3: Output formatting

Add `VERDICT` section to `format-explain-pair` (text) and
`format-explain-pair-md` (markdown).

### Step 4: Schema doc

Document `:verdict` shape in `doc/schema.md`.

## Step dependency graph

```
Step 1 (verdict fn) → Step 2 (wire) → Step 3 (output) → Step 4 (docs)
```

All steps are small. Total: ~60 lines of code + ~40 lines of tests.
