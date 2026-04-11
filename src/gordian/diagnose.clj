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
