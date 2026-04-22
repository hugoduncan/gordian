(ns gordian.output.rollup-flag-matrix-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [gordian.output :as sut]
            [gordian.output.fixtures :as fx]))

(defn- complexity-data [{:keys [namespace-rollup project-rollup]}]
  (cond-> (assoc fx/cyclomatic-data
                 :options {:sort :cc
                           :bar nil
                           :namespace-rollup namespace-rollup
                           :project-rollup project-rollup
                           :mins nil})
    (not namespace-rollup) (dissoc :namespace-rollups)
    (not project-rollup) (dissoc :project-rollup)))

(defn- local-data [{:keys [namespace-rollup project-rollup]}]
  (cond-> (assoc fx/local-data
                 :options {:sort :total
                           :bar nil
                           :namespace-rollup namespace-rollup
                           :project-rollup project-rollup
                           :mins nil})
    (not namespace-rollup) (update :display dissoc :namespace-rollups)
    (not namespace-rollup) (dissoc :namespace-rollups)
    (not project-rollup) (dissoc :project-rollup)))

(defn- rendered [format-fn data]
  (str/join "\n" (format-fn data)))

(deftest complexity-rollup-flag-matrix-test
  (testing "namespace-only"
    (let [data (complexity-data {:namespace-rollup true :project-rollup false})
          text (rendered sut/format-complexity data)
          md   (rendered sut/format-complexity-md data)]
      (is (true? (get-in data [:options :namespace-rollup])))
      (is (false? (get-in data [:options :project-rollup])))
      (is (contains? data :namespace-rollups))
      (is (not (contains? data :project-rollup)))
      (is (str/includes? text "NAMESPACE ROLLUP"))
      (is (not (str/includes? text "PROJECT ROLLUP")))
      (is (str/includes? md "## Namespace rollup"))
      (is (not (str/includes? md "## Project rollup")))))

  (testing "project-only"
    (let [data (complexity-data {:namespace-rollup false :project-rollup true})
          text (rendered sut/format-complexity data)
          md   (rendered sut/format-complexity-md data)]
      (is (false? (get-in data [:options :namespace-rollup])))
      (is (true? (get-in data [:options :project-rollup])))
      (is (not (contains? data :namespace-rollups)))
      (is (contains? data :project-rollup))
      (is (not (str/includes? text "NAMESPACE ROLLUP")))
      (is (str/includes? text "PROJECT ROLLUP"))
      (is (not (str/includes? md "## Namespace rollup")))
      (is (str/includes? md "## Project rollup"))))

  (testing "both-rollups"
    (let [data (complexity-data {:namespace-rollup true :project-rollup true})
          text (rendered sut/format-complexity data)
          md   (rendered sut/format-complexity-md data)]
      (is (true? (get-in data [:options :namespace-rollup])))
      (is (true? (get-in data [:options :project-rollup])))
      (is (contains? data :namespace-rollups))
      (is (contains? data :project-rollup))
      (is (str/includes? text "NAMESPACE ROLLUP"))
      (is (str/includes? text "PROJECT ROLLUP"))
      (is (str/includes? md "## Namespace rollup"))
      (is (str/includes? md "## Project rollup")))))

(deftest local-rollup-flag-matrix-test
  (testing "namespace-only"
    (let [data (local-data {:namespace-rollup true :project-rollup false})
          text (rendered sut/format-local data)
          md   (rendered sut/format-local-md data)]
      (is (true? (get-in data [:options :namespace-rollup])))
      (is (false? (get-in data [:options :project-rollup])))
      (is (contains? data :namespace-rollups))
      (is (not (contains? data :project-rollup)))
      (is (contains? (:display data) :namespace-rollups))
      (is (str/includes? text "NAMESPACE ROLLUP"))
      (is (not (str/includes? text "PROJECT ROLLUP")))
      (is (str/includes? md "## Namespace rollup"))
      (is (not (str/includes? md "## Project rollup")))))

  (testing "project-only"
    (let [data (local-data {:namespace-rollup false :project-rollup true})
          text (rendered sut/format-local data)
          md   (rendered sut/format-local-md data)]
      (is (false? (get-in data [:options :namespace-rollup])))
      (is (true? (get-in data [:options :project-rollup])))
      (is (not (contains? data :namespace-rollups)))
      (is (contains? data :project-rollup))
      (is (not (contains? (:display data) :namespace-rollups)))
      (is (not (str/includes? text "NAMESPACE ROLLUP")))
      (is (str/includes? text "PROJECT ROLLUP"))
      (is (not (str/includes? md "## Namespace rollup")))
      (is (str/includes? md "## Project rollup"))))

  (testing "both-rollups"
    (let [data (local-data {:namespace-rollup true :project-rollup true})
          text (rendered sut/format-local data)
          md   (rendered sut/format-local-md data)]
      (is (true? (get-in data [:options :namespace-rollup])))
      (is (true? (get-in data [:options :project-rollup])))
      (is (contains? data :namespace-rollups))
      (is (contains? data :project-rollup))
      (is (contains? (:display data) :namespace-rollups))
      (is (str/includes? text "NAMESPACE ROLLUP"))
      (is (str/includes? text "PROJECT ROLLUP"))
      (is (str/includes? md "## Namespace rollup"))
      (is (str/includes? md "## Project rollup")))))
