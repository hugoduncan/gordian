(ns gordian.output.local
  (:require [clojure.string :as str]
            [gordian.output.common :as common]))

(defn- unit-label [{:keys [ns var kind arity dispatch]}]
  (case kind
    :defmethod (str ns "/" var " [dispatch " dispatch "]")
    (str ns "/" var " [arity " arity "]")))

(defn- max-unit-label [max-unit]
  (str (unit-label max-unit) " (total=" (format "%.1f" (double (:lcc-total max-unit))) ")"))

(defn- bar-value [bar-metric x]
  (case bar-metric
    :flow (or (:flow-burden x) (:avg-flow x) 0)
    :state (or (:state-burden x) (:avg-state x) 0)
    :shape (or (:shape-burden x) (:avg-shape x) 0)
    :abstraction (or (:abstraction-burden x) (:avg-abstraction x) 0)
    :dependency (or (:dependency-burden x) (:avg-dependency x) 0)
    :working-set (or (get-in x [:working-set :burden]) (:avg-working-set x) 0)
    (or (:lcc-total x) (:max-lcc x) 0)))

(defn- findings-label [findings]
  (when (seq findings)
    (str/join ", " (map (comp name :kind) findings))))

(defn- unit-header [label-width gap]
  [(str "  " (common/pad-right label-width "unit")
        "  " (common/pad-left 6 "total")
        "  " (common/pad-left 4 "flow")
        "  " (common/pad-left 5 "state")
        "  " (common/pad-left 5 "shape")
        "  " (common/pad-left 6 "abst")
        "  " (common/pad-left 4 "dep")
        "  " (common/pad-left 4 "ws")
        gap "bar")
   (str "  " (common/rule label-width)
        "  " (common/rule 6)
        "  " (common/rule 4)
        "  " (common/rule 5)
        "  " (common/rule 5)
        "  " (common/rule 6)
        "  " (common/rule 4)
        "  " (common/rule 4)
        gap (common/rule 3))])

(defn- unit-row [bar-metric label-width gap unit]
  [(str "  " (common/pad-right label-width (unit-label unit))
        "  " (common/pad-left 6 (format "%.1f" (double (:lcc-total unit))))
        "  " (common/pad-left 4 (format "%.1f" (double (:flow-burden unit))))
        "  " (common/pad-left 5 (format "%.1f" (double (:state-burden unit))))
        "  " (common/pad-left 5 (format "%.1f" (double (:shape-burden unit))))
        "  " (common/pad-left 6 (format "%.1f" (double (:abstraction-burden unit))))
        "  " (common/pad-left 4 (format "%.1f" (double (:dependency-burden unit))))
        "  " (common/pad-left 4 (format "%.1f" (double (get-in unit [:working-set :burden]))))
        gap (common/bar (bar-value bar-metric unit)))
   (when-let [label (findings-label (:findings unit))]
     (str "    findings: " label))])

(defn- rollup-header [label-width gap]
  [(str "  " (common/pad-right label-width "namespace")
        "  " (common/pad-left 5 "units")
        "  " (common/pad-left 7 "total")
        "  " (common/pad-left 6 "avg")
        "  " (common/pad-left 6 "max")
        "  " (common/pad-left 6 "flow")
        "  " (common/pad-left 6 "state")
        "  " (common/pad-left 6 "shape")
        "  " (common/pad-left 6 "abst")
        "  " (common/pad-left 5 "dep")
        "  " (common/pad-left 4 "ws")
        gap "bar")
   (str "  " (common/rule label-width)
        "  " (common/rule 5)
        "  " (common/rule 7)
        "  " (common/rule 6)
        "  " (common/rule 6)
        "  " (common/rule 6)
        "  " (common/rule 6)
        "  " (common/rule 6)
        "  " (common/rule 6)
        "  " (common/rule 5)
        "  " (common/rule 4)
        gap (common/rule 3))])

