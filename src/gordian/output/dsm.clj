(ns gordian.output.dsm
  (:require [clojure.string :as str]))

(defn- format-block-members [members]
  (str/join ", " (map str members)))

(defn- format-collapsed-edge-line [{:keys [from to edge-count]}]
  (str "  B" from " -> B" to "  " edge-count " edge"
       (when (not= 1 edge-count) "s")))

(defn format-dsm
  "Return a vector of lines for DSM output."
  [{:keys [src-dirs summary blocks edges details ordering]}]
  (let [{:keys [block-count singleton-block-count largest-block-size
                inter-block-edge-count density]} summary
        block-lines (map (fn [{:keys [id size density members]}]
                           (str "  B" id
                                "  size " size
                                "  density " (format "%.2f" (double density))
                                "  members: " (format-block-members members)))
                         blocks)
        edge-lines  (if (seq edges)
                      (map format-collapsed-edge-line edges)
                      ["  (none)"])
        detail-lines (when (seq details)
                       (mapcat (fn [{:keys [id size members internal-edge-count density internal-edges]}]
                                 [(str "Block B" id)
                                  (str "  size: " size)
                                  (str "  members: " (format-block-members members))
                                  (str "  internal edges: " internal-edge-count)
                                  (str "  density: " (format "%.2f" (double density)))
                                  (str "  mini-matrix edges: " (pr-str internal-edges))
                                  ""])
                               details))]
    (vec
     (concat
      ["gordian dsm"
       (str "src: " (str/join " " src-dirs))
       ""
       "Diagonal block partition"
       (str "  ordering: " (name (:strategy ordering)))
       (str "  refined: " (if (:refined? ordering) "yes" "no"))
       (str "  alpha: " (format "%.1f" (double (:alpha ordering))))
       (str "  blocks: " block-count)
       (str "  singleton blocks: " singleton-block-count)
       (str "  largest block: " largest-block-size)
       ""
       "Block DSM"
       (str "  inter-block edges: " inter-block-edge-count)
       (str "  density: " (format "%.4f" (double density)))
       ""
       "Blocks"]
      block-lines
      ["" "Inter-block edges"]
      edge-lines
      (concat ["" "Block details" ""]
              (if (seq detail-lines)
                detail-lines
                ["No multi-namespace blocks."]))))))

(defn format-dsm-md
  "Return markdown lines for DSM output."
  [{:keys [src-dirs summary blocks edges details ordering]}]
  (vec
   (concat
    ["# Gordian DSM"
     ""
     (str "Source: `" (str/join " " src-dirs) "`")
     ""
     "## Summary"
     ""
     "| Metric | Value |"
     "|---|---:|"
     (str "| Ordering | " (name (:strategy ordering)) " |")
     (str "| Refined | " (if (:refined? ordering) "yes" "no") " |")
     (str "| Alpha | " (format "%.1f" (double (:alpha ordering))) " |")
     (str "| Blocks | " (:block-count summary) " |")
     (str "| Singleton blocks | " (:singleton-block-count summary) " |")
     (str "| Largest block | " (:largest-block-size summary) " |")
     (str "| Inter-block edges | " (:inter-block-edge-count summary) " |")
     (str "| Density | " (format "%.4f" (double (:density summary))) " |")
     ""
     "## Blocks"
     ""
     "| Block | Size | Density | Members |"
     "|---|---:|---:|---|"]
    (map (fn [{:keys [id size density members]}]
           (str "| B" id " | " size
                " | " (format "%.2f" (double density))
                " | `" (str/join "`, `" (map str members)) "` |"))
         blocks)
    ["" "## Inter-block edges" ""
     "| From | To | Edge count |"
     "|---|---|---:|"]
    (if (seq edges)
      (map (fn [{:keys [from to edge-count]}]
             (str "| B" from " | B" to " | " edge-count " |"))
           edges)
      ["| (none) |  | 0 |"])
    ["" "## Block details" ""]
    (if (seq details)
      (mapcat (fn [{:keys [id size members internal-edge-count density internal-edges]}]
                [""
                 (str "## Block B" id)
                 ""
                 (str "- Size: " size)
                 (str "- Members: `" (str/join "`, `" (map str members)) "`")
                 (str "- Internal edges: " internal-edge-count)
                 (str "- Density: " (format "%.2f" (double density)))
                 (str "- Mini-matrix edges: `" (pr-str internal-edges) "`")])
              details)
      ["No multi-namespace blocks."]))))

(defn print-dsm
  "Print a human-readable DSM report to stdout."
  [data]
  (run! println (format-dsm data)))
