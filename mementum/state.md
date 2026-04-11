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

## Open questions / next tasks

- Implement Babashka entrypoint (`gordian` / `analyze` command)
- Parse namespace require graph (via clj-kondo or direct AST)
- Compute Ca, Ce, instability per namespace
- Compute propagation cost (transitive closure)
- Detect SCCs (Tarjan / Kosaraju)
- Core/periphery classification
- Output: summary table + DOT file + JSON report
- bbin install story
