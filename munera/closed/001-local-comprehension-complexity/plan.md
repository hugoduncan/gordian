Approach:
- treat `doc/design/021-local-comprehension-complexity.md` as the main source for the metric definition
- refine that document into a clear, stable, reviewable definition of Local Comprehension Complexity
- focus on conceptual clarity first: what the metric measures, how its burden dimensions relate, and what its first-version boundaries are
- use the `code-shaper` lens to test whether the metric meaningfully captures simplicity, consistency, robustness, and local comprehensibility
- defer implementation planning until the metric definition is settled

Expected shape:
- a converged metric definition describing:
  - the metric’s central question
  - its burden dimensions
  - its unit model
  - the role of the burden vector and any aggregate score
  - its normative v1 simplifications
  - the findings/interpretations it is intended to support
  - its relationship to `code-shaper` qualities
- explicit treatment of ambiguities, overlaps, and resolved v1 decisions in the metric definition itself

Risks:
- burden dimensions overlap ambiguously
- the metric drifts toward implementation convenience rather than conceptual clarity
- the design tries to solve adjacent implementation questions prematurely
- a headline score overshadows the explanatory vector

Mitigations:
- keep implementation concerns explicitly out of scope
- prioritize clear conceptual boundaries between burden dimensions
- preserve the burden vector as the primary artifact
- record unresolved conceptual questions explicitly rather than leaving them implicit
