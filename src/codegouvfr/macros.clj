;; Copyright (c) 2019-2025 DINUM, Bastien Guerry <bastien.guerry@code.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.macros
  (:require [clojure.java.io :as io]
            [clojure.walk :as walk]
            [markdown-to-hiccup.core :as md]))

(defmacro inline-page [page]
  [:div.fr-container
   [:div.fr-grid-row.fr-grid-row--center
    [:div.fr-col-10.fr-col-md-10
     (->> (str "public/md/" page)
          io/resource
          slurp
          md/md->hiccup
          md/component
          (walk/prewalk-replace
           {:h2 :h2.fr-h2.fr-mt-3w
            :p  :p.fr-my-3w
            :li :li.fr-ml-2w}))]]])
