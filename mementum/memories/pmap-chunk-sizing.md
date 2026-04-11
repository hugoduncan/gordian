🎯 pmap chunk sizing: target ~200 chunks, not per-item

## The problem

Per-item pmap overhead (~0.1ms dispatch) dominates when work items are
microsecond-scale. Per-item pmap on 57k pairs: 211ms — slower than
sequential (157ms).

## Empirical data (57k candidate pairs, 12 cores)

| chunk size | chunks | time  |
|------------|--------|-------|
| per-item   | 57195  | 211ms |
| 50         | 1144   |  90ms |
| 200        |  286   |  16ms | ← sweet spot
| 500        |  115   |  35ms |
| 1000       |   58   |  59ms |
| 2000        |   29   |  17ms |
| 5000       |   12   |  27ms |

## Formula

```clojure
(def chunk-size (max 50 (quot (count items) 200)))
```

Targets ~200 chunks. Floor of 50 prevents over-chunking on small inputs.
Each chunk should take at least ~1ms to amortise dispatch overhead.

## Rule

pmap is only faster than sequential when work-per-item >> dispatch-overhead.
For microsecond work: chunk first, then pmap the chunks.
For millisecond work (e.g. file IO + parse): per-item pmap is fine.
