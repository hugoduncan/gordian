2026-04-22

Created from the review of the combined 007+008+009 local-analysis implementation.

Trigger:
- review verdict: approve-with-followups
- remaining issues are precision/reviewability concerns, not feature failure

Why this task exists:
- `gordian local` is good and should stay closed as a feature line
- a few semantics are still surprising or asymmetrical enough to merit explicit cleanup
- the 009 refactor improved module shape, but inner evidence record shapes can still be made more reviewable

Intended payoff:
- more trustworthy and explainable burden semantics
- clearer reviewability of local evidence records
- lower risk that `common.clj` becomes a new semantic grab-bag
- a cleaner, more stable base for any future local-analysis refinements

This task should stay narrow. It is a quality-tightening follow-up, not a redesign.

Outcome:
- sentinel semantics were narrowed from broad "any sentinel-bearing branch/predicate form" counting to explicit sentinel-bearing forms:
  - branch forms only count when an outcome arm carries a sentinel value
  - direct `=` comparisons against sentinel literals still count
- branch-local opaque pipelines are now treated as branch-local helper/uncertainty, not as top-level opaque pipeline stages:
  - `:dependency :opaque-stages` now counts only top-level/main-path pipeline stages
  - branch-local opaque chains still contribute through `:dependency :helpers`
  - working-set program points no longer emit `:pipeline-stage` / `:main-path-step` samples for branch-local pipeline stages
- inner evidence record shapes were made more explicit with documented constructors for:
  - step maps in `gordian.local.steps`
  - branch-region maps in `gordian.local.shape`
  - program-point maps in `gordian.local.working-set`

Validation:
- full suite passes after the change
- representative `bb gordian local src --top 3` sanity check passes

