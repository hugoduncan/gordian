Approach:
- treat this as a narrow follow-up to task 015
- add the missing rollup-combination coverage first
- make the summary fallback seam explicit with the smallest reasonable helper extraction
- preserve all task 015 semantics

Execution phases:

### Phase 1 — lock the missing coverage matrix
- identify the exact current tests for rollup omission/inclusion in `complexity` and `local`
- add explicit test cases for namespace-only, project-only, and both-rollups combinations
- cover both canonical payload shape and human-facing section rendering where appropriate

Exit condition:
- all independent rollup combinations are test-visible for both commands

### Phase 2 — tighten summary fallback seam
- identify the duplicated formatter logic that derives summary counts when `:project-rollup` is absent
- extract or clarify that logic into a tiny explicit seam
- keep behavior unchanged

Exit condition:
- summary fallback behavior is explicit and reviewable rather than repeated inline

### Phase 3 — validation and consistency pass
- run full suite
- verify task 015 semantics remain unchanged
- update task docs/checklists if needed to reflect the completed follow-up

Exit condition:
- follow-up feedback is fully addressed with green validation

Expected shape:
- explicit tests for all rollup flag combinations in both commands
- small explicit summary helper seam
- no semantic drift from task 015
