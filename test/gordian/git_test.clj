(ns gordian.git-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [gordian.git :as sut]))

;;; ── path->ns ─────────────────────────────────────────────────────────────

(deftest path->ns-test
  (testing "strips src-dir prefix and converts to ns symbol"
    (is (= 'gordian.scan
           (sut/path->ns "src/gordian/scan.clj" ["src"]))))

  (testing "underscore converted to hyphen"
    (is (= 'gordian.cc-change
           (sut/path->ns "src/gordian/cc_change.clj" ["src"]))))

  (testing "nested path"
    (is (= 'a.b.c
           (sut/path->ns "src/a/b/c.clj" ["src"]))))

  (testing "test/ prefix stripped"
    (is (= 'gordian.scan-test
           (sut/path->ns "test/gordian/scan_test.clj" ["test"]))))

  (testing "first matching src-dir wins"
    (is (= 'gordian.scan
           (sut/path->ns "src/gordian/scan.clj" ["test" "src"]))))

  (testing "no matching src-dir — path converted as-is without prefix strip"
    (is (= 'gordian.scan
           (sut/path->ns "gordian/scan.clj" []))))

  (testing ".clj suffix dropped"
    (is (= 'foo.bar
           (sut/path->ns "src/foo/bar.clj" ["src"]))))

  (testing "trailing slash on src-dir normalised"
    (is (= 'gordian.scan
           (sut/path->ns "src/gordian/scan.clj" ["src/"])))))

;;; ── commits-as-ns ────────────────────────────────────────────────────────

(def ^:private proj-graph
  {'gordian.scan    #{}
   'gordian.main    #{}
   'gordian.output  #{}})

(def ^:private file-commits
  [{:sha "a" :files #{"src/gordian/scan.clj" "src/gordian/main.clj"}}
   {:sha "b" :files #{"src/gordian/scan.clj" "src/gordian/output.clj"
                      "test/gordian/scan_test.clj"}}
   {:sha "c" :files #{"src/gordian/main.clj"}}         ; only 1 project ns → dropped
   {:sha "d" :files #{"external/lib.clj" "other/lib.clj"}}]) ; none in graph → dropped

(deftest commits-as-ns-test
  (testing "returns a seq"
    (is (seq? (sut/commits-as-ns file-commits ["src" "test"] proj-graph))))

  (testing "file paths resolved to ns symbols"
    (let [result (vec (sut/commits-as-ns file-commits ["src"] proj-graph))]
      (is (= 'gordian.scan (first (filter #(= % 'gordian.scan)
                                          (:nss (first result))))))))

  (testing "only project namespaces (graph keys) kept"
    ;; test/gordian/scan_test.clj → gordian.scan-test, not in graph → excluded
    (let [result (vec (sut/commits-as-ns file-commits ["src" "test"] proj-graph))]
      (is (every? #(every? proj-graph (:nss %)) result))))

  (testing "commits with fewer than 2 project ns dropped"
    ;; sha c: only gordian.main (1 ns) → dropped
    ;; sha d: no project ns → dropped
    (let [result (vec (sut/commits-as-ns file-commits ["src"] proj-graph))
          shas   (set (map :sha result))]
      (is (not (contains? shas "c")))
      (is (not (contains? shas "d")))))

  (testing "commits with ≥2 project ns retained"
    (let [result (vec (sut/commits-as-ns file-commits ["src"] proj-graph))
          shas   (set (map :sha result))]
      (is (contains? shas "a"))
      (is (contains? shas "b"))))

  (testing "empty commits → empty result"
    (is (empty? (sut/commits-as-ns [] ["src"] proj-graph))))

  (testing "empty graph → all commits dropped (no project ns)"
    (is (empty? (sut/commits-as-ns file-commits ["src"] {})))))

;;; ── commits ──────────────────────────────────────────────────────────────
;;;
;;; Tests run against the real gordian repository.  The git history is
;;; immutable so these assertions are stable across runs.

(def ^:private repo-dir ".")

(def ^:private known-sha
  "cb6edf9799a350a4562cc8debe1f218e26798808")

(def ^:private known-sha-clj-files
  "The .clj files touched by known-sha (bb.edn is excluded by the filter)."
  #{"src/gordian/cc_change.clj"
    "test/gordian/cc_change_test.clj"})

(deftest commits-test
  (let [result (sut/commits repo-dir)]

    (testing "returns a non-empty seq"
      (is (seq result)))

    (testing "each entry has :sha and :files"
      (doseq [c (take 10 result)]
        (is (string? (:sha c)))
        (is (set? (:files c)))))

    (testing "each :sha is a 40-char hex string"
      (doseq [c (take 10 result)]
        (is (re-matches #"[0-9a-f]{40}" (:sha c)))))

    (testing "each entry contains only .clj files"
      (doseq [c (take 20 result)]
        (is (every? #(str/ends-with? % ".clj") (:files c)))))

    (testing "every entry has at least one file"
      (doseq [c (take 20 result)]
        (is (seq (:files c)))))

    (testing "known commit present with correct .clj files"
      (let [entry (first (filter #(= known-sha (:sha %)) result))]
        (is (some? entry) "known commit should be present")
        (is (= known-sha-clj-files (:files entry)))))))

(deftest commits-since-test
  (testing "since far past returns same commits as full history"
    (let [all    (vec (sut/commits repo-dir))
          capped (vec (sut/commits repo-dir "10 years ago"))]
      (is (= all capped))))

  (testing "since 1 second from now returns no commits"
    (is (empty? (sut/commits repo-dir "1 second ago"))))

  (testing "since recent date returns fewer commits than full history"
    ;; gordian has commits from multiple sessions; a tight window cuts some
    (let [all    (count (sut/commits repo-dir))
          recent (count (sut/commits repo-dir "1 hour ago"))]
      (is (< recent all))))

  (testing "nil since behaves identically to no-arg arity"
    (is (= (vec (sut/commits repo-dir))
           (vec (sut/commits repo-dir nil))))))