(defn- rollup-row [bar-metric label-width gap rollup]
  (str "  " (common/pad-right label-width (str (:ns rollup)))
       "  " (common/pad-left 5 (:unit-count rollup))
       "  " (common/pad-left 7 (format "%.1f" (double (:total-lcc rollup))))
       "  " (common/pad-left 6 (format "%.1f" (double (:avg-lcc rollup))))
       "  " (common/pad-left 6 (format "%.1f" (double (:max-lcc rollup))))
       "  " (common/pad-left 6 (format "%.1f" (double (:avg-flow rollup))))
       "  " (common/pad-left 6 (format "%.1f" (double (:avg-state rollup))))
       "  " (common/pad-left 6 (format "%.1f" (double (:avg-shape rollup))))
       "  " (common/pad-left 6 (format "%.1f" (double (:avg-abstraction rollup))))
       "  " (common/pad-left 5 (format "%.1f" (double (:avg-dependency rollup))))
       "  " (common/pad-left 4 (format "%.1f" (double (:avg-working-set rollup))))
       gap (common/bar (bar-value bar-metric rollup))))

(defn format-local
  [{:keys [src-dirs units namespace-rollups project-rollup max-unit options bar-metric]}]
  (let [label-width (max 10 (apply max (concat [10] (map (comp count unit-label) units))))
        ns-width    (max 10 (apply max (concat [10] (map #(count (str (:ns %))) namespace-rollups))))
        gap         "  "]
    (into
     ["gordian local"
      (str "src: " (str/join " " src-dirs))
      ""
      "SUMMARY"
      (str "  namespaces: " (:namespace-count project-rollup))
      (str "  units: " (:unit-count project-rollup))
      (str "  total lcc: " (format "%.1f" (double (:total-lcc project-rollup))))
      (str "  avg lcc: " (format "%.2f" (double (:avg-lcc project-rollup))))
      (str "  max lcc: " (format "%.1f" (double (:max-lcc project-rollup))))
      (str "  avg flow: " (format "%.2f" (double (:avg-flow project-rollup))))
      (str "  avg state: " (format "%.2f" (double (:avg-state project-rollup))))
      (str "  avg shape: " (format "%.2f" (double (:avg-shape project-rollup))))
      (str "  avg abstraction: " (format "%.2f" (double (:avg-abstraction project-rollup))))
      (str "  avg dependency: " (format "%.2f" (double (:avg-dependency project-rollup))))
      (str "  avg ws: " (format "%.2f" (double (:avg-working-set project-rollup))))
      (str "  mins: " (if (seq (:mins options)) (pr-str (:mins options)) "{}"))
      (str "  bar metric: " (name bar-metric))
      (str "  max unit: " (if max-unit (max-unit-label max-unit) "(none)"))
      ""
      "UNITS"
      (first (unit-header label-width gap))
      (second (unit-header label-width gap))]
     (concat
      (if (seq units)
        (mapcat (fn [unit]
                  (remove nil? (unit-row bar-metric label-width gap unit)))
                units)
        ["  (none)"])
      [""
       "NAMESPACE ROLLUP"
       (first (rollup-header ns-width gap))
       (second (rollup-header ns-width gap))]
      (if (seq namespace-rollups)
        (map (partial rollup-row bar-metric ns-width gap) namespace-rollups)
        ["  (none)"])
      [""
       "PROJECT ROLLUP"
       (str "  units=" (:unit-count project-rollup)
            " namespaces=" (:namespace-count project-rollup)
            " total=" (format "%.1f" (double (:total-lcc project-rollup)))
            " avg=" (format "%.2f" (double (:avg-lcc project-rollup)))
            " max=" (format "%.1f" (double (:max-lcc project-rollup))))
       (str "  avg-flow=" (format "%.2f" (double (:avg-flow project-rollup)))
            " avg-state=" (format "%.2f" (double (:avg-state project-rollup)))
            " avg-shape=" (format "%.2f" (double (:avg-shape project-rollup)))
            " avg-abstraction=" (format "%.2f" (double (:avg-abstraction project-rollup)))
            " avg-dependency=" (format "%.2f" (double (:avg-dependency project-rollup)))
            " avg-ws=" (format "%.2f" (double (:avg-working-set project-rollup))))]))))

(defn format-local-md
  [{:keys [src-dirs units namespace-rollups project-rollup max-unit options bar-metric]}]
  (let [unit-lines
        (if (seq units)
          (map (fn [unit]
                 (str "| `" (unit-label unit) "` | "
                      (format "%.1f" (double (:lcc-total unit))) " | "
                      (format "%.1f" (double (:flow-burden unit))) " | "
                      (format "%.1f" (double (:state-burden unit))) " | "
                      (format "%.1f" (double (:shape-burden unit))) " | "
                      (format "%.1f" (double (:abstraction-burden unit))) " | "
                      (format "%.1f" (double (:dependency-burden unit))) " | "
                      (format "%.1f" (double (get-in unit [:working-set :burden]))) " | "
                      (or (findings-label (:findings unit)) "") " |"))
               units)
          ["| (none) | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 |  |"])
        rollup-lines
        (if (seq namespace-rollups)
          (map (fn [rollup]
                 (str "| `" (:ns rollup) "` | " (:unit-count rollup)
                      " | " (format "%.1f" (double (:total-lcc rollup)))
                      " | " (format "%.1f" (double (:avg-lcc rollup)))
                      " | " (format "%.1f" (double (:max-lcc rollup)))
                      " | " (format "%.1f" (double (:avg-flow rollup)))
                      " | " (format "%.1f" (double (:avg-state rollup)))
                      " | " (format "%.1f" (double (:avg-shape rollup)))
                      " | " (format "%.1f" (double (:avg-abstraction rollup)))
                      " | " (format "%.1f" (double (:avg-dependency rollup)))
                      " | " (format "%.1f" (double (:avg-working-set rollup))) " |"))
               namespace-rollups)
          ["| (none) | 0 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 |"])]
    (into
     ["# gordian local"
      ""
      (str "**Source:** `" (str/join " " src-dirs) "`")
      ""
      "## Summary"
      ""
      "| Metric | Value |"
      "|--------|-------|"
      (str "| Namespaces | " (:namespace-count project-rollup) " |")
      (str "| Units | " (:unit-count project-rollup) " |")
      (str "| Total LCC | " (format "%.1f" (double (:total-lcc project-rollup))) " |")
      (str "| Avg LCC | " (format "%.2f" (double (:avg-lcc project-rollup))) " |")
      (str "| Max LCC | " (format "%.1f" (double (:max-lcc project-rollup))) " |")
      (str "| Avg flow | " (format "%.2f" (double (:avg-flow project-rollup))) " |")
      (str "| Avg state | " (format "%.2f" (double (:avg-state project-rollup))) " |")
      (str "| Avg shape | " (format "%.2f" (double (:avg-shape project-rollup))) " |")
      (str "| Avg abstraction | " (format "%.2f" (double (:avg-abstraction project-rollup))) " |")
      (str "| Avg dependency | " (format "%.2f" (double (:avg-dependency project-rollup))) " |")
      (str "| Avg WS | " (format "%.2f" (double (:avg-working-set project-rollup))) " |")
      (str "| Mins | `" (pr-str (:mins options)) "` |")
      (str "| Bar metric | `" (name bar-metric) "` |")
      (str "| Max unit | "
           (if max-unit
             (str "`" (unit-label max-unit) "` (total=" (format "%.1f" (double (:lcc-total max-unit))) ")")
             "(none)")
           " |")
      ""
      "## Units"
      ""
      "| Unit | Total | Flow | State | Shape | Abstraction | Dependency | WS | Findings |"
      "|------|-------|------|-------|-------|-------------|------------|----|----------|"]
     (concat
      unit-lines
      [""
       "## Namespace rollup"
       ""
       "| Namespace | Units | Total LCC | Avg LCC | Max LCC | Avg flow | Avg state | Avg shape | Avg abstraction | Avg dependency | Avg WS |"
       "|-----------|-------|-----------|---------|---------|----------|-----------|-----------|-----------------|----------------|--------|"]
      rollup-lines
      [""
       "## Project rollup"
       ""
       (str "- **Units:** " (:unit-count project-rollup))
       (str "- **Namespaces:** " (:namespace-count project-rollup))
       (str "- **Total LCC:** " (format "%.1f" (double (:total-lcc project-rollup))))
       (str "- **Avg LCC:** " (format "%.2f" (double (:avg-lcc project-rollup))))
       (str "- **Max LCC:** " (format "%.1f" (double (:max-lcc project-rollup))))
       (str "- **Finding counts:** `" (pr-str (:finding-counts project-rollup)) "`")]))))

(defn print-local [result]
  (run! println (format-local result)))
