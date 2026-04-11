(ns gordian.cc-change)

;;; ── change counting ──────────────────────────────────────────────────────

(defn ns-change-counts
  "Return {ns-sym → int} — how many commits touched each namespace.
  Input: [{:nss #{ns-sym}}] as produced by gordian.git/commits-as-ns."
  [commits]
  (reduce (fn [acc {:keys [nss]}]
            (reduce #(update %1 %2 (fnil inc 0)) acc nss))
          {}
          commits))

(defn co-change-counts
  "Return {[ns-a ns-b] → int} — commits containing both namespaces.
  Pairs stored in canonical order: (compare (str a) (str b)) < 0
  so each pair is counted exactly once regardless of set iteration order."
  [commits]
  (reduce (fn [acc {:keys [nss]}]
            (reduce (fn [m pair] (update m pair (fnil inc 0)))
                    acc
                    (for [a nss b nss
                          :when (neg? (compare (str a) (str b)))]
                      [a b])))
          {}
          commits))
