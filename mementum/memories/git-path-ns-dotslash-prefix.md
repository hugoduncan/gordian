❌ change coupling silently produces 0 results on auto-discovered Polylith projects

## What happened
`gordian diagnose --change` showed 0 change-coupling findings on psi/refactor
despite 1577 commits in the last 90 days.

## Root cause
`path->ns` in `git.clj` built a prefix for stripping by calling:
  `(str/replace d #"/$" "")`  — removes trailing slash only

Auto-discovery returns src-dirs like `./components/agent-session/src`.
Git log returns paths like `components/agent-session/src/psi/agent_session/foo.clj`.

Built prefix: `./components/agent-session/src/`
Git path:      `components/agent-session/src/...`
`str/starts-with?` → false → path never stripped → ns never matched project graph → 0 pairs.

## Fix
Two-layer fix:

**ea994f9** — defence-in-depth in `git.clj`'s `path->ns`:
```clojure
(-> d (str/replace #"^\./" "") (str/replace #"/$" "") (str "/"))
```

**0b9aea0** — systematic fix at the source:
- `discover.clj`'s `existing-dir`: `(str (fs/normalize path))` instead of `(str path)`
- `main.clj`'s `resolve-opts`: `normalize-src-dirs` helper runs `fs/normalize` on
  all `:src-dirs` before returning, covering explicit paths and config-sourced paths too
- Wrong docstring "All paths are absolute strings" corrected

## Result
1294 candidate pairs, 54 reported change pairs, 24 hidden-change findings unlocked.
src-dirs in EDN output now show `components/agent-session/src` not `./components/agent-session/src`.

## Signal
Symptom is silent: `:candidate-pairs 0` in `:lenses :change` of EDN output.
If change coupling is enabled but produces nothing, check this first.
