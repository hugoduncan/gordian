(ns gordian.main)

(defn parse-args
  "Returns {:src-dir s} or {:error msg}."
  [[src-dir & _rest]]
  (if src-dir
    {:src-dir src-dir}
    {:error "src-dir is required"}))

(defn run [args]
  (let [{:keys [src-dir error]} (parse-args args)]
    (if error
      (do (println (str "Error: " error))
          (println "Usage: bb analyze <src-dir>")
          (System/exit 1))
      (do (println (str "Analyzing: " src-dir))
          (println "(not yet implemented)")))))
