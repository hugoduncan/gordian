(ns gordian.diagnose
  "Generate ranked findings from a gordian report.
  All functions are pure — they take data and return data."
  (:require [clojure.set :as set]
            [clojure.string :as str]
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

(defn- cycle-strategy
  "Derive a resolution strategy hint for a cycle from available evidence.
  Returns {:strategy keyword :basis map-or-nil}.
  Evaluated top-down; first match wins:
    merge     — a member pair co-changes almost always (Jaccard > 0.7)
    extract   — a member pair shares independent domain vocabulary
    invert    — a stable high-Ca member can host the protocol
    investigate — no discriminating evidence"
  [members change-pairs conceptual-pairs nodes]
  (let [member-set (set members)
        in-cycle?  (fn [p] (and (contains? member-set (:ns-a p))
                                (contains? member-set (:ns-b p))))
        node-map   (into {} (map (juxt :ns identity)) nodes)]
    (or
     (when-let [p (first (filter #(and (in-cycle? %) (> (:score %) 0.7))
                                 change-pairs))]
       {:strategy :merge
        :basis    {:ns-a (:ns-a p) :ns-b (:ns-b p) :score (:score p)}})
     (when-let [p (first (filter #(and (in-cycle? %)
                                       (seq (:independent-terms %)))
                                 conceptual-pairs))]
       {:strategy :extract
        :basis    {:ns-a (:ns-a p) :ns-b (:ns-b p)
                   :shared-terms (:independent-terms p)}})
     (when-let [n (first (filter #(and (>= (or (:ca %) 0) 3)
                                       (< (or (:instability %) 1.0) 0.3))
                                 (map #(get node-map %) members)))]
       {:strategy :invert
        :basis    {:ns (:ns n) :ca (:ca n) :instability (:instability n)}})
     {:strategy :investigate :basis nil})))

(def ^:private strategy-suffix-fn
  {:merge       (fn [{:keys [ns-a ns-b score]}]
                  (str " — merge " ns-a " and " ns-b
                       " (Jaccard=" (format "%.2f" (double score)) ")"))
   :extract     (fn [{:keys [shared-terms]}]
                  (str " — extract concept ["
                       (str/join " " shared-terms) "] from cycle"))
   :invert      (fn [{:keys [ns instability]}]
                  (str " — invert: put protocol in " ns
                       " (I=" (format "%.2f" (double instability)) ")"))
   :investigate (fn [_] " — investigate")})

(defn- find-cycles
  "Convert SCC cycle sets into findings. Each cycle is a :high finding.
  Uses change-pairs, conceptual-pairs, and nodes to derive a strategy hint."
  [cycles change-pairs conceptual-pairs nodes]
  (mapv (fn [members]
          (let [subject {:members members}
                strat   (cycle-strategy members change-pairs conceptual-pairs nodes)
                suffix  ((get strategy-suffix-fn (:strategy strat)) (:basis strat))
                reason  (str (count members) "-namespace cycle" suffix)]
            {:severity  :high
             :category  :cycle
             :action    (finding/action-for-category :cycle)
             :next-step (finding/next-step-for :cycle subject nodes)
             :subject   subject
             :reason    reason
             :evidence  {:members        members
                         :size           (count members)
                         :strategy       (:strategy strat)
                         :strategy-basis (:basis strat)}}))
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
                  (let [subject {:ns ns}]
                    {:severity  :medium
                     :category  :sdp-violation
                     :action    (finding/action-for-category :sdp-violation)
                     :next-step (finding/next-step-for :sdp-violation subject nil)
                     :subject   subject
                     :reason    (str "high Ca=" ca " but I=" (format "%.2f" instability)
                                     " — depended upon yet unstable")
                     :evidence  {:ca ca :ce (:ce node) :instability instability
                                 :role (:role node)}}))))
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
                                        :family      (:family node)}
                          subject      {:ns ns}]
                      (if (facade? node)
                        {:severity  :low
                         :category  :facade
                         :action    (finding/action-for-category :facade)
                         :next-step (finding/next-step-for :facade subject nil)
                         :subject   subject
                         :reason    (str "likely façade — Ca-ext="
                                         (:ca-external node)
                                         " Ce-ext=" (:ce-external node)
                                         " Ce-fam=" (:ce-family node)
                                         " — delegates to family, outside world depends on it")
                         :evidence  (merge {:reach reach :fan-in fan-in
                                            :ca (:ca node) :ce (:ce node)
                                            :role role}
                                           fam-evidence)}
                        {:severity  :medium
                         :category  :god-module
                         :action    (finding/action-for-category :god-module)
                         :next-step (finding/next-step-for :god-module subject nil)
                         :subject   subject
                         :reason    (str "shared module — reach="
                                         (format "%.1f%%" (* 100.0 reach))
                                         " fan-in="
                                         (format "%.1f%%" (* 100.0 fan-in)))
                         :evidence  (merge {:reach reach :fan-in fan-in
                                            :ca (:ca node) :ce (:ce node)
                                            :role role}
                                           fam-evidence)}))))
          nodes))))

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
  (let [hidden-c   (hidden-pair-index conceptual-pairs)
        hidden-x   (hidden-pair-index change-pairs)
        cross-keys (set/intersection (set (keys hidden-c)) (set (keys hidden-x)))]
    {:findings
     (mapv (fn [k]
             (let [cp      (get hidden-c k)
                   xp      (get hidden-x k)
                   subject {:ns-a (:ns-a cp) :ns-b (:ns-b cp)}]
               {:severity  :high
                :category  :cross-lens-hidden
                :action    (finding/action-for-category :cross-lens-hidden)
                :next-step (finding/next-step-for :cross-lens-hidden subject nil)
                :subject   subject
                :reason    (str "hidden in 2 lenses — conceptual="
                                (format "%.2f" (:score cp))
                                " change=" (format "%.2f" (:score xp)))
                :evidence  (cond-> {:conceptual-score (:score cp)
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
                        family-noise (finding/family-noise? p)
                        subject      {:ns-a (:ns-a p) :ns-b (:ns-b p)}]
                    {:severity  sev
                     :category  :hidden-conceptual
                     :action    (finding/action-for-category :hidden-conceptual)
                     :next-step (finding/next-step-for :hidden-conceptual subject nil)
                     :subject   subject
                     :reason    (if family-noise
                                  (str "hidden conceptual coupling — score="
                                       (format "%.2f" (:score p))
                                       " — likely naming similarity")
                                  (str "hidden conceptual coupling — score="
                                       (format "%.2f" (:score p))))
                     :evidence  (cond-> {:score        (:score p)
                                         :shared-terms (:shared-terms p)}
                                  (some? (:same-family? p))
                                  (assoc :same-family?      (:same-family? p)
                                         :family-terms      (:family-terms p)
                                         :independent-terms (:independent-terms p)))})))
        conceptual-pairs)))

(defn- change-asymmetry
  "Derive directionality from confidence values.
  Returns {:directional? bool :satellite ns :anchor ns :ratio double}
  when both confidences are positive; nil otherwise."
  [ns-a ns-b conf-a conf-b]
  (when (and (pos? (or conf-a 0)) (pos? (or conf-b 0)))
    (let [[satellite anchor s-conf a-conf]
          (if (> (or conf-a 0) (or conf-b 0))
            [ns-a ns-b conf-a conf-b]
            [ns-b ns-a conf-b conf-a])
          ratio (/ s-conf a-conf)]
      {:directional? (> ratio 2.0)
       :satellite    satellite
       :anchor       anchor
       :ratio        ratio})))

(defn find-hidden-change
  "Pairs hidden in change lens only (not already cross-lens).
  When conf-a/conf-b ratio > 2, emits a directional finding with
  :narrow-satellite-interface action. Otherwise symmetric."
  [change-pairs cross-keys]
  (into []
        (keep (fn [p]
                (when (and (not (:structural-edge? p))
                           (not (contains? cross-keys (finding/pair-key p))))
                  (let [subject {:ns-a (:ns-a p) :ns-b (:ns-b p)}
                        asym    (change-asymmetry (:ns-a p) (:ns-b p)
                                                  (:confidence-a p) (:confidence-b p))
                        dir?    (:directional? asym)
                        action  (if dir?
                                  :narrow-satellite-interface
                                  (finding/action-for-category :hidden-change))
                        reason  (if dir?
                                  (str (:satellite asym) " changes whenever "
                                       (:anchor asym) " changes"
                                       " (conf=" (format "%.0f%%"
                                                         (* 100.0
                                                            (max (or (:confidence-a p) 0)
                                                                 (or (:confidence-b p) 0)))) ")")
                                  (str "hidden change coupling — score="
                                       (format "%.2f" (:score p))
                                       " (" (:co-changes p) " co-changes)"))]
                    {:severity  :medium
                     :category  :hidden-change
                     :action    action
                     :next-step (finding/next-step-for :hidden-change subject nil)
                     :subject   subject
                     :reason    reason
                     :evidence  (cond-> {:score        (:score p)
                                         :co-changes   (:co-changes p)
                                         :confidence-a (:confidence-a p)
                                         :confidence-b (:confidence-b p)}
                                  dir?
                                  (assoc :direction {:satellite (:satellite asym)
                                                     :anchor    (:anchor asym)
                                                     :ratio     (:ratio asym)}))})))
        change-pairs)))

(defn- find-hubs
  "Namespaces with reach > 3× mean. Informational — entry points naturally
  have high reach, but extreme values warrant monitoring."
  [nodes]
  (let [reach-mean (mean (map :reach nodes))]
    (into []
          (keep (fn [{:keys [ns reach] :as node}]
                  (when (and (pos? reach-mean)
                             (> reach (* 3.0 reach-mean)))
                    (let [subject {:ns ns}]
                      {:severity  :low
                       :category  :hub
                       :action    (finding/action-for-category :hub)
                       :next-step (finding/next-step-for :hub subject nil)
                       :subject   subject
                       :reason    (str "high-reach hub — "
                                       (format "%.1f%%" (* 100.0 reach))
                                       " of project reachable")
                       :evidence  {:reach reach :ce (:ce node)
                                   :instability (:instability node)
                                   :role (:role node)}})))
          nodes))))



;;; ── vestigial edge findings ──────────────────────────────────────────────

(defn- find-vestigial-edges
  "Structural edges with no conceptual signal and no change coupling.
  A remove-dependency candidate — the edge exists in code but carries no
  signal justifying it in either lens.
  Only runs when both lenses are active (both collections non-nil);
  returns [] otherwise to avoid false positives on unscanned codebases.
  Skips edges where either endpoint has role :peripheral — wiring modules
  are expected to depend on pure modules without shared vocabulary."
  [graph nodes conceptual-pairs change-pairs]
  (if (or (nil? conceptual-pairs) (nil? change-pairs))
    []
    (let [project-nss  (set (keys graph))
          concept-keys (into #{} (map finding/pair-key) conceptual-pairs)
          change-keys  (into #{} (map finding/pair-key) change-pairs)
          node-map     (into {} (map (juxt :ns identity)) nodes)
          peripheral?  (fn [ns] (= :peripheral (:role (get node-map ns))))]
      (into []
            (mapcat
             (fn [[ns-a deps]]
               (keep
                (fn [ns-b]
                  (when (and (contains? project-nss ns-b)
                             (not (peripheral? ns-a))
                             (not (peripheral? ns-b)))
                    (let [k #{ns-a ns-b}]
                      (when (and (not (contains? concept-keys k))
                                 (not (contains? change-keys k)))
                        (let [subject {:ns-a ns-a :ns-b ns-b}]
                          {:severity  :low
                           :category  :vestigial-edge
                           :action    (finding/action-for-category :vestigial-edge)
                           :next-step (finding/next-step-for :vestigial-edge subject nil)
                           :subject   subject
                           :reason    (str ns-a " → " ns-b
                                           " — no conceptual or change signal")
                           :evidence  {:ns-a          ns-a
                                       :ns-b          ns-b
                                       :instability-a (:instability (get node-map ns-a))
                                       :instability-b (:instability (get node-map ns-b))}})))))
                deps))
             graph)))))

;;; ── main diagnose function ──────────────────────────────────────────────

(defn diagnose
  "Generate all findings from a complete report map.
  Returns findings in generation order — callers are responsible for sorting.
  Destructures :graph for vestigial-edge detection; safe when absent."
  [{:keys [cycles nodes conceptual-pairs change-pairs graph] :as _report}]
  (let [nodes*  (or nodes [])
        cpairs  (or conceptual-pairs [])
        xpairs  (or change-pairs [])
        {:keys [findings cross-keys]}
        (find-cross-lens-hidden cpairs xpairs)]
    (vec
     (concat
      (find-cycles (or cycles []) xpairs cpairs nodes*)
      findings
      (find-hidden-conceptual cpairs cross-keys)
      (find-hidden-change xpairs cross-keys)
      (find-sdp-violations nodes*)
      (find-god-modules nodes*)
      (find-hubs nodes*)
      (find-vestigial-edges (or graph {}) nodes* conceptual-pairs change-pairs)))))
