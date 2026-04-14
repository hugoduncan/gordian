(ns gordian.diagnose
  "Generate ranked findings from a gordian report.
  All functions are pure — they take data and return data."
  (:require [clojure.set :as set]
            [gordian.finding :as finding]))

;;; ── health assessment ────────────────────────────────────────────────────

(defn health
  "Assess overall project health from report metrics.
  Returns {:propagation-cost double :health keyword
           :cycle-count int :ns-count int}."
  [{:keys [propagation-cost cycles nodes]}]
  {:propagation-cost (or propagation-cost 0.0)
   :health           (cond
                       (> (or propagation-cost 0.0) 0.30) :concerning
                       (> (or propagation-cost 0.0) 0.10) :moderate
                       :else                              :healthy)
   :cycle-count      (count cycles)
   :ns-count         (count nodes)})

;;; ── cycle findings ──────────────────────────────────────────────────────

(defn- find-cycles
  "Convert SCC cycle sets into findings. Each cycle is a :high finding."
  [cycles]
  (mapv (fn [members]
          {:severity :high
           :category :cycle
           :subject  {:members members}
           :reason   (str (count members) "-namespace cycle")
           :evidence {:members members :size (count members)}})
        cycles))

;;; ── node-level findings ─────────────────────────────────────────────────

(defn- mean [xs]
  (if (seq xs)
    (/ (reduce + 0.0 xs) (count xs))
    0.0))

(defn- find-sdp-violations
  "Namespaces with high Ca (≥2) but high instability (>0.5).
  These are depended upon by many modules but are themselves unstable —
  a violation of the Stable Dependencies Principle."
  [nodes]
  (into []
        (keep (fn [{:keys [ns ca instability] :as node}]
                (when (and (some? ca) (>= ca 2)
                           (some? instability) (> instability 0.5))
                  {:severity :medium
                   :category :sdp-violation
                   :subject  {:ns ns}
                   :reason   (str "high Ca=" ca " but I=" (format "%.2f" instability)
                                  " — depended upon yet unstable")
                   :evidence {:ca ca :ce (:ce node) :instability instability
                              :role (:role node)}})))
        nodes))

(defn- facade?
  "True if the node looks like a façade: high external fan-in,
  low external efferent coupling, delegates to family siblings.
  Requires family-scoped metrics on the node."
  [{:keys [ca-external ce-external ce-family]}]
  (and (some? ca-external)
       (>= ca-external 2)
       (<= (or ce-external 0) 1)
       (>= (or ce-family 0) 1)))

(defn- find-god-modules
  "Shared-role namespaces with reach or fan-in > 2× the mean.
  These are bottlenecks through which too much flows in both directions.
  When a candidate matches the façade pattern (high Ca-external, low
  Ce-external, delegates to family siblings), it is emitted as a :facade
  finding at :low severity instead of :god-module at :medium."
  [nodes]
  (let [reach-mean  (mean (map :reach nodes))
        fan-in-mean (mean (map :fan-in nodes))]
    (into []
          (keep (fn [{:keys [ns reach fan-in role] :as node}]
                  (when (and (= role :shared)
                             (or (> reach (* 2.0 reach-mean))
                                 (> fan-in (* 2.0 fan-in-mean))))
                    (let [fam-evidence {:ca-family   (:ca-family node)
                                        :ca-external (:ca-external node)
                                        :ce-family   (:ce-family node)
                                        :ce-external (:ce-external node)
                                        :family      (:family node)}]
                      (if (facade? node)
                        {:severity :low
                         :category :facade
                         :subject  {:ns ns}
                         :reason   (str "likely façade — Ca-ext="
                                        (:ca-external node)
                                        " Ce-ext=" (:ce-external node)
                                        " Ce-fam=" (:ce-family node)
                                        " — delegates to family, outside world depends on it")
                         :evidence (merge {:reach reach :fan-in fan-in
                                           :ca (:ca node) :ce (:ce node)
                                           :role role}
                                          fam-evidence)}
                        {:severity :medium
                         :category :god-module
                         :subject  {:ns ns}
                         :reason   (str "shared module — reach="
                                        (format "%.1f%%" (* 100.0 reach))
                                        " fan-in="
                                        (format "%.1f%%" (* 100.0 fan-in)))
                         :evidence (merge {:reach reach :fan-in fan-in
                                           :ca (:ca node) :ce (:ce node)
                                           :role role}
                                          fam-evidence)})))))
          nodes)))

;;; ── pair-level findings ─────────────────────────────────────────────────

