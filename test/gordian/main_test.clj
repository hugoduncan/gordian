(ns gordian.main-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.fs :as fs]
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
  (testing "no args → defaults to [\".\"] (auto-discovery)"
    (is (= ["."] (:src-dirs (sut/parse-args [])))))

  (testing "nil args → defaults to [\".\"]"
    (is (= ["."] (:src-dirs (sut/parse-args nil)))))

  (testing "'analyze' alone → defaults to [\".\"]"
    (is (= ["."] (:src-dirs (sut/parse-args ["analyze"])))))

  (testing "--json and --edn together → error"
    (is (contains? (sut/parse-args ["src/" "--json" "--edn"]) :error)))

  (testing "--markdown and --json together → error"
    (is (contains? (sut/parse-args ["src/" "--markdown" "--json"]) :error)))

  (testing "--markdown and --edn together → error"
    (is (contains? (sut/parse-args ["src/" "--markdown" "--edn"]) :error))))

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
      (is (str/includes? out "dir-or-src"))
      (is (str/includes? out "subgraph"))
      (is (str/includes? out "communities"))
      (is (str/includes? out "tests"))
      (is (str/includes? out "cyclomatic"))
      (is (str/includes? out "--include-tests"))
      (is (str/includes? out "--exclude")))))

;;; ── parse-args / subcommands ──────────────────────────────────────────────

(deftest parse-args-diagnose-test
  (testing "diagnose . → command :diagnose"
    (let [opts (sut/parse-args ["diagnose" "."])]
      (is (= :diagnose (:command opts)))
      (is (= ["."] (:src-dirs opts)))))

  (testing "diagnose alone → command :diagnose, defaults to ."
    (let [opts (sut/parse-args ["diagnose"])]
      (is (= :diagnose (:command opts)))
      (is (= ["."] (:src-dirs opts)))))

  (testing "diagnose with --conceptual override"
    (let [opts (sut/parse-args ["diagnose" "." "--conceptual" "0.30"])]
      (is (= :diagnose (:command opts)))
      (is (= 0.30 (:conceptual opts)))))

  (testing "diagnose with --rank actionability"
    (let [opts (sut/parse-args ["diagnose" "." "--rank" "actionability"])]
      (is (= :diagnose (:command opts)))
      (is (= :actionability (:rank opts)))))

  (testing "--show-noise parsed as boolean true"
    (let [opts (sut/parse-args ["diagnose" "." "--show-noise"])]
      (is (true? (:show-noise opts)))))

  (testing "no --show-noise → :show-noise absent or nil"
    (let [opts (sut/parse-args ["diagnose" "."])]
      (is (not (:show-noise opts)))))

  (testing "--top N parsed as long"
    (let [opts (sut/parse-args ["diagnose" "." "--top" "5"])]
      (is (= 5 (:top opts)))))

  (testing "no --top → :top absent or nil"
    (let [opts (sut/parse-args ["diagnose" "."])]
      (is (nil? (:top opts)))))

  (testing "plain src/ → no :command (backward compat)"
    (is (nil? (:command (sut/parse-args ["src/"])))))

  (testing "analyze subcommand → no :command"
    (is (nil? (:command (sut/parse-args ["analyze" "src/"]))))))

