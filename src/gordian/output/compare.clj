(ns gordian.output.compare
  (:require [clojure.string :as str]
            [gordian.output.common :as common]))

(defn- format-compare-health [{:keys [before after delta]}]
  [(str "  propagation cost: "
        (format "%.1f%%" (* 100.0 (:propagation-cost before)))
        " → " (format "%.1f%%" (* 100.0 (:propagation-cost after)))
        "  " (common/arrow (:propagation-cost delta))
        (common/delta-str (:propagation-cost delta) "%.1f%%"))
   (str "  cycles: " (:cycle-count before) " → " (:cycle-count after)
        (when (not= 0 (:cycle-count delta))
          (str "  " (common/arrow (:cycle-count delta))
               (common/delta-str (:cycle-count delta) "%d"))))
   (str "  namespaces: " (:ns-count before) " → " (:ns-count after)
        (when (not= 0 (:ns-count delta))
          (str "  " (common/arrow (:ns-count delta))
               (common/delta-str (:ns-count delta) "%d"))))])

(defn- format-compare-nodes [{:keys [added removed changed]}]
  (concat
   (when (seq removed)
     (cons "  Removed:"
           (mapv #(str "    - " (:ns %)) removed)))
   (when (seq added)
     (cons "  Added:"
           (mapv #(str "    + " (:ns %)) added)))
   (when (seq changed)
     (cons "  Changed:"
           (mapcat (fn [{:keys [ns delta]}]
                     [(str "    " ns
                           (when-let [r (:role delta)]
                             (str "  role: " (:before r) " → " (:after r)))
                           (when-let [d (:instability delta)]
                             (str "  I" (common/delta-str d "%.2f")))
                           (when-let [d (:reach delta)]
                             (str "  reach" (common/delta-str (* 100.0 d) "%.1f%%")))
                           (when-let [d (:ca delta)]
                             (str "  Ca" (common/delta-str d "%.0f")))
                           (when-let [d (:ce delta)]
                             (str "  Ce" (common/delta-str d "%.0f"))))])
                   changed)))))

(defn- format-compare-cycles [{:keys [added removed]}]
  (concat
   (when (seq removed)
     (mapv #(str "  ✅ removed: " (str/join " ↔ " (sort-by str %))) removed))
   (when (seq added)
     (mapv #(str "  🔴 added: " (str/join " ↔ " (sort-by str %))) added))))

(defn- format-compare-pairs [{:keys [added removed changed]}]
  (concat
   (when (seq removed)
     (mapv #(str "  ✅ removed: " (:ns-a %) " ↔ " (:ns-b %)
                 " (was " (format "%.2f" (get-in % [:score])) ")")
           removed))
   (when (seq added)
     (mapv #(str "  🔴 added: " (:ns-a %) " ↔ " (:ns-b %)
                 " (" (format "%.2f" (:score %)) ")")
           added))
   (when (seq changed)
     (mapv #(str "  " (common/arrow (get-in % [:delta :score])) " "
                 (:ns-a %) " ↔ " (:ns-b %)
                 "  " (format "%.2f" (get-in % [:before :score]))
                 " → " (format "%.2f" (get-in % [:after :score]))
                 "  (" (common/delta-str (get-in % [:delta :score]) "%.2f") ")")
           changed))))

(defn- format-compare-findings [{:keys [added removed]}]
  (concat
   (when (seq removed)
     (mapv #(str "  ✅ " (name (:category %)) ": "
                 (common/format-finding-subject %) " — " (:reason %))
           removed))
   (when (seq added)
     (mapv #(str "  🔴 " (name (:category %)) ": "
                 (common/format-finding-subject %) " — " (:reason %))
           added))))

(defn- section-if-nonempty
  "Emit header + lines only when content is non-empty."
  [header lines]
  (when (seq lines)
    (into [header] lines)))

(defn format-compare
  "Format a compare diff as human-readable lines."
  [diff]
  (let [health   (:health diff)
        nodes    (:nodes diff)
        cycles   (:cycles diff)
        c-pairs  (:conceptual-pairs diff)
        x-pairs  (:change-pairs diff)
        findings (:findings diff)]
    (into
     ["gordian compare"
      ""
      "HEALTH"]
     (concat
      (format-compare-health health)
      [""]
      (section-if-nonempty "NAMESPACES" (format-compare-nodes nodes))
      (when (seq (format-compare-nodes nodes)) [""])
      (section-if-nonempty "CYCLES" (format-compare-cycles cycles))
      (when (seq (format-compare-cycles cycles)) [""])
      (section-if-nonempty "CONCEPTUAL PAIRS" (format-compare-pairs c-pairs))
      (when (seq (format-compare-pairs c-pairs)) [""])
      (section-if-nonempty "CHANGE PAIRS" (format-compare-pairs x-pairs))
      (when (seq (format-compare-pairs x-pairs)) [""])
      (section-if-nonempty "FINDINGS" (format-compare-findings findings))))))

(defn format-compare-md
  "Format a compare diff as markdown lines."
  [diff]
  (let [health   (:health diff)
        nodes    (:nodes diff)
        cycles   (:cycles diff)
        c-pairs  (:conceptual-pairs diff)
        x-pairs  (:change-pairs diff)
        findings (:findings diff)]
    (into
     ["# gordian compare" ""]
     (concat
      ["## Health" ""
       "| Metric | Before | After | Δ |"
       "|--------|--------|-------|---|"
       (str "| Propagation cost | "
            (format "%.1f%%" (* 100.0 (get-in health [:before :propagation-cost])))
            " | "
            (format "%.1f%%" (* 100.0 (get-in health [:after :propagation-cost])))
            " | " (common/delta-str (get-in health [:delta :propagation-cost]) "%.1f%%") " |")
       (str "| Cycles | " (get-in health [:before :cycle-count])
            " | " (get-in health [:after :cycle-count])
            " | " (common/delta-str (get-in health [:delta :cycle-count]) "%d") " |")
       (str "| Namespaces | " (get-in health [:before :ns-count])
            " | " (get-in health [:after :ns-count])
            " | " (common/delta-str (get-in health [:delta :ns-count]) "%d") " |")
       ""]
      (when (or (seq (:added nodes)) (seq (:removed nodes)) (seq (:changed nodes)))
        (concat
         ["## Namespaces" ""]
         (when (seq (:added nodes))
           (cons "**Added:**"
                 (conj (mapv #(str "- `" (:ns %) "`") (:added nodes)) "")))
         (when (seq (:removed nodes))
           (cons "**Removed:**"
                 (conj (mapv #(str "- `" (:ns %) "`") (:removed nodes)) "")))
         (when (seq (:changed nodes))
           (concat
            ["**Changed:**" ""
             "| Namespace | Metric | Before | After | Δ |"
             "|-----------|--------|--------|-------|---|"]
            (mapcat (fn [{:keys [ns before after delta]}]
                      (keep (fn [k]
                              (when-let [d (get delta k)]
                                (if (map? d)
                                  (str "| `" ns "` | " (name k) " | " (:before d) " | " (:after d) " | — |")
                                  (str "| `" ns "` | " (name k) " | "
                                       (let [bv (get before k)] (if (float? bv) (format "%.2f" bv) bv))
                                       " | "
                                       (let [av (get after k)] (if (float? av) (format "%.2f" av) av))
                                       " | " (if (float? d) (common/delta-str d "%.2f") (common/delta-str d "%d")) " |"))))
                            (keys delta)))
                    (:changed nodes))
            [""]))))
      (when (or (seq (:added cycles)) (seq (:removed cycles)))
        (concat
         ["## Cycles" ""]
         (when (seq (:removed cycles))
           (mapv #(str "- ✅ Removed: " (str/join " ↔ " (sort-by str %))) (:removed cycles)))
         (when (seq (:added cycles))
           (mapv #(str "- 🔴 Added: " (str/join " ↔ " (sort-by str %))) (:added cycles)))
         [""]))
      (when (or (seq (:added c-pairs)) (seq (:removed c-pairs)) (seq (:changed c-pairs)))
        (concat
         ["## Conceptual Pairs" ""]
         (when (seq (:removed c-pairs))
           (mapv #(str "- ✅ `" (:ns-a %) "` ↔ `" (:ns-b %) "` (was " (format "%.2f" (:score %)) ")")
                 (:removed c-pairs)))
         (when (seq (:added c-pairs))
           (mapv #(str "- 🔴 `" (:ns-a %) "` ↔ `" (:ns-b %) "` (" (format "%.2f" (:score %)) ")")
                 (:added c-pairs)))
         (when (seq (:changed c-pairs))
           (mapv #(str "- " (common/arrow (get-in % [:delta :score]))
                       " `" (:ns-a %) "` ↔ `" (:ns-b %) "`"
                       " " (format "%.2f" (get-in % [:before :score]))
                       " → " (format "%.2f" (get-in % [:after :score]))
                       " (" (common/delta-str (get-in % [:delta :score]) "%.2f") ")")
                 (:changed c-pairs)))
         [""]))
      (when (or (seq (:added x-pairs)) (seq (:removed x-pairs)) (seq (:changed x-pairs)))
        (concat
         ["## Change Pairs" ""]
         (when (seq (:removed x-pairs))
           (mapv #(str "- ✅ `" (:ns-a %) "` ↔ `" (:ns-b %) "` (was " (format "%.2f" (:score %)) ")")
                 (:removed x-pairs)))
         (when (seq (:added x-pairs))
           (mapv #(str "- 🔴 `" (:ns-a %) "` ↔ `" (:ns-b %) "` (" (format "%.2f" (:score %)) ")")
                 (:added x-pairs)))
         (when (seq (:changed x-pairs))
           (mapv #(str "- " (common/arrow (get-in % [:delta :score]))
                       " `" (:ns-a %) "` ↔ `" (:ns-b %) "`"
                       " " (format "%.2f" (get-in % [:before :score]))
                       " → " (format "%.2f" (get-in % [:after :score]))
                       " (" (common/delta-str (get-in % [:delta :score]) "%.2f") ")")
                 (:changed x-pairs)))
         [""]))
      (when (or (seq (:added findings)) (seq (:removed findings)))
        (concat
         ["## Findings" ""]
         (when (seq (:removed findings))
           (mapv #(str "- ✅ " (name (:category %)) ": " (:reason %))
                 (:removed findings)))
         (when (seq (:added findings))
           (mapv #(str "- 🔴 " (name (:category %)) ": " (:reason %))
                 (:added findings)))))))))

(defn print-compare
  "Print a human-readable compare report to stdout."
  [diff]
  (run! println (format-compare diff)))
