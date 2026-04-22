- [ ] Phase 1: add tests for branch variant edge cases across `if` / `cond` / `condp` / `case`
- [ ] Phase 1: add tests for nilability and obvious map-keyset variant detection
- [ ] Phase 1: add tests for sentinel-counting edge cases
- [ ] Phase 1: add tests for abstraction-classification edge cases
- [ ] Phase 1: add guard tests for current working-set point kinds and helper/opaque-chain boundaries

- [ ] Phase 2: extract main-path / step logic into a concern-local seam
- [ ] Phase 2: extract branch/shape/variant logic into a concern-local seam
- [ ] Phase 2: extract dependency opacity / semantic-jump logic into a concern-local seam
- [ ] Phase 2: extract working-set program-point construction into a concern-local seam
- [ ] Phase 2: reduce `gordian.local.evidence` to a thin assembly façade or equivalently small orchestrator

- [ ] Phase 3: document the canonical shared evidence payload near the assembly boundary
- [ ] Phase 3: normalize naming and argument order across extracted helpers/modules
- [ ] Phase 3: ensure burden scorers rely only on explicit evidence fields, not incidental helper coupling

- [ ] Phase 4: remove dead/private unused constants or helpers
- [ ] Phase 4: simplify any remaining tangled helper interactions discovered during extraction
- [ ] Phase 4: final code-shape pass for simplicity, consistency, and robustness

- [ ] Phase 5: run full suite
- [ ] Phase 5: run representative `gordian local` sanity checks
- [ ] Phase 5: final review against the post-008 task review findings
