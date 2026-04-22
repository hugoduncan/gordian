Approach:
- preserve existing `gordian local` UX for current burden-family aliases
- introduce one explicit metric-resolution seam for aliases and arbitrary numeric key paths
- implement the narrowest useful arbitrary-key model first: documented direct numeric keys and nested numeric dotted paths

Execution phases:

### Phase 1 — inventory and lock current local metric-option behavior
- identify all current places where local metric names are hardcoded (`cli` validation, min parsing, metric lookup, bar validation, docs, tests)
- add or refine tests that lock current built-in alias behavior for `--sort`, `--min`, and `--bar`
- identify the canonical per-unit numeric fields that should be addressable in the first slice

Exit condition:
- current behavior and the target numeric key surface are explicit and test-visible

### Phase 2 — design and implement metric-resolution seam
- define the metric token syntax and backward-compatible alias map
- implement a single resolver from CLI metric token -> unit numeric extractor / canonical field path
- make `metric-value`, `parse-min-expression`, `valid-sort-key?`, and `valid-bar-metric?` use the shared resolver

Exit condition:
- one explicit resolver governs local metric targeting end-to-end

### Phase 3 — extend CLI validation and report shaping
- allow arbitrary numeric key tokens for `--sort`
- allow arbitrary numeric key tokens for `--bar`
- allow arbitrary numeric key tokens in `--min metric=value`
- keep `ns` and `var` as sort-only special cases
- ensure unknown or non-numeric keys fail with clear command errors

Exit condition:
- CLI accepts and validates arbitrary numeric per-unit keys coherently

### Phase 4 — docs and help convergence
- update scoped help text for `gordian local`
- update README examples and option descriptions
- document at least one nested-path example such as `working-set.peak`
- clarify that built-in aliases remain supported

Exit condition:
- user-facing docs match the shipped key syntax and semantics

### Phase 5 — validation and review
- run focused tests for parse/validation/report behavior
- run representative `gordian local` commands using built-in aliases and arbitrary nested keys
- run full suite
- verify no semantic drift for existing built-in workflows

Exit condition:
- arbitrary-key support is covered, documented, and backward compatible

Expected shape:
- existing built-in local metric aliases still work unchanged
- arbitrary numeric per-unit keys can be used in `--sort`, `--min`, and `--bar`
- one explicit metric-resolution seam replaces duplicated hardcoded mappings
- docs/help/tests converge on the same key model
