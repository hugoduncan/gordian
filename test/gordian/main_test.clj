(ns gordian.main-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gordian.main :as sut]))

;;; ── parse-args ───────────────────────────────────────────────────────────

(deftest parse-args-src-dir-test
  (testing "bare src-dir"
    (is (= {:src-dir "src/"} (sut/parse-args ["src/"]))))

  (testing "analyze subcommand + src-dir"
    (is (= {:src-dir "src/"} (sut/parse-args ["analyze" "src/"]))))

  (testing "extra positional args after src-dir are ignored"
    (is (= {:src-dir "src/"} (sut/parse-args ["src/" "extra"])))))

(deftest parse-args-errors-test
  (testing "no args → error"
    (is (contains? (sut/parse-args []) :error)))

  (testing "nil args → error"
    (is (contains? (sut/parse-args nil) :error)))

  (testing "'analyze' alone → error"
    (is (contains? (sut/parse-args ["analyze"]) :error)))

  (testing "--json and --edn together → error"
    (is (contains? (sut/parse-args ["src/" "--json" "--edn"]) :error))))

(deftest parse-args-help-test
  (testing "--help flag → {:help true}"
    (is (= {:help true} (sut/parse-args ["--help"]))))

  (testing "--help with src-dir → still {:help true}"
    (is (= {:help true} (sut/parse-args ["src/" "--help"])))))

(deftest parse-args-options-test
  (testing "--dot <file> captured"
    (is (= {:src-dir "src/" :dot "out.dot"}
           (sut/parse-args ["src/" "--dot" "out.dot"]))))

  (testing "--json flag captured"
    (is (= {:src-dir "src/" :json true}
           (sut/parse-args ["src/" "--json"]))))

  (testing "--edn flag captured"
    (is (= {:src-dir "src/" :edn true}
           (sut/parse-args ["src/" "--edn"])))))

;;; ── analyze / dot output ─────────────────────────────────────────────────

(deftest dot-output-test
  (testing "--dot writes DOT file to given path"
    (let [tmp (str (java.io.File/createTempFile "gordian-dot" ".dot"))]
      (with-out-str (sut/analyze {:src-dir "test/fixture" :dot tmp}))
      (let [content (slurp tmp)]
        (is (clojure.string/includes? content "digraph gordian")))
      (clojure.java.io/delete-file tmp)))

  (testing "without --dot no extra file written to any sentinel path"
    ;; analyze runs without throwing; DOT sentinel path untouched
    (let [sentinel (str (java.io.File/createTempFile "gordian-no-dot" ".dot"))]
      (clojure.java.io/delete-file sentinel)               ; ensure absent
      (with-out-str (sut/analyze {:src-dir "test/fixture"})) ; suppress stdout
      (is (not (.exists (java.io.File. sentinel)))))))

;;; ── print-help ───────────────────────────────────────────────────────────

(deftest print-help-test
  (testing "help output mentions key flags"
    (let [out (with-out-str (sut/print-help))]
      (is (str/includes? out "--help"))
      (is (str/includes? out "--dot"))
      (is (str/includes? out "--json"))
      (is (str/includes? out "--edn"))
      (is (str/includes? out "src-dir")))))
