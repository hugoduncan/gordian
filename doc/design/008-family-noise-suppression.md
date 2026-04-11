# F3 — Family-Noise Suppression for Conceptual Coupling

## The Problem

After splitting `psi.agent-session` into submodules, gordian reports many
conceptual coupling pairs like:

```
psi.agent-session.mutations ↔ psi.agent-session.ui    sim=0.35
  shared terms: mutation, agent, session
```

These are technically true — the namespaces do share vocabulary. But the
shared vocabulary is entirely derived from the namespace naming convention.
Every sibling in `psi.agent-session.*` will share "psi", "agent", "session"
because those tokens appear in the namespace name, and `extract-terms` mines
namespace name tokens.

This floods `diagnose` with `:hidden-conceptual` findings that are not
actionable. The user has to mentally filter them during triage.

## Key Insight

The noise comes from a specific, identifiable source: **tokens that are
segments of the shared namespace prefix**. We already have:

- `family/family-prefix` → `"psi.agent-session"`
- `conceptual/tokenize` → `["psi" "agent" "session"]`

For a same-family pair, the shared prefix tokens are the expected overlap.
Any *additional* shared terms are the genuine signal.

## Design

### Two-layer approach

**Layer 1 — Pair annotation:** Tag every conceptual pair with family
metadata. This is data enrichment, always applied, no flag needed.

**Layer 2 — Finding severity adjustment:** In `diagnose`, use the
annotation to downgrade or suppress findings that are purely family noise.

### Layer 1: Annotate conceptual pairs

A new function in `family.clj`:

```
annotate-conceptual-pairs : pairs × graph → pairs'
```

For each conceptual pair:
1. Check `same-family?` on the two namespaces
2. If same-family: tokenize the shared family prefix
3. Partition `shared-terms` into `family-terms` (overlap with prefix tokens)
   and `independent-terms` (genuine domain signal)
4. Add to the pair map:
   - `:same-family?`    boolean
   - `:family-terms`    [string]  (terms from prefix)
   - `:independent-terms` [string]  (terms NOT from prefix)

For different-family pairs: `:same-family? false`, all terms are independent.

**Example:**

```clojure
;; Before annotation:
{:ns-a psi.agent-session.mutations
 :ns-b psi.agent-session.ui
 :score 0.35
 :shared-terms ["mutation" "agent" "session"]}

;; After annotation:
{:ns-a psi.agent-session.mutations
 :ns-b psi.agent-session.ui
 :score 0.35
 :shared-terms ["mutation" "agent" "session"]
 :same-family? true
 :family-terms ["agent" "session"]
 :independent-terms ["mutation"]}
```

Note: "mutation" is NOT a prefix token — it comes from the last segment of
`psi.agent-session.mutations`. It's domain vocabulary that happens to be
in a sibling namespace. That's a genuine (if expected) signal.

**Edge case: both namespaces contribute their own last-segment tokens.**
`psi.agent-session.mutations` contributes "mutation" to its own TF-IDF
vector. `psi.agent-session.ui` contributes "ui". Neither of those appears
in the *shared* prefix, so they'd only show up as shared-terms if both
namespaces use the same word in function names or docstrings. The prefix
tokenization handles this correctly — only prefix tokens are classified
as family-terms.

### Layer 2: Finding severity in diagnose

In `find-hidden-conceptual`:

```
Current:  score ≥ 0.20 → :medium,  score < 0.20 → :low
```

New logic when `:same-family? true`:
- If `independent-terms` is empty → pure family noise → `:low` severity,
  reason annotated with "likely naming similarity"
- If `independent-terms` is non-empty → partial signal → keep existing
  severity, but annotate evidence with family context

New logic when `:same-family? false`:
- Unchanged. Cross-family hidden conceptual coupling is the discovery signal.

In `find-cross-lens-hidden`:
- Same-family pairs that are hidden in both lenses remain `:high` (change
  coupling is independent of naming). But add `:same-family?` to evidence
  so the user can see the family relationship.

### Where annotation runs

In `main.clj/build-report`, after conceptual pairs are computed and the
graph is available:

```clojure
report (if conceptual-threshold
         (let [... pairs computed ...]
           (assoc report
                  :conceptual-pairs (family/annotate-conceptual-pairs pairs direct)
                  ...))
         report)
```

The annotation needs the graph (to derive `family-prefix` for each ns).
Actually, it only needs the two namespace symbols from each pair —
`family-prefix` works on symbols directly, no graph needed. But we pass
pairs + the tokenize function (to decompose the prefix).

### What changes in output

**Diagnose (text + markdown):**
- Family-noise findings show "likely naming similarity" in reason
- Evidence includes `:family-terms` and `:independent-terms`

**explain-pair:**
- When conceptual pair is same-family, show the family annotation

**EDN/JSON:**
- Pairs have `:same-family?`, `:family-terms`, `:independent-terms`
- Findings have the annotation in evidence

**Human-readable table (analyze):**
- No change. The conceptual section already shows shared terms.
  The annotation is in the data for machine consumers.

---

## Implementation Steps

### Step 1: `annotate-conceptual-pairs` in family.clj

New function. Takes pairs, returns pairs with family annotation.
Uses `family-prefix` and `conceptual/tokenize` to classify shared terms.

Depends on: `gordian.conceptual/tokenize` (already public).

Note on dependency direction: `family.clj` currently has no deps on other
gordian modules. Adding a dep on `conceptual/tokenize` would create
`family → conceptual`. Alternative: pass `tokenize-fn` as a parameter
(like we did with `terms-fn` in scan.clj). Or: extract `tokenize` to a
shared utility. Or: just accept the dependency — tokenize is a pure leaf
function, the dep is benign.

**Decision: accept the dependency.** `tokenize` is pure, stable, and
unlikely to change. The alternative (passing it as a parameter or extracting
it) adds indirection for no practical benefit.

Actually, wait. Let me reconsider. The only thing we need from tokenize
is splitting a prefix string into lowercase stemmed tokens. We could
inline a minimal version. But that's duplication. And `tokenize` is
exactly what produced the terms in the first place — using the same
function to decompose the prefix ensures token-level consistency (same
stemming, same stop-word removal).

**Final decision: `family.clj` requires `gordian.conceptual` for `tokenize`.**

Tests:
- Same-family pair: shared-terms partitioned correctly
- Different-family pair: all terms independent
- No shared terms: empty family-terms and independent-terms
- Prefix tokens that are stop-words (removed by tokenize): handled

### Step 2: Wire annotation into build-report

`main.clj`: after conceptual pairs computed, call
`family/annotate-conceptual-pairs` before assigning to report.

### Step 3: Severity adjustment in diagnose

`find-hidden-conceptual`: when `:same-family?` true and
`:independent-terms` empty → force `:low` severity, annotate reason.

`find-cross-lens-hidden`: add `:same-family?` to evidence (informational).

### Step 4: Output formatting

`format-evidence-lines` for `:hidden-conceptual`: when same-family,
show family-terms vs independent-terms distinction.

### Step 5: Schema doc update

Document new pair keys and finding annotation.

## Step Dependency Graph

```
Step 1 (annotate fn) → Step 2 (wire) → Step 3 (severity) → Step 5 (docs)
                                      → Step 4 (output)   ↗
```

## What This Does NOT Do

- Does not remove same-family pairs from output (they're still present)
- Does not change similarity scores (the TF-IDF computation is unchanged)
- Does not add a `--suppress-family-noise` flag (the annotation is always
  present; severity adjustment is the default behavior; if we later want
  a flag to fully hide family-noise pairs, the annotation enables it)
- Does not affect change coupling (change coupling is behavioral, not naming)
