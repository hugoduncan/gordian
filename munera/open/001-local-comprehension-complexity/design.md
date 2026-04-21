Define a Gordian local-analysis feature based on Local Comprehension Complexity (LCC).

Goal:
- add a metric model for code in the small that improves on cyclomatic and cognitive complexity
- focus on local comprehension burden: flow, state, shape, abstraction, dependency, and working set
- produce a design and implementation path suitable for a future `gordian local` command

Context:
- design captured in `doc/design/021-local-comprehension-complexity.md`
- v1 emphasizes top-level function arities / defmethod bodies and syntax-first heuristics
- headline metric is explanatory first: burden vector + findings, with a v1 rollup

Constraints / acceptance:
- preserve Gordian’s pure-core / thin-IO architecture style
- keep v1 deterministic and implementation-friendly
- define a narrow first slice before broader semantic analysis
- task is complete when there is an implementation plan and executable first slice defined clearly enough to build incrementally
