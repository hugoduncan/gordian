- [ ] Phase 1: add semantic regression tests that encode the review findings against current `gordian local` heuristics
- [ ] Phase 1: add 021b-aligned tests for abstraction ordered-step sequences and oscillation
- [ ] Phase 1: add 021b-aligned tests for branch outcome shape variants
- [ ] Phase 1: add 021b-aligned tests for working-set program-point sampling
- [ ] Phase 1: add 021b-aligned tests for burden ownership / anti-double-counting boundaries

- [ ] Phase 2: refactor `gordian.local.evidence` to emit explicit ordered main-path step evidence
- [ ] Phase 2: refactor `gordian.local.evidence` to emit explicit branch-local outcome evidence
- [ ] Phase 2: refactor `gordian.local.evidence` to emit explicit working-set program points
- [ ] Phase 2: clarify evidence ownership boundaries for flow/state/shape/abstraction/dependency
- [ ] Phase 2: simplify or split evidence helpers if that improves reviewability and burden-local reasoning

- [ ] Phase 3: converge `flow-burden` scoring onto the refactored evidence model
- [ ] Phase 3: converge `abstraction-burden` scoring onto ordered main-path evidence
- [ ] Phase 3: converge `shape-burden` scoring onto explicit transition/variant evidence
- [ ] Phase 3: converge `dependency-burden` scoring onto explicit helper/opaque-chain/jump evidence
- [ ] Phase 3: converge `state-burden` scoring onto explicit mutation/rebinding/temporal evidence
- [ ] Phase 3: converge `working-set` scoring onto explicit 021b program points
- [ ] Phase 3: lock exclusions and anti-double-counting behavior in tests

- [ ] Phase 4: tighten finding triggers to derive from converged evidence/burdens
- [ ] Phase 4: add tests for finding trigger semantics

- [ ] Phase 5: update README/state/docs wording where semantic claims need sharpening
- [ ] Phase 5: run full suite and representative `gordian local` command sanity checks
- [ ] Phase 5: final review for semantic alignment, architecture fit, and code-shape simplicity
