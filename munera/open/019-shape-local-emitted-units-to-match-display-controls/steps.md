- [ ] Phase 1: add/update final-state tests locking shaped top-level `:units` behavior for `gordian local`
- [ ] Phase 1: add current-state characterization tests only if they help drive the change safely
- [ ] Phase 1: lock that `--fail-above` still evaluates canonical analyzed units rather than shaped emitted `:units`
- [ ] Phase 1: identify obsolete tests/fixtures asserting separate top-level `:units` vs `:display :units`

- [ ] Phase 2: refactor the local command/report seam so enforcement no longer depends on finalized top-level `:units`
- [ ] Phase 2: preserve access to canonical analyzed units for enforcement from a pre-finalization seam
- [ ] Phase 2: make canonical enforcement population usage explicit and reviewable

- [ ] Phase 3: change local report finalization so top-level `:units` on the finalized report respects `--min`, `--sort`, and `--top`
- [ ] Phase 3: remove `:display :units` or reduce `:display` to only justified non-unit responsibilities
- [ ] Phase 3: verify `:max-unit` remains based on the full canonical analyzed population even when absent from shaped top-level `:units`
- [ ] Phase 3: keep namespace rollups canonical unless an explicitly documented and test-covered different rule is adopted
- [ ] Phase 3: verify `:project-rollup` remains canonical over the full analyzed unit population rather than shaped top-level `:units`

- [ ] Phase 4: align text output with the refined top-level `:units` contract
- [ ] Phase 4: align markdown output with the refined top-level `:units` contract
- [ ] Phase 4: align EDN/JSON output with the refined top-level `:units` contract
- [ ] Phase 4: remove residual output dependencies on the old `:display` split where no longer needed
- [ ] Phase 4: verify no remaining `:display` structure duplicates the public unit list

- [ ] Phase 5: update `README.md` and scoped help for `local` if needed
- [ ] Phase 5: update/remove obsolete fixtures and tests for the old payload split
- [ ] Phase 5: add/keep regression coverage for canonical enforcement and summary-basis behavior
- [ ] Phase 5: run representative sanity checks across output modes and enforcement scenarios
- [ ] Phase 5: run full suite
