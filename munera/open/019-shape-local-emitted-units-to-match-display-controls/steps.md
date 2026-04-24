- [ ] Phase 1: add/update tests locking shaped top-level `:units` behavior for `gordian local`
- [ ] Phase 1: lock that enforcement still evaluates canonical analyzed units rather than shaped emitted `:units`
- [ ] Phase 1: identify obsolete tests/fixtures asserting separate top-level `:units` vs `:display :units`

- [ ] Phase 2: change local report/output finalization so emitted top-level `:units` respects `--min`, `--sort`, and `--top`
- [ ] Phase 2: preserve canonical analyzed units for enforcement without exposing them as the emitted top-level `:units`
- [ ] Phase 2: remove or simplify `:display` if it becomes redundant
- [ ] Phase 2: verify remaining report sections still have coherent basis semantics

- [ ] Phase 3: align text output with the new emitted `:units` contract
- [ ] Phase 3: align markdown output with the new emitted `:units` contract
- [ ] Phase 3: align EDN/JSON output with the new emitted `:units` contract
- [ ] Phase 3: ensure output semantics remain coherent with `complexity` where intended

- [ ] Phase 4: update README/help/examples if needed
- [ ] Phase 4: update/remove obsolete fixtures and tests for the old payload split
- [ ] Phase 4: add/keep regression coverage for canonical enforcement behavior
- [ ] Phase 4: run representative sanity checks
- [ ] Phase 4: run full suite
