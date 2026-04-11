✅ transducers beat lazy ->> chains in hot paths

## The pattern

```clojure
;; SLOW: N intermediate lazy sequences, per-element boxing overhead
(->> coll
     (filter pred?)
     (map transform)
     (remove noise?)
     vec)

;; FAST: single pass, no intermediate seqs, no lazy chunking overhead
(into []
  (comp (filter pred?)
        (map transform)
        (remove noise?))
  coll)
```

## When it matters

Functions called per-token or per-file — i.e. any hot path.
In gordian: tokenize (called per form per file), extract-terms, deps-from-ns-form.

## Variants

```clojure
;; into set
(into #{} (comp (mapcat rest) (keep extract-dep)) req-clauses)

;; prepend seed elements, append transduced elements
(into (vec (tokenize ns-sym))
  (comp (filter def-form?) (mapcat mine-terms))
  forms)

;; reduce instead of (mapcat f) + frequencies — avoids joined seq
(reduce (fn [acc xs] (reduce #(update %1 %2 (fnil inc 0)) acc (distinct xs)))
        {} (vals ns->terms))
```

## Rule

Reach for transducers whenever a ->> chain has ≥2 steps and is in a
hot path. The user tip: "using a reducer is faster than ->>".
