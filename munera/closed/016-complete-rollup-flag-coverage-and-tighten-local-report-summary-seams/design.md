Complete the follow-up work identified in the Munera review of task 015 by adding full opt-in rollup flag coverage for `gordian complexity` and `gordian local`, and by tightening the small remaining summary-seam duplication around omitted project rollups.

## Intent

- close the remaining gap between task 015’s acceptance criteria and its current test coverage
- increase confidence that `--namespace-rollup` and `--project-rollup` behave correctly in all independent combinations for both commands
- make the formatter summary behavior around omitted project rollups slightly clearer and less duplicative without redesigning the reporting model
- finish the follow-up review work before task 015 is closed

## Problem statement

The review of task 015 found the implementation to be sound and shippable, but identified two follow-up items:

1. the acceptance criteria called for tests covering all independent rollup combinations for both commands:
   - default unit-only
   - namespace-only
   - project-only
   - both rollups

   The current suite covers default omission well, but does not yet fully lock the namespace-only / project-only / both combinations for both `complexity` and `local`.

2. the output formatters now compute some summary counts directly from units when `:project-rollup` is absent. This is reasonable, but the behavior is duplicated and slightly implicit in formatter code.

Neither issue is a correctness failure in the shipped implementation, but together they leave a small confidence and code-shape gap relative to the review feedback.

## Scope

### In scope

- add focused tests for `gordian complexity` covering:
  - namespace-only rollup opt-in
  - project-only rollup opt-in
  - both-rollups opt-in
- add focused tests for `gordian local` covering:
  - namespace-only rollup opt-in
  - project-only rollup opt-in
  - both-rollups opt-in
- cover both machine-readable and human-readable behavior where appropriate
- verify that `:options` metadata and section presence stay coherent in each combination
- tighten the small summary fallback seam so summary-count derivation for omitted project rollups is explicit and easy to review
- keep the implementation narrow and aligned with the task 015 design

### Out of scope

- changing rollup flag names or semantics
- reworking the canonical report model introduced in task 015
- introducing a generic shared reporting framework
- changing sort / top / min / bar semantics
- revisiting unrelated docs unless a tiny consistency correction is needed

### Explicitly deferred

- broader output formatter deduplication across commands
- generalized section-selection abstractions
- broader schema cleanup beyond what is required to keep this follow-up accurate

## Acceptance criteria

- tests explicitly cover namespace-only, project-only, and both-rollups modes for `gordian complexity`
- tests explicitly cover namespace-only, project-only, and both-rollups modes for `gordian local`
- those tests verify both:
  - machine-readable section presence/absence
  - human-readable or markdown section rendering where appropriate
- `:options :namespace-rollup` and `:options :project-rollup` are asserted in the relevant combinations
- summary-count fallback behavior when `:project-rollup` is absent is explicit and reviewable, not ad hoc
- no command semantics change from task 015
- full suite passes

## Minimum concepts

- **rollup combination coverage** — explicit testing of each independent rollup-flag state
- **namespace-only mode** — `--namespace-rollup` true, `--project-rollup` false
- **project-only mode** — `--namespace-rollup` false, `--project-rollup` true
- **both-rollups mode** — both flags true
- **summary fallback seam** — the logic that derives top-line summary counts when project rollup data is intentionally omitted

## Design direction

Prefer a narrow confidence-and-shape follow-up.

This task should not reopen the design of task 015. The intended work is:
1. complete the missing test matrix
2. make the summary fallback seam explicit and locally reviewable
3. stop there

The preferred outcome is stronger confidence and slightly cleaner shape, not another abstraction pass.

## Possible implementation approaches

### Approach A — focused tests plus tiny helper extraction (preferred)

- add the missing rollup combination tests
- extract a tiny helper in each output namespace, or a tiny shared helper, for summary counts when project rollup is absent
- keep all command/report semantics unchanged

Pros:
- directly addresses the review feedback
- low risk
- small and easy to review

Cons:
- may leave some broader formatter duplication in place

### Approach B — tests only

- add the missing rollup-combination tests
- leave summary fallback logic as-is

Pros:
- smallest diff

Cons:
- leaves the code-shape feedback unaddressed

### Approach C — broader formatter consolidation

- extract more shared structure across local and complexity output formatters

Pros:
- more reuse

Cons:
- too large for the feedback addressed here
- higher regression risk

## Architectural guidance

Follow existing Gordian preferences:
- keep canonical report shaping in report/finalizer layers
- let formatters render what the report contains
- keep any fallback-summary helper tiny and obvious
- prefer explicit tests over broader abstraction

Avoid:
- moving section-presence policy back into formatters
- adding a generalized output framework for a small follow-up
- changing task 015 semantics while doing confidence cleanup

## Risks

- tests could become overly coupled to exact formatter wording rather than section semantics
- a cleanup pass could accidentally reintroduce placeholder rollups or alter summary semantics
- the task could drift into a broader refactor of output namespaces

## Mitigations

- test section presence/absence and key invariants rather than brittle full-output snapshots
- keep any summary helper tiny and behavior-preserving
- stop once the review feedback is satisfied

## Done means

The task is done when the missing namespace-only / project-only / both-rollups test coverage exists for both commands, the summary fallback seam is explicit and reviewable, and the implementation retains the exact semantics shipped in task 015 with full-suite validation.
