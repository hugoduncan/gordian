(ns gordian.git
  (:require [babashka.process :as proc]
            [clojure.string   :as str]))

;;; ── git log parsing ──────────────────────────────────────────────────────

(def ^:private sha-re
  "Regex matching a full 40-character git SHA-1 hex string."
  #"^[0-9a-f]{40}$")

(defn- parse-log
  "Parse the output of `git log --name-only --format=\"%H\"` into
  [{:sha str :files #{path-str}}].
  Only commits that modified at least one .clj file are returned."
  [output]
  (let [{:keys [current result]}
        (reduce
         (fn [{:keys [current result]} line]
           (cond
             (str/blank? line)
             {:current current :result result}

             (re-matches sha-re line)
             {:current {:sha line :files #{}}
              :result  (if (seq (:files current))
                         (conj result current)
                         result)}

             :else
             {:current (update current :files conj line)
              :result  result}))
         {:current nil :result []}
         (str/split-lines output))]
    ;; flush the final commit
    (if (seq (:files current))
      (conj result current)
      result)))

(defn commits
  "Run git log in repo-dir and return [{:sha str :files #{path-str}}].
  Only commits that touched at least one .clj file are included.
  Uses --diff-filter=AM (Added + Modified) — deletions and renames
  are excluded because they skew co-change counts."
  [repo-dir]
  (->> (-> (proc/shell {:out :string :dir repo-dir}
                       "git" "log" "--name-only" "--diff-filter=AM"
                       "--format=%H")
           :out
           parse-log)
       (keep (fn [{:keys [sha files]}]
               (let [clj-files (into #{} (filter #(str/ends-with? % ".clj")) files)]
                 (when (seq clj-files)
                   {:sha sha :files clj-files}))))))
