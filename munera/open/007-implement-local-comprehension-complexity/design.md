Implement Gordian Local Comprehension Complexity (LCC) as a new local-analysis command and pure analysis pipeline, following the converged metric definition in `doc/design/021-local-comprehension-complexity.md`.

## Intent

- turn the converged LCC metric definition into a usable Gordian feature
- add a new local-analysis lens for safe local change reasoning, complementing `gordian complexity`
- make `gordian local` feel structurally familiar to users of `gordian complexity`, especially in scope selection, ranking/truncation controls, and report layout
- preserve Gordian’s explanatory style by emitting burden vectors plus findings, not only a scalar score
- shape the implementation for simplicity, consistency, and robustness, with a pure analysis core and thin CLI/output shell

## Problem statement

Gordian now has a converged metric definition for Local Comprehension Complexity in `doc/design/021-local-comprehension-complexity.md`, but no implementation. Users therefore cannot yet apply the metric to real code, inspect burden vectors, or rank local units by local change burden.

This creates a gap:
- the concept is defined but not operational
- existing `gordian complexity` surfaces cc/loc hotspots but not comprehension burden
- the design’s most valuable ideas — especially working-set burden, abstraction burden, shape burden, and explicit state burden — are not yet available as project analysis tools
- users already have a successful mental model for Gordian local-metric commands via `gordian complexity`, so `gordian local` should reuse that model where it helps comprehension rather than inventing a wholly different CLI/report style

The implementation task must therefore translate the normative metric definition into a practical Gordian command without collapsing it into a simplistic scalar metric or overreaching into full semantic analysis.

The implementation should begin with a narrow v1 slice that preserves the metric’s conceptual center:
- burden vector primary
- findings explanatory
- headline rollup secondary
- deterministic syntax-first heuristics
- top-level local units only

## Scope

### In scope

- implement a new `gordian local` command
- implement v1 local-unit extraction aligned with the metric definition:
  - top-level `defn` arities
  - top-level `defmethod` bodies
  - nested local helpers folded into the enclosing top-level unit
- implement v1 burden families:
  - `flow-burden`
  - `state-burden`
  - `shape-burden`
  - `abstraction-burden`
  - `dependency-burden`
  - `working-set`
- defer `regularity-burden` from the first executable slice unless a clearly conservative, high-signal implementation falls out naturally after the core burden families are complete
- implement a canonical machine-readable report for LCC results
- implement findings for high-confidence v1 finding kinds
- implement a human-readable output mode suitable for hotspot triage
- shape the CLI and human-readable output to resemble `gordian complexity` where the workflows are analogous
- follow Gordian’s standard output conventions for EDN / JSON / markdown / text where practical
- use discovered source paths by default, with explicit path override behavior consistent with Gordian command conventions
- add tests for unit extraction, burden scoring, findings, report shaping, and CLI behavior

### Out of scope

- compare/gate integration
- historical trending
- editor integration
- full semantic analysis or compiler-grade inference
- project-specific configuration of abstraction vocabularies or transparency registries in v1
- standalone reporting of nested local `fn` / `letfn` locals
- aggressive calibration beyond what is needed to lock stable v1 behavior
- turning LCC into a generic plugin-based local metric framework

### Explicitly deferred

- richer regularity scoring if the signal is weak or noisy
- project-specific transparency/effect registries
- independent reporting for nested local units
- compare/gate workflows for local metrics
- deeper cross-unit semantic reconstruction
- advanced map/keyset inference beyond the coarse v1 rules

## Non-goals

- do not merge LCC into `gordian complexity`
- do not reduce LCC to a scalar-only ranking tool
- do not block delivery on full semantic precision
- do not build a generic analysis framework broader than this feature requires
- do not let CLI/output concerns distort the normative metric semantics

## Acceptance criteria

- `gordian local` exists as a subcommand
- with no explicit paths, it analyzes discovered source paths by default in the same spirit as `gordian complexity`
- explicit paths override discovery-based defaults
- canonical analyzed units are:
  - top-level `defn` arities
  - top-level `defmethod` bodies
- nested local helpers are folded into the enclosing top-level unit
- the report includes at least:
  - unit identity
  - burden vector
  - `:working-set` substructure with `:peak`, `:avg`, and derived burden
  - `:lcc-total`
  - findings
- implemented burden families cover at least:
  - flow
  - state
  - shape
  - abstraction
  - dependency
  - working-set
