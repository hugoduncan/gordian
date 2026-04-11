(ns gordian.main-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
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
      (with-out-str (sut/analyze {:src-dirs ["resources/fixture"] :dot tmp}))
      (let [content (slurp tmp)]
        (is (str/includes? content "digraph gordian")))
      (io/delete-file tmp)))

  (testing "without --dot no extra file written to sentinel path"
    (let [sentinel (str (java.io.File/createTempFile "gordian-no-dot" ".dot"))]
      (io/delete-file sentinel)
      (with-out-str (sut/analyze {:src-dirs ["resources/fixture"]}))
      (is (not (.exists (java.io.File. sentinel)))))))

;;; ── analyze / json output ────────────────────────────────────────────────

(deftest json-output-test
  (testing "--json outputs JSON to stdout"
    (let [out (with-out-str (sut/analyze {:src-dirs ["resources/fixture"] :json true}))]
      (is (str/includes? out "propagation-cost"))
      (is (str/includes? out "\"alpha\""))
      (is (map? (json/parse-string out true)))))

  (testing "--json suppresses human-readable table"
    (let [out (with-out-str (sut/analyze {:src-dirs ["resources/fixture"] :json true}))]
      (is (not (str/includes? out "gordian — namespace")))))

  (testing "without --json outputs human-readable table"
    (let [out (with-out-str (sut/analyze {:src-dirs ["resources/fixture"]}))]
      (is (str/includes? out "gordian — namespace"))
      (is (not (str/includes? out "\"propagation-cost\""))))))

;;; ── analyze / edn output ─────────────────────────────────────────────────

(deftest edn-output-test
  (testing "--edn outputs parseable EDN to stdout"
    (let [out    (with-out-str (sut/analyze {:src-dirs ["resources/fixture"] :edn true}))
          parsed (read-string out)]
      (is (map? parsed))
      (is (= ["resources/fixture"] (:src-dirs parsed)))
      (is (every? symbol? (map :ns (:nodes parsed))))))

  (testing "--edn suppresses human-readable table"
    (let [out (with-out-str (sut/analyze {:src-dirs ["resources/fixture"] :edn true}))]
      (is (not (str/includes? out "gordian — namespace")))))

  (testing "without --edn outputs human-readable table"
    (let [out (with-out-str (sut/analyze {:src-dirs ["resources/fixture"]}))]
      (is (str/includes? out "gordian — namespace")))))

;;; ── multi-dir integration ────────────────────────────────────────────────

(deftest multi-dir-analyze-test
  (testing "two src-dirs — all namespaces appear in output"
    (let [out (with-out-str
                (sut/analyze {:src-dirs ["resources/fixture" "resources/fixture-cljc"]}))]
      (doseq [ns-name ["alpha" "beta" "gamma" "portable"]]
        (is (str/includes? out ns-name)))))

  (testing "build-report with two dirs — merged node count"
    (let [report (sut/build-report ["resources/fixture" "resources/fixture-cljc"])]
      (is (= 4 (count (:nodes report)))))))

;;; ── parse-args / --conceptual ────────────────────────────────────────────

(deftest parse-args-conceptual-test
  (testing "--conceptual <float> captured as double"
    (let [opts (sut/parse-args ["src/" "--conceptual" "0.30"])]
      (is (= ["src/"] (:src-dirs opts)))
      (is (= 0.30 (:conceptual opts)))))

  (testing "--conceptual value coerced to double"
    (is (= 0.4 (:conceptual (sut/parse-args ["src/" "--conceptual" "0.4"])))))

  (testing "without --conceptual, key is absent"
    (is (nil? (:conceptual (sut/parse-args ["src/"]))))))

;;; ── analyze / conceptual output ──────────────────────────────────────────

