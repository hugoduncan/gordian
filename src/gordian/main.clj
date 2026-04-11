(ns gordian.main
  (:require [gordian.scan        :as scan]
            [gordian.close       :as close]
            [gordian.aggregate   :as aggregate]
            [gordian.metrics     :as metrics]
            [gordian.scc         :as scc]
            [gordian.classify    :as classify]
            [gordian.output      :as output]
            [gordian.conceptual  :as conceptual]
            [gordian.dot         :as dot]
            [gordian.json        :as report-json]
            [gordian.edn         :as report-edn]
            [babashka.cli        :as cli]))

;;; ── CLI spec ─────────────────────────────────────────────────────────────

(def ^:private cli-spec
  {:dot        {:desc "Write Graphviz DOT graph to <file>"}
   :json       {:desc "Output JSON to stdout (suppresses table)" :coerce :boolean}
   :edn        {:desc "Output EDN to stdout (suppresses table)"  :coerce :boolean}
   :conceptual {:desc "Conceptual coupling analysis; provide similarity threshold e.g. 0.30"
                :coerce :double}
   :help       {:desc "Show this help message"                   :coerce :boolean}})

(def ^:private usage-summary
  "Usage: gordian [analyze] <src-dir>... [options]

Options:
  --dot  <file>         Write Graphviz DOT graph to <file>
  --json                Output JSON to stdout (suppresses human-readable table)
  --edn                 Output EDN to stdout (suppresses human-readable table)
  --conceptual <float>  Conceptual coupling analysis at given similarity threshold
  --help                Show this help message

Examples:
  gordian src/
  gordian analyze src/ test/
  gordian src/ --dot deps.dot
  gordian src/ --json > report.json
  gordian src/ test/ --edn > report.edn
  gordian src/ --conceptual 0.30")

(defn print-help []
  (println usage-summary))

;;; ── arg parsing ──────────────────────────────────────────────────────────

(defn parse-args
  "Parse command-line args. Strips optional 'analyze' subcommand.
  All positional args are treated as src-dirs.
  Returns one of:
    {:help true}
    {:error <msg>}
    {:src-dirs [<s> ...] [:dot <file>] [:json true] [:edn true]}"
  [raw-args]
  (let [raw-args (if (= "analyze" (first raw-args)) (rest raw-args) raw-args)
        {:keys [args opts]} (cli/parse-args raw-args {:spec cli-spec})
        src-dirs (seq args)]
    (cond
      (:help opts)                     {:help true}
      (nil? src-dirs)                  {:error "at least one src-dir is required"}
      (and (:json opts) (:edn opts))   {:error "--json and --edn are mutually exclusive"}
      :else                            (assoc opts :src-dirs (vec src-dirs)))))

;;; ── pipeline ─────────────────────────────────────────────────────────────

(defn- merge-node-metrics [report metrics-map]
  (update report :nodes
          (fn [nodes]
            (mapv #(merge % (get metrics-map (:ns %) {})) nodes))))

(defn build-report
  "Full pipeline: scan-dirs → close → aggregate + metrics + cycles.
  `src-dirs`            — vector of source directory paths.
  `conceptual-threshold` — when provided, runs conceptual coupling analysis
                           and attaches :conceptual-pairs + :conceptual-threshold.

  When conceptual-threshold is supplied, uses scan/scan-all-dirs so each file
  is read and parsed exactly once (rather than a structural scan + a separate
  full-file terms scan)."
  ([src-dirs] (build-report src-dirs nil))
  ([src-dirs conceptual-threshold]
   (let [[direct ns->terms]
         (if conceptual-threshold
           (let [{:keys [graph ns->terms]} (scan/scan-all-dirs conceptual/extract-terms src-dirs)]
             [graph ns->terms])
           [(scan/scan-dirs src-dirs) nil])
         closed (close/close direct)
         report (-> closed
                    aggregate/aggregate
                    (merge-node-metrics (metrics/compute direct))
                    (update :nodes classify/classify)
                    (assoc :src-dirs src-dirs
                           :graph    direct
                           :cycles   (scc/find-cycles direct)))]
     (if conceptual-threshold
       (let [tfidf (conceptual/build-tfidf ns->terms)
             pairs (conceptual/conceptual-pairs tfidf direct conceptual-threshold 3)]
         (assoc report
                :conceptual-pairs     pairs
                :conceptual-threshold conceptual-threshold))
       report))))

(defn analyze
  "Run analysis with parsed opts map.
  :src-dirs    — required vector of source directories
  :dot <file>  — write Graphviz DOT to <file> (stderr status line)
  :json true   — print JSON to stdout, suppress human-readable table
  :edn  true   — print EDN to stdout, suppress human-readable table
  :conceptual  — similarity threshold for conceptual coupling section"
  [{:keys [src-dirs dot json edn conceptual]}]
  (let [report (build-report src-dirs conceptual)]
    (when dot
      (spit dot (dot/generate report))
      (binding [*out* *err*] (println (str "DOT written to " dot))))
    (cond
      json (println (report-json/generate report))
      edn  (print   (report-edn/generate  report))
      :else (output/print-report report))))

(defn run [args]
  (let [opts (parse-args args)]
    (cond
      (:help opts)  (do (print-help) (System/exit 0))
      (:error opts) (do (println (str "Error: " (:error opts)))
                        (println)
                        (print-help)
                        (System/exit 1))
      :else         (analyze opts))))
