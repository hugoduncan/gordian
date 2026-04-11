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
