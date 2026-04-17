(ns gordian.finding
  "Shared finding semantics: identity, predicates, and evidence accessors."
  (:require [clojure.string :as str]))

(def hidden-pair-categories
  #{:cross-lens-hidden :hidden-conceptual :hidden-change})

(defn pair-key
  "Canonical pair identity — unordered set of two namespace symbols."
  [pair]
  #{(:ns-a pair) (:ns-b pair)})

(defn finding-key
  "Identity key for a finding — category + subject."
  [finding]
  [(:category finding) (:subject finding)])

(defn hidden-pair-category?
  "True when the finding category represents a hidden pair signal."
  [category]
  (contains? hidden-pair-categories category))

(defn family-noise?
  "True when evidence suggests same-family naming similarity with no
  independent terms. Accepts a finding or an evidence map."
  [{:keys [evidence same-family? independent-terms]}]
  (let [evidence (or evidence {:same-family? same-family?
                               :independent-terms independent-terms})]
    (and (:same-family? evidence)
         (empty? (:independent-terms evidence)))))

(defn finding-score
  "Primary numeric score for a finding, when present."
  [finding]
  (or (get-in finding [:evidence :score])
      (get-in finding [:evidence :conceptual-score])
      0.0))

;;; ── action mapping ──────────────────────────────────────────────────────

(def ^:private action-map
  {:cycle                 :fix-cycle
   :cross-lens-hidden     :extract-abstraction
   :sdp-violation         :split-stable-adapter
   :god-module            :split-responsibilities
   :vestigial-edge        :remove-dependency
   :hidden-conceptual     :monitor-or-extract
   :hidden-change         :investigate-contract
   :hub                   :monitor
   :facade                :none})

(defn action-for-category
  "Action keyword implied by a finding category. Returns nil for unknown."
  [category]
  (get action-map category))

(def action-display
  "Human-readable display string for each action keyword.
  :none maps to nil — no action line should be emitted."
  {:fix-cycle                  "fix cycle"
   :extract-abstraction        "extract abstraction"
   :split-stable-adapter       "split: stable model + adapter"
   :split-responsibilities     "split by responsibility"
   :remove-dependency          "remove dependency"
   :narrow-satellite-interface "narrow interface"
   :monitor-or-extract         "monitor — consider extracting"
   :investigate-contract       "investigate implicit contract"
   :monitor                    "monitor"
   :none                       nil})

;;; ── next-step helpers ────────────────────────────────────────────────────

(defn- common-ns-prefix
  "Longest common dot-separated prefix shared by all namespace strings.
  Returns nil when there is no shared prefix."
  [ns-strs]
  (when (seq ns-strs)
    (let [parts   (mapv #(str/split (str %) #"\.") ns-strs)
          min-len (apply min (map count parts))
          common  (->> (range min-len)
                       (take-while #(apply = (map (fn [p] (nth p %)) parts)))
                       (mapv #(nth (first parts) %)))]
      (when (seq common)
        (str/join "." common)))))

(defn next-step-for
  "Suggest the most useful gordian command to investigate a finding.
  category — finding :category keyword.
  subject  — finding :subject map.
  nodes    — full node list (used for cycle Ca-based fallback); may be nil.
  Returns a command string or nil."
  [category subject nodes]
  (case category
    (:cross-lens-hidden :hidden-conceptual :hidden-change :vestigial-edge)
    (str "gordian explain-pair " (:ns-a subject) " " (:ns-b subject))

    (:sdp-violation :god-module :hub :facade)
    (str "gordian explain " (:ns subject))

    :cycle
    (let [members (:members subject)
          prefix  (common-ns-prefix (map str members))]
      (if (seq prefix)
        (str "gordian subgraph " prefix)
        (let [node-map (into {} (map (juxt :ns identity)) (or nodes []))
              best     (apply max-key #(or (:ca (get node-map %)) 0) members)]
          (str "gordian explain " best))))

    nil))

;;; ── ordering ─────────────────────────────────────────────────────────────

(defn severity-rank
  "Numeric rank for severity, lower = higher priority. Unknown → 3."
  [s]
  (case s :high 0 :medium 1 :low 2 3))

;;; ── magnitude ────────────────────────────────────────────────────────────

(defn finding-magnitude
  "Primary magnitude used for sorting findings within a severity tier."
  [finding]
  (or (get-in finding [:evidence :score])
      (get-in finding [:evidence :conceptual-score])
      (get-in finding [:evidence :reach])
      (get-in finding [:evidence :size])
      (get-in finding [:evidence :instability])
      0))
