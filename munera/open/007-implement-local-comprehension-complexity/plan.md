Approach:
- treat `doc/design/021-local-comprehension-complexity.md` as the normative semantic source
- implement LCC as a dedicated `gordian local` command over a pure analysis pipeline
- deliberately reuse the successful `gordian complexity` CLI and output idioms where they fit the LCC workflow
- sequence work from lowest-level invariants outward:
  1. local-unit extraction
  2. evidence extraction
  3. burden scoring
  4. findings/report assembly
  5. CLI/output integration
- prefer conservative high-confidence heuristics over speculative breadth in v1

Expected shape:
- pure namespaces for units, evidence, burden scoring, findings, and report assembly
- command wiring integrated with existing Gordian CLI structure
- canonical machine-readable report plus useful text output for hotspot triage
- tests that lock unit model, burden semantics, and finding/report shape

Risks:
- accidental double-counting across burden families
- noisy abstraction/dependency heuristics
- working-set estimation becoming too implementation-heavy or expensive
- command surface growing before semantics stabilize

Mitigations:
- respect the metric’s primary evidence ownership rules
- keep the first slice narrow and deterministic
- test burden families independently before integrating totals/findings
- keep output/reporting layered above the pure analysis core
