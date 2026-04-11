(ns gordian.main
  (:require [gordian.scan      :as scan]
            [gordian.close     :as close]
            [gordian.aggregate :as aggregate]
            [gordian.metrics   :as metrics]
            [gordian.scc       :as scc]
            [gordian.classify  :as classify]
            [gordian.output    :as output]
            [gordian.dot       :as dot]
            [gordian.json      :as report-json]
            [babashka.cli      :as cli]))

;;; ── CLI spec ─────────────────────────────────────────────────────────────

(def ^:private cli-spec
  {:dot  {:desc "Write Graphviz DOT graph to <file>"}
   :json {:desc "Output JSON to stdout (suppresses table)" :coerce :boolean}
   :edn  {:desc "Output EDN to stdout (suppresses table)"  :coerce :boolean}
   :help {:desc "Show this help message"                   :coerce :boolean}})

(def ^:private usage-summary
  "Usage: gordian [analyze] <src-dir> [options]

Options:
  --dot  <file>   Write Graphviz DOT graph to <file>
  --json          Output JSON to stdout (suppresses human-readable table)
  --edn           Output EDN to stdout (suppresses human-readable table)
  --help          Show this help message

Examples:
  gordian analyze src/
  gordian src/ --dot deps.dot
  gordian src/ --json > report.json
  gordian src/ --edn  > report.edn")

(defn print-help []
  (println usage-summary))

;;; ── arg parsing ──────────────────────────────────────────────────────────

(defn parse-args
  "Parse command-line args. Strips optional 'analyze' subcommand.
  Returns one of:
    {:help true}
    {:error <msg>}
    {:src-dir <s> [:dot <file>] [:json true] [:edn true]}"
  [raw-args]
  (let [raw-args (if (= "analyze" (first raw-args)) (rest raw-args) raw-args)
        {:keys [args opts]} (cli/parse-args raw-args {:spec cli-spec})
        src-dir  (first args)]
    (cond
      (:help opts)                     {:help true}
      (nil? src-dir)                   {:error "src-dir is required"}
      (and (:json opts) (:edn opts))   {:error "--json and --edn are mutually exclusive"}
      :else                            (assoc opts :src-dir src-dir))))

;;; ── pipeline ─────────────────────────────────────────────────────────────

(defn- merge-node-metrics [report metrics-map]
  (update report :nodes
          (fn [nodes]
            (mapv #(merge % (get metrics-map (:ns %) {})) nodes))))

(defn build-report
  "Full pipeline: scan → close → aggregate + metrics + cycles → unified report map."
  [src-dir]
  (let [direct (scan/scan src-dir)
        closed (close/close direct)]
    (-> closed
        aggregate/aggregate
        (merge-node-metrics (metrics/compute direct))
        (update :nodes classify/classify)
        (assoc :src-dir src-dir
               :graph   direct
               :cycles  (scc/find-cycles direct)))))

(defn analyze
  "Run analysis with parsed opts map.
  :src-dir required; :dot optional filename; :json/:edn handled in later steps."
  [{:keys [src-dir dot]}]
  (let [report    (build-report src-dir)
        json-path "gordian-report.json"]
    (output/print-report report)
    (when dot
      (spit dot (dot/generate report))
      (println (str "DOT written to " dot)))
    (spit json-path (report-json/generate report))
    (println (str "JSON written to " json-path))))

(defn run [args]
  (let [opts (parse-args args)]
    (cond
      (:help opts)  (do (print-help) (System/exit 0))
      (:error opts) (do (println (str "Error: " (:error opts)))
                        (println)
                        (print-help)
                        (System/exit 1))
      :else         (analyze opts))))
