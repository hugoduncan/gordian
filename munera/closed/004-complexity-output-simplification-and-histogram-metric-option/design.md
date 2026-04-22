Refine `gordian complexity` output by removing the `decisions` column from human-readable output and adding an explicit option that selects which metric drives the histogram bars.

## Intent

- simplify the text/markdown complexity presentation now that both `cc` and `loc` are shown directly
- reduce visual clutter from a derived cyclomatic detail that is less important for the default report
- make histogram semantics explicit rather than implicitly coupling them to sort order

## Problem statement

`gordian complexity` currently shows a `decisions` column in human-readable output and chooses histogram bars from the primary sort metric when sorting by `cc` or `loc`, otherwise defaulting to `cc`.

Two UX issues remain:
- `decisions` is derived directly from `cc` (`cc - 1`) and adds width/noise without enough explanatory value in the default report
- histogram selection is implicit, which makes it harder to compare the same report ordered one way but visualised by another metric

The command now has two first-class metrics. The bar metric should therefore be user-selectable in a small explicit way rather than inferred only from sort behavior.

## Scope

### In scope

- remove `decisions` from human-readable text output for complexity units
- remove `decisions` from markdown unit tables for complexity output
- add a complexity CLI option that explicitly selects the histogram metric
- define allowed histogram metrics for v1
- define interaction between `--sort` and the histogram option
- update docs/help/tests for the refined output behavior

### Out of scope

- removing `:cc-decision-count` from machine-readable payloads
- adding metric-selection for the whole command
- adding separate histogram controls for different sections
- redesigning the complexity schema beyond the histogram option metadata
- arbitrary user formulas or multi-metric blended bars

## Non-goals

- do not remove `:cc-decision-count` from EDN/JSON payloads unless a separate task decides that
- do not add per-section histogram settings
- do not introduce a generic charting framework
- do not change the underlying complexity calculations

## Minimum concepts

- **bar metric** — the metric used to size histogram bars in human-readable complexity output
- **display simplification** — removal of derived/default-noise columns from human-readable output only
- **machine-readable preservation** — retaining schema fields that may still be useful to downstream consumers even if no longer shown in the default table

## Decision direction

### Decisions to lock

- `decisions` should be removed from default text and markdown complexity tables
- `:cc-decision-count` should remain in machine-readable payloads in v1
- histogram metric should be selected explicitly by a new option rather than inferred only from sort
- allowed histogram metrics in v1 should be exactly `cc` and `loc`
- when histogram option is absent, default behavior should remain stable and documented

## Precise v1 proposal

### Remove `decisions` from human-readable output

Text unit columns become:
- identity
- `cc`
- `risk`
- `loc`
- bar

Markdown unit columns become:
- unit
- `cc`
- `risk`
- `loc`

`decisions` is removed from human-facing output only.

### Keep `:cc-decision-count` in machine-readable output

Reason:
- it is still semantically useful
- removing it would be a schema contraction unrelated to the visual-output refinement
- downstream consumers may rely on it even if humans no longer need it by default

### Histogram option

Add a new complexity option:
- `--bar cc|loc`

Allowed v1 values:
- `cc`
- `loc`

Semantics:
- applies to human-readable text output only
- controls bars in unit and namespace-rollup sections
- project rollup remains non-bar textual summary as today

### Interaction with `--sort`

`--sort` and `--bar` are independent.

Examples:
- `gordian complexity --sort cc --bar cc`
- `gordian complexity --sort loc --bar loc`
- `gordian complexity --sort ns --bar loc`
- `gordian complexity --sort cc-risk --bar loc`

Default when `--bar` is absent:
- preserve current user expectation by using:
  - `loc` when `--sort loc`
  - otherwise `cc`

This keeps current behavior broadly stable while making cross-metric visual comparison possible when desired.

## CLI examples

```text
gordian complexity
gordian complexity --bar cc
gordian complexity --bar loc
gordian complexity --sort ns --bar loc
gordian complexity --sort cc-risk --bar loc --top 15
```

## Canonical schema direction

Add explicit output-option metadata:

```clojure
{:options {:sort :ns
           :top 20
           :mins {:cc 10}
           :bar :loc}}
```

No change to per-unit machine-readable metric fields is required.

## Human output sketch

```text
UNITS

gordian.main/build-report [arity 2]         14  moderate   22  ██████████████████████
gordian.output/format-report [arity 1]      11  moderate   18  ██████████████████
```

With `--sort ns --bar loc`, ordering is namespace-based while bars still represent LOC.

## Acceptance criteria

- text complexity output no longer shows a `decisions` column
- markdown complexity output no longer shows a `decisions` column
- EDN/JSON complexity payload still includes `:cc-decision-count`
- complexity CLI accepts `--bar cc|loc`
- invalid `--bar` values fail with a clear validation error
- bars in human-readable complexity output follow the selected bar metric
- when `--bar` is absent, default bar behavior is documented and tested
- `:options` metadata records the selected `:bar` metric when provided

## Done means

The task is complete when the command surface, output, and docs converge on:
- simpler human-readable complexity tables without `decisions`
- explicit bar-metric selection via CLI
- preserved machine-readable schema compatibility for `:cc-decision-count`
- clear tests for default and overridden histogram behavior
