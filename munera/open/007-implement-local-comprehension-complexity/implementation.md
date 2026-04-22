2026-04-21

Task created after closure of the LCC metric-definition task.

Normative basis:
- `doc/design/021-local-comprehension-complexity.md`
- `munera/closed/001-local-comprehension-complexity/`

Current status:
- the LCC metric definition is converged
- no implementation exists yet
- this task covers the first implementation of the metric in Gordian

Initial implementation direction:
- new `gordian local` command
- pure analysis pipeline
- top-level local units only
- burden vector + findings primary, headline rollup secondary

Likely follow-on work after this task:
- compare/gate support for local metrics
- richer regularity scoring
- deeper semantic refinement if justified by real usage
