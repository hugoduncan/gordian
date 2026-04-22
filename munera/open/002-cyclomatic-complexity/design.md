Implement cyclomatic complexity analysis for Clojure/Babashka code as a Gordian command.

Intent:
- add a simple, standard, code-in-the-small complexity metric to Gordian
- make it easy to inspect complexity hotspots across a whole project, just source, just tests, or explicit paths
- support both machine-readable output and useful human-readable triage output

Problem statement:
- Gordian currently focuses on architectural structure and coupling, but has no standard local code-risk metric
- users need a lightweight way to identify functions or methods whose branch complexity implies higher defect risk and test burden
- the result must fit Gordian’s existing command style: pure analysis core, thin IO shell, auto-discovery defaults, and multiple output modes

Scope:
In scope:
- cyclomatic complexity for Clojure or Babashka source files
- a new Gordian sub-command dedicated to cyclomatic complexity analysis
- default target = discovered project source paths only
- alternate target selection modes:
  - source only
  - tests only
  - explicit paths
- output modes:
  - human-readable text
  - EDN
  - JSON
- namespace rollup
- project rollup
- `--top` and `--sort` options
- human output includes horizontal bar-chart style visualisation
- standard risk levels:
  - 1–10  Simple, low risk
  - 11–20 Moderate complexity, moderate risk
  - 21–50 High complexity, high risk
  - 51+   Untestable, very high risk

Out of scope:
- cognitive complexity
- Local Comprehension Complexity / burden-vector work
- historical trending
- editor integration
- clj-kondo-based semantic analysis
- quality gates or compare integration
- per-expression source highlighting beyond what is needed to identify the analyzed unit
- markdown output unless added later for consistency

Acceptance criteria:
- `gordian complexity` exists as a sub-command
- with no path arguments, the command analyzes the discovered project using Gordian’s standard directory discovery defaults for source paths only
- user can restrict scope to source-only or tests-only modes, or provide explicit paths
- explicit paths override discovery-based scope selection
- output supports text, EDN, and JSON
- text output includes a horizontal bar chart for each reported unit or aggregate row
- every reported cyclomatic complexity value is classified into the standard risk bands above
- canonical reporting unit is arity-level for top-level executable bodies
- output includes `:cc-decision-count` alongside `:cc` and `:cc-risk`
- namespace rollup groups results by namespace
- project rollup summarizes project-wide totals/distribution
- `--top` limits reported rows independently within each applicable section
- `--sort` controls ordering of reported units/rollups using documented simple v1 sort keys
- implementation follows Gordian’s pure-core / thin-IO architecture style
- tests cover scoring rules, rollups, sort/truncation behavior, output shape, and CLI option behavior

Minimum concepts:
- analysis unit: a top-level executable body at arity granularity
- cyclomatic decision point: syntax forms that increase cyclomatic complexity
- cyclomatic score: base cyclomatic complexity plus decision increments
- cc decision count: number of counted cyclomatic decision points contributing above the base score
- cc risk band: standard classification derived from cc
- rollup: aggregation of cc by namespace or project
- report: canonical data structure consumed by text / EDN / JSON renderers

Refined design decisions:
- canonical reporting unit = arity-level top-level executable body
- report per `defn` arity, `defmethod` body, and top-level `def`-bound `fn` body where extractable
- nested anonymous functions are folded into the enclosing top-level unit in v1
- default scope = discovered source paths only
- rollups are additional sections, not alternate views
- include metric-specific compact fields in canonical output, e.g. `:metric :cyclomatic-complexity`, `:cc`, `:cc-decision-count`, and `:cc-risk`; detailed per-decision source points are deferred

Scoring model v1:
- base complexity = 1 per unit
- syntax-first, deterministic, no macroexpansion
- strict explicit branching model, with one agreed Clojure-specific inclusion: `cond->`

Count `+1` for each occurrence of:
- `if`
- `if-not`
- `when`
- `when-not`
- `if-let`
- `if-some`
- `when-let`
- `when-some`
- `cond` — `+1` per test/result clause pair, including trailing `:else` branch when present
- `condp` — `+1` per clause pair, including default clause when present
- `case` — `+1` per branch, including default branch when present
- `and` — `+1` per additional operand after the first
- `or` — `+1` per additional operand after the first
- `catch` — `+1` per catch clause
- `cond->` — `+1` per condition/form pair

