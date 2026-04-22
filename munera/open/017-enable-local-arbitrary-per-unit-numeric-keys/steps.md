- [x] Phase 1: inventory all current hardcoded local metric-name seams
- [x] Phase 1: lock current built-in alias behavior for `--sort`, `--min`, and `--bar` in tests
- [x] Phase 1: enumerate the first-slice canonical per-unit numeric keys to support directly

- [x] Phase 2: define backward-compatible alias map and arbitrary key-path syntax
- [x] Phase 2: implement one shared metric-resolution function for local metric tokens
- [x] Phase 2: route metric lookup and min parsing through the shared resolver

- [x] Phase 3: allow arbitrary numeric keys for `--sort`
- [x] Phase 3: allow arbitrary numeric keys for `--bar`
- [x] Phase 3: allow arbitrary numeric keys for `--min metric=value`
- [x] Phase 3: keep `ns` and `var` as sort-only special cases
- [x] Phase 3: add clear validation errors for unknown and non-numeric keys

- [x] Phase 4: update scoped help for `gordian local`
- [x] Phase 4: update `README.md` examples and option descriptions
- [x] Phase 4: document nested-path usage such as `working-set.peak`
- [x] Phase 4: document that built-in aliases remain supported

- [x] Phase 5: add focused tests for arbitrary-key sorting/filtering/bar behavior
- [x] Phase 5: run representative `gordian local` sanity checks with alias and nested-key inputs
- [x] Phase 5: run full suite
- [x] Phase 5: final review for backward compatibility and code-shape improvement

- [x] Phase 6: resolve whether supported arbitrary numeric keys are derived from the authoritative canonical unit/report shape or from a maintained documented schema surface
- [x] Phase 6: if needed, remove shadow-schema duplication and derive supported numeric paths from the authoritative seam
- [x] Phase 6: otherwise narrow task/docs/design wording to the maintained documented schema surface explicitly
- [x] Phase 6: make namespace-rollup sorting semantics explicit and coherent for arbitrary `--sort` keys
- [x] Phase 6: add tests locking the final supported-key authority and rollup-sort behavior
- [ ] Phase 6: re-review task 017 and only then close it