(defn- hidden-pair-index
  "Index hidden (non-structural-edge) pairs by pair-key.
  Returns {#{ns-a ns-b} → pair-map}."
  [pairs]
  (into {} (comp (remove :structural-edge?)
                 (map (juxt finding/pair-key identity)))
        pairs))

(defn find-cross-lens-hidden
  "Pairs hidden in both conceptual and change lenses — strongest signal.
  Returns :high findings and the set of cross-lens pair-keys."
  [conceptual-pairs change-pairs]
  (let [hidden-c (hidden-pair-index conceptual-pairs)
        hidden-x (hidden-pair-index change-pairs)
        cross-keys (set/intersection (set (keys hidden-c)) (set (keys hidden-x)))]
    {:findings
     (mapv (fn [k]
             (let [cp (get hidden-c k)
                   xp (get hidden-x k)]
               {:severity :high
                :category :cross-lens-hidden
                :subject  {:ns-a (:ns-a cp) :ns-b (:ns-b cp)}
                :reason   (str "hidden in 2 lenses — conceptual="
                               (format "%.2f" (:score cp))
                               " change=" (format "%.2f" (:score xp)))
                :evidence (cond-> {:conceptual-score (:score cp)
                                   :shared-terms     (:shared-terms cp)
                                   :change-score     (:score xp)
                                   :co-changes       (:co-changes xp)
                                   :confidence-a     (:confidence-a xp)
                                   :confidence-b     (:confidence-b xp)}
                            (some? (:same-family? cp))
                            (assoc :same-family?      (:same-family? cp)
                                   :family-terms      (:family-terms cp)
                                   :independent-terms (:independent-terms cp)))}))
           cross-keys)
     :cross-keys cross-keys}))

(defn- conceptual-severity
  "Determine severity for a hidden conceptual pair.
  Same-family pairs with no independent terms (pure naming noise) → :low.
  All other pairs: score ≥ 0.20 → :medium, score < 0.20 → :low."
  [pair]
  (if (finding/family-noise? pair)
    :low
    (if (>= (:score pair) 0.20) :medium :low)))

(defn find-hidden-conceptual
  "Pairs hidden in conceptual lens only (not already cross-lens).
  Same-family pairs with only prefix-derived terms are downgraded to :low
  with a 'likely naming similarity' annotation."
  [conceptual-pairs cross-keys]
  (into []
        (keep (fn [p]
                (when (and (not (:structural-edge? p))
                           (not (contains? cross-keys (finding/pair-key p))))
                  (let [sev          (conceptual-severity p)
                        family-noise (finding/family-noise? p)]
                    {:severity sev
                     :category :hidden-conceptual
                     :subject  {:ns-a (:ns-a p) :ns-b (:ns-b p)}
                     :reason   (if family-noise
                                 (str "hidden conceptual coupling — score="
                                      (format "%.2f" (:score p))
                                      " — likely naming similarity")
                                 (str "hidden conceptual coupling — score="
                                      (format "%.2f" (:score p))))
                     :evidence (cond-> {:score        (:score p)
                                        :shared-terms (:shared-terms p)}
                                 (some? (:same-family? p))
                                 (assoc :same-family?      (:same-family? p)
                                        :family-terms      (:family-terms p)
                                        :independent-terms (:independent-terms p)))}))))
        conceptual-pairs))

(defn find-hidden-change
  "Pairs hidden in change lens only (not already cross-lens).
  Always :medium since change pairs already meet a coupling threshold."
  [change-pairs cross-keys]
  (into []
        (keep (fn [p]
                (when (and (not (:structural-edge? p))
                           (not (contains? cross-keys (finding/pair-key p))))
                  {:severity :medium
                   :category :hidden-change
                   :subject  {:ns-a (:ns-a p) :ns-b (:ns-b p)}
                   :reason   (str "hidden change coupling — score="
                                  (format "%.2f" (:score p))
                                  " (" (:co-changes p) " co-changes)")
                   :evidence {:score (:score p)
                              :co-changes (:co-changes p)
                              :confidence-a (:confidence-a p)
                              :confidence-b (:confidence-b p)}})))
        change-pairs))

(defn- find-hubs
  "Namespaces with reach > 3× mean. Informational — entry points naturally
  have high reach, but extreme values warrant monitoring."
  [nodes]
  (let [reach-mean (mean (map :reach nodes))]
    (into []
          (keep (fn [{:keys [ns reach] :as node}]
                  (when (and (pos? reach-mean)
                             (> reach (* 3.0 reach-mean)))
                    {:severity :low
                     :category :hub
                     :subject  {:ns ns}
                     :reason   (str "high-reach hub — "
                                    (format "%.1f%%" (* 100.0 reach))
                                    " of project reachable")
                     :evidence {:reach reach :ce (:ce node)
                                :instability (:instability node)
                                :role (:role node)}})))
          nodes)))

;;; ── main diagnose function ──────────────────────────────────────────────

(defn- severity-rank [s]
  (case s :high 0 :medium 1 :low 2 3))

(defn- finding-sort-key
  "Sort key: severity first (high→low), then highest score within severity."
  [f]
  [(severity-rank (:severity f))
   (- (finding/finding-magnitude f))])

(defn diagnose
  "Generate all findings from a complete report map.
  Returns findings sorted by severity then score descending."
  [{:keys [cycles nodes conceptual-pairs change-pairs] :as _report}]
  (let [{:keys [findings cross-keys]}
        (find-cross-lens-hidden (or conceptual-pairs [])
                                (or change-pairs []))]
    (->> (concat
          (find-cycles (or cycles []))
          findings
          (find-hidden-conceptual (or conceptual-pairs []) cross-keys)
          (find-hidden-change (or change-pairs []) cross-keys)
          (find-sdp-violations (or nodes []))
          (find-god-modules (or nodes []))
          (find-hubs (or nodes [])))
         (sort-by finding-sort-key)
         vec)))
