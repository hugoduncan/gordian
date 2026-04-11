(ns gordian.main-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.main :as sut]))

(deftest parse-args-test
  (testing "bare src-dir"
    (is (= {:src-dir "src/"} (sut/parse-args ["src/"]))))

  (testing "analyze subcommand + src-dir"
    (is (= {:src-dir "src/"} (sut/parse-args ["analyze" "src/"]))))

  (testing "extra args after src-dir are ignored"
    (is (= {:src-dir "src/"} (sut/parse-args ["src/" "--extra"]))))

  (testing "analyze + extra args after src-dir are ignored"
    (is (= {:src-dir "src/"} (sut/parse-args ["analyze" "src/" "--extra"]))))

  (testing "no args yields error"
    (is (contains? (sut/parse-args []) :error)))

  (testing "nil args yields error"
    (is (contains? (sut/parse-args nil) :error)))

  (testing "'analyze' alone (no src-dir) yields error"
    (is (contains? (sut/parse-args ["analyze"]) :error))))
