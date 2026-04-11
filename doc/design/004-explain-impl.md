# Implementation Plan: `gordian explain` / `gordian explain-pair`

Design: `doc/design/004-explain.md`

## Step 1: `explain.clj` — building blocks

New `src/gordian/explain.clj`, new `test/gordian/explain_test.clj`.
Register in `bb.edn`.

Functions:
- `shortest-path [graph from to]` → `[sym ...]` or nil
- `direct-deps [graph project-nss ns-sym]` → `{:project [sym] :external [sym]}`
- `direct-dependents [graph ns-sym]` → `[sym]`
- `ns-pairs [pairs ns-sym]` → `[pair]`

Test graph:
```clojure
{'a.core #{'b.svc 'c.util 'ext.lib}
 'b.svc  #{'c.util}
 'c.util #{}
 'd.leaf #{'a.core}}
```

Tests:
- `shortest-path` A→C via [a.core b.svc c.util] (2-hop, but also direct a→c)
  Actually a.core directly depends on c.util, so [a.core c.util]
- `shortest-path` d.leaf→c.util: [d.leaf a.core c.util]
- `shortest-path` c.util→a.core: nil (no reverse path)
- `shortest-path` a.core→a.core: nil (self)
- `shortest-path` a→nonexistent: nil
- `shortest-path` handles cycle without hang
- `direct-deps` a.core: project=[b.svc c.util], external=[ext.lib]
- `direct-deps` c.util: project=[], external=[]
- `direct-dependents` c.util: [a.core b.svc]
- `direct-dependents` d.leaf: [] (nobody depends on it)
- `ns-pairs` filters correctly
- `ns-pairs` empty input → empty

## Step 2: `explain.clj` — `explain-ns`, `explain-pair-data`

Composite functions that assemble explanations from a report map.

Test fixture: a small report map with nodes, graph, conceptual-pairs,
change-pairs, cycles.

Tests for `explain-ns`:
- Returns map with keys: :ns :metrics :direct-deps :direct-dependents
  :conceptual-pairs :change-pairs :cycles
- :metrics has :reach :fan-in :ca :ce :instability :role
- :conceptual-pairs filtered to those involving the ns
- :change-pairs filtered to those involving the ns
- :cycles lists cycle sets containing the ns
- Unknown ns → map with :error key

Tests for `explain-pair-data`:
- Returns map with :ns-a :ns-b :structural :conceptual :change :finding
- :structural has :direct-edge? :direction :shortest-path
- Direct edge a→b → :direct-edge? true, :direction :a→b
- No edge → :direct-edge? false, :direction nil
- :shortest-path populated when transitive path exists
- :conceptual is the pair map when present, nil otherwise
- :change is the pair map when present, nil otherwise
- :finding present when pair is hidden coupling
- Unknown ns → :error key

## Step 3: `output.clj` — formatting

New functions: `format-explain-ns`, `format-explain-pair`,
`print-explain-ns`, `print-explain-pair`.

Tests for `format-explain-ns`:
- Header shows ns name and role
- Shows metrics (Ca, Ce, I, reach, fan-in)
- Shows project and external deps
- Shows dependents
- Shows conceptual pairs with scores and terms
- Shows "none" for empty sections
- Error data → error message

Tests for `format-explain-pair`:
- Header shows both ns names
- Shows structural edge status
- Shows shortest path when found
- Shows "(none)" when no path
- Shows conceptual score + shared terms
- Shows change score + co-changes
- Shows finding diagnosis when present
- Shows "(no data)" for absent lenses

## Step 4: `main.clj` — subcommand routing

Changes to parse-args, new command functions, dispatch in run.

Tests:
- `parse-args ["explain" "gordian.scan"]` → {:command :explain :explain-ns 'gordian.scan :src-dirs ["."]}
- `parse-args ["explain"]` → {:error "..."}
- `parse-args ["explain-pair" "a" "b"]` → {:command :explain-pair :explain-ns-a 'a :explain-ns-b 'b}
- `parse-args ["explain-pair" "a"]` → {:error "..."}
- `parse-args ["explain" "a" "--edn"]` → has :edn true
- Integration: explain gordian.main → output with dependents
- Integration: explain-pair gordian.aggregate gordian.close → output
- --edn → structured map with expected keys
- Help mentions explain/explain-pair

## Step 5: Docs + PLAN
