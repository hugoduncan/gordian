Approach:
- treat `doc/design/021-local-comprehension-complexity.md` as the normative design basis
- write an implementation-plan companion doc that narrows v1 into concrete modules, data flow, and steps
- start with the smallest valuable subset: flow, state, shape, abstraction, and working-set burdens
- defer experimental or low-confidence areas such as regularity scoring and deep semantic inference

Expected shape:
- one or more pure analysis modules for unit extraction and burden computation
- machine-readable output aligned with Gordian command conventions
- later integration into CLI as a dedicated local-analysis command or equivalent workflow

Risks:
- heuristic noise from abstraction and dependency classification
- parser choice affecting source locations and shape fidelity
- accidental double-counting across burden dimensions

Mitigations:
- implement the normative v1 constraints exactly
- calibrate with a small benchmark corpus before broadening scope
- keep findings explanatory and conservative
