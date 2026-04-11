# Design: `gordian .` auto-discovery + `.gordian.edn` config

## Goal

Remove the #1 onboarding friction: having to manually discover and list
source directories. `gordian .` should just work on any standard Clojure
project layout.

## Current behaviour

```
gordian src/                          # explicit src-dir required
gordian analyze src/ test/            # multiple dirs explicit
gordian components/*/src              # user has to know about globs
```

`parse-args` requires at least one positional arg (src-dir). No config
file support. No layout detection.

## Target behaviour

```
gordian .                             # auto-discover src dirs in .
gordian                               # same as gordian . (no args = cwd)
gordian /path/to/project              # auto-discover in given dir
gordian . --include-tests             # also discover test dirs
gordian src/ test/                    # explicit dirs still work (no discovery)
gordian . --exclude 'user|scratch'    # exclude matching ns patterns
```

Config file `.gordian.edn` in project root:
```
gordian .                             # reads .gordian.edn if present
gordian . --conceptual 0.40           # CLI overrides config values
```

## Design decisions

### 1. When does auto-discovery trigger?

**Rule:** Auto-discovery triggers when a positional arg is a directory that
does NOT look like a source root (i.e., does not directly contain `.clj`
files or standard `clj` directory structure like `com/`, `my_ns/`, etc.).

Simpler rule: auto-discovery triggers when positional args are directories
that contain project markers (deps.edn, bb.edn, project.clj, shadow-cljs.edn,
workspace.edn). If none of the positional args are project roots, treat them
as explicit src-dirs (backward compatible).

**Chosen approach:** Detect project markers. This is unambiguous — a directory
with `deps.edn` is a project root, `src/gordian/` is a source dir.

When no positional args are given, default to `"."`.

### 2. What layouts to detect?

Probe in order, accumulate all that exist:

| Pattern                    | Kind     | When              |
|----------------------------|----------|-------------------|
| `src/`                     | src      | always            |
| `test/`                    | test     | `--include-tests` |
| `components/*/src/`        | src      | always            |
| `components/*/test/`       | test     | `--include-tests` |
| `bases/*/src/`             | src      | always            |
| `bases/*/test/`            | test     | `--include-tests` |
| `extensions/*/src/`        | src      | always            |
| `extensions/*/test/`       | test     | `--include-tests` |
| `projects/*/src/`          | src      | always            |
| `projects/*/test/`         | test     | `--include-tests` |
| `development/src/`         | src      | always            |

If no patterns match, error: "no source directories found in <dir>".

### 3. Project markers

A directory is a project root if it contains any of:
- `deps.edn`
- `bb.edn`
- `project.clj`
- `build.boot`
- `shadow-cljs.edn`
- `workspace.edn` (Polylith)

### 4. `--include-tests` flag

Default: src-only. With `--include-tests`, test dirs are also scanned.
This is the only new CLI flag for discovery itself.

### 5. `--exclude` flag

`--exclude <regex>` — drop namespaces whose fully-qualified name matches.
Applied after scanning, before any analysis. Multiple `--exclude` flags
allowed (any match excludes).

Implementation: filter applied in `build-report` after scan, before close.
This keeps scan pure and the filter composable.

### 6. `.gordian.edn` config file

Located in the project root (same dir as the project marker).

```edn
{:src-dirs          ["src" "components/*/src"]  ;; override auto-discovery
 :include-tests     true
 :conceptual        0.20
 :change            true                        ;; enable change coupling
 :change-since      "90 days ago"
 :exclude           ["user" ".*-scratch"]       ;; regex strings
 :dot               "gordian.dot"               ;; default dot output
}
```

All keys optional. Semantics:
- `:src-dirs` — if present, replaces auto-discovery entirely (explicit list)
- All other keys — defaults that CLI flags override

**Merge order:** defaults ← config file ← CLI flags.

CLI wins for any key present in both config and CLI. For `:src-dirs`:
- CLI positional args present → use them (no config, no discovery)
- No CLI positional args + config `:src-dirs` → use config dirs
- No CLI positional args + no config `:src-dirs` → auto-discover

### 7. Backward compatibility

All existing invocations continue to work unchanged:
- `gordian src/` — explicit dir, no discovery, no config
- `gordian analyze src/ test/` — explicit dirs
- `gordian src/ --conceptual 0.30` — explicit dir + options

New behaviours only when:
- No positional args given, or
- Positional arg is a project root (has marker file)

## Architecture

### New: `discover.clj` (pure)

