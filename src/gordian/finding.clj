(ns gordian.finding
  "Shared finding semantics: identity, predicates, and evidence accessors.")

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

(defn finding-magnitude
  "Primary magnitude used for sorting findings within a severity tier."
  [finding]
  (or (get-in finding [:evidence :score])
      (get-in finding [:evidence :conceptual-score])
      (get-in finding [:evidence :reach])
      (get-in finding [:evidence :size])
      (get-in finding [:evidence :instability])
      0))
