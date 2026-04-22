Approach:
- converge the existing partial `gordian cyclomatic` implementation onto the accepted `002` task design rather than treating the current code as task-complete
- keep the feature as a pure analysis pipeline plus thin CLI wiring
- preserve the conservative, deterministic v1 scoring model for Clojure syntax
- make the canonical reporting unit arity-level top-level executable bodies, classify each score into a standard risk band, then aggregate by namespace and project using metric-qualified schema fields

Current implementation status:
- already implemented:
  - pure cyclomatic scoring/rollup module in `src/gordian/cyclomatic.clj`
  - CLI wiring for `gordian cyclomatic`
  - text / markdown / EDN / JSON output plumbing
  - basic tests for scoring, rollup, output, and command integration
- known gaps against task `002`:
  - command shape diverges: implementation is `gordian cyclomatic`, design target is `gordian complexity`
  - canonical reporting unit not yet implemented; current report is function-level with max-over-arities
  - unit extraction is incomplete: `defmethod` and top-level `def` + literal `fn` are missing
  - scoring semantics do not yet match the locked v1 rules (`cond->`, default branches, loop/recursion exclusions)
  - canonical metric-qualified schema is not yet implemented
  - risk bands are not yet reported
  - `--sort` and section-local `--top` are not implemented for this command
  - scope controls / validation do not yet match the design; current default behavior includes tests for `:cyclomatic`
  - human text output lacks the requested horizontal bar charts

Expected finished shape:
- pure analysis module(s) for:
  - extracting arity-level analyzable units from forms
  - counting explicit cyclomatic decision points and computing `:cc`
  - assigning `:cc-risk`
  - rollups by namespace and project using compact metric-qualified field names
  - sorting / truncation / report assembly
- CLI integration in `main.clj` for:
  - accepted public command shape (`gordian complexity`, with any compatibility decision for `cyclomatic` made explicitly)
  - default discovery behavior = source paths only
  - source-only / tests-only / explicit path selection
  - `--json` / `--edn` / text output modes
  - optional rollup flags if retained
  - `--top`
  - `--sort`
  - validation of conflicting scope and output options
- output support in `output.clj` and JSON / EDN serializers via canonical report data

Locked v1 decisions:
- canonical reporting unit = arity-level top-level executable body
- nested anonymous functions count toward the enclosing top-level unit
- base complexity = 1 per unit
- scoring model is strict explicit branching only, plus `cond->`
- default scope with no explicit paths = discovered source paths only
- rollups are additive sections, not alternate views
- canonical output uses compact metric-qualified fields such as `:metric :cyclomatic-complexity`, `:cc`, `:cc-decision-count`, and `:cc-risk`, but not detailed per-decision locations

Required v1 counted forms:
- `if`, `if-not`, `when`, `when-not`
- `if-let`, `if-some`, `when-let`, `when-some`
- `cond` (including trailing `:else` branch when present)
- `condp` (including default clause when present)
- `case` (including default branch when present)
- `and`, `or`
- `catch`
- `cond->`

Required v1 exclusions:
- threading macros except `cond->`
- `loop`, `recur`, self-recursion, mutual recursion, `for`, and `doseq` as independent cc increments
- higher-order sequence functions
- macroexpansion-derived branching
- standalone nested-anonymous-function units

Loop handling in v1:
- loops and recursion do not independently increase `:cc`
- branches inside loop or recursive bodies still count normally
- this keeps v1 branch-oriented and avoids uneven treatment of Clojure iteration idioms

CLI behavior for implementation:
- no path args => analyze discovered source paths
- `--tests-only` => analyze discovered test paths only
- `--source-only` => explicit source-only mode, equivalent to default but still useful for scripts
- explicit paths => analyze exactly those paths
- combining explicit paths with `--source-only` / `--tests-only` should be rejected for clarity
- `--source-only` combined with `--tests-only` should be rejected
- `--json` combined with `--edn` should be rejected
- unknown `--sort` keys should be rejected
- `--top` must be a positive integer

Unit extraction for implementation:
- `defn` / `defn-` => one unit per arity body
- `defmethod` => one unit per method body, preserving dispatch value in identity where available
- top-level `def` with literal `fn` value => one unit per `fn` arity body
- nested anonymous functions count toward the enclosing top-level unit
- extension/protocol/reify forms are deferred from v1

Sorting / truncation guidance:
- default sort = `cc` => descending cc, then namespace, var, and arity identity
- `--sort ns` => ascending namespace, then descending cc
- `--sort var` => ascending var identity, then descending cc
- `--sort cc-risk` => descending cc risk severity, then descending cc
- no separate sort-direction flag in v1
- `--top N` applies independently to each applicable section rather than globally across the whole report

Canonical report shape should include:
- command metadata and scope metadata
- metric identity metadata, e.g. `:metric :cyclomatic-complexity`
- analyzed units with identity, origin, `:cc`, `:cc-decision-count`, and `:cc-risk`
- unit identity fields should include `:ns`, `:var`, `:kind`, `:arity`, `:dispatch`, `:file`, `:line`, and `:origin`
- namespace rollups using compact metric-qualified aggregate field names such as `:total-cc`, `:avg-cc`, `:max-cc`, and `:cc-risk-counts`
- project rollup using the same compact metric-qualified aggregate field names
- any applied sort/top options for reproducibility
- human output includes units, namespace rollups, project rollup, and horizontal bar charts

Recommended implementation order:
1. lock the implementation companion doc and CLI compatibility decision
2. refactor extraction to produce canonical arity-level units
3. align scoring semantics exactly with the accepted v1 rules
4. emit canonical schema plus risk-band classification
5. add sorting / section-local truncation in pure code
6. fix CLI scope resolution and validation semantics
7. finish text output with bar charts
8. expand tests to cover the finalized behavior and compatibility choices

Risks:
- ambiguity in mapping classic cyclomatic complexity onto Lisp syntax
- noise if common macros are misclassified as decision points
- parser/source-location limitations affecting unit identity and UX
- feature overlap/confusion with the separate Local Comprehension Complexity track
- surprising UX around command naming and sort/top semantics if not specified tightly

Mitigations:
- document the exact v1 counted constructs and exclusions
- keep v1 conservative and syntax-based
- emit stable machine-readable schema so heuristics can evolve later
- position this as a standard branch-complexity lens, not a full maintainability model
- explicitly decide whether `cyclomatic` remains as an alias or is replaced by `complexity`
- define sort/top semantics explicitly in the implementation companion doc before coding the convergence work
