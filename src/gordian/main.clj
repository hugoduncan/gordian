(ns gordian.main
  (:require [gordian.scan      :as scan]
            [gordian.close     :as close]
            [gordian.aggregate :as aggregate]
            [gordian.output    :as output]))

(defn parse-args
  "Returns {:src-dir s} or {:error msg}."
  [[src-dir & _rest]]
  (if src-dir
    {:src-dir src-dir}
    {:error "src-dir is required"}))

(defn analyze
  "Full pipeline: scan → close → aggregate → print."
  [src-dir]
  (-> src-dir
      scan/scan
      close/close
      (aggregate/aggregate)
      (output/print-report src-dir)))

(defn run [args]
  (let [{:keys [src-dir error]} (parse-args args)]
    (if error
      (do (println (str "Error: " error))
          (println "Usage: bb analyze <src-dir>")
          (System/exit 1))
      (analyze src-dir))))
