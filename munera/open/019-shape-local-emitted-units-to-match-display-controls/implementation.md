2026-04-24
- Task created from an incorrectly inlined plan entry in `munera/plan.md`.
- Source content was preserved and normalized into the required Munera task directory structure.
- Chosen task id: `019-shape-local-emitted-units-to-match-display-controls`.
- Existing sequence suggests this follows closed task `018-add-failure-thresholds-to-complexity-and-local`.

2026-04-24 — design rationale
- Refined the task after reviewing the current `local` implementation seams in `src/gordian/local/report.clj`, `src/gordian/main.clj`, and `src/gordian/output/local.clj`.
- The key design pressure is that `gordian local` currently has two public-facing unit populations with different semantics:
  - top-level `:units` on the report
  - shaped units under `:display :units`
- This creates a user-visible mismatch because text/markdown render from `:display`, while EDN/JSON expose top-level `:units`.
- The task therefore focuses on refining the public report contract, not merely moving fields around.
- A critical constraint is that `local-cmd` currently passes top-level `:units` into enforcement evaluation.
- That means any change to emitted top-level `:units` must first decouple enforcement from finalized emitted output, otherwise `--fail-above` would accidentally start evaluating only the shaped subset.
- The refined design therefore prefers:
  - canonical full analyzed units retained at a pre-finalization seam
  - top-level emitted `:units` changed to the shaped user-requested list
  - enforcement explicitly evaluated against canonical units
  - summary fields such as `:max-unit`, namespace rollups, and project rollup kept on an explicit and reviewable basis
- The design intentionally avoids exposing a new public `:canonical-units` field unless implementation pressure makes that necessary, because doing so would preserve the very consumer-facing complexity this task is trying to reduce.
- The remaining open implementation choice is how much of `:display` survives after the change:
  - ideal outcome: remove it entirely
  - acceptable fallback: retain only narrowly justified remnants rather than the current split unit contract
- The task plan was strengthened to lock the current mismatch and desired semantics in tests before implementation, then separate enforcement from emitted `:units`, then reshape the finalized report contract, then converge formatters/docs/fixtures.

2026-04-24 — ambiguity review resolution
- Reviewed the task for ambiguity and tightened the design/plan/steps to remove the highest-risk open interpretations.
- Resolved that this task changes the finalized `gordian local` report map itself, not only JSON/EDN renderer behavior.
- Resolved that top-level `:units` on the finalized report is the shaped public unit list after `--min`, `--sort`, and `--top`.
- Resolved that `:max-unit` remains canonical: it continues to refer to the maximum unit over the full analyzed population even when that unit is absent from shaped top-level `:units`.
- Resolved that namespace rollups remain canonical by default; any different shaping rule would need to be explicitly documented and test-covered rather than decided implicitly during implementation.
- Resolved that `:display` must not survive as a duplicate unit-list carrier; if any `:display` structure remains, it must have a narrowly defined non-unit responsibility.
- Resolved that summary-basis clarity is required behaviorally and in tests/docs, but the task does not require adding new payload-level basis metadata unless implementation pressure makes that necessary.
- Clarified that the authoritative documentation surfaces for this task are `README.md` and scoped help for `local`.
- Clarified that Phase 1 should add final-state tests for the desired contract, not merely temporary characterization tests unless such tests are useful during implementation.

2026-04-24 — design/plan review tightening
- Incorporated follow-up review feedback from the `munera-task-design` pass.
- Tightened the acceptance criteria to state explicitly that `:project-rollup` remains canonical over the full analyzed unit population rather than shaped top-level `:units`.
- Tightened the plan and steps so Phase 1 treats current-state characterization tests as optional scaffolding, while final-state contract tests remain the primary requirement.
- This leaves the task with one explicit canonical summary basis for:
  - `:max-unit`
  - namespace rollups
  - `:project-rollup`
- The task is now intended to be implementation-ready without further semantic interpretation work.
