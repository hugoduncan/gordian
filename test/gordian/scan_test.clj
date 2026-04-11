(ns gordian.scan-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.scan :as sut]))

(def fixture-dir "test/fixture")

;;; ── deps-from-ns-form ────────────────────────────────────────────────────

(deftest deps-from-ns-form-test
  (testing "no require clause"
    (is (= #{} (sut/deps-from-ns-form '(ns alpha)))))

  (testing "vector with alias"
    (is (= '#{some.lib}
           (sut/deps-from-ns-form '(ns foo (:require [some.lib :as s]))))))

  (testing "vector with refer"
    (is (= '#{some.lib}
           (sut/deps-from-ns-form '(ns foo (:require [some.lib :refer [f]]))))))

  (testing "bare symbol"
    (is (= '#{some.lib}
           (sut/deps-from-ns-form '(ns foo (:require some.lib))))))

  (testing "multiple deps in one :require"
    (is (= '#{a b c}
           (sut/deps-from-ns-form '(ns foo (:require [a] [b] [c]))))))

  (testing "multiple :require clauses merged"
    (is (= '#{a b}
           (sut/deps-from-ns-form '(ns foo (:require [a]) (:require [b]))))))

  (testing "non-require clauses are ignored"
    (is (= '#{a}
           (sut/deps-from-ns-form '(ns foo (:import java.io.File) (:require [a])))))))

;;; ── parse-file ───────────────────────────────────────────────────────────

(deftest parse-file-test
  (testing "no-dep file"
    (is (= {:ns 'alpha :deps #{}}
           (sut/parse-file (str fixture-dir "/alpha.clj")))))

  (testing "single dep with alias"
    (is (= {:ns 'beta :deps '#{alpha}}
           (sut/parse-file (str fixture-dir "/beta.clj")))))

  (testing "two deps, mixed styles"
    (is (= {:ns 'gamma :deps '#{alpha beta}}
           (sut/parse-file (str fixture-dir "/gamma.clj")))))

  (testing "missing file returns nil"
    (is (nil? (sut/parse-file "test/fixture/does_not_exist.clj"))))

  (testing "empty file returns nil"
    (let [tmp (str (java.io.File/createTempFile "gordian" ".clj"))]
      (spit tmp "")
      (is (nil? (sut/parse-file tmp))))))

;;; ── scan ─────────────────────────────────────────────────────────────────

(deftest scan-test
  (testing "full fixture graph"
    (is (= {'alpha #{}
            'beta  '#{alpha}
            'gamma '#{alpha beta}}
           (sut/scan fixture-dir))))

  (testing "empty directory"
    (let [tmp (str (java.io.File/createTempFile "gordian-dir" ""))]
      ;; use a fresh empty dir
      (let [dir (doto (java.io.File. (str tmp "-dir")) .mkdirs)]
        (is (= {} (sut/scan (str dir))))
        (clojure.java.io/delete-file dir)))))
