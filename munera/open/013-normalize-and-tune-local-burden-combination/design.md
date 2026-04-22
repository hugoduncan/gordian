Normalize and tune the combined `gordian local` burden score so that burden families contribute on a more commensurate basis and the total score better reflects perceived comprehension difficulty.

## Intent

- preserve the successful `gordian local` feature while improving the interpretability and usefulness of the combined LCC total
- reduce accidental domination of the total score by burden families with larger raw numeric ranges, especially dependency
- add a reviewable calibration/tuning seam so future LCC adjustments are explicit rather than ad hoc
- keep raw burden values intact for explanation while making the combined score more semantically balanced

## Problem statement

Empirical inspection of current `gordian local` scores on the gordian codebase shows that the burden families are meaningful, but their raw scales are not commensurate.

Observed behavior:
- `dependency` has the largest and most frequent raw spikes and strongly drives total score for formatter/orchestrator code
- `working-set` and `state` are high-signal but can be comparatively under-expressed in the raw sum
- `abstraction` appears semantically valuable but numerically modest
- balanced multi-family burden profiles can lose to single-family spikes with larger raw ranges

Examples from the current repo analysis:
- `gordian.output.local/format-local` is overwhelmingly dependency-driven in the raw total
- `gordian.scc/tarjan` is clearly hard because of stateful temporal reasoning, but must compete with dependency-heavy units on an incomparable raw scale
- `gordian.main/build-report` looks like a genuine comprehension hotspot with a balanced profile, but the current raw-sum total under-represents that shape relative to high dependency spikes

The current total score is therefore useful but partially conflates:
- semantic contribution of a burden family
- arbitrary raw scale of that family’s formula

We want a better combined score without hiding the raw evidence.

## Scope

### In scope

- add an explicit normalization step for local burden families before combination into the total score
- choose and implement a concrete initial normalization scheme for all six burden families
- keep raw burden values available in the report payload and output explanations
- make the normalization/calibration seam explicit and reviewable in code and report shape
- add distribution summary/calibration support needed to derive normalization scales from the analyzed unit set
- implement an initial weighting policy for the normalized burdens, even if the first pass uses equal weights
- add tests that lock the normalization behavior, report shape, and combined-score determinism
- validate and tune on the gordian codebase using representative watchlist units
- update docs/help/schema where needed so the combined score remains understandable

### Out of scope

- redesigning the underlying evidence extraction formulas for individual burden families
- reworking `gordian complexity`
- cross-project reference corpora or global calibration databases
- making normalization user-configurable in this task unless it falls out trivially and clearly
- compare/gate integration for normalized local metrics
- broad CLI redesign

### Explicitly deferred

- alternative normalization families beyond what is needed to ship one good reviewable scheme
- project-configurable weighting/normalization profiles
- historical or cross-repo calibration baselines
- a broader local-metrics framework spanning other commands

## Acceptance criteria

- the combined local score is no longer a direct raw sum of burden family scores
- normalization of burden families is explicit in implementation and reviewable in tests
- raw burden family values remain available in canonical machine-readable output
- normalized burden family values or the calibration metadata needed to reproduce them are available in canonical machine-readable output
- the report shape makes the basis of the combined score understandable
- initial normalization scales are derived from the analyzed unit population using a documented deterministic rule
- the implementation supports future tuning without hidden constants scattered across the codebase
- representative rankings on gordian improve in the intended direction:
  - stateful algorithms are not drowned out by dependency-heavy formatters
  - balanced multi-family hotspots compete more fairly with single-family spikes
  - dependency-heavy formatters remain high only when justified across the normalized combination
- text/markdown remain readable and do not become overloaded with calibration detail
- full suite passes

## Minimum concepts

- **raw burden** — the current family-specific score as produced by the existing burden formulas
- **normalized burden** — the transformed family-specific value used for combination
- **calibration scale** — the per-family scale parameter derived from the analyzed unit distribution
- **combination weight** — the post-normalization multiplier applied to a family when computing the combined total
- **watchlist** — a small set of representative units used to review ranking behavior during tuning

## Design direction

Prefer a narrow, explicit calibration layer over another burden-semantic redesign.

The preferred shape is:
1. keep existing raw burden calculations unchanged
2. compute per-family distribution summaries from the analyzed unit set
3. derive deterministic per-family scales from those summaries
4. normalize each family with a shared transform shape and family-specific scale parameter
5. combine normalized families with explicit weights
6. preserve raw burdens for explanation and expose enough calibration metadata for downstream reasoning

Preferred initial formula:
- `norm_f(x) = log1p(x / s_f)`
- `total' = Σ w_f * norm_f(x_f)`

Preferred initial scale rule:
- `s_f = p75(non-zero raw values for family f)`
- fallback to median non-zero when data is too sparse
- sparse-family guard to avoid unstable overfitting

Preferred initial weight rule:
- start with equal weights to isolate the effect of normalization
- optionally apply small follow-up tuning weights only if post-normalization review still shows persistent mis-ranking

## Possible implementation approaches

### Approach A — explicit per-report dynamic calibration (preferred)

- compute scales from the current analyzed unit population in `gordian.local.report`
- store calibration metadata in the canonical report
- compute normalized burdens and total from the same report basis

Pros:
- deterministic and local to the analyzed project
- no hidden global state
- easiest to review against current repo behavior
- directly supports future tuning

Cons:
- normalized totals are project-relative rather than globally absolute

### Approach B — fixed built-in scales and weights

- hardcode one scale per burden family
- normalize without looking at the current unit distribution

Pros:
- simpler output and implementation
- easier cross-project comparability

Cons:
- weaker empirical grounding
- likely brittle because current burden families have clearly different distributions
- harder to justify as the burden formulas evolve

### Approach C — percentile-only normalization

- convert each family to a percentile rank and sum weighted percentiles

Pros:
- easy to reason about rank contribution
- strongly limits domination by extreme raw values

Cons:
- loses magnitude information within tails
- less smooth and less explanatory than a continuous transform

## Architectural guidance

Follow existing local-analysis structure:
- keep raw evidence and raw burden formulas in their current concern-local namespaces
- keep report assembly and ranking logic in `gordian.local.report`
- prefer an explicit calibration/normalization seam over burying transforms inside the existing burden formulas
- preserve canonical-data-first style in machine-readable outputs

Avoid:
- scattering normalization constants across multiple namespaces
- hiding calibration rules in formatter code
- removing raw burdens from the report
- turning this into a general framework task

## Risks

- normalization could improve one class of hotspot while making another class less intuitive
- per-project calibration may surprise users who expect exact cross-repo comparability of totals
- exposing too much calibration detail in human-readable output could clutter the UX
- tuning weights too early could mask whether normalization alone solved most of the problem

## Mitigations

- keep raw and normalized burdens both available in machine-readable output
- separate the normalization step from weighting so their effects can be reviewed independently
- validate changes against a fixed watchlist of representative units
- keep text/markdown concise; reserve detailed calibration metadata for EDN/JSON and tests

## Watchlist for tuning

- `gordian.scc/tarjan`
- `gordian.local.evidence/extract-evidence`
- `gordian.main/build-report`
- `gordian.main/diagnose-cmd`
- `gordian.explain/shortest-path`
- `gordian.output.local/format-local`
- `gordian.output.local/format-local-md`

## Done means

The task is done when `gordian local` still exposes the same raw burden evidence, but the combined score is produced through an explicit normalized-and-weighted calibration layer that is deterministic, reviewable, empirically tuned on gordian, and clearly improves the fairness and usefulness of total-score rankings.