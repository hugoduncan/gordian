# 010 — Compare/Diff Mode

## Problem

After refactoring, there's no way to see what changed structurally.
Users manually eyeball two EDN dumps. We need:

```bash
gordian compare before.edn after.edn
```

## Design

### Core: `compare.clj` (pure)

Takes two envelope-wrapped report maps (from `--edn` output) and produces
a diff report. All pure functions.

#### Input contract

Both inputs must be schema ≥ 1 (`gordian/schema` key present).
Both must be `:gordian/command :analyze` or `:diagnose`.
Compare operates on the shared structural data — nodes, pairs, cycles.

#### Diff structure

```clojure
{:gordian/command :compare
 :before {:gordian/version "0.2.0" :src-dirs [...]}  ; metadata only
 :after  {:gordian/version "0.2.0" :src-dirs [...]}

 ;; top-line health delta
 :health {:before {:propagation-cost 0.21 :health :moderate :cycle-count 2 :ns-count 45}
          :after  {:propagation-cost 0.15 :health :healthy  :cycle-count 0 :ns-count 47}
          :delta  {:propagation-cost -0.06 :cycle-count -2 :ns-count +2}}

 ;; namespace changes
 :nodes {:added    [{:ns foo.bar :metrics {...}}]
         :removed  [{:ns old.thing :metrics {...}}]
         :changed  [{:ns x.core
                     :before {:reach 0.30 :ca 5 :instability 0.40 :role :shared}
                     :after  {:reach 0.25 :ca 4 :instability 0.33 :role :core}
                     :delta  {:reach -0.05 :ca -1 :instability -0.07}}]}

 ;; cycle changes
 :cycles {:added   [#{a b c}]
          :removed [#{x y}]}

 ;; coupling pair changes (conceptual + change)
 :conceptual-pairs {:added   [{pair}]
                    :removed [{pair}]
                    :changed [{:ns-a ... :ns-b ...
                               :before {:score 0.45}
                               :after  {:score 0.32}
                               :delta  {:score -0.13}}]}

 :change-pairs {:added   [{pair}]
                :removed [{pair}]
                :changed [{...}]}

 ;; findings changes (when both are diagnose reports)
 :findings {:added   [{finding}]
            :removed [{finding}]}}
```

### Functions

```clojure
(compare-health before after)     → {:before ... :after ... :delta ...}
(compare-nodes before after)      → {:added ... :removed ... :changed ...}
(compare-cycles before after)     → {:added ... :removed ...}
(compare-pairs before after kind) → {:added ... :removed ... :changed ...}
(compare-findings before after)   → {:added ... :removed ...}
(compare-reports before after)    → full diff map
```

#### Node matching
By `:ns` symbol. Same ns in both → compare metrics.
Only report changed nodes where at least one metric differs.

#### Pair matching
By `#{:ns-a :ns-b}` set. Same pair in both → compare `:score`.
Only report changed pairs where score delta exceeds 0.01.

#### Cycle matching
Set equality. Same cycle set in both → unchanged.

#### Finding matching
By `[:category :subject]`. Same category+subject → unchanged.

### CLI: `gordian compare <before.edn> <after.edn>`

New subcommand. Reads two EDN files from disk. Output modes: text (default),
`--markdown`, `--json`, `--edn`.

Output sections:
1. **Health summary** — PC, cycles, ns-count with arrows (↑↓→)
2. **Improved** — removed cycles, removed findings, decreased scores
3. **Regressed** — added cycles, added findings, increased scores
4. **Added/Removed** — new namespaces, disappeared namespaces

### Implementation steps

1. `compare.clj` — health, node, cycle comparison (pure)
2. `compare.clj` — pair comparison, finding comparison (pure)
3. `output.clj`  — `format-compare`, `format-compare-md`
4. `main.clj`    — wire `compare` subcommand + CLI
5. Tests throughout

### Schema note

No schema bump needed — compare output is a new command, not a change
to existing commands. The envelope gets `{:gordian/command :compare}`.

### Deferred: `--ref` mode

`gordian compare --ref HEAD~4 --ref HEAD` requires checking out code at
two git refs, running gordian on each, and comparing. Significantly more
complex (temp dirs, subprocess invocation). Do the file-based compare first;
`--ref` can be layered on top later.
