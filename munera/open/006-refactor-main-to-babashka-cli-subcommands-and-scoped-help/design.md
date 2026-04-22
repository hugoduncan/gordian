Refactor `gordian.main` to use a Babashka CLI subcommand-oriented design with scoped `--help`, while shaping the refactor for simplicity, consistency, and robustness.

## Intent

- make Gordian’s CLI easier to understand by giving each subcommand its own focused help
- reduce the structural and cognitive load concentrated in `gordian.main`
- align CLI parsing, validation, dispatch, and help generation around a consistent subcommand model
- improve maintainability by separating top-level command routing from subcommand-specific option knowledge

## Problem statement

`gordian.main` currently carries a large mixed responsibility surface:
- top-level command discovery
- option parsing
- subcommand-specific validation
- help text
- command dispatch
- pipeline wiring

The current help surface is also too flat. Top-level `--help` includes many subcommand-specific options, which makes the default entrypoint noisy and harder to scan. Users should be able to learn:
- at the top level: what commands exist, what they do, and what global options apply
- at the subcommand level: the arguments, options, and examples relevant only to that subcommand

This is both a UX problem and a structure problem. A good refactor should therefore improve the user-visible help model and also reshape the code so subcommand-specific concerns live near each other.

## Scope

### In scope

- refactor `gordian.main` to use a Babashka CLI subcommand-oriented structure
- add focused `--help` for each supported subcommand
- narrow top-level `--help` to commands, global options, and global usage only
- move subcommand-specific option descriptions into subcommand help
- define a consistent structure for subcommand specs, help text, parsing, validation, and dispatch
- preserve existing command behavior unless a change is required to support the new help/parse model
- keep the public command set intact, including the `cyclomatic` compatibility alias
- add/update tests covering top-level and subcommand help behavior

### Out of scope

- redesigning Gordian report schemas
- changing analysis semantics of commands
- changing output formatting beyond help text and CLI error/help presentation where required
- removing existing commands
- introducing shell completion generation unless Babashka CLI support falls out trivially
- a broad rewrite of unrelated pipeline code

## Non-goals

- do not turn this into a feature task unrelated to CLI structure
- do not mix in output refactors or analysis refactors unless needed for clean seams
- do not retain a giant centralized option/help table if subcommand-local structure is clearer
- do not duplicate shared global options across subcommands without an explicit composition rule

## Acceptance criteria

- `gordian --help` shows:
  - top-level usage
  - available commands and short descriptions
  - global options only
- `gordian <subcommand> --help` shows:
  - subcommand usage
  - subcommand purpose/description
  - positional arguments for that subcommand
  - subcommand-specific options
  - any global options that remain applicable to that subcommand, if included by explicit composition
  - relevant examples
- subcommand-specific options no longer clutter top-level `--help`
- parsing/validation remains correct for all current commands
- `gordian cyclomatic --help` behaves as the compatibility alias for `complexity`
- the refactor reduces responsibility concentration in `gordian.main`
- tests cover help routing, option scoping, and representative command parsing/dispatch behavior

## Minimum concepts

- **global options** — options meaningful across multiple commands and suitable for top-level help
- **subcommand spec** — the parse/validation/help definition for one command
- **subcommand handler** — the code that runs one command after parsing and validation
- **command registry** — the map/table that defines available commands and their descriptions, specs, and handlers
- **compatibility alias** — a public command name that resolves to a canonical subcommand without duplicating behavior
- **scoped help** — help text derived from the selected command context rather than one global flat usage block

## Code-shaper constraints

This refactor should follow code-shaper principles explicitly:

- keep top-level flow control separate from command-specific computation
- keep data shapes for command definitions consistent across commands
- prefer a small number of obvious responsibilities:
  - command registry
  - parsing/help selection
  - validation
  - dispatch
  - existing pipeline functions
