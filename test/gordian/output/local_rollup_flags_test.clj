(ns gordian.output.local-rollup-flags-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [gordian.output :as sut]
            [gordian.output.fixtures :as fx]))

(deftest complexity-default-output-omits-rollups
  (let [data (-> fx/cyclomatic-data
                 (assoc :options {:sort :cc :bar nil :namespace-rollup false :project-rollup false :mins nil})
                 (dissoc :namespace-rollups :project-rollup))
        text (str/join "\n" (sut/format-complexity data))
        md   (str/join "\n" (sut/format-complexity-md data))]
    (is (str/includes? text "namespace rollup: off"))
    (is (str/includes? text "project rollup: off"))
    (is (not (str/includes? text "NAMESPACE ROLLUP")))
    (is (not (str/includes? text "PROJECT ROLLUP")))
    (is (str/includes? md "| Namespace rollup | `off` |"))
    (is (str/includes? md "| Project rollup | `off` |"))
    (is (not (str/includes? md "## Namespace rollup")))
    (is (not (str/includes? md "## Project rollup")))))

(deftest local-default-output-omits-rollups
  (let [data (-> fx/local-data
                 (assoc :options {:sort :total :bar nil :namespace-rollup false :project-rollup false :mins nil})
                 (update :display dissoc :namespace-rollups)
                 (dissoc :namespace-rollups :project-rollup))
        text (str/join "\n" (sut/format-local data))
        md   (str/join "\n" (sut/format-local-md data))]
    (is (str/includes? text "namespace rollup: off"))
    (is (str/includes? text "project rollup: off"))
    (is (not (str/includes? text "NAMESPACE ROLLUP")))
    (is (not (str/includes? text "PROJECT ROLLUP")))
    (is (str/includes? md "| Namespace rollup | `off` |"))
    (is (str/includes? md "| Project rollup | `off` |"))
    (is (not (str/includes? md "## Namespace rollup")))
    (is (not (str/includes? md "## Project rollup")))))
