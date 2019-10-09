;; Copyright (c) 2019 DINSIC, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.core
  (:require [cljs.core.async :as async]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.session :as session]
            [cljs-bean.core :refer [bean]]
            [ajax.core :refer [GET POST]]
            [markdown-to-hiccup.core :as md]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]))

(defonce repos-url "https://api-code.etalab.gouv.fr/api/repertoires/all")
(defonce orgas-url "https://api-code.etalab.gouv.fr/api/organisations/all")
(defonce stats-url "https://api-code.etalab.gouv.fr/api/stats/general")
(def pages 200) ;; FIXME: Make customizable?
(def init-filter {:lang "" :licence "" :search "" :search-orgas "" :has-at-least-one-repo true})

(re-frame/reg-event-db
 :initialize-db!
 (fn [_ _]
   {:repos         nil
    :repos-page    0
    :orgas         nil
    :sort-repos-by :date
    :sort-orgas-by :repos
    :view          :repos
    :reverse-sort  true
    :stats         nil
    :filter        init-filter}))

(re-frame/reg-event-db
 :update-repos!
 (fn [db [_ repos]] (if repos (assoc db :repos repos))))

(re-frame/reg-event-db
 :update-stats!
 (fn [db [_ stats]] (if stats (assoc db :stats stats))))

(re-frame/reg-event-db
 :filter!
 (fn [db [_ s]]
   (re-frame/dispatch [:repos-page! 0])
   ;; FIXME: Find a more idiomatic way?
   (assoc db :filter (merge (:filter db) s))))

(re-frame/reg-event-db
 :repos-page!
 (fn [db [_ n]] (assoc db :repos-page n)))

(re-frame/reg-event-db
 :view!
 (fn [db [_ view query-params]]
   (re-frame/dispatch [:repos-page! 0])
   (re-frame/dispatch [:filter! (merge init-filter query-params)])
   (assoc db :view view)))

(re-frame/reg-event-db
 :update-orgas!
 (fn [db [_ orgas]] (if orgas (assoc db :orgas orgas))))

(re-frame/reg-sub
 :sort-repos-by?
 (fn [db _] (:sort-repos-by db)))

(re-frame/reg-sub
 :sort-orgas-by?
 (fn [db _] (:sort-orgas-by db)))

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
 :reverse-sort?
 (fn [db _] (:reverse-sort db)))

(re-frame/reg-event-db
 :sort-repos-by!
 (fn [db [_ k]]
   (re-frame/dispatch [:repos-page! 0])
   (when (= k (:sort-repos-by db))
     (re-frame/dispatch [:reverse-sort!]))
   (assoc db :sort-repos-by k)))

(re-frame/reg-event-db
 :sort-orgas-by!
 (fn [db [_ k]]
   (when (= k (:sort-orgas-by db))
     (re-frame/dispatch [:reverse-sort!]))
   (assoc db :sort-orgas-by k)))

(re-frame/reg-event-db
 :reverse-sort!
 (fn [db _] (assoc db :reverse-sort (not (:reverse-sort db)))))

