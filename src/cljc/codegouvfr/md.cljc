(ns codegouvfr.md
  (:require [markdown-to-hiccup.core :as md]))

(defn to-hiccup
  "Convert a markdown `s` string to hiccup structure."
  [s]
  (-> s (md/md->hiccup) (md/component)))
