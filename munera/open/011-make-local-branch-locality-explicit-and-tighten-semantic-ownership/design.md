Make branch locality explicit in `gordian local` step evidence and tighten the remaining semantic ownership boundaries in the local-analysis core after tasks 007–010.

## Intent

- address the approved follow-up review items from the combined 007+008+009+010 review without reopening the feature line
- replace indirect branch-locality inference with a direct, reviewable invariant in step evidence
- reduce the chance that `gordian.local.common` becomes a long-term semantic policy bucket
- preserve the successful `gordian local` command/report/output surface while slightly improving local code shape and evidence clarity

## Problem statement

The combined 007–010 work is good and should remain closed, but the review identified a few residual implementation-shape issues worth tightening:

- branch locality is currently inferred indirectly from `:active-predicates`, which conflates predicate depth with branch-locality and makes the invariant harder to review
- `gordian.local.common` still mixes syntax helpers with several semantic registries and remains the likeliest future concentration point
- `gordian.local.shape` is cohesive but dense, so any future growth risks recreating a local semantic knot
- `gordian.local.evidence` is thin enough now, but should not drift back toward inline semantic assembly density

These are not feature failures. They are implementation-shape hardening items intended to keep the local-analysis subsystem easy to reason about as it evolves.

## Scope

### In scope

- make branch locality explicit on step records, rather than inferring it indirectly from `:active-predicates`
- update dependency and working-set logic to consume the explicit branch-locality field
- add tests that lock the branch-locality invariant directly
- reduce semantic policy concentration in `gordian.local.common` where this can be done cleanly without churn
- perform a small code-shape pass on `gordian.local.shape` and `gordian.local.evidence` to keep responsibility boundaries clear
- preserve user-facing `gordian local` command/report/output behavior unless an internal-only wording or comment adjustment is warranted

### Out of scope

- redesigning `gordian local` CLI or output
- redoing 007–010 from scratch
- regularity-burden
- compare/gate integration for local metrics
- a full branch-body path model redesign
- project-configurable semantic registries

### Explicitly deferred

- larger local-metrics redesign
- richer path-sensitive branch modeling
- broader refactors outside `gordian.local.*`

## Acceptance criteria

- step evidence includes an explicit branch-locality field with a clear invariant
- dependency and working-set logic no longer rely on `:active-predicates` as a proxy for branch locality
- tests explicitly lock the branch-locality behavior at the step/evidence level
- `gordian.local.common` has slightly clearer ownership boundaries, or a deliberate documented reason is established for what remains there
- no user-visible regression in `gordian local` command/report behavior
- full suite passes

## Minimum concepts

- **branch-local step** — a step occurring inside a branch body rather than on the top-level tracked path
- **branch-locality invariant** — the explicit rule determining when a step is marked branch-local
- **semantic ownership boundary** — the namespace/module that should own a given semantic registry or rule helper
- **policy bucket** — a shared namespace collecting too many unrelated semantic registries

## Design direction

Prefer a narrow hardening follow-up.

Sequence the work from most semantically important to least:
1. make branch locality explicit in step evidence
2. migrate downstream consumers to the explicit field
3. add/adjust tests around the invariant
4. reduce obvious semantic concentration in `common.clj`
5. do a final code-shape pass on `shape.clj` / `evidence.clj` only if needed

## Possible implementation approaches

### Approach A — minimal explicitness upgrade (preferred)

- add `:branch-local?` to step maps in `gordian.local.steps`
- switch dependency/working-set consumers to that field
- leave `:active-predicates` for its original meaning only
- move only clearly misplaced semantic registries out of `common.clj`

Pros:
- directly addresses the review item
- low risk
- preserves current behavior while improving reviewability

Cons:
- does not fully redesign path modeling

### Approach B — broader step/evidence schema cleanup

- revisit more of the step schema while adding explicit branch locality
- potentially split more helpers out of `shape.clj`

Pros:
- could produce slightly cleaner internals

Cons:
- unnecessary scope expansion for the current review items

### Approach C — deeper path-model redesign

Rejected unless the narrow hardening proves impossible. The review did not call for reopening the local path model.

## Architectural guidance

Follow the post-010 shape:
- keep `gordian.local.evidence` thin
- keep burden formulas in `burden.clj`
- keep findings/report roles unchanged
- prefer small concern-local helpers over generic frameworks
- keep step/evidence invariants visible near their constructors

Avoid:
- reintroducing indirect semantic coupling between evidence producers and burden formulas
- growing `common.clj` while trying to fix it
- broad namespace churn without a clear ownership improvement

## Risks

- a branch-locality change could subtly perturb helper/working-set counts
- moving registries out of `common.clj` could create naming churn without much payoff
- the task could expand into another generalized refactor

## Mitigations

- add/adjust focused tests before or with each semantic ownership change
- keep branch-locality semantics behavior-preserving unless a clearer invariant requires a tiny, justified adjustment
- only move semantic registries when the ownership improvement is obvious

## Done means

The task is done when branch locality is represented explicitly in step evidence, downstream consumers use that invariant directly, local semantic ownership boundaries are slightly tighter, and the `gordian local` subsystem remains architecturally aligned with Gordian’s small pure-module style.
