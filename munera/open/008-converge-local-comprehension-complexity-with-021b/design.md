Converge the shipped `gordian local` implementation with the normative LCC semantics in `doc/design/021-local-comprehension-complexity.md` and `doc/design/021b-local-comprehension-complexity-computation-rules.md`.

## Intent

- close the semantic gap between the current `gordian local` implementation and the normative LCC design
- preserve the useful shipped command surface while making the metric meaning trustworthy
- refactor the current implementation so burden semantics are encoded explicitly rather than emerging from broad tree-count heuristics
- strengthen tests so they lock the intended 021b rules instead of merely preserving the current approximation

## Problem statement

Task 007 successfully shipped a usable `gordian local` command, but review found that the implementation only partially matches the normative metric semantics.

The main gap is in `src/gordian/local/evidence.clj` and its downstream burden calculations:
- several burden families are computed from coarse whole-tree counts rather than the rule-specific evidence model described in `021b`
- abstraction oscillation is not derived from ordered main-path step labels
- variant/shape burden does not clearly come from branch outcome shape differences
- working-set program points are synthetic and do not clearly match the v1 point model in `021b`
- tests mostly preserve the current heuristic behavior, making future convergence harder

This means the feature is operational, but the semantics are weaker than the design and review language currently imply.

The follow-up task must therefore converge the implementation toward the normative design without discarding the shipped command/UI/report shape that already fits Gordian well.

## Scope

### In scope

- converge `gordian local` burden-family semantics toward the explicit v1 rules in `021b`
- refactor evidence extraction so the evidence model visibly matches the 021b concepts:
  - main-path steps
  - branch regions / branch outcome evidence
  - control forms and nesting depth
  - bindings / rebinding evidence
  - mutable/effect observations
  - shape observations
  - working-set program points
- ensure burden-family scoring consumes that evidence rather than broad proxy counts
- tighten high-confidence finding triggers so they derive from the intended evidence/burden semantics
- add spec-anchored tests that verify the intended v1 semantics burden by burden
- preserve the existing command name, report shape, output modes, and CLI workflow unless convergence requires a small semantics-driven change
- update docs if semantics, caveats, or implementation notes need sharper wording

### Out of scope

- redesigning the `gordian local` command surface
- compare/gate integration for local metrics
- regularity-burden implementation
- project-specific transparency/effect registries
- full semantic analysis beyond conservative v1 rules
- changing unrelated Gordian commands

### Explicitly deferred

- regularity-burden
- compare/gate workflows for local metrics
- richer semantic reconstruction beyond syntax-first conservative v1 rules
- project-specific registries/configuration for helper transparency or effects

## Non-goals

- do not replace the shipped feature with an entirely new architecture
- do not broaden the task into a generic local-analysis framework beyond what this convergence needs
- do not weaken the existing command/output UX that already fits Gordian
- do not overreach into semantic cleverness that violates the deterministic conservative v1 intent

## Acceptance criteria

- burden-family scoring semantics are materially aligned with `021b` for:
  - flow
  - state
  - shape
  - abstraction
  - dependency
  - working-set
- abstraction burden uses an ordered main-path step label sequence, not only level presence
- shape variant burden is derived from branch outcome shape differences, not merely branch-form presence
- working-set uses a clearly identifiable v1 program-point model from `021b`
- evidence extraction is factored so burden-family inputs are explicit and reviewable
- tests include rule-focused cases that lock 021b semantics and anti-double-counting expectations
- existing `gordian local` CLI/output/report shape remains intact unless a semantics-driven adjustment is justified and documented
- the implementation remains pure-core / thin-IO and follows existing Gordian architecture patterns
- docs/state wording no longer overclaim beyond the actual semantics implemented

## Minimum concepts

- **normative evidence** — evidence structures that correspond directly to 021b concepts
- **main-path step sequence** — ordered dominant step labels used by abstraction and related reasoning
- **branch outcome evidence** — branch-local result-shape evidence merged back into unit scope
- **program-point model** — explicit v1 working-set sampling points from 021b
- **semantic convergence** — replacing proxy heuristics with rule-based burden computation while preserving the command/report surface

## Design direction

### Preferred shape: converge in place, not rewrite from scratch

Keep the current command/report/output architecture and refine the pure local-analysis core in place.

Preferred sequencing:
1. sharpen evidence model to match 021b terminology and ownership
2. refactor burden scoring to consume the sharpened evidence explicitly
3. tighten findings to derive from the improved evidence/burden semantics
4. add spec-anchored tests before or alongside each convergence step
5. update docs/state language to match reality

This preserves the delivered value of task 007 while addressing the review findings directly.

### Evidence first, then burdens

Do not start by patching individual burden totals opportunistically.

Instead, first establish an evidence model that supports the intended rules:
- ordered main-path steps
- branch-local outcome evidence
- clear ownership of control/state/shape/dependency signals
- explicit working-set program points

Then rebuild burden computations over that evidence.

### Conservative convergence over maximal cleverness

Where the normative design leaves room, prefer conservative explicit rules.
If a burden rule cannot yet be implemented faithfully, it is better to document a narrow conservative rule than to silently substitute a broad proxy count.

## Possible implementation approaches

### Approach A — patch current heuristics locally

Pros:
- small edits
- fast

Cons:
- likely preserves the current conceptual blur
- risks another round of partial convergence

### Approach B — evidence-model refactor, then burden convergence (preferred)

Pros:
- aligns with the review findings
- best preserves semantic clarity
- makes tests and future refinement easier

Cons:
- more disciplined refactor work up front

### Approach C — full rewrite of `gordian.local.*`

Rejected unless convergence-in-place proves impossible.
The current command/report/output layering is already good and should be preserved.

## Architectural guidance

Follow existing Gordian patterns:
- pure namespaces with explicit data flow
- thin command wiring in `main.clj`
- canonical report assembly in one pure layer
- output formatting separated from scoring
- tests anchored at the pure seams

Specific guidance for this task:
- keep `src/gordian/local/units.clj` unless extraction semantics truly need change
- reshape `src/gordian/local/evidence.clj` into smaller, more explicit evidence producers if needed
- keep `burden.clj` as the place where burden formulas live, not hidden in evidence extraction
- keep `findings.clj` pure and threshold-oriented
- keep `report.clj` responsible for sorting/filtering/truncation/report shaping only

Avoid:
- reintroducing broad whole-tree proxy counting where 021b defines rule-specific evidence
- mixing burden formulas back into evidence extraction
- changing CLI/output just because internals are being converged

## Risks

- convergence work may destabilize reported scores in ways that break current tests/output expectations
- burden families may still bleed into one another if evidence ownership is not made crisp enough
- working-set refinement may become too complex if not bounded by the v1 point model
- the task may sprawl into a larger local-metrics redesign

## Mitigations

- add rule-focused tests before replacing heuristic behavior where possible
- converge one burden family at a time against explicit evidence inputs
- keep working-set bounded to the documented v1 program points
- preserve command/output surface and avoid adjacent feature work

## Done means

The task is complete when `gordian local` still works as shipped, but its evidence model, burden-family scoring, and high-confidence findings are materially aligned with the normative 021b semantics, and the test suite clearly demonstrates that convergence.