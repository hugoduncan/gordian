Approach:
- add a dedicated cyclomatic-complexity command using the existing Gordian command/output conventions
- build the feature as a pure analysis pipeline plus thin CLI wiring
- define a conservative, deterministic v1 scoring model for Clojure syntax
- analyze top-level executable bodies at arity granularity, classify each score into a standard risk band, then optionally aggregate by namespace and project

Expected shape:
- pure analysis module(s) for:
  - parsing / extracting analyzable units from forms
  - counting explicit cyclomatic decision points and computing cyclomatic complexity
  - assigning cyclomatic risk levels
  - rollups by namespace and project
  - sorting / truncation / report assembly
- CLI integration in `main.clj` for:
  - `gordian complexity`
  - default discovery behavior (source paths only)
  - source-only / tests-only / explicit path selection
  - `--json` / `--edn` / text output modes
  - optional rollup flags
  - `--top`
  - `--sort`
- output support in `output.clj` and JSON / EDN serializers via canonical report data

Locked v1 decisions:
- canonical reporting unit = arity-level top-level executable body
- nested anonymous functions count toward the enclosing top-level unit
- base complexity = 1 per unit
- scoring model is strict explicit branching only, plus `cond->`
- default scope with no explicit paths = discovered source paths only
- rollups are additive sections, not alternate views
- canonical output uses compact metric-qualified fields such as `:metric :cyclomatic-complexity`, `:cc`, `:cc-decision-count`, and `:cc-risk`, but not detailed per-decision locations

Recommended v1 counted forms:
- `if`, `if-not`, `when`, `when-not`
- `if-let`, `if-some`, `when-let`, `when-some`
- `cond` (including trailing `:else` branch when present)
- `condp` (including default clause when present)
- `case` (including default branch when present)
- `and`, `or`
- `catch`
- `cond->`

Recommended v1 exclusions:
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
- `--top` must be a positive integer
- unknown `--sort` keys should be rejected

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
- optional namespace rollups using compact metric-qualified aggregate field names such as `:total-cc`, `:avg-cc`, `:max-cc`, and `:cc-risk-counts`
- optional project rollup using the same compact metric-qualified aggregate field names
- any applied sort/top options for reproducibility
- human output examples should be included in the implementation companion doc to lock formatting expectations

Risks:
- ambiguity in mapping classic cyclomatic complexity onto Lisp syntax
- noise if common macros are misclassified as decision points
- parser/source-location limitations affecting unit identity and UX
- feature overlap/confusion with the separate Local Comprehension Complexity track
- surprising UX around sort/top semantics if not specified tightly

Mitigations:
- document the exact v1 counted constructs and exclusions
- keep v1 conservative and syntax-based
- emit stable machine-readable schema so heuristics can evolve later
- position this as a standard branch-complexity lens, not a full maintainability model
- define sort/top semantics explicitly in the implementation companion doc before coding
