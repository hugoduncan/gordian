(ns gordian.output
  (:require [clojure.string :as str]))

;;; ── formatting helpers ───────────────────────────────────────────────────

(defn- pct [x]      (format "%5.1f%%" (* 100.0 x)))
(defn- pad-right [n s] (format (str "%-" n "s") s))
(defn- pad-left  [n s] (format (str "%" n "s")  s))

(defn column-widths
  "Return {:ns-col int} sized to fit the longest namespace name."
  [nodes]
  {:ns-col (max 20 (apply max 0 (map #(count (str (:ns %))) nodes)))})

(defn format-row
  "Format one node row as a string."
  [ns-col {:keys [ns reach fan-in ca ce instability role]}]
  (str (pad-right ns-col (str ns))
       "  " (pct reach)
       "  " (pct fan-in)
       "  " (pad-left 3 (str (or ce "-")))
       "  " (pad-left 3 (str (or ca "-")))
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

;;; ── conceptual coupling section ──────────────────────────────────────────

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
          header (str (pad-right ns-col "namespace-a")
                      "  " (pad-right ns-col "namespace-b")
                      "  score  structural  shared concepts")
          rule   (apply str (repeat (count header) "─"))]
      (into
       [(str "conceptual coupling (score ≥ " (format "%.2f" (double threshold)) "):")
        ""
        header
        rule]
       (map (fn [{:keys [ns-a ns-b score structural-edge? shared-terms]}]
              (str (pad-right ns-col (str ns-a))
                   "  " (pad-right ns-col (str ns-b))
                   "  " (format "%4.2f" (double score))
                   "  " (if structural-edge?
                          (str "yes      " (str/join " " shared-terms))
                          (str "no  ←    " (str/join " " shared-terms)))))
            pairs)))))

;;; ── change coupling section ─────────────────────────────────────────────

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
          header (str (pad-right ns-col "namespace-a")
                      "  " (pad-right ns-col "namespace-b")
                      "  Jaccard  co  conf-a  conf-b  structural")
          rule   (apply str (repeat (count header) "─"))]
      (into
       [(str "change coupling (Jaccard ≥ " (format "%.2f" (double threshold)) "):")
        ""
        header
        rule]
       (map (fn [{:keys [ns-a ns-b score co-changes
                         confidence-a confidence-b structural-edge?]}]
              (str (pad-right ns-col (str ns-a))
                   "  " (pad-right ns-col (str ns-b))
                   "  " (format "%6.4f" (double score))
                   "  " (pad-left 2 (str co-changes))
                   "  " (format "%5.1f%%" (* 100.0 confidence-a))
                   "  " (format "%5.1f%%" (* 100.0 confidence-b))
                   "  " (if structural-edge?
                          "yes"
                          "no  ←")))
            pairs)))))

