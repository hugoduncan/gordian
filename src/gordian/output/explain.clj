(ns gordian.output.explain
  (:require [clojure.string :as str]
            [gordian.output.common :as common]))

(defn- format-pair-line
  "Format one coupling pair as a line for explain-ns output.
  context-ns — the namespace being explained; we show the other side."
  [ns-col context-ns pair]
  (let [other (if (= context-ns (:ns-a pair)) (:ns-b pair) (:ns-a pair))]
    (str "  " (common/pad-right ns-col (str other))
         "  score=" (format "%.2f" (:score pair))
         "  " (if (:structural-edge? pair) "structural" "hidden")
         (when (seq (:shared-terms pair))
           (str "  shared: " (str/join ", " (:shared-terms pair))))
         (when (:co-changes pair)
           (str "  " (:co-changes pair) " co-changes")))))

(defn format-explain-ns
  "Format explain-ns data as human-readable lines."
  [{:keys [ns metrics direct-deps direct-dependents
           conceptual-pairs change-pairs cycles error available]}]
  (if error
    [(str "Error: " error)
     (str "Available namespaces: " (str/join ", " (map str available)))]
    (let [ns-col (max 20 (apply max 0
                                (map #(count (str (or (:ns-a %) (:ns-b %))))
                                     (concat conceptual-pairs change-pairs))))]
      (into
       [(str "gordian explain — " ns)
        ""
        (str "  role: " (if (:role metrics) (name (:role metrics)) "unknown")
             "    Ca=" (or (:ca metrics) "-")
             "  Ce=" (or (:ce metrics) "-")
             "  I=" (if (:instability metrics)
                      (format "%.2f" (:instability metrics))
                      "-"))
        (str "  reach: " (if (:reach metrics)
                           (format "%.1f%%" (* 100.0 (:reach metrics)))
                           "-")
             "   fan-in: " (if (:fan-in metrics)
                             (format "%.1f%%" (* 100.0 (:fan-in metrics)))
                             "-"))
        (str "  family: " (or (:family metrics) "-")
             "    Ca-fam=" (or (:ca-family metrics) "-")
             "  Ca-ext=" (or (:ca-external metrics) "-")
             "  Ce-fam=" (or (:ce-family metrics) "-")
             "  Ce-ext=" (or (:ce-external metrics) "-"))
        ""
        (str "DIRECT DEPENDENCIES (" (count (:project direct-deps))
             " project, " (count (:external direct-deps)) " external)")
        (str "  project: " (if (seq (:project direct-deps))
                             (str/join ", " (map str (:project direct-deps)))
                             "(none)"))
        (str "  external: " (if (seq (:external direct-deps))
                              (str/join ", " (map str (:external direct-deps)))
                              "(none)"))
        ""
        (str "DIRECT DEPENDENTS (" (count direct-dependents) ")")]
       (concat
        (if (seq direct-dependents)
          (mapv #(str "  " %) direct-dependents)
          ["  (none)"])
        [""
         (str "CONCEPTUAL COUPLING (" (count conceptual-pairs) " pairs)")]
        (if (seq conceptual-pairs)
          (mapv #(format-pair-line ns-col ns %) conceptual-pairs)
          ["  (none)"])
        [""
         (str "CHANGE COUPLING (" (count change-pairs) " pairs)")]
        (if (seq change-pairs)
          (mapv #(format-pair-line ns-col ns %) change-pairs)
          ["  (none)"])
        [""
         (str "CYCLES: " (if (seq cycles)
                           (str (count cycles) " — "
                                (str/join "; " (map #(str/join ", " (sort (map str %)))
                                                    cycles)))
                           "none"))])))))

(def ^:private verdict-emoji
  {:expected-structural       ""
   :family-naming-noise       ""
   :family-siblings           "🟡"
   :likely-missing-abstraction "🔴"
   :hidden-conceptual          "🟡"
   :hidden-change              "🟡"
   :transitive-only            "🟢"
   :unrelated                  ""})

(defn format-explain-pair
  "Format explain-pair data as human-readable lines."
  [{:keys [ns-a ns-b structural conceptual change finding verdict error available]}]
  (if error
    [(str "Error: " error)
     (str "Available namespaces: " (str/join ", " (map str available)))]
    (into
     [(str "gordian explain-pair — " ns-a " ↔ " ns-b)
      ""
      "STRUCTURAL"
      (str "  direct edge: "
           (if (:direct-edge? structural)
             (str "yes (" (name (:direction structural)) ")")
             "no"))
      (str "  shortest path: "
           (if-let [p (:shortest-path structural)]
             (str/join " → " (map str p))
             "(none)"))]
     (concat
      [""
       "CONCEPTUAL"]
      (if conceptual
        [(str "  score: " (format "%.2f" (:score conceptual)))
         (str "  shared terms: " (str/join ", " (:shared-terms conceptual)))
         (str "  hidden: " (if (:structural-edge? conceptual) "no" "yes"))]
        ["  (no data)"])
      [""
       "CHANGE COUPLING"]
      (if change
        [(str "  score: " (format "%.2f" (:score change)))
         (str "  co-changes: " (:co-changes change))
         (str "  confidence: " (format "%.0f%%/%.0f%%"
                                       (* 100.0 (:confidence-a change))
                                       (* 100.0 (:confidence-b change))))]
        ["  (no data)"])
      (when finding
        [""
         "DIAGNOSIS"
         (str "  " (case (:severity finding)
                     :high   "● HIGH"
                     :medium "● MEDIUM"
                     :low    "● LOW")
              " — " (:reason finding))])
      (when verdict
        [""
         "VERDICT"
         (str "  " (name (:category verdict)))
         (str "  " (:explanation verdict))])))))

(defn format-explain-ns-md
  "Markdown rendering of explain-ns data."
  [{:keys [ns metrics direct-deps direct-dependents
           conceptual-pairs change-pairs cycles error available]}]
  (if error
    [(str "**Error:** " error)
     ""
     (str "Available namespaces: " (str/join ", " (map #(str "`" % "`") available)))]
    (into
     [(str "# Gordian Explain — " ns)
      ""
      "| Metric | Value |"
      "|--------|-------|"
      (str "| Role | " (if (:role metrics) (name (:role metrics)) "-") " |")
      (str "| Ca | " (or (:ca metrics) "-") " |")
      (str "| Ce | " (or (:ce metrics) "-") " |")
      (str "| I | " (if (:instability metrics)
                      (format "%.2f" (:instability metrics)) "-") " |")
      (str "| Reach | " (if (:reach metrics)
                          (common/md-pct (:reach metrics)) "-") " |")
      (str "| Fan-in | " (if (:fan-in metrics)
                           (common/md-pct (:fan-in metrics)) "-") " |")
      (str "| Family | `" (or (:family metrics) "-") "` |")
      (str "| Ca-family | " (or (:ca-family metrics) "-") " |")
      (str "| Ca-external | " (or (:ca-external metrics) "-") " |")
      (str "| Ce-family | " (or (:ce-family metrics) "-") " |")
      (str "| Ce-external | " (or (:ce-external metrics) "-") " |")]
     (concat
      ["" "## Direct Dependencies" ""
       (str "- **Project:** "
            (if (seq (:project direct-deps))
              (str/join ", " (map #(str "`" % "`") (:project direct-deps)))
              "(none)"))
       (str "- **External:** "
            (if (seq (:external direct-deps))
              (str/join ", " (map #(str "`" % "`") (:external direct-deps)))
              "(none)"))]
      ["" "## Direct Dependents" ""]
      (if (seq direct-dependents)
        (mapv #(str "- `" % "`") direct-dependents)
        ["(none)"])
      ["" (str "## Conceptual Coupling (" (count conceptual-pairs) " pairs)") ""]
      (if (seq conceptual-pairs)
        (into ["| Namespace | Score | Edge | Shared Terms |"
               "|-----------|-------|------|--------------|"]
              (map (fn [p]
                     (let [other (if (= ns (:ns-a p)) (:ns-b p) (:ns-a p))]
                       (str "| " other
                            " | " (format "%.2f" (:score p))
                            " | " (if (:structural-edge? p) "structural" "hidden")
                            " | " (str/join ", " (or (:shared-terms p) []))
                            " |")))
                   conceptual-pairs))
        ["(none)"])
      ["" (str "## Change Coupling (" (count change-pairs) " pairs)") ""]
      (if (seq change-pairs)
        (into ["| Namespace | Score | Co | Conf A | Conf B |"
               "|-----------|-------|----|--------|--------|"]
              (map (fn [p]
                     (let [other (if (= ns (:ns-a p)) (:ns-b p) (:ns-a p))]
                       (str "| " other
                            " | " (format "%.2f" (:score p))
                            " | " (:co-changes p)
                            " | " (common/md-pct (:confidence-a p))
                            " | " (common/md-pct (:confidence-b p))
                            " |")))
                   change-pairs))
        ["(none)"])
      ["" "## Cycles" ""
       (if (seq cycles)
         (str/join "; " (map #(str/join ", " (sort (map str %))) cycles))
         "none")]))))

(defn format-explain-pair-md
  "Markdown rendering of explain-pair data."
  [{:keys [ns-a ns-b structural conceptual change finding verdict error available]}]
  (if error
    [(str "**Error:** " error)
     ""
     (str "Available namespaces: " (str/join ", " (map #(str "`" % "`") available)))]
    (into
     [(str "# Gordian Explain-Pair — " ns-a " ↔ " ns-b)
      ""
      "## Structural"
      ""
      "| Property | Value |"
      "|----------|-------|"
      (str "| Direct edge | "
           (if (:direct-edge? structural)
             (str "yes (" (name (:direction structural)) ")")
             "no") " |")
      (str "| Shortest path | "
           (if-let [p (:shortest-path structural)]
             (str/join " → " (map str p))
             "(none)") " |")]
     (concat
      ["" "## Conceptual" ""]
      (if conceptual
        ["| Property | Value |"
         "|----------|-------|"
         (str "| Score | " (format "%.2f" (:score conceptual)) " |")
         (str "| Shared terms | " (str/join ", " (:shared-terms conceptual)) " |")
         (str "| Hidden | " (if (:structural-edge? conceptual) "no" "yes") " |")]
        ["(no data)"])
      ["" "## Change Coupling" ""]
      (if change
        ["| Property | Value |"
         "|----------|-------|"
         (str "| Score | " (format "%.2f" (:score change)) " |")
         (str "| Co-changes | " (:co-changes change) " |")
         (str "| Confidence | " (format "%.0f%%/%.0f%%"
                                        (* 100.0 (:confidence-a change))
                                        (* 100.0 (:confidence-b change))) " |")]
        ["(no data)"])
      (when finding
        ["" "## Diagnosis" ""
         (str (case (:severity finding)
                :high   "🔴"
                :medium "🟡"
                :low    "🟢")
              " **" (str/upper-case (name (:severity finding)))
              "** — " (:reason finding))])
      (when verdict
        (let [emoji (get verdict-emoji (:category verdict) "")]
          ["" "## Verdict" ""
           (str (when (seq emoji) (str emoji " "))
                "**" (str/replace (name (:category verdict)) "-" " ")
                "** — " (:explanation verdict))]))))))

(defn print-explain-ns
  "Print a human-readable explain-ns report to stdout."
  [data]
  (run! println (format-explain-ns data)))

(defn print-explain-pair
  "Print a human-readable explain-pair report to stdout."
  [data]
  (run! println (format-explain-pair data)))
