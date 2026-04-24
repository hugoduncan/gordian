(ns gordian.output.complexity
  (:require [clojure.string :as str]
            [gordian.output.common :as common]
            [gordian.output.enforcement :as enforcement]))

(defn- complexity-unit-label
  [{:keys [ns var arity]}]
  (str ns "/" var " [arity " arity "]"))

(defn- complexity-max-unit-label
  [max-unit]
  (str (:ns max-unit) "/" (:var max-unit)
       " [arity " (:arity max-unit) "]"
       " (cc=" (:cc max-unit) ", loc=" (:loc max-unit) ")"))

(defn- complexity-bar-value
  [bar-metric x]
  (case bar-metric
    :loc (or (:loc x) (:max-loc x) 0)
    (or (:cc x) (:max-cc x) 0)))

(defn- complexity-unit-header
  [unit-label-width bar-col-gap]
  [(str "  " (common/pad-right unit-label-width "unit")
        "  " (common/pad-left 4 "cc")
        "  " (common/pad-right 10 "risk")
        "  " (common/pad-left 4 "loc")
        bar-col-gap "bar")
   (str "  " (common/rule unit-label-width)
        "  " (common/rule 4)
        "  " (common/rule 10)
        "  " (common/rule 4)
        bar-col-gap (common/rule 3))])

(defn- complexity-unit-row
  [bar-metric unit-label-width bar-col-gap {:keys [cc cc-risk loc] :as unit}]
  (str "  " (common/pad-right unit-label-width (complexity-unit-label unit))
       "  " (common/pad-left 4 cc)
       "  " (common/pad-right 10 (name (:level cc-risk)))
       "  " (common/pad-left 4 loc)
       bar-col-gap (common/bar (complexity-bar-value bar-metric unit))))

(defn- complexity-rollup-header
  [ns-label-width bar-col-gap]
  [(str "  " (common/pad-right ns-label-width "namespace")
        "  " (common/pad-left 5 "units")
        "  " (common/pad-left 8 "total-cc")
        "  " (common/pad-left 6 "avg-cc")
        "  " (common/pad-left 6 "max-cc")
        "  " (common/pad-left 9 "total-loc")
        "  " (common/pad-left 7 "avg-loc")
        "  " (common/pad-left 7 "max-loc")
        bar-col-gap "bar")
   (str "  " (common/rule ns-label-width)
        "  " (common/rule 5)
        "  " (common/rule 8)
        "  " (common/rule 6)
        "  " (common/rule 6)
        "  " (common/rule 9)
        "  " (common/rule 7)
        "  " (common/rule 7)
        bar-col-gap (common/rule 3))])

(defn- complexity-rollup-row
  [bar-metric ns-label-width bar-col-gap {:keys [ns unit-count total-cc avg-cc max-cc total-loc avg-loc max-loc] :as rollup}]
  (str "  " (common/pad-right ns-label-width (str ns))
       "  " (common/pad-left 5 unit-count)
       "  " (common/pad-left 8 total-cc)
       "  " (common/pad-left 6 (format "%.2f" (double avg-cc)))
       "  " (common/pad-left 6 max-cc)
       "  " (common/pad-left 9 total-loc)
       "  " (common/pad-left 7 (format "%.2f" (double avg-loc)))
       "  " (common/pad-left 7 max-loc)
       bar-col-gap (common/bar (complexity-bar-value bar-metric rollup))))