;;; ── full report ──────────────────────────────────────────────────────────

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
        header (str (pad-right ns-col "namespace")
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
      [(str "gordian — namespace coupling report")
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

;;; ── diagnose output ──────────────────────────────────────────────────────

(defn- format-finding-subject [{:keys [category subject]}]
  (case category
    :cycle          (str (str/join " → " (sort (map str (:members subject)))))
    (:cross-lens-hidden :hidden-conceptual :hidden-change)
    (str (:ns-a subject) " ↔ " (:ns-b subject))
    (str (:ns subject))))

(defn- severity-marker [severity]
  (case severity
    :high   "● HIGH"
    :medium "● MEDIUM"
    :low    "● LOW"))

(defn- format-evidence-lines [{:keys [category evidence]}]
  (case category
    :cross-lens-hidden
    [(str "  conceptual score=" (format "%.2f" (:conceptual-score evidence))
          " — shared terms: " (str/join ", " (:shared-terms evidence)))
     (str "  change     score=" (format "%.2f" (:change-score evidence))
          " — " (:co-changes evidence) " co-changes")
     "  → no structural edge"]

    :hidden-conceptual
    [(str "  shared terms: " (str/join ", " (:shared-terms evidence)))
     "  → no structural edge"]

    :hidden-change
    [(str "  " (:co-changes evidence) " co-changes"
          ", conf=" (format "%.0f%%/%.0f%%"
                            (* 100.0 (:confidence-a evidence))
                            (* 100.0 (:confidence-b evidence))))
     "  → no structural edge"]

    :cycle
    [(str "  members: " (str/join ", " (sort (map str (:members evidence)))))]

    :sdp-violation
    [(str "  Ca=" (:ca evidence) " Ce=" (:ce evidence)
          " I=" (format "%.2f" (:instability evidence)))]

    :god-module
    [(str "  reach=" (format "%.1f%%" (* 100.0 (:reach evidence)))
          " fan-in=" (format "%.1f%%" (* 100.0 (:fan-in evidence))))]

    :hub
    [(str "  Ce=" (:ce evidence)
          " I=" (format "%.2f" (or (:instability evidence) 0.0))
          " role=" (name (or (:role evidence) :unknown)))]

    []))

(defn format-diagnose
  "Format findings as human-readable lines.
  health — map from diagnose/health.
  findings — sorted vec of finding maps."
  [{:keys [src-dirs]} health findings]
  (let [count-sev (fn [s] (count (filter #(= s (:severity %)) findings)))
        n-high    (count-sev :high)
        n-medium  (count-sev :medium)
        n-low     (count-sev :low)
        n-total   (count findings)]
    (into
     [(str "gordian diagnose — " n-total " finding" (when (not= 1 n-total) "s"))
      (str "src: " (str/join " " src-dirs))
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
      (mapcat (fn [f]
                (concat
                 [(str (severity-marker (:severity f))
                       "  " (format-finding-subject f))
                  (str "  " (:reason f))]
                 (format-evidence-lines f)
                 [""]))
              findings)
      [(str n-total " finding" (when (not= 1 n-total) "s")
            " (" n-high " high, " n-medium " medium, " n-low " low)")]))))

;;; ── explain output ───────────────────────────────────────────────────────

(defn- format-pair-line
  "Format one coupling pair as a line for explain-ns output.
  context-ns — the namespace being explained; we show the other side."
  [ns-col context-ns pair]
  (let [other (if (= context-ns (:ns-a pair)) (:ns-b pair) (:ns-a pair))]
    (str "  " (pad-right ns-col (str other))
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

(defn format-explain-pair
  "Format explain-pair data as human-readable lines."
  [{:keys [ns-a ns-b structural conceptual change finding error available]}]
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
              " — " (:reason finding))])))))

;;; ── markdown output ──────────────────────────────────────────────────────

(defn- md-pct [x] (format "%.1f%%" (* 100.0 x)))

(defn format-report-md
  "Markdown rendering of the analyze report."
  [{:keys [src-dirs propagation-cost cycles nodes
           conceptual-pairs conceptual-threshold
           change-pairs change-threshold]}]
  (into
   [(str "# Gordian — Namespace Coupling Report")
    ""
    (str "**Source:** `" (str/join " " src-dirs) "`")
    ""
    "## Summary"
    ""
    "| Metric | Value |"
    "|--------|-------|"
    (str "| Propagation cost | " (md-pct propagation-cost) " |")
    (str "| Namespaces | " (count nodes) " |")
    (str "| Cycles | " (count cycles) " |")]
   (concat
    ;; cycles
    (when (seq cycles)
      (into ["" "## Cycles" ""]
            (map-indexed
             (fn [i members]
               (str (inc i) ". "
                    (str/join " → " (sort (map str members)))
                    " (" (count members) " namespaces)"))
             cycles)))
    ;; namespace table
    ["" "## Namespace Metrics" ""
     "| Namespace | Reach | Fan-in | Ce | Ca | I | Role |"
     "|-----------|-------|--------|----|----|---|------|"]
    (map (fn [{:keys [ns reach fan-in ca ce instability role]}]
           (str "| " ns
                " | " (md-pct reach)
                " | " (md-pct fan-in)
                " | " (or ce "-")
                " | " (or ca "-")
                " | " (if instability (format "%.2f" instability) "-")
                " | " (if role (name role) "")
                " |"))
         nodes)
    ;; conceptual
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
    ;; change
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
                        " | " (md-pct confidence-a)
                        " | " (md-pct confidence-b)
                        " | " (if structural-edge? "yes" "no ←")
                        " |"))
                 change-pairs))))))

(defn- md-severity-heading [severity]
  (case severity
    :high   "## 🔴 HIGH"
    :medium "## 🟡 MEDIUM"
    :low    "## 🟢 LOW"))

(defn- md-evidence-lines [{:keys [category evidence]}]
  (case category
    :cross-lens-hidden
    [(str "- **Conceptual** score=" (format "%.2f" (:conceptual-score evidence))
          " — shared terms: " (str/join ", " (:shared-terms evidence)))
     (str "- **Change** score=" (format "%.2f" (:change-score evidence))
          " — " (:co-changes evidence) " co-changes")
     "- → No structural edge"]

    :hidden-conceptual
    [(str "- Shared terms: " (str/join ", " (:shared-terms evidence)))
     "- → No structural edge"]

    :hidden-change
    [(str "- " (:co-changes evidence) " co-changes"
          ", conf=" (format "%.0f%%/%.0f%%"
                            (* 100.0 (:confidence-a evidence))
                            (* 100.0 (:confidence-b evidence))))
     "- → No structural edge"]

    :cycle
    [(str "- Members: " (str/join ", " (sort (map str (:members evidence)))))]

    :sdp-violation
    [(str "- Ca=" (:ca evidence) " Ce=" (:ce evidence)
          " I=" (format "%.2f" (:instability evidence)))]

    :god-module
    [(str "- Reach=" (md-pct (:reach evidence))
          " Fan-in=" (md-pct (:fan-in evidence)))]

    :hub
    [(str "- Ce=" (:ce evidence)
          " I=" (format "%.2f" (or (:instability evidence) 0.0))
          " role=" (name (or (:role evidence) :unknown)))]

    []))

