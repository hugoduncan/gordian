🎯 stemmer -es rule: sibilant guard prevents "namespaces"→"namespac"

Simple suffix stripping of "-es" breaks compound nouns ending in "e":
  "namespaces" - "es" = "namespac"  ← wrong, doesn't match "namespace"
  "processes"  - "es" = "process"   ← correct (word ends in double-s)

Fix: only strip "-es" when the resulting stem ends in ss/x/z (sibilants).
  "processes" → "process" (ends in ss) ✓
  "namespaces" → guard fails → falls to "-s" rule → "namespace" ✓
  "indexes"   → "index" (ends in x) ✓

The "-s" guard (skip words ending in ss/us/is) is complementary:
  "class", "status", "analysis" — protected from over-stemming.

Both guards are needed; without either, common Clojure terms degrade.
