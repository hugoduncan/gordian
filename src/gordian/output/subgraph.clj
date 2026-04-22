(ns gordian.output.subgraph
  (:require [clojure.string :as str]
            [gordian.output.common :as common]))

(defn- format-subgraph-pair-line [pair]
  (str "  " (:ns-a pair) " ↔ " (:ns-b pair)
       (when-let [a (:actionability-score pair)]
         (str " [act=" (format "%.1f" a) "]"))
       (when-let [s (:score pair)]
         (str " score=" (format "%.2f" s)))))

(defn format-subgraph
  "Format subgraph/family view as human-readable lines."
  [{:keys [prefix rank-by members internal boundary pairs findings clusters error]}]
  (if error
    [(str "Error: " error)]
    (into
     [(str "gordian subgraph — " prefix)
      (str "members: " (count members))
      (str "rank: " (name rank-by))
      ""
      "MEMBERS"]
     (concat
      (map #(str "  " %) members)
      ["" "INTERNAL"
       (str "  nodes: " (get internal :node-count))
       (str "  edges: " (get internal :edge-count))
       (str "  density: " (format "%.1f%%" (* 100.0 (get internal :density 0.0))))
       (str "  propagation cost: " (format "%.1f%%" (* 100.0 (get internal :propagation-cost 0.0))))
       (str "  cycles: " (if (seq (:cycles internal)) (count (:cycles internal)) "none"))
       ""
       "BOUNDARY"
       (str "  incoming edges: " (:incoming-count boundary))
       (str "  outgoing edges: " (:outgoing-count boundary))
       (str "  dependents: " (if (seq (:dependents boundary))
                               (str/join ", " (:dependents boundary))
                               "none"))
       (str "  external deps: " (if (seq (:external-deps boundary))
                                  (str/join ", " (:external-deps boundary))
                                  "none"))]
      (when (seq (get-in pairs [:conceptual :internal]))
        (concat ["" "INTERNAL CONCEPTUAL PAIRS"]
                (map format-subgraph-pair-line (get-in pairs [:conceptual :internal]))))
      (when (seq (get-in pairs [:change :internal]))
        (concat ["" "INTERNAL CHANGE PAIRS"]
                (map format-subgraph-pair-line (get-in pairs [:change :internal]))))
      (when (seq findings)
        (concat ["" "TOUCHING FINDINGS"]
                (mapcat common/format-finding-lines findings)))
      (when (seq clusters)
        (common/format-cluster-section clusters))))))

(defn format-subgraph-md
  "Format subgraph/family view as markdown lines."
  [{:keys [prefix rank-by members internal boundary pairs findings clusters error]}]
  (if error
    [(str "**Error:** " error)]
    (into
     [(str "# gordian subgraph — " prefix)
      ""
      (str "**Rank:** `" (name rank-by) "`")
      ""
      "## Members"
      ""]
     (concat
      (map #(str "- `" % "`") members)
      ["" "## Internal" ""
       "| Metric | Value |"
       "|--------|-------|"
       (str "| Nodes | " (get internal :node-count) " |")
       (str "| Edges | " (get internal :edge-count) " |")
       (str "| Density | " (format "%.1f%%" (* 100.0 (get internal :density 0.0))) " |")
       (str "| Propagation cost | " (format "%.1f%%" (* 100.0 (get internal :propagation-cost 0.0))) " |")
       (str "| Cycles | " (if (seq (:cycles internal)) (count (:cycles internal)) "none") " |")
       ""
       "## Boundary"
       ""
       "| Metric | Value |"
       "|--------|-------|"
       (str "| Incoming edges | " (:incoming-count boundary) " |")
       (str "| Outgoing edges | " (:outgoing-count boundary) " |")
       (str "| Dependents | " (if (seq (:dependents boundary))
                                (str/join ", " (:dependents boundary))
                                "none") " |")
       (str "| External deps | " (if (seq (:external-deps boundary))
                                   (str/join ", " (:external-deps boundary))
                                   "none") " |")]
      (when (seq (get-in pairs [:conceptual :internal]))
        (concat ["" "## Internal Conceptual Pairs" ""]
                (map (fn [pair]
                       (str "- `" (:ns-a pair) "` ↔ `" (:ns-b pair) "`"
                            (when-let [a (:actionability-score pair)]
                              (str " [act=" (format "%.1f" a) "]"))
                            (when-let [s (:score pair)]
                              (str " score=" (format "%.2f" s)))))
                     (get-in pairs [:conceptual :internal]))))
      (when (seq findings)
        (concat ["" "## Findings" ""]
                (mapcat common/md-format-finding findings)))
      (when (seq clusters)
        (common/md-format-cluster-section clusters))))))

(defn print-subgraph
  "Print a human-readable subgraph report to stdout."
  [data]
  (run! println (format-subgraph data)))