- output supports text, EDN, JSON, and markdown
- the CLI surface is familiar to `gordian complexity` users where concepts transfer cleanly, including a compact option model for scope, ranking, truncation, threshold filtering, and bar selection
- human-readable output presents a compact unit table and rollup sections in the same general style as `gordian complexity`, but with burden-vector columns replacing `cc`/`loc`
- findings include high-confidence v1 kinds such as:
  - `:deep-control-nesting`
  - `:mutable-state-tracking`
  - `:shape-churn`
  - `:conditional-return-shape`
  - `:abstraction-mix`
  - `:abstraction-oscillation`
  - `:working-set-overload`
  - `:opaque-pipeline` / `:helper-chasing` where supported by implemented dependency heuristics
- the implementation follows Gordian’s pure-core / thin-IO style
- tests cover:
  - unit extraction rules
  - burden-family scoring semantics
  - findings generation
  - report assembly
  - CLI command behavior and output mode selection

## Minimum concepts

- **local command** — user-facing Gordian surface for LCC analysis
- **local unit** — one top-level `defn` arity or one top-level `defmethod` body
- **evidence** — syntax-derived facts used to compute burden families and findings
- **burden vector** — the primary explanatory metric result
- **headline rollup** — secondary aggregate score for ranking
- **display filter** — a threshold filter that affects displayed unit rows without changing the underlying rollup basis
- **finding** — explanatory result synthesized from burden/evidence
- **main path** — lexical primary sequence of steps in the local unit
- **transparent helper** — helper with locally obvious semantics in v1
- **working-set point** — one approximated program point used in working-set estimation

## Design direction

### Preferred shape: analysis-core first, command integrated early

Implement a pure local-analysis core first, but expose it quickly through a dedicated `gordian local` command so the feature can be dogfooded in realistic workflows.

The core should separate:
- unit extraction
- evidence extraction
- burden computation
- findings synthesis
- report assembly

The CLI layer should then:
- select scope
- call the pure analysis pipeline
- route to text / EDN / JSON / markdown output

This preserves Gordian’s architectural style while keeping the feature visible and testable as a real command.

### Keep the burden vector primary

Text output should rank units by `:lcc-total` or equivalent default ranking, but should still show burden-family details and findings. The feature should not read like “cyclomatic complexity with more fields.”

At the same time, the command should reuse the successful `gordian complexity` interaction model where possible:
- familiar scope semantics
- familiar `--sort`, `--top`, and `--bar` style controls where meaningful
- familiar compact tabular unit output
- familiar namespace/project rollup sections

### Conservative v1 heuristics

Where the metric definition allows breadth, prefer the narrower high-confidence interpretation first:
- conservative abstraction classification
- coarse shape sensitivity
- coarse dependency burden via semantic lookup distance / opacity
- high-signal findings only

## Implementation approaches

### Approach A — full command and all burden families in one pass

Pros:
- quickest path to a visible feature
- fewer intermediate seams

Cons:
- higher risk of conflating parsing, evidence, scoring, and output design
- harder to debug scoring noise

### Approach B — staged pure pipeline with early command wiring (preferred)

Stages:
1. unit extraction + provenance
2. evidence extraction
3. burden-family scoring
4. findings + report assembly
5. command and output integration

Pros:
- best fit with Gordian architecture
- easier to test and calibrate incrementally
- easier to keep burden families conceptually separate

Cons:
- requires a bit more design discipline up front

### Approach C — extend `complexity` command (rejected)

Rejected because:
- LCC has a different conceptual model and output shape
- it would blur scalar local metrics with explanatory burden analysis
- it would make the CLI and report model less clear

## Architectural guidance

Follow existing Gordian patterns:
- pure analysis modules
- thin IO entrypoint / command wiring
- canonical report shape before formatter-specific concerns
- separate output formatting from scoring logic
- reuse discovery and scope semantics from existing local analysis commands where appropriate

Likely module shape:
- `src/gordian/local/units.clj`
  - extract analyzable top-level units with provenance
- `src/gordian/local/evidence.clj`
  - syntax walk and evidence extraction
- `src/gordian/local/burden.clj`
  - burden-family scoring
- `src/gordian/local/findings.clj`
  - finding derivation
- `src/gordian/local/report.clj`
  - report assembly and ranking
- `src/gordian/output/local.clj`
  - text / markdown rendering
- command wiring through existing CLI / main structure

