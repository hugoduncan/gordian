(ns auth.core)

(defn authenticate [token]
  (some? token))
