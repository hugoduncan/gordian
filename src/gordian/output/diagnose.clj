(ns gordian.output.diagnose
  (:require [clojure.string :as str]
            [gordian.output.common :as common]))

(defn format-diagnose
  "Format findings as human-readable lines.
  health           — map from diagnose/health.
  findings         — sorted vec of finding maps.
  clusters-data    — optional {:clusters [...] :unclustered [...]} from cluster/cluster-findings.
  rank             — :severity or :actionability.
  suppressed-count — optional count of noise findings suppressed from display."
  ([report health findings]
   (format-diagnose report health findings nil :severity nil))
  ([report health findings clusters-data]
   (format-diagnose report health findings clusters-data :severity nil))
  ([report health findings clusters-data rank]
   (format-diagnose report health findings clusters-data rank nil nil))
  ([report health findings clusters-data rank suppressed-count]
   (format-diagnose report health findings clusters-data rank suppressed-count nil))
  ([{:keys [src-dirs]} health findings clusters-data rank suppressed-count truncated-from]
   (let [count-sev (fn [s] (count (filter #(= s (:severity %)) findings)))
         n-high    (count-sev :high)
         n-medium  (count-sev :medium)
         n-low     (count-sev :low)
         n-total   (count findings)
         has-clusters? (seq (:clusters clusters-data))
         summary   (str (if truncated-from
                          (str "top " n-total " of " truncated-from " findings")
                          (str n-total " finding" (when (not= 1 n-total) "s")))
                        " (" n-high " high, " n-medium " medium, " n-low " low)"
                        (when (pos? (or suppressed-count 0))
                          (str " — " suppressed-count
                               " noise suppressed (--show-noise to include)")))]
     (into
      [(str "gordian diagnose — "
            (if truncated-from
              (str "top " n-total " of " truncated-from " findings")
              (str n-total " finding" (when (not= 1 n-total) "s"))))
       (str "src: " (str/join " " src-dirs))
       (str "rank: " (name rank))
       ""
       "HEALTH"
       (str "  propagation cost: "
            (format "%.1f%%" (* 100.0 (:propagation-cost health)))
            " (" (name (:health health)) ")")
       (str "  cycles: " (if (zero? (:cycle-count health))
                           "none"
                           (:cycle-count health)))
       (str "  namespaces: " (:ns-count health))
       ""]
      (concat
       (if has-clusters?
         (concat
          (common/format-cluster-section (:clusters clusters-data))
          (when (seq (:unclustered clusters-data))
            (concat
             ["UNCLUSTERED" ""]
             (mapcat common/format-finding-lines (:unclustered clusters-data)))))
         (mapcat common/format-finding-lines findings))
       [summary])))))

(defn format-diagnose-md
  "Markdown rendering of findings.
  clusters-data    — optional {:clusters [...] :unclustered [...]}.
  rank             — :severity or :actionability.
  suppressed-count — optional count of noise findings suppressed from display.
  truncated-from   — optional total count before --top truncation."
  ([report health findings]
   (format-diagnose-md report health findings nil :severity nil nil))
  ([report health findings clusters-data]
   (format-diagnose-md report health findings clusters-data :severity nil nil))
  ([report health findings clusters-data rank]
   (format-diagnose-md report health findings clusters-data rank nil nil))
  ([report health findings clusters-data rank suppressed-count]
   (format-diagnose-md report health findings clusters-data rank suppressed-count nil))
  ([{:keys [src-dirs]} health findings clusters-data rank suppressed-count truncated-from]
   (let [count-sev (fn [s] (count (filter #(= s (:severity %)) findings)))
         n-high    (count-sev :high)
         n-medium  (count-sev :medium)
         n-low     (count-sev :low)
         n-total   (count findings)
         has-clusters? (seq (:clusters clusters-data))]
     (into
      [(str "# Gordian Diagnose — "
            (if truncated-from
              (str "Top " n-total " of " truncated-from " Findings")
              (str n-total " Finding" (when (not= 1 n-total) "s"))))
       ""
       (str "**Source:** `" (str/join " " src-dirs) "`")
       ""
       (str "**Rank:** `" (name rank) "`")
       ""
       "## Health"
       ""
       "| Metric | Value |"
       "|--------|-------|"
       (str "| Propagation cost | " (common/md-pct (:propagation-cost health))
            " (" (name (:health health)) ") |")
       (str "| Cycles | " (if (zero? (:cycle-count health))
                            "none"
                            (:cycle-count health)) " |")
       (str "| Namespaces | " (:ns-count health) " |")]
      (concat
       (if has-clusters?
         (concat
          (common/md-format-cluster-section (:clusters clusters-data))
          (when (seq (:unclustered clusters-data))
            (concat
             ["## Unclustered" ""]
             (mapcat common/md-format-finding (:unclustered clusters-data)))))
         (let [grouped  (group-by :severity findings)]
           (mapcat identity
                   (keep (fn [sev]
                           (when-let [fs (seq (get grouped sev))]
                             (into [(common/md-severity-heading sev) ""]
                                   (mapcat common/md-format-finding fs))))
                         [:high :medium :low]))))
       ["---"
        ""
        (str "**"
             (if truncated-from
               (str "top " n-total " of " truncated-from " findings")
               (str n-total " finding" (when (not= 1 n-total) "s")))
             "** (" n-high " high, " n-medium " medium, " n-low " low)"
             (when (pos? (or suppressed-count 0))
               (str " — " suppressed-count
                    " noise suppressed (--show-noise to include)")))])))))

(defn print-diagnose
  "Print a human-readable diagnose report to stdout."
  ([report health findings]
   (run! println (format-diagnose report health findings)))
  ([report health findings clusters]
   (run! println (format-diagnose report health findings clusters)))
  ([report health findings clusters rank]
   (run! println (format-diagnose report health findings clusters rank)))
  ([report health findings clusters rank suppressed-count]
   (run! println (format-diagnose report health findings clusters rank suppressed-count)))
  ([report health findings clusters rank suppressed-count truncated-from]
   (run! println (format-diagnose report health findings clusters rank suppressed-count truncated-from))))
