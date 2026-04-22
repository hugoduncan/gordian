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
