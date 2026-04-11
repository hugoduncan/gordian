(ns gordian.output)

;;; ── formatting helpers ───────────────────────────────────────────────────

(defn- pct
  "Format a 0–1 fraction as a right-aligned percentage string, e.g. \" 33.3%\"."
  [x]
  (format "%5.1f%%" (* 100.0 x)))

(defn- pad-right [n s] (format (str "%-" n "s") s))

(defn column-widths
  "Return {:ns-col int} given a seq of node maps.
  ns-col is wide enough for the longest namespace name."
  [nodes]
  {:ns-col (max 20 (apply max 0 (map #(count (str (:ns %))) nodes)))})

(defn format-row
  "Format one node row as a string."
  [ns-col {:keys [ns reach fan-in]}]
  (str (pad-right ns-col (str ns)) "  " (pct reach) "  " (pct fan-in)))

(defn format-report
  "Return a vector of lines for the full coupling report."
  [{:keys [propagation-cost nodes]} src-dir]
  (let [{:keys [ns-col]} (column-widths nodes)
        header (str (pad-right ns-col "namespace") "    reach   fan-in")
        rule   (apply str (repeat (count header) "─"))]
    (into
     [(str "gordian — namespace coupling report")
      (str "src: " src-dir)
      ""
      (str "propagation cost: " (format "%.4f" propagation-cost)
           "  (on average " (format "%.1f" (* 100.0 propagation-cost))
           "% of project reachable per change)")
      ""
      header
      rule]
     (concat
      (map (partial format-row ns-col) nodes)
      [""]))))

;;; ── IO ───────────────────────────────────────────────────────────────────

(defn print-report
  "Print a human-readable coupling report to stdout."
  [metrics src-dir]
  (run! println (format-report metrics src-dir)))
