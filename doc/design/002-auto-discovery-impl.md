# Implementation Plan: Auto-Discovery + Config

Design: `doc/design/002-auto-discovery.md`

## Scope summary

| Area | Files | Nature |
|------|-------|--------|
| Layout detection | new `discover.clj` + test | pure, 3 fns |
| Config loading | new `config.clj` + test | thin IO, 2 fns |
| Graph filtering | new `filter.clj` + test | pure, 1 fn |
| CLI wiring | modify `main.clj` + test | integrate all |
| Test fixtures | 2 new fixture dirs | dirs + marker files + .clj files |
| Docs | README, help text, PLAN.md | |

## Detailed function signatures

### `discover.clj`

```clojure
(ns gordian.discover
  (:require [babashka.fs :as fs]))

(def project-markers
  "File names whose presence identifies a Clojure project root."
  #{"deps.edn" "bb.edn" "project.clj" "build.boot"
    "shadow-cljs.edn" "workspace.edn"})

(defn project-root?
  "True if dir is a directory containing a project marker file."
  [dir] → boolean)

(defn discover-dirs
  "Probe standard Clojure project layouts under root.
  Returns {:src-dirs [string] :test-dirs [string]}.
  Only directories that actually exist on disk are returned.
  Paths are relative to root (e.g. \"src\", \"components/auth/src\")."
  [root] → {:src-dirs [str] :test-dirs [str]})

(defn resolve-dirs
  "Given discover result + options, return flat vec of dirs to scan.
  include-tests? controls whether :test-dirs are appended."
  [{:keys [src-dirs test-dirs]} {:keys [include-tests]}]
  → [str])
```

**Discovery probes** (all relative to `root`):

| Pattern | Category | Notes |
|---------|----------|-------|
| `src/` | src | standard |
| `test/` | test | standard |
| `development/src/` | src | Polylith dev |
| `components/*/src/` | src | list subdirs of `components/` |
| `components/*/test/` | test | list subdirs of `components/` |
| `bases/*/src/` | src | list subdirs of `bases/` |
| `bases/*/test/` | test | list subdirs of `bases/` |
| `extensions/*/src/` | src | list subdirs of `extensions/` |
| `extensions/*/test/` | test | list subdirs of `extensions/` |
| `projects/*/src/` | src | list subdirs of `projects/` |
| `projects/*/test/` | test | list subdirs of `projects/` |

Subdirectory listing: `(fs/list-dir (fs/path root "components"))` → filter
`fs/directory?` → append `/src` or `/test` → filter `fs/directory?`.

All returned paths are strings (via `str`) for compatibility with existing
scan functions which pass paths to `fs/glob`.

### `config.clj`

```clojure
(ns gordian.config
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]))

(defn load-config
  "Read .gordian.edn from dir if it exists. Returns map or nil.
  Returns nil on missing file or malformed EDN (logs nothing)."
  [dir] → map | nil)

(defn merge-opts
  "Merge config into CLI opts. CLI values take precedence.
  Only non-nil CLI values override config."
  [config cli-opts] → map)
```

**Config keys recognised** (all optional):

| Key | Type | CLI equivalent |
|-----|------|----------------|
| `:src-dirs` | [string] | positional args |
| `:include-tests` | boolean | `--include-tests` |
| `:exclude` | [string] | `--exclude` |
| `:conceptual` | double | `--conceptual` |
| `:change` | boolean/string | `--change` |
| `:change-since` | string | `--change-since` |
| `:dot` | string | `--dot` |

### `filter.clj`

```clojure
(ns gordian.filter)

(defn filter-graph
  "Remove namespaces matching any pattern from the dependency graph.
  Removes matched ns from both keys and dependency sets.
  patterns — seq of regex strings. Empty/nil patterns → graph unchanged."
  [graph patterns] → {sym → #{sym}})
```

### `main.clj` changes

**New CLI spec entries:**
```clojure
:include-tests {:desc "Include test directories in analysis" :coerce :boolean}
:exclude       {:desc "Exclude namespaces matching regex (repeatable)" :coerce [:string]}
```

**New internal function `resolve-opts`:**
```clojure
(defn- resolve-opts
  "Resolve src-dirs from CLI args, config, or discovery.
  Returns final opts map with :src-dirs guaranteed to be a non-empty vector,
  or an {:error ...} map."
  [parsed-args] → opts | {:error msg})
```

Logic:
1. If parsed args have `:src-dirs` (user gave positional args):
   a. Check if first src-dir is a project root → load config, merge opts
   b. If project root and only one positional arg → discover dirs from it
   c. If not project root → use as-is (backward compatible)
2. If no `:src-dirs`:
   a. Default to `"."`
   b. If `"."` is a project root → load config + discover
   c. Otherwise → error

**`build-report` change:** Accept optional `:exclude` key in opts. After
scan, before close, call `filter/filter-graph`.

