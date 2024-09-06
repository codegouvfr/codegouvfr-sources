(ns codegouvfr.macros
  (:require [clojure.java.io :as io]
            [clojure.walk :as walk]
            [markdown-to-hiccup.core :as md]))

(defmacro inline-page [page]
  [:div.fr-container
   [:div.fr-col-8.fr-col-md-8
    (->> (str "public/md/" page)
         io/resource
         slurp
         md/md->hiccup
         md/component
         (walk/prewalk-replace
          {:h2 :h2.fr-h2.fr-mt-3w
           :p  :p.fr-my-3w
           :li :li.fr-ml-2w}))]])
