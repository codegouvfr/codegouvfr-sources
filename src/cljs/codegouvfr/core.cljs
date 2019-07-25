;; Copyright (c) 2019 DINSIC, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.core
  (:require [cljs.core.async :as async]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.session :as session]
            [cljs-bean.core :refer [bean]]
            [ajax.core :refer [GET POST]]))

(defonce repos-url "https://api-codes-sources-fr.antoine-augusti.fr/api/repertoires/all")
(defonce orgas-url "https://api-codes-sources-fr.antoine-augusti.fr/api/organisations/all")
(def pages 200) ;; FIXME: customizable?

(re-frame/reg-event-db
 :initialize-db!
 (fn [_ _]      
   {:repos        nil
    :repos-page   0
    :orgas        nil
    :sort-by      :stars
    :view         :repos
    :reverse-sort :true
    :filter       ""}))

(re-frame/reg-event-db
 :update-repos!
 (fn [db [_ repos]]
   (if repos (assoc db :repos repos))))

(re-frame/reg-event-db
 :filter!
 (fn [db [_ s]]
   (re-frame/dispatch [:repos-page! 0])
   (assoc db :filter s)))

(re-frame/reg-event-db
 :repos-page!
 (fn [db [_ n]]
   (assoc db :repos-page n)))

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
 :repos-page
 (fn [db _] (:repos-page db)))

(re-frame/reg-sub
 :filter
 (fn [db _] (:filter db)))

(re-frame/reg-sub
 :view
 (fn [db _] (:view db)))

(re-frame/reg-sub 
 :reverse-sort
 (fn [db _] (:reverse-sort db)))

(re-frame/reg-event-db
 :sort-by!
 (fn [db [_ k]]
   (re-frame/dispatch [:repos-page! 0])
   (when (= k (:sort-by db))
     (re-frame/dispatch [:reverse-sort!]))
   (assoc db :sort-by k)))

(re-frame/reg-event-db
 :reverse-sort!
 (fn [db _]
   (assoc db :reverse-sort (not (:reverse-sort db)))))

(defn or-kwds [m ks]
  (first (remove nil? (map #(apply % [m]) ks))))

(defn fa [s]
  [:span {:class "icon has-text-info"}
   [:i {:class (str "fas " s)}]])

(defn to-locale-date [s]
  (.toLocaleDateString
   (js/Date. (.parse js/Date s))))

(defn apply-filter [m s ks]
  (if (empty? s) m ;; Filter string is empty, return the map
      (filter #(re-find (re-pattern (str "(?i)" s))
                        (clojure.string/join
                         " " (vals (select-keys % ks))))
              m)))

(def search-filter-chan (async/chan 10))

(defn start-search-filter-loop []
  (async/go
    (loop [s (async/<! search-filter-chan)]
      (re-frame/dispatch [:filter! s])
      (recur (async/<! search-filter-chan)))))

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
     (apply-filter
      (if @(re-frame/subscribe [:reverse-sort])
        (reverse repos)
        repos)
      @(re-frame/subscribe [:filter])
      [:description :nom])))) ;; FIXME: Other fields?

(re-frame/reg-sub
 :orgas
 (fn [db _]
   (let [orgas (case @(re-frame/subscribe [:sort-by])
                 :name (sort #(compare (or-kwds %1 [:nom :login])
                                       (or-kwds %2 [:nom :login]))
                             (:orgas db))
                 ;; FIXME: intuitive enough to sort by length of desc?
                 :desc (sort #(compare (count (:description %1))
                                       (count (:description %2)))
                             (:orgas db)) 
                 (:orgas db))]
     (apply-filter
      orgas
      @(re-frame/subscribe [:filter])
      [:description :nom :login]))))

(defn organizations-page []
  (into
   [:div]
   (for [d (partition-all 3 @(re-frame/subscribe [:orgas]))]
     ^{:key d}
     [:div {:class "columns"}
      (for [{:keys [nom login organisation_url site_web
                    date_creation description nombre_repertoires email]
             :as   o} d]
        ^{:key o}
        [:div {:class "column is-4"}
         [:div {:class "card"}
          [:div {:class "card-header"}
           [:a {:class  "card-header-title subtitle"
                :target "new"
                :title  "Visiter le compte d'organisation"
                :href   organisation_url} (or nom login)]]
          [:div {:class "card-content"}
           [:div {:class "content"}
            [:p description]
            [:p "Créé le " (to-locale-date date_creation)]]]
          [:div {:class "card-footer"}
           (if nombre_repertoires
             [:div {:class "card-footer-item"
                    :title "Nombre de répertoires"}
              nombre_repertoires])
           (if email [:a {:class "card-footer-item"
                          :title "Contacter par email"
                          :href  (str "mailto:" email)}
                      (fa "fa-envelope")])
           (if site_web [:a {:class  "card-footer-item"
                             :title  "Visiter le site web"
                             :target "new"
                             :href   site_web} (fa "fa-link")])]]])])))

