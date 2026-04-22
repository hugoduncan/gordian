Approach:
- preserve the delivered `gordian local` command and its report/output conventions
- refactor the pure local-analysis core toward explicit 021b evidence ownership and burden semantics
- use spec-anchored tests to drive convergence and prevent regression back to broad proxy heuristics
- keep the work bounded to semantic convergence, not feature expansion

Execution phases:

### Phase 1 — lock semantic gaps in tests
- add targeted tests that encode the current review findings as executable expectations
- add burden-family tests derived from 021b rules for:
  - ordered abstraction step sequences and oscillation
  - branch outcome shape variants
  - working-set program-point sampling
  - anti-double-counting / burden ownership boundaries
- distinguish between command/report regression tests and semantic rule tests

Exit condition:
- the test suite clearly identifies where current heuristics diverge from intended semantics

### Phase 2 — refactor evidence model
- reshape `gordian.local.evidence` so it emits evidence that corresponds directly to 021b concepts
- make ordered main-path step sequences explicit
- make branch-local outcome evidence explicit
- make working-set program points explicit and reviewable
- clarify evidence ownership boundaries for flow/state/shape/abstraction/dependency

Exit condition:
- evidence payload is explicit enough that burden computations read like 021b rule translations, not broad tree proxies

### Phase 3 — converge burden-family scorers
- rebuild or refine burden-family scoring over the improved evidence model:
  - flow
  - abstraction
  - shape
  - dependency
  - state
  - working-set
- make burden formulas and exclusions visible in `burden.clj`
- lock anti-double-counting behavior in tests

Exit condition:
- burden-family outputs materially match 021b semantics on representative rule-focused cases

### Phase 4 — converge findings
- tighten finding triggers so they derive from the improved evidence/burden semantics
- preserve the shipped finding kinds unless semantics require a narrower trigger
- add tests tying finding triggers to the intended evidence conditions

Exit condition:
- findings remain high-signal and semantically defensible

### Phase 5 — docs and closure alignment
- update README / task notes / state language if needed so they accurately describe the converged semantics
- confirm task 007 remains historical feature-delivery work and task 008 closes the convergence gap
- run full suite and sanity-check representative command output

Exit condition:
- documentation and task status accurately reflect the implementation semantics

Expected shape:
- command/output/report surface remains stable
- pure-core internals become more explicit and reviewable
- tests clearly separate semantic rule coverage from presentation/workflow coverage

Risks:
- score changes may invalidate existing fixture expectations
- evidence model refactor may temporarily increase complexity before simplification lands
- convergence could sprawl into adjacent feature ideas

Mitigations:
- converge one burden family at a time
- prefer small explicit helper functions over monolithic evidence logic
- keep compare/gate/regularity out of scope
- preserve command/report UX unless a semantics-driven change is necessary