(defn format-diagnose-md
  "Markdown rendering of findings."
  [{:keys [src-dirs]} health findings]
  (let [count-sev (fn [s] (count (filter #(= s (:severity %)) findings)))
        n-high    (count-sev :high)
        n-medium  (count-sev :medium)
        n-low     (count-sev :low)
        n-total   (count findings)
        grouped   (group-by :severity findings)
        sections  (keep (fn [sev]
                          (when-let [fs (seq (get grouped sev))]
                            (into [(md-severity-heading sev) ""]
                                  (mapcat (fn [f]
                                            (concat
                                             [(str "### " (format-finding-subject f))
                                              ""
                                              (:reason f)
                                              ""]
                                             (md-evidence-lines f)
                                             [""]))
                                          fs))))
                        [:high :medium :low])]
    (into
     [(str "# Gordian Diagnose — " n-total " Finding"
           (when (not= 1 n-total) "s"))
      ""
      (str "**Source:** `" (str/join " " src-dirs) "`")
      ""
      "## Health"
      ""
      "| Metric | Value |"
      "|--------|-------|"
      (str "| Propagation cost | " (md-pct (:propagation-cost health))
           " (" (name (:health health)) ") |")
      (str "| Cycles | " (if (zero? (:cycle-count health))
                           "none"
                           (:cycle-count health)) " |")
      (str "| Namespaces | " (:ns-count health) " |")]
     (concat
      (mapcat identity sections)
      ["---"
       ""
       (str "**" n-total " finding" (when (not= 1 n-total) "s")
            "** (" n-high " high, " n-medium " medium, " n-low " low)")]))))

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
                          (md-pct (:reach metrics)) "-") " |")
      (str "| Fan-in | " (if (:fan-in metrics)
                           (md-pct (:fan-in metrics)) "-") " |")]
     (concat
      ;; deps
      ["" "## Direct Dependencies" ""
       (str "- **Project:** "
            (if (seq (:project direct-deps))
              (str/join ", " (map #(str "`" % "`") (:project direct-deps)))
              "(none)"))
       (str "- **External:** "
            (if (seq (:external direct-deps))
              (str/join ", " (map #(str "`" % "`") (:external direct-deps)))
              "(none)"))]
      ;; dependents
      ["" "## Direct Dependents" ""]
      (if (seq direct-dependents)
        (mapv #(str "- `" % "`") direct-dependents)
        ["(none)"])
      ;; conceptual
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
      ;; change
      ["" (str "## Change Coupling (" (count change-pairs) " pairs)") ""]
      (if (seq change-pairs)
        (into ["| Namespace | Score | Co | Conf A | Conf B |"
               "|-----------|-------|----|--------|--------|"]
              (map (fn [p]
                     (let [other (if (= ns (:ns-a p)) (:ns-b p) (:ns-a p))]
                       (str "| " other
                            " | " (format "%.2f" (:score p))
                            " | " (:co-changes p)
                            " | " (md-pct (:confidence-a p))
                            " | " (md-pct (:confidence-b p))
                            " |")))
                   change-pairs))
        ["(none)"])
      ;; cycles
      ["" "## Cycles" ""
       (if (seq cycles)
         (str/join "; " (map #(str/join ", " (sort (map str %))) cycles))
         "none")]))))

(defn format-explain-pair-md
  "Markdown rendering of explain-pair data."
  [{:keys [ns-a ns-b structural conceptual change finding error available]}]
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
      ;; conceptual
      ["" "## Conceptual" ""]
      (if conceptual
        ["| Property | Value |"
         "|----------|-------|"
         (str "| Score | " (format "%.2f" (:score conceptual)) " |")
         (str "| Shared terms | " (str/join ", " (:shared-terms conceptual)) " |")
         (str "| Hidden | " (if (:structural-edge? conceptual) "no" "yes") " |")]
        ["(no data)"])
      ;; change
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
      ;; diagnosis
      (when finding
        ["" "## Diagnosis" ""
         (str (case (:severity finding)
                :high   "🔴"
                :medium "🟡"
                :low    "🟢")
              " **" (str/upper-case (name (:severity finding)))
              "** — " (:reason finding))])))))

;;; ── IO ───────────────────────────────────────────────────────────────────

(defn print-report
  "Print a human-readable coupling report to stdout.
  `report` — unified map from gordian.main/build-report."
  [report]
  (run! println (format-report report)))

(defn print-diagnose
  "Print a human-readable diagnose report to stdout."
  [report health findings]
  (run! println (format-diagnose report health findings)))

(defn print-explain-ns
  "Print a human-readable explain-ns report to stdout."
  [data]
  (run! println (format-explain-ns data)))

(defn print-explain-pair
  "Print a human-readable explain-pair report to stdout."
  [data]
  (run! println (format-explain-pair data)))
