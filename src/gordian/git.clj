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

;;; ── path → namespace resolution ──────────────────────────────────────────

(defn path->ns
  "Map a repo-relative file path to a namespace symbol.
  Strips the first matching src-dir prefix, then converts path separators
  to dots, underscores to hyphens, and drops the .clj suffix.

  Example:
    (path->ns \"src/gordian/cc_change.clj\" [\"src\"])
    → 'gordian.cc-change

  If no src-dir prefix matches the path is converted as-is (best effort)."
  [path src-dirs]
  (let [stripped (or (some (fn [d]
                             (let [prefix (str d "/")]
                               (when (str/starts-with? path prefix)
                                 (subs path (count prefix)))))
                           src-dirs)
                     path)]
    (-> stripped
        (str/replace "/" ".")
        (str/replace "_" "-")
        (str/replace #"\.clj$" "")
        symbol)))

(defn commits-as-ns
  "Translate file paths in each commit to namespace symbols.
  Only namespaces that are keys in graph (project namespaces) are kept.
  Commits that resolve to fewer than 2 project namespaces are dropped
  — they can produce no co-change pairs and would only inflate change counts.

  Returns [{:sha str :nss #{ns-sym}}]."
  [file-commits src-dirs graph]
  (let [project (set (keys graph))]
    (keep (fn [{:keys [sha files]}]
            (let [nss (into #{}
                            (comp (map #(path->ns % src-dirs))
                                  (filter project))
                            files)]
              (when (>= (count nss) 2)
                {:sha sha :nss nss})))
          file-commits)))