Avoid:
- folding LCC logic into `cyclomatic.clj`
- mixing traversal, scoring, and rendering in one namespace
- prematurely introducing a generic local-analysis framework if it harms local comprehensibility
- leaking parser-specific detail into command/output layers

## CLI direction

Preferred initial command surface:

```text
gordian local
gordian local .
gordian local src/ test/
```

Initial option direction should intentionally mirror `gordian complexity` where the concepts map cleanly:
- output mode flags (`--json`, `--edn`, `--markdown`)
- scope controls (`--source-only`, `--tests-only`)
- ranking/truncation controls (`--sort`, `--top`)
- explicit bar selection (`--bar`) for human-readable histograms
- `--help`

Preferred v1 sort direction:
- sort by `lcc-total` by default
- allow burden-family-oriented sort keys such as `total`, `flow`, `state`, `shape`, `abstraction`, `dependency`, `working-set`, plus `ns` and `var` if they keep the surface coherent

Preferred v1 bar direction:
- bars should be driven by one selected burden dimension or total burden, analogous to `complexity`
- the default bar metric should be stable and documented

Threshold/filter controls analogous to `complexity --min metric=value` are part of v1 because they materially improve hotspot triage for burden-vector analysis.

## Precise v1 CLI and output proposal

### Command forms

```text
gordian local
gordian local .
gordian local src/ test/
```

### Scope semantics

Match `gordian complexity` where practical:
- default with no explicit paths = discovered source paths only
- `--source-only` = discovered source paths only
- `--tests-only` = discovered test paths only
- explicit paths override discovery-based scope selection
- reject `--source-only` combined with `--tests-only`
- reject explicit paths combined with `--source-only` or `--tests-only`

### Supported v1 flags

- `--source-only`
- `--tests-only`
- `--json`
- `--edn`
- `--markdown`
- `--sort KEY`
- `--top N`
- `--bar METRIC`
- repeatable `--min METRIC=VALUE`
- `--help`

Supported v1 threshold metrics:
- `total`
- `flow`
- `state`
- `shape`
- `abstraction`
- `dependency`
- `working-set`

### Sort options

Recommended v1 sort keys:
- `total`
- `flow`
- `state`
- `shape`
- `abstraction`
- `dependency`
- `working-set`
- `ns`
- `var`

Tie-break direction:
- burden-based keys sort descending on that burden, then descending `:lcc-total`, then namespace / var / arity identity
- `ns` sorts ascending namespace, then descending `:lcc-total`
- `var` sorts ascending var identity, then descending `:lcc-total`

Default sort:
- `total`

### Top semantics

As in `complexity`:
- `--top` truncates after sort order is applied
- it applies independently to displayed unit rows and namespace rollups
- project rollup remains untruncated

### Threshold / filter semantics

As in `complexity`, canonical threshold syntax is generic and metric-qualified:
- repeatable `--min metric=value`

Examples:
- `gordian local --min total=12`
- `gordian local --min abstraction=4 --min working-set=3`

Combination rule:
- if multiple `--min` constraints are supplied, a unit row must satisfy all of them to be displayed

Application semantics:
- `--min` applies only to displayed unit rows
- namespace rollups are computed from the full analyzed unit set, not the thresholded display subset
- project rollup is computed from the full analyzed unit set, not the thresholded display subset
- displayed unit rows are the only section filtered by `--min`

### Bar semantics

Add an explicit histogram selection option analogous to `complexity`:
- `--bar total|flow|state|shape|abstraction|dependency|working-set`

Semantics:
- applies to human-readable output only
- drives bars in unit and namespace-rollup sections
- project rollup remains a textual summary section without bars

Default when `--bar` is absent:
- use the selected sort metric when sorting by a burden metric
- otherwise default to `total`

### Human-readable output model

Rollup rule:
- namespace and project burden-family rollups use averages for per-family burdens rather than totals, so they remain interpretable across different namespace sizes


Keep the same broad section structure as `gordian complexity`:
- command heading / scope summary
- units section
- namespace rollup section
- project rollup section

Recommended unit columns:
- identity
- total
- flow
- state
- shape
- abstraction
- dependency
- ws
- bar

Where:
- `ws` is the derived working-set burden, with peak/avg available in detailed or machine-readable output
- findings are shown inline in a compact form beneath each row or in a following short findings line when present

