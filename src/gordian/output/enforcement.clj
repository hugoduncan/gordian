(ns gordian.output.enforcement
  (:require [clojure.string :as str]))

(defn unit-label [{:keys [ns var arity dispatch kind]}]
  (case kind
    :defmethod (str ns "/" var " [dispatch " dispatch "]")
    (str ns "/" var " [arity " arity "]")))

(defn threshold-display [{:keys [metric-token threshold]}]
  (str (name metric-token) "<=" threshold))

(defn- check-line [{:keys [metric-token threshold passed? violation-count max-observed]}]
  (str "  - " (threshold-display {:metric-token metric-token :threshold threshold})
       " → " (if passed? "PASS" "FAIL")
       "; violations=" violation-count
       "; max-observed="
       (if (integer? threshold)
         (format "%.0f" (double max-observed))
         (format "%.1f" (double max-observed)))))

(defn format-enforcement-text [{:keys [passed? unit-count checks violations]}]
  (into
   [""
    "ENFORCEMENT"
    (str "  result: " (if passed? "PASS" "FAIL"))
    (str "  subject: units")
    (str "  analyzed units: " unit-count)]
   (concat
    (map check-line checks)
    (when (seq violations)
      (concat
       [""
        "FAILURES"]
       (map (fn [{:keys [metric-token threshold actual] :as v}]
              (str "  - " (threshold-display {:metric-token metric-token :threshold threshold})
                   ": actual="
                   (if (integer? threshold)
                     (format "%.0f" (double actual))
                     (format "%.1f" (double actual)))
                   " "
                   (unit-label v)))
            violations))))))

(defn format-enforcement-md [{:keys [passed? unit-count checks violations]}]
  (into
   [""
    "## Enforcement"
    ""
    "| Metric | Value |"
    "|--------|-------|"
    (str "| Result | `" (if passed? "PASS" "FAIL") "` |")
    "| Subject | `units` |"
    (str "| Analyzed units | " unit-count " |")
    ""
    "| Threshold | Result | Violations | Max observed |"
    "|-----------|--------|------------|--------------|"]
   (concat
    (map (fn [{:keys [passed? violation-count max-observed threshold] :as check}]
           (str "| `" (threshold-display check) "` | `" (if passed? "PASS" "FAIL")
                "` | " violation-count
                " | " (if (integer? threshold)
                         (format "%.0f" (double max-observed))
                         (format "%.1f" (double max-observed)))
                " |"))
         checks)
    (when (seq violations)
      (concat
       [""
        "### Failures"
        ""
        "| Threshold | Actual | Unit |"
        "|-----------|--------|------|"]
       (map (fn [{:keys [threshold actual] :as v}]
              (str "| `" (threshold-display v) "` | "
                   (if (integer? threshold)
                     (format "%.0f" (double actual))
                     (format "%.1f" (double actual)))
                   " | `" (unit-label v) "` |"))
            violations))))))
