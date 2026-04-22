Approach:
- preserve the current `gordian local` command/report/output behavior
- refactor the local-analysis evidence/extraction core toward smaller concern-local pure modules
- keep one canonical evidence assembly boundary consumed by `burden.clj`
- use existing semantic tests plus a few focused edge-case additions to ensure the refactor is behavior-preserving

Execution phases:

### Phase 1 — lock fragile semantics and review edge cases in tests
- add focused tests for branch variant edge cases:
  - `cond`
  - `condp`
  - `case`
  - nilability differences
  - obvious map keyset differences
- add focused tests for sentinel-counting edge cases
- add focused tests for abstraction-classification edge cases
- add tests that guard current working-set point kinds and helper/opaque-chain boundaries during refactor

Exit condition:
- the most fragile current semantic seams are explicitly protected by tests before code movement begins

### Phase 2 — introduce explicit concern seams
- decide the smallest useful module split for the current codebase
- likely extract some subset of:
  - steps/main-path extraction
  - flow analysis
  - shape/variant logic
  - dependency opacity/jump logic
  - working-set program-point construction
- keep `gordian.local.evidence` as a thin assembly façade or similarly small orchestration layer

Exit condition:
- the monolithic evidence namespace is materially reduced and concern ownership is obvious

### Phase 3 — make the shared evidence model explicit
- add a concise schema comment or docstring for the canonical evidence payload
- normalize naming and argument ordering across extractors
- ensure burden scorers depend on explicit evidence fields rather than incidental helper coupling

Exit condition:
- a reviewer can understand the intermediate model without reading every helper implementation first

### Phase 4 — cleanup and simplification
- remove dead/private unused constants or helpers
- simplify any remaining tangled helper interactions discovered during extraction
- confirm no new patterns conflict with Gordian’s usual pure-core architecture

Exit condition:
- the refactor leaves the local-analysis core simpler, more consistent, and more robust than before

### Phase 5 — validation and closure
- run full suite
- run representative `gordian local` command sanity checks
- compare behavior on a small representative sample if needed
- update task notes / docs only if the refactor clarifies internal semantics worth recording

Exit condition:
- behavior remains stable and the implementation shape improvement is demonstrable

Expected shape:
- same user-facing command/report/output behavior
- smaller pure modules or clearly separated semantic seams
- stronger tests around fragile semantic edges
- a thinner, more reviewable evidence assembly layer

Risks:
- accidental semantic drift during file splitting
- over-factoring into too many tiny helpers
- refactor churn without enough shape improvement

Mitigations:
- add tests before moving fragile logic
- prefer a few meaningful modules over excessive fragmentation
- keep burden formulas and report shaping in their current roles
- review the final shape against the original review findings, not just test pass/fail