(defn format-complexity
  "Format complexity report as human-readable lines."
  [{:keys [src-dirs units namespace-rollups project-rollup max-unit options metrics bar-metric enforcement]}]
  (let [unit-label-width (max 10 (apply max (concat [10] (map (comp count complexity-unit-label) units))))
        ns-label-width   (max 10 (apply max (concat [10] (map #(count (str (:ns %))) namespace-rollups))))
        bar-col-gap      "  "
        {:keys [namespace-count unit-count]} (common/unit-summary-counts project-rollup units)]
    (into
     ["gordian complexity"
      (str "src: " (str/join " " src-dirs))
      ""
      "SUMMARY"
      (str "  metrics: " (str/join ", " (map name metrics)))
      (str "  namespaces: " namespace-count)
      (str "  units: " unit-count)
      (str "  mins: " (if (seq (:mins options)) (pr-str (:mins options)) "{}"))
      (str "  namespace rollup: " (if (:namespace-rollup options) "on" "off"))
      (str "  project rollup: " (if (:project-rollup options) "on" "off"))
      (str "  bar metric: " (name bar-metric))
      (str "  max unit: " (if max-unit
                            (complexity-max-unit-label max-unit)
                            "(none)"))
      ""
      "UNITS"
      (first (complexity-unit-header unit-label-width bar-col-gap))
      (second (complexity-unit-header unit-label-width bar-col-gap))]
     (concat
      (if (seq units)
        (map (partial complexity-unit-row bar-metric unit-label-width bar-col-gap)
             units)
        ["  (none)"])
      (when namespace-rollups
        (concat
         [""
          "NAMESPACE ROLLUP"
          (first (complexity-rollup-header ns-label-width bar-col-gap))
          (second (complexity-rollup-header ns-label-width bar-col-gap))]
         (if (seq namespace-rollups)
           (map (partial complexity-rollup-row bar-metric ns-label-width bar-col-gap)
                namespace-rollups)
           ["  (none)"])))
      (when project-rollup
        [""
         "PROJECT ROLLUP"
         (str "  units=" (:unit-count project-rollup)
              " namespaces=" (:namespace-count project-rollup)
              " total-cc=" (:total-cc project-rollup)
              " avg-cc=" (format "%.2f" (double (:avg-cc project-rollup)))
              " max-cc=" (:max-cc project-rollup)
              " total-loc=" (:total-loc project-rollup)
              " avg-loc=" (format "%.2f" (double (:avg-loc project-rollup)))
              " max-loc=" (:max-loc project-rollup))
         (str "  simple=" (get-in project-rollup [:cc-risk-counts :simple])
              " moderate=" (get-in project-rollup [:cc-risk-counts :moderate])
              " high=" (get-in project-rollup [:cc-risk-counts :high])
              " untestable=" (get-in project-rollup [:cc-risk-counts :untestable]))])
      (when enforcement
        (enforcement/format-enforcement-text enforcement))))))

(defn format-complexity-md
  "Format complexity report as markdown lines."
  [{:keys [src-dirs units namespace-rollups project-rollup max-unit options metrics bar-metric enforcement]}]
  (let [{:keys [namespace-count unit-count]} (common/unit-summary-counts project-rollup units)]
    (into
     ["# gordian complexity"
      ""
      (str "**Source:** `" (str/join " " src-dirs) "`")
      ""
      "## Summary"
      ""
      "| Metric | Value |"
      "|--------|-------|"
      (str "| Metrics | `" (pr-str metrics) "` |")
      (str "| Namespaces | " namespace-count " |")
      (str "| Units | " unit-count " |")
      (str "| Mins | `" (pr-str (:mins options)) "` |")
      (str "| Namespace rollup | `" (if (:namespace-rollup options) "on" "off") "` |")
      (str "| Project rollup | `" (if (:project-rollup options) "on" "off") "` |")
      (str "| Bar metric | `" (name bar-metric) "` |")
      (str "| Max unit | " (if max-unit
                             (str "`" (:ns max-unit) "/" (:var max-unit) " [arity " (:arity max-unit) "]` (cc=" (:cc max-unit) ", loc=" (:loc max-unit) ")")
                             "(none)") " |")
      ""
      "## Units"
      ""
      "| Unit | CC | Risk | LOC |"
      "|------|----|------|-----|"]
     (concat
      (if (seq units)
        (map (fn [{:keys [ns var arity cc cc-risk loc]}]
               (str "| `" ns "/" var " [arity " arity "]` | "
                    cc " | " (name (:level cc-risk)) " | " loc " |"))
             units)
        ["| (none) | 0 | n/a | 0 |"])
      (when namespace-rollups
        (concat
         [""
          "## Namespace rollup"
          ""
          "| Namespace | Units | Total CC | Avg CC | Max CC | Total LOC | Avg LOC | Max LOC |"
          "|-----------|-------|----------|--------|--------|-----------|---------|---------|"]
         (if (seq namespace-rollups)
           (map (fn [{:keys [ns unit-count total-cc avg-cc max-cc total-loc avg-loc max-loc]}]
                  (str "| `" ns "` | " unit-count
                       " | " total-cc
                       " | " (format "%.2f" (double avg-cc))
                       " | " max-cc
                       " | " total-loc
                       " | " (format "%.2f" (double avg-loc))
                       " | " max-loc " |"))
                namespace-rollups)
           ["| (none) | 0 | 0 | 0.00 | 0 | 0 | 0.00 | 0 |"])))
      (when project-rollup
        [""
         "## Project rollup"
         ""
         (str "- **Units:** " (:unit-count project-rollup))
         (str "- **Namespaces:** " (:namespace-count project-rollup))
         (str "- **Total CC:** " (:total-cc project-rollup))
         (str "- **Avg CC:** " (format "%.2f" (double (:avg-cc project-rollup))))
         (str "- **Max CC:** " (:max-cc project-rollup))
         (str "- **Total LOC:** " (:total-loc project-rollup))
         (str "- **Avg LOC:** " (format "%.2f" (double (:avg-loc project-rollup))))
         (str "- **Max LOC:** " (:max-loc project-rollup))
         (str "- **Risk counts:** `" (pr-str (:cc-risk-counts project-rollup)) "`")])
      (when enforcement
        (enforcement/format-enforcement-md enforcement))))))

(defn print-complexity
  "Print a human-readable complexity report to stdout."
  [result]
  (run! println (format-complexity result)))
