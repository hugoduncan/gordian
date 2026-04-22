(ns gordian.cli.help
  (:require [clojure.string :as str]
            [gordian.cli.registry :as registry]))

(defn- render-options [spec]
  (for [[opt {:keys [desc]}] spec]
    (format "  --%-26s %s" (name opt) desc)))

(defn- render-commands []
  (let [lines (registry/command-summary-lines)
        width (apply max (map #(count (:name %)) lines))]
    (for [{:keys [name summary aliases]} lines]
      (str "  "
           (format (str "%-" width "s") name)
           "  "
           summary
           (when (seq aliases)
             (str " (alias: " (str/join ", " aliases) ")"))))))

(defn top-level-help []
  (str/join
   "\n"
   (concat
    ["Usage: gordian <command> [args] [options]"
     ""
     "When given a project root (dir with deps.edn, bb.edn, etc.), gordian"
     "auto-discovers source directories. With no arguments, defaults to analyze on '.'."
     ""
     "Commands:"]
    (render-commands)
    [""
     "Global options:"]
    (render-options (:spec (registry/command-definition :compare)))
    [""
     "Examples:"
     "  gordian"
     "  gordian . --markdown"
     "  gordian diagnose --help"
     "  gordian complexity . --help"])))

(defn command-help [command]
  (let [{:keys [usage description positional spec examples]} (registry/command-definition command)]
    (str/join
     "\n"
     (concat
      [(str "Usage: " usage)
       ""]
      description
      (when (seq positional)
        (concat ["" "Positional arguments:"] positional))
      ["" "Options:"]
      (render-options spec)
      (when (seq examples)
        (concat ["" "Examples:"] (map #(str "  " %) examples)))))))

(defn help-text
  ([] (top-level-help))
  ([command] (command-help command)))
