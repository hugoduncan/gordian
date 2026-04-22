Approach:
- treat `doc/design/021-local-comprehension-complexity.md` as the normative semantic source
- treat `doc/design/021b-local-comprehension-complexity-computation-rules.md` as the preferred v1 computation specification
- implement LCC as a dedicated `gordian local` command over a pure analysis pipeline
- deliberately reuse the successful `gordian complexity` CLI and output idioms where they fit the LCC workflow
- prefer conservative high-confidence heuristics over speculative breadth in v1
- sequence implementation so each phase produces a stable, testable seam before the next phase builds on it

Execution phases:

### Phase 1 — command shell and scope model
- add `local` to the CLI registry and scoped help
- wire default scope semantics parallel to `complexity`
- support explicit paths and discovered source/test scope selection
- lock CLI validation for:
  - `--source-only`
  - `--tests-only`
  - explicit path override behavior
  - output mode exclusivity
  - `--sort`, `--top`, `--min`, and `--bar`
- keep the command thin: no scoring logic in CLI/main

Exit condition:
- `gordian local --help` works
- scope and option validation are test-covered
- command shell can invoke a stub analysis pipeline

### Phase 2 — local-unit extraction
- implement extraction of top-level analyzable units:
  - `defn` arities
  - `defmethod` bodies
- preserve provenance fields needed for reporting:
  - namespace
  - var
  - arity / dispatch
  - file
  - line
  - origin
- fold nested local helpers into enclosing top-level units
- align discovery and explicit-path behavior with existing local-analysis commands

Exit condition:
- canonical unit extraction works on representative fixtures
- multi-arity and `defmethod` identity are test-covered
- unit extraction is available as a pure layer consumed by later phases

### Phase 3 — shared evidence extraction
- implement a shared evidence model over extracted local units
- evidence should cover at least:
  - main-path steps
  - branch regions
  - control forms with nesting depth
  - bindings and rebinding relations
  - call/operator classification
  - effect/mutation observations
  - coarse shape observations
  - candidate program points for working-set estimation
- keep evidence normalized enough that burden families can consume it without re-traversing whole units in ad hoc ways
- keep evidence ownership aligned with `021b`

Exit condition:
- shared evidence shape is stable enough to support multiple burden scorers
- tests cover representative evidence extraction cases for control, calls, bindings, effects, and shapes

### Phase 4 — burden scoring core
Implement burden families in the conservative order implied by `021b`:

1. `flow-burden`
2. `abstraction-burden`
3. `shape-burden`
4. `dependency-burden`
5. `state-burden`
6. `working-set`

For each burden:
- consume shared evidence rather than re-discovering facts independently
- implement the exact v1 computation rules from `021b`
- lock exclusions and anti-double-counting behavior in tests
- expose burden-specific evidence or summaries where needed for findings/debugging

Exit condition:
- all required v1 burdens are implemented and test-covered
- burden-family scoring is pure and independent of output formatting
- `regularity-burden` remains omitted unless a clearly conservative implementation falls out naturally

### Phase 5 — findings
- implement high-confidence finding derivation from burden outputs and shared evidence
- start only with v1 finding kinds already called out in task/design docs:
  - `:deep-control-nesting`
  - `:mutable-state-tracking`
  - `:shape-churn`
  - `:conditional-return-shape`
  - `:abstraction-mix`
  - `:abstraction-oscillation`
  - `:working-set-overload`
  - `:opaque-pipeline`
  - `:helper-chasing` where dependency evidence supports it
- keep finding trigger thresholds conservative and aligned with `021b`
- separate finding derivation from burden scoring so thresholds can evolve without rewriting scoring logic

Exit condition:
- finding generation is pure and test-covered
- findings are explainable from recorded evidence

### Phase 6 — canonical report assembly
- assemble the canonical machine-readable report for `gordian local`
- include:
  - command/scope/options metadata
  - unit rows with burden vector, `:working-set`, `:lcc-total`, and findings
  - namespace rollups
  - project rollup
- omit `regularity-burden` from first-slice payloads unless implemented
- lock rollup semantics:
  - burden-family rollups use averages
  - `--min` affects only displayed unit rows, not rollup basis
- implement default ranking and sort behavior

Exit condition:
- EDN report shape is stable and test-covered
- sort/top/min/bar semantics are implemented at the report/finalizer layer, not inside formatters

### Phase 7 — human-readable and machine-readable output
- implement text output in a layout structurally familiar to `gordian complexity`
- implement markdown output with analogous sections/tables
- implement EDN and JSON output over the canonical report
- keep findings visible in human output without collapsing the command into a scalar-only table
- ensure bars reflect the selected `--bar` metric

Exit condition:
- text / markdown / EDN / JSON output modes are all working and test-covered
- output remains layered above canonical report assembly

### Phase 8 — polish and docs
- finalize help text and examples for `gordian local`
- document CLI semantics in README or command docs
- add representative end-to-end tests
- review naming, data-shape consistency, and output clarity

Exit condition:
- help/docs match actual behavior
- representative end-to-end workflows are covered

Expected shape:
- pure namespaces for:
  - unit extraction
  - shared evidence extraction
  - burden scoring
  - findings
  - report assembly
- command wiring integrated with existing Gordian CLI structure
- canonical machine-readable report plus useful text/markdown output for hotspot triage
- tests that lock unit model, burden semantics, finding behavior, and report shape

Risks:
- accidental double-counting across burden families
- noisy abstraction/dependency heuristics
- working-set estimation becoming too implementation-heavy or expensive
- report/output work beginning before burden semantics stabilize
- command surface growing before real usage justifies it

Mitigations:
- respect the metric’s primary evidence ownership rules
- keep the first slice narrow and deterministic
- test burden families independently before integrating totals/findings
- keep output/reporting layered above the pure analysis core
- prefer under-counting to speculative over-counting in v1
