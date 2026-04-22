Approach:
- treat this as a structural refactor with behavior preservation as the primary constraint
- decompose output formatting by command/domain rather than by speculative abstraction layer
- keep a stable façade in `gordian.output` and migrate implementations behind it incrementally
- split tests in parallel with production extraction so locality improves continuously

Recommended implementation sequence:
1. inventory public functions in `gordian.output` and group them by command family
2. extract the smallest stable shared helpers into `gordian.output.common`
3. extract `analyze` output and its tests to `gordian.output.analyze`
4. extract `diagnose` output and its tests to `gordian.output.diagnose`
5. extract `explain` output and its tests to `gordian.output.explain`
6. extract the remaining command families one by one: compare, subgraph, communities, tests, gate, DSM, complexity
7. reduce `gordian.output` to a compatibility façade that delegates to the new namespaces
8. remove any now-dead private helpers and fixture duplication that became obsolete during the split

Proposed target grouping:
- analyze: report, cycles, conceptual coupling, change coupling
- diagnose: findings, clusters, markdown/text diagnose rendering
- explain: explain-ns, explain-pair, verdict formatting, markdown/text
- compare: compare text/markdown
- subgraph: subgraph text/markdown
- communities: communities text/markdown
- tests: tests command text/markdown
- gate: gate text/markdown
- DSM: DSM text/markdown and closely related helpers
- complexity: complexity text/markdown and complexity-specific bar/table helpers

Validation strategy:
- preserve current public function names at the façade
- run output tests after each extraction
- avoid changing assertion intent while relocating tests
- prefer exact-string stability over cleanup churn during the refactor

Risks:
- helper extraction can create accidental shared abstractions that are worse than duplication
- large fixture blocks may tempt premature centralization
- façade forwarding can become noisy if extraction is not organized coherently

Mitigations:
- bias toward local private helpers
- centralize only truly shared low-level formatting primitives
- keep each extraction independently testable and reviewable
- retain the façade until all call sites and tests are settled