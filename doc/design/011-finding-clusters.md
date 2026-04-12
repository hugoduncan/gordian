# 011 — Grouped/Clustered Findings in Diagnose

## Problem

When diagnose produces many findings, related findings about the same area
of the codebase are scattered across the list. E.g., three hidden pairs
involving `a.core` appear as separate items. Grouping them reveals the
underlying cluster: "there's a tightly coupled knot around a.core + b.svc
+ c.util".

## Design

### Core: `cluster.clj` (pure)

Union-find on finding subjects to build connected components of namespaces.

#### Input
Vector of findings from `diagnose/diagnose`.

#### Algorithm
1. Extract namespace mentions from each finding's `:subject`
   - `{:ns sym}` → `[sym]`
   - `{:ns-a sym :ns-b sym}` → `[sym-a sym-b]`
   - `{:members set}` → `(vec set)`
2. Union-find: union all namespaces mentioned in the same finding
3. Group findings by their component root
4. Filter: only emit clusters with ≥ 2 findings (singletons stay as-is)
5. Order clusters by max severity, then by number of findings desc

#### Output shape

```clojure
{:clusters [{:namespaces #{a.core b.svc c.util}
             :findings   [finding1 finding2 finding3]
             :max-severity :high
             :summary "3 findings across 3 namespaces"}]
 :unclustered [finding4]}  ;; singleton findings
```

### Integration

- `diagnose.clj` — new `cluster-findings` function
- `output.clj` — `format-diagnose` gains optional cluster view
- `main.clj` — diagnose output includes clusters
- Both text and markdown output

### Implementation steps

1. `cluster.clj` — union-find, `cluster-findings` (pure)
2. Wire into diagnose output
3. Tests
