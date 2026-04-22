(ns gordian.output.analyze
  (:require [clojure.string :as str]
            [gordian.output.common :as common]))

(defn column-widths
  "Return {:ns-col int} sized to fit the longest namespace name."
  [nodes]
  {:ns-col (max 20 (apply max 0 (map #(count (str (:ns %))) nodes)))})

(defn format-row
  "Format one node row as a string."
  [ns-col {:keys [ns reach fan-in ca ce instability role]}]
  (str (common/pad-right ns-col (str ns))
       "  " (common/pct reach)
       "  " (common/pct fan-in)
       "  " (common/pad-left 3 (str (or ce "-")))
       "  " (common/pad-left 3 (str (or ca "-")))
       "  " (if instability (format "%4.2f" instability) "   -")
       "  " (if role (name role) "")))

(defn- format-cycles
  "Return lines describing detected cycles.
  Returns nil when there are no cycles — the section is omitted entirely."
  [cycles]
  (when (seq cycles)
    (into ["cycles:"]
          (map-indexed (fn [i members]
                         (str "  [" (inc i) "] "
                              (str/join " → " (sort (map str members)))
                              "  (" (count members) " namespaces)"))
                       cycles))))

(defn- conceptual-ns-col
  "Column width fitting the longest namespace name across all pairs."
  [pairs]
  (apply max 20
         (mapcat (fn [{:keys [ns-a ns-b]}]
                   [(count (str ns-a)) (count (str ns-b))])
                 pairs)))

(defn format-conceptual
  "Return a vector of lines for the conceptual coupling section.
  Returns [] when pairs is empty — the section is omitted entirely.
  Shared terms shown for all pairs: ← flags no-structural-edge rows (the
  discovery rows); terms on structural rows show what the coupling is about."
  [pairs threshold]
  (if (empty? pairs)
    []
    (let [ns-col (conceptual-ns-col pairs)
          header (str (common/pad-right ns-col "namespace-a")
                      "  " (common/pad-right ns-col "namespace-b")
                      "  score  structural  shared concepts")
          rule   (apply str (repeat (count header) "─"))]
      (into
       [(str "conceptual coupling (score ≥ " (format "%.2f" (double threshold)) "):")
        ""
        header
        rule]
       (map (fn [{:keys [ns-a ns-b score structural-edge? shared-terms]}]
              (str (common/pad-right ns-col (str ns-a))
                   "  " (common/pad-right ns-col (str ns-b))
                   "  " (format "%4.2f" (double score))
                   "  " (if structural-edge?
                          (str "yes      " (str/join " " shared-terms))
                          (str "no  ←    " (str/join " " shared-terms)))))
            pairs)))))

(defn- change-ns-col
  "Column width fitting the longest namespace name across all pairs."
  [pairs]
  (apply max 20
         (mapcat (fn [{:keys [ns-a ns-b]}]
                   [(count (str ns-a)) (count (str ns-b))])
                 pairs)))

(defn format-change-coupling
  "Return a vector of lines for the change coupling section.
  Returns [] when pairs is empty — section omitted entirely.
  Columns: namespace-a  namespace-b  Jaccard  co  conf-a  conf-b  structural
  ← flags non-structural-edge rows (the discovery signal)."
  [pairs threshold]
  (if (empty? pairs)
    []
    (let [ns-col (change-ns-col pairs)
          header (str (common/pad-right ns-col "namespace-a")
                      "  " (common/pad-right ns-col "namespace-b")
                      "  Jaccard  co  conf-a  conf-b  structural")
          rule   (apply str (repeat (count header) "─"))]
      (into
       [(str "change coupling (Jaccard ≥ " (format "%.2f" (double threshold)) "):")
        ""
        header
        rule]
       (map (fn [{:keys [ns-a ns-b score co-changes
                         confidence-a confidence-b structural-edge?]}]
              (str (common/pad-right ns-col (str ns-a))
                   "  " (common/pad-right ns-col (str ns-b))
                   "  " (format "%6.4f" (double score))
                   "  " (common/pad-left 2 (str co-changes))
                   "  " (format "%5.1f%%" (* 100.0 confidence-a))
                   "  " (format "%5.1f%%" (* 100.0 confidence-b))
                   "  " (if structural-edge?
                          "yes"
                          "no  ←")))
            pairs)))))

(defn format-report
  "Return a vector of lines for the full coupling report.
  `report` — {:src-dirs :propagation-cost :cycles :nodes [node-maps]}.
  Cycles section omitted when none present.
  Conceptual section appended when :conceptual-pairs is present.
  Change coupling section appended when :change-pairs is present."
  [{:keys [src-dirs propagation-cost cycles nodes
           conceptual-pairs conceptual-threshold
           change-pairs change-threshold]}]
  (let [{:keys [ns-col]} (column-widths nodes)
        header (str (common/pad-right ns-col "namespace")
                    "    reach   fan-in   Ce   Ca      I  role")
        rule   (apply str (repeat (count header) "─"))
        cycle-lines (format-cycles cycles)
        middle (if cycle-lines
                 (concat [""] cycle-lines ["" header rule])
                 ["" header rule])
        conceptual-lines (when (seq conceptual-pairs)
                           (into [""] (format-conceptual conceptual-pairs
                                                         conceptual-threshold)))
        change-lines (when (seq change-pairs)
                       (into [""] (format-change-coupling change-pairs
                                                          change-threshold)))]
    (into
     (into
      ["gordian — namespace coupling report"
       (str "src: " (str/join " " src-dirs))
       ""
       (str "propagation cost: " (format "%.4f" propagation-cost)
            "  (on average " (format "%.1f" (* 100.0 propagation-cost))
            "% of project reachable per change)")]
      middle)
     (concat
      (map (partial format-row ns-col) nodes)
      [""]
      conceptual-lines
      change-lines))))

(defn format-report-md
  "Markdown rendering of the analyze report."
  [{:keys [src-dirs propagation-cost cycles nodes
           conceptual-pairs conceptual-threshold
           change-pairs change-threshold]}]
  (into
   ["# Gordian — Namespace Coupling Report"
    ""
    (str "**Source:** `" (str/join " " src-dirs) "`")
    ""
    "## Summary"
    ""
    "| Metric | Value |"
    "|--------|-------|"
    (str "| Propagation cost | " (common/md-pct propagation-cost) " |")
    (str "| Namespaces | " (count nodes) " |")
    (str "| Cycles | " (count cycles) " |")]
   (concat
    (when (seq cycles)
      (into ["" "## Cycles" ""]
            (map-indexed
             (fn [i members]
               (str (inc i) ". "
                    (str/join " → " (sort (map str members)))
                    " (" (count members) " namespaces)"))
             cycles)))
    ["" "## Namespace Metrics" ""
     "| Namespace | Reach | Fan-in | Ce | Ca | I | Role |"
     "|-----------|-------|--------|----|----|---|------|"]
    (map (fn [{:keys [ns reach fan-in ca ce instability role]}]
           (str "| " ns
                " | " (common/md-pct reach)
                " | " (common/md-pct fan-in)
                " | " (or ce "-")
                " | " (or ca "-")
                " | " (if instability (format "%.2f" instability) "-")
                " | " (if role (name role) "")
                " |"))
         nodes)
    (when (seq conceptual-pairs)
      (into ["" (str "## Conceptual Coupling (score ≥ "
                     (format "%.2f" (double conceptual-threshold)) ")")
             ""
             "| Namespace A | Namespace B | Score | Structural | Shared Concepts |"
             "|-------------|-------------|-------|------------|-----------------|"]
            (map (fn [{:keys [ns-a ns-b score structural-edge? shared-terms]}]
                   (str "| " ns-a
                        " | " ns-b
                        " | " (format "%.2f" (double score))
                        " | " (if structural-edge? "yes" "no ←")
                        " | " (str/join ", " shared-terms)
                        " |"))
                 conceptual-pairs)))
    (when (seq change-pairs)
      (into ["" (str "## Change Coupling (Jaccard ≥ "
                     (format "%.2f" (double change-threshold)) ")")
             ""
             "| Namespace A | Namespace B | Jaccard | Co | Conf A | Conf B | Structural |"
             "|-------------|-------------|---------|-----|--------|--------|------------|"]
            (map (fn [{:keys [ns-a ns-b score co-changes
                              confidence-a confidence-b structural-edge?]}]
                   (str "| " ns-a
                        " | " ns-b
                        " | " (format "%.4f" (double score))
                        " | " co-changes
                        " | " (common/md-pct confidence-a)
                        " | " (common/md-pct confidence-b)
                        " | " (if structural-edge? "yes" "no ←")
                        " |"))
                 change-pairs))))))

(defn print-report
  "Print a human-readable coupling report to stdout.
  `report` — unified map from gordian.main/build-report."
  [report]
  (run! println (format-report report)))
