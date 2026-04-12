# 016 — Project Glossary Stepwise Task List

Each task is intended to be independently testable and commit-sized.
Commit after each completed task.

---

## Task 1 — Pure glossary aggregation and ranking core

### Goal
Create a pure glossary model that can:
- aggregate term evidence from namespace terms
- aggregate conceptual-pair evidence, preferring `:independent-terms`
- aggregate community dominant-term evidence
- rank and filter glossary entries

### Files
- create `src/gordian/glossary.clj`
- create `test/gordian/glossary_test.clj`

### Tests
- namespace term aggregation counts namespace breadth correctly
- pair aggregation prefers `:independent-terms` over raw `:shared-terms`
- family-only terms are penalized relative to independent terms
- community evidence increments community counts
- glossary entries are sorted by score descending
- `:top` limits entry count
- `:min-score` filters low-scoring entries
- entry evidence includes namespaces, pair evidence, communities, related terms

### Commit
- `feat: add pure glossary ranking model`

---

## Task 2 — Thread namespace terms through report and envelope internals

### Goal
Retain namespace term extraction in the build pipeline so glossary mode can use
it without re-parsing or introducing a separate scan path.

### Files
- update `src/gordian/main.clj`
- update `src/gordian/envelope.clj`
- update `test/gordian/main_test.clj`
- update `test/gordian/envelope_test.clj`

### Tests
- `build-report` with conceptual analysis retains `:ns->terms`
- `build-report` without conceptual analysis does not attach `:ns->terms`
- envelope strips `:ns->terms` from machine-readable output as an internal key
- existing conceptual metadata behavior remains unchanged

### Commit
- `refactor: retain namespace term map for glossary derivation`

---

## Task 3 — Text and markdown glossary rendering

### Goal
Add human-facing rendering for glossary results.

### Files
- update `src/gordian/output.clj`
- update `test/gordian/output_test.clj`

### Tests
- text formatter renders heading, summary, ranked entries, kind, and evidence
- markdown formatter renders heading, summary, and glossary entry sections
- empty glossary renders a stable summary with zero entries
- evidence lines include related terms and namespace evidence

### Commit
- `feat: add glossary text and markdown output`

---

## Task 4 — CLI, command wiring, and integration

### Goal
Expose glossary as a first-class command.

### Files
- update `src/gordian/main.clj`
- update `bb.edn`
- update `test/gordian/main_test.clj`

### Tests
- `parse-args ["glossary"]` sets `:command :glossary`
- `parse-args` captures `--top` and `--min-score`
- help output mentions `glossary`, `--top`, and `--min-score`
- text command output includes glossary heading
- JSON output includes `:gordian/command :glossary` and `:entries`
- EDN output is parseable and includes glossary entries
- markdown output includes markdown glossary heading

### Commit
- `feat: wire glossary command through cli and outputs`

---

## Task 5 — Full regression and docs registration

### Goal
Register tests and ensure the feature is fully integrated.

### Files
- update `bb.edn`
- optionally update any command lists / docs references if needed

### Tests
- glossary test namespace is included in `bb test`
- full test suite passes

### Commit
- `test: register glossary feature in full suite`