Explicit v1 exclusions:
- `->`, `->>`, `as->`, `some->`, `some->>`
- `cond->>` unless consciously added during implementation-plan refinement
- `let`, `letfn`, `binding`
- `loop`, `recur`, self-recursion, mutual recursion, `for`, and `doseq` as independent cc increments
- plain function calls
- higher-order sequence functions such as `filter`, `mapcat`, `reduce`, `keep`, `remove`
- macroexpansion-derived branches
- data-driven dispatch not syntactically visible
- separate reporting of nested anonymous functions

Loop handling in v1:
- loops and recursion do not independently increase `:cc`
- branches inside loop or recursive bodies still count normally via the explicit counted forms above
- higher-order iteration forms also do not independently increase `:cc`

Implementation shape guidance:
- prefer syntax-first analysis over semantic inference
- start with deterministic form-based counting over parsed code
- keep a canonical report data model, with output formatting layered above it
- reuse existing Gordian discovery / typed path handling where possible
- preserve provenance so units can be attributed to src vs test and to explicit files

Exact v1 unit extraction:
- include top-level `defn` and `defn-` forms
  - each declared arity is a separate reported unit
  - single-arity functions produce one unit
  - multi-arity functions produce one unit per arity body
- include top-level `defmethod` forms
  - each method body is one reported unit
  - dispatch value should be preserved in unit identity when available
- include top-level `def` whose value is a literal `fn`
  - each arity of that `fn` is a separate reported unit
- defer in v1:
  - `extend-type`
  - `extend-protocol`
  - `reify`
  - protocol implementation bodies expressed through extension forms
  - nested `fn` as standalone reported units

Exact CLI surface and validation rules:
- command forms:
  - `gordian complexity`
  - `gordian complexity .`
  - `gordian complexity src/ test/`
- supported flags:
  - `--source-only`
  - `--tests-only`
  - `--json`
  - `--edn`
  - `--top N`
  - `--sort KEY`
- validation rules:
  - reject `--source-only` combined with `--tests-only`
  - reject explicit paths combined with `--source-only` or `--tests-only`
  - reject `--json` combined with `--edn`
  - reject unknown `--sort` keys
  - reject non-positive `--top` values

Sort options:
- support simple v1 sort keys: `cc`, `ns`, `var`, `cc-risk`
- default ordering = `cc` (descending cc, then namespace, var, arity identity)
- `ns` = ascending namespace, then descending cc
- `var` = ascending var identity, then descending cc
- `cc-risk` = descending cc risk severity, then descending cc
- no separate sort-direction flag in v1

Canonical EDN shape example:
```clojure
{:command :complexity
 :metric :cyclomatic-complexity
 :scope {:mode :discovered
         :source? true
         :tests? false
         :paths ["src" "components/foo/src"]}
 :options {:sort :cc
           :top 20}
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
                    :label "Moderate complexity, moderate risk"}}]
 :namespace-rollups [{:ns 'gordian.main
                      :unit-count 6
                      :total-cc 54
                      :avg-cc 9.0
                      :max-cc 14
                      :cc-risk-counts {:simple 4 :moderate 2 :high 0 :untestable 0}}]
 :project-rollup {:unit-count 187
                  :namespace-count 24
                  :total-cc 811
                  :avg-cc 4.3
                  :max-cc 27
                  :cc-risk-counts {:simple 170 :moderate 13 :high 4 :untestable 0}}}
```

Human output example:
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

Presentation notes:
- human output is sorted according to `--sort`
- `--top N` applies independently to the units section and any rollup sections
- unit bars are scaled from raw `cc`, capped at a reasonable maximum width such as 50
- namespace rollup bars should use `max-cc` rather than `total-cc` to remain visually interpretable

Done means:
- the task has an implementation-ready plan with clear scoring rules, unit extraction rules, output schema, CLI surface, validation rules, sort/truncation semantics, and example outputs suitable for building in small commits