**`parse-args` change:** Remove the `nil? src-dirs → error` check. That
validation moves to `resolve-opts` (after discovery attempt).

## Test fixtures

### `resources/fixture-project/` — simple deps.edn project

```
resources/fixture-project/
├── deps.edn                  ;; {}
├── src/
│   └── myapp/
│       └── core.clj          ;; (ns myapp.core)
└── test/
    └── myapp/
        └── core_test.clj     ;; (ns myapp.core-test (:require [myapp.core]))
```

### `resources/fixture-polylith/` — Polylith workspace

```
resources/fixture-polylith/
├── workspace.edn             ;; {}
├── deps.edn                  ;; {}
├── components/
│   ├── auth/
│   │   └── src/
│   │       └── auth/
│   │           └── core.clj  ;; (ns auth.core)
│   └── users/
│       ├── src/
│       │   └── users/
│       │       └── core.clj  ;; (ns users.core (:require [auth.core]))
│       └── test/
│           └── users/
│               └── core_test.clj ;; (ns users.core-test (:require [users.core]))
└── bases/
    └── api/
        └── src/
            └── api/
                └── handler.clj ;; (ns api.handler (:require [users.core]))
```

---

## Step-wise plan

### Step 1: Test fixtures

Create the two fixture directory trees. No code yet — just the files.
Commit separately so they're available for all subsequent steps.

**Files created:**
- `resources/fixture-project/deps.edn`
- `resources/fixture-project/src/myapp/core.clj`
- `resources/fixture-project/test/myapp/core_test.clj`
- `resources/fixture-polylith/workspace.edn`
- `resources/fixture-polylith/deps.edn`
- `resources/fixture-polylith/components/auth/src/auth/core.clj`
- `resources/fixture-polylith/components/users/src/users/core.clj`
- `resources/fixture-polylith/components/users/test/users/core_test.clj`
- `resources/fixture-polylith/bases/api/src/api/handler.clj`

**Commit message:** `test: add fixture-project and fixture-polylith layouts`

**Verification:** `bb test` still passes (fixtures are inert files).

---

### Step 2: `discover.clj` + `discover_test.clj`

Implement `project-root?`, `discover-dirs`, `resolve-dirs`. All pure
(file-existence checks, directory listing).

**Tests for `project-root?`:**

```
(deftest project-root?-test
  ;; fixture-project has deps.edn → true
  ;; fixture-polylith has workspace.edn + deps.edn → true
  ;; resources/fixture (no marker) → false
  ;; non-existent path → false
  ;; file path (not dir) → false
```

**Tests for `discover-dirs`:**

```
(deftest discover-dirs-simple-project-test
  ;; fixture-project → {:src-dirs ["…/src"] :test-dirs ["…/test"]}
  ;; src-dirs has exactly 1 entry
  ;; test-dirs has exactly 1 entry
  ;; all returned paths are strings
  ;; all returned paths exist as directories

(deftest discover-dirs-polylith-test
  ;; fixture-polylith → src-dirs contains components/auth/src, components/users/src, bases/api/src
  ;; test-dirs contains components/users/test
  ;; no duplicate entries
  ;; all returned paths exist

(deftest discover-dirs-nonexistent-test
  ;; dir with no standard layout → {:src-dirs [] :test-dirs []}
```

**Tests for `resolve-dirs`:**

```
(deftest resolve-dirs-test
  ;; src-only by default
  ;; with :include-tests → appends test-dirs
  ;; empty discover result → empty vec
  ;; empty test-dirs + include-tests → just src-dirs
```

**Register test in bb.edn task.**

**Commit message:** `feat: discover.clj — project layout detection`

---

### Step 3: `config.clj` + `config_test.clj`

Implement `load-config` and `merge-opts`. Uses temp dirs for isolated tests.

**Tests for `load-config`:**

```
(deftest load-config-test
  ;; dir with valid .gordian.edn → parsed map
  ;; dir without .gordian.edn → nil
  ;; dir with malformed .gordian.edn → nil (no throw)
  ;; empty .gordian.edn → nil (empty string isn't valid EDN map)
  ;; .gordian.edn with {} → {}
```

**Tests for `merge-opts`:**

```
(deftest merge-opts-test
  ;; config {:conceptual 0.20} + cli {} → {:conceptual 0.20}
  ;; config {:conceptual 0.20} + cli {:conceptual 0.40} → {:conceptual 0.40}
  ;; config {:change true :exclude ["user"]} + cli {:change "."} → {:change "." :exclude ["user"]}
  ;; nil config + cli opts → cli opts
  ;; config + nil cli → config
```

**Register test in bb.edn task.**

**Commit message:** `feat: config.clj — .gordian.edn loading and merge`

---

### Step 4: `filter.clj` + `filter_test.clj`

Implement `filter-graph`. Pure graph transformation.

**Tests:**

