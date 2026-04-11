❌ edamame hangs on defmacro backtick without :syntax-quote true

## The bug

parse-opts missing :syntax-quote → edamame throws on every sub-token
inside syntax-quoted form (`(do ~@body)). ::skip handler catches
exception but reader stays INSIDE the form → infinite exception loop.
112,000+ exceptions per defmacro body before timeout.

## Symptom

gordian hangs on any file containing defmacro with backtick body.
parse-file (ns-only) works fine — problem only in read-all-forms.

## Fix

```clojure
(def parse-opts
  {:read-cond    :allow
   :features     #{:clj}
   :fn           true
   :deref        true
   :regex        true
   :syntax-quote true})   ; ← required for defmacro files
```

## Rule

Any edamame full-file reader must include :syntax-quote true.
The error message "Syntax quote not allowed. Use the :syntax-quote
option" tells you exactly what to add — but only if you can see it
before timeout kills the process.
