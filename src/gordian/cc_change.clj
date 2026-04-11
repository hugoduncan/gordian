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

;;; ── coupling pairs ───────────────────────────────────────────────────────

(defn- structural-edge?
  "True when graph contains a directed edge in either direction between a and b."
  [graph a b]
  (or (contains? (get graph a #{}) b)
      (contains? (get graph b #{}) a)))

(defn change-coupling-pairs
  "Compute namespace pairs whose change coupling meets threshold.
  Returns a vector of maps sorted by :coupling descending:
    {:ns-a :ns-b :coupling :confidence-a :confidence-b
     :co-changes :structural-edge?}

  coupling     = co / (changes-a + changes-b - co)   Jaccard, symmetric
  confidence-a = co / changes-a   conditional: 'when a changes, b changes X%'
  confidence-b = co / changes-b

  threshold — minimum Jaccard coupling to include (default 0.30)
  min-co    — minimum raw co-change count; guards against noise in shallow
              histories where a single shared commit would score 1.0 (default 2)

  Only pairs where both namespaces are keys in graph are returned.
  External deps that appeared in commits but were not scanned are excluded."
  ([commits graph] (change-coupling-pairs commits graph 0.30 2))
  ([commits graph threshold min-co]
   (let [project   (set (keys graph))
         n-changes (ns-change-counts commits)
         co-counts (co-change-counts commits)]
     (->> co-counts
          (keep (fn [[[a b] co]]
                  (when (and (project a) (project b) (>= co min-co))
                    (let [ca       (get n-changes a 0)
                          cb       (get n-changes b 0)
                          coupling (/ (double co) (+ ca cb (- co)))]
                      (when (>= coupling threshold)
                        {:ns-a             a
                         :ns-b             b
                         :co-changes       co
                         :coupling         coupling
                         :confidence-a     (/ (double co) ca)
                         :confidence-b     (/ (double co) cb)
                         :structural-edge? (structural-edge? graph a b)})))))
          (sort-by :coupling >)
          vec))))
