💡 edamame options needed to parse real Clojure files

edamame/parse-string-all with defaults fails on common Clojure syntax.
Required options for parsing production .clj files:

  {:read-cond :allow    ; handle #?(:clj ...) reader conditionals
   :features  #{:clj}  ; expand :clj branch of reader conditionals
   :fn        true      ; allow #(...) anonymous function literals
   :deref     true      ; allow @foo deref syntax
   :regex     true}     ; allow #"regex" literals

Symptom: parse-file returns nil for files using #(), @, or #"".
Caught during self-analysis showing PC=0% (most files silently failed).
