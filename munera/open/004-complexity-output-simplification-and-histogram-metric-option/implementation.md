2026-04-21

Task created to refine `gordian complexity` output after the two-metric convergence work.

Requested work:
- remove `decisions` from human-readable complexity output
- add an option that explicitly chooses which metric drives histogram bars

Current design status:
- the machine-readable schema already carries enough information to support this refinement without changing the underlying analysis model
- the main design choice to lock is whether the new histogram option should override sort-coupled bar behavior or replace it entirely

Locked/assumed direction for v1:
- remove `decisions` from text and markdown output only
- preserve `:cc-decision-count` in EDN/JSON payloads
- add `--bar cc|loc` as the explicit histogram selector
- keep `--sort` and `--bar` independent
- preserve near-current default behavior when `--bar` is absent:
  - `loc` if `--sort loc`
  - otherwise `cc`

Implementation shape:
- update pure complexity option metadata to carry `:bar`
- add CLI parsing/validation for `--bar`
- refactor complexity output helpers so the chosen bar metric is passed explicitly rather than inferred ad hoc
- simplify text/markdown rendering by removing `decisions` from visible tables
- retain machine-readable payload fields unchanged except for adding `:options :bar`

Completion target:
- a focused refinement that changes only display and display-option semantics, not the complexity analysis core
