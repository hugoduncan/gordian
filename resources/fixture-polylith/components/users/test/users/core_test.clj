(ns users.core-test
  (:require [users.core]))

(defn test-find []
  (users.core/find-user 1))
