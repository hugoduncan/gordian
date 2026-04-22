Approach:
- preserve the current `gordian local` command/report/output behavior
- address the review items in a narrow, test-guided sequence
- prefer explicit semantic clarification over speculative redesign

Execution phases:

### Phase 1 — lock and decide the ambiguous semantics
- add or refine tests around sentinel counting semantics
- add or refine tests around branch-local opaque/helper behavior
- decide for each whether the right move is:
  - preserve and document
  - or refine behavior to match a clearer rule

Exit condition:
- the intended semantics for the two most ambiguous areas are explicit and test-anchored

### Phase 2 — implement the selected semantic cleanup
- narrow or rename/document sentinel counting behavior
- preserve or refine branch-local opaque-chain handling
- keep the burden/report surface stable

Exit condition:
- the code matches the selected semantics and the tests explain the rule

### Phase 3 — tighten inner evidence reviewability
- document or normalize the `step` shape
- document or normalize the `branch-region` shape
- document or normalize the `program-point` shape
- prefer concise constructors/helpers/comments over framework-like machinery

Exit condition:
- a reviewer can understand the inner evidence records without tracing every helper body

### Phase 4 — cohesion and cleanup pass
- reduce obvious policy concentration in `common.clj`
- remove dead/private unused leftovers
- make any tiny naming or argument-order cleanups discovered during the work

Exit condition:
- the subsystem is slightly tighter and simpler, not just more documented

### Phase 5 — validation and closure
- run full suite
- run representative `gordian local` sanity checks
- update task notes/state if the clarified semantics are worth recording

Exit condition:
- behavior is stable, semantics are clearer, and the review follow-ups are closed

Expected shape:
- same user-facing command/report workflow
- clearer semantics at the ambiguous review points
- more explicit inner evidence record shapes
- slightly better local module cohesion
