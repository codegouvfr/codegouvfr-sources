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
(defonce stats-url "https://api-codes-sources-fr.antoine-augusti.fr/api/stats/general")
(def pages 200) ;; FIXME: customizable?

(re-frame/reg-event-db
 :initialize-db!
 (fn [_ _]      
   {:repos           nil
    :repos-page      0
    :orgas           nil
    :sort-by         :stars
    :view            :repos
    :reverse-sort    true
    :has-description false
    :is-fork         false
    :is-licensed     false
    :lang-filter     ""
    :stats           nil
    :filter          ""}))

(re-frame/reg-event-db
 :update-repos!
 (fn [db [_ repos]]
   (if repos (assoc db :repos repos))))

(re-frame/reg-event-db
 :update-stats!
 (fn [db [_ stats]]
   (if stats (assoc db :stats stats))))

(re-frame/reg-event-db
 :filter!
 (fn [db [_ s]]
   (re-frame/dispatch [:repos-page! 0])
   (assoc db :filter s)))

(re-frame/reg-event-db
 :lang-filter!
 (fn [db [_ s]]
   (re-frame/dispatch [:repos-page! 0])
   (assoc db :lang-filter s)))

(re-frame/reg-event-db
 :repos-page!
 (fn [db [_ n]]
   (assoc db :repos-page n)))

(re-frame/reg-event-db
 :has-description!
 (fn [db [_ n]]
   (assoc db :has-description n)))

(re-frame/reg-event-db
 :view!
 (fn [db [_ view]]
   (re-frame/dispatch [:lang-filter! ""])
   (re-frame/dispatch [:filter! ""])
   (re-frame/dispatch [:repos-page! 0])
   (assoc db :view view)))

(re-frame/reg-event-db
 :is-fork!
 (fn [db [_ b]]
   (re-frame/dispatch [:repos-page! 0])
   (assoc db :is-fork b)))

(re-frame/reg-event-db
 :is-licensed!
 (fn [db [_ b]]
   (re-frame/dispatch [:repos-page! 0])
   (assoc db :is-licensed b)))

(re-frame/reg-sub
 :lang-filter?
 (fn [db _] (:lang-filter db)))

(re-frame/reg-sub
 :has-description?
 (fn [db _] (:has-description db)))

(re-frame/reg-sub
 :is-fork?
 (fn [db _] (:is-fork db)))

(re-frame/reg-sub
 :is-licensed?
 (fn [db _] (:is-licensed db)))

(re-frame/reg-event-db
 :update-orgas!
 (fn [db [_ orgas]]
   (if orgas (assoc db :orgas orgas))))

(re-frame/reg-sub
 :sort-by?
 (fn [db _] (:sort-by db)))

(re-frame/reg-sub
 :repos-page?
 (fn [db _] (:repos-page db)))

(re-frame/reg-sub
 :filter?
 (fn [db _] (:filter db)))

(re-frame/reg-sub
 :view?
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

(defn apply-search-filter [m s ks]
  (if (empty? s) m ;; Filter string is empty, return the map
      (filter #(re-find (re-pattern (str "(?i)" s))
                        (clojure.string/join
                         " " (vals (select-keys % ks))))
              m)))

(defn apply-description-filter [m]
  (if @(re-frame/subscribe [:has-description?])
    (filter #(not (empty? (:description %))) m)
    m))

(defn apply-license-filter [m]
  (if @(re-frame/subscribe [:is-licensed?])
    (filter #(let [l (:licence %)]
               (and l (not (= l "Other"))))
            m)
    m))

(defn apply-fork-filter [m]
  (if @(re-frame/subscribe [:is-fork?])
    (filter #(:est_fork %) m)
    m))

(defn apply-lang-filter [m l]
  (filter #(re-find (re-pattern (str "(?i)" l))
                    (or (:langage %) ""))
          m))

