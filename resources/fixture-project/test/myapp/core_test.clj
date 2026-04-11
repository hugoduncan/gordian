(ns myapp.core-test
  (:require [myapp.core]))

(defn test-greet []
  (myapp.core/greet "world"))
