# Gordian — Working State

**Last updated:** 2026-04-10

## What this project is

`gordian` is a Babashka script that analyzes the `require` graph of Clojure
projects. It reports structural coupling metrics that surface architectural
complexity.

Usage: `bb gordian analyze src/`  or  `gordian analyze src/` (after bbin install)

## Current status

**Feature-complete v0 alpha.** All core metrics implemented, tested, and
self-verified. 193 assertions, 41 tests, 0 failures.

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

## Potential next work

- Tokenizer: add backtick/punctuation to split pattern (cleaner docstring tokens)
- Keyword literal extraction (`:propagation-cost` → domain vocab, currently skipped)
- `--conceptual-terms N` to control how many shared terms are shown
- Self-analysis as a CI check (fail if PC > threshold)
- Package-level rollup (group namespaces by prefix)
- Historical trending (compare across commits)
- clj-kondo integration (richer dep data: macro requires, etc.)
- `--threshold` flag to gate CI on propagation cost
- Watch mode
