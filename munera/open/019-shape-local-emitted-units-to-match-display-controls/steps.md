- [x] Phase 1: add/update final-state tests locking shaped top-level `:units` behavior for `gordian local`
- [x] Phase 1: add current-state characterization tests only if they help drive the change safely
- [x] Phase 1: lock that `--fail-above` still evaluates canonical analyzed units rather than shaped emitted `:units`
- [x] Phase 1: identify obsolete tests/fixtures asserting separate top-level `:units` vs `:display :units`

- [x] Phase 2: refactor the local command/report seam so enforcement no longer depends on finalized top-level `:units`
- [x] Phase 2: preserve access to canonical analyzed units for enforcement from a pre-finalization seam
- [x] Phase 2: make canonical enforcement population usage explicit and reviewable

- [x] Phase 3: change local report finalization so top-level `:units` on the finalized report respects `--min`, `--sort`, and `--top`
- [x] Phase 3: remove `:display :units` or reduce `:display` to only justified non-unit responsibilities
- [x] Phase 3: verify `:max-unit` remains based on the full canonical analyzed population even when absent from shaped top-level `:units`
- [x] Phase 3: keep namespace rollups canonical unless an explicitly documented and test-covered different rule is adopted
- [x] Phase 3: verify `:project-rollup` remains canonical over the full analyzed unit population rather than shaped top-level `:units`

- [x] Phase 4: align text output with the refined top-level `:units` contract
- [x] Phase 4: align markdown output with the refined top-level `:units` contract
- [x] Phase 4: align EDN/JSON output with the refined top-level `:units` contract
- [x] Phase 4: remove residual output dependencies on the old `:display` split where no longer needed
- [x] Phase 4: verify no remaining `:display` structure duplicates the public unit list

- [x] Phase 5: update `README.md` and scoped help for `local` if needed
- [x] Phase 5: update/remove obsolete fixtures and tests for the old payload split
- [x] Phase 5: add/keep regression coverage for canonical enforcement and summary-basis behavior
- [x] Phase 5: run representative sanity checks across output modes and enforcement scenarios
- [x] Phase 5: run full suite

Review follow-up:
- [x] clarify human-readable `local` summary count basis when `:project-rollup` is present and top-level `:units` is shaped
- [x] decide whether summary counts should reflect shaped emitted units, canonical analyzed units, or both with explicit labels
- [x] add/update tests locking the chosen summary-count semantics for text and markdown output
- [x] fix `doc/schema.md` local section heading so it no longer labels shaped `:units` as canonical