(defn or-kwds [m ks]
  (first (remove nil? (map #(apply % [m]) ks))))

(defn fa [s]
  [:span {:class "icon"}
   [:i {:class (str "fas " s)}]])

(defn fab [s]
  [:span {:class "icon"}
   [:i {:class (str "fab " s)}]])

(defn to-locale-date [s]
  (if (string? s)
    (.toLocaleDateString
     (js/Date. (.parse js/Date s)))))

;; FIXME: Also escape [ and ] characters
(defn escape-search-string [s]
  (clojure.string/replace s #"[.*+?^${}()|]" "\\$&"))

(defn apply-repos-filters [m]
  (let [f   @(re-frame/subscribe [:filter?])
        s   (:search f)
        o   (:search-orgas f)
        la  (:lang f)
        lic (:licence f)
        de  (:has-description f)
        fk  (:is-fork f)
        ar  (:is-archive f)
        li  (:is-licensed f)]
    (filter
     #(and (if fk (:est_fork %) true)
           (if ar (not (:est_archive %)) true)
           (if li (let [l (:licence %)]
                    (and l (not (= l "Other")))) true)
           (if lic
             (cond (= lic "Inconnue")
                   (not li)
                   (re-find (re-pattern (str "(?i)" lic))
                            (or (:licence %) ""))
                   true))
           (if de (seq (:description %)) true)
           (if o (re-find (re-pattern (str "(?i)" o))
                          (or (:repertoire_url %) "")) true)
           (if la (re-find (re-pattern (str "(?i)" la))
                           (or (:langage %) "")) true)
           (if s (re-find (re-pattern (str "(?i)" s))
                          (clojure.string/join
                           " " [(:nom %) (:login %)
                                (:organisation_nom %)
                                (:description %)]))))
     m)))

(defn apply-orgas-filters [m]
  (let [f  @(re-frame/subscribe [:filter?])
        s  (:search f)
        de (:has-description f)
        re (:has-at-least-one-repo f)]
    (filter
     #(and (if de (seq (:description %)) true)
           (if re (> (:nombre_repertoires %) 0) true)
           (if s (re-find (re-pattern (str "(?i)" s))
                          (clojure.string/join
                           " " [(:nom %) (:login %)
                                (:description %)
                                (:site_web %)
                                (:organisation_url %)]))))
     m)))

(def filter-chan (async/chan 10))

(defn start-filter-loop []
  (async/go
    (loop [f (async/<! filter-chan)]
      (re-frame/dispatch [:filter! f])
      (recur (async/<! filter-chan)))))

(re-frame/reg-sub
 :stats?
 (fn [db _] (:stats db)))

(re-frame/reg-sub
 :repos?
 (fn [db _]
   (let [repos0 (:repos db)
         repos  (case @(re-frame/subscribe [:sort-repos-by?])
                  :name   (sort-by :nom repos0)
                  :forks  (sort-by :nombre_forks repos0)
                  :stars  (sort-by :nombre_stars repos0)
                  :issues (sort-by :nombre_issues_ouvertes repos0)
                  :date   (sort #(compare (js/Date. (.parse js/Date (:derniere_mise_a_jour %1)))
                                          (js/Date. (.parse js/Date (:derniere_mise_a_jour %2))))
                                repos0)
                  :desc   (sort #(compare (count (:description %1))
                                          (count (:description %2)))
                                repos0)
                  repos0)]
     (apply-repos-filters (if @(re-frame/subscribe [:reverse-sort?])
                            (reverse repos)
                            repos)))))

(re-frame/reg-sub
 :orgas?
 (fn [db _]
   (let [orgs  (:orgas db)
         orgas (case @(re-frame/subscribe [:sort-orgas-by?])
                 :repos (sort-by :nombre_repertoires orgs)
                 :date  (sort #(compare (js/Date. (.parse js/Date (:date_creation %1)))
                                        (js/Date. (.parse js/Date (:date_creation %2))))
                              orgs)
                 :name  (sort #(compare (or-kwds %1 [:nom :login])
                                        (or-kwds %2 [:nom :login]))
                              orgs)
                 orgs)]
     (apply-orgas-filters (if @(re-frame/subscribe [:reverse-sort?])
                            (reverse orgas)
                            orgas)))))

