Clarify and tighten the remaining semantic and reviewability issues in the `gordian local` subsystem after tasks 007, 008, and 009.

## Intent

- resolve the main follow-up items from the post-009 Munera task review without reopening the whole local-analysis design
- make the remaining surprising LCC semantics explicitly justified, narrowed, or renamed so the implementation is easier to explain and trust
- improve the explicitness of the internal evidence model where reviewability is still weaker than desired
- preserve the successful `gordian local` command/report/output surface while tightening semantic edges and code shape

## Problem statement

The combined 007+008+009 work is successful and should remain closed, but review found several follow-up issues worth addressing:

- sentinel counting semantics are broader/surprising and may over-count shape burden
- dependency and working-set semantics differ between top-level pipelines and branch-local pipelines because the current main-path model does not extend into branch bodies
- inner evidence record shapes (`step`, `branch-region`, `program-point`) are still only partially explicit, so some reviewability gains from 009 stop at the top-level evidence envelope
- `src/gordian/local/common.clj` is a useful seam but risks becoming a new policy bucket
- there may be small dead-code / cleanup leftovers after the refactor

These are not failures of the feature. They are precision and maintainability follow-ups.

## Scope

### In scope

- clarify, narrow, or rename/document sentinel-count semantics so the rule is intentional and reviewable
- make branch-local dependency semantics explicit and consistent enough to review:
  - either preserve the current asymmetry and document it cleanly
  - or refine the evidence model so branch-local opaque chains have explicit representation
- document or normalize the inner evidence record shapes for:
  - main-path steps
  - branch regions
  - working-set program points
- reduce policy concentration in `gordian.local.common` where this materially improves local cohesion
- remove small dead-code / cleanup leftovers discovered during the work
- add tests that lock the intended semantics chosen for the follow-ups

### Out of scope

- redesigning `gordian local` CLI or output
- redoing 007/008/009 from scratch
- compare/gate integration for local metrics
- regularity-burden implementation
- broad semantic expansion beyond the reviewed follow-up items
- introducing project-configurable semantic registries

### Explicitly deferred

- larger local-metrics redesign
- configurable transparency/effect registries
- full branch-body path-model redesign beyond what is needed to clarify current semantics

## Acceptance criteria

- sentinel-count behavior is no longer surprising-by-default:
  - either semantics are narrowed to the intended rule
  - or current semantics are explicitly named/documented/tested to match their true meaning
- branch-local opaque/helper semantics are explicit and defended by tests
- `step`, `branch-region`, and `program-point` shapes are documented enough to review without reading all helper internals first
- `gordian.local.common` has clearer responsibility boundaries, or its remaining mixed role is intentionally small and stable
- small dead/private unused leftovers are removed
- `gordian local` user-facing command/report behavior remains intact unless a tiny semantics-driven wording adjustment is justified
- full suite passes

## Minimum concepts

- **sentinel-bearing form** — the exact thing the current shape rule counts, if broader than a sentinel-return branch
- **branch-local opaque chain** — an opaque helper/pipeline sequence inside a branch body that may or may not participate in the main-path model
- **inner evidence shape** — one canonical map shape such as `step`, `branch-region`, or `program-point`
- **policy bucket** — a shared namespace collecting too many semantic registries or ownerships

## Design direction

Prefer a narrow follow-up that clarifies semantics and improves reviewability without disturbing the good command/report architecture.

Sequence the work from most user-meaningful ambiguity to least:
1. sentinel semantics
2. branch-local dependency semantics
3. inner evidence shape documentation/normalization
4. `common.clj` cohesion cleanup
5. minor dead-code cleanup

## Possible implementation approaches

### Approach A — semantic clarification with minimal code movement

- keep current behavior where defensible
- rename/document rules and add tests
- smallest diff

Pros:
- low risk
- behavior stability

Cons:
- may preserve awkward semantics that would be better slightly refined

### Approach B — targeted semantic cleanup plus evidence-shape tightening (preferred)

- refine the most confusing rules where the current implementation is not self-explanatory
- add explicit constructors/comments/docstrings for inner evidence shapes
- move only obviously misplaced policy sets out of `common.clj`

Pros:
- best balance of clarity and stability
- directly addresses the review items

Cons:
- requires a few judgment calls on semantics

### Approach C — deeper path-model redesign

Rejected unless the narrow cleanup proves impossible. This follow-up should not reopen local-analysis architecture wholesale.

## Architectural guidance

Follow the current post-009 shape:
- keep `gordian.local.evidence` as a thin façade
- keep burden formulas in `burden.clj`
- keep findings in `findings.clj`
- prefer concern-local helpers over new generic frameworks
- improve inner data-shape visibility with small explicit constructors/helpers/comments rather than large abstraction layers

Avoid:
- moving semantic scoring back into evidence extractors
- introducing cycles among local-analysis namespaces
- expanding `common.clj` while trying to fix it

## Risks

- small semantic cleanups may perturb scores in subtle ways
- over-documenting without clarifying ownership could add noise rather than clarity
- branch-local dependency work could drift into a deeper control/path-model redesign

## Mitigations

- write tests before or with each semantic decision
- keep changes scoped to the specific review items
- preserve the command/report surface unless a wording-only adjustment is clearly warranted

## Done means

The task is done when the remaining post-review semantic ambiguities in `gordian local` are clarified or tightened, the inner evidence model is easier to review, minor cohesion/cleanup issues are addressed, and the subsystem remains architecturally aligned with Gordian’s small pure-module style.
