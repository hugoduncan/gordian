(ns gordian.main
  (:require [gordian.scan      :as scan]
            [gordian.close     :as close]
            [gordian.aggregate :as aggregate]
            [gordian.metrics   :as metrics]
            [gordian.scc       :as scc]
            [gordian.output    :as output]))

(defn parse-args
  "Returns {:src-dir s} or {:error msg}."
  [[src-dir & _rest]]
  (if src-dir
    {:src-dir src-dir}
    {:error "src-dir is required"}))

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
        (assoc :src-dir src-dir
               :cycles  (scc/find-cycles direct)))))

(defn analyze [src-dir]
  (output/print-report (build-report src-dir)))

(defn run [args]
  (let [{:keys [src-dir error]} (parse-args args)]
    (if error
      (do (println (str "Error: " error))
          (println "Usage: bb analyze <src-dir>")
          (System/exit 1))
      (analyze src-dir))))