Recommended namespace rollup columns:
- namespace
- units
- total-lcc
- avg-lcc
- max-lcc
- avg-flow
- avg-state
- avg-shape
- avg-abstraction
- avg-dependency
- avg-ws
- bar

Recommended project rollup fields:
- units analyzed
- namespaces analyzed
- avg-lcc
- max-lcc
- avg burden per family
- top finding counts by kind if this fits cleanly

### Canonical machine-readable direction

For the initial v1 implementation, omit `regularity-burden` unless it is implemented with a clearly conservative, high-signal rule set.


Keep the report shape analogous in spirit to `complexity`, but fitted to the burden-vector model.

Preferred direction:

```clojure
{:command :local
 :metric :local-comprehension-complexity
 :scope {:mode :discovered
         :source? true
         :tests? false
         :paths ["src" "components/foo/src"]}
 :options {:sort :total
           :top 20
           :mins {:total 12 :abstraction 4}
           :bar :total}
 :units [{:ns 'gordian.main
          :var 'build-report
          :kind :defn-arity
          :arity 2
          :dispatch nil
          :file "src/gordian/main.clj"
          :line 87
          :origin :src
          :flow-burden 3
          :state-burden 1
          :shape-burden 4
          :abstraction-burden 5
          :dependency-burden 2
          :working-set {:peak 6
                        :avg 3.8
                        :burden 2}
          :lcc-total 18
          :findings [{:kind :abstraction-oscillation
                      :severity :high
                      :score 5
                      :message "Domain and mechanism concerns alternate repeatedly"}]}]
 :namespace-rollups [{:ns 'gordian.main
                      :unit-count 6
                      :total-lcc 54
                      :avg-lcc 9.0
                      :max-lcc 18
                      :avg-flow 1.7
                      :avg-state 0.8
                      :avg-shape 2.1
                      :avg-abstraction 2.5
                      :avg-dependency 1.3
                      :avg-working-set 1.6}]
 :project-rollup {:unit-count 187
                  :namespace-count 24
                  :total-lcc 811
                  :avg-lcc 4.3
                  :max-lcc 27
                  :avg-flow 1.4
                  :avg-state 0.6
                  :avg-shape 1.2
                  :avg-abstraction 1.3
                  :avg-dependency 0.9
                  :avg-working-set 1.1}}
```

### Human output sketch

```text
LOCAL

Scope: discovered source paths
Units analyzed: 187

UNITS

gordian.main/build-report [arity 2]      18  3  1  4  5  2  2  ██████████████████
  findings: abstraction-oscillation, shape-churn

gordian.scan/parse-file-all [arity 2]    15  3  0  4  2  2  2  ███████████████
  findings: shape-churn, opaque-pipeline

NAMESPACE ROLLUP

gordian.main      6  total=54  avg=9.0  max=18  flow=1.7  state=0.8  shape=2.1  abst=2.5  dep=1.3  ws=1.6  ██████████████████

gordian.scan      5  total=39  avg=7.8  max=15  flow=1.8  state=0.2  shape=2.4  abst=1.6  dep=1.2  ws=1.4  ███████████████

PROJECT ROLLUP

units=187  namespaces=24  total=811  avg=4.3  max=27
avg-flow=1.4  avg-state=0.6  avg-shape=1.2  avg-abstraction=1.3  avg-dependency=0.9  avg-ws=1.1
```

### Validation rules

- reject `--source-only` combined with `--tests-only`
- reject explicit paths combined with `--source-only` or `--tests-only`
- reject mutually exclusive output modes used together
- reject unknown `--sort` keys
- reject invalid `--bar` values
- reject malformed `--min` expressions
- reject unknown threshold metrics
- reject non-positive threshold values
- reject non-positive `--top` values

## Risks

- burden families bleed into each other and create confusing scores
- working-set approximation becomes noisy or expensive
- abstraction or dependency heuristics become too speculative
- text output collapses the feature into a scalar hotspot list
- implementation expands into premature generalization

## Mitigations

- preserve primary evidence ownership by burden family
- implement high-confidence heuristics first
- keep working-set estimation coarse and deterministic in v1
- keep burden vector and findings visible in human output
- shape the implementation as small pure modules with focused tests

## Done means

The task is complete when Gordian has a working `local` command backed by a pure LCC analysis pipeline that faithfully implements the converged v1 metric semantics, emits burden vectors plus findings for top-level local units, and presents them through a CLI/output model that feels recognizably aligned with `gordian complexity` while preserving LCC’s explanatory burden-vector character.
