(ns gordian.diagnose
  "Generate ranked findings from a gordian report.
  All functions are pure — they take data and return data.")

;;; ── utilities ────────────────────────────────────────────────────────────

(defn pair-key
  "Canonical pair identity — unordered set of two namespace symbols."
  [pair]
  #{(:ns-a pair) (:ns-b pair)})

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
                       :else                               :healthy)
   :cycle-count      (count cycles)
   :ns-count         (count nodes)})

;;; ── cycle findings ──────────────────────────────────────────────────────

(defn find-cycles
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

(defn find-sdp-violations
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

(defn find-god-modules
  "Shared-role namespaces with reach or fan-in > 2× the mean.
  These are bottlenecks through which too much flows in both directions."
  [nodes]
  (let [reach-mean  (mean (map :reach nodes))
        fan-in-mean (mean (map :fan-in nodes))]
    (into []
          (keep (fn [{:keys [ns reach fan-in role] :as node}]
                  (when (and (= role :shared)
                             (or (> reach (* 2.0 reach-mean))
                                 (> fan-in (* 2.0 fan-in-mean))))
                    {:severity :medium
                     :category :god-module
                     :subject  {:ns ns}
                     :reason   (str "shared module — reach="
                                    (format "%.1f%%" (* 100.0 reach))
                                    " fan-in="
                                    (format "%.1f%%" (* 100.0 fan-in)))
                     :evidence {:reach reach :fan-in fan-in
                                :ca (:ca node) :ce (:ce node)
                                :role role}})))
          nodes)))

(defn find-hubs
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
