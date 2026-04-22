Clarify the boundary between canonical `gordian local` analysis data and display-shaped/truncated presentation data, and clean up the small remaining implementation-shape residue identified in the combined 007+008+009+010+011 review.

## Intent

- preserve the successful `gordian local` analysis/report feature while making the report shape easier to reason about and safer for downstream consumers
- stop mixing canonical analysis results with display-only filtering/truncation state in `gordian.local.report`
- remove small residue that weakens confidence in the local subsystem’s internal shape
- keep the task narrow and code-shape focused, not another semantics redesign

## Problem statement

The combined 007–011 work is strong and approved, but review identified a remaining report-shape issue:

- `gordian.local.report/finalize-report` currently rewrites `:units` into the display subset while leaving `:project-rollup` and `:max-unit` based on the full analyzed unit set
- this is intentional from a UX perspective, but it blurs the semantic boundary between canonical analysis data and presentation-oriented slices
- the result is a top-level report map where some fields describe the full analysis basis while others describe the filtered/truncated display basis
- this makes the payload slightly harder to reason about, more fragile for future machine consumers, and less aligned with Gordian’s preference for canonical data first and formatter/view shaping second

Review also found small cleanup/future-shape items:
- `metric-name-order` in `gordian.local.report` appears unused
- `gordian.local.shape` remains dense and should not absorb unrelated work unless a very small seam falls out naturally during the report-shape cleanup

These are not feature failures. They are report-boundary and code-shape hardening items.

## Scope

### In scope

- separate canonical `gordian local` analysis payload from display-shaped subsets/sort/truncation state
- ensure the final report shape makes it explicit which fields are full-analysis basis vs display basis
- preserve current user-visible text/markdown/EDN/JSON behavior unless a small, well-justified machine-readable clarification is needed
- remove obvious residue such as unused local declarations related to the current report shape
- add/update tests that lock the canonical-vs-display boundary clearly
- make only very small adjacent shape improvements if they directly support the report-boundary clarification

### Out of scope

- redesigning `gordian local` CLI or metric semantics
- revisiting burden formulas or evidence semantics
- broader refactors across unrelated Gordian commands
- a proactive split of `gordian.local.shape` without a concrete need arising from this task
- compare/gate integration for local metrics

### Explicitly deferred

- larger local report/presentation framework unification across commands
- deeper `shape.clj` decomposition unless a future task justifies it
- broader machine-readable schema normalization beyond what this task needs

## Acceptance criteria

- canonical analyzed units remain available as canonical analysis data after final report shaping
- display filtering/truncation for `gordian local` is represented explicitly rather than by overwriting the canonical unit collection without annotation
- `:project-rollup`, `:namespace-rollups`, `:max-unit`, and any display-specific unit subset have a clear and reviewable basis
- text and markdown output remain behaviorally stable unless a tiny wording clarification is justified
- EDN/JSON remain coherent and easier for downstream consumers to reason about
- unused `metric-name-order` residue is removed or given a real role
- tests cover the canonical-vs-display distinction
- full suite passes

## Minimum concepts

- **canonical local report** — the complete analysis result before display-only filtering, sorting, or truncation
- **display subset** — the unit sequence shown after applying `--min`, `--sort`, and `--top`
- **report basis** — the population from which a rollup or summary field is computed
- **presentation shaping** — sorting/filtering/truncation decisions made for output convenience rather than core analysis meaning

## Design direction

Prefer a narrow refactor that makes report basis explicit.

Sequence:
1. identify the canonical local report shape
2. identify the display-shaped unit slice and where it should live
3. adjust formatters/output wiring to consume the explicit display slice or explicit basis metadata
4. remove small residue such as unused declarations
5. add tests that lock the distinction

The preferred outcome is not a larger framework. It is a slightly clearer local report contract.

## Possible implementation approaches

### Approach A — add explicit display keys on top of canonical report (preferred)

- keep canonical `:units` as the full analyzed set
- add explicit display-oriented keys such as `:display/units` or a small nested display map
- keep rollups/max-unit on canonical basis unless explicitly documented otherwise
- update output formatters to read from the display slice where appropriate

Pros:
- minimal semantic risk
- preserves canonical data for machine consumers
- directly addresses the review issue

Cons:
- introduces a small amount of additional report structure

### Approach B — keep overwriting `:units` but add basis metadata everywhere

- retain current shaping behavior
- annotate each top-level section with basis metadata

Pros:
- smaller diff in some code paths

Cons:
- preserves the more confusing shape
- weaker long-term clarity than keeping canonical data intact

### Approach C — defer all shaping to output namespaces

- `local.report` returns canonical data only
- each output path computes filtering/truncation itself

Pros:
- very pure canonical report seam

Cons:
- likely duplicates logic and drifts from existing Gordian patterns
- too much churn for this task

## Architectural guidance

Follow existing Gordian preferences:
- canonical analysis payload first
- presentation shaping explicit and reviewable
- pure report assembly remains separate from rendering
- keep the task narrow and local to `gordian.local.*` / local output wiring

Avoid:
- reintroducing hidden coupling between canonical data and display convenience
- changing user-facing semantics unnecessarily
- turning this into a generalized reporting abstraction task

## Risks

- a report-shape change could accidentally perturb existing output or tests
- adding explicit display keys could create noisy payloads if named poorly
- the task could drift into broader output-schema redesign

## Mitigations

- add focused tests around report basis before or alongside changes
- preserve current formatter behavior while clarifying underlying data shape
- keep naming explicit and local rather than abstract/framework-y

## Done means

The task is done when the `gordian local` report shape clearly separates canonical analysis data from display-shaped output slices, small report residue is removed, downstream reasoning becomes easier, and the user-facing experience remains stable.