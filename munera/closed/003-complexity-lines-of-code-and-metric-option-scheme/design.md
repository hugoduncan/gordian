Add LOC as a second built-in local metric for `gordian complexity`, and use that addition to converge the command, schema, and option model onto a stable metric-qualified interface.

## Intent

- extend the existing local metric command with a simple size metric that helps interpret cyclomatic complexity in context
- avoid painting the CLI and schema into a cyclomatic-only corner before more local metrics arrive
- define stable rules for metric-targeted sorting, thresholds, truncation, and output

## Problem statement

`gordian complexity` currently reports cyclomatic complexity only. Users also want lines-of-code as a complementary local metric for unit, namespace, and project views.

The current command still carries some single-metric history in its option model. In particular, sort and threshold semantics are not yet fully generalised for a command that reports more than one metric. If LOC is added without locking those rules now, the command will accumulate avoidable CLI and schema churn.

A key part of this convergence is to remove cyclomatic-shaped threshold API from the canonical surface. Retaining `--min-cc` would preserve exactly the naming asymmetry this task is trying to eliminate.

LOC belongs in `gordian complexity` rather than a separate command because it is intended as a complementary local-code metric over the same analyzable units and scopes, not as a distinct workflow.

## Scope

### In scope

- define how LOC is measured for the `complexity` command
- add canonical schema fields for LOC alongside existing cyclomatic fields
- define forward-compatible rules for metric-targeted options such as sort and thresholds/filters
- specify output behavior for human, EDN, and JSON forms when both metrics are active
- explicitly remove `--min-cc` from the canonical CLI surface and replace it with generic metric-qualified threshold syntax
- define whether thresholds reshape the report basis or only affect displayed rows
- identify the smallest implementation slice that adds LOC and converges the command surface without building a generic metric framework

### Out of scope

- implementing every future metric now
- replacing `complexity` with separate metric-specific commands
- semantic/source-map-precise physical line accounting beyond a practical v1 definition
- historical trending, gates, or compare integration for local metrics
- a plugin/registry framework for arbitrary metrics

## Non-goals

- do not introduce user-facing metric selection in v1
- do not redesign the analysis unit model
- do not nest per-metric payloads into a generic map-of-maps unless required later
- do not over-abstract for hypothetical metrics beyond cyclomatic and LOC
- do not preserve cyclomatic-shaped threshold flags as parallel long-term API

## Minimum concepts

- **complexity command** — the user-facing local-metrics surface
- **analysis unit** — arity-level top-level executable body, same as the current complexity command
- **active metrics** — fixed v1 metric set of cyclomatic complexity and LOC
- **metric-qualified fields** — compact unit/rollup fields such as `:cc` and `:loc`
- **primary ranking key** — the key named by `--sort`, which drives ordering and therefore `--top`
- **metric-targeted threshold** — a generic threshold expression naming the metric it constrains
- **display filter** — a threshold that affects which unit rows are shown, without changing the underlying rollup aggregates

## Decision summary

### Locked collaboratively

- active metrics are always both metrics in v1: cyclomatic complexity and LOC
- there is no user-facing metric selection flag in v1
- canonical threshold/filter syntax is generic rather than metric-specific
- LOC is measured over the whole reported unit form span, not only the executable body span
- canonical API converges directly to the generic threshold form
- `--min-cc` is removed rather than retained as a compatibility alias
- `--min` shapes only displayed unit rows; namespace and project rollups remain computed from the full analyzed unit set
- preferred implementation shape is report-finalizer-first

## Precise v1 decisions

### Active metrics

Always compute both:
- cyclomatic complexity
- LOC

There is no `--metric` or `--metrics` option in v1.

### LOC definition

LOC = count of non-blank, non-comment physical source lines within the whole reported unit form span.

This is a literal physical-line heuristic, not a reader-semantic comment model.

