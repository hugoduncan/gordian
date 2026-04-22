Approach:
- preserve the current `gordian local` command/report/output behavior
- clarify the canonical-vs-display boundary in the local report shape
- prefer explicit report-basis representation over broader refactoring

Execution phases:

### Phase 1 — lock current canonical/display expectations in tests
- add or refine tests around `gordian.local.report/finalize-report`
- make the intended distinction visible: canonical analyzed units vs displayed unit rows
- keep current text/markdown behavior described by tests where appropriate

Exit condition:
- the report-basis issue is test-visible before implementation changes

### Phase 2 — separate canonical report data from display-shaped data
- keep canonical analysis results intact in the final local report
- add an explicit display slice or equivalent explicit basis representation
- keep rollups/max-unit clearly tied to their intended basis

Exit condition:
- final report shape no longer silently conflates canonical and display subsets

### Phase 3 — migrate formatters/consumers to the explicit display seam
- update local output formatting to consume the explicit display slice if needed
- preserve current user-visible behavior unless a tiny wording clarification is justified
- ensure EDN/JSON remain coherent and reviewable

Exit condition:
- all consumer paths use the clarified report shape consistently

### Phase 4 — cleanup and validation
- remove `metric-name-order` if still unused, or give it a justified role
- do a tiny adjacent shape pass only if directly exposed by the work
- run full suite and representative `gordian local` sanity checks

Exit condition:
- report boundary is clearer, residue is removed, and behavior remains stable

Expected shape:
- same `gordian local` UX
- canonical analysis payload preserved
- explicit display-oriented unit slice or explicit basis metadata
- cleaner local report contract for both humans and machine consumers