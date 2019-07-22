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
(defonce orgas-url "https://api-codes-sources-fr.antoine-augusti.fr/api/organisations/all")

(re-frame/reg-event-db
 :initialize-db!
 (fn [_ _]      
   {:repos        nil
    :orgas        nil
    :sort-by      :stars
    :view         :orgas
    :reverse-sort :true}))

(re-frame/reg-event-db
 :update-repos!
 (fn [db [_ repos]]
   (if repos (assoc db :repos repos))))

(re-frame/reg-event-db
 :view!
 (fn [db [_ view]]
   (assoc db :view view)))

(re-frame/reg-event-db
 :update-orgas!
 (fn [db [_ orgas]]
   (if orgas (assoc db :orgas orgas))))

(re-frame/reg-sub
 :sort-by
 (fn [db _] (:sort-by db)))

(re-frame/reg-sub
 :view
 (fn [db _] (:view db)))

(re-frame/reg-sub 
 :reverse-sort
 (fn [db _] (:reverse-sort db)))

(re-frame/reg-event-db
 :sort-by!
 (fn [db [_ k]]
   (when (= k (:sort-by db))
     (re-frame/dispatch [:reverse-sort!]))
   (assoc db :sort-by k)))

(re-frame/reg-event-db
 :reverse-sort!
 (fn [db _]
   (assoc db :reverse-sort (not (:reverse-sort db)))))

(re-frame/reg-sub
 :repos
 (fn [db _]
   (let [repos (case @(re-frame/subscribe [:sort-by])
                 :name   (sort-by :nom (:repos db))
                 :forks  (sort-by :nombre_forks (:repos db))
                 :stars  (sort-by :nombre_stars (:repos db))
                 :issues (sort-by :nombre_issues_ouvertes (:repos db))
                 ;; FIXME: intuitive enough to sort by length of desc?
                 :desc   (sort #(compare (count (:description %1))
                                         (count (:description %2)))
                               (:repos db)) 
                 (:repos db))]
     (if @(re-frame/subscribe [:reverse-sort])
       (reverse repos)
       repos))))

(defn nom-or-login [m] (or (:nom m) (:login m)))

(re-frame/reg-sub
 :orgas
 (fn [db _]
   (let [orgas (case @(re-frame/subscribe [:sort-by])
                 :name (sort #(compare (nom-or-login %1)
                                       (nom-or-login %2)) (:orgas db))
                 ;; FIXME: intuitive enough to sort by length of desc?
                 :desc (sort #(compare (count (:description %1))
                                       (count (:description %2)))
                             (:orgas db)) 
                 (:orgas db))]
     (if @(re-frame/subscribe [:reverse-sort])
       (reverse orgas)
       orgas))))

(defn organizations-page []
  [:table {:class "table"}
   [:thead
    [:tr
     [:th [:button {:on-click (fn [] (re-frame/dispatch [:sort-by! :name]))} "Nom"]]
     [:th [:button {:on-click (fn [] (re-frame/dispatch [:sort-by! :desc]))} "Description"]]]]
   [:tbody
    (for [d @(re-frame/subscribe [:orgas])]
      ^{:key d}
      [:tr
       [:td [:a {:href (:organisation_url d)} (or (:nom d) (:login d))]]
       [:td (:description d)]])]])

(defn repositories-page []
  [:table {:class "table"}
   [:thead
    [:tr
     [:th [:button {:on-click (fn [] (re-frame/dispatch [:sort-by! :name]))} "Nom"]]
     [:th [:button {:on-click (fn [] (re-frame/dispatch [:sort-by! :desc]))} "Description"]]
     [:th [:button {:on-click (fn [] (re-frame/dispatch [:sort-by! :forks]))} "Forks"]]
     [:th [:button {:on-click (fn [] (re-frame/dispatch [:sort-by! :stars]))} "Stars"]]
     [:th [:button {:on-click (fn [] (re-frame/dispatch [:sort-by! :issues]))} "Issues"]]]]
   [:tbody
    (for [d @(re-frame/subscribe [:repos])]
      ^{:key d}
      [:tr
       [:td [:a {:href (:repertoire_url d)} (:nom d)]]
       [:td (:description d)]
       [:td (:nombre_forks d)]
       [:td (:nombre_stars d)]
       [:td (:nombre_issues_ouvertes d)]])]])

(defn main-page []
  [:div
   [:nav {:class "level"}
    [:a {:class "button level-item" :on-click #(re-frame/dispatch [:view! :repos])} "Dépôts"]
    [:a {:class "button level-item" :on-click #(re-frame/dispatch [:view! :orgas])} "Organisations"]]
   (case @(re-frame/subscribe [:view])
     :repos [repositories-page]
     :orgas [organizations-page])])

(defn main-class []
  (reagent/create-class
   {:component-will-mount
    (fn []
      (GET repos-url :handler
           #(re-frame/dispatch [:update-repos! (map (comp bean clj->js) %)]))
      (GET orgas-url :handler
           #(re-frame/dispatch [:update-orgas! (map (comp bean clj->js) %)])))
    :reagent-render main-page}))

(defn ^:export init []
  (re-frame/clear-subscription-cache!)
  (re-frame/dispatch-sync [:initialize-db!])
  (reagent/render
   [main-class]
   (. js/document (getElementById "app"))))