(deftest parse-args-explain-test
  (testing "explain <ns> → :command :explain"
    (let [opts (sut/parse-args ["explain" "gordian.scan"])]
      (is (= :explain (:command opts)))
      (is (= 'gordian.scan (:explain-ns opts)))
      (is (= ["."] (:src-dirs opts)))))

  (testing "explain with --edn"
    (let [opts (sut/parse-args ["explain" "gordian.scan" "--edn"])]
      (is (= :explain (:command opts)))
      (is (true? (:edn opts)))))

  (testing "explain without ns → error"
    (is (contains? (sut/parse-args ["explain"]) :error))))

(deftest parse-args-explain-pair-test
  (testing "explain-pair <a> <b> → :command :explain-pair"
    (let [opts (sut/parse-args ["explain-pair" "a.core" "b.svc"])]
      (is (= :explain-pair (:command opts)))
      (is (= 'a.core (:explain-ns-a opts)))
      (is (= 'b.svc (:explain-ns-b opts)))
      (is (= ["."] (:src-dirs opts)))))

  (testing "explain-pair with one arg → error"
    (is (contains? (sut/parse-args ["explain-pair" "a.core"]) :error)))

  (testing "explain-pair with no args → error"
    (is (contains? (sut/parse-args ["explain-pair"]) :error))))

(deftest parse-args-compare-test
  (testing "compare <a> <b> → :command :compare"
    (let [opts (sut/parse-args ["compare" "before.edn" "after.edn"])]
      (is (= :compare (:command opts)))
      (is (= "before.edn" (:before-file opts)))
      (is (= "after.edn" (:after-file opts)))))

  (testing "compare with --markdown"
    (let [opts (sut/parse-args ["compare" "a.edn" "b.edn" "--markdown"])]
      (is (= :compare (:command opts)))
      (is (true? (:markdown opts)))))

  (testing "compare with one arg → error"
    (is (contains? (sut/parse-args ["compare" "only.edn"]) :error)))

  (testing "compare with no args → error"
    (is (contains? (sut/parse-args ["compare"]) :error))))

(deftest parse-args-gate-test
  (testing "gate requires baseline"
    (is (contains? (sut/parse-args ["gate" "."]) :error)))

  (testing "gate with baseline and explicit dir"
    (let [opts (sut/parse-args ["gate" "." "--baseline" "base.edn"])]
      (is (= :gate (:command opts)))
      (is (= ["."] (:src-dirs opts)))
      (is (= "base.edn" (:baseline opts)))))

  (testing "gate with no dir defaults to ."
    (let [opts (sut/parse-args ["gate" "--baseline" "base.edn"])]
      (is (= :gate (:command opts)))
      (is (= ["."] (:src-dirs opts)))))

  (testing "gate captures threshold flags"
    (let [opts (sut/parse-args ["gate" "." "--baseline" "base.edn"
                                "--max-pc-delta" "0.01"
                                "--max-new-high-findings" "1"
                                "--max-new-medium-findings" "2"
                                "--fail-on" "new-cycles,new-high-findings"])]
      (is (= 0.01 (:max-pc-delta opts)))
      (is (= 1 (:max-new-high-findings opts)))
      (is (= 2 (:max-new-medium-findings opts)))
      (is (= "new-cycles,new-high-findings" (:fail-on opts))))))

(deftest parse-args-subgraph-test
  (testing "subgraph <prefix> -> :command :subgraph"
    (let [opts (sut/parse-args ["subgraph" "gordian"])]
      (is (= :subgraph (:command opts)))
      (is (= "gordian" (:prefix opts)))
      (is (= ["."] (:src-dirs opts)))))

  (testing "subgraph with --markdown"
    (let [opts (sut/parse-args ["subgraph" "gordian" "--markdown"])]
      (is (= :subgraph (:command opts)))
      (is (true? (:markdown opts)))))

  (testing "subgraph without prefix -> error"
    (is (contains? (sut/parse-args ["subgraph"]) :error))))

(deftest parse-args-communities-test
  (testing "communities -> :command :communities"
    (let [opts (sut/parse-args ["communities"])]
      (is (= :communities (:command opts)))
      (is (= ["."] (:src-dirs opts)))))

  (testing "communities . --lens structural --threshold 1.0"
    (let [opts (sut/parse-args ["communities" "." "--lens" "structural" "--threshold" "1.0"])]
      (is (= :communities (:command opts)))
      (is (= :structural (:lens opts)))
      (is (= 1.0 (:threshold opts))))))

(deftest parse-args-dsm-test
  (testing "dsm -> :command :dsm"
    (let [opts (sut/parse-args ["dsm"])]
      (is (= :dsm (:command opts)))
      (is (= ["."] (:src-dirs opts)))))

  (testing "dsm . --exclude foo\\..*"
    (let [opts (sut/parse-args ["dsm" "." "--exclude" "foo\\..*"])]
      (is (= :dsm (:command opts)))
      (is (= ["foo\\..*"] (:exclude opts)))))

  (testing "dsm --include-tests"
    (let [opts (sut/parse-args ["dsm" "--include-tests"])]
      (is (= :dsm (:command opts)))
      (is (true? (:include-tests opts)))))

  (testing "dsm --html-file out.html"
    (let [opts (sut/parse-args ["dsm" "--html-file" "out.html"])]
      (is (= :dsm (:command opts)))
      (is (= "out.html" (:html-file opts))))))

(deftest parse-args-tests-test
  (testing "tests -> :command :tests"
    (let [opts (sut/parse-args ["tests"])]
      (is (= :tests (:command opts)))
      (is (= ["."] (:src-dirs opts)))))

  (testing "tests . --markdown"
    (let [opts (sut/parse-args ["tests" "." "--markdown"])]
      (is (= :tests (:command opts)))
      (is (true? (:markdown opts))))))

(deftest parse-args-cyclomatic-test
  (testing "complexity -> :command :cyclomatic"
    (let [opts (sut/parse-args ["complexity"])]
      (is (= :cyclomatic (:command opts)))
      (is (= ["."] (:src-dirs opts)))))

  (testing "cyclomatic -> :command :cyclomatic"
    (let [opts (sut/parse-args ["cyclomatic"])]
      (is (= :cyclomatic (:command opts)))
      (is (= ["."] (:src-dirs opts)))))

  (testing "complexity . --json"
    (let [opts (sut/parse-args ["complexity" "." "--json"])]
      (is (= :cyclomatic (:command opts)))
      (is (true? (:json opts)))))

  (testing "cyclomatic . --json"
    (let [opts (sut/parse-args ["cyclomatic" "." "--json"])]
      (is (= :cyclomatic (:command opts)))
      (is (true? (:json opts)))))

  (testing "complexity rejects conflicting scope flags"
    (is (= "complexity rejects --source-only combined with --tests-only"
           (:error (sut/parse-args ["complexity" "--source-only" "--tests-only"])))))

  (testing "complexity rejects explicit paths with scope flags"
    (is (= "complexity rejects explicit paths combined with --source-only or --tests-only"
           (:error (sut/parse-args ["complexity" "src" "--tests-only"])))))

  (testing "complexity rejects invalid sort key"
    (is (= "complexity --sort must be one of: cc, ns, var, cc-risk"
           (:error (sut/parse-args ["complexity" "--sort" "bogus"])))))

  (testing "complexity rejects non-positive top"
    (is (= "complexity --top must be a positive integer"
           (:error (sut/parse-args ["complexity" "--top" "0"]))))))

(deftest parse-args-complexity-min-cc-test
  (testing "complexity rejects negative min-cc"
    (is (= "complexity --min-cc must be a non-negative integer"
           (:error (sut/parse-args ["complexity" "--min-cc" "-1"]))))))

;;; ── diagnose integration ─────────────────────────────────────────────────

(deftest diagnose-integration-test
  (testing "diagnose on fixture-project produces output"
    (let [out (with-out-str
                (sut/diagnose-cmd {:src-dirs ["resources/fixture-project/src"]}))]
      (is (str/includes? out "gordian diagnose"))
      (is (str/includes? out "HEALTH"))))

  (testing "diagnose with --edn includes :findings"
    (let [out (with-out-str
                (sut/diagnose-cmd {:src-dirs ["resources/fixture-project/src"]
                                   :edn true}))
          parsed (read-string out)]
      (is (contains? parsed :findings))
      (is (contains? parsed :health))
      (is (vector? (:findings parsed))))))

;;; ── explain integration ─────────────────────────────────────────────────

(deftest explain-integration-test
  (testing "explain on gordian.main produces output"
    (let [out (with-out-str
                (sut/explain-cmd {:src-dirs ["src/"]
                                  :explain-ns 'gordian.main}))]
      (is (str/includes? out "gordian explain"))
      (is (str/includes? out "gordian.main"))
      (is (str/includes? out "DIRECT DEPENDENCIES"))))

  (testing "explain with --edn returns structured map"
    (let [out (with-out-str
                (sut/explain-cmd {:src-dirs ["src/"]
                                  :explain-ns 'gordian.main
                                  :edn true}))
          parsed (read-string out)]
      (is (= 'gordian.main (:ns parsed)))
      (is (contains? parsed :metrics))
      (is (contains? parsed :direct-deps)))))

(deftest explain-pair-integration-test
  (testing "explain-pair produces output"
    (let [out (with-out-str
                (sut/explain-pair-cmd {:src-dirs ["src/"]
                                       :explain-ns-a 'gordian.aggregate
                                       :explain-ns-b 'gordian.close}))]
      (is (str/includes? out "gordian explain-pair"))
      (is (str/includes? out "gordian.aggregate"))
      (is (str/includes? out "gordian.close"))
      (is (str/includes? out "STRUCTURAL"))))

  (testing "explain-pair with --edn returns structured map"
    (let [out (with-out-str
                (sut/explain-pair-cmd {:src-dirs ["src/"]
                                       :explain-ns-a 'gordian.aggregate
                                       :explain-ns-b 'gordian.close
                                       :edn true}))
          parsed (read-string out)]
      (is (= 'gordian.aggregate (:ns-a parsed)))
      (is (= 'gordian.close (:ns-b parsed)))
      (is (contains? parsed :structural)))))

(deftest subgraph-integration-test
  (testing "subgraph produces output"
    (let [out (with-out-str
                (sut/subgraph-cmd {:src-dirs ["src/"]
                                   :prefix "gordian"}))]
      (is (str/includes? out "gordian subgraph"))
      (is (str/includes? out "INTERNAL"))
      (is (str/includes? out "BOUNDARY"))))

  (testing "subgraph with --edn returns structured map"
    (let [out (with-out-str
                (sut/subgraph-cmd {:src-dirs ["src/"]
                                   :prefix "gordian"
                                   :edn true}))
          parsed (read-string out)]
      (is (= :subgraph (:gordian/command parsed)))
      (is (= "gordian" (:prefix parsed)))
      (is (contains? parsed :members))
      (is (contains? parsed :internal)))))

(deftest explain-prefix-fallback-test
  (testing "explain falls back to subgraph when no exact namespace exists"
    (let [out (with-out-str
                (sut/explain-cmd {:src-dirs ["src/"]
                                  :explain-ns 'gordian}))]
      (is (str/includes? out "gordian subgraph"))
      (is (str/includes? out "INTERNAL")))))

(deftest communities-integration-test
  (testing "communities produces output"
    (let [out (with-out-str
                (sut/communities-cmd {:src-dirs ["src/"]}))]
      (is (str/includes? out "gordian communities"))
      (is (str/includes? out "SUMMARY"))))

  (testing "communities with --edn returns structured map"
    (let [out (with-out-str (sut/communities-cmd {:src-dirs ["src/"] :edn true}))
          parsed (read-string out)]
      (is (= :communities (:gordian/command parsed)))
      (is (contains? parsed :communities))
      (is (contains? parsed :summary)))))

(deftest dsm-integration-test
  (testing "dsm with --edn returns project-internal structured map"
    (let [out (with-out-str (sut/dsm-cmd {:src-dirs ["resources/fixture"] :edn true}))
          parsed (read-string out)]
      (is (= :dsm (:gordian/command parsed)))
      (is (= :diagonal-blocks (:basis parsed)))
      (is (contains? parsed :ordering))
      (is (contains? parsed :blocks))
      (is (contains? parsed :edges))
      (is (contains? parsed :summary))
      (is (contains? parsed :details))
      (is (every? #(contains? % :subdsm) (:blocks parsed)))
      (is (not (contains? parsed :collapsed)))
      (is (not (contains? parsed :scc-details)))
      (is (= #{'alpha 'beta 'gamma}
             (set (mapcat :members (:blocks parsed)))))))

  (testing "dsm with --json returns structured map"
    (let [out (with-out-str (sut/dsm-cmd {:src-dirs ["resources/fixture"] :json true}))
          parsed (json/parse-string out true)]
      (is (= "dsm" (name (:gordian/command parsed))))
      (is (= "diagonal-blocks" (:basis parsed)))
      (is (contains? parsed :ordering))
      (is (contains? parsed :blocks))
      (is (contains? parsed :edges))
      (is (contains? parsed :summary))
      (is (contains? parsed :details))
      (is (not (contains? parsed :collapsed)))
      (is (not (contains? parsed :scc-details)))))

  (testing "dsm writes html file when requested"
    (let [tmp (str (java.io.File/createTempFile "gordian-dsm" ".html"))]
      (with-out-str (sut/dsm-cmd {:src-dirs ["resources/fixture"] :html-file tmp}))
      (let [html (slurp tmp)]
        (is (str/includes? html "<!DOCTYPE html>"))
        (is (str/includes? html "<title>Gordian DSM</title>"))
        (is (pos? (count html))))
      (io/delete-file tmp)))

  (testing "dsm can emit edn and html artifact together"
    (let [tmp (str (java.io.File/createTempFile "gordian-dsm" ".html"))
          out (with-out-str (sut/dsm-cmd {:src-dirs ["resources/fixture"]
                                          :edn true
                                          :html-file tmp}))
          parsed (read-string out)]
      (is (= :dsm (:gordian/command parsed)))
      (is (.exists (io/file tmp)))
      (io/delete-file tmp))))

(deftest tests-integration-test
  (testing "tests produces output"
    (let [out (with-out-str
                (sut/tests-cmd {:src-dirs ["resources/fixture-project"] :command :tests}))]
      (is (str/includes? out "gordian tests"))
      (is (str/includes? out "SUMMARY"))
      (is (str/includes? out "TEST NAMESPACES"))))

  (testing "tests with --edn returns structured map"
    (let [out (with-out-str
                (sut/tests-cmd {:src-dirs ["resources/fixture-project"]
                                :command :tests
                                :edn true}))
          parsed (read-string out)]
      (is (= :tests (:gordian/command parsed)))
      (is (contains? parsed :summary))
      (is (contains? parsed :test-namespaces))
      (is (contains? parsed :findings)))))

(deftest cyclomatic-integration-test
  (testing "cyclomatic produces output"
    (let [out (with-out-str
                (sut/cyclomatic-cmd {:src-dirs ["resources/fixture"] :command :cyclomatic}))]
      (is (str/includes? out "gordian complexity"))
      (is (str/includes? out "SUMMARY"))
      (is (str/includes? out "NAMESPACE ROLLUP"))
      (is (str/includes? out "alpha"))))

  (testing "cyclomatic with --edn returns structured map"
    (let [out (with-out-str
                (sut/cyclomatic-cmd {:src-dirs ["resources/fixture"]
                                     :command :cyclomatic
                                     :edn true}))
          parsed (read-string out)]
      (is (= :complexity (:gordian/command parsed)))
      (is (contains? parsed :units))
      (is (contains? parsed :namespace-rollups))
      (is (contains? parsed :project-rollup))
      (is (contains? parsed :max-unit))
      (is (contains? parsed :scope))
      (is (contains? parsed :options))))

  (testing "cyclomatic min-cc filters displayed units and rollups"
    (let [out (with-out-str
                (sut/cyclomatic-cmd {:src-dirs ["src"]
                                     :command :cyclomatic
                                     :edn true
                                     :min-cc 20}))
          parsed (read-string out)]
      (is (every? #(<= 20 (:cc %)) (:units parsed)))
      (is (every? #(<= 20 (:max-cc %)) (:namespace-rollups parsed)))
      (is (= 20 (get-in parsed [:options :min-cc]))))))

;;; ── parse-args / --markdown ────────────────────────────────────────────────

(deftest parse-args-markdown-test
  (testing "--markdown flag parsed"
    (is (true? (:markdown (sut/parse-args ["src/" "--markdown"])))))

  (testing "without --markdown, key is absent"
    (is (nil? (:markdown (sut/parse-args ["src/"]))))))

;;; ── markdown integration ─────────────────────────────────────────────────

(deftest resolve-opts-complexity-test
  (testing "complexity project-root default does not include tests"
    (let [opts (sut/resolve-opts {:command :cyclomatic :src-dirs ["resources/fixture-project"]})]
      (is (not (:include-tests opts)))))

  (testing "complexity tests-only includes tests"
    (let [opts (sut/resolve-opts {:command :cyclomatic :src-dirs ["resources/fixture-project"] :tests-only true})]
      (is (true? (:include-tests opts)))))

  (testing "complexity source-only keeps tests excluded"
    (let [opts (sut/resolve-opts {:command :cyclomatic :src-dirs ["resources/fixture-project"] :source-only true})]
      (is (not (:include-tests opts))))))

(deftest complexity-envelope-metadata-test
  (testing "complexity EDN output includes reproducibility metadata inside the payload and standard envelope"
    (let [out (with-out-str
                (sut/cyclomatic-cmd {:src-dirs ["resources/fixture-project"]
                                     :command :cyclomatic
                                     :edn true
                                     :sort :cc-risk
                                     :top 5
                                     :min-cc 2}))
          parsed (read-string out)]
      (is (= :complexity (:gordian/command parsed)))
      (is (contains? parsed :gordian/version))
      (is (contains? parsed :gordian/schema))
      (is (= :discovered (get-in parsed [:scope :mode])))
      (is (= true (get-in parsed [:scope :source?])))
      (is (= false (get-in parsed [:scope :tests?])))
      (is (vector? (get-in parsed [:scope :paths])))
      (is (= :cc-risk (get-in parsed [:options :sort])))
      (is (= 5 (get-in parsed [:options :top])))
      (is (= 2 (get-in parsed [:options :min-cc]))))))

(deftest markdown-integration-test
  (testing "analyze --markdown produces markdown"
    (let [out (with-out-str
                (sut/analyze {:src-dirs ["resources/fixture"]
                              :markdown true}))]
      (is (str/includes? out "# Gordian"))))

  (testing "diagnose --markdown produces markdown"
    (let [out (with-out-str
                (sut/diagnose-cmd {:src-dirs ["resources/fixture-project/src"]
                                   :markdown true}))]
      (is (str/includes? out "# Gordian Diagnose"))))

  (testing "explain --markdown produces markdown"
    (let [out (with-out-str
                (sut/explain-cmd {:src-dirs ["src/"]
                                  :explain-ns 'gordian.main
                                  :markdown true}))]
      (is (str/includes? out "# Gordian Explain"))))

  (testing "explain-pair --markdown produces markdown"
    (let [out (with-out-str
                (sut/explain-pair-cmd {:src-dirs ["src/"]
                                       :explain-ns-a 'gordian.aggregate
                                       :explain-ns-b 'gordian.close
                                       :markdown true}))]
      (is (str/includes? out "# Gordian Explain-Pair"))))

  (testing "cyclomatic --markdown produces markdown"
    (let [out (with-out-str
                (sut/cyclomatic-cmd {:src-dirs ["resources/fixture"]
                                     :markdown true}))]
      (is (str/includes? out "# gordian complexity")))))

;;; ── parse-args / --include-tests + --exclude ─────────────────────────────

(deftest parse-args-include-tests-test
  (testing "--include-tests flag captured"
    (is (true? (:include-tests (sut/parse-args ["." "--include-tests"])))))

  (testing "without --include-tests, key is absent"
    (is (nil? (:include-tests (sut/parse-args ["."]))))))

(deftest parse-args-exclude-test
  (testing "single --exclude captured as vector"
    (is (= ["user"] (:exclude (sut/parse-args ["." "--exclude" "user"])))))

  (testing "multiple --exclude captured as vector"
    (is (= ["user" "scratch"]
           (:exclude (sut/parse-args ["." "--exclude" "user" "--exclude" "scratch"])))))

  (testing "without --exclude, key is absent"
    (is (nil? (:exclude (sut/parse-args ["."]))))))

;;; ── resolve-opts ─────────────────────────────────────────────────────────

(deftest resolve-opts-project-root-test
  (testing "project root discovers src dirs"
    (let [opts (sut/resolve-opts {:src-dirs ["resources/fixture-project"]})]
      (is (not (contains? opts :error)))
      (is (seq (:src-dirs opts)))
      (is (some #(str/includes? % "src") (:src-dirs opts)))))

  (testing "polylith root discovers component and base dirs"
    (let [opts (sut/resolve-opts {:src-dirs ["resources/fixture-polylith"]})]
      (is (not (contains? opts :error)))
      (is (some #(str/includes? % "components/auth/src") (:src-dirs opts)))
      (is (some #(str/includes? % "bases/api/src") (:src-dirs opts))))))

(deftest resolve-opts-explicit-dirs-test
  (testing "explicit src dir (not project root) → used as-is"
    (let [opts (sut/resolve-opts {:src-dirs ["resources/fixture"]})]
      (is (= ["resources/fixture"] (:src-dirs opts))))))

(deftest resolve-opts-include-tests-test
  (testing "include-tests adds test dirs"
    (let [opts (sut/resolve-opts {:src-dirs ["resources/fixture-project"]
                                  :include-tests true})]
      (is (some #(str/includes? % "test") (:src-dirs opts)))))

  (testing "tests command forces include-tests during resolution"
    (let [opts (sut/resolve-opts {:src-dirs ["resources/fixture-project"]
                                  :command :tests})]
      (is (true? (:include-tests opts)))
      (is (some #(str/includes? % "test") (:src-dirs opts))))))

(deftest resolve-opts-empty-project-test
  (testing "project root with no src dirs → error"
    (fs/with-temp-dir [tmp {:prefix "gordian-empty-proj"}]
      (spit (str tmp "/deps.edn") "{}")
      (is (contains? (sut/resolve-opts {:src-dirs [(str tmp)]}) :error)))))

;;; ── build-report with --exclude ──────────────────────────────────────────

(deftest build-report-exclude-test
  (testing "exclude removes matching ns from nodes"
    (let [report (sut/build-report ["resources/fixture-project/src"
                                    "resources/fixture-project/test"]
                                   nil nil ["core-test"])]
      (is (every? #(not (str/includes? (str (:ns %)) "core-test"))
                  (:nodes report)))))

  (testing "no exclude → all nodes present"
    (let [report (sut/build-report ["resources/fixture-project/src"
                                    "resources/fixture-project/test"])]
      (is (= 2 (count (:nodes report)))))))

(deftest structural-report-from-graph-test
  (testing "structural helper builds the expected graph-backed report"
    (let [graph  {'alpha '#{'beta}
                  'beta  #{}}
          report (sut/structural-report-from-graph graph)]
      (is (= graph (:graph report)))
      (is (= [] (:cycles report)))
      (is (= 2 (count (:nodes report))))
      (is (= #{'alpha 'beta} (set (map :ns (:nodes report)))))))

  (testing "build-report structural portion matches helper"
    (let [report      (sut/build-report ["resources/fixture"])
          structural  (sut/structural-report-from-graph (:graph report))]
      (is (= (:graph report) (:graph structural)))
      (is (= (:cycles report) (:cycles structural)))
      (is (= (:propagation-cost report) (:propagation-cost structural)))
      (is (= (set (map :ns (:nodes report)))
             (set (map :ns (:nodes structural))))))))

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
