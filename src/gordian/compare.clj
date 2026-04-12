(ns gordian.compare
  "Compare two gordian report snapshots and produce a diff.
  All functions are pure — they take data and return data."
  (:require [gordian.finding :as finding]))

;;; ── health comparison ────────────────────────────────────────────────────

(defn compare-health
  "Compare top-line health between two reports.
  Accepts raw reports (from build-report) or envelope-wrapped maps.
  Returns {:before map :after map :delta map}."
  [before after]
  (let [health-of (fn [r]
                    {:propagation-cost (or (:propagation-cost r) 0.0)
                     :cycle-count      (count (or (:cycles r) []))
                     :ns-count         (count (or (:nodes r) []))})
        h-before  (health-of before)
        h-after   (health-of after)]
    {:before h-before
     :after  h-after
     :delta  {:propagation-cost (- (:propagation-cost h-after)
                                   (:propagation-cost h-before))
              :cycle-count      (- (:cycle-count h-after)
                                   (:cycle-count h-before))
              :ns-count         (- (:ns-count h-after)
                                   (:ns-count h-before))}}))

;;; ── node comparison ──────────────────────────────────────────────────────

(def ^:private node-metric-keys
  "Keys to compare between node snapshots."
  [:reach :fan-in :ca :ce :instability :role
   :ca-family :ca-external :ce-family :ce-external])

(defn- node-metrics
  "Extract comparable metrics from a node map."
  [node]
  (select-keys node node-metric-keys))

(defn- node-delta
  "Compute delta for numeric metrics between two node maps.
  Non-numeric keys (like :role) are included only when they differ."
  [before after]
  (let [bm (node-metrics before)
        am (node-metrics after)]
    (reduce-kv
     (fn [acc k av]
       (let [bv (get bm k)]
         (cond
           (and (number? av) (number? bv) (not= av bv))
           (assoc acc k (- (double av) (double bv)))

           (and (not (number? av)) (not= av bv))
           (assoc acc k {:before bv :after av})

           :else acc)))
     {}
     am)))

(defn compare-nodes
  "Compare per-namespace metrics between two reports.
  Returns {:added [node] :removed [node] :changed [diff]}."
  [before after]
  (let [before-idx (into {} (map (juxt :ns identity)) (or (:nodes before) []))
        after-idx  (into {} (map (juxt :ns identity)) (or (:nodes after) []))
        before-nss (set (keys before-idx))
        after-nss  (set (keys after-idx))
        added-nss  (sort-by str (remove before-nss after-nss))
        removed-nss (sort-by str (remove after-nss before-nss))
        common-nss (filter after-nss before-nss)]
    {:added   (mapv (fn [ns-sym] {:ns ns-sym :metrics (node-metrics (get after-idx ns-sym))})
                    added-nss)
     :removed (mapv (fn [ns-sym] {:ns ns-sym :metrics (node-metrics (get before-idx ns-sym))})
                    removed-nss)
     :changed (into []
                    (keep (fn [ns-sym]
                            (let [bn (get before-idx ns-sym)
                                  an (get after-idx ns-sym)
                                  d  (node-delta bn an)]
                              (when (seq d)
                                {:ns     ns-sym
                                 :before (node-metrics bn)
                                 :after  (node-metrics an)
                                 :delta  d}))))
                    common-nss)}))

;;; ── cycle comparison ─────────────────────────────────────────────────────

(defn compare-cycles
  "Compare cycles between two reports.
  Returns {:added [cycle-set] :removed [cycle-set]}."
  [before after]
  (let [bc (set (or (:cycles before) []))
        ac (set (or (:cycles after) []))]
    {:added   (vec (sort-by (comp str first) (remove bc ac)))
     :removed (vec (sort-by (comp str first) (remove ac bc)))}))

;;; ── pair comparison ──────────────────────────────────────────────────────

(defn compare-pairs
  "Compare coupling pairs between two reports for a given lens.
  kind — :conceptual or :change (used as :kind tag in output).
  Pairs matched by #{:ns-a :ns-b}. Score changes < 0.01 are ignored.
  Returns {:added [pair] :removed [pair] :changed [diff]}."
  [before after kind]
  (let [pairs-key (case kind
                    :conceptual :conceptual-pairs
                    :change     :change-pairs)
        bp        (or (get before pairs-key) [])
        ap        (or (get after pairs-key) [])
        b-idx     (into {} (map (juxt finding/pair-key identity)) bp)
        a-idx     (into {} (map (juxt finding/pair-key identity)) ap)
        b-keys    (set (keys b-idx))
        a-keys    (set (keys a-idx))
        added-ks  (remove b-keys a-keys)
        removed-ks (remove a-keys b-keys)
        common-ks  (filter a-keys b-keys)]
    {:added   (vec (sort-by (comp str :ns-a) (map a-idx added-ks)))
     :removed (vec (sort-by (comp str :ns-a) (map b-idx removed-ks)))
     :changed (into []
                    (keep (fn [k]
                            (let [bp (get b-idx k)
                                  ap (get a-idx k)
                                  delta (- (:score ap) (:score bp))]
                              (when (> (abs delta) 0.01)
                                {:ns-a   (:ns-a ap)
                                 :ns-b   (:ns-b ap)
                                 :before {:score (:score bp)}
                                 :after  {:score (:score ap)}
                                 :delta  {:score delta}}))))
                    common-ks)}))

;;; ── finding comparison ───────────────────────────────────────────────────

(defn compare-findings
  "Compare findings between two diagnose reports.
  Returns {:added [finding] :removed [finding]}.
  Findings matched by [:category :subject]."
  [before after]
  (let [bf (set (map finding/finding-key (or (:findings before) [])))
        af (set (map finding/finding-key (or (:findings after) [])))
        added-ks   (remove bf af)
        removed-ks (remove af bf)
        ;; rebuild finding lookup for output
        after-by-key  (into {} (map (juxt finding/finding-key identity))
                            (or (:findings after) []))
        before-by-key (into {} (map (juxt finding/finding-key identity))
                            (or (:findings before) []))]
    {:added   (vec (sort-by (comp str :subject) (keep after-by-key added-ks)))
     :removed (vec (sort-by (comp str :subject) (keep before-by-key removed-ks)))}))

;;; ── composite compare ───────────────────────────────────────────────────

(defn- report-metadata
  "Extract envelope metadata from a report."
  [report]
  (select-keys report [:gordian/version :gordian/schema :src-dirs :lenses]))

(defn compare-reports
  "Full comparison of two gordian report snapshots.
  Inputs are typically the envelope-wrapped EDN output of analyze or diagnose.
  Returns a structured diff map."
  [before after]
  {:gordian/command  :compare
   :before           (report-metadata before)
   :after            (report-metadata after)
   :health           (compare-health before after)
   :nodes            (compare-nodes before after)
   :cycles           (compare-cycles before after)
   :conceptual-pairs (compare-pairs before after :conceptual)
   :change-pairs     (compare-pairs before after :change)
   :findings         (compare-findings before after)})
