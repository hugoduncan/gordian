(ns users.core
  (:require [auth.core]))

(defn find-user [id]
  {:id id :authenticated (auth.core/authenticate "tok")})
