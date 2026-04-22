Approach:
- treat this as a narrow command-surface and report-shape convergence task across the two local-analysis commands
- make rollup inclusion an explicit option, with identical semantics in `complexity` and `local`
- preserve existing unit analysis and scoring behavior
- update canonical report shaping first, then rendering, then docs/tests

Execution phases:

### Phase 1 — lock the option and section model
- lock the explicit flag names as `--namespace-rollup` and `--project-rollup`
- document default omission semantics for both commands
- identify existing report/finalizer seams in `complexity` and `local` where optional section inclusion should live

Exit condition:
- one consistent option/section model is defined for both commands

### Phase 2 — CLI and report-shape changes for `complexity`
- add explicit rollup include flags to `complexity`
- thread the section choices into option metadata
- make canonical `complexity` report shaping omit `:namespace-rollups` and `:project-rollup` unless requested
- keep unit display semantics unchanged otherwise

Exit condition:
- `complexity` has unit-only default behavior with explicit independent rollup opt-in

### Phase 3 — CLI and report-shape changes for `local`
- add the same explicit rollup include flags to `local`
- thread the section choices into option metadata
- make canonical `local` report shaping omit `:namespace-rollups` and `:project-rollup` unless requested
- preserve the existing canonical/display distinction already established in the local report

Exit condition:
- `local` has unit-only default behavior with explicit independent rollup opt-in

### Phase 4 — output rendering and help/docs
- update text and markdown output for both commands to render only requested rollup sections
- update scoped help and examples for both commands
- update README / practical guide / related docs for the new default and explicit rollup flags

Exit condition:
- human-facing output and docs match the new section model

### Phase 5 — validation and compatibility review
- add tests for default omission and each independent opt-in combination for both commands
- verify EDN/JSON omission semantics
- verify interaction with `--sort`, `--top`, `--min`, and `--bar`
- run full suite

Exit condition:
- behavior is fully test-covered and consistent across both commands

Expected shape:
- `complexity` and `local` share the same rollup option names and default semantics
- canonical reports include only requested rollup sections
- human-readable output is more compact by default
- docs and help make the behavioral change explicit
