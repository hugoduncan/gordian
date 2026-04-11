(ns gordian.git-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [gordian.git :as sut]))

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