Rules:
- the counted span runs from the first source line of the reported unit form to the last source line of that same form, inclusive
- blank lines do not count
- comment-only lines do not count
- for v1, a comment-only line is any line matching `^\s*;+.*$`
- lines containing code plus trailing or inline comments do count
- docstring lines inside the reported unit span do count
- multi-line string lines inside the reported unit span count according to the same physical-line rules
- reader-discard forms and metadata receive no special semantic treatment beyond the literal source-line rules above

If current parsing/provenance is insufficient to compute unit form spans reliably, capturing or normalising source spans becomes enabling work within this task.

### Option-targeting model

#### Sort

`--sort` chooses the primary ranking key.

Allowed v1 keys:
- `cc`
- `loc`
- `cc-risk`
- `ns`
- `var`

Tie-break rules:
- `cc` => descending `:cc`, then descending `:loc`, then namespace/var/arity identity
- `loc` => descending `:loc`, then descending `:cc`, then namespace/var/arity identity
- `cc-risk` => descending risk severity, then descending `:cc`, then descending `:loc`
- `ns` => ascending namespace, then descending `:cc`, then descending `:loc`
- `var` => ascending var identity, then descending `:cc`, then descending `:loc`

#### Top

`--top` remains metric-agnostic and truncates after the chosen sort order is applied.

As with the current complexity design:
- `--top` applies independently to displayed units and namespace rollups
- namespace rollup sorting/truncation occurs independently of unit-row filtering
- project rollup remains untruncated

#### Thresholds / filters

Canonical syntax is generic and metric-qualified:
- repeatable `--min metric=value`

Supported v1 threshold metrics:
- `cc`
- `loc`

Examples:
- `gordian complexity --min cc=10`
- `gordian complexity --min cc=10 --min loc=20`

Combination rule:
- if multiple `--min` constraints are supplied, a unit row must satisfy all of them to be displayed

Threshold application semantics:
- `--min` applies only to displayed unit rows
- namespace rollups are computed from the full analyzed unit set, not the thresholded display subset
- project rollup is computed from the full analyzed unit set, not the thresholded display subset
- displayed unit rows are the only section filtered by `--min`

Validation rules:
- reject malformed `--min` expressions
- reject unknown threshold metrics
- reject unknown `--sort` keys
- reject non-positive threshold values
- reject `--min-cc`; it is no longer accepted CLI

### Human output model

- always show both metrics in the same unit and rollup sections
- do not split output into separate per-metric modes or sections
- keep one compact table per section

Recommended unit columns:
- identity
- `cc`
- `risk`
- `loc`
- bar

Recommended namespace rollup columns:
- namespace
- units
- total-cc
- avg-cc
- max-cc
- total-loc
- avg-loc
- max-loc
- bar

Bar rule:
- when `--sort` is `cc` or `loc`, bars reflect that metric
- otherwise bars default to `cc`

### Canonical schema direction

The report should explicitly state the active metrics even though they are fixed in v1.

Use compact metric-qualified fields rather than nested per-metric maps.

Canonical direction:
```clojure
{:command :complexity
 :metrics [:cyclomatic-complexity :lines-of-code]
 :scope {...}
 :options {:sort :cc
           :top 20
           :mins {:cc 10 :loc 20}}
 :units [{:ns 'gordian.main
          :var 'build-report
          :kind :defn-arity
          :arity 2
          :dispatch nil
          :file "src/gordian/main.clj"
          :line 87
          :origin :src
          :cc 14
          :cc-decision-count 13
          :cc-risk {:level :moderate
                    :label "Moderate complexity, moderate risk"}
          :loc 22}]
 :namespace-rollups [{:ns 'gordian.main
                      :unit-count 6
                      :total-cc 54
                      :avg-cc 9.0
                      :max-cc 14
                      :cc-risk-counts {:simple 4 :moderate 2 :high 0 :untestable 0}
                      :total-loc 103
                      :avg-loc 17.2
                      :max-loc 31}]
 :project-rollup {:unit-count 187
                  :namespace-count 24
                  :total-cc 811
                  :avg-cc 4.3
                  :max-cc 27
                  :cc-risk-counts {:simple 170 :moderate 13 :high 4 :untestable 0}
                  :total-loc 2410
                  :avg-loc 12.9
                  :max-loc 77}}
```

