Refine `gordian local` output so emitted `:units` always reflects display shaping.

## Intent

- make `--top`, `--min`, and `--sort` more useful and less surprising for machine-readable consumers of `gordian local`
- align emitted `local` output semantics more closely with `complexity` where reasonable
- preserve enforcement evaluation against the full canonical analyzed population
- simplify the local payload by removing or reducing the exposed canonical-vs-display split

## Problem

`gordian local` currently emits two unit populations: canonical top-level `:units` and shaped `:display :units`.

This creates friction for machine-readable consumers:
- `--top` is less useful in JSON/EDN because top-level `:units` does not reflect the visible shaped set
- consumers must understand an extra payload distinction that `complexity` does not expose the same way
- the emitted payload carries an internal canonical/display boundary that may be useful for implementation but adds consumer burden

The desired behavior is:
- top-level emitted `:units` reflects `--min`, `--sort`, and `--top`
- enforcement still evaluates the full analyzed unit population rather than the display-shaped subset
- `:display` is removed or simplified unless still needed for a narrowly justified reason
- text/markdown/EDN/JSON/docs/tests converge on the same visible semantics

## Scope

### In scope

- change `local` so top-level emitted `:units` respects `--min`, `--sort`, and `--top`
- preserve enforcement evaluation against the full canonical analyzed population
- remove or simplify `:display` in `local` output
- update text/markdown/EDN/JSON behavior, tests, fixtures, and README/docs to match
- align `local` output semantics more closely with `complexity` where reasonable

### Out of scope

- changing LCC scoring, findings, calibration, or analyzable-unit extraction
- changing `complexity` behavior
- changing enforcement threshold semantics

## Acceptance criteria

- `bb gordian local --source-only --min total=5 --top 1 --sort total --json` emits at most one item in top-level `:units`
- `--min`, `--sort`, and `--top` affect machine-readable `local` output in the same visible way they affect text/markdown output
- enforcement still evaluates the full analyzed unit population, not the display-shaped subset
- tests cover both shaped emitted output and canonical enforcement behavior
- obsolete tests/fixtures asserting separate canonical `:units` vs `:display :units` behavior are updated or removed

## Minimum concepts

- canonical analysis population
- emitted/report payload
- display shaping (`--min`, `--sort`, `--top`)
- enforcement population

## Implementation approaches

### Preferred

- make `local/finalize-report` shape top-level `:units`, mirroring `complexity` output semantics
- keep canonical units local to command execution for enforcement, rather than exposing them in the emitted payload
- drop `:display` entirely unless still needed for rollup presentation or another narrowly justified output seam

### Alternative

- keep `:display` only for backward compatibility during a transition, but make top-level `:units` the shaped population
- this is less clean and should only be used if needed to reduce migration pain

## Architecture guidance

- follow the existing separation between analysis and report shaping
- keep enforcement based on pre-display canonical units, as already done in `local-cmd` and `complexity-cmd`
- prefer canonical implementation simplicity plus emitted payload clarity over preserving an exposed internal distinction that adds consumer burden
