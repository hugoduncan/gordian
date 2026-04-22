(ns gordian.cli.parse
  (:require [babashka.cli :as cli]
            [gordian.cli.registry :as registry]))

(defn parse-result-help-command [result]
  (:help-command result))

(defn- validate-output-modes [{:keys [json edn markdown]}]
  (when (< 1 (count (filter identity [json edn markdown])))
    {:error "--json, --edn, and --markdown are mutually exclusive"}))

(defn- run-validators [command opts]
  (or (validate-output-modes opts)
      (when-let [validate (:validate (registry/command-definition command))]
        (validate opts))))

(defn- command-token? [token]
  (some? (registry/resolve-command token)))

(defn parse-args [raw-args]
  (let [raw-args      (or raw-args [])
        command-token (when (command-token? (first raw-args)) (first raw-args))
        command       (registry/resolve-command command-token)
        command-args  (if command-token (rest raw-args) raw-args)]
    (if command
      (let [{:keys [args opts]} (cli/parse-args command-args {:spec (:spec (registry/command-definition command))})]
        (cond
          (:help opts)
          {:help true :help-command command}

          :else
          (let [parsed ((:parse (registry/command-definition command)) {:args args :opts opts})]
            (if (:error parsed)
              parsed
              (or (run-validators command parsed)
                  parsed)))))
      (let [{:keys [args opts]} (cli/parse-args command-args {:spec (:spec (registry/command-definition :analyze))})
            parsed ((:parse (registry/command-definition :analyze)) {:args args :opts opts})]
        (cond
          (:help opts)
          {:help true}

          :else
          (or (run-validators :analyze parsed)
              parsed))))))
