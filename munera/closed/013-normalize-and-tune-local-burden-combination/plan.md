Approach:
- keep raw local burden formulas unchanged
- add an explicit calibration seam for normalized burden combination
- ship one documented initial normalization/weighting scheme, then tune narrowly against the gordian watchlist

Execution phases:

### Phase 1 — expose burden-family distributions and lock current behavior
- add/refine tests around current local report burden fields and total-score assembly
- add code support to compute per-family distribution summaries over analyzed units
- make calibration inputs deterministic and reviewable in tests

Exit condition:
- burden-family distributions and current combination basis are test-visible

### Phase 2 — implement normalization metadata and normalized burden values
- derive per-family scales from the analyzed unit population using the chosen deterministic rule
- add calibration metadata to the canonical local report
- compute normalized burden family values per unit without changing raw burden fields

Exit condition:
- canonical report contains reproducible calibration inputs and normalized burden data

### Phase 3 — switch combined score to normalized combination
- replace the raw-sum total with normalized combination using explicit weights
- preserve existing raw burden values and findings
- update project/namespace/unit rollups and max-unit selection to use the new total consistently

Exit condition:
- local totals and ranking are driven by the normalized combination end-to-end

### Phase 4 — tune and validate on gordian
- review the watchlist and top-N rankings before/after normalization
- keep equal weights if normalization alone is sufficient; otherwise apply small explicit weight adjustments
- lock the chosen scales/weight policy behavior with focused tests

Exit condition:
- ranking changes are reviewed, justified, and encoded explicitly

### Phase 5 — output/docs cleanup and final validation
- ensure text/markdown stay readable and do not over-surface calibration detail
- ensure EDN/JSON expose raw burdens and normalized/calibration data coherently
- update README/help/schema docs where needed
- run full suite and representative `gordian local` sanity checks

Exit condition:
- calibrated local score is documented, coherent, and fully validated

Expected shape:
- raw burden families preserved
- normalized burden families and calibration metadata explicit
- combined local score computed from normalized families via reviewable weights
- ranking behavior improved for the identified watchlist without destabilizing the local command UX