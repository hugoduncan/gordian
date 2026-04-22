2026-04-22

Created as a follow-up to task 007 after task review found the shipped `gordian local` feature useful but semantically under-converged relative to `021` / `021b`.

Normative basis:
- `doc/design/021-local-comprehension-complexity.md`
- `doc/design/021b-local-comprehension-complexity-computation-rules.md`
- review of commit `50ab3ff` (`feat: add local comprehension complexity command`)

Current status:
- `gordian local` exists and is integrated
- command/report/output architecture is good enough to preserve
- main follow-up need is semantic convergence of evidence and burden logic

Implementation direction:
- preserve the current command/report UX
- converge internals toward explicit 021b rule ownership
- add spec-anchored tests burden by burden before/while refactoring heuristics

Likely payoff:
- trustworthy burden semantics
- easier future extension to compare/gate/regularity work
- less review ambiguity between shipped behavior and normative docs
