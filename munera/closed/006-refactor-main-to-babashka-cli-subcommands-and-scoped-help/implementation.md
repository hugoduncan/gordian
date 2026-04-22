2026-04-21

Task created to refactor `gordian.main` around Babashka CLI subcommands with scoped help.

Observed current state:
- `gordian.main` owns a large mixed-responsibility CLI surface
- there is one large flat `cli-spec`
- there is one large flat `usage-summary`
- `parse-args` performs command detection, option parsing, subcommand validation, and some normalization in one place
- top-level help currently exposes many subcommand-specific options
- command inventory currently includes: analyze, diagnose, compare, gate, subgraph, communities, dsm, tests, complexity, cyclomatic, explain, explain-pair

Requested behavior:
- top-level `--help` should describe commands and global options only
- `gordian <subcommand> --help` should describe that subcommand’s positional args, options, and examples
- the refactor should be shaped for simplicity, consistency, and robustness

Implementation direction:
- keep `gordian.main` as the entrypoint and dispatch shell
- extract command-specific parse/help/validation knowledge behind a command registry or equivalent small command-definition layer
- separate global options from subcommand-local options
- route `cyclomatic` to canonical `complexity` help/behavior as a compatibility alias
- preserve existing command execution semantics unless adjustment is required for the new help model

Preferred shape:
- a small command registry with per-command summary, aliases, parse entry, help entry, and handler entry
- per-command option specs and validation helpers rather than one giant cross-command `cli-spec`
- top-level help assembled from the command registry
- subcommand help assembled from the selected command definition

Potential extraction seams:
- global option helpers
- command registry / alias resolution
- help rendering helpers
- command-specific parse/validation helpers

Key code-shaper constraints to follow:
- top-level flow control should stay separate from command-specific parsing details
- command definitions should use one consistent data shape
- shared logic should be minimal and explicit
- command-local options/help should live near the command that owns them
- avoid a giant generic abstraction that makes local comprehension worse

Risks:
- accidental CLI regressions while moving validation logic
- help/spec drift if specs and help are defined separately without a clear ownership rule
- over-centralization into a larger framework than the problem warrants

Mitigations:
- migrate one command family at a time into the new structure
- keep alias handling explicit
- add tests for help routing and representative parse failures
- prefer hybrid registry + explicit per-command helpers over an over-generic system

Completion target:
- scoped help at top level and per subcommand
- thinner `gordian.main`
- localized command-specific parsing/validation/help logic
- preserved behavior for existing commands