(apply-lang-filter '({:langage "python"}) "")

(def search-filter-chan (async/chan 10))

(defn start-search-filter-loop []
  (async/go
    (loop [s (async/<! search-filter-chan)]
      (re-frame/dispatch [:filter! s])
      (recur (async/<! search-filter-chan)))))

(def lang-filter-chan (async/chan 10))

(defn start-lang-filter-loop []
  (async/go
    (loop [s (async/<! lang-filter-chan)]
      (re-frame/dispatch [:lang-filter! s])
      (recur (async/<! lang-filter-chan)))))

(re-frame/reg-sub
 :stats?
 (fn [db _]
   (:stats db)))

(re-frame/reg-sub
 :repos?
 (fn [db _]
   (let [lang  @(re-frame/subscribe [:lang-filter?])
         reps  (:repos db)
         repos (case @(re-frame/subscribe [:sort-by?])
                 :name   (sort-by :nom reps)
                 :forks  (sort-by :nombre_forks reps)
                 :stars  (sort-by :nombre_stars reps)
                 :issues (sort-by :nombre_issues_ouvertes reps)
                 :date   (sort #(compare (js/Date. (.parse js/Date (:derniere_mise_a_jour %1)))
                                         (js/Date. (.parse js/Date (:derniere_mise_a_jour %2))))
                               reps)
                 ;; FIXME: intuitive enough to sort by length of desc?
                 :desc   (sort #(compare (count (:description %1))
                                         (count (:description %2)))
                               reps) 
                 reps)]
     (apply-description-filter
      (apply-license-filter
       (apply-fork-filter
        (apply-lang-filter
         (apply-search-filter
          (if @(re-frame/subscribe [:reverse-sort])
            (reverse repos)
            repos)
          @(re-frame/subscribe [:filter?])
          [:description :nom :topics]) ;; FIXME: Other fields?
         lang)))))))

(re-frame/reg-sub
 :orgas?
 (fn [db _]
   (let [orgs  (:orgas db)
         orgas (case @(re-frame/subscribe [:sort-by?])
                 :name (sort #(compare (or-kwds %1 [:nom :login])
                                       (or-kwds %2 [:nom :login]))
                             orgs)
                 ;; FIXME: intuitive enough to sort by length of desc?
                 :desc (sort #(compare (count (:description %1))
                                       (count (:description %2)))
                             orgs) 
                 orgs)]
     (apply-search-filter
      orgas
      @(re-frame/subscribe [:filter?])
      [:description :nom :login]))))

