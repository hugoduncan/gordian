(ns gordian.discover
  "Detect Clojure project layouts and discover source/test directories."
  (:require [babashka.fs :as fs]))

;;; ── project root detection ───────────────────────────────────────────────

(def project-markers
  "File names whose presence identifies a Clojure project root."
  #{"deps.edn" "bb.edn" "project.clj" "build.boot"
    "shadow-cljs.edn" "workspace.edn"})

(defn project-root?
  "True if dir is an existing directory containing a project marker file."
  [dir]
  (and (fs/directory? dir)
       (boolean (some #(fs/exists? (fs/path dir %)) project-markers))))

;;; ── layout probing ──────────────────────────────────────────────────────

(defn- existing-dir
  "Return (str path) if path is an existing directory, else nil."
  [path]
  (when (fs/directory? path)
    (str path)))

(defn- subdir-dirs
  "For a parent dir like 'components', return all existing child/suffix dirs.
  E.g. (subdir-dirs root \"components\" \"src\") →
       [\"root/components/auth/src\" \"root/components/users/src\" ...]"
  [root parent suffix]
  (let [parent-path (fs/path root parent)]
    (if (fs/directory? parent-path)
      (->> (fs/list-dir parent-path)
           (filter fs/directory?)
           (keep #(existing-dir (fs/path % suffix)))
           vec)
      [])))

(defn discover-dirs
  "Probe standard Clojure project layouts under root.
  Returns {:src-dirs [string] :test-dirs [string]}.
  Only directories that actually exist on disk are returned.
  All paths are absolute strings."
  [root]
  (let [mono-parents ["components" "bases" "extensions" "projects"]]
    {:src-dirs  (vec (concat
                      (keep existing-dir [(fs/path root "src")
                                          (fs/path root "development/src")])
                      (mapcat #(subdir-dirs root % "src") mono-parents)))
     :test-dirs (vec (concat
                      (keep existing-dir [(fs/path root "test")])
                      (mapcat #(subdir-dirs root % "test") mono-parents)))}))

(defn resolve-dirs
  "Given discover result + options, return flat vec of dirs to scan.
  Includes test dirs only when :include-tests is truthy."
  [{:keys [src-dirs test-dirs]} {:keys [include-tests]}]
  (if include-tests
    (into (vec src-dirs) test-dirs)
    (vec src-dirs)))

(defn resolve-paths
  "Given discover result + options, return typed path entries.
  Includes test dirs only when :include-tests is truthy.
  Result shape: [{:dir string :kind :src|:test} ...]."
  [{:keys [src-dirs test-dirs]} {:keys [include-tests]}]
  (vec (concat
        (mapv (fn [dir] {:dir dir :kind :src}) src-dirs)
        (when include-tests
          (mapv (fn [dir] {:dir dir :kind :test}) test-dirs)))))