```
(deftest filter-graph-test
  (let [graph {'a.core   #{'b.core 'c.util}
               'b.core   #{'c.util}
               'c.util   #{}
               'user     #{'a.core}}]

    ;; exclude "user" → user removed from keys, user removed from dep sets
    ;; exclude "util" → c.util removed from keys AND from dep sets of a.core, b.core
    ;; exclude "a\\.core" → matches exactly a.core (regex dot)
    ;; exclude ["user" "util"] → both removed
    ;; empty patterns → graph unchanged
    ;; nil patterns → graph unchanged
    ;; pattern matching nothing → graph unchanged
    ;; pattern matching everything → empty graph
    ;; excluded ns that appears only in dep sets (not as key) → removed from dep sets
```

**Register test in bb.edn task.**

**Commit message:** `feat: filter.clj — namespace exclusion by regex`

---

### Step 5: Wire into `main.clj` + `main_test.clj`

This is the integration step. Changes to `parse-args`, new `resolve-opts`,
new CLI flags, `build-report` accepts `:exclude`.

**Changes to `parse-args`:**
- Add `:include-tests` and `:exclude` to `cli-spec`
- Remove `(nil? src-dirs) → error` — no-args is now valid
- Update `usage-summary` and help text

**New `resolve-opts` function:**
- Called between `parse-args` and `analyze`
- Detects project roots, loads config, runs discovery
- Returns enriched opts with resolved `:src-dirs`

**Changes to `build-report`:**
- Accept opts map (or add `:exclude` parameter)
- After scan, apply `filter/filter-graph` if `:exclude` present
- Filter `ns->terms` map too (for conceptual coupling)

**Changes to `analyze`:**
- Call `resolve-opts` before `build-report`

**Tests for parse-args:**

```
(deftest parse-args-no-args-test
  ;; no args → not an error anymore (will be resolved later)
  ;; still has no :src-dirs key (resolve-opts handles it)

(deftest parse-args-include-tests-test
  ;; --include-tests → {:include-tests true}
  ;; without --include-tests → key absent

(deftest parse-args-exclude-test
  ;; --exclude "user" → {:exclude ["user"]}
  ;; --exclude "user" --exclude "scratch" → {:exclude ["user" "scratch"]}
  ;; without --exclude → key absent
```

**Tests for resolve-opts:**

```
(deftest resolve-opts-project-root-test
  ;; {:src-dirs ["resources/fixture-project"]} → discovers src/
  ;; :src-dirs resolved to actual src path(s)

(deftest resolve-opts-polylith-test
  ;; {:src-dirs ["resources/fixture-polylith"]} → discovers component + base src dirs

(deftest resolve-opts-explicit-dirs-test
  ;; {:src-dirs ["resources/fixture"]} → used as-is (no marker file)

(deftest resolve-opts-no-args-test
  ;; {} with cwd being gordian project root → discovers src/
  ;; (may need to set a test root or use fixture)

(deftest resolve-opts-include-tests-test
  ;; {:src-dirs ["resources/fixture-project"] :include-tests true} → includes test/

(deftest resolve-opts-config-merge-test
  ;; fixture with .gordian.edn → config values applied
  ;; CLI flags override config
```

**Tests for exclude in build-report:**

```
(deftest build-report-exclude-test
  ;; build-report with fixture-project, exclude "core-test" → only myapp.core in nodes
  ;; build-report with no exclude → all nodes present
  ;; excluded ns not in any pair results
```

**Tests for help text:**

```
(deftest print-help-updated-test
  ;; help mentions --include-tests
  ;; help mentions --exclude
```

**Integration test:**

```
(deftest auto-discover-integration-test
  ;; analyze with {:src-dirs ["resources/fixture-project"]} → produces report
  ;; report has correct namespaces from discovered src/
```

**Update bb.edn** to register new test namespaces if not already covered.

**Commit message:** `feat: gordian . — auto-discovery, config, --exclude, --include-tests`

---

### Step 6: Docs + PLAN.md

- Update `usage-summary` in main.clj (already done in step 5)
- Update README.md with new usage examples
- Update PLAN.md status
- Update `mementum/state.md`

**Commit message:** `docs: README and PLAN for auto-discovery`

---

## Risk notes

1. **Step 5 coupling:** Like schema normalization, the wiring step touches
   parse-args + resolve-opts + build-report + analyze. These are tightly
   coupled through the pipeline. If tests break mid-step, they all need to
   land together. Steps 1–4 are safely independent.

2. **`resolve-opts` and cwd:** Some tests may depend on the test runner's
   cwd. Use fixture paths explicitly rather than relying on `"."`.

3. **Path format:** Discovery returns paths relative to root. If root is
   `"resources/fixture-project"`, src-dir is
   `"resources/fixture-project/src"`. Scan's `fs/glob` handles both relative
   and absolute paths.

4. **`.gordian.edn` in test fixtures:** Need a fixture with a config file
   for merge testing. Create a temp dir in the config test, or add a
   `.gordian.edn` to one of the fixtures.
