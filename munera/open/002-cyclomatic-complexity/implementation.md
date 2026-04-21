2026-04-21

Task created to add a standard cyclomatic complexity lens to Gordian.

Refinements agreed collaboratively:
- canonical unit = arity-level top-level executable body
- scoring style = strict explicit branching, with `cond->` included
- default scope = discovered source paths only
- rollups = additional sections
- output includes `:cc-decision-count`, but not detailed per-decision source points in v1
- add `--top` and `--sort` options
- default branch clauses in `cond`, `condp`, and `case` count as branches
- loops and recursion do not independently increase `:cc` in v1; only explicit branching and boolean decision forms do
- branches inside loop or recursive bodies still count normally
- keep simple v1 sort keys only: `cc`, `ns`, `var`, `cc-risk`
- `--top` applies independently per report section
- use compact metric-qualified schema and report field names (`:metric :cyclomatic-complexity`, `:cc`, `:cc-decision-count`, `:cc-risk`, `:total-cc`, `:avg-cc`, `:max-cc`, `:cc-risk-counts`) so other complexity metrics can be added later without ambiguity

Requested outcomes:
- complexity metric for Clojure or Babashka code
- surfaced as a Gordian sub-command
- default scope = discovered project source paths
- alternate scopes = source-only, tests-only, or explicit paths
- output modes = human, EDN, JSON
- optional namespace rollup
- optional project rollup
- human output includes horizontal bar chart
- standard risk levels:
  - 1–10  Simple, low risk
  - 11–20 Moderate complexity, moderate risk
  - 21–50 High complexity, high risk
  - 51+   Untestable, very high risk

Notes:
- this is intentionally separate from the existing Local Comprehension Complexity task
- v1 should prefer deterministic syntax-first counting over semantic inference or macroexpansion
- the task is now refined enough to begin implementation planning with exact counted forms, unit extraction rules, canonical schema, CLI validation rules, and human-output expectations all specified

Implementation-ready specifics to preserve:
- analyzable units in v1:
  - `defn` / `defn-` => one unit per arity body
  - `defmethod` => one unit per method body
  - top-level `def` with literal `fn` value => one unit per arity body
- deferred from v1:
  - `extend-type`, `extend-protocol`, `reify`, and nested `fn` as standalone units
- CLI validation:
  - reject `--source-only` with `--tests-only`
  - reject explicit paths with either scope flag
  - reject `--json` with `--edn`
  - reject unknown `--sort` keys
  - reject non-positive `--top` values
- canonical schema should include unit identity fields (`:ns`, `:var`, `:kind`, `:arity`, `:dispatch`, `:file`, `:line`, `:origin`) plus `:cc`, `:cc-decision-count`, and `:cc-risk`
- human output should include example-driven formatting expectations for units, namespace rollups, project rollup, and horizontal bars
