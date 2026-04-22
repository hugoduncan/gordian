Make `gordian complexity` and `gordian local` omit namespace and project rollup sections by default, and require explicit independent opt-in flags for each rollup level.

## Intent

- reduce default report noise in the two local-analysis commands by focusing the default output on unit-level hotspots
- make aggregate sections an explicit user choice rather than always-on payload surface
- keep namespace and project rollups independently requestable so users can ask for exactly the summary level they want
- converge the CLI, canonical report shape, and human-readable output semantics across `complexity` and `local`

## Problem statement

`gordian complexity` and `gordian local` currently include namespace and project rollups as part of their standard report shape. Those summaries are useful, but they are not always wanted:

- many local-analysis workflows start with unit hotspot triage
- the rollup sections add visual and machine-readable bulk even when the user only wants per-unit results
- there is no way to ask for namespace rollups without project rollup, or vice versa, if the command treats both as always-on report sections

The desired behavior is:
- default output should focus on units
- namespace rollups should appear only when explicitly requested
- project rollup should appear only when explicitly requested
- the two rollup levels should be independently controllable

This must be done without making the commands inconsistent with each other, and without blurring the distinction between canonical analysis data and display-shaped report sections.

## Scope

### In scope

- define new explicit CLI options for requesting namespace rollups in `gordian complexity`
- define new explicit CLI options for requesting project rollup in `gordian complexity`
- define the corresponding explicit CLI options for `gordian local`
- make the namespace and project rollup options independent for each command
- change default text / markdown / EDN / JSON behavior so rollups are omitted unless requested
- make the canonical report shape explicit about which sections are included vs omitted
- update scoped help, README, practical guide, and any command docs needed for accuracy
- add tests covering default omission, independent opt-in, and interaction with existing display controls

### Out of scope

- redesigning unit extraction or scoring semantics for either command
- changing sort / top / min / bar semantics except where their interaction with optional rollups must be clarified
- changing architectural Gordian commands outside small documentation consistency updates
- introducing a generalized section-selection framework for all commands
- compare/gate integration for local metrics

### Explicitly deferred

- a broader cross-command `--sections` or report-shaping DSL
- user-configurable defaults via project config, unless clearly trivial and already aligned with existing command conventions
- follow-on UX work such as compact summary presets

## Acceptance criteria

- `gordian complexity` omits namespace rollups by default
- `gordian complexity` omits project rollup by default
- `gordian local` omits namespace rollups by default
- `gordian local` omits project rollup by default
- each command exposes one explicit option to include namespace rollups and one explicit option to include project rollup
- namespace and project rollup options can be used independently or together
- text and markdown render only the requested sections
- EDN and JSON omit unrequested rollup sections rather than populating them implicitly
- report/options metadata makes the rollup inclusion choices reviewable
- scoped help and docs show the new default behavior and option names
- tests cover:
  - default unit-only behavior
  - namespace-only opt-in
  - project-only opt-in
  - both rollups opt-in
  - coherence with `--top`, `--sort`, `--min`, and `--bar`
- full suite passes

## Minimum concepts

- **unit section** — the per-unit hotspot rows; remains the default primary output
- **namespace rollup** — per-namespace aggregate summary, now optional
- **project rollup** — whole-project aggregate summary, now optional
- **independent opt-in** — each rollup level requested by its own flag, with no implication that one forces the other
- **report section selection** — command-level choice of which output sections are present in canonical and rendered output

## Design direction

Prefer a narrow, explicit option model over hidden heuristics.

The preferred behavior is:
- units remain the stable default surface
- namespace rollups require an explicit flag
- project rollup requires an explicit flag
- the same conceptual model applies to both `complexity` and `local`

The task should avoid inventing a generic multi-command section framework unless the implementation falls out almost for free. The commands only need a small explicit seam for these two rollup levels.

## Option model direction

Use positive include-style flags rather than negative suppression flags.

Preferred shape:
- `--namespace-rollup`
- `--project-rollup`

Semantics:
- absent => section omitted
- present => section included
- flags are independent
- both present => include both rollup sections

Rationale:
- singular option names match the fact that each flag enables one report section
- `--project-rollup` already has clear singular semantics
- `--namespace-rollup` stays parallel with `--project-rollup` even though the rendered/canonical section contains multiple namespace rows
- this also aligns better with earlier Gordian complexity design language that treated section toggles as singular booleans

This keeps the behavior easy to read in help and scripts and avoids awkward double negatives such as `--no-project-rollup`.

## Canonical report direction

The report should remain coherent when sections are omitted.

Preferred shape:
- keep `:units` as the canonical unit data
- include `:namespace-rollups` only when requested
- include `:project-rollup` only when requested
- record section choices in `:options` or a nearby explicit metadata field

The command should not emit silent placeholder rollup values when those sections were not requested.

## Interaction notes

### Sorting / top / min / bar

Existing unit-display controls should keep their current meaning.

Clarifications to lock:
- `--sort`, `--top`, `--min`, and `--bar` continue to shape unit display behavior as today
- if namespace rollups are requested, any existing namespace-rollup truncation/sorting behavior should remain as-is unless a tiny adjustment is required for consistency
- project rollup remains a single summary section when requested

### Human-readable output

Default `complexity` and `local` output should become more compact:
- summary/header
- units
- findings where applicable (`local`)
- no namespace or project rollup sections unless requested

### Machine-readable output

EDN/JSON should match the requested section set, not a hidden full payload.

## Possible implementation approaches

### Approach A — command-local boolean include flags (preferred)

- add explicit include flags to both commands
- thread boolean section choices into report finalization
- conditionally associate rollup sections in the canonical report
- conditionally render sections in text/markdown

Pros:
- narrow and easy to understand
- minimal CLI churn
- fits the specific user request exactly

Cons:
- duplicates a little report-shaping logic across two local-analysis commands if not factored carefully

### Approach B — generic section-selection map for local-analysis commands

- introduce a shared internal report section model used by both commands

Pros:
- potentially reusable

Cons:
- larger than the request requires
- risks abstracting before there is evidence other commands need it

## Architectural guidance

Follow existing Gordian preferences:
- canonical report first
- command options explicit in the payload
- formatters render what the report actually contains
- keep the change narrow and parallel between `complexity` and `local`

Avoid:
- leaving rollups in the canonical payload when they were not requested
- making one command default to different rollup semantics than the other
- introducing a broad output-section abstraction unless clearly justified by the implementation

## Risks

- changing defaults could surprise existing users/scripts that rely on rollups always being present
- machine-readable consumers may assume `:namespace-rollups` and `:project-rollup` always exist
- the two commands could drift in option naming or omission semantics

## Mitigations

- use the same option names and semantics for both commands
- update help and docs with clear examples of explicit rollup requests
- add focused tests for machine-readable omission semantics
- call out the behavioral change clearly in docs/release notes if appropriate

## Done means

The task is done when `gordian complexity` and `gordian local` both default to unit-focused output, both require explicit independent flags for namespace and project rollups, the canonical payloads and rendered outputs only include requested rollup sections, and the CLI/help/docs/tests all converge on that model.
