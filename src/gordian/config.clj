(ns gordian.config
  "Load and merge .gordian.edn project configuration."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]))

(defn load-config
  "Read .gordian.edn from dir if it exists. Returns map or nil.
  Returns nil on missing file or malformed EDN."
  [dir]
  (let [path (fs/path dir ".gordian.edn")]
    (when (fs/exists? path)
      (try
        (let [content (edn/read-string (slurp (str path)))]
          (when (map? content) content))
        (catch Exception _ nil)))))

(defn merge-opts
  "Merge config map with CLI opts. CLI values take precedence.
  nil config is treated as empty map."
  [config cli-opts]
  (merge (or config {}) (or cli-opts {})))