## CLI examples

```text
gordian complexity
gordian complexity --sort loc
gordian complexity --min cc=10
gordian complexity --min cc=10 --min loc=20 --sort loc --top 15
gordian complexity src/ test/ --sort ns
```

## Human output sketch

```text
COMPLEXITY

Scope: discovered source paths
Units analyzed: 187

UNITS

gordian.main/build-report [arity 2]         14  moderate   22  ██████████████
gordian.output/format-report [arity 1]      11  moderate   18  ███████████
gordian.scan/parse-file-all [arity 2]        9  simple     16  █████████

NAMESPACE ROLLUP

gordian.main      6  total-cc=54  avg-cc=9.0  max-cc=14  total-loc=103  avg-loc=17.2  max-loc=31  ██████████████
gordian.output    7  total-cc=49  avg-cc=7.0  max-cc=11  total-loc=95   avg-loc=13.6  max-loc=24  ███████████

PROJECT ROLLUP

units=187  namespaces=24  total-cc=811  avg-cc=4.3  max-cc=27  total-loc=2410  avg-loc=12.9  max-loc=77
simple=170  moderate=13  high=4  untestable=0
```

## Architecture guidance

Follow existing Gordian patterns:
- preserve pure-core / thin-IO architecture
- preserve the current analysis-unit identity model
- preserve discovered source/test scope semantics unless explicitly changed by this task
- preserve standard envelope conventions for EDN/JSON output
- preserve command-facing `complexity` / metric-facing `cyclomatic` terminology split
- prefer compact metric-qualified fields over deeply nested per-metric maps
- avoid introducing a full metric registry or plugin architecture for just two metrics
- keep report assembly, sorting, filtering, and truncation pure; keep CLI parsing/validation and file IO thin

## Possible implementation shapes

### 1. Minimal extension
- add LOC computation to the existing complexity pipeline
- extend the report shape with `:loc`, `:total-loc`, `:avg-loc`, and `:max-loc`
- replace cyclomatic-shaped threshold handling with generic `--min` parsing and generalized sort semantics
- best if current code already has enough source-span provenance

### 2. Report-finalizer-first (preferred)
- first refactor complexity option handling and report shaping into a more explicitly two-metric pure layer
- then add LOC into that cleaner core
- preferred because the task’s main risk is command-shape leakage rather than LOC computation difficulty
- best if current command logic still leaks cyclomatic-specific assumptions across output and CLI wiring

### 3. Direct convergence
- ship LOC, generic `--min`, and two-metric text output together
- update docs/tests/help in one focused convergence pass
- best if the current command is still new enough that a small CLI cleanup cost is acceptable

## Acceptance criteria

- `gordian complexity` reports both `:cc` and `:loc` for every analyzed unit
- EDN/JSON output includes `:metrics [:cyclomatic-complexity :lines-of-code]`
- rollups include `:total-loc`, `:avg-loc`, and `:max-loc`
- `--sort loc` orders unit output by descending LOC with documented tie-breaks
- repeated `--min` constraints combine conjunctively for displayed unit rows
- `--min` filtering semantics over displayed units versus unfiltered rollups are defined and tested
- malformed `--min` values fail with a clear validation error
- `gordian complexity --min-cc 10` fails with a migration hint to `--min cc=10`
- human-readable unit rows and namespace rollups display both metrics in the same sections
- task is complete when there is an implementation-ready plan for adding LOC and converging the command surface incrementally rather than by big-bang redesign

## Done means

The task has an implementation-ready design that:
- precisely defines LOC
- fixes the v1 two-metric model
- defines canonical generic threshold syntax
- removes `--min-cc`
- defines threshold application semantics for displayed units versus rollups
- explains how sort/top behave when both metrics are present
- gives a concrete schema direction
- gives a clear incremental convergence path from the current cyclomatic-centered command
