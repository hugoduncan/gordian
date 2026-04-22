2026-04-22

Created from the approve-with-followups review of the combined 007+008+009+010 local-analysis implementation.

Trigger:
- review verdict: approve-with-followups
- strongest remaining issue: branch locality is represented indirectly rather than explicitly in step evidence

Why this task exists:
- tasks 007–010 successfully delivered and hardened `gordian local`
- the remaining issues are code-shape and semantic-ownership hardening, not feature failure
- making branch locality explicit should improve reviewability and reduce future coupling risk in dependency and working-set logic

Intended payoff:
- clearer step evidence invariants
- less indirect coupling between step extraction and downstream consumers
- lower risk that `gordian.local.common` becomes the default semantic policy bucket
- a slightly more mutation-friendly local-analysis core

This task should stay narrow. It is a follow-up hardening pass, not a redesign.