(defn repositories-page []
  [:table {:class "table is-hoverable is-fullwidth"}
   [:thead
    [:tr
     [:th [:a {:class    "button"
               :title    "Trier par ordre alphabétique des noms"
               :on-click #(re-frame/dispatch [:sort-by! :name])} "Nom"]]
     [:th [:a {:class    "button"
               :title    "Trier par longueur de description"
               :on-click #(re-frame/dispatch [:sort-by! :desc])} "Description"]]
     [:th [:a {:class    "button"
               :title    "Trier par date de mise à jour"
               :on-click #(re-frame/dispatch [:sort-by! :date])} "MàJ"]]
     [:th [:a {:class    "button"
               :title    "Trier par nombre de fourches"
               :on-click #(re-frame/dispatch [:sort-by! :forks])} "Fourches"]]
     [:th [:a {:class    "button"
               :title    "Trier par nombre d'étoiles"
               :on-click #(re-frame/dispatch [:sort-by! :stars])} "Étoiles"]]
     [:th [:a {:class    "button"
               :title    "Trier par nombre de tickets"
               :on-click #(re-frame/dispatch [:sort-by! :issues])} "Tickets"]]]]
   (into [:tbody]
         (for [d (take pages (drop (* pages @(re-frame/subscribe [:repos-page?]))
                                   @(re-frame/subscribe [:repos?])))]
           ^{:key d}
           (let [{:keys [licence]} d]
             [:tr
              [:td [:a {:href   (:repertoire_url d)
                        :target "new"
                        :title  (str (:organisation_nom d)
                                     (if licence (str " / Licence : " licence)))}
                    (:nom d)]]
              [:td (:description d)]
              [:td (to-locale-date (:derniere_mise_a_jour d))]
              [:td (:nombre_forks d)]
              [:td (:nombre_stars d)]
              [:td (:nombre_issues_ouvertes d)]])))])

(defn organizations-page []
  (into
   [:div]
   (for [d (partition-all 3 @(re-frame/subscribe [:orgas?]))]
     ^{:key d}
     [:div {:class "columns"}
      (for [{:keys [nom login organisation_url site_web
                    date_creation description nombre_repertoires email
                    avatar_url]
             :as   o} d]
        ^{:key o}
        [:div {:class "column is-4"}
         [:div {:class "card"}
          [:div {:class "card-content"}
           [:div {:class "media"}
            (if avatar_url
              [:div {:class "media-left"}
               [:figure {:class "image is-48x48"}
                [:img {:src avatar_url}]]])
            [:div {:class "media-content"}
             [:p [:a {:class  "title is-4"
                      :target "new"
                      :title  "Visiter le compte d'organisation"
                      :href   organisation_url} (or nom login)]]
             [:p {:class "subtitle is-6"}
              (str "Créé le " (to-locale-date date_creation))]]]
           [:div {:class "content"}
            [:p description]]]
          [:div {:class "card-footer"}
           (if nombre_repertoires
             [:div {:class "card-footer-item"
                    :title "Nombre de dépôts"}
              nombre_repertoires])
           (if email [:a {:class "card-footer-item"
                          :title "Contacter par email"
                          :href  (str "mailto:" email)}
                      (fa "fa-envelope")])
           (if site_web [:a {:class  "card-footer-item"
                             :title  "Visiter le site web"
                             :target "new"
                             :href   site_web} (fa "fa-link")])]]])])))

(defn figure [heading title]
  [:div {:class "level-item has-text-centered"}
   [:div
    [:p {:class "heading"} heading]
    [:p {:class "title"} (str title)]]])

(defn stats-card [heading data]
  [:div {:class "column"}
   [:div {:class "card"}
    [:h1 {:class "card-header-title subtitle"} heading]
    [:div {:class "card-content"}
     [:table {:class "table is-fullwidth"}
      [:tbody
       (for [o (reverse (clojure.walk/stringify-keys (sort-by val data)))]
         ^{:key (key o)}
         [:tr [:td (key o)] [:td (val o)]])]]]]])

(defn stats-page []
  (let [{:keys [nb_repos nb_orgs avg_nb_repos median_nb_repos
                top_orgs_by_repos top_orgs_by_stars top_licenses]
         :as   stats}
        @(re-frame/subscribe [:stats?])]
    [:div
     [:div {:class "level"}
      (figure "Dépôts" nb_repos)
      (figure "Organisations" nb_orgs)
      (figure "Moyenne des dépôts par organisation" avg_nb_repos)
      (figure "Médiane des dépôts par organisation" median_nb_repos)]
     [:br]
     [:div {:class "columns"}
      (stats-card "Organisations avec le plus de dépôts" top_orgs_by_repos)
      (stats-card "Organisations les plus étoilées" top_orgs_by_stars)]
     [:div {:class "columns"}
      (stats-card "Licences les plus utilisées" top_licenses)]]))

(defn about-page []
  [:div
   [:div {:class "container"}
    [:h1 {:class "title"} "Codes sources ?"]
    [:p "Le code source d'un programme informatique est ce qu'écrit une programmeuse ou un programmeur.  Il peut s'agir de programmes complexes ou de quelques lignes."]
    [:p "Ce code source peut être partagé sous licence libre pour permettre aux autres programmeurs de l'étudier, de le modifier, de le diffuser et de partager leurs améliorations."]
    [:br]]
   [:div {:class "container"}
    [:h1 {:class "title"} "Secteur public ?"]
    [:p "Les codes sources développés dans le cadre de missions de service public ont vocation à être publiés, dans certains conditions."]
    [:p "Ce site propose de chercher dans l'ensemble des codes sources aujourd'hui identifiés comme provenant d'un organisme public."]
    [:p "Il a été développé par " [:a {:target "new" :href "la mission Etalab."} "la mission Etalab."]]
    [:br]]
   [:div {:class "container"}
    [:h1 {:class "title"} "Que puis-je faire ?"]
    [:p [:strong "Vous êtes un organisme public ?"] " Avant de développer du code source par vous-même, vous pouvez chercher si du code déjà développé n'est pas public ici. Vous pouvez aussi repérer des projets qui vous intéressent pour vous rapprocher des organismes porteurs et leur demander comment contribuer."]
    [:br]
    [:p [:strong "Vous vous y connaissez en code ?"] " Vous pouvez trouver des projets qui vous intéressent et contribuer."]
    [:br]]
   [:div {:class "container"}
    [:h1 {:class "title"} "Une question ?"]
    [:p "Pour suivre l'actualité des logiciels libres utilisés et produits par l'administration, inscrivez-vous à la " [:a {:href "gazette #bluehats" :target "new"} "gazette #bluehats."]]
    [:p "Pour toute autre question, n'hésitez pas à écrire à " [:a {:href "mailto:bastien.guerry@data.gouv.fr"} "Bastien Guerry."]]]])

(defn change-page [next]
  (let [repos-page  @(re-frame/subscribe [:repos-page?])
        count-pages (count (partition-all pages @(re-frame/subscribe [:repos?])))]
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
   [:div {:class "field is-grouped"}
    [:p {:class "control"}
     [:a {:class "button" :href "latest.xml" :title "Flux RSS des derniers dépôts"}
      (fa "fa-rss")]]
    [:p {:class "control"}
     [:a {:class    "button is-link"
          :on-click #(re-frame/dispatch [:view! :repos])} "Dépôts"]]
    [:p {:class "control"}
     [:a {:class    "button is-danger"
          :on-click #(re-frame/dispatch [:view! :orgas])} "Organisations"]]
    [:p {:class "control"}
     [:a {:class    "button is-info"
          :on-click #(re-frame/dispatch [:view! :stats])} "Chiffres"]]
    [:p {:class "control"}
     [:a {:class    "button is-warning"
          :on-click #(re-frame/dispatch [:view! :about])} "À propos"]]]
   [:br]
   (cond
     (= @(re-frame/subscribe [:view?]) :repos)
     (let [repos-pages    @(re-frame/subscribe [:repos-page?])
           count-pages    (count (partition-all pages @(re-frame/subscribe [:repos?])))
           first-disabled (= repos-pages 0)
           last-disabled  (= repos-pages (dec count-pages))]
       [:div {:class "level-left"}        
        [:label {:class "checkbox level-item"}
         [:input {:type      "checkbox"
                  :on-change #(re-frame/dispatch [:is-fork! (.-checked (.-target %))])}]
         " Fourches seules"]
        [:label {:class "checkbox level-item"}
         [:input {:type      "checkbox"
                  :on-change #(re-frame/dispatch [:has-description! (.-checked (.-target %))])}]
         " Avec description"]        
        [:label {:class "checkbox level-item"}
         [:input {:type      "checkbox"
                  :on-change #(re-frame/dispatch [:is-licensed! (.-checked (.-target %))])}]
         " Avec licence identifiée"]
        [:div {:class "level-item"}
         [:input {:class       "input"
                  :size        10
                  :placeholder "Langage"
                  :on-change   (fn [e]                           
                                 (let [ev (.-value (.-target e))]
                                   (async/go (async/>! lang-filter-chan ev))))}]]
        [:div {:class "level-item"}
         [:input {:class       "input"
                  :size        20
                  :placeholder "Recherche libre"
                  :on-change   (fn [e]                           
                                 (let [ev (.-value (.-target e))]
                                   (async/go (async/>! search-filter-chan ev))))}]]
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
          (fa "fa-fast-forward")]]])
     (= @(re-frame/subscribe [:view?]) :orgas)
     [:div {:class "level-left"}
      [:div {:class "level-item"}
       [:input {:class       "input"
                :placeholder "Recherche libre"
                :on-change   (fn [e]                           
                               (let [ev (.-value (.-target e))]
                                 (async/go (async/>! search-filter-chan ev))))}]]])
   [:br]
   (case @(re-frame/subscribe [:view?])
     :repos [repositories-page]
     :stats [stats-page]
     :about [about-page]
     :orgas [organizations-page])])

(defn main-class []
  (reagent/create-class
   {:component-will-mount
    (fn []
      (GET repos-url :handler
           #(re-frame/dispatch
             [:update-repos! (map (comp bean clj->js) %)]))
      (GET orgas-url :handler
           #(re-frame/dispatch
             [:update-orgas! (map (comp bean clj->js) %)]))
      (GET stats-url :handler
           #(re-frame/dispatch
             [:update-stats! (clojure.walk/keywordize-keys %)])))
    :reagent-render main-page}))

(defn ^:export init []
  (re-frame/clear-subscription-cache!)
  (re-frame/dispatch-sync [:initialize-db!])
  (start-search-filter-loop)
  (start-lang-filter-loop)
  (reagent/render
   [main-class]
   (. js/document (getElementById "app"))))