(defn repositories-page []
  (let [rep-f @(re-frame/subscribe [:sort-repos-by?])]
    [:div {:class "table-container"}
     [:table {:class "table is-hoverable is-fullwidth"}
      [:thead
       [:tr
        [:th [:abbr {:title "Organisation / dépôt"}
              [:a {:class    (str "button" (when (= rep-f :name) " is-light"))
                   :title    "Trier par ordre alphabétique des noms de dépôts"
                   :on-click #(re-frame/dispatch [:sort-repos-by! :name])} "Organisation / dépôt"]]]
        [:th [:abbr {:title "Archive"}
              [:a {:class "button is-static"
                   :title "Lien vers l'archive faite par Software Heritage"} "Archive"]]]
        [:th [:abbr {:title "Description"}
              [:a {:class    (str "button" (when (= rep-f :desc) " is-light"))
                   :title    "Trier par longueur de description"
                   :on-click #(re-frame/dispatch [:sort-repos-by! :desc])} "Description"]]]
        [:th [:abbr {:title "Mise à jour"}
              [:a {:class    (str "button" (when (= rep-f :date) " is-light"))
                   :title    "Trier par date de mise à jour"
                   :on-click #(re-frame/dispatch [:sort-repos-by! :date])} "MàJ"]]]
        [:th [:abbr {:title "Fourches"}
              [:a {:class    (str "button" (when (= rep-f :forks) " is-light"))
                   :title    "Trier par nombre de fourches"
                   :on-click #(re-frame/dispatch [:sort-repos-by! :forks])} "Fourches"]]]
        [:th [:abbr {:title "Étoiles"}
              [:a {:class    (str "button" (when (= rep-f :stars) " is-light"))
                   :title    "Trier par nombre d'étoiles"
                   :on-click #(re-frame/dispatch [:sort-repos-by! :stars])} "Étoiles"]]]
        [:th [:abbr {:title "Tickets"}
              [:a {:class    (str "button" (when (= rep-f :issues) " is-light"))
                   :title    "Trier par nombre de tickets"
                   :on-click #(re-frame/dispatch [:sort-repos-by! :issues])} "Tickets"]]]]]
      (into [:tbody]
            (for [d (take pages (drop (* pages @(re-frame/subscribe [:repos-page?]))
                                      @(re-frame/subscribe [:repos?])))]
              ^{:key d}
              (let [{:keys [licence repertoire_url nom organisation_nom software_heritage_url
                            description derniere_mise_a_jour nombre_forks nombre_stars
                            nombre_issues_ouvertes]} d]
                [:tr
                 [:td [:div
                       [:a {:href  (rfe/href :repos nil {:search-orgas (subs repertoire_url 0
                                                                             (- (count repertoire_url)
                                                                                (+ 1 (count nom))))})
                            :title "Voir la liste des dépôts de cette organisation"}
                        organisation_nom]
                       " / "
                       [:a {:href   repertoire_url
                            :target "new"
                            :title  (str "Voir ce dépôt" (if licence (str " sous licence " licence)))}
                        (:nom d)]]]
                 [:td {:class "has-text-centered"}
                  [:a {:href   software_heritage_url
                       :title  "Lien vers l'archive faite par Software Heritage"
                       :target "new"}
                   [:img {:width "18px" :src "/images/swh-logo.png"}]]]
                 [:td description]
                 [:td (or (to-locale-date derniere_mise_a_jour) "N/A")]
                 [:td {:class "has-text-right"} nombre_forks]
                 [:td {:class "has-text-right"} nombre_stars]
                 [:td {:class "has-text-right"} nombre_issues_ouvertes]])))]]))

(defn organizations-page []
  (into
   [:div]
   (for [d (partition-all 3 @(re-frame/subscribe [:orgas?]))]
     ^{:key d}
     [:div {:class "columns"}
      (for [{:keys [nom login organisation_url site_web
                    date_creation description nombre_repertoires email
                    avatar_url plateforme]
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
             [:p
              [:a {:class  "title is-4"
                   :target "new"
                   :title  "Visiter le compte d'organisation"
                   :href   organisation_url} (or nom login)]]
             (let [d (to-locale-date date_creation)]
               (if d
                 [:p {:class "subtitle is-6"}
                  (str "Créé le " d)]))]]
           [:div {:class "content"}
            [:p description]]]
          [:div {:class "card-footer"}
           (if nombre_repertoires
             [:div {:class "card-footer-item"
                    :title "Nombre de dépôts"}
              [:a {:title "Voir les dépôts"
                   :href  (rfe/href :repos nil {:search-orgas organisation_url})}
               nombre_repertoires
               (if (= nombre_repertoires 1)
                 " dépôt" " dépôts")]])
           (cond (= plateforme "GitHub")
                 [:a {:class "card-footer-item"
                      :title "Visiter sur GitHub"
                      :href  organisation_url}
                  (fab "fa-github")]
                 (= plateforme "GitLab")
                 [:a {:class "card-footer-item"
                      :title "Visiter le groupe sur l'instance GitLab"
                      :href  organisation_url}
                  (fab "fa-gitlab")])
           (if email [:a {:class "card-footer-item"
                          :title "Contacter par email"
                          :href  (str "mailto:" email)}
                      (fa "fa-envelope")])
           (if site_web [:a {:class  "card-footer-item"
                             :title  "Visiter le site web"
                             :target "new"
                             :href   site_web} (fa "fa-globe")])]]])])))

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
                top_orgs_by_repos top_orgs_by_stars top_licenses
                platforms software_heritage]
         :as   stats} @(re-frame/subscribe [:stats?])
        top_orgs_by_repos_0
        (into {} (map #(vector (str (:organisation_nom %)
                                    " (" (:plateforme %) ")")
                               (:count %))
                      top_orgs_by_repos))
        top_licenses_0
        (into {} (map #(let [[k v] %] [[:a {:href (str "/?licence=" k)} k] v])
                      (clojure.walk/stringify-keys top_licenses)))]
    [:div
     [:div {:class "level"}
      (figure [:span [:a {:href  "/glossaire#depot"
                          :title "Voir le glossaire"} "Dépôts de "]
               [:a {:href  "/glossaire#code-source"
                    :title "Voir le glossaire"}
                "code source"]] nb_repos)
      (figure [:span [:a {:href  "/glossaire#organisation-groupe"
                          :title "Voir le glossaire"}
                      "Organisations ou groupes"]] nb_orgs)
      (figure "Nombre moyen de dépôts par organisation/groupe" avg_nb_repos)
      (figure "Nombre médian de dépôts par organisation/groupe" median_nb_repos)]
     [:br]
     [:div {:class "columns"}
      (stats-card [:span [:a {:href  "/glossaire#organisation-groupe"
                              :title "Voir le glossaire"} "Organisations/groupes"]
                   " avec le plus de "
                   [:a {:href  "/glossaire#depot"
                        :title "Voir le glossaire"} "dépôts"]] top_orgs_by_repos_0)
      (stats-card "Organisations/groupes les plus étoilés" top_orgs_by_stars)]
     [:div {:class "columns"}
      (stats-card [:span [:a {:href  "/glossaire#licence"
                              :title "Voir le glossaire"} "Licences"]
                   " les plus utilisées"]
                  top_licenses_0)]
     [:div {:class "columns"}
      (stats-card "Répartition par plateformes" platforms)
      (stats-card [:span "Sauvegarde sur "
                   [:a {:href  "/glossaire#software-heritage"
                        :title "Voir le glossaire"}
                    "Software Heritage"]]
                  {"Dépôts dans Software Heritage"
                   (:repos_in_archive software_heritage)
                   "Proportion de dépôts archivés"
                   (:ratio_in_archive software_heritage)})]]))

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
    ;; FIXME: why :p here? Use level?
    [:p {:class "control"}
     [:a {:class "button is-success"
          :href  (rfe/href :repos)} "Dépôts de code source"]]
    [:p {:class "control"}
     [:a {:class "button is-danger"
          :title "Les comptes d'organisation GitHub ou groupes GitLab"
          :href  (rfe/href :orgas)} "Organisations ou groupes"]]
    [:p {:class "control"}
     [:a {:class "button is-info"
          :href  (rfe/href :stats)} "Chiffres"]]
    [:p {:class "control"}
     [:input {:class       "input"
              :size        20
              :placeholder "Recherche libre"
              :on-change   (fn [e]
                             (let [ev0 (.-value (.-target e))
                                   ev  (escape-search-string ev0)]
                               (async/go (async/>! filter-chan {:search ev}))))}]]
    (let [flt @(re-frame/subscribe [:filter?])]
      (if (seq (:search-orgas flt))
        [:p {:class "control"}
         [:a {:class "button is-outlined is-warning"
              :title "Supprimer le filtre : voir toutes les organisations ou groupes"
              :href  (rfe/href :repos)}
          [:span (:search-orgas flt)]
          (fa "fa-times")]]))]
   [:br]
   (cond
     (= @(re-frame/subscribe [:view?]) :repos)
     (let [repos          @(re-frame/subscribe [:repos?])
           repos-pages    @(re-frame/subscribe [:repos-page?])
           count-pages    (count (partition-all pages repos))
           first-disabled (= repos-pages 0)
           last-disabled  (= repos-pages (dec count-pages))]
       [:div {:class "level-left"}
        [:div {:class "level-item"}
         [:input {:class       "input"
                  :size        12
                  :placeholder "Licence"
                  :on-change   (fn [e]
                                 (let [ev0 (.-value (.-target e))
                                       ev  (escape-search-string ev0)]
                                   (async/go (async/>! filter-chan {:licence ev}))))}]]
        [:div {:class "level-item"}
         [:input {:class       "input"
                  :size        12
                  :placeholder "Langage"
                  :on-change   (fn [e]
                                 (let [ev0 (.-value (.-target e))
                                       ev  (escape-search-string ev0)]
                                   (async/go (async/>! filter-chan {:lang ev}))))}]]
        [:label {:class "checkbox level-item" :title "Que les dépôts fourchés d'autres dépôts"}
         [:input {:type      "checkbox"
                  :on-change #(re-frame/dispatch [:filter! {:is-fork (.-checked (.-target %))}])}]
         " Fourches seules"]
        [:label {:class "checkbox level-item" :title "Ne pas inclure les dépôts archivés"}
         [:input {:type      "checkbox"
                  :on-change #(re-frame/dispatch [:filter! {:is-archive (.-checked (.-target %))}])}]
         " Sauf archives"]
        [:label {:class "checkbox level-item" :title "Que les dépôts ayant une description"}
         [:input {:type      "checkbox"
                  :on-change #(re-frame/dispatch [:filter! {:has-description (.-checked (.-target %))}])}]
         " Avec description"]
        [:label {:class "checkbox level-item" :title "Que les dépôts ayant une licence identifiée"}
         [:input {:type      "checkbox"
                  :on-change #(re-frame/dispatch [:filter! {:is-licensed (.-checked (.-target %))}])}]
         " Avec licence identifiée"]
        [:span {:class "button is-static level-item"}
         (let [rps (count repos)]
           (if (= rps 1) "1 dépôt" (str rps " dépôts")))]
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
     (let [org-f @(re-frame/subscribe [:sort-orgas-by?])
           orgas @(re-frame/subscribe [:orgas?])]
       [:div {:class "level-left"}
        [:label {:class "checkbox level-item" :title "Que les organisations ayant publié du code"}
         [:input {:type      "checkbox"
                  :checked   (:has-at-least-one-repo @(re-frame/subscribe [:filter?]))
                  :on-change #(re-frame/dispatch [:filter! {:has-at-least-one-repo
                                                            (.-checked (.-target %))}])}]
         " Avec du code publié"]
        [:a {:class    (str "button level-item is-" (if (= org-f :name) "warning" "light"))
             :title    "Trier par ordre alphabétique des noms d'organisations ou de groupes"
             :on-click #(re-frame/dispatch [:sort-orgas-by! :name])} "Par ordre alphabétique"]
        [:a {:class    (str "button level-item is-" (if (= org-f :repos) "warning" "light"))
             :title    "Trier par nombre de dépôts"
             :on-click #(re-frame/dispatch [:sort-orgas-by! :repos])} "Par nombre de dépôts"]
        [:a {:class    (str "button level-item is-" (if (= org-f :date) "warning" "light"))
             :title    "Trier par date de création de l'organisation ou du groupe"
             :on-click #(re-frame/dispatch [:sort-orgas-by! :date])} "Par date de création"]
        [:span {:class "button is-static level-item"}
         (let [orgs (count orgas)]
           (if (= orgs 1) "1 groupe" (str orgs " groupes")))]]))
   [:br]
   (case @(re-frame/subscribe [:view?])
     :repos [repositories-page]
     :stats [stats-page]
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

(def routes
  [["/" :repos]
   ["/chiffres" :stats]
   ["/groupes" :orgas]])

(defn on-navigate [match]
  (let [target-page (:name (:data match))
        params      (:query-params match)]
    (re-frame/dispatch [:view! (keyword target-page) params])))

(defn ^:export init []
  (re-frame/clear-subscription-cache!)
  (re-frame/dispatch-sync [:initialize-db!])
  (rfe/start!
   (rf/router routes)
   on-navigate
   {:use-fragment false})
  (start-filter-loop)
  (reagent/render
   [main-class]
   (. js/document (getElementById "app"))))