(deftest conceptual-output-test
  (testing "--conceptual appends conceptual section to output"
    (let [out (with-out-str
                (sut/analyze {:src-dirs   ["resources/fixture"]
                              :conceptual 0.01}))]
      (is (str/includes? out "conceptual coupling"))))

  (testing "without --conceptual no conceptual section"
    (let [out (with-out-str (sut/analyze {:src-dirs ["resources/fixture"]}))]
      (is (not (str/includes? out "conceptual coupling")))))

  (testing "build-report with conceptual threshold attaches pairs"
    (let [report (sut/build-report ["resources/fixture"] 0.01)]
      (is (contains? report :conceptual-pairs))
      (is (contains? report :conceptual-threshold))
      (is (number? (:conceptual-threshold report)))))

  (testing "build-report without threshold has no conceptual keys"
    (let [report (sut/build-report ["resources/fixture"])]
      (is (not (contains? report :conceptual-pairs))))))

;;; ── print-help ───────────────────────────────────────────────────────────

(deftest print-help-test
  (testing "help output mentions key flags"
    (let [out (with-out-str (sut/print-help))]
      (is (str/includes? out "--help"))
      (is (str/includes? out "--dot"))
      (is (str/includes? out "--json"))
      (is (str/includes? out "--edn"))
      (is (str/includes? out "--conceptual"))
      (is (str/includes? out "--change"))
      (is (str/includes? out "--change-since"))
      (is (str/includes? out "src-dir")))))

;;; ── parse-args / --change ────────────────────────────────────────────────

(deftest parse-args-change-test
  (testing "--change bare flag → true (normalized to \".\" in analyze)"
    (let [opts (sut/parse-args ["src/" "--change"])]
      (is (= ["src/"] (:src-dirs opts)))
      (is (true? (:change opts)))))

  (testing "--change <dir> captures explicit path"
    (let [opts (sut/parse-args ["src/" "--change" "."])]
      (is (= ["src/"] (:src-dirs opts)))
      (is (= "." (:change opts)))))

  (testing "--change with non-default path"
    (is (= "/my/repo" (:change (sut/parse-args ["src/" "--change" "/my/repo"])))))

  (testing "without --change, key is absent"
    (is (nil? (:change (sut/parse-args ["src/"])))))

  (testing "--change-since captures date string"
    (let [opts (sut/parse-args ["src/" "--change" "--change-since" "90 days ago"])]
      (is (= "90 days ago" (:change-since opts)))))

  (testing "--change-since without --change is accepted by parse-args"
    (is (= "90 days ago" (:change-since (sut/parse-args ["src/" "--change-since" "90 days ago"])))))

  (testing "without --change-since, key is absent"
    (is (nil? (:change-since (sut/parse-args ["src/" "--change"]))))))

;;; ── analyze / change output ──────────────────────────────────────────────

(deftest change-output-test
  (testing "--change with explicit dir appends change coupling section"
    (let [out (with-out-str
                (sut/analyze {:src-dirs ["src/"] :change "."}))]
      (is (str/includes? out "change coupling"))))

  (testing "--change bare flag (true) defaults to . and appends section"
    (let [out (with-out-str
                (sut/analyze {:src-dirs ["src/"] :change true}))]
      (is (str/includes? out "change coupling"))))

  (testing "without --change no change coupling section"
    (let [out (with-out-str (sut/analyze {:src-dirs ["resources/fixture"]}))]
      (is (not (str/includes? out "change coupling")))))

  (testing "build-report with change opts attaches pairs and threshold"
    (let [report (sut/build-report ["src/"] nil {:change "." :min-co 1})]
      (is (contains? report :change-pairs))
      (is (contains? report :change-threshold))
      (is (vector? (:change-pairs report)))))

  (testing "build-report without change opts has no change keys"
    (let [report (sut/build-report ["resources/fixture"])]
      (is (not (contains? report :change-pairs)))))

  (testing "--change-since scopes the commit window"
    ;; since 1 second ago → no commits → empty pairs
    (let [report (sut/build-report ["src/"] nil {:change "." :since "1 second ago"})]
      (is (= [] (:change-pairs report))))))