(defn repositories-page []
  [:table {:class "table is-hoverable is-fullwidth"}
   [:thead
    [:tr
     [:th [:button {:on-click (fn [] (re-frame/dispatch [:sort-by! :name]))} "Nom"]]
     [:th [:button {:on-click (fn [] (re-frame/dispatch [:sort-by! :desc]))} "Description"]]
     [:th [:button {:on-click (fn [] (re-frame/dispatch [:sort-by! :forks]))} "Forks"]]
     [:th [:button {:on-click (fn [] (re-frame/dispatch [:sort-by! :stars]))} "Stars"]]
     [:th [:button {:on-click (fn [] (re-frame/dispatch [:sort-by! :issues]))} "Issues"]]]]
   [:tbody
    (for [d (take pages
                  (drop (* pages @(re-frame/subscribe [:repos-page]))
                        @(re-frame/subscribe [:repos])))]
      ^{:key d}
      [:tr
       [:td [:a {:href (:repertoire_url d)} (:nom d)]]
       [:td (:description d)]
       [:td (:nombre_forks d)]
       [:td (:nombre_stars d)]
       [:td (:nombre_issues_ouvertes d)]])]])

(defn change-page [next]
  (let [repos-page  @(re-frame/subscribe [:repos-page])
        count-pages (count (partition-all pages @(re-frame/subscribe [:repos])))]
    (cond
      (= next "first")
      (re-frame/dispatch [:repos-page! 0])
      (= next "last")
      (re-frame/dispatch [:repos-page! (dec count-pages)])
      (and (< repos-page (dec count-pages)) next)
      (re-frame/dispatch [:repos-page! (inc repos-page)])
      (and (> repos-page 0) (not next))
      (re-frame/dispatch [:repos-page! (dec repos-page)]))))

(defn main-page []
  [:div
   [:div {:class "columns"}
    [:div {:class "column"}
     [:a {:class "button" :on-click #(re-frame/dispatch [:view! :repos])} "Dépôts"]]
    [:div {:class "column"}
     [:a {:class "button" :on-click #(re-frame/dispatch [:view! :orgas])} "Organisations"]]
    [:div {:class "column"}
     [:a {:class "button"} "Chiffres"]]
    [:div {:class "column is-two-thirds"}
     [:input {:class     "input"
              :on-change (fn [e]                           
                           (let [ev (.-value (.-target e))]
                             (async/go (async/>! search-filter-chan ev))))}]]
    [:div {:class "column"}
     [:a {:class "button" :href "latest.xml" :title "Flux RSS des derniers dépôts"}
      (fa "fa-rss")]]]
   (if  (or (= @(re-frame/subscribe [:view]) :repos)
            (not (empty? @(re-frame/subscribe [:filter]))))
     (let [repos-pages    @(re-frame/subscribe [:repos-page])
           count-pages    (count (partition-all pages @(re-frame/subscribe [:repos])))
           first-disabled (= repos-pages 0)
           last-disabled  (= repos-pages (dec count-pages))]
       [:div {:class "level-right"}
        [:nav {:class "pagination level-item" :role "navigation" :aria-label "pagination"}
         [:a {:class    "pagination-previous"
              :on-click #(change-page "first")
              :disabled first-disabled}
          (fa "fa-fast-backward")]
         [:a {:class    "pagination-previous"
              :on-click #(change-page nil)
              :disabled first-disabled}
          (fa "fa-step-backward")]
         [:a {:class    "pagination-next"
              :on-click #(change-page true)
              :disabled last-disabled}
          (fa "fa-step-forward")]
         [:a {:class    "pagination-next"
              :on-click #(change-page "last")
              :disabled last-disabled}
          (fa "fa-fast-forward")]]]))
   [:br]
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
  (start-search-filter-loop)
  (reagent/render
   [main-class]
   (. js/document (getElementById "app"))))

