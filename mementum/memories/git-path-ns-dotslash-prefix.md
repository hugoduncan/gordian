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

## Fix (ea994f9)
Add `(str/replace d #"^\./" "")` before stripping trailing slash:
```clojure
(let [prefix (-> d
                 (str/replace #"^\./" "")
                 (str/replace #"/$" "")
                 (str "/"))]
```

## Result
1294 candidate pairs, 54 reported change pairs, 24 hidden-change findings unlocked.

## Signal
Symptom is silent: `:candidate-pairs 0` in `:lenses :change` of EDN output.
If change coupling is enabled but produces nothing, check this first.
