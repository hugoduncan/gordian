- [x] Phase 1: add semantic regression tests that encode the review findings against current `gordian local` heuristics
- [x] Phase 1: add 021b-aligned tests for abstraction ordered-step sequences and oscillation
- [x] Phase 1: add 021b-aligned tests for branch outcome shape variants
- [x] Phase 1: add 021b-aligned tests for working-set program-point sampling
- [x] Phase 1: add 021b-aligned tests for burden ownership / anti-double-counting boundaries

- [x] Phase 2: refactor `gordian.local.evidence` to emit explicit ordered main-path step evidence
- [x] Phase 2: refactor `gordian.local.evidence` to emit explicit branch-local outcome evidence
- [x] Phase 2: refactor `gordian.local.evidence` to emit explicit working-set program points
- [x] Phase 2: clarify evidence ownership boundaries for flow/state/shape/abstraction/dependency
- [x] Phase 2: simplify or split evidence helpers if that improves reviewability and burden-local reasoning

- [x] Phase 3: converge `flow-burden` scoring onto the refactored evidence model
- [x] Phase 3: converge `abstraction-burden` scoring onto ordered main-path evidence
- [x] Phase 3: converge `shape-burden` scoring onto explicit transition/variant evidence
- [x] Phase 3: converge `dependency-burden` scoring onto explicit helper/opaque-chain/jump evidence
- [x] Phase 3: converge `state-burden` scoring onto explicit mutation/rebinding/temporal evidence
- [x] Phase 3: converge `working-set` scoring onto explicit 021b program points
- [x] Phase 3: lock exclusions and anti-double-counting behavior in tests

- [x] Phase 4: tighten finding triggers to derive from converged evidence/burdens
- [x] Phase 4: add tests for finding trigger semantics

- [x] Phase 5: update README/state/docs wording where semantic claims need sharpening
- [x] Phase 5: run full suite and representative `gordian local` command sanity checks
- [x] Phase 5: final review for semantic alignment, architecture fit, and code-shape simplicity
