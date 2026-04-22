- [ ] Phase 1: inventory all current hardcoded local metric-name seams
- [ ] Phase 1: lock current built-in alias behavior for `--sort`, `--min`, and `--bar` in tests
- [ ] Phase 1: enumerate the first-slice canonical per-unit numeric keys to support directly

- [ ] Phase 2: define backward-compatible alias map and arbitrary key-path syntax
- [ ] Phase 2: implement one shared metric-resolution function for local metric tokens
- [ ] Phase 2: route metric lookup and min parsing through the shared resolver

- [ ] Phase 3: allow arbitrary numeric keys for `--sort`
- [ ] Phase 3: allow arbitrary numeric keys for `--bar`
- [ ] Phase 3: allow arbitrary numeric keys for `--min metric=value`
- [ ] Phase 3: keep `ns` and `var` as sort-only special cases
- [ ] Phase 3: add clear validation errors for unknown and non-numeric keys

- [ ] Phase 4: update scoped help for `gordian local`
- [ ] Phase 4: update `README.md` examples and option descriptions
- [ ] Phase 4: document nested-path usage such as `working-set.peak`
- [ ] Phase 4: document that built-in aliases remain supported

- [ ] Phase 5: add focused tests for arbitrary-key sorting/filtering/bar behavior
- [ ] Phase 5: run representative `gordian local` sanity checks with alias and nested-key inputs
- [ ] Phase 5: run full suite
- [ ] Phase 5: final review for backward compatibility and code-shape improvement