- prefer local comprehensibility over clever abstraction
- factor shared logic only when it is genuinely shared
- keep command-specific options/help near the command that owns them

## Design direction

### Preferred shape: explicit command registry + per-command specs

Use a data-driven command registry that describes each command in one place, for example with fields like:
- canonical command key
- aliases
- summary/description
- positional-arg contract
- option spec
- help examples
- parse/validation function
- handler function

This allows:
- top-level help to list commands from one authoritative registry
- subcommand help to be generated from the selected command definition
- aliases like `cyclomatic` to point to the `complexity` command without duplicate logic

### Keep global and subcommand option scopes separate

Define a small explicit global option layer and compose it with subcommand-local specs only where applicable.

Examples of likely global concerns:
- output mode exclusivity helpers if truly shared
- maybe `--help`

Examples of likely subcommand-local concerns:
- `--conceptual`, `--change`, `--change-since` for analyze/diagnose/explain family commands
- `--baseline`, gate thresholds, `--fail-on` for gate
- `--lens`, `--threshold` for communities
- complexity-only `--sort`, `--min`, `--top`, `--source-only`, `--tests-only`

The key design goal is that ownership is obvious.

### Thin top-level dispatcher

`gordian.main` should remain the entrypoint, but after refactor it should primarily:
- identify the target command or default command
- route `--help` to the right help formatter
- parse using the selected command definition
- call the command handler

It should not keep growing as a giant case statement containing every command’s parsing details.

### Preserve existing pure-core / thin-IO architecture

This task changes CLI structure, not the architecture of analysis modules.
Parsing, command selection, and help assembly can be reshaped into smaller pure helpers.
Runtime effects should remain concentrated in the entrypoint and existing command execution paths.

## Implementation approaches

### Approach A — fully data-driven registry

Pros:
- strongest consistency
- help and parsing can derive from the same source of truth
- easiest to add commands cleanly later

Cons:
- requires careful design to avoid over-abstraction

### Approach B — per-command functions plus a light registry

A lighter alternative is:
- one registry only for command discovery/summary/aliases
- each command owns its own spec/help/validation helpers in functions

Pros:
- more explicit, less abstract
- often easier to read in Clojure

Cons:
- some duplication risk

### Preferred direction

Prefer a hybrid leaning toward B:
- one small registry for command discovery, descriptions, canonical names, and handler/help/parser entry points
- per-command helpers/functions for option specs, validation, and examples

This is more locally comprehensible and fits code-shaper goals better than an overly generic all-data framework.

## Architectural guidance

Follow:
- thin IO shell in `main.clj`
- pure helpers for parse normalization, validation, and help assembly
- consistent option and result data shapes
- explicit alias handling

Likely beneficial extractions:
- a small `gordian.cli` or `gordian.main.command` helper namespace for command registry/help composition
- or multiple `gordian.main.*` helper namespaces if needed

Avoid:
- introducing cycles
- merging unrelated command semantics just because they share one flag name
- gigantic multiline hard-coded usage strings that drift from actual specs

## Examples of desired UX

Top level:
```text
gordian --help
Usage: gordian <command> [args] [options]

Commands:
  analyze       Raw metrics + coupling
  diagnose      Ranked findings
  compare       Compare snapshots
  ...

Global options:
  --help        Show help
  --json        Machine-readable output
  --edn         Machine-readable output
  --markdown    Markdown output
```

Subcommand:
```text
gordian complexity --help
Usage: gordian complexity [<path>...] [options]

Analyze local code metrics (cyclomatic complexity + LOC).

Options:
  --sort cc|loc|ns|var|cc-risk
  --min cc=N|loc=N
  --top N
  --source-only
  --tests-only
  --help
```

## Done means

The task is complete when Gordian’s CLI has clear scoped help, subcommand concerns are structurally localized, `gordian.main` no longer acts as a monolithic parse/help hub, and the refactor leaves the CLI behavior simpler to understand and safer to extend.