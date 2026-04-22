2026-04-21

Task created to add a standard cyclomatic complexity lens to Gordian.

Current status note:
- the implementation now satisfies the task-defining requirements in the codebase (`src/gordian/cyclomatic.clj`, CLI wiring in `main.clj`, output/tests, README/schema/docs)
- any remaining work is cleanup/polish rather than core task completion

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
- namespace rollup
- project rollup
- human output includes horizontal bar chart
- standard risk levels:
  - 1–10  Simple, low risk
  - 11–20 Moderate complexity, moderate risk
  - 21–50 High complexity, high risk
  - 51+   Untestable, very high risk

Convergence result:
- canonical public command is now `gordian complexity`
- `gordian cyclomatic` remains as a compatibility alias
- canonical unit model is now arity-level executable bodies
- extraction covers `defn` / `defn-`, `defmethod`, and top-level `def` + literal `fn`
- scoring rules now match the accepted v1 design including `cond->`, default-branch handling, and loop/iteration exclusions
- canonical machine-readable schema now uses metric-qualified fields such as `:cc`, `:cc-decision-count`, `:cc-risk`, `:total-cc`, `:avg-cc`, `:max-cc`, and `:cc-risk-counts`
- default complexity scope is discovered source paths only, with `--tests-only` / `--source-only` support and validation
- sort/top behavior is implemented
- human output includes canonical units / rollups and horizontal bars

Notes:
- this is intentionally separate from the existing Local Comprehension Complexity task
- v1 should prefer deterministic syntax-first counting over semantic inference or macroexpansion
- the task is now refined enough to converge the prototype against the accepted design with exact counted forms, unit extraction rules, canonical schema, CLI validation rules, and human-output expectations all specified

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

Recommended implementation sequence:
1. write the companion implementation doc that locks command naming / compatibility, finalized scoring semantics, schema, sort/top semantics, and output examples
2. refactor extraction so the pure core emits canonical arity-level units
3. align scoring semantics exactly with the accepted v1 rules
4. add risk classification and canonical metric-qualified schema
5. add pure sorting / section-local truncation helpers
6. align CLI scope resolution and validation semantics with the accepted design
7. finish text output with horizontal bar charts
8. expand tests to cover the final shape and compatibility decisions

Patch plan from task review:
1. payload metadata alignment
   - add explicit complexity-payload metadata for `:scope` and `:options`
   - keep it consistent with the Gordian envelope rather than duplicating conflicting state
   - include at least resolved scope mode/paths plus applied `:sort`, `:top`, and `:min-cc`
   - preserve the existing Gordian machine-readable convention that EDN and JSON outputs are wrapped in the standard envelope, as with other analysis commands
   - λanalysis_output.
     end(output) ∧ format(json ∨ edn)
     → envelope{
         input-options,
         purpose(re-run(analysis) ∧ reproducible)
       }
2. rollup-surface decision ✅
   - resolved policy: rollups are always emitted in v1
   - removed `--namespace-rollup` / `--project-rollup` optionality from task/design/docs
   - result: one clear policy, no half-documented optionality, and simpler payload/output expectations
3. source-location decision — deferred
   - either populate `:line` from parsed forms where feasible
   - or explicitly document `:line` as nullable/deferred in v1 and keep tests/schema aligned with that decision

Code-shaper cleanup checklist:
1. pure report finalization ✅
   - extracted complexity-specific payload shaping from `main.clj` into pure helpers in `gordian.cyclomatic`
   - includes payload metadata assembly plus display transformations (`:min-cc`, `:sort`, `:top`)
   - result: `main.clj` is thinner and more focused on path resolution, scanning, envelope wrapping, and output dispatch
2. text table rendering simplification ✅
   - factored complexity text-table header/row/rule construction into small private helpers in `output.clj`
   - avoided introducing a generic table engine; used local helpers to reduce repeated inline layout code
   - result: current output preserved while formatting logic became easier to edit safely
3. terminology tightening ✅
   - renamed command-facing entrypoints/renderers toward `complexity` terminology where helpful (`complexity-cmd`, `format-complexity`, `print-complexity`)
   - kept metric-specific implementation language centered on `cyclomatic` in the analysis namespace and schema
   - result: less convergence-history leakage and a cleaner conceptual boundary between command and metric
