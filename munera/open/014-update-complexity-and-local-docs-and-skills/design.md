Update Gordian’s user-facing documentation and skills so the shipped `complexity` and `local` commands are described consistently, concretely, and in the right places.

## Intent

- make the shipped `gordian complexity` and `gordian local` commands discoverable and usable from the main user-facing docs and skill prompts
- close the gap between implemented functionality and the guidance users/agents actually read first
- keep the task documentation-shaped and convergence-focused rather than expanding either command’s semantics

## Problem statement

`gordian complexity` and `gordian local` are implemented and documented in parts of the repo, but the documentation surface is uneven.

Current state from a quick repo read:
- `README.md` already has substantive sections for both commands
- `doc/practical-guide.md` is oriented around architecture workflows and does not yet clearly integrate `complexity` and `local` into the practical workflow
- `skills/` contains Gordian skills focused on coupling / architecture workflows, but does not clearly surface when to use `complexity` or `local`, nor how they complement the architectural commands

This creates a documentation mismatch:
- users may not discover the local-analysis commands from the practical guide
- agents using the skills may under-recommend `complexity` and `local`
- overlapping docs can drift in terminology, examples, and scope semantics

The task is therefore to converge the docs and skills around the shipped command set, especially the distinction between:
- architectural coupling analysis (`analyze`, `diagnose`, `explain`, etc.)
- local executable-unit metrics (`complexity`)
- local comprehension burden analysis (`local`)

## Scope

### In scope

- update `README.md` so `complexity` and `local` are accurate, current, and aligned with the shipped CLI
- update `doc/practical-guide.md` to include practical guidance on when and how to use `complexity` and `local`
- update the relevant files under `skills/` so Gordian skills recommend `complexity` and `local` in the right situations
- align terminology, examples, and command-selection guidance across those surfaces
- clarify how `complexity` and `local` complement, rather than replace, Gordian’s architectural commands

### Out of scope

- changing command semantics or CLI flags
- implementing new features in `complexity` or `local`
- broad docs cleanup unrelated to these two commands
- revising unrelated skills unless needed for a small consistency fix
- compare/gate integration for local metrics unless already shipped and directly relevant to accuracy

### Explicitly deferred

- deeper tutorialization of the full local-metrics methodology
- new dedicated design docs for `complexity` or `local`
- schema/reference expansion beyond what is needed to keep README / practical guide / skills accurate

## Acceptance criteria

- `README.md` clearly documents both `gordian complexity` and `gordian local` with current command names, examples, and positioning
- `doc/practical-guide.md` includes practical workflow guidance for both commands, including when to reach for each and how they relate to architectural analysis
- relevant skill files under `skills/` explicitly mention `complexity` and `local` as appropriate Gordian tools
- the docs consistently distinguish:
  - coupling / architecture analysis
  - cyclomatic + LOC local metrics (`complexity`)
  - local comprehension burden analysis (`local`)
- examples and option names match the shipped CLI surface
- no doc still implies the removed `cyclomatic` command name is canonical
- the update stays concise and avoids duplicating large blocks of text unnecessarily

## Minimum concepts

- **architectural analysis** — namespace-level coupling and structure commands such as `analyze`, `diagnose`, `explain`, `subgraph`, and `communities`
- **complexity** — executable-unit local metrics focused on cyclomatic complexity and LOC
- **local** — executable-unit local comprehension burden analysis (LCC-style burden vector + findings)
- **workflow positioning** — guidance for which Gordian command to use for which class of question
- **skill trigger** — wording in skill docs that causes an agent to reach for the right command at the right time

## Design direction

Prefer convergence over expansion.

The goal is not to write maximal new documentation. The goal is to make the existing documentation surfaces agree on a simple command-selection model:
- use architectural commands for namespace coupling and structure
- use `complexity` for local executable-unit complexity and size hotspots
- use `local` for comprehension burden and reviewability hotspots

The practical guide should integrate these commands into realistic workflows, not just add isolated reference sections.

The skills should be updated with compact command-selection guidance rather than large duplicated manuals.

## Possible implementation approaches

### Approach A — minimal additive convergence (preferred)

- tighten existing README sections where needed
- add targeted practical-guide sections or workflow callouts for `complexity` and `local`
- update the two Gordian skill variants to mention when to use these commands

Pros:
- narrow and low-risk
- reduces drift without creating new documentation burden
- matches the size of the task

Cons:
- does not create a full standalone tutorial for local metrics

### Approach B — large documentation expansion

- add deep new sections, extended examples, and broad workflow rewrites

Pros:
- more comprehensive

Cons:
- higher churn
- more likely to drift or over-document
- larger than the user request requires

## Architectural guidance

Follow existing Gordian documentation style:
- concise but precise
- command-first examples
- clear distinctions between related commands
- canonical CLI names only
- avoid repeating full reference material when a short positioning explanation is enough

For `skills/`, prefer trigger guidance such as:
- use `complexity` when the question is about local branching / LOC hotspots
- use `local` when the question is about local comprehension burden, helper chasing, abstraction mix, or working-set overload

## Risks

- README and practical guide could diverge again if one gets a richer update than the other
- skill docs could become too verbose and stop functioning as compact agent triggers
- docs might blur `complexity` and `local` into near-synonyms

## Mitigations

- keep one clear conceptual distinction across all three surfaces
- prefer short, high-signal examples over exhaustive prose
- explicitly describe `complexity` and `local` as complementary but different

## Done means

The task is done when a reader or agent can reliably discover and choose between Gordian’s architectural commands, `complexity`, and `local` from the README, the practical guide, and the relevant skill docs, without encountering stale command names or muddled positioning.
