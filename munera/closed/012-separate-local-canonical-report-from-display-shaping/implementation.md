2026-04-22

Created from the approve-with-followups review of the combined 007+008+009+010+011 local-analysis implementation.

Trigger:
- review verdict: approve-with-followups
- strongest remaining issue: `gordian local` mixes canonical analysis data with display-shaped unit slicing in the final report map

Why this task exists:
- tasks 007–011 successfully delivered, converged, refactored, and hardened `gordian local`
- the remaining concern is mostly about report-contract clarity, not feature semantics
- keeping canonical analysis data intact should make the subsystem easier to reason about and safer for future machine consumers

Intended payoff:
- clearer report-basis semantics
- less surprise in EDN/JSON payload interpretation
- better alignment with Gordian’s canonical-data-first style
- removal of small local report residue

What shipped:
- preserved canonical `:units` and `:namespace-rollups` in `gordian.local.report/finalize-report`
- added explicit display-shaped slices under `:display {:units ... :namespace-rollups ...}`
- migrated local text/markdown formatters to consume the explicit display seam
- removed unused `metric-name-order` residue
- added tests locking the canonical-vs-display boundary and EDN shape

Validation:
- representative sanity checks passed:
  - `bb -m gordian.main local resources/fixture`
  - `bb -m gordian.main local resources/fixture --markdown`
  - `bb -m gordian.main local resources/fixture --edn`
- full suite passes: 352 tests, 3176 assertions, 0 failures

This task stayed narrow. It clarified report shape without changing `gordian local` semantics or user-facing output behavior.