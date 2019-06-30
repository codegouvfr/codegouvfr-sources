;; Copyright (c) 2019 DINSIC, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.core
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.session :as session]
            [cljs-bean.core :refer [bean]]
            [ajax.core :refer [GET POST]]))

(defonce repos-url "https://api-codes-sources-fr.antoine-augusti.fr/api/repertoires/all")

(re-frame/reg-event-db
 :initialize-db
 (fn [_ _]      
   {:repos nil}))

(re-frame/reg-event-db
 :update-repos
 (fn [db [_ repos]]
   (assoc db :repos repos)))

(re-frame/reg-sub
 :repos
 (fn [db _]
   (:repos db)))

(defn current-page []
  [:div {:class "container"}
   [:table {:class "table"}
    [:thead
     [:tr
      [:th "Nom"]
      [:th "Description"]
      [:th "Forks"]
      [:th "Stars"]
      [:th "Issues"]]]
    [:tbody
     (for [d @(re-frame/subscribe [:repos])]
       [:tr
        [:td [:a {:href (:repertoire_url d)} (:nom d)]]
        [:td (:description d)]
        [:td (:nombre_forks d)]
        [:td (:nombre_stars d)]
        [:td (:nombre_issues_ouvertes d)]])]]])

(defn main-class []
  (reagent/create-class
   {:component-will-mount
    (fn []
      (GET repos-url :handler
           #(re-frame/dispatch [:update-repos (map (comp bean clj->js) %)])))
    :reagent-render current-page}))

(defn ^:export init []
  (re-frame/clear-subscription-cache!)
  (re-frame/dispatch-sync [:initialize-db])
  (reagent/render-component
   [main-class]
   (. js/document (getElementById "app"))))