```clojure
(ns gordian.discover)

(defn project-root?
  "True if dir contains a Clojure project marker file."
  [dir] ...)

(defn discover-src-dirs
  "Given a project root dir, return {:src-dirs [...] :test-dirs [...]}.
  Probes standard layouts. Expands globs. Only returns dirs that exist."
  [root-dir] ...)

(defn resolve-dirs
  "Given discover result and options, return a flat vector of dirs to scan.
  Includes test dirs only when include-tests? is true."
  [discovered {:keys [include-tests]}] ...)
```

All functions are pure (take dir paths as strings, return data).
Glob expansion uses `clojure.java.io` / `java.io.File` — available in bb.

### New: `config.clj` (IO, thin)

```clojure
(ns gordian.config)

(defn load-config
  "Read .gordian.edn from dir if it exists. Returns map or nil."
  [dir] ...)

(defn merge-config
  "Merge defaults ← config ← cli-opts. CLI wins."
  [config cli-opts] ...)
```

### Changes to `main.clj`

`parse-args` changes:
- No positional args → default to `["."]` instead of error
- Detect project roots in positional args → trigger discovery
- Load `.gordian.edn` from project root
- Merge config + CLI opts
- New CLI spec entries: `--include-tests`, `--exclude`

`analyze` and `build-report` changes:
- `build-report` accepts optional `:exclude` patterns
- After scan, before close: filter graph keys by exclude patterns

## Files changed

### New source (2 files)

| File                     | Purpose                                |
|--------------------------|----------------------------------------|
| `src/gordian/discover.clj` | Layout detection, glob expansion      |
| `src/gordian/config.clj`   | Config file loading, merge            |

### Modified source (1 file)

| File                     | Change                                  |
|--------------------------|-----------------------------------------|
| `src/gordian/main.clj`  | Discovery flow, config loading, --exclude, --include-tests |

### New tests (2 files)

| File                            | Tests                                |
|---------------------------------|--------------------------------------|
| `test/gordian/discover_test.clj` | Layout detection against fixtures   |
| `test/gordian/config_test.clj`   | Config loading, merging             |

### Modified tests (1 file)

| File                           | Change                                |
|--------------------------------|---------------------------------------|
| `test/gordian/main_test.clj`  | New parse-args cases, --exclude tests |

### New test fixtures

| Path                               | Purpose                           |
|------------------------------------|-----------------------------------|
| `resources/fixture-project/`       | Minimal deps.edn project layout   |
| `resources/fixture-polylith/`      | Minimal Polylith workspace layout |

## Step-wise implementation plan

### Step 1: `discover.clj` + `discover_test.clj` — layout detection

Pure functions: `project-root?`, `discover-src-dirs`, `resolve-dirs`.
Test fixtures: minimal project layouts with marker files + src/test dirs.
No integration with main yet.

Tests:
- `project-root?` detects deps.edn, bb.edn, project.clj, workspace.edn
- `project-root?` returns false for non-project dirs
- `discover-src-dirs` finds `src/` in simple project
- `discover-src-dirs` finds `components/*/src/` in Polylith layout
- `discover-src-dirs` returns both src and test dirs
- `resolve-dirs` includes test dirs only when `:include-tests` true
- `resolve-dirs` returns empty when nothing found
- Glob expansion works for `*/src` patterns

### Step 2: `config.clj` + `config_test.clj` — config file loading

`load-config` reads `.gordian.edn` from a directory.
`merge-config` merges config with CLI opts (CLI wins).

Tests:
- `load-config` reads valid `.gordian.edn`
- `load-config` returns nil when file missing
- `load-config` returns nil when file is malformed (or throws?)
- `merge-config` CLI values override config values
- `merge-config` config fills in missing CLI values
- `merge-config` `:src-dirs` in config used when no CLI positional args
- `merge-config` with nil config returns CLI opts unchanged

### Step 3: `main.clj` + `main_test.clj` — wire discovery + config

`parse-args`:
- No positional args → `["."]`
- New `--include-tests` and `--exclude` in cli-spec
- Help text updated

New internal fn `resolve-opts`:
- Detect project roots in src-dirs
- Load config from project root
- Run discovery if project root
- Merge config + CLI
- Return final opts with resolved `:src-dirs`

Tests:
- `gordian .` on real gordian project discovers `src/`
- `--include-tests` adds test dirs
- `--exclude` pattern filters namespaces from report
- Config file values apply when no CLI override
- CLI flags override config values
- Explicit src-dirs bypass discovery entirely
- Help text mentions new flags

### Step 4: `--exclude` filtering in `build-report`

Add namespace exclusion after scan, before close.
This is the only change to the analysis pipeline.

Tests:
- `build-report` with `:exclude ["scan"]` drops `gordian.scan`
- Excluded ns removed from graph and from all pair results
- Exclude with no matches → same as no exclude

### Step 5: Integration test + docs

- End-to-end: `gordian .` from project root → produces report
- Update usage-summary, README
- Update PLAN.md status
