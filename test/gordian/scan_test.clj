(ns gordian.scan-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [gordian.conceptual :as conceptual]
            [gordian.scan :as sut]))

(def fixture-dir "resources/fixture")

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

  (testing "reader conditional — #?(:clj ...) resolved with :clj feature"
    ;; resources/fixture-cljc/portable.clj requires [alpha] and #?(:clj [beta] :cljs [...])
    ;; edamame with :features #{:clj} expands the :clj branch → deps #{alpha beta}
    (is (= {:ns 'portable :deps '#{alpha beta}}
           (sut/parse-file "resources/fixture-cljc/portable.clj"))))

  (testing "missing file returns nil"
    (is (nil? (sut/parse-file "resources/fixture/does_not_exist.clj"))))

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

  (testing "reader-cond fixture directory"
    (is (= {'portable '#{alpha beta}}
           (sut/scan "resources/fixture-cljc"))))

  (testing "empty directory"
    (let [tmp (str (java.io.File/createTempFile "gordian-dir" ""))
          dir (doto (java.io.File. (str tmp "-dir")) .mkdirs)]
      (is (= {} (sut/scan (str dir))))
      (io/delete-file dir))))

;;; ── parse-file-terms ─────────────────────────────────────────────────────

(deftest parse-file-terms-test
  (testing "returns [ns-sym terms] pair"
    (let [result (sut/parse-file-terms conceptual/extract-terms (str fixture-dir "/alpha.clj"))]
      (is (vector? result))
      (is (= 2 (count result)))))

  (testing "ns sym is correct"
    (let [[ns-sym _] (sut/parse-file-terms conceptual/extract-terms (str fixture-dir "/alpha.clj"))]
      (is (= 'alpha ns-sym))))

  (testing "ns name tokens present"
    (let [[_ terms] (sut/parse-file-terms conceptual/extract-terms (str fixture-dir "/alpha.clj"))]
      (is (some #{"alpha"} terms))))

  (testing "def symbol tokens present"
    (let [[_ terms] (sut/parse-file-terms conceptual/extract-terms (str fixture-dir "/beta.clj"))]
      (is (some #{"hello"} terms))))

  (testing "gamma includes run"
    (let [[_ terms] (sut/parse-file-terms conceptual/extract-terms (str fixture-dir "/gamma.clj"))]
      (is (some #{"run"} terms))))

  (testing "missing file returns nil"
    (is (nil? (sut/parse-file-terms conceptual/extract-terms "resources/fixture/no_such.clj")))))

;;; ── scan-terms ───────────────────────────────────────────────────────────

(deftest scan-terms-test
  (testing "returns map keyed by ns syms"
    (let [result (sut/scan-terms conceptual/extract-terms fixture-dir)]
      (is (= '#{alpha beta gamma} (set (keys result))))))

  (testing "each value is a non-empty term vector"
    (doseq [[_ terms] (sut/scan-terms conceptual/extract-terms fixture-dir)]
      (is (vector? terms))
      (is (seq terms))))

  (testing "alpha terms include 'alpha' and 'hello'"
    (let [terms (get (sut/scan-terms conceptual/extract-terms fixture-dir) 'alpha)]
      (is (some #{"alpha"} terms))
      (is (some #{"hello"} terms))))

  (testing "empty directory returns empty map"
    (let [tmp (doto (java.io.File. (str (java.io.File/createTempFile "gordian" "") "-d")) .mkdirs)]
      (is (= {} (sut/scan-terms conceptual/extract-terms (str tmp))))
      (io/delete-file tmp))))

;;; ── scan-terms-dirs ──────────────────────────────────────────────────────

(deftest scan-terms-dirs-test
  (testing "single dir — same as scan-terms"
    (is (= (sut/scan-terms conceptual/extract-terms fixture-dir)
           (sut/scan-terms-dirs conceptual/extract-terms [fixture-dir]))))

  (testing "merges two dirs — keys are union of both"
    (let [result (sut/scan-terms-dirs conceptual/extract-terms [fixture-dir "resources/fixture-cljc"])]
      (is (= '#{alpha beta gamma portable} (set (keys result))))))

  (testing "empty dirs list → empty map"
    (is (= {} (sut/scan-terms-dirs conceptual/extract-terms [])))))

;;; ── scan-dirs ────────────────────────────────────────────────────────────

(deftest scan-dirs-test
  (testing "single dir — same as scan"
    (is (= (sut/scan fixture-dir)
           (sut/scan-dirs [fixture-dir]))))

  (testing "merges two dirs into one graph"
    (is (= {'alpha    #{}
            'beta     '#{alpha}
            'gamma    '#{alpha beta}
            'portable '#{alpha beta}}
           (sut/scan-dirs [fixture-dir "resources/fixture-cljc"]))))

  (testing "empty dirs list → empty graph"
    (is (= {} (sut/scan-dirs [])))))

(deftest scan-path-test
  (testing "typed src path returns graph and origins"
    (let [result (sut/scan-path {:dir fixture-dir :kind :src})]
      (is (= {'alpha #{}
              'beta  '#{alpha}
              'gamma '#{alpha beta}}
             (:graph result)))
      (is (= {'alpha :src
              'beta  :src
              'gamma :src}
             (:origins result))))))

(deftest scan-paths-test
  (testing "typed paths merge graph and origins"
    (let [result (sut/scan-paths [{:dir fixture-dir :kind :src}
                                  {:dir "resources/fixture-cljc" :kind :test}])]
      (is (= {'alpha    #{}
              'beta     '#{alpha}
              'gamma    '#{alpha beta}
              'portable '#{alpha beta}}
             (:graph result)))
      (is (= {'alpha    :src
              'beta     :src
              'gamma    :src
              'portable :test}
             (:origins result)))))

  (testing "later path wins on collisions for both graph and origin"
    (let [result (sut/scan-paths [{:dir fixture-dir :kind :src}
                                  {:dir fixture-dir :kind :test}])]
      (is (= {'alpha #{}
              'beta  '#{alpha}
              'gamma '#{alpha beta}}
             (:graph result)))
      (is (= {'alpha :test
              'beta  :test
              'gamma :test}
             (:origins result)))))

  (testing "empty path list → empty result"
    (is (= {:graph {} :origins {}}
           (sut/scan-paths [])))))

;;; ── parse-file-all ───────────────────────────────────────────────────────

(deftest parse-file-all-test
  (testing "returns map with :ns :deps :terms"
    (let [result (sut/parse-file-all conceptual/extract-terms (str fixture-dir "/alpha.clj"))]
      (is (map? result))
      (is (contains? result :ns))
      (is (contains? result :deps))
      (is (contains? result :terms))))

  (testing ":ns matches parse-file :ns"
    (is (= (:ns (sut/parse-file (str fixture-dir "/alpha.clj")))
           (:ns (sut/parse-file-all conceptual/extract-terms (str fixture-dir "/alpha.clj"))))))

  (testing ":deps matches parse-file :deps"
    (doseq [f ["alpha.clj" "beta.clj" "gamma.clj"]]
      (is (= (:deps (sut/parse-file (str fixture-dir "/" f)))
             (:deps (sut/parse-file-all conceptual/extract-terms (str fixture-dir "/" f)))))))

  (testing ":terms matches parse-file-terms terms"
    (doseq [f ["alpha.clj" "beta.clj" "gamma.clj"]]
      (let [[_ terms] (sut/parse-file-terms conceptual/extract-terms (str fixture-dir "/" f))
            all-terms (:terms (sut/parse-file-all conceptual/extract-terms (str fixture-dir "/" f)))]
        (is (= terms all-terms)))))

  (testing "missing file returns nil"
    (is (nil? (sut/parse-file-all conceptual/extract-terms "resources/fixture/no_such.clj")))))

;;; ── scan-all ─────────────────────────────────────────────────────────────

(deftest scan-all-test
  (testing "returns map with :graph and :ns->terms keys"
    (let [result (sut/scan-all conceptual/extract-terms fixture-dir)]
      (is (contains? result :graph))
      (is (contains? result :ns->terms))))

  (testing ":graph matches scan"
    (is (= (sut/scan fixture-dir)
           (:graph (sut/scan-all conceptual/extract-terms fixture-dir)))))

  (testing ":ns->terms matches scan-terms"
    (is (= (sut/scan-terms conceptual/extract-terms fixture-dir)
           (:ns->terms (sut/scan-all conceptual/extract-terms fixture-dir)))))

  (testing "empty directory → empty graph and terms"
    (let [tmp (doto (java.io.File. (str (java.io.File/createTempFile "gordian" "") "-d")) .mkdirs)
          result (sut/scan-all conceptual/extract-terms (str tmp))]
      (is (= {} (:graph result)))
      (is (= {} (:ns->terms result)))
      (io/delete-file tmp))))

;;; ── scan-all-dirs ────────────────────────────────────────────────────────

(deftest scan-all-dirs-test
  (testing "single dir — same as scan-all"
    (is (= (sut/scan-all conceptual/extract-terms fixture-dir)
           (sut/scan-all-dirs conceptual/extract-terms [fixture-dir]))))

  (testing ":graph matches scan-dirs"
    (is (= (sut/scan-dirs [fixture-dir "resources/fixture-cljc"])
           (:graph (sut/scan-all-dirs conceptual/extract-terms [fixture-dir "resources/fixture-cljc"])))))

  (testing ":ns->terms matches scan-terms-dirs"
    (is (= (sut/scan-terms-dirs conceptual/extract-terms [fixture-dir "resources/fixture-cljc"])
           (:ns->terms (sut/scan-all-dirs conceptual/extract-terms [fixture-dir "resources/fixture-cljc"])))))

  (testing "empty dirs list → empty result"
    (is (= {:graph {} :ns->terms {}} (sut/scan-all-dirs conceptual/extract-terms [])))))
