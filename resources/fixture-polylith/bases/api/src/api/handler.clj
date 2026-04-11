(ns api.handler
  (:require [users.core]))

(defn handle-request [req]
  (users.core/find-user (:id req)))
