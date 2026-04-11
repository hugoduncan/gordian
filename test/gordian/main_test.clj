(ns gordian.main-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [cheshire.core :as json]
            [gordian.main :as sut]))

;;; ── parse-args ───────────────────────────────────────────────────────────

(deftest parse-args-src-dirs-test
  (testing "single src-dir"
    (is (= {:src-dirs ["src/"]} (sut/parse-args ["src/"]))))

  (testing "multiple src-dirs"
    (is (= {:src-dirs ["src/" "test/"]} (sut/parse-args ["src/" "test/"]))))

  (testing "analyze subcommand + src-dirs"
    (is (= {:src-dirs ["src/" "test/"]}
           (sut/parse-args ["analyze" "src/" "test/"]))))

  (testing "options do not bleed into src-dirs"
    (let [{:keys [src-dirs dot]} (sut/parse-args ["src/" "--dot" "out.dot"])]
      (is (= ["src/"] src-dirs))
      (is (= "out.dot" dot)))))

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

  (testing "--help with src-dirs → still {:help true}"
    (is (= {:help true} (sut/parse-args ["src/" "--help"])))))

(deftest parse-args-options-test
  (testing "--dot <file> captured"
    (is (= {:src-dirs ["src/"] :dot "out.dot"}
           (sut/parse-args ["src/" "--dot" "out.dot"]))))

  (testing "--json flag captured"
    (is (= {:src-dirs ["src/"] :json true}
           (sut/parse-args ["src/" "--json"]))))

  (testing "--edn flag captured"
    (is (= {:src-dirs ["src/"] :edn true}
           (sut/parse-args ["src/" "--edn"])))))

;;; ── analyze / dot output ─────────────────────────────────────────────────

(deftest dot-output-test
  (testing "--dot writes DOT file to given path"
    (let [tmp (str (java.io.File/createTempFile "gordian-dot" ".dot"))]
      (with-out-str (sut/analyze {:src-dirs ["test/fixture"] :dot tmp}))
      (let [content (slurp tmp)]
        (is (str/includes? content "digraph gordian")))
      (clojure.java.io/delete-file tmp)))

  (testing "without --dot no extra file written to sentinel path"
    (let [sentinel (str (java.io.File/createTempFile "gordian-no-dot" ".dot"))]
      (clojure.java.io/delete-file sentinel)
      (with-out-str (sut/analyze {:src-dirs ["test/fixture"]}))
      (is (not (.exists (java.io.File. sentinel)))))))

;;; ── analyze / json output ────────────────────────────────────────────────

(deftest json-output-test
  (testing "--json outputs JSON to stdout"
    (let [out (with-out-str (sut/analyze {:src-dirs ["test/fixture"] :json true}))]
      (is (str/includes? out "propagation-cost"))
      (is (str/includes? out "\"alpha\""))
      (is (map? (json/parse-string out true)))))

  (testing "--json suppresses human-readable table"
    (let [out (with-out-str (sut/analyze {:src-dirs ["test/fixture"] :json true}))]
      (is (not (str/includes? out "gordian — namespace")))))

  (testing "without --json outputs human-readable table"
    (let [out (with-out-str (sut/analyze {:src-dirs ["test/fixture"]}))]
      (is (str/includes? out "gordian — namespace"))
      (is (not (str/includes? out "\"propagation-cost\""))))))

;;; ── analyze / edn output ─────────────────────────────────────────────────

(deftest edn-output-test
  (testing "--edn outputs parseable EDN to stdout"
    (let [out    (with-out-str (sut/analyze {:src-dirs ["test/fixture"] :edn true}))
          parsed (read-string out)]
      (is (map? parsed))
      (is (= ["test/fixture"] (:src-dirs parsed)))
      (is (every? symbol? (map :ns (:nodes parsed))))))

  (testing "--edn suppresses human-readable table"
    (let [out (with-out-str (sut/analyze {:src-dirs ["test/fixture"] :edn true}))]
      (is (not (str/includes? out "gordian — namespace")))))

  (testing "without --edn outputs human-readable table"
    (let [out (with-out-str (sut/analyze {:src-dirs ["test/fixture"]}))]
      (is (str/includes? out "gordian — namespace")))))

;;; ── multi-dir integration ────────────────────────────────────────────────

(deftest multi-dir-analyze-test
  (testing "two src-dirs — all namespaces appear in output"
    (let [out (with-out-str
                (sut/analyze {:src-dirs ["test/fixture" "test/fixture-cljc"]}))]
      (doseq [ns-name ["alpha" "beta" "gamma" "portable"]]
        (is (str/includes? out ns-name)))))

  (testing "build-report with two dirs — merged node count"
    (let [report (sut/build-report ["test/fixture" "test/fixture-cljc"])]
      (is (= 4 (count (:nodes report)))))))

;;; ── print-help ───────────────────────────────────────────────────────────

(deftest print-help-test
  (testing "help output mentions key flags"
    (let [out (with-out-str (sut/print-help))]
      (is (str/includes? out "--help"))
      (is (str/includes? out "--dot"))
      (is (str/includes? out "--json"))
      (is (str/includes? out "--edn"))
      (is (str/includes? out "src-dir")))))
