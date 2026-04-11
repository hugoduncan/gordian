# Gordian — Working State

**Last updated:** 2026-04-11

## What this project is

`gordian` is a Babashka script that analyzes the `require` graph of Clojure
projects. It reports structural coupling metrics that surface architectural
complexity.

Usage: `gordian` (auto-discovers from cwd) or `gordian src/` (explicit dirs)

## Current status

**v0.2.0 alpha.** Schema envelope, façade detection, family-noise suppression,
family-scoped metrics, auto-discovery, config, diagnose, explain, markdown.
161 tests, 1219 assertions, 0 failures.

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
main.clj       pipeline + CLI (babashka.cli)                IO
```

## CLI

```
gordian [analyze] <src-dir>... [options]

  --dot  <file>   Write DOT graph to <file> (status to stderr)
  --json          JSON to stdout, suppress table
  --edn           EDN to stdout, suppress table
  --help          Usage summary
```

Output modes are mutually exclusive (--json / --edn).
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
explain.clj     drill-down queries (ns + pair)               pure
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

## Roadmap

See PLAN.md. All 5 primary items complete. Deferred items remain (v2):
cluster detection, diff/baseline, change window comparison, test mode,
presets.

## Session 12 — user feedback analysis

Real-world feedback from AI assistant using gordian on ~158-file Polylith
project. 12 items analyzed and sequenced into phases. See
`doc/design/006-user-feedback-analysis.md` for full analysis.

**Phase A — Schema & Metadata** ✅ done.

**Phase B — Signal Quality** (in progress):
- F5 ✅: Façade-aware interpretation — family.clj + family-scoped Ca/Ce
- F3 ✅: Family-noise suppression — annotate pairs, downgrade naming noise
  - `text.clj` extracted (tokenize + stem, shared utility)
  - `annotate-conceptual-pair(s)` classifies shared-terms as family vs independent
  - Same-family + no independent terms → `:low` + "likely naming similarity"
  - Output shows family-terms vs independent-terms breakdown

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

- Remove zombie API `scan-terms` / `scan-terms-dirs`
- Shared `gordian.test-fixtures` ns
- Keyword literal extraction
- `--conceptual-terms N`
- Package-level rollup
- Historical trending
- clj-kondo integration
- Watch mode
