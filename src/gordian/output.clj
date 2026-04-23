(ns gordian.output
  (:require [gordian.output.analyze :as analyze]
            [gordian.output.communities :as communities]
            [gordian.output.compare :as compare]
            [gordian.output.complexity :as complexity]
            [gordian.output.diagnose :as diagnose]
            [gordian.output.dsm :as dsm]
            [gordian.output.enforcement :as enforcement]
            [gordian.output.explain :as explain]
            [gordian.output.gate :as gate]
            [gordian.output.local :as local]
            [gordian.output.subgraph :as subgraph]
            [gordian.output.tests :as tests]))

(def column-widths analyze/column-widths)
(def format-row analyze/format-row)
(def format-conceptual analyze/format-conceptual)
(def format-change-coupling analyze/format-change-coupling)
(def format-report analyze/format-report)
(def format-report-md analyze/format-report-md)
(def print-report analyze/print-report)

(def format-diagnose diagnose/format-diagnose)
(def format-diagnose-md diagnose/format-diagnose-md)
(def print-diagnose diagnose/print-diagnose)

(def format-explain-ns explain/format-explain-ns)
(def format-explain-pair explain/format-explain-pair)
(def format-explain-ns-md explain/format-explain-ns-md)
(def format-explain-pair-md explain/format-explain-pair-md)
(def print-explain-ns explain/print-explain-ns)
(def print-explain-pair explain/print-explain-pair)

(def format-compare compare/format-compare)
(def format-compare-md compare/format-compare-md)
(def print-compare compare/print-compare)

(def format-subgraph subgraph/format-subgraph)
(def format-subgraph-md subgraph/format-subgraph-md)
(def print-subgraph subgraph/print-subgraph)

(def format-communities communities/format-communities)
(def format-communities-md communities/format-communities-md)
(def print-communities communities/print-communities)

(def format-tests tests/format-tests)
(def format-tests-md tests/format-tests-md)
(def print-tests tests/print-tests)

(def format-gate gate/format-gate)
(def format-gate-md gate/format-gate-md)
(def print-gate gate/print-gate)

(def format-dsm dsm/format-dsm)
(def format-dsm-md dsm/format-dsm-md)
(def print-dsm dsm/print-dsm)

(def format-complexity complexity/format-complexity)
(def format-complexity-md complexity/format-complexity-md)
(def print-complexity complexity/print-complexity)

(def format-local local/format-local)
(def format-local-md local/format-local-md)
(def print-local local/print-local)
