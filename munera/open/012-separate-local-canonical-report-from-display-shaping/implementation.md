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

This task should stay narrow. It is a report-shape cleanup, not a redesign of `gordian local` semantics or output UX.