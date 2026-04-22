2026-04-21

Task created to refactor the oversized `gordian.output` implementation and matching tests.

Observed current state:
- `src/gordian/output.clj` is ~1777 lines
- `test/gordian/output_test.clj` is ~1118 lines
- the file contains formatter logic for many command families: analyze, diagnose, explain, compare, subgraph, communities, tests, gate, DSM, and complexity

Implementation direction:
- keep `gordian.output` as the compatibility façade
- move command-specific implementation into smaller namespaces under `gordian.output.*`
- split tests to mirror production namespace boundaries
- preserve behavior first; do not bundle feature work into the refactor

Suggested first-cut namespace shape:
- `gordian.output.common`
- `gordian.output.analyze`
- `gordian.output.diagnose`
- `gordian.output.explain`
- `gordian.output.compare`
- `gordian.output.subgraph`
- `gordian.output.communities`
- `gordian.output.tests`
- `gordian.output.gate`
- `gordian.output.dsm`
- `gordian.output.complexity`

Refactor strategy:
1. identify the public functions currently consumed from `gordian.output`
2. carve out one command family at a time into a dedicated namespace
3. leave forwarding defs/wrappers in `gordian.output` so callers do not need to change immediately
4. move tests for that command family into a dedicated test namespace in the same step
5. run the full test suite after each extraction
6. continue until `gordian.output` is a thin façade

Key design preferences:
- prefer command-local private helpers over over-general shared helpers
- extract only truly shared primitives into `gordian.output.common`
- preserve exact output strings where practical to minimize churn
- keep markdown and text formatters for the same command near each other

Risks:
- accidental output drift from helper extraction
- brittle tests if fixture setup is over-centralized during the split
- hidden dependencies between helper functions currently co-located in one file

Mitigations:
- extract incrementally by command family
- keep compatibility façade intact until the end
- move tests alongside implementation so locality improves immediately
- avoid helper deduplication unless duplication is obviously real and stable

Completion target:
- command-oriented output namespaces
- mirrored command-oriented test namespaces
- thin compatibility façade in `gordian.output`
- no intentional change to user-visible output behavior