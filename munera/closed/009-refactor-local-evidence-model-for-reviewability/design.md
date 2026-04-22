Refactor the `gordian local` evidence/extraction core to improve reviewability, locality, and maintainability after tasks 007 and 008, without changing the shipped command/report semantics.

## Intent

- reduce the responsibility concentration and local cognitive load in `src/gordian/local/evidence.clj`
- preserve the semantic gains from task 008 while reshaping the implementation to better fit Gordian’s usual small-pure-module style
- make the shared evidence model easier to audit against `021` / `021b`
- create clearer seams for future refinement of local-analysis semantics without accidental cross-burden regressions

## Problem statement

Tasks 007 and 008 successfully shipped and converged `gordian local`, but follow-up review found that the implementation quality is uneven.

What is good now:
- the command/report/output surface is good and should remain stable
- burden semantics are materially closer to `021b`
- tests now protect important semantic rules

What is still weak:
- `src/gordian/local/evidence.clj` has become a monolithic semantic utility namespace
- it mixes too many concerns in one place:
  - step extraction
  - flow traversal
  - shape classification
  - branch outcome/variant logic
  - dependency opacity detection
  - mutable-state tracking
  - working-set program-point construction
- intermediate evidence shapes are implicit rather than clearly described
- some semantic rules are reviewable only by reading multiple intertwined helpers rather than one explicit concept seam
- this raises the maintenance risk that future changes to one burden family will perturb another

This is now a code-shape and architecture-fit problem more than a metric-semantics problem.

## Scope

### In scope

- refactor the pure local-analysis internals for reviewability and separation of concerns
- split `src/gordian/local/evidence.clj` if that materially improves local comprehensibility
- make the shared evidence model more explicit and easier to audit
- preserve current command behavior and report shape unless a tiny internal-facing adjustment is clearly justified
- preserve current burden semantics and test behavior unless a change is required to clarify or fix an implementation boundary
- tighten tests where needed to protect refactor seams and edge cases noted in review:
  - branch variant edge cases (`cond`, `condp`, `case`, nilability, keyset differences)
  - sentinel counting edge cases
  - abstraction classification edge cases
- remove small dead-code / cleanup issues discovered in review

### Out of scope

- redesigning `gordian local` CLI or output
- changing LCC headline semantics beyond refactor-driven bug fixes
- compare/gate integration for local metrics
- regularity-burden implementation
- project-specific registries/configuration for transparency/effects
- broad rewrites of unrelated Gordian commands

### Explicitly deferred

- any larger redesign of local metrics beyond internal factoring
- configurable semantic registries
- new end-user features for `gordian local`

## Non-goals

- do not redo task 008’s semantic convergence work from scratch
- do not replace the current evidence model with a speculative framework
- do not broaden this into a generic local static-analysis platform
- do not weaken the current tests just to make the refactor easier

## Acceptance criteria

- responsibility concentration in `src/gordian/local/evidence.clj` is materially reduced
- the refactored local-analysis core follows existing Gordian patterns better:
  - smaller pure namespaces or clearly separated concern groups
  - explicit data flow
  - stable intermediate data shapes
- the shared evidence model is documented enough to be reviewable without reading every helper in detail
- current `gordian local` CLI/report/output behavior remains intact
- existing semantic tests continue to pass
- additional edge-case tests are added for the review-noted weak spots
- obvious cleanup issues from review are addressed (for example unused local constants)
- the refactor reduces the chance that one burden-family change silently affects another

## Minimum concepts

- **shared evidence model** — the canonical intermediate representation used by burden scorers
- **concern-local extractor** — a pure helper/module responsible for one semantic area such as steps, flow, shape, dependency, or program points
- **semantic seam** — a place where one concept can be changed or reviewed without re-reading unrelated concepts
- **behavior-preserving refactor** — internal reshaping that keeps user-visible command/report semantics stable

## Code-shaper constraints

This task should explicitly follow code-shaper goals:
- simplify local reasoning
- reduce multi-responsibility functions/namespaces
- make data-shape boundaries visible
- keep consistent naming and argument order
- avoid clever generic abstraction unless it clearly reduces complexity

In particular:
- prefer a few small explicit modules over one giant utility namespace
- prefer isolated rule helpers over intertwined conditional towers
- keep burden formulas in `burden.clj`, not re-hidden inside extraction code

## Possible implementation approaches

### Approach A — split `evidence.clj` into concern-local namespaces (preferred)

Possible shape:
- `gordian.local.steps`
- `gordian.local.flow`
- `gordian.local.shape`
- `gordian.local.dependency`
- `gordian.local.working-set`
- thin `gordian.local.evidence` façade that assembles the canonical evidence map

Pros:
- best fit with review findings
- strongest local comprehensibility improvement
- easiest future refinement per burden family

Cons:
- more file movement and seam definition work

### Approach B — keep one namespace but introduce clearly separated sections + constructor helpers

Pros:
- smaller diff
- less namespace churn

Cons:
- likely leaves too much responsibility concentration in place
- weaker response to the review finding

### Approach C — full local-analysis subsystem redesign

Rejected unless the lighter refactor proves impossible.
This task should improve shape, not reopen the whole feature design.

## Preferred design direction

Prefer Approach A:
- keep the current external command/report architecture
- split evidence extraction into smaller concern-local pure modules
- keep one thin assembly layer that returns the canonical evidence map consumed by `burden.clj`
- add a short evidence-schema description at the assembly boundary

This best addresses the review without destabilizing the command.

## Architectural guidance

Follow existing Gordian patterns:
- pure namespaces with explicit data flow
- thin façade/assembly namespace when multiple pure concerns need composition
- tests at the pure seams
- stable report assembly/output separation

Specific guidance:
- preserve `units.clj`, `burden.clj`, `findings.clj`, and `report.clj` roles unless a tiny cleanup is warranted
- factor semantic classification helpers next to the concept they support
- isolate working-set program-point construction from unrelated shape/dependency logic
- isolate branch variant logic so it reads directly as a 021b rule implementation
- add a short docstring or schema comment on the canonical evidence payload

Avoid:
- introducing cycles among local-analysis namespaces
- moving burden formulas into extractors
- over-abstracting into a framework-y visitor layer when direct pure functions are clearer

## Risks

- behavior-preserving refactors can still subtly perturb metrics
- splitting helpers may create accidental duplicate traversals or inconsistent naming
- the task may drift into another semantic-convergence task instead of an internal-shape task

## Mitigations

- keep existing semantic tests green throughout
- add edge-case tests before moving fragile rules
- refactor one concern seam at a time
- preserve one canonical evidence assembly boundary

## Done means

The task is complete when the combined 007+008 local LCC work remains behaviorally intact for users, but the internal evidence/extraction code is significantly easier to review, understand, and safely evolve.