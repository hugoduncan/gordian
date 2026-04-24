Refine `gordian local` so the finalized report map exposes shaped top-level `:units`, while enforcement and canonical summaries continue to use the full analyzed unit population.

## Intent

- make `--top`, `--min`, and `--sort` behave consistently across human-readable and machine-readable `gordian local` output
- reduce surprise for EDN/JSON consumers by making top-level `:units` represent the units the user actually asked to see
- preserve the internal canonical full-unit population where it is needed for enforcement and canonical summaries
- simplify the public `local` output contract without weakening correctness

## Problem statement

`gordian local` currently has two different unit populations in play:
- canonical top-level `:units` on the finalized report
- shaped display units under `:display :units`

This creates a visible mismatch:
- text and markdown render from `:display :units`
- EDN and JSON expose top-level `:units`
- so `--min`, `--sort`, and `--top` visibly affect text/markdown output, but do not visibly affect top-level machine-readable `:units`

That makes machine-readable output less intuitive. For example, a user can ask for `--top 1` and still receive many units in top-level `:units`.

The current implementation also relies on the top-level `:units` field for enforcement in `local-cmd`, so changing top-level emitted `:units` naively would break the invariant that `--fail-above` evaluates the full canonical population.

There is a second basis distinction to preserve explicitly:
- `:max-unit`, `:project-rollup`, and namespace rollups are conceptually based on the full analyzed population
- display shaping is a presentation concern

So the real problem is not merely “move data around”. It is to redesign the finalized local report contract so that:
- top-level `:units` on the finalized report matches display shaping
- enforcement still evaluates canonical pre-display units
- full-population summary fields keep a clear and reviewable basis
- output consumers no longer need to understand an unnecessary exposed `:display` split for the primary unit list

## Scope

### In scope

- change the finalized `gordian local` report map so top-level `:units` respects `--min`, `--sort`, and `--top`
- preserve `--fail-above` evaluation against the full analyzed unit population
- preserve coherent basis semantics for `:max-unit`, `:project-rollup`, and namespace rollups
- remove `:display` entirely or narrow it to only the pieces still needed after the change
- update text, markdown, EDN, and JSON behavior to converge on the refined finalized-report contract
- update `README.md`, scoped help for `local`, tests, and fixtures to describe the refined contract accurately

### Out of scope

- changing LCC scoring, evidence extraction, findings, calibration, or analyzable-unit extraction
- changing `complexity` behavior
- changing enforcement threshold semantics
- redesigning all Gordian report shapes into a generalized framework

## Acceptance criteria

- `bb gordian local --source-only --min total=5 --top 1 --sort total --json` emits at most one item in top-level `:units`
- `--min`, `--sort`, and `--top` affect top-level machine-readable `:units` in the same visible way they affect text/markdown unit rendering
- this task changes the finalized `gordian local` report map itself: top-level `:units` on the finalized report becomes the shaped emitted/public unit list
- `gordian local --fail-above ...` still evaluates thresholds against the full analyzed unit population, not the shaped emitted `:units`
- `:max-unit` continues to refer to the maximum unit over the full canonical analyzed population, even when that unit is not present in shaped top-level `:units`
- namespace rollups remain computed from the full canonical analyzed unit population unless an explicit namespace-rollup shaping rule is separately documented and test-covered
- `:project-rollup` continues to be computed from the full canonical analyzed unit population, not shaped top-level `:units`
- `:display` must not retain a duplicate unit population after this task; if any `:display` structure remains, it must have a narrowly defined non-unit responsibility
- tests cover both shaped emitted output and canonical enforcement behavior
- obsolete tests/fixtures asserting separate canonical top-level `:units` vs `:display :units` behavior are updated or removed

## Minimum concepts

- **canonical unit population** — the full analyzed local units before display shaping
- **emitted unit population** — the top-level `:units` on the finalized report, exposed in EDN/JSON and used as the primary public unit list
- **display shaping** — `--min`, `--sort`, and `--top`
- **enforcement population** — the full canonical population used by `--fail-above`
- **summary basis** — the population from which `:max-unit`, namespace rollups, and project rollups are computed

## Possible implementation approaches

### Approach A — keep canonical units internal at command execution seams, emit shaped `:units` publicly (preferred)

- keep `local/rollup` producing canonical units
- compute emitted shaped `:units` during finalization
- pass canonical units separately to enforcement in `local-cmd`
- keep full-population summary fields derived from the canonical units
- remove `:display` if it becomes redundant

Pros:
- public output contract becomes simple
- enforcement correctness remains explicit
- minimal semantic leakage of internal canonical/display distinction

Cons:
- requires touching both `gordian.local.report` and `gordian.main/local-cmd`

### Approach B — emit shaped `:units` and retain canonical units in a separate explicit internal/public field

- top-level `:units` becomes shaped
- a separate field such as `:canonical-units` or `:analysis/units` preserves the full population
- enforcement reads from that explicit canonical field

Pros:
- explicit basis
- lower risk during transition

Cons:
- keeps more payload complexity than desired
- exposes internal distinction to consumers again

### Approach C — keep current payload and only change JSON/EDN renderers

Pros:
- smaller diff

Cons:
- weakens canonical contract consistency
- leaves top-level report shape internally confusing
- does not really solve the public data-model mismatch

## Architecture guidance

Follow existing Gordian preferences:
- canonical analysis first
- presentation shaping explicit
- enforcement must use canonical pre-display data
- emitted payloads should be understandable without internal implementation knowledge
- summary-basis clarity is required behaviorally and in tests/docs; this task does not require adding new payload-level basis metadata unless implementation pressure makes it necessary

Current architecture to respect:
- `local/rollup` builds canonical units and summaries
- `local/finalize-report` currently adds options, scope, and `:display`
- `output.local` currently renders units from `:display`
- `local-cmd` currently uses top-level `:units` for enforcement, so this seam must change if top-level emitted `:units` becomes shaped

Avoid:
- silently letting enforcement depend on the shaped subset
- leaving summary-field basis ambiguous after the change
- preserving `:display` solely out of inertia
- widening the task into a generalized reporting abstraction

## Risks

- changing top-level `:units` could accidentally make enforcement evaluate the wrong population
- project/namespace summaries could end up mixing canonical and shaped bases inconsistently
- some existing machine-readable tests or fixtures may implicitly rely on the current split model

## Mitigations

- add tests that lock enforcement against canonical full units before or alongside the payload change
- make summary-basis expectations explicit in tests
- update output and docs together so one refined contract ships coherently

## Done means

The task is done when the finalized `gordian local` report map exposes shaped top-level `:units`, enforcement still evaluates the full canonical analyzed population, `:max-unit`, namespace rollups, and `:project-rollup` retain explicit and coherent canonical basis semantics, `:display` no longer duplicates the unit list, and docs/tests/output all converge on that refined contract.
