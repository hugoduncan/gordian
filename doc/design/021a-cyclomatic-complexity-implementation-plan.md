# Cyclomatic Complexity — Implementation Plan

Status: accepted implementation companion for Munera task `002-cyclomatic-complexity`

## Purpose

Converge the existing partial cyclomatic-complexity prototype onto the accepted
`002` task design.

This document locks:
- command naming and compatibility
- unit extraction rules
- scoring rules and exclusions
- canonical schema
- sort/top semantics
- scope semantics and validation
- output expectations
- implementation order

It is intentionally narrower than the task design doc: this is the build plan
for finishing the already-started feature.

## Current state

The repository already contains a partial implementation:
- `src/gordian/cyclomatic.clj`
- CLI wiring in `src/gordian/main.clj`
- output in `src/gordian/output.clj`
- tests in `test/gordian/cyclomatic_test.clj` and related integration tests

That implementation is useful, but diverges from the accepted task shape:
- command name is `cyclomatic`, not `complexity`
- reporting unit is function-level with max-over-arities, not arity-level units
- extraction only handles `defn` / `defn-`
- scoring rules do not match the accepted v1 rules
- report schema is not metric-qualified
- risk bands, `--sort`, section-local `--top`, scope controls, and bar charts
  are incomplete or absent

## Command naming decision

Accepted public command:
- `gordian complexity`

Compatibility decision:
- retain `gordian cyclomatic` as a compatibility alias in v1
- `:gordian/command` in the canonical payload should be `:complexity`
- the metric identity should be `:metric :cyclomatic-complexity`

Rationale:
- the task and design language consistently target `complexity`
- `complexity` leaves room for future local metrics while keeping the payload
  explicit about which metric is being reported
- retaining `cyclomatic` as an alias avoids breaking current users abruptly

CLI help / README / schema docs should describe `complexity` as canonical and
mention `cyclomatic` only as a compatibility alias if we choose to surface it.

## Canonical unit model

The canonical reported unit is one arity-level top-level executable body.

Included in v1:
- top-level `defn`
- top-level `defn-`
- top-level `defmethod`
- top-level `def` whose value is a literal `fn`

Unit kinds:
- `:defn-arity`
- `:defmethod`
- `:def-fn-arity`

Identity fields:
- `:ns`
- `:var`
- `:kind`
- `:arity`
- `:dispatch`
- `:file`
- `:line`
- `:origin`

Conventions:
- `:arity` is the number of parameters in the analyzed body when available
- `:dispatch` is non-nil only for `defmethod`
- `:origin` is `:src` or `:test`
- multi-arity top-level forms produce one unit per arity body
- nested anonymous functions are not standalone units in v1; their branching
  contributes to the enclosing top-level unit

Deferred from v1:
- `extend-type`
- `extend-protocol`
- `reify`
- nested `fn` as standalone units

## Scoring rules

### Base rule

Every unit has:
- base cyclomatic complexity `1`
- decision count `0`

For each counted decision point, increment:
- `:cc` by the appropriate amount
- `:cc-decision-count` by the same amount

Thus:
- `:cc = 1 + :cc-decision-count`

### Counted forms

Count `+1` for each occurrence of:
- `if`
- `if-not`
- `when`
- `when-not`
- `if-let`
- `if-some`
- `when-let`
- `when-some`
- each `catch`
- each `cond->` condition/form pair

Count clause-based branching as follows:
- `cond`
  - `+1` per test/result clause pair
  - trailing `:else` branch counts
- `condp`
  - `+1` per clause pair
  - default clause counts when present
- `case`
  - `+1` per branch arm
  - default branch counts when present
- `and`
  - `+1` per additional operand after the first
- `or`
  - `+1` per additional operand after the first

### Explicit exclusions

Do not count as independent cyclomatic increments:
- `->`, `->>`, `as->`, `some->`, `some->>`
- `cond->>`
- `let`, `letfn`, `binding`
- `loop`
- `recur`
- self-recursion
- mutual recursion
- `for`
- `doseq`
- `while`
- plain function calls
- higher-order sequence functions such as `mapcat`, `reduce`, `filter`, `keep`
- macroexpansion-derived branches
- data-driven dispatch not visible in syntax

Branches nested inside excluded forms still count if they use included forms.

### Traversal rule

The analysis is syntax-first and recursive over parsed forms.

Special cases:
- quoted forms do not contribute
- `var` forms do not contribute
- maps, vectors, and sets are traversed recursively
- `try` contributes via recursive traversal of the body plus `+1` per `catch`

## Risk bands

Risk bands are derived from final `:cc`:
- `1..10`   => `{:level :simple      :label "Simple, low risk"}`
- `11..20`  => `{:level :moderate    :label "Moderate complexity, moderate risk"}`
- `21..50`  => `{:level :high        :label "High complexity, high risk"}`
- `51+`     => `{:level :untestable  :label "Untestable, very high risk"}`

Rollups aggregate counts into:
- `:cc-risk-counts {:simple n :moderate n :high n :untestable n}`

## Canonical payload shape

