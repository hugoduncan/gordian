(ns gordian.cli
  (:require [gordian.cli.help :as help]
            [gordian.cli.parse :as parse]))

(defn parse-args [raw-args]
  (parse/parse-args raw-args))

(defn parse-result-help-command [result]
  (parse/parse-result-help-command result))

(defn help-text
  ([] (help/help-text))
  ([command] (help/help-text command)))
