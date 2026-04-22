# Gordian — Working State

**Last updated:** 2026-04-21

## What this project is

`gordian` is a Babashka script that analyzes the `require` graph of Clojure
projects. It reports structural coupling metrics that surface architectural
complexity.

Usage: `gordian <command>` with scoped `--help`; no explicit command defaults to analyze. `gordian` auto-discovers from cwd; `gordian src/` remains accepted for explicit dirs.

## Current status

**v0.2.0 alpha.** Schema envelope, façade detection, family-noise suppression,
explain-pair verdicts, family-scoped metrics, auto-discovery, config,
diagnose, explain, markdown, dedicated `tests` mode, DSM, completed
`complexity` mode with cyclomatic complexity + LOC, simplified human-readable
complexity output with explicit `--bar` metric selection, and scoped CLI
subcommand help with command-local parsing.
329 tests, 2686 assertions, 0 failures.

## Architecture (src/gordian/)

```
scan.clj       .clj files → {ns→#{direct-deps}}           IO
close.clj      transitive closure (BFS, cycle-safe)        pure
aggregate.clj  propagation cost, reach, fan-in             pure
metrics.clj    Ca, Ce, instability (Robert Martin)         pure
scc.clj        Tarjan SCC → cycle detection                pure
classify.clj   core/peripheral/shared/isolated roles       pure
output.clj     format-report → vector of lines             pure
dot.clj        Graphviz DOT string                         pure
json.clj       JSON string (cheshire)                      pure
main.clj       pipeline wiring + CLI entry point           IO
```

## Self-analysis result

`bb gordian src/` → PC=9%, star topology, no cycles.
`gordian.main` is the only peripheral (wires everything). All other modules
are core — pure, independent, maximally stable.

## Architecture (src/gordian/) — current

```
scan.clj       edamame parse .clj → {ns→#{direct-deps}};   IO
               scan-dirs merges multiple directories;
               scan-terms / scan-terms-dirs (full-file parse → {ns→[term]})
close.clj      transitive closure (BFS, cycle-safe)         pure
aggregate.clj  propagation cost, reach, fan-in              pure
metrics.clj    Ca, Ce, instability (Robert Martin)          pure
scc.clj        Tarjan SCC → cycle detection                 pure
classify.clj   core/peripheral/shared/isolated roles        pure
conceptual.clj tokenize, extract-terms, build-tfidf,        pure
               cosine-sim, coupling-terms, conceptual-pairs
output.clj     human-readable table + conceptual section    pure
dot.clj        Graphviz DOT string                          pure
json.clj       JSON string (cheshire)                       pure
edn.clj        EDN string (clojure.pprint)                  pure
main.clj       pipeline + thin CLI dispatch shell           IO
cli.clj        command registry + scoped help + parsing     pure-ish
```

## CLI

```
gordian <command> [args] [options]

gordian --help              ; top-level commands + global options only
gordian <subcommand> --help ; scoped help for that command

Commands:
  analyze      (default) Raw metrics + optional coupling sections
  diagnose     Ranked findings (auto-enables all lenses) + clusters
  compare      Compare two EDN snapshots (before.edn after.edn)
  gate         Compare current state against a saved baseline
  subgraph     Family/subsystem view for a namespace prefix
  communities  Discover latent architecture communities
  dsm          Dependency Structure Matrix view
  tests        Test architecture analysis
  complexity   Local code metrics (cyclomatic + LOC)
  explain      Everything about a namespace
  explain-pair Everything about a pair

Top-level global options:
  --json
  --edn
  --markdown
  --help
```

Implementation shape now:
- `src/gordian/cli.clj` — command registry, scoped help assembly, command-local parse/validate
- `src/gordian/main.clj` — thin entrypoint/dispatcher over command definitions and handlers
  --conceptual <float>   Conceptual coupling threshold
  --change [<repo-dir>]  Change coupling (git log)
  --change-since <date>  Limit change horizon
  --include-tests        Include test dirs
  --exclude <regex>      Exclude namespaces
  --help                 Usage summary
```

Output modes are mutually exclusive (--json / --edn / --markdown).
Can combine --dot with any output mode.
Multiple src-dirs merged into one graph (e.g. src/ test/).
Cycles section only appears when cycles are present.

## Commits this session

```
740cfb3  task 1  bb.edn + CLI skeleton
da49b4a  task 2  scan .clj → require graph
4697ffd  task 3  transitive closure
99f0c40  task 4  propagation cost + reach/fan-in
4204b8e  task 5  output + full pipeline
1afb98c  step 6  Ca/Ce/instability
4ad7a93  step 7  SCC cycle detection (Tarjan)
db372c0  step 8  core/periphery classification
2671aab  step 9  DOT file output
265b475  step 10 JSON report output
ab291d3  step 11 bbin install + self-analysis
```

## Session 4 commits

```
e49853e  move fixtures to resources/
d5ff590  fix: edamame parse-next (was silently failing on 6 test files)
bd7c02c  split unit/integration tests; fix dot/generate :src-dirs
         PC src/+test/ 21.5% → 14.7% → 9.8%