```clojure
{:gordian/command :complexity
 :metric :cyclomatic-complexity
 :scope {:mode :discovered|:explicit
         :source? boolean
         :tests? boolean
         :paths [string ...]}
 :options {:sort :cc|:ns|:var|:cc-risk
           :top pos-int-or-nil
           :namespace-rollup boolean
           :project-rollup boolean}
 :units [{:ns symbol
          :var symbol
          :kind keyword
          :arity int-or-nil
          :dispatch any
          :file string
          :line int-or-nil
          :origin :src|:test
          :cc int
          :cc-decision-count int
          :cc-risk {:level keyword :label string}}]
 :namespace-rollups [{:ns symbol
                      :unit-count int
                      :total-cc int
                      :avg-cc double
                      :max-cc int
                      :cc-risk-counts {:simple int :moderate int :high int :untestable int}}]
 :project-rollup {:unit-count int
                  :namespace-count int
                  :total-cc int
                  :avg-cc double
                  :max-cc int
                  :cc-risk-counts {:simple int :moderate int :high int :untestable int}}}
```

Notes:
- `:namespace-rollups` and `:project-rollup` remain additive report sections
- sort/top options are part of report metadata for reproducibility
- the Gordian envelope remains outside this payload in EDN/JSON command output

## Sort semantics

Supported sort keys:
- `cc`
- `ns`
- `var`
- `cc-risk`

Default:
- `cc`

Ordering:
- `cc`
  - descending `:cc`
  - then ascending `:ns`, `:var`, `:kind`, `:arity`, `:dispatch`
- `ns`
  - ascending `:ns`
  - then descending `:cc`
  - then ascending `:var`, `:kind`, `:arity`, `:dispatch`
- `var`
  - ascending `:var` identity within ascending `:ns`
  - then descending `:cc`
  - then ascending `:kind`, `:arity`, `:dispatch`
- `cc-risk`
  - descending risk severity order: `:untestable`, `:high`, `:moderate`, `:simple`
  - then descending `:cc`
  - then ascending `:ns`, `:var`, `:kind`, `:arity`, `:dispatch`

Namespace rollups:
- sort by descending `:max-cc`, then ascending `:ns`
- this remains independent from unit sorting in v1

Project rollup:
- singleton section; not sorted

## `--top` semantics

`--top N` is section-local.

Apply independently to:
- `:units`
- `:namespace-rollups`

Do not apply to:
- `:project-rollup`

This means `--top 20` can yield:
- 20 units
- 20 namespace rollups
- 1 project rollup

## Scope semantics

### Default

`gordian complexity`
- analyze discovered source paths only

### Explicit scope flags

- `--source-only`
  - analyze discovered source paths only
- `--tests-only`
  - analyze discovered test paths only

### Explicit paths

When positional paths are supplied:
- analyze exactly those paths
- do not use discovery-based source/test scope selection

### Source/test provenance

When discovery provides typed paths:
- source directories produce units with `:origin :src`
- test directories produce units with `:origin :test`

When explicit paths are supplied:
- infer `:origin` using the same path heuristic already used by `tests` mode
  where practical
- if a path cannot be identified as test-like, treat it as `:src`

## Validation rules

Reject:
- `--source-only` with `--tests-only`
- explicit paths with either `--source-only` or `--tests-only`
- `--json` with `--edn`
- unknown `--sort` keys
- non-positive `--top` values

Markdown handling:
- if markdown remains supported for consistency, document it explicitly
- otherwise reject it for this command and document that decision

## Human output expectations

Human-readable output should include:
- title / scope summary
- units section
- namespace rollup section
- project rollup section
- horizontal bars

Bar rules:
- unit bars scale from raw `:cc`
- namespace bars scale from `:max-cc`, not `:total-cc`
- project rollup may omit a bar or use `:max-cc`; either choice should be kept
  consistent across docs and tests
- cap bars at a fixed width such as 50

Illustrative shape:

```text
COMPLEXITY

Scope: discovered source paths
Units analyzed: 187

UNITS

gordian.main/build-report [arity 2]         14  moderate    decisions=13  ██████████████
gordian.output/format-report [arity 1]      11  moderate    decisions=10  ███████████
gordian.scan/parse-file-all [arity 2]        9  simple      decisions=8   █████████

NAMESPACE ROLLUP

gordian.main      total=54  avg=9.0  max=14  units=6   ██████████████
gordian.output    total=49  avg=7.0  max=11  units=7   ███████████

PROJECT ROLLUP

units=187  namespaces=24  total=811  avg=4.3  max=27
simple=170  moderate=13  high=4  untestable=0
```

## Implementation sequence

1. update CLI and docs to make `complexity` canonical and `cyclomatic` an alias
2. refactor pure extraction to produce canonical arity-level units
3. align scoring semantics exactly with this document
4. add risk-band classification
5. replace prototype schema with canonical metric-qualified schema
6. add pure rollup, sorting, and section-local truncation helpers
7. align scope resolution and validation semantics with this document
8. update text / markdown / EDN / JSON output around canonical payloads
9. expand tests for extraction, scoring, schema, sort/top, scope, validation,
   and alias compatibility

## Acceptance test matrix

At minimum, tests should cover:
- single-arity `defn`
- multi-arity `defn`
- `defmethod`
- top-level `def` + literal `fn`
- nested branching under excluded forms
- `cond->`
- default-branch handling for `cond`, `condp`, `case`
- exclusion of `loop`, `recur`, `for`, `doseq`, and `while` as independent increments
- risk-band boundaries at 10/11, 20/21, and 50/51
- each supported sort key
- section-local `--top`
- default discovered-source behavior
- `--tests-only`
- explicit path override behavior
- CLI alias behavior for `complexity` and `cyclomatic`

## Non-goals for this task

Still out of scope:
- cognitive complexity
- Local Comprehension Complexity
- compare / gate integration
- historical trending
- source-span-level decision reporting
- semantic or macroexpanded analysis
