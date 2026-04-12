(ns gordian.discover-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [gordian.discover :as sut]))

;;; ── project-root? ───────────────────────────────────────────────────────

(deftest project-root?-test
  (testing "fixture-project has deps.edn → true"
    (is (true? (sut/project-root? "resources/fixture-project"))))

  (testing "fixture-polylith has workspace.edn + deps.edn → true"
    (is (true? (sut/project-root? "resources/fixture-polylith"))))

  (testing "plain fixture dir (no marker) → false"
    (is (false? (sut/project-root? "resources/fixture"))))

  (testing "non-existent path → false"
    (is (false? (sut/project-root? "resources/does-not-exist"))))

  (testing "file path (not dir) → false"
    (is (false? (sut/project-root? "resources/fixture-project/deps.edn"))))

  (testing "gordian project root itself → true (has bb.edn)"
    (is (true? (sut/project-root? ".")))))

;;; ── discover-dirs — simple project ──────────────────────────────────────

(deftest discover-dirs-simple-project-test
  (let [{:keys [src-dirs test-dirs]} (sut/discover-dirs "resources/fixture-project")]

    (testing "finds src/"
      (is (= 1 (count src-dirs)))
      (is (some #(str/ends-with? % "/src") src-dirs)))

    (testing "finds test/"
      (is (= 1 (count test-dirs)))
      (is (some #(str/ends-with? % "/test") test-dirs)))

    (testing "all paths are strings"
      (is (every? string? (concat src-dirs test-dirs))))

    (testing "all paths exist as directories"
      (is (every? fs/directory? (concat src-dirs test-dirs))))))

;;; ── discover-dirs — Polylith layout ─────────────────────────────────────

(deftest discover-dirs-polylith-test
  (let [{:keys [src-dirs test-dirs]} (sut/discover-dirs "resources/fixture-polylith")]

    (testing "finds component src dirs"
      (is (some #(str/includes? % "components/auth/src") src-dirs))
      (is (some #(str/includes? % "components/users/src") src-dirs)))

    (testing "finds base src dirs"
      (is (some #(str/includes? % "bases/api/src") src-dirs)))

    (testing "finds component test dirs"
      (is (some #(str/includes? % "components/users/test") test-dirs)))

    (testing "no duplicate entries"
      (is (= (count src-dirs) (count (distinct src-dirs))))
      (is (= (count test-dirs) (count (distinct test-dirs)))))

    (testing "all paths exist as directories"
      (is (every? fs/directory? (concat src-dirs test-dirs))))))

;;; ── discover-dirs — no layout ───────────────────────────────────────────

(deftest discover-dirs-no-layout-test
  (let [tmp (str (fs/create-temp-dir {:prefix "gordian-empty"}))]
    (testing "dir with no standard layout → empty"
      (let [{:keys [src-dirs test-dirs]} (sut/discover-dirs tmp)]
        (is (empty? src-dirs))
        (is (empty? test-dirs))))))

;;; ── resolve-dirs ────────────────────────────────────────────────────────

(deftest resolve-dirs-test
  (let [discovered {:src-dirs  ["/a/src" "/b/src"]
                    :test-dirs ["/a/test"]}]

    (testing "src-only by default"
      (is (= ["/a/src" "/b/src"]
             (sut/resolve-dirs discovered {}))))

    (testing "with :include-tests → appends test dirs"
      (is (= ["/a/src" "/b/src" "/a/test"]
             (sut/resolve-dirs discovered {:include-tests true}))))

    (testing "empty discover result → empty vec"
      (is (= [] (sut/resolve-dirs {:src-dirs [] :test-dirs []} {}))))

    (testing "empty test-dirs + include-tests → just src-dirs"
      (is (= ["/a/src"]
             (sut/resolve-dirs {:src-dirs ["/a/src"] :test-dirs []}
                               {:include-tests true}))))))

(deftest resolve-paths-test
  (let [discovered {:src-dirs  ["/a/src" "/b/src"]
                    :test-dirs ["/a/test"]}]

    (testing "src-only by default"
      (is (= [{:dir "/a/src" :kind :src}
              {:dir "/b/src" :kind :src}]
             (sut/resolve-paths discovered {}))))

    (testing "with :include-tests → typed test dirs appended"
      (is (= [{:dir "/a/src" :kind :src}
              {:dir "/b/src" :kind :src}
              {:dir "/a/test" :kind :test}]
             (sut/resolve-paths discovered {:include-tests true}))))

    (testing "empty discover result → empty vec"
      (is (= [] (sut/resolve-paths {:src-dirs [] :test-dirs []} {}))))

    (testing "empty test-dirs + include-tests → just typed src dirs"
      (is (= [{:dir "/a/src" :kind :src}]
             (sut/resolve-paths {:src-dirs ["/a/src"] :test-dirs []}
                                {:include-tests true}))))))
