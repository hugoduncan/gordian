2026-04-22

Created as a follow-up to the post-008 munera task review of the combined local LCC implementation.

Trigger:
- review verdict: approve-with-follow-ups
- strongest remaining issue: `src/gordian/local/evidence.clj` is semantically improved but too monolithic and too coupled

Why this task exists:
- task 007 successfully shipped `gordian local`
- task 008 materially converged semantics toward `021` / `021b`
- the remaining problem is implementation shape and reviewability, not the end-user command surface

Intended payoff:
- lower local cognitive load in the local-analysis core
- clearer semantic seams for future refinement
- less risk of cross-burden regressions when changing one concept
- better alignment with Gordian’s small pure module style

This task is explicitly a behavior-preserving internal refactor unless a small bug fix or seam clarification is required.