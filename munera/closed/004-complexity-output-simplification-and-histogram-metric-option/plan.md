Approach:
- treat this as a narrow convergence pass on the existing `complexity` command
- preserve the two-metric analysis core and machine-readable schema
- change only the human-facing report surface plus one explicit display option

Implementation shape:
- first lock the output/CLI contract in the task docs
- then add `--bar` parsing and pure option metadata shaping
- then simplify text/markdown rendering and thread the chosen bar metric through the output helpers
- finally update tests and README/help

Recommended implementation sequence:
1. add the new complexity CLI option `--bar` with allowed values `cc|loc`
2. validate unknown `--bar` values clearly in `main.clj`
3. thread `:bar` into complexity option metadata in pure report finalization
4. add a small pure helper for effective bar metric selection:
   - explicit `:bar` wins
   - otherwise `:loc` when `:sort :loc`
   - otherwise `:cc`
5. update text complexity output to remove the `decisions` column and use the effective bar metric
6. update markdown complexity output to remove the `decisions` column
7. keep `:cc-decision-count` in machine-readable outputs unchanged
8. update README/help/tests to reflect the refined display model

Risks:
- accidental behavioral drift in default bar selection
- confusion if docs do not clearly explain the distinction between sort metric and bar metric
- overreaching into schema changes that are not necessary for this task

Mitigations:
- preserve current default bar semantics when `--bar` is omitted
- explicitly test both default and overridden bar behavior
- limit schema change to `:options :bar`
