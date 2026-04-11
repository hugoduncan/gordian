# User Feedback Analysis — Real-World Usage

Source: AI assistant used gordian on a large Clojure project (~158 files,
~17 components, Polylith-ish layout with `psi.agent-session.*` family).

## Feedback Summary

12 items organized by the user's priority. Mapped against existing
capabilities, implementation effort, and dependency ordering.

---

## Tier 1 — Highest leverage (user's top 5)

### F1. Compare/diff mode
**User pain:** After each refactor, wanted before/after delta — PC change,
new/removed findings, coupling score shifts. Currently requires manual
eyeballing of two EDN dumps.

**Maps to:** PLAN.md "Deferred: Diff / baseline mode"

**Design sketch:**
- `gordian compare before.edn after.edn` — two saved snapshots
- `gordian compare --ref HEAD~4 --ref HEAD` — git-checkout comparison
- Delta report: ΔPC, Δcycles, findings added/removed, node metric deltas
- Depends on: stable schema (F2)

**Effort:** Medium-large. The EDN snapshot comparison is straightforward;
the git-ref mode requires checkout + re-scan (or stashing reports per ref).

**Sequence:** After F2 (schema).

### F2. Stable documented EDN/JSON schema
**User pain:** Schema drift between versions broke scripting. `:sim` → `:score`
rename surprised them. No version marker in output.

**What exists:** Schema was normalized (session 11) but output has no
`:schema-version`, `:command`, `:lenses-enabled`, or `:thresholds` metadata.

**Design sketch:**
- Add envelope: `{:gordian/version "0.2.0" :gordian/schema 1
                  :command :analyze :lenses {...} :thresholds {...} ...}`
- Document the exact EDN shape for each command in README or `doc/schema.md`
- `gordian schema` subcommand (optional, low priority)

**Effort:** Small. Mostly additive — wrap existing output maps.

**Sequence:** First. Everything else benefits from this.

### F3. Family-noise suppression for conceptual coupling
**User pain:** After splitting `psi.agent-session` into submodules, got
flooded with sibling pairs sharing "mutation", "agent", "session" — true
but not actionable.

**What exists:** Nothing. Stop-words are generic English, not project-aware.

**Design sketch:**
- Extract namespace-prefix tokens (segments of ns name)
- For each pair, compute family overlap: tokens from shared ns prefix
- Discount or remove shared terms that come from common prefix
- `--suppress-family-noise` flag (or on by default with opt-out)
- Alternative: add `:family-noise? true` tag to findings, let user filter

**Effort:** Medium. Needs careful design — the heuristic must not suppress
genuine cross-family signal.

**Sequence:** Independent. Can be done anytime.

### F4. Grouped/clustered findings in diagnose
**User pain:** Same conceptual cluster appears as N pairwise findings.
Wants "cluster: background-jobs — members: A, B, C, D — evidence: ..."

**Maps to:** PLAN.md "Deferred: Cluster detection"

**Design sketch:**
- Post-process findings: group pairs sharing members into clusters
- Simple: connected-component grouping on finding pairs
- Output: cluster name (dominant terms), members, evidence summary
- Could be a separate `gordian clusters` or a section in `diagnose`

**Effort:** Medium. The clustering is straightforward (union-find on pair
findings). The UX design for output format needs care.

**Sequence:** After F3 (better to cluster after noise is suppressed).

### F5. Façade-aware interpretation
**User pain:** After refactoring `psi.agent-session.core` to delegate,
gordian still flagged it as god-module. It's actually a thin façade now.

**Design sketch:**
- Detect façade pattern: high Ca, low Ce (direct), delegates to submodules
- New role `:facade` or finding annotation `:likely-facade`
- Heuristic: Ca ≥ 3, Ce ≤ 2, all deps are in same ns family
- Adjust god-module finding severity when façade detected

**Effort:** Small. Pure heuristic on existing data.

**Sequence:** Independent. Quick win.

---

## Tier 2 — High value, moderate effort

### F6. Surface thresholds/lens activation in output
**User pain:** Not clear what thresholds were used, whether change lens
actually ran, whether tests were included.

**Overlaps with:** F2 (schema envelope).

**Design sketch:** Part of the envelope in F2:
```edn
{:lenses {:structural true
          :conceptual {:enabled true :threshold 0.20}
          :change {:enabled true :since "90 days ago" :min-co 2
                   :candidate-pairs 42 :reported-pairs 3}}
 :include-tests false
 :excludes ["user"]}
```

**Effort:** Small. Mostly passing config through to output.

**Sequence:** Bundle with F2.

### F7. Explain-pair verdict
**User pain:** Wants opinionated interpretation, not just raw facts.
"Missing abstraction" / "naming noise" / "expected" / "vestigial".

**Design sketch:**
- Add `:interpretation` key to explain-pair output
- Rules: family overlap + conceptual-only → "likely naming noise"
- Structural + conceptual → "expected"
- Change-only → "possible vestigial dependency"
- Hidden in both → "likely missing abstraction"

**Effort:** Small. Rule-based classification on existing data.

**Sequence:** After F3 + F5 (family detection + façade).

### F8. Family/subgraph views
**User pain:** Wants `gordian explain-family psi.agent-session.mutations`
or `gordian subgraph psi.agent-session`.

**Design sketch:**
- `gordian explain <prefix>` — when arg matches multiple ns, show family view
- Subgraph: filter to ns matching prefix + their direct external deps
- Metrics recomputed on subgraph (or annotated from full graph)
- Internal coupling density, boundary surface

**Effort:** Medium. Needs subgraph extraction + metric recomputation.

**Sequence:** Independent but benefits from F3 (family detection).

### F9. Change-coupling diagnostics when empty
**User pain:** 0 change pairs — was the lens even active? Not obvious.

**Overlaps with:** F6 (lens activation in output).

**Design sketch:** Part of F6 envelope:
```edn
:change {:enabled true :candidate-pairs 123 :reported-pairs 0
         :reason "no pairs met threshold"}
```

**Effort:** Tiny. Just thread the counts through.

**Sequence:** Bundle with F2/F6.

---

## Tier 3 — Future / larger scope

### F10. Actionability sort
`--rank actionability` vs `--rank severity`.
**Maps to:** Enhanced `diagnose.clj` scoring. Could fold family-noise
suppression (F3) and façade detection (F5) into a composite actionability
score.

### F11. CI/refactor-ratchet mode
`gordian gate --baseline gordian.edn --max-pc-delta 0.01`
**Maps to:** PLAN.md deferred "Self-analysis CI check". Depends on F2 (schema)
and F1 (compare). Natural capstone.

### F12. Better cluster detection
Full community detection (label propagation, modularity). Extends F4 beyond
finding-grouping to structural cluster discovery.

---

## Proposed Sequencing

```
Phase A — Schema & Metadata (small, unblocks everything)
  F2  Stable EDN/JSON schema envelope
  F6  Surface thresholds and lens activation in output
  F9  Change-coupling diagnostics when empty
  
Phase B — Signal Quality (medium, reduces noise)
  F5  Façade-aware interpretation
  F3  Family-noise suppression
  F7  Explain-pair verdict
  
Phase C — Comparison & Clustering (medium-large, highest strategic value)
  F1  Compare/diff mode
  F4  Grouped/clustered findings in diagnose
  
Phase D — Workflows & CI (builds on everything above)
  F8   Family/subgraph views
  F10  Actionability sort
  F11  CI/refactor-ratchet mode
  F12  Full cluster detection
```

Phase A is ~1 session. Phase B is ~1-2 sessions. Phase C is ~2 sessions.
Phase D is ongoing.
