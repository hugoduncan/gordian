(ns gordian.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [gordian.config :as sut]))

;;; ── load-config ──────────────────────────────────────────────────────────

(deftest load-config-test
  (testing "dir with valid .gordian.edn → parsed map"
    (fs/with-temp-dir [tmp {:prefix "gordian-cfg"}]
      (spit (str tmp "/.gordian.edn") "{:conceptual 0.20 :change true}")
      (is (= {:conceptual 0.20 :change true} (sut/load-config (str tmp))))))

  (testing "dir without .gordian.edn → nil"
    (fs/with-temp-dir [tmp {:prefix "gordian-nocfg"}]
      (is (nil? (sut/load-config (str tmp))))))

  (testing "malformed .gordian.edn → nil"
    (fs/with-temp-dir [tmp {:prefix "gordian-bad"}]
      (spit (str tmp "/.gordian.edn") "{:broken")
      (is (nil? (sut/load-config (str tmp))))))

  (testing "empty .gordian.edn → nil (empty string is not a map)"
    (fs/with-temp-dir [tmp {:prefix "gordian-empty"}]
      (spit (str tmp "/.gordian.edn") "")
      (is (nil? (sut/load-config (str tmp))))))

  (testing ".gordian.edn with {} → empty map"
    (fs/with-temp-dir [tmp {:prefix "gordian-mt"}]
      (spit (str tmp "/.gordian.edn") "{}")
      (is (= {} (sut/load-config (str tmp))))))

  (testing ".gordian.edn with non-map value → nil"
    (fs/with-temp-dir [tmp {:prefix "gordian-vec"}]
      (spit (str tmp "/.gordian.edn") "[1 2 3]")
      (is (nil? (sut/load-config (str tmp)))))))

;;; ── merge-opts ───────────────────────────────────────────────────────────

(deftest merge-opts-test
  (testing "config fills in missing CLI values"
    (is (= {:conceptual 0.20}
           (sut/merge-opts {:conceptual 0.20} {}))))

  (testing "CLI values override config"
    (is (= {:conceptual 0.40}
           (sut/merge-opts {:conceptual 0.20} {:conceptual 0.40}))))

  (testing "both contribute different keys"
    (is (= {:change true :exclude ["user"]}
           (sut/merge-opts {:exclude ["user"]} {:change true}))))

  (testing "CLI overrides one key, config provides another"
    (is (= {:change "." :exclude ["user"]}
           (sut/merge-opts {:change true :exclude ["user"]} {:change "."}))))

  (testing "nil config → CLI opts"
    (is (= {:conceptual 0.30}
           (sut/merge-opts nil {:conceptual 0.30}))))

  (testing "nil cli-opts → config"
    (is (= {:conceptual 0.20}
           (sut/merge-opts {:conceptual 0.20} nil))))

  (testing "both nil → empty map"
    (is (= {} (sut/merge-opts nil nil)))))
