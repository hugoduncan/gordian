Approach:
- preserve the current `gordian local` command/report/output behavior
- address the review items with a narrow, evidence-first hardening pass
- prefer explicit invariants and clearer ownership over broader refactoring

Execution phases:

### Phase 1 — lock the current branch-locality behavior in tests
- add or refine tests that expose branch-local vs top-level step distinctions directly
- ensure existing dependency / working-set boundary tests still describe the intended behavior

Exit condition:
- the branch-locality invariant is test-visible before implementation changes

### Phase 2 — make branch locality explicit in step evidence
- add an explicit branch-locality field to step maps in `gordian.local.steps`
- keep `:active-predicates` for predicate-depth semantics only
- update direct step shape comments/docstrings accordingly

Exit condition:
- branch locality is represented directly at step construction time

### Phase 3 — migrate downstream consumers to the explicit invariant
- update `gordian.local.dependency` to use the explicit branch-locality field
- update `gordian.local.working-set` to use the explicit branch-locality field
- keep behavior stable unless a tiny semantics clarification is justified

Exit condition:
- dependency and working-set logic no longer infer branch locality from predicate depth

### Phase 4 — tighten semantic ownership boundaries
- review `gordian.local.common` for clearly movable semantic registries/helpers
- move only obviously misplaced items, if doing so improves ownership clarity without churn
- do a light shape pass on `shape.clj` / `evidence.clj` if the work exposes an obvious simplification

Exit condition:
- local ownership boundaries are slightly clearer and no new semantic concentration is introduced

### Phase 5 — validation and closure
- run full suite
- run representative `gordian local` sanity checks
- update task notes/state if the resulting invariant is worth recording

Exit condition:
- behavior is stable, review items are addressed, and the hardening benefit is clear

Expected shape:
- same user-facing command/report workflow
- explicit branch-locality invariant in step evidence
- downstream semantics based on direct evidence rather than proxy inference
- slightly tighter semantic ownership boundaries
