(ns gordian.scan-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [gordian.conceptual :as conceptual]
            [gordian.scan :as sut]))

(def deps-from-ns-form #'gordian.scan/deps-from-ns-form)
(def parse-file #'gordian.scan/parse-file)
(def scan #'gordian.scan/scan)
(def scan-path #'gordian.scan/scan-path)
(def parse-file-all #'gordian.scan/parse-file-all)
(def scan-all #'gordian.scan/scan-all)

(def fixture-dir "resources/fixture")

;;; ── deps-from-ns-form ────────────────────────────────────────────────────

(deftest deps-from-ns-form-test
  (testing "no require clause"
    (is (= #{} (deps-from-ns-form '(ns alpha)))))

  (testing "vector with alias"
    (is (= '#{some.lib}
           (deps-from-ns-form '(ns foo (:require [some.lib :as s]))))))

  (testing "vector with refer"
    (is (= '#{some.lib}
           (deps-from-ns-form '(ns foo (:require [some.lib :refer [f]]))))))

  (testing "bare symbol"
    (is (= '#{some.lib}
           (deps-from-ns-form '(ns foo (:require some.lib))))))

  (testing "multiple deps in one :require"
    (is (= '#{a b c}
           (deps-from-ns-form '(ns foo (:require [a] [b] [c]))))))

  (testing "multiple :require clauses merged"
    (is (= '#{a b}
           (deps-from-ns-form '(ns foo (:require [a]) (:require [b]))))))

  (testing "non-require clauses are ignored"
    (is (= '#{a}
           (deps-from-ns-form '(ns foo (:import java.io.File) (:require [a])))))))

;;; ── parse-file ───────────────────────────────────────────────────────────

(deftest parse-file-test
  (testing "no-dep file"
    (is (= {:ns 'alpha :deps #{}}
           (parse-file (str fixture-dir "/alpha.clj")))))

  (testing "single dep with alias"
    (is (= {:ns 'beta :deps '#{alpha}}
           (parse-file (str fixture-dir "/beta.clj")))))

  (testing "two deps, mixed styles"
    (is (= {:ns 'gamma :deps '#{alpha beta}}
           (parse-file (str fixture-dir "/gamma.clj")))))

  (testing "reader conditional — #?(:clj ...) resolved with :clj feature"
    ;; resources/fixture-cljc/portable.clj requires [alpha] and #?(:clj [beta] :cljs [...])
    ;; edamame with :features #{:clj} expands the :clj branch → deps #{alpha beta}
    (is (= {:ns 'portable :deps '#{alpha beta}}
           (parse-file "resources/fixture-cljc/portable.clj"))))

  (testing "missing file returns nil"
    (is (nil? (parse-file "resources/fixture/does_not_exist.clj"))))

  (testing "empty file returns nil"
    (let [tmp (str (java.io.File/createTempFile "gordian" ".clj"))]
      (spit tmp "")
      (is (nil? (parse-file tmp))))))

;;; ── scan ─────────────────────────────────────────────────────────────────

(deftest scan-test
  (testing "full fixture graph"
    (is (= {'alpha #{}
            'beta  '#{alpha}
            'gamma '#{alpha beta}}
           (scan fixture-dir))))

  (testing "reader-cond fixture directory"
    (is (= {'portable '#{alpha beta}}
           (scan "resources/fixture-cljc"))))

  (testing "empty directory"
    (let [tmp (str (java.io.File/createTempFile "gordian-dir" ""))
          dir (doto (java.io.File. (str tmp "-dir")) .mkdirs)]
      (is (= {} (scan (str dir))))
      (io/delete-file dir))))

;;; ── scan-dirs ────────────────────────────────────────────────────────────

(deftest scan-dirs-test
  (testing "single dir — same as scan"
    (is (= (scan fixture-dir)
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
    (let [result (scan-path {:dir fixture-dir :kind :src})]
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
    (let [result (parse-file-all conceptual/extract-terms (str fixture-dir "/alpha.clj"))]
      (is (map? result))
      (is (contains? result :ns))
      (is (contains? result :deps))
      (is (contains? result :terms))))

  (testing ":ns matches parse-file :ns"
    (is (= (:ns (parse-file (str fixture-dir "/alpha.clj")))
           (:ns (parse-file-all conceptual/extract-terms (str fixture-dir "/alpha.clj"))))))

  (testing ":deps matches parse-file :deps"
    (doseq [f ["alpha.clj" "beta.clj" "gamma.clj"]]
      (is (= (:deps (parse-file (str fixture-dir "/" f)))
             (:deps (parse-file-all conceptual/extract-terms (str fixture-dir "/" f)))))))

  (testing ":terms include expected extracted tokens"
    (let [alpha-terms (:terms (parse-file-all conceptual/extract-terms (str fixture-dir "/alpha.clj")))
          beta-terms  (:terms (parse-file-all conceptual/extract-terms (str fixture-dir "/beta.clj")))
          gamma-terms (:terms (parse-file-all conceptual/extract-terms (str fixture-dir "/gamma.clj")))]
      (is (some #{"alpha"} alpha-terms))
      (is (some #{"hello"} alpha-terms))
      (is (some #{"hello"} beta-terms))
      (is (some #{"run"} gamma-terms))))

  (testing "missing file returns nil"
    (is (nil? (parse-file-all conceptual/extract-terms "resources/fixture/no_such.clj")))))

;;; ── scan-all ─────────────────────────────────────────────────────────────

(deftest scan-all-test
  (testing "returns map with :graph and :ns->terms keys"
    (let [result (scan-all conceptual/extract-terms fixture-dir)]
      (is (contains? result :graph))
      (is (contains? result :ns->terms))))

  (testing ":graph matches scan"
    (is (= (scan fixture-dir)
           (:graph (scan-all conceptual/extract-terms fixture-dir)))))

  (testing ":ns->terms contains expected namespaces and term vectors"
    (let [ns->terms (:ns->terms (scan-all conceptual/extract-terms fixture-dir))]
      (is (= '#{alpha beta gamma} (set (keys ns->terms))))
      (doseq [[_ terms] ns->terms]
        (is (vector? terms))
        (is (seq terms)))
      (let [alpha-terms (get ns->terms 'alpha)]
        (is (some #{"alpha"} alpha-terms))
        (is (some #{"hello"} alpha-terms)))))

  (testing "empty directory → empty graph and terms"
    (let [tmp (doto (java.io.File. (str (java.io.File/createTempFile "gordian" "") "-d")) .mkdirs)
          result (scan-all conceptual/extract-terms (str tmp))]
      (is (= {} (:graph result)))
      (is (= {} (:ns->terms result)))
      (io/delete-file tmp))))

;;; ── scan-all-dirs ────────────────────────────────────────────────────────

(deftest scan-all-dirs-test
  (testing "single dir — same as scan-all"
    (is (= (scan-all conceptual/extract-terms fixture-dir)
           (sut/scan-all-dirs conceptual/extract-terms [fixture-dir]))))

  (testing ":graph matches scan-dirs"
    (is (= (sut/scan-dirs [fixture-dir "resources/fixture-cljc"])
           (:graph (sut/scan-all-dirs conceptual/extract-terms [fixture-dir "resources/fixture-cljc"])))))

  (testing ":ns->terms merges directories"
    (let [ns->terms (:ns->terms (sut/scan-all-dirs conceptual/extract-terms [fixture-dir "resources/fixture-cljc"]))]
      (is (= '#{alpha beta gamma portable} (set (keys ns->terms))))))

  (testing "empty dirs list → empty result"
    (is (= {:graph {} :ns->terms {}} (sut/scan-all-dirs conceptual/extract-terms [])))))
