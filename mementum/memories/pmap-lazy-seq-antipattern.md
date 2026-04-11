❌ pmap returns lazy seqs → work runs on calling thread, not workers

## The bug

```clojure
;; WRONG: fn returns lazy (keep ...) seq
(->> items
     (pmap (fn [chunk] (keep expensive-fn chunk)))
     (apply concat))   ; ← ALL work happens HERE on main thread
```

pmap futures complete instantly (returning un-evaluated lazy seqs).
(apply concat) then forces the chain on the calling thread.
Result: appears to use one core because it IS one core.

## The fix

```clojure
;; CORRECT: fn returns eager vector — work runs inside the future
(->> items
     (pmap (fn [chunk] (into [] (keep expensive-fn) chunk)))
     (into [] cat))   ; cheap eager flatten, work already done
```

Rule: any fn passed to pmap must return a **fully evaluated** value.
Lazy seqs escape the future and are evaluated by whoever forces them.

## Also

- (apply concat lazy-chunks) is slow for many chunks (chain of lazy frames)
- (into [] cat chunks) is the correct eager replacement

## Related

- Same issue with (map f) inside pmap — use (mapv f) or (into [] (map f))
- pmap + (apply concat) is a classic one-core trap in Clojure
