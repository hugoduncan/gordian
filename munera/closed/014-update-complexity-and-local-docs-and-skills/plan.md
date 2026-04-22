Approach:
- converge the main user-facing documentation surfaces on the shipped `complexity` and `local` command set
- keep changes narrow, accuracy-focused, and workflow-oriented
- update skill triggers so agents recommend the local-analysis commands in the right situations

Execution phases:

### Phase 1 — inventory and convergence targets
- identify where `README.md`, `doc/practical-guide.md`, and the relevant `skills/` files already mention or omit `complexity` / `local`
- identify stale terminology, missing workflow positioning, and any CLI drift
- decide the minimum set of skill files that should mention these commands explicitly

Exit condition:
- the exact doc surfaces and gaps are identified and bounded

### Phase 2 — update README
- tighten the README sections for `complexity` and `local` where needed
- ensure examples, option names, and command positioning match the shipped CLI
- make the distinction between `complexity`, `local`, and architecture commands explicit but concise

Exit condition:
- README is current, accurate, and aligned with the command model

### Phase 3 — update practical guide
- add practical workflow guidance for when to use `complexity` and `local`
- connect them to existing architecture workflows instead of treating them as isolated features
- keep the guide practical: question → command → interpretation

Exit condition:
- the practical guide helps users choose and apply both commands in real workflows

### Phase 4 — update skills
- update the relevant Gordian skill files to include `complexity` and `local` triggers and command guidance
- keep the skill updates compact and action-oriented
- avoid duplicating large README/practical-guide sections inside skills

Exit condition:
- skills recommend `complexity` and `local` when the user’s question calls for them

### Phase 5 — consistency pass and validation
- verify terminology and examples are consistent across README, practical guide, and skills
- verify no stale `cyclomatic` canonical naming remains
- do a final read for concision and non-duplication

Exit condition:
- the three surfaces are aligned, concise, and free of obvious drift

Expected shape:
- README: current, accurate command reference and positioning
- practical guide: integrated workflow guidance for `complexity` and `local`
- skills: compact triggers for recommending the local-analysis commands