```

## Session 3 commits

```
f3176ff  multiple src-dirs (scan-dirs, :src-dirs throughout)
8887411  cycles section omitted when none present
8be5bbb  README — full metric descriptions, updated usage
```

## Session 2 commits

```
d9192ec  edamame parsing (+ :fn :deref :regex options)
ef2ee7d  --help switch (babashka.cli, parse-args redesign)
7bebd09  --dot <file> option; fix edamame options for #() @deref #regex
ed5b520  --json to stdout, suppress table
3613b3c  --edn output option (native Clojure types preserved)
```

## Conceptual coupling — implemented (session 5)

`gordian analyze src/ --conceptual 0.30` appends a conceptual coupling section.

New modules:
- `conceptual.clj` — tokenize, extract-terms, build-tfidf, cosine-sim, coupling-terms, conceptual-pairs (pure)
- `scan.clj` extended — scan-terms, scan-terms-dirs (full-file edamame parse)
- `output.clj` extended — format-conceptual (before format-report; ordering matters)
- `main.clj` extended — --conceptual <float> CLI flag, build-report arity-2

Self-analysis (0.20 threshold) reveals:
- `gordian.close` ↔ `gordian.scc` sim=0.24, no structural edge ← graph traversal siblings
- `gordian.conceptual` is now a `core` module (Ca=2, Ce=0) — scan + main both depend on it

Tokenization note: backtick `` ` `` is not in the split pattern; docstring markdown
`` `graph` `` appears as a token. Minor artifact, future refinement.

## Session 5 commits

```
d89aa88  step 1  tokenize + extract-terms
80634eb  step 2  scan-terms + scan-terms-dirs
82eceaa  step 3  build-tfidf
b12f097  step 4  cosine-sim + coupling-terms
2742495  step 5  conceptual-pairs
587ffee  step 6  format-conceptual
7492713  step 7  wire --conceptual into CLI + pipeline
```

401 assertions, 71 tests, 0 failures.

## Session 6 commits — tokenizer improvements

```
943d8d9  fix: tokenize splits on [^a-zA-Z0-9]+ — removes all punctuation noise
a30643e  fix: add stop-words to tokenize — suppresses english function words
382131f  feat: add stemmer — suffix rules for -ing/-ed/-er/-es/-s/-able/-ness/-ly
```

445 assertions, 72 tests, 0 failures.

Self-analysis at 0.20 threshold after improvements:
- `gordian.close ↔ gordian.aggregate` sim=0.31, no structural edge
  shared: "reach transitive close" — reachability computation siblings ← genuine signal
- `gordian.scan ↔ gordian.main` sim=0.23, structural edge (expected)

Before: terms were `` "as `graph` but" `` — punctuation noise.
After:  terms are "reach transitive close" — clean domain vocabulary.

