Approach:
- refine the finalized `gordian local` report contract rather than only moving fields
- make top-level `:units` on the finalized report the shaped unit list users asked to see
- preserve canonical analyzed units for enforcement and full-population summaries at command/report seams
- remove or narrow `:display` only after its remaining responsibilities are made explicit

Execution phases:

### Phase 1 — lock the current mismatch and desired contract in tests
- add or update final-state tests for the desired refined behavior: top-level `:units` on the finalized report is shaped by `--min`, `--sort`, and `--top`
- add current-state characterization tests only if they help drive the change safely
- add or update tests locking that enforcement still evaluates the full canonical analyzed population, not emitted shaped `:units`
- identify fixtures/tests that currently depend on separate top-level `:units` vs `:display :units`

Exit condition:
- the target contract is test-visible and any useful characterization coverage is in place

### Phase 2 — separate canonical enforcement input from emitted unit payload
- refactor the `local` command/report seam so enforcement no longer depends on finalized top-level `:units`
- keep access to canonical units from `local/rollup` or another explicit pre-finalization seam
- make the enforcement path obviously use canonical units

Exit condition:
- emitted unit shaping can change without risking enforcement correctness

### Phase 3 — reshape the finalized local report contract
- change finalization so top-level `:units` on the finalized report reflects `--min`, `--sort`, and `--top`
- remove `:display :units` or reduce `:display` to only what is still justified
- verify `:max-unit` remains based on the full canonical analyzed population even when it is not present in shaped top-level `:units`
- keep namespace rollups computed from the full canonical analyzed unit population unless a different rule is explicitly documented and test-covered
- verify `:project-rollup` remains canonical over the full analyzed unit population rather than shaped top-level `:units`

Exit condition:
- the finalized local report has one clear public unit list and explicit summary basis semantics

### Phase 4 — converge formatters and machine-readable output
- update text output to consume the refined finalized-report contract directly
- update markdown output to consume the refined finalized-report contract directly
- ensure EDN and JSON expose the shaped top-level `:units`
- remove residual output code that depends on the old `:display` split where no longer needed
- ensure no remaining `:display` structure duplicates the public unit list

Exit condition:
- all output modes reflect the same visible unit-population semantics

### Phase 5 — docs, fixtures, and validation
- update `README.md` and scoped help for `local` if they describe or imply the old split-payload model
- update/remove obsolete fixtures and tests for the old contract
- add regression coverage for canonical enforcement and summary-basis semantics where needed
- run representative `gordian local` sanity checks across text/markdown/EDN/JSON and enforcement scenarios
- run full suite

Exit condition:
- docs, tests, fixtures, and runtime behavior all converge on the refined contract

Expected shape:
- top-level `:units` on the finalized report reflects `--min`, `--sort`, and `--top`
- enforcement still evaluates canonical full analyzed units
- `:max-unit`, namespace rollups, and `:project-rollup` have explicit, coherent canonical basis semantics
- `:display` is removed or reduced to only justified remaining non-unit responsibilities
- machine-readable `local` output becomes easier to understand and use
