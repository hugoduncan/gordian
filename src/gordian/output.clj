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
  [ns-col {:keys [ns reach fan-in ca ce instability]}]
  (str (pad-right ns-col (str ns))
       "  " (pct reach)
       "  " (pct fan-in)
       "  " (pad-left 3 (str (or ce "-")))
       "  " (pad-left 3 (str (or ca "-")))
       "  " (if instability (format "%4.2f" instability) "   -")))

(defn- format-cycles
  "Return lines describing detected cycles, or a 'none' notice."
  [cycles]
  (if (empty? cycles)
    ["cycles: none"]
    (into ["cycles:"]
          (map-indexed (fn [i members]
                         (str "  [" (inc i) "] "
                              (str/join " → " (sort (map str members)))
                              "  (" (count members) " namespaces)"))
                       cycles))))

(defn format-report
  "Return a vector of lines for the full coupling report.
  `report` — {:src-dir :propagation-cost :cycles :nodes [node-maps]}."
  [{:keys [src-dir propagation-cost cycles nodes]}]
  (let [{:keys [ns-col]} (column-widths nodes)
        header (str (pad-right ns-col "namespace")
                    "    reach   fan-in   Ce   Ca      I")
        rule   (apply str (repeat (count header) "─"))]
    (into
     (-> [(str "gordian — namespace coupling report")
          (str "src: " src-dir)
          ""
          (str "propagation cost: " (format "%.4f" propagation-cost)
               "  (on average " (format "%.1f" (* 100.0 propagation-cost))
               "% of project reachable per change)")
          ""]
         (into (format-cycles cycles))
         (conj "" header rule))
     (concat
      (map (partial format-row ns-col) nodes)
      [""]))))

;;; ── IO ───────────────────────────────────────────────────────────────────

(defn print-report
  "Print a human-readable coupling report to stdout.
  `report` — unified map from gordian.main/build-report."
  [report]
  (run! println (format-report report)))