Key stemmer design decisions:
- `-es` only for sibilant stems (ss/x/z): "processes"→"process" ✓, "namespaces"→"namespace" ✓
  (without the guard, "namespaces"→"namespac" which doesn't match "namespace")
- `-s` guard: skip words ending in ss/us/is ("class", "status", "analysis" protected)
- dedup-final collapses double consonants: "scann"→"scan", "coupl"→"coupl" (only doubles)
- Stop words applied before stemming; length filter applied after stemming (safety net)

## Session 7 commits — conceptual coupling performance

```
26d24f0  perf: algorithmic speedups for conceptual coupling analysis
6dc3068  perf: parallelise all independent hot paths with pmap
```

473 assertions, 76 tests, 0 failures.

Algorithmic (session 7a):
1. `memoize stem` — same token stemmed at most once per process
2. `normalize-tfidf` — unit vectors pre-computed; cosine = dot product (no sqrt per pair)
3. Inverted index in `conceptual-pairs` — only pairs sharing ≥1 term evaluated (skips zero-dot pairs)
4. `dot-and-top-terms` — fused single pass replaces separate `cosine-sim` + `coupling-terms` calls
5. `parse-file-all` / `scan-all` / `scan-all-dirs` — single file read+parse when `--conceptual` active

Parallel (session 7b):
- `scan`, `scan-terms`, `scan-all` — `pmap parse-file*` (IO + edamame parse per file)
- `build-tfidf` — df sequential; per-ns weight vectors via `pmap`
- `normalize-tfidf` — `pmap` over ns vectors
- `conceptual-pairs` — chunked `pmap` over candidate pairs (not per-pair: per-pair overhead > per-pair work)
- 12 cores available

Critical bug fix (session 7c):
- `eval-candidates` was returning a lazy `(keep ...)` seq from inside `pmap`
- pmap futures completed instantly; all eval happened on main thread via `(apply concat)`
- This is why it appeared single-core — it WAS single-core
- Fix: `(into [] (keep ...) pairs)` forces eval inside the future; `(into [] cat ...)` replaces `(apply concat)`
- Memory stored: `mementum/memories/pmap-lazy-seq-antipattern.md`

New public API: `normalize-tfidf`, `parse-file-all`, `scan-all`, `scan-all-dirs`

## Session 8 commits — real-world hang + transducer perf

```
c66b391  fix+perf: syntax-quote hang + lazy chain → transducer in hot paths
```

76 tests, 473 assertions, 0 failures.

Critical bug: `parse-opts` was missing `:syntax-quote true`.
- edamame throws per sub-token inside defmacro backtick body
- `::skip` handler catches but reader stays inside the form → infinite loop
- Symptom: hang on any file containing `defmacro` with `` `(...) `` body
- Fix: add `:syntax-quote true` to `parse-opts` in `scan.clj`
- Memory: `mementum/memories/edamame-syntax-quote-hang.md`

Perf: lazy `->>` → transducers (user-suggested pattern):
- `tokenize`: `(into [] (comp ...) tokens)` — 1 pass, 4 fewer lazy seqs
- `extract-terms`: `(into ns-tokens (comp filter mapcat) forms)`
- `deps-from-ns-form`: `(into #{} (comp mapcat keep) req-clauses)`
- `build-tfidf` df: nested `reduce` instead of `(mapcat distinct) + frequencies`

Result: `bb gordian components/*/src --conceptual 0.20` → **0.61s** (158 files, 17 components, was hanging)

## Session 9 commits — structural refactor: scan→conceptual edge removed

```
d3c0727  refactor: inject terms-fn into scan — removes scan→conceptual structural edge
```

76 tests, 473 assertions, 0 failures.

Self-analysis (src/) after:
- PC 8.3% → 7.6%
- `gordian.scan`: peripheral (I=0.50, Ce=1) → **core (I=0.00, Ce=0)**
- `gordian.conceptual`: Ca=2 → Ca=1 (main only)
- `conceptual↔scan`: was structural edge → now no structural edge (informational, sim=0.23)
- Only `gordian.main` is peripheral — perfect star topology

Change: six terms-related functions in scan.clj (`parse-file-terms`, `scan-terms`,
`scan-terms-dirs`, `parse-file-all`, `scan-all`, `scan-all-dirs`) now take
`terms-fn` as first arg. main.clj passes `conceptual/extract-terms` at the
single call site in `build-report`.

## Session 11 commits — schema normalization + auto-discovery

```
63fe1e6  refactor: normalize pair schema — :sim/:coupling → :score, add :kind tag
4d3d705  test: add fixture-project and fixture-polylith layouts
58b7499  feat: discover.clj — project layout detection
8644ea5  feat: config.clj — .gordian.edn loading and merge
9931ee5  feat: filter.clj — namespace exclusion by regex
35c5ccf  feat: gordian . — auto-discovery, config, --exclude, --include-tests
8624e3a  docs: README and PLAN for auto-discovery + config
```

102 tests, 760 assertions, 0 failures.

Schema normalization:
- All pair records now share {:ns-a :ns-b :score :kind :structural-edge?}
- Conceptual: :sim → :score, :kind :conceptual
- Change: :coupling → :score, :kind :change
- Enables generic downstream consumers (diagnose, explain, diff)

Auto-discovery:
- `gordian` / `gordian .` auto-discovers src dirs from project root
- Detects deps.edn, bb.edn, project.clj, workspace.edn (Polylith)
- Probes src/, test/, components/*/src, bases/*/src, etc.
- `--include-tests` adds test directories
- `--exclude <regex>` filters namespaces (repeatable)
- `.gordian.edn` config file: project-local defaults, CLI overrides

New modules:
- `discover.clj` — project-root?, discover-dirs, resolve-dirs (pure)
- `config.clj` — load-config, merge-opts (thin IO)
- `filter.clj` — filter-graph (pure)

Architecture (src/gordian/) — current:
```
scan.clj        edamame parse .clj → {ns→#{deps}}; terms     IO
git.clj         git log → commit/co-change data              IO
config.clj      .gordian.edn loading + merge                 IO (thin)
discover.clj    project layout detection                     pure
filter.clj      namespace exclusion by regex                 pure
close.clj       transitive closure (BFS, cycle-safe)         pure
aggregate.clj   propagation cost, reach, fan-in              pure
metrics.clj     Ca, Ce, instability (Robert Martin)          pure
scc.clj         Tarjan SCC → cycle detection                 pure
classify.clj    core/peripheral/shared/isolated roles        pure
conceptual.clj  TF-IDF + cosine coupling                    pure
cc_change.clj   change-coupling-pairs (Jaccard)              pure
diagnose.clj    ranked findings (7 categories)               pure
explain.clj     drill-down queries (ns + pair + verdict)     pure
compare.clj     snapshot diff (health, nodes, pairs, etc.)   pure
cluster.clj     union-find finding clustering                pure
gate.clj        CI / ratchet checks over compare diffs       pure
prioritize.clj  actionability scoring and ranking            pure
subgraph.clj    family/subsystem slicing and summaries       pure
communities.clj architecture community discovery             pure
tests.clj       test architecture analysis                   pure
envelope.clj    metadata envelope for EDN/JSON               pure
output.clj      human-readable table + markdown              pure
dot.clj         Graphviz DOT string                          pure
json.clj        JSON string (generic walker)                 pure
edn.clj         EDN string (clojure.pprint)                  pure
main.clj        pipeline + CLI + discovery wiring            IO
```

## Session 11b commits — gordian diagnose

```
d41280e  feat: diagnose — pair-key, health, find-cycles
1c2e1ca  feat: diagnose — find-sdp-violations, find-god-modules, find-hubs
6f498d9  feat: diagnose — cross-lens, hidden-conceptual, hidden-change pair findings
9932a71  feat: diagnose — diagnose function + severity sorting
1108f95  feat: output — format-diagnose + print-diagnose
6785576  feat: gordian diagnose — ranked findings subcommand
07db9ab  docs: README and PLAN for diagnose mode
```

115 tests, 849 assertions, 0 failures.

Diagnose subcommand:
- `gordian diagnose` auto-enables conceptual (0.15) + change coupling
- Cross-lens pair matching: hidden in both lenses → :high severity
- Seven finding categories: cycle, cross-lens-hidden, hidden-conceptual,
  hidden-change, sdp-violation, god-module, hub
- Sorted by severity then score
- Supports --edn and --json with :findings and :health keys

New module: `diagnose.clj` (pure — 10 functions, 0 IO)

## Session 11c commits — gordian explain

```
cf416ab  feat: explain — shortest-path, direct-deps, direct-dependents, ns-pairs
712fbfc  feat: explain — explain-ns, explain-pair-data composite queries
da36546  feat: output — format-explain-ns, format-explain-pair
0530a50  feat: gordian explain / explain-pair — drill-down investigation
```

127 tests, 945 assertions, 0 failures.

Explain subcommands:
- `gordian explain gordian.scan` — everything about a namespace
- `gordian explain-pair gordian.aggregate gordian.close` — everything about a pair
- Auto-enables all lenses, supports --edn/--json
- BFS shortest-path with parent tracking
- Project vs external dep split
- Diagnosis annotation on explain-pair

New module: `explain.clj` (pure — 6 functions, 0 IO)

## Session 11d commits — markdown output

```
03e3597  feat: output — format-report-md (analyze markdown)
027db49  feat: output — format-diagnose-md
4bac2fe  feat: output — format-explain-ns-md, format-explain-pair-md
2785842  feat: --markdown flag for all commands
```

133 tests, 988 assertions, 0 failures.

`--markdown` flag on all four commands (analyze, diagnose, explain,
explain-pair). Mutually exclusive with --json/--edn. Markdown tables,
severity emoji, backtick namespaces, sections omitted when empty.

## Session 13 commits — Phase C (compare + clusters)

```
5b2234e  feat: compare mode — gordian compare before.edn after.edn
afec86d  feat: diagnose finding clusters via union-find
```

## Session 14 commits — Phase D1 (gate mode)

```
8a30e8f  feat: gate mode — CI / refactor ratchet
```

## Session 15 commits — Phase D2 (actionability ranking)

```
be1670d  feat: actionability ranking for diagnose
```

## Session 16 commits — Phase D3 (subgraph views)

```
a668948  feat: subgraph views + explain prefix fallback
```

## Session 17 commits — Phase D4 (communities)

```
77a8e1b  feat: communities discovery command
b34940c  docs: update PLAN for completed Phase D work and remaining follow-ups
5e196a0  docs: update state with communities commit hash
```

## Session 18 commits — test architecture mode

```
93240bf  refactor: extract structural-report-from-graph
93024ea  feat: add typed src/test path discovery
ad8737e  feat: preserve namespace origins during scan
cd457a8  feat: add tests.clj role and graph helpers
7d7e93b  feat: classify test namespaces by role and style
fda83c1  feat: detect test architecture invariant violations
3dc60b7  feat: compare src and src+test architecture
292280e  feat: add ranked findings for test mode
c706417  feat: assemble tests command report
b37aa07  feat: add text and markdown output for tests command
74e8bd7  feat: add gordian tests subcommand
```

242 tests, 1564 assertions, 0 failures.

Communities (`gordian communities`):
- new `communities.clj` pure module
- lens modes: structural, conceptual, change, combined
- canonical undirected weighted edge model
- connected-components community detection (v1)
- thresholding for non-structural lenses
- per-community density, internal weight, boundary weight
- dominant terms from conceptual shared terms
- bridge namespace heuristic
- text / markdown / EDN / JSON output

Subgraph views (`gordian subgraph <prefix>`):
- new `subgraph.clj` pure slicing module
- prefix matching + member selection
- induced graph metrics: nodes, edges, density, propagation cost, cycles
- boundary metrics: incoming/outgoing edges, dependents, external deps
- pairs sliced into `:internal` and `:touching`
- touching findings + local reclustering
- `gordian explain <prefix>` falls back to subgraph when no exact ns exists
- text / markdown / EDN / JSON output

Actionability ranking (`gordian diagnose --rank actionability`):
- new `prioritize.clj` pure ranking module
- `:actionability-score` added to all findings
- cluster-aware ranking context
- `--rank severity|actionability` on diagnose
- text and markdown show `[act=N.N]`
- EDN/JSON include `:actionability-score`
- diagnose output now records `:rank-by`

Gate mode (`gordian gate --baseline base.edn`):
- builds current diagnose-style report
- compares against saved baseline snapshot
- evaluates pass/fail checks
- default checks: pc-delta, new-cycles, new-high-findings
- optional thresholds: `--max-pc-delta`, `--max-new-high-findings`, `--max-new-medium-findings`
- explicit selection: `--fail-on new-cycles,new-high-findings`
- text / markdown / EDN / JSON output
- exit code 0 on pass, 1 on fail

New module:
- `gate.clj` — pure gate checks + report assembly

190 tests, 1372 assertions, 0 failures.

Compare mode (`gordian compare before.edn after.edn`):
- Reads two EDN snapshots, produces structured diff
- Health delta (PC, cycles, ns-count with direction arrows)
- Node changes: added, removed, changed (metric deltas)
- Cycle changes: added, removed
- Pair changes: added, removed, score delta (conceptual + change)
- Finding changes: added, removed
- All 4 output modes: text, markdown, JSON, EDN

Finding clusters (`gordian diagnose`):
- Union-find on finding subjects (shared namespace mentions)
- Groups related findings into clusters (≥ 2 findings)
- Sorted by max severity, then cluster size
- Unclustered findings shown separately
- Both text and markdown output
- EDN/JSON include :clusters and :unclustered keys

New modules:
- `compare.clj` — pure comparison functions
- `cluster.clj` — pure union-find + clustering

## Roadmap

See PLAN.md. Core Phase D complete.

Remaining strategic work:
- compare `--ref` mode
- gate follow-ups (richer checks, cluster-aware checks, baseline workflows)
- community-detection follow-ups (label propagation / modularity, richer bridge metrics)

Deferred (v2):
- change window comparison
- presets

## Glossary experiment — attempted, then reverted

A `gordian glossary` feature was prototyped and then removed.

Why it was reverted:
- current conceptual analysis is token-based, not phrase-based
- on gordian itself the output was dominated by root namespace and code-shape
  tokens like `gordian`, `node`, `pair`, `map`, `return`
- the genuinely useful concepts were mostly multi-word phrases such as
  "change coupling" and "propagation cost", which the current TF-IDF token
  pipeline does not represent well

Conclusion:
- token vocabulary extraction may still be useful as a low-level signal
- a real glossary likely needs phrase-level concepts or much stronger
  evidence filtering before it is worth shipping

Memory: `mementum/memories/token-vocabulary-is-not-a-glossary.md`

## Session 12 — user feedback analysis

Real-world feedback from AI assistant using gordian on ~158-file Polylith
project. 12 items analyzed and sequenced into phases. See
`doc/design/006-user-feedback-analysis.md` for full analysis.

**Phase A — Schema & Metadata** ✅ done.

**Phase B — Signal Quality** (in progress):
- F5 ✅: Façade-aware interpretation — family.clj + family-scoped Ca/Ce
- F3 ✅: Family-noise suppression — annotate pairs, downgrade naming noise
- F7 ✅: Explain-pair verdict — 8 interpretation categories
  - Pure rule evaluation over structural/conceptual/change signals
  - Text + markdown VERDICT section with emoji severity hints
  - Categories: expected-structural, family-naming-noise, family-siblings,
    likely-missing-abstraction, hidden-conceptual, hidden-change,
    transitive-only, unrelated

Session 12b commits (F5):
```
1226c88  step 1  feat: family.clj
b953717  step 2  feat: wire family-metrics into pipeline
683a8e8  step 3  feat: façade detection in diagnose
3fcf27b  step 4  feat: surface in explain output
bd1c952  step 5  docs: schema + PLAN
```

Session 12c commits (F3):
```
bff010a  step 0  refactor: extract tokenizer into gordian.text
6115880  step 1  feat: annotate-conceptual-pairs
7b049af  step 2  feat: wire into build-report
ab937ac  step 3  feat: severity adjustment in diagnose
944585a  step 4  feat: output formatting
```

**Phase A — Schema & Metadata** ✅ done.
- F2: Stable EDN/JSON schema envelope — `:gordian/version`, `:lenses`, thresholds
- F6: Surface thresholds/lens activation in output — in `:lenses` section
- F9: Change-coupling diagnostics when empty — `:candidate-pairs`/`:reported-pairs`
- Bug fix: JSON was dropping conceptual-pairs, change-pairs, findings, health
- Bug fix: namespaced keyword serialization in JSON

Session 12 commits:
```
5c7a393  step 1  fix: JSON generic walker
95488fa  step 2  feat: candidate-pair counts
7c3e8cc  step 3  feat: envelope.clj
e6d5345  step 4  feat: wire envelope into commands
5156ee7  step 6  docs: schema.md
```

**Phase B — Signal Quality** (~1-2 sessions):
- F5: Façade-aware interpretation (`:facade` role)
- F3: Family-noise suppression (ns-prefix token discount)
- F7: Explain-pair verdict (`:interpretation` key)

**Phase C — Comparison & Clustering** (~2 sessions):
- F1: Compare/diff mode (`gordian compare before.edn after.edn`)
- F4: Grouped/clustered findings in diagnose

**Phase D — Workflows & CI** (ongoing):
- Family/subgraph views, actionability sort, CI ratchet, full clusters

## Potential next work (backlog)

Phase D items:
- CI/refactor-ratchet follow-ups: richer checks, cluster-aware checks, baseline workflows
- Community detection follow-ups: label propagation / modularity, richer bridge metrics
- Compare `--ref` mode (`gordian compare --ref HEAD~4 --ref HEAD`)

## Session 19 commits — DSM initial implementation

```
44e239f  feat: add DSM SCC ordering foundation
f060e9a  feat: add collapsed DSM edge and block metrics
cb24517  feat: add DSM SCC detail matrix data
f6cb438  feat: assemble pure DSM report data
aa06636  feat: wire DSM command and output
```

284 tests, 1788 assertions, 0 failures.

New command:
- `gordian dsm`

Initial design implemented:
- collapsed SCC matrix over the condensation graph
- deterministic dependees-first SCC ordering
- counted inter-block structural edges
- SCC block metadata: `:size`, `:cyclic?`, `:internal-edge-count`, `:density`
- non-singleton SCC detail mini-matrices via local `:internal-edges` coordinates
- text / markdown / EDN / JSON output

New module:
- `dsm.clj` — pure SCC/DSM shaping and report assembly

## Session 21 commits — DSM redesign to diagonal block partitions

```
b84decb  refactor: add DSM ordering foundations for block partitioning
e0f84a3  feat: add DSM interval statistics
5a33a40  feat: add DSM block cost scoring
afde96f  feat: add DSM dynamic partitioning
d2784e3  refactor: assemble partitioned DSM report shape
5cd43ca  refactor: migrate DSM text and markdown output to block partitions
55a97a8  refactor: migrate DSM HTML to block partition semantics
```

315 tests, 1924 assertions, 0 failures.

Revised design:
- project-only namespace basis (external deps removed from matrix basis)
- deterministic dependency-respecting order via `:dfs-topo`
- contiguous diagonal block partitioning via dynamic programming
- Thebeau-style block cost with `alpha=1.5`
- partitioned block summaries and inter-block edge counts
- block detail mini-matrices replacing SCC-specific detail semantics
- text / markdown / EDN / JSON / HTML updated toward block-partition terminology
- local adjacent-swap refinement with `:ordering :refined?`
- compatibility shims removed; partitioned payload is now canonical

Potential follow-ups:
- local adjacent-swap refinement of ordering
- expose ordering / alpha options in CLI if needed
- co-usage-informed ordering seeds
- richer small-matrix rendering
- compare / gate integration for DSM block metrics
- drilldown on one block or one inter-block relation

## Session 23 commits — bug fix + path normalization

```
ea994f9  fix: strip leading ./ from src-dirs in path->ns — change coupling broken on auto-discovered Polylith paths
0b9aea0  refactor: normalize src-dir paths via fs/normalize at discover + resolve-opts boundaries
```

317 tests, 2072 assertions, 0 failures.

## Session 27 — Munera task 006 closed

Refactored `gordian.main` around scoped subcommand help and command-local parsing.

Key implementation commits:
- `16d3452` refactor: extract CLI command registry and alias resolution
- `20647bd` refactor: add scoped CLI help and command-local parsing
- `4ad7d8b` docs: document scoped CLI help and subcommands

What changed:
- new `src/gordian/cli.clj` owns:
  - command registry
  - alias resolution
  - top-level help rendering
  - subcommand help rendering
  - command-local specs
  - command-local parse/validation
- `src/gordian/main.clj` now delegates help/parsing to `gordian.cli`
- top-level `gordian --help` now shows commands + global options only
- `gordian <subcommand> --help` now shows scoped usage, positional args, command options, and examples
- scoped top-level and per-command help implemented via `gordian.cli.*`
- README updated to document the new help model

Validation:
- full suite passes: 328 tests, 2645 assertions, 0 failures

Task status change:
- moved `munera/open/006-refactor-main-to-babashka-cli-subcommands-and-scoped-help` → `munera/closed/`
- removed task 006 from `munera/plan.md` open-task list

## Session 26 — Munera task 005 closed

Verified task `005-refactor-gordian-output-namespace-and-tests` is already complete in the codebase and test suite.

Closure evidence:
- `src/gordian/output.clj` is now a thin façade
- command-focused formatter namespaces exist under `src/gordian/output/`
- split formatter tests exist under `test/gordian/output/`
- full suite passes: 328 tests, 2628 assertions, 0 failures

Key implementation commits already present:
- `b30999f` refactor: split output formatters and tests by command
- `c577625` refactor: shape output formatter internals

Task status change:
- moved `munera/open/005-refactor-gordian-output-namespace-and-tests` → `munera/closed/`
- removed task 005 from `munera/plan.md` open-task list

## Session 24 / 25 — complexity mode completed

```
67050ee  feat: add cyclomatic complexity command
bf2c064  docs: close cyclomatic task loop
410a46e  docs: refine cyclomatic task convergence plan
9e1836b  docs: add cyclomatic implementation plan
6f781f6  docs: make complexity command canonical
ad799ed  feat: extract cyclomatic analyzable units
d4e39fd  fix: align cyclomatic scoring semantics
23872c9  feat: add canonical cyclomatic report shape
eaffba1  feat: add complexity scope and sort controls
063aab4  docs: update complexity task status
3d7f79d  refactor: remove legacy complexity compatibility payload
cb16ddd  feat: improve complexity text output and filtering
```

328 tests, 2162 assertions, 0 failures.

Canonical command:
- `gordian complexity`

CLI breaking change (post-v0.2.0 alpha follow-up):
- removed the public `gordian cyclomatic` alias
- `complexity` is now the only supported local-metrics command name

Implemented now:
- pure local-metric analysis in `cyclomatic.clj`
- canonical arity-level unit extraction:
  - `defn` / `defn-`
  - `defmethod`
  - top-level `def` with literal `fn`
- built-in metrics always active:
  - `:cyclomatic-complexity`
  - `:lines-of-code`
- canonical machine-readable payload:
  - `:metrics [:cyclomatic-complexity :lines-of-code]`
  - `:units`
  - `:namespace-rollups`
  - `:project-rollup`
  - `:max-unit`
- per-unit fields include:
  - `:cc`
  - `:cc-decision-count`
  - `:cc-risk`
  - `:loc`
- rollups include:
  - `:total-cc` / `:avg-cc` / `:max-cc`
  - `:total-loc` / `:avg-loc` / `:max-loc`
- standard risk-band classification:
  - `:simple`
  - `:moderate`
  - `:high`
  - `:untestable`
- CLI controls:
  - `--sort cc|loc|ns|var|cc-risk`
  - `--top N`
  - repeatable `--min cc=N|loc=N`
  - `--source-only`
  - `--tests-only`
- output modes:
  - text
  - markdown
  - EDN
  - JSON
- text output improvements:
  - fixed-column tabular layout
  - aligned horizontal bar origins
  - dual-metric unit/rollup display
  - display-only unit filtering via `--min`

Current scoring rules:
- base complexity 1 per analyzed unit
- `if` family adds 1 (`if`, `if-not`, `if-let`, `if-some`, `when`, `when-not`, `when-let`, `when-some`, `when-first`)
- `cond` counts all clause pairs, including trailing `:else`
- `condp` counts clause pairs and default when present
- `case` counts branch arms and default when present
- `cond->` counts each condition/form pair
- `and` / `or` add `operand-count - 1`
- each `catch` in `try` adds 1
- `loop`, `recur`, recursion, `for`, `doseq`, and `while` do not independently add complexity

Display refinements (task 004):
- removed `decisions` from text and markdown complexity tables
- added `--bar cc|loc` to control human-readable histogram bars explicitly
- default bar metric remains `loc` for `--sort loc`, otherwise `cc`
- machine-readable payloads still retain `:cc-decision-count`
- payload now records `:bar-metric`, while `:options` records explicit `:bar` when provided

Scope semantics:
- default project-root behavior = discovered source paths only
- `--tests-only` = discovered test paths only
- explicit paths override discovery-based scope selection

Task status:
- Munera task `002-cyclomatic-complexity` is implemented
- Munera task `003-complexity-lines-of-code-and-metric-option-scheme` is implemented and closed

Notes:
- scans `.clj` files
- uses full-file edamame parsing via `scan/parse-file-all-forms`
- compatibility payload shims removed; canonical complexity payload is now authoritative

Bug: `path->ns` in `git.clj` stripped trailing `/` from src-dirs but not
leading `./`. Auto-discovery always returns `./components/*/src` style paths.
Git log returns `components/*/src/...` (no `./`). Prefix never matched →
0 candidate pairs for change coupling on any auto-discovered Polylith project.

Layer 1 fix (ea994f9): `(str/replace d #"^\./" "")` in `git.clj`'s `path->ns`
— defence-in-depth at the consumer.

Layer 2 fix (0b9aea0): `fs/normalize` at the sources:
- `discover.clj`'s `existing-dir` now returns `(str (fs/normalize path))`
- `main.clj`'s `resolve-opts` runs `normalize-src-dirs` before returning,
  covering explicit paths and config-sourced paths
- Fixed wrong docstring "All paths are absolute strings" in `discover.clj`

Result: src-dirs are now `components/agent-session/src` not `./components/agent-session/src`
everywhere — in output, in EDN snapshots, and in path matching.

## Session 22 — DSM quality tuning

Current DSM quality investigation focused on the recurring
"singletons + giant residual block" shape.

Findings from repo + smoke tests:
- on `gordian src/`, the previous objective produced 3 singleton blocks plus one
  29-namespace residual block
- this was not mainly an ordering failure: the fixed-order split costs still
  strongly preferred the giant residual block over plausible splits
- the primary cause was objective bias: cross-block penalties dominated, and the
  cohesion penalty only fired for zero-internal-edge blocks
- graph shape still matters: `gordian.main` pulls many leaves together, and
  large projects like `psi/refactor` still retain some very large residual
  blocks even after tuning

Quality change implemented:
- add a mild weak-cohesion penalty for multi-namespace blocks whose internal
  edge count falls below a modest target density
- preserve the stronger existing penalty for zero-internal-edge blocks

Observed effect:
- `gordian src/` improved from `3 singletons + giant blob` to 11 blocks with a
  5-namespace block and an 18-namespace residual block
- `psi/refactor` also fragmented substantially more, though it still contains
  a large 102-node block, indicating graph shape remains an important factor

Refinement follow-up:
- added block-level adjacent-swap refinement after node-level refinement
- swap allowed only when adjacent blocks are pairwise incomparable in the
  dependency DAG
- evidence from brute-force search found real graphs where node refinement
  stalls but block swapping lowers total partition cost
- on current `gordian src/` and `psi/refactor` snapshots this did not further
  change the resulting partition, but it closes a real refinement blind spot

Recursive DSM follow-up:
- large residual blocks may be globally correct at top level yet still hide
  internal structure
- implemented recursive decomposition via `:subdsm` on blocks
- recursion runs on the induced subgraph of a block
- bounded by max depth and minimum block size
- retained only when the recursive result is non-trivial (more than one child
  block, and largest child smaller than parent)

Performance investigation:
- added phase profiling for DSM
- confirmed main runtime hotspot was repeated `partition-cost` recomputation in
  top-level refinement, not the O(n²) DP itself
- recursive refinement was cheap to disable, but top-level refinement remained
  the dominant cost center

Scoring redesign:
- replaced the Thebeau-style exponent-weighted objective with a prefix-sum
  friendly block cost: `crossings + β·size² + weak-cohesion-penalty`
- tuning sweep across `β ∈ {0.02,0.03,0.05,0.07,0.1,0.15,0.2}` on `gordian src/`
  and `β ∈ {0.02,0.03,0.05,0.07,0.1}` on `psi/refactor`
- default `β` set to `0.05` as a compromise:
  - gordian: 17 blocks, largest block 11
  - psi/refactor: 147 blocks, largest block 28
- lower beta (`0.02`/`0.03`) left large residual blocks mostly intact
- higher beta (`0.07+`) fragmented too aggressively

## Session 20 commits — DSM HTML output

```
6358e74  feat: add HTML rendering foundation
fac9765  feat: add DSM HTML summary and tables
085841e  feat: add collapsed DSM HTML matrix
4334e61  feat: add DSM HTML SCC detail sections
419b727  feat: assemble full DSM HTML page
8456214  feat: add DSM HTML file output
54933ad  feat: polish DSM HTML navigation and tooltips
```

298 tests, 1862 assertions, 0 failures.

New module:
- `html.clj` — self-contained HTML rendering helpers and DSM HTML report generation

New output mode:
- `gordian dsm . --html-file dsm.html`

HTML report includes:
- summary cards
- collapsed SCC matrix with intensity classes
- block table with anchors into cyclic SCC details
- inter-block dependency table
- expandable SCC detail sections using native `<details>` / `<summary>`
- mini-matrices for internal SCC structure
- header/cell tooltips and embedded CSS

Cleanup/feature:
- Removed zombie term-scan API `scan-terms` / `scan-terms-dirs` and the test-only helper they kept alive; `scan-all*` is now the canonical full-file conceptual scan path
- Shared `gordian.test-fixtures` ns
- Keyword literal extraction
- `--conceptual-terms N`
- Package-level rollup
- Historical trending
- clj-kondo integration
- Watch mode
