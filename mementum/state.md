# Gordian — Working State

**Last updated:** 2026-04-10

## What this project is

`gordian` is a Babashka script that analyzes the `require` graph of Clojure
projects. It reports structural coupling metrics that surface architectural
complexity: afferent/efferent coupling (Ca/Ce), instability, propagation cost,
cycles (SCCs), and core/periphery classification.

Usage: `bb gordian analyze src/`

## Current status

Alpha. Fresh scaffold — `README.md` and `doc/references.md` exist; no source
code yet. Single root commit (`e6aaf83`).

## Key concepts (per references.md)

- **Propagation cost** (MacCormack 2006) — fraction of system reachable through
  transitive require graph. Core metric.
- **Ca/Ce/Instability** — Robert Martin (1994) coupling vocabulary.
- **SCCs** — strongly connected components as cycle signal.
- **Core/periphery** — MacCormack 2012 network classification.
- Data source: clj-kondo analysis output is a natural fit.

## Implemented (commits 740cfb3 → 4204b8e)

- `bb.edn` entrypoint + CLI (`bb analyze <src-dir>`)
- `gordian.scan/scan`: .clj → `{ns→#{direct-deps}}`
- `gordian.close/close`: transitive closure (BFS per node, handles cycles)
- `gordian.aggregate/aggregate`: PC = Σ|reach(n)|/N², per-node reach + fan-in
- `gordian.output/format-report` + `print-report`: ruled table output
- 66 assertions across 14 tests, all green
- `bb analyze test/fixture` produces correct table

## Next tasks

- Ca / Ce / instability per namespace (Robert Martin metrics)
- Detect SCCs (Tarjan) — cycle reporting
- Core/periphery classification (MacCormack 2012)
- DOT file output (graphviz)
- JSON report output
- bbin install story
- Self-analysis: run gordian on itself
