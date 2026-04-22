Approach:
- treat this as a CLI-structure refactor with behavior preservation and help-surface improvement as the primary goals
- apply code-shaper principles by separating top-level flow control from subcommand-specific parsing/validation/help knowledge
- keep command data shapes consistent across commands
- prefer a small explicit command registry plus command-local helpers over one giant flat spec or an over-generic framework

Recommended implementation sequence:
1. inventory current commands, aliases, positional-arg contracts, and option ownership in `gordian.main`
2. define a canonical command-definition shape covering: name, aliases, summary, option spec, examples, parse/validation entry, and handler entry
3. split current flat options into:
   - true global options
   - command-local options
   - shared option groups composed explicitly into relevant commands
4. implement alias resolution so `cyclomatic` resolves to `complexity` without duplicating help or behavior
5. implement top-level help rendering from the command registry
6. implement subcommand help rendering from the selected command definition
7. refactor parse flow so command selection happens first, then scoped parse/validation for that command
8. extract command-specific validation out of the monolithic `parse-args`
9. keep `gordian.main` as a thin dispatcher over command definitions and existing pipeline functions
10. update docs/help tests and command parsing tests

Preferred structural shape:
- one small registry for command discovery and summaries
- one consistent command-definition map per command
- explicit shared option groups where they materially reduce duplication
- command-local parse/validation helpers for commands with special positional semantics

Suggested command families for option/help grouping:
- analyze family: analyze, diagnose, explain, explain-pair
- snapshot family: compare, gate
- graph-view family: subgraph, communities, dsm, tests
- local-metric family: complexity, cyclomatic alias

Validation strategy:
- top-level `--help` must show only commands + global options
- `gordian <subcommand> --help` must show only relevant subcommand options and examples
- alias help for `cyclomatic` must converge on `complexity`
- representative parse failures should still produce clear errors
- output mode exclusivity behavior must remain correct where applicable

Code-shaper guidance:
- avoid combining computation and flow control in the same large function
- keep option ownership obvious
- keep command data shapes uniform
- keep helpers small and close to where they are used
- avoid abstracting command differences away if that harms readability

Risks:
- command-local specs may drift if not normalized by one command-definition shape
- some options are shared by several but not all commands; shared-group composition can become muddy
- preserving backward-compatible positional behavior may complicate the new structure

Mitigations:
- lock one command-definition shape early
- make shared option groups explicit and named
- add regression tests for current command entry patterns
- introduce the new structure incrementally behind unchanged handlers where possible