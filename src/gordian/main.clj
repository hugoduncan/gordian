(ns gordian.main
  (:require [gordian.scan      :as scan]
            [gordian.close     :as close]
            [gordian.aggregate :as aggregate]
            [gordian.metrics   :as metrics]
            [gordian.scc       :as scc]
            [gordian.classify  :as classify]
            [gordian.output    :as output]
            [gordian.dot       :as dot]
            [gordian.json      :as report-json]))

(defn parse-args
  "Strip optional 'analyze' subcommand, then return {:src-dir s} or {:error msg}.
  Accepts: gordian <src-dir>  OR  gordian analyze <src-dir>"
  [[first-arg & rest-args :as args]]
  (let [[src-dir] (if (= "analyze" first-arg) rest-args args)]
    (if src-dir
      {:src-dir src-dir}
      {:error "src-dir is required"})))

(defn- merge-node-metrics
  "Merge per-node metrics map into the :nodes vector of a report."
  [report metrics-map]
  (update report :nodes
          (fn [nodes]
            (mapv #(merge % (get metrics-map (:ns %) {})) nodes))))

(defn build-report
  "Full pipeline: scan → close → aggregate + metrics + cycles → unified report map.
  Returns {:src-dir :propagation-cost :cycles :nodes [{:ns :reach :fan-in :ca :ce :instability}]}."
  [src-dir]
  (let [direct  (scan/scan src-dir)
        closed  (close/close direct)]
    (-> closed
        aggregate/aggregate
        (merge-node-metrics (metrics/compute direct))
        (update :nodes classify/classify)
        (assoc :src-dir src-dir
               :graph   direct
               :cycles  (scc/find-cycles direct)))))

(defn analyze [src-dir]
  (let [report    (build-report src-dir)
        dot-path  "gordian-report.dot"
        json-path "gordian-report.json"]
    (output/print-report report)
    (spit dot-path  (dot/generate report))
    (spit json-path (report-json/generate report))
    (println (str "DOT  written to " dot-path))
    (println (str "JSON written to " json-path))))

(defn run [args]
  (let [{:keys [src-dir error]} (parse-args args)]
    (if error
      (do (println (str "Error: " error))
          (println "Usage: bb analyze <src-dir>")
          (System/exit 1))
      (analyze src-dir))))
