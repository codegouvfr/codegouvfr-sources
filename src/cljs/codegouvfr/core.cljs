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
            [codegouvfr.i18n :as i]
            [clojure.string :as s]
            [clojure.walk :as walk]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]))

(defonce dev? false)
(defonce repos-per-page 100) ;; FIXME: Make customizable?
(defonce orgas-per-page 100) ;; FIXME: Make customizable?
(defonce timeout 100)
(defonce init-filter {:q nil :g nil :language nil :license nil})
(defonce annuaire-prefix "https://lannuaire.service-public.fr/")
(defonce repos-csv-url "https://www.data.gouv.fr/fr/datasets/r/54a38a62-411f-4ea7-9631-ae78d1cef34c")
(defonce orgas-csv-url "https://www.data.gouv.fr/fr/datasets/r/79f8975b-a747-445c-85d0-2cf707e12200")
(defonce stats-url "https://api-code.etalab.gouv.fr/api/stats/general")
(defonce filter-chan (async/chan 100))
(defonce display-filter-chan (async/chan 100))

(defonce routes
  [["/" :home-redirect]
   ["/:lang"
    ["/repos" :repos]
    ["/groups" :orgas]
    ["/stats" :stats]
    ["/deps"
     ["/:orga"
      ["/:repo" :repo-deps]
      ["" :orga-deps]]]]])

(defn set-item!
  "Set `key` in browser's localStorage to `val`."
  [key val]
  (.setItem (.-localStorage js/window) key (.stringify js/JSON (clj->js val))))

(defn get-item
  "Returns value of `key` from browser's localStorage."
  [key]
  (js->clj (.parse js/JSON (.getItem (.-localStorage js/window) key))))

(defn remove-item!
  "Remove the browser's localStorage value for the given `key`."
  [key]
  (.removeItem (.-localStorage js/window) key))

(re-frame/reg-event-db
 :initialize-db!
 (fn [_ _]
   {:repos          nil
    :repos-page     0
    :orgas-page     0
    :orgas          nil
    :sort-repos-by  :date
    :sort-orgas-by  :repos
    :view           :repos
    :reverse-sort   false
    :filter         init-filter
    :display-filter init-filter
    :lang           "en"
    :path-params    nil}))

(re-frame/reg-event-db
 :lang!
 (fn [db [_ lang]]
   (assoc db :lang lang)))

(re-frame/reg-event-db
 :path-params!
 (fn [db [_ path-params]]
   (assoc db :path-params path-params)))

(re-frame/reg-sub
 :lang?
 (fn [db _] (:lang db)))

(re-frame/reg-sub
 :path-params?
 (fn [db _] (:path-params db)))

(re-frame/reg-event-db
 :update-repos!
 (fn [db [_ repos]] (assoc db :repos repos)))

(re-frame/reg-event-db
 :filter!
 (fn [db [_ s]]
   (re-frame/dispatch [:repos-page! 0])
   (re-frame/dispatch [:orgas-page! 0])
   ;; FIXME: Find a more idiomatic way?
   (assoc db :filter (merge (:filter db) s))))

(re-frame/reg-event-db
 :display-filter!
 (fn [db [_ s]]
   (assoc db :display-filter (merge (:display-filter db) s))))

(re-frame/reg-event-db
 :repos-page!
 (fn [db [_ n]] (assoc db :repos-page n)))

(re-frame/reg-event-db
 :orgas-page!
 (fn [db [_ n]] (assoc db :orgas-page n)))

(re-frame/reg-event-db
 :view!
 (fn [db [_ view query-params]]
   (re-frame/dispatch [:repos-page! 0])
   (re-frame/dispatch [:orgas-page! 0])
   (re-frame/dispatch [:filter! (merge init-filter query-params)])
   (re-frame/dispatch [:display-filter! (merge init-filter query-params)])
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
 :orgas-page?
 (fn [db _] (:orgas-page db)))

(re-frame/reg-sub
 :filter?
 (fn [db _] (:filter db)))

(re-frame/reg-sub
 :display-filter?
 (fn [db _] (:display-filter db)))

(re-frame/reg-sub
 :view?
 (fn [db _] (:view db)))

(re-frame/reg-sub
 :reverse-sort?
 (fn [db _] (:reverse-sort db)))

(re-frame/reg-event-db
 :reverse-sort!
 (fn [db _] (assoc db :reverse-sort (not (:reverse-sort db)))))

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
   (re-frame/dispatch [:orgas-page! 0])
   (when (= k (:sort-orgas-by db))
     (re-frame/dispatch [:reverse-sort!]))
   (assoc db :sort-orgas-by k)))

(defn or-kwds [m ks]
  (first (remove nil? (map #(apply % [m]) ks))))

(defn fa [s]
  [:span.icon
   [:i {:class (str "fas " s)}]])

(defn fab [s]
  [:span.icon
   [:i {:class (str "fab " s)}]])

(defn to-locale-date [s]
  (if (string? s)
    (.toLocaleDateString
     (js/Date. (.parse js/Date s)))))

(defn s-includes? [s sub]
  (if (and (string? s) (string? sub))
    (s/includes? (s/lower-case s) (s/lower-case sub))))

(defn apply-repos-filters [m]
  (let [f   @(re-frame/subscribe [:filter?])
        s   (:q f)
        g   (:g f)
        la  (:language f)
        lic (:license f)
        de  (:has-description f)
        fk  (:is-fork f)
        ar  (:is-archive f)
        li  (:is-licensed f)]
    (filter
     #(and (if fk (:f? %) true)
           (if ar (not (:a? %)) true)
           (if li (let [l (:li %)] (and l (not (= l "Other")))) true)
           (if lic (s-includes? (:li %) lic) true)
           (if la (s-includes? (:l %) la) true)
           (if de (seq (:d %)) true)
           (if g (s-includes? (:r %) g) true)
           (if s (s-includes?
                  (s/join " " [(:n %) (:r %) (:o %) (:t %) (:d %)])
                  s)
               true))
     m)))

(defn apply-orgas-filters [m]
  (let [f  @(re-frame/subscribe [:filter?])
        s  (:q f)
        de (:has-description f)
        re (:has-at-least-one-repo f)]
    (filter
     #(and (if de (seq (:d %)) true)
           (if re (> (:r %) 0) true)
           (if s (s-includes?
                  (s/join " " [(:n %) (:l %) (:d %) (:h %) (:o %)])
                  s)
               true))
     m)))

(defn start-display-filter-loop []
  (async/go
    (loop [f (async/<! display-filter-chan)]
      (re-frame/dispatch [:display-filter! f])
      (recur (async/<! display-filter-chan)))))

(defn start-filter-loop []
  (async/go
    (loop [f (async/<! filter-chan)]
      (let [v  @(re-frame/subscribe [:view?])
            l  @(re-frame/subscribe [:lang?])
            fs @(re-frame/subscribe [:filter?])]
        (rfe/push-state v {:lang l}
                        (filter #(and (string? (val %))
                                      (not-empty (val %)))
                                (merge fs f))))
      (re-frame/dispatch [:filter! f])
      (recur (async/<! filter-chan)))))

(re-frame/reg-sub
 :repos?
 (fn [db _]
   (let [repos0 (:repos db)
         favs   (get-item :favs)
         repos  (case @(re-frame/subscribe [:sort-repos-by?])
                  :name   (reverse (sort-by :n repos0))
                  :forks  (sort-by :f repos0)
                  :stars  (sort-by :s repos0)
                  :issues (sort-by :i repos0)
                  :favs   (concat (filter #(not (some #{(:n %)} favs)) repos0)
                                  (filter #(some #{(:n %)} favs) repos0))
                  :date   (sort #(compare (js/Date. (.parse js/Date (:u %1)))
                                          (js/Date. (.parse js/Date (:u %2))))
                                repos0)
                  :desc   (sort #(compare (count (:d %1))
                                          (count (:d %2)))
                                repos0)
                  repos0)]
     (apply-repos-filters (if @(re-frame/subscribe [:reverse-sort?])
                            repos
                            (reverse repos))))))

(re-frame/reg-sub
 :orgas?
 (fn [db _]
   (let [orgs  (:orgas db)
         orgas (case @(re-frame/subscribe [:sort-orgas-by?])
                 :repos (sort-by :r orgs)
                 :date  (sort #(compare (js/Date. (.parse js/Date (:c %1)))
                                        (js/Date. (.parse js/Date (:c %2))))
                              orgs)
                 :name  (reverse (sort #(compare (or-kwds %1 [:n :l])
                                                 (or-kwds %2 [:n :l]))
                                       orgs))
                 orgs)]
     (apply-orgas-filters (if @(re-frame/subscribe [:reverse-sort?])
                            orgas
                            (reverse orgas))))))

(defn favorite [lang n]
  (let [fav-class
        (reagent/atom (if (some #{n} (get-item :favs)) "" "has-text-grey"))]
    [:a {:class    @fav-class
         :title    (i/i lang [:fav-add])
         :on-click #(let [favs (get-item :favs)]
                      (if (some #{n} favs)
                        (do (set-item! :favs (remove (fn [x] (= n x)) favs))
                            (reset! fav-class "has-text-grey"))
                        (do (set-item! :favs (distinct (conj favs n)))
                            (reset! fav-class ""))))}
     (fa "fa-star")]))

(defn change-repos-page [next]
  (let [repos-page  @(re-frame/subscribe [:repos-page?])
        count-pages (count (partition-all
                            repos-per-page @(re-frame/subscribe [:repos?])))]
    (cond
      (= next "first")
      (re-frame/dispatch [:repos-page! 0])
      (= next "last")
      (re-frame/dispatch [:repos-page! (dec count-pages)])
      (and (< repos-page (dec count-pages)) next)
      (re-frame/dispatch [:repos-page! (inc repos-page)])
      (and (> repos-page 0) (not next))
      (re-frame/dispatch [:repos-page! (dec repos-page)]))))

(defn repositories-page [lang repos-cnt]
  (if (= repos-cnt 0)
    [:div [:p (i/i lang [:no-repo-found])] [:br]]
    (let [rep-f @(re-frame/subscribe [:sort-repos-by?])]
      [:div.table-container
       [:table.table.is-hoverable.is-fullwidth
        [:thead
         [:tr
          [:th [:abbr
                [:a {:class    (when-not (= rep-f :favs) "has-text-grey")
                     :title    (i/i lang [:fav-sort])
                     :on-click #(re-frame/dispatch [:sort-repos-by! :favs])}
                 (fa "fa-star")]]]
          [:th [:abbr
                [:a.button {:class    (when (= rep-f :name) "is-light")
                            :title    (i/i lang [:sort-repos-alpha])
                            :on-click #(re-frame/dispatch [:sort-repos-by! :name])}
                 (i/i lang [:orga-repo])]]]
          [:th [:abbr
                [:a.button.is-static {:title (i/i lang [:swh-link])}
                 (i/i lang [:archive])]]]
          [:th [:abbr
                [:a.button {:class    (when (= rep-f :desc) "is-light")
                            :title    (i/i lang [:sort-description-length])
                            :on-click #(re-frame/dispatch [:sort-repos-by! :desc])}
                 (i/i lang [:description])]]]
          [:th [:abbr
                [:a.button {:class    (when (= rep-f :date) "is-light")
                            :title    (i/i lang [:sort-update-date])
                            :on-click #(re-frame/dispatch [:sort-repos-by! :date])}
                 (i/i lang [:update-short])]]]
          [:th [:abbr
                [:a.button {:class    (when (= rep-f :forks) "is-light")
                            :title    (i/i lang [:sort-forks])
                            :on-click #(re-frame/dispatch [:sort-repos-by! :forks])}
                 (i/i lang [:forks])]]]
          [:th [:abbr
                [:a.button {:class    (when (= rep-f :stars) "is-light")
                            :title    (i/i lang [:sort-stars])
                            :on-click #(re-frame/dispatch [:sort-repos-by! :stars])}
                 (i/i lang [:stars])]]]
          [:th [:abbr
                [:a.button {:class    (when (= rep-f :issues) "is-light")
                            :title    (i/i lang [:sort-issues])
                            :on-click #(re-frame/dispatch [:sort-repos-by! :issues])}
                 (i/i lang [:issues])]]]]]
        (into [:tbody]
              (for [dd (take repos-per-page
                             (drop (* repos-per-page @(re-frame/subscribe [:repos-page?]))
                                   @(re-frame/subscribe [:repos?])))]
                ^{:key dd}
                (let [{:keys [a? d f i li n o r s u dp]}
                      dd
                      group
                      (subs r 0 (- (count r) (+ 1 (count n))))]
                  [:tr
                   [:td [favorite lang n]]
                   [:td [:div
                         [:a {:href   r
                              :target "new"
                              :title  (str (i/i lang [:go-to-repo])
                                           (if li (str (i/i lang [:under-license]) li)))}
                          n]
                         " < "
                         [:a {:href  (rfe/href :repos {:lang lang} {:g group})
                              :title (i/i lang [:browse-repos-orga])}
                          o]]]
                   [:td.has-text-centered
                    [:a {:href   (str "https://archive.softwareheritage.org/browse/origin/" r)
                         :title  (i/i lang [:swh-link])
                         :target "new"}
                     [:img {:width "18px" :src "/images/swh-logo.png"}]]]
                   [:td {:class (when a? "has-text-grey")
                         :title (when a? (i/i lang [:repo-archived]))}
                    [:span
                     (when dp
                       [:span
                        [:a.has-text-grey
                         {:title (i/i lang [:deps])
                          :href  (rfe/href
                                  :repo-deps
                                  {:lang lang
                                   :orga o ;; (s/replace o "https://github.com/" "")
                                   :repo n})}
                         (fa "fa-cubes")]
                        " "]) d]]
                   [:td (or (to-locale-date u) "N/A")]
                   [:td.has-text-right f]
                   [:td.has-text-right s]
                   [:td.has-text-right i]])))]])))

(defn change-orgas-page [next]
  (let [orgas-page  @(re-frame/subscribe [:orgas-page?])
        count-pages (count (partition-all
                            orgas-per-page @(re-frame/subscribe [:orgas?])))]
    (cond
      (= next "first")
      (re-frame/dispatch [:orgas-page! 0])
      (= next "last")
      (re-frame/dispatch [:orgas-page! (dec count-pages)])
      (and (< orgas-page (dec count-pages)) next)
      (re-frame/dispatch [:orgas-page! (inc orgas-page)])
      (and (> orgas-page 0) (not next))
      (re-frame/dispatch [:orgas-page! (dec orgas-page)]))))

(defn organizations-page [lang]
  (let [org-f          @(re-frame/subscribe [:sort-orgas-by?])
        orgas          @(re-frame/subscribe [:orgas?])
        orgs-cnt       (count orgas)
        orgas-pages    @(re-frame/subscribe [:orgas-page?])
        count-pages    (count (partition-all orgas-per-page orgas))
        first-disabled (= orgas-pages 0)
        last-disabled  (= orgas-pages (dec count-pages))]
    [:div
     [:div.level-left
      [:label.checkbox.level-item {:title (i/i lang [:only-orga-with-code])}
       [:input {:type      "checkbox"
                :on-change #(re-frame/dispatch [:filter! {:has-at-least-one-repo
                                                          (.-checked (.-target %))}])}]
       (i/i lang [:with-code])]
      [:a.button.level-item
       {:class    (str "is-" (if (= org-f :name) "warning" "light"))
        :title    (i/i lang [:sort-orgas-alpha])
        :on-click #(re-frame/dispatch [:sort-orgas-by! :name])} (i/i lang [:sort-alpha])]
      [:a.button.level-item
       {:class    (str "is-" (if (= org-f :repos) "warning" "light"))
        :title    (i/i lang [:sort-repos])
        :on-click #(re-frame/dispatch [:sort-orgas-by! :repos])} (i/i lang [:sort-repos])]
      [:a.button.level-item
       {:class    (str "is-" (if (= org-f :date) "warning" "light"))
        :title    (i/i lang [:sort-orgas-creation])
        :on-click #(re-frame/dispatch [:sort-orgas-by! :date])} (i/i lang [:sort-creation])]
      [:span.button.is-static.level-item
       (let [orgs (count orgas)]
         (if (< orgs 2)
           (str orgs (i/i lang [:one-group]))
           (str orgs (i/i lang [:groups]))))]
      [:nav.pagination.level-item {:role "navigation" :aria-label "pagination"}
       [:a.pagination-previous
        {:on-click #(change-orgas-page "first")
         :disabled first-disabled}
        (fa "fa-fast-backward")]
       [:a.pagination-previous
        {:on-click #(change-orgas-page nil)
         :disabled first-disabled}
        (fa "fa-step-backward")]
       [:a.pagination-next
        {:on-click #(change-orgas-page true)
         :disabled last-disabled}
        (fa "fa-step-forward")]
       [:a.pagination-next
        {:on-click #(change-orgas-page "last")
         :disabled last-disabled}
        (fa "fa-fast-forward")]]
      [:a {:title (i/i lang [:download])
           :href  orgas-csv-url}
       (fa "fa-file-csv")]]
     [:br]
     (into
      [:div]
      (if (= orgs-cnt 0)
        [[:p (i/i lang [:no-orga-found])] [:br]]
        (for [dd (partition-all
                  3
                  (take orgas-per-page
                        (drop (* orgas-per-page @(re-frame/subscribe [:orgas-page?]))
                              @(re-frame/subscribe [:orgas?]))))]
          ^{:key dd}
          [:div.columns
           (for [{:keys [n l o h c d r e au p an dp] :as o} dd]
             ^{:key o}
             [:div.column.is-4
              [:div.card
               [:div.card-content
                [:div.media
                 (if au
                   [:div.media-left
                    [:figure.image.is-48x48
                     [:img {:src au}]]])
                 [:div.media-content
                  [:a.title.is-4
                   {:target "new"
                    :title  (i/i lang [:go-to-orga])
                    :href   o}
                   [:span (or n l)
                    " "
                    [:span.is-size-5
                     (cond (= p "GitHub")
                           (fab "fa-github")
                           (= p "GitLab")
                           (fab "fa-gitlab"))]]]
                  [:br]
                  (let [date (to-locale-date c)]
                    (if date
                      [:p (str (i/i lang [:created-at]) date)]))
                  (if r
                    ;; FIXME: hackish, orgas-mapping should give
                    ;; the forge base on top of "plateforme".
                    [:a {:title (i/i lang [:go-to-repos])
                         :href  (rfe/href :repos {:lang lang}
                                          {:g (s/replace o "/groups/" "/")})}
                     r (if (< r 2)
                         (i/i lang [:repo]) (i/i lang [:repos]))])]]
                [:div.content
                 [:p d]]]
               [:div.card-footer
                (when (and (= p "GitHub") dp)
                  [:a.card-footer-item
                   {:title (i/i lang [:deps])
                    :href  (rfe/href
                            :orga-deps
                            {:lang lang
                             :orga (s/replace o "https://github.com/" "")})}
                   (fa "fa-cubes")])
                (if e [:a.card-footer-item
                       {:title (i/i lang [:contact-by-email])
                        :href  (str "mailto:" e)}
                       (fa "fa-envelope")])
                (if h [:a.card-footer-item
                       {:title  (i/i lang [:go-to-website])
                        :target "new"
                        :href   h} (fa "fa-globe")])
                (if an [:a.card-footer-item
                        {:title  (i/i lang [:go-to-sig-website])
                         :target "new"
                         :href   (str annuaire-prefix an)}
                        (fa "fa-link")])]]])])))]))

(defn organizations-page-class [lang]
  (reagent/create-class
   {:component-will-mount
    (fn []
      (GET "/orgas"
           :handler
           #(re-frame/dispatch
             [:update-orgas! (map (comp bean clj->js) %)])))
    :reagent-render (fn [] (organizations-page lang))}))

(defn figure [heading title]
  [:div.column
   [:div.has-text-centered
    [:div
     [:p.heading heading]
     [:p.title (str title)]]]])

(defn stats-card [heading data]
  [:div.column
   [:div.card
    [:h1.card-header-title.subtitle heading]
    [:div.card-content
     [:table.table.is-fullwidth
      [:tbody
       (for [o (reverse (walk/stringify-keys (sort-by val data)))]
         ^{:key (key o)}
         [:tr [:td (key o)] [:td (val o)]])]]]]])

(defn deps-card [heading deps lang]
  [:div.column
   [:div.card
    [:h1.card-header-title.subtitle
     heading
     [:sup
      [:a.has-text-grey.is-size-7
       {:href  (str "/" lang "/glossary#dependencies")
        :title (i/i lang [:go-to-glossary])}
       (fa "fa-question-circle")]]]
    [:div.card-content
     [:table.table.is-fullwidth
      [:thead [:tr
               [:th (i/i lang [:type])]
               [:th (i/i lang [:name])]
               [:th (i/i lang [:number-of-repos])]]]
      [:tbody
       (for [{:keys [t n rs] :as o} deps]
         ^{:key o}
         [:tr [:td t] [:td n] [:td rs]])]]]]])

(defn stats-page
  [lang stats deps deps-total]
  (let [{:keys [nb_repos nb_orgs avg_nb_repos median_nb_repos
                top_orgs_by_repos top_orgs_by_stars top_licenses
                platforms software_heritage top_languages]} stats
        top_orgs_by_repos_0
        (into {} (map #(vector (str (:organisation_nom %)
                                    " (" (:plateforme %) ")")
                               (:count %))
                      top_orgs_by_repos))
        top_languages_0
        (into {} (map #(let [[k v] %]
                         [[:a {:href (str "/" lang "/repos?language=" k)} k] v])
                      (walk/stringify-keys top_languages)))
        top_licenses_0
        (into {} (map #(let [[k v] %]
                         [[:a {:href (str "/" lang "/repos?license=" k)} k] v])
                      (walk/stringify-keys top_licenses)))]
    [:div
     [:div.columns
      (figure (i/i lang [:repos-of-source-code]) nb_repos)
      (figure (i/i lang [:orgas-or-groups]) nb_orgs)
      (figure (i/i lang [:mean-repos-by-orga]) avg_nb_repos)
      (figure (i/i lang [:median-repos-by-orga]) median_nb_repos)
      (figure (i/i lang [:deps-stats]) (:deps-total deps-total))]
     [:br]
     [:div.columns
      (stats-card [:span
                   (i/i lang [:orgas-or-groups])
                   [:sup
                    [:a.has-text-grey.is-size-7
                     {:href  (str "/" lang "/glossary#organization-group")
                      :title (i/i lang [:go-to-glossary])}
                     (fa "fa-question-circle")]]
                   " "
                   (i/i lang [:with-more-of])
                   (i/i lang [:repos])
                   [:sup
                    [:a.has-text-grey.is-size-7
                     {:href  (str "/" lang "/glossary#repository")
                      :title (i/i lang [:go-to-glossary])}
                     (fa "fa-question-circle")]]]
                  top_orgs_by_repos_0)
      (stats-card (i/i lang [:orgas-with-more-stars]) top_orgs_by_stars)]
     [:div.columns
      (stats-card [:span
                   (i/i lang [:most-used-licenses])
                   [:sup
                    [:a.has-text-grey.is-size-7
                     {:href  (str "/" lang "/glossary#license")
                      :title (i/i lang [:go-to-glossary])}
                     (fa "fa-question-circle")]]]
                  top_licenses_0)
      (stats-card [:span (i/i lang [:most-used-languages])]
                  top_languages_0)]
     [:div.columns
      (stats-card (i/i lang [:distribution-by-platform]) platforms)
      (stats-card [:span (i/i lang [:archive-on])
                   "Software Heritage"
                   [:sup [:a.has-text-grey.is-size-7
                          {:href  (str "/" lang "/glossary#software-heritage")
                           :title (i/i lang [:go-to-glossary])}
                          (fa "fa-question-circle")]]]
                  {(i/i lang [:repos-on-swh])
                   (:repos_in_archive software_heritage)
                   (i/i lang [:percent-of-repos-archived])
                   (:ratio_in_archive software_heritage)})]
     [:div.columns
      (deps-card (i/i lang [:deps]) deps lang)]
     [:br]]))

(defn stats-page-class [lang]
  (let [deps       (reagent/atom nil)
        stats      (reagent/atom nil)
        deps-total (reagent/atom nil)]
    (reagent/create-class
     {:component-will-mount
      (fn []
        (GET "/deps-total"
             :handler #(reset! deps-total (walk/keywordize-keys %)))
        (GET "/deps"
             :handler #(reset! deps (map (comp bean clj->js) %)))
        (GET stats-url
             :handler #(reset! stats (walk/keywordize-keys %))))
      :reagent-render (fn [] (stats-page lang @stats @deps @deps-total))})))

(defn repo-deps-page
  "Table with repository dependencies."
  [lang orga repo deps sort-key sort-rev?]
  (let [rdeps0 (:d deps)
        rdeps  (sort-by @sort-key rdeps0)
        rdeps  (if @sort-rev? (reverse rdeps) rdeps)
        cdeps  (count rdeps)]
    (if (= (:g deps) orga)
      [:div
       [:div [:h1 (str cdeps " " (if (< cdeps 2)
                                   (i/i lang [:dep-of])
                                   (i/i lang [:deps-of]))
                       " ")
              [:a {:href (rfe/href :repos {:lang lang} {:q repo})} repo]
              [:sup
               [:a.has-text-grey.is-size-6
                {:href  (str "/" lang "/glossary#dependencies")
                 :title (i/i lang [:go-to-glossary])}
                (fa "fa-question-circle")]]
              " ("
              [:a {:href (rfe/href :orgas {:lang lang} {:q orga})} orga]
              ")"]]
       [:br]
       (if-let [dps (not-empty rdeps)]
         [:div.table-container
          [:table.table.is-hoverable.is-fullwidth
           [:thead [:tr
                    [:th
                     [:a.button
                      {:class    (when (= @sort-key :t) "is-light")
                       :title    (i/i lang [:sort])
                       :on-click #(if (= @sort-key :t)
                                    (reset! sort-rev? (not @sort-rev?))
                                    (reset! sort-key :t))}
                      (i/i lang [:type])]]
                    [:th
                     [:a.button
                      {:class    (when (= @sort-key :n) "is-light")
                       :on-click #(if (= @sort-key :n)
                                    (reset! sort-rev? (not @sort-rev?))
                                    (reset! sort-key :n))}
                      (i/i lang [:name])]]
                    [:th
                     [:a.button
                      {:class    (when (= @sort-key :core) "is-light")
                       :on-click #(if (= @sort-key :core)
                                    (reset! sort-rev? (not @sort-rev?))
                                    (reset! sort-key :core))}
                      (i/i lang [:core-dep])]]
                    [:th
                     [:a.button
                      {:class    (when (= @sort-key :dev) "is-light")
                       :on-click #(if (= @sort-key :dev)
                                    (reset! sort-rev? (not @sort-rev?))
                                    (reset! sort-key :dev))}
                      (i/i lang [:dev-dep])]]]]
           (into [:tbody]
                 (for [{:keys [t n core dev] :as r} rdeps]
                   ^{:key r}
                   [:tr
                    [:td t] [:td n]
                    [:td core] [:td dev]]))]
          [:br]]
         [:div
          [:p (i/i lang [:deps-not-found])]
          [:br]])]
      [:div
       [:h2 (i/i lang [:group-repo-not-found])]
       [:br]])))

(defn repo-deps-page-class [lang]
  (let [deps      (reagent/atom nil)
        params    @(re-frame/subscribe [:path-params?])
        repo      (:repo params)
        orga      (:orga params)
        sort-key  (reagent/atom :t)
        sort-rev? (reagent/atom false)]
    (reagent/create-class
     {:component-will-mount
      (fn []
        (GET (str "/deps/repos/" repo)
             :handler #(reset! deps (walk/keywordize-keys %))))
      :reagent-render
      (fn [] (repo-deps-page lang (:orga params) (:repo params) @deps sort-key sort-rev?))})))

(defn orga-deps-page
  "Table with group/organization dependencies."
  [lang orga deps sort-key sort-rev?]
  (let [cdeps (count deps)
        deps  (if (not (= @sort-key :repos))
                (sort-by @sort-key deps)
                (sort #(compare (count (:repos %1))
                                (count (:repos %2)))
                      deps))
        deps  (if @sort-rev? (reverse deps) deps)]
    [:div
     [:div [:h1 (str cdeps " " (if (< cdeps 2)
                                 (i/i lang [:dep-of])
                                 (i/i lang [:deps-of]))
                     " ")
            [:a {:href (rfe/href :orgas {:lang lang} {:q orga})} orga]
            [:sup
             [:a.has-text-grey.is-size-6
              {:href  (str "/" lang "/glossary#dependencies")
               :title (i/i lang [:go-to-glossary])}
              (fa "fa-question-circle")]]]]
     [:br]
     (if (not-empty deps)
       [:div.table-container
        [:table.table.is-hoverable.is-fullwidth
         [:thead
          [:tr
           [:th
            [:a.button
             {:class    (when (= @sort-key :type) "is-light")
              :title    (i/i lang [:sort])
              :on-click #(reset! sort-key :type)}
             (i/i lang [:type])]]
           [:th
            [:a.button
             {:class    (when (= @sort-key :name) "is-light")
              :on-click #(if (= @sort-key :name)
                           (reset! sort-rev? (not @sort-rev?))
                           (reset! sort-key :name))}
             (i/i lang [:name])]]
           [:th
            [:a.button
             {:class    (when (= @sort-key :core) "is-light")
              :on-click #(if (= @sort-key :core)
                           (reset! sort-rev? (not @sort-rev?))
                           (reset! sort-key :core))}
             (i/i lang [:core-dep])]]
           [:th
            [:a.button
             {:class    (when (= @sort-key :dev) "is-light")
              :on-click #(if (= @sort-key :dev)
                           (reset! sort-rev? (not @sort-rev?))
                           (reset! sort-key :dev))}
             (i/i lang [:dev-dep])]]
           [:th
            [:a.button
             {:class    (when (= @sort-key :repos) "is-light")
              :on-click #(if (= @sort-key :repos)
                           (reset! sort-rev? (not @sort-rev?))
                           (reset! sort-key :repos))}
             (i/i lang [:Repos])]]]]
         (into [:tbody]
               (for [{:keys [type name core dev repos] :as d} deps]
                 ^{:key d}
                 [:tr
                  [:td type] [:td name]
                  [:td core] [:td dev]
                  [:td (for [{:keys [name full_name] :as r} repos]
                         ^{:key r}
                         [:span [:a {:href
                                     (str "https://github.com/"
                                          full_name)}
                                 name] " "])]]))]
        [:br]]
       [:div
        [:h2 (i/i lang [:deps-not-found])]
        [:br]])]))

(defn orga-deps-page-class [lang]
  (let [deps      (reagent/atom nil)
        orga      (:orga @(re-frame/subscribe [:path-params?]))
        sort-key  (reagent/atom :type)
        sort-rev? (reagent/atom false)]
    (reagent/create-class
     {:component-will-mount
      (fn []
        (GET (str "/deps/orgas/" orga)
             :handler
             #(reset! deps (walk/keywordize-keys %))))
      :reagent-render (fn [] (orga-deps-page lang orga @deps sort-key sort-rev?))})))

(defn main-menu [q lang view]
  [:div.field.is-grouped
   ;; FIXME: why :p here? Use level?
   [:p.control
    [:a.button.is-success {:href (rfe/href :repos {:lang lang})}
     (i/i lang [:repos-of-source-code])]]
   [:p.control
    [:a.button.is-danger
     {:title (i/i lang [:github-gitlab-etc])
      :href  (rfe/href :orgas {:lang lang})}
     (i/i lang [:orgas-or-groups])]]
   [:p.control
    [:a.button.is-info {:href (rfe/href :stats {:lang lang})}
     (i/i lang [:stats])]]
   (if (or (= view :repos) (= view :orgas))
     [:p.control
      [:input.input
       {:size        20
        :placeholder (i/i lang [:free-search])
        :value       (or @q (:q @(re-frame/subscribe [:display-filter?])))
        :on-change   (fn [e]
                       (let [ev (.-value (.-target e))]
                         (reset! q ev)
                         (async/go
                           (async/>! display-filter-chan {:q ev})
                           (<! (async/timeout timeout))
                           (async/>! filter-chan {:q ev}))))}]])
   (let [flt @(re-frame/subscribe [:filter?])]
     (if (seq (:g flt))
       [:p.control
        [:a.button.is-outlined.is-warning
         {:title (i/i lang [:remove-filter])
          :href  (rfe/href :repos {:lang lang})}
         [:span (:g flt)]
         (fa "fa-times")]]))
   [:br]])

(defn main-page [q license language]
  (let [lang @(re-frame/subscribe [:lang?])
        view @(re-frame/subscribe [:view?])]
    [:div
     [main-menu q lang view]
     (cond
       (= view :home-redirect)
       (if dev?
         [:p "Testing."]
         (if (contains? i/supported-languages lang)
           (do (set! (.-location js/window) (str "/" lang "/repos")) "")
           (do (set! (.-location js/window) (str "/en/repos")) "")))
       ;; Table to display repository
       (= view :repos)
       (let [repos          @(re-frame/subscribe [:repos?])
             repos-pages    @(re-frame/subscribe [:repos-page?])
             count-pages    (count (partition-all repos-per-page repos))
             first-disabled (= repos-pages 0)
             last-disabled  (= repos-pages (dec count-pages))]
         [:div
          [:div.level-left
           [:div.level-item
            [:input.input
             {:size        12
              :placeholder (i/i lang [:license])
              :value       (or @license
                               (:license @(re-frame/subscribe [:display-filter?])))
              :on-change   (fn [e]
                             (let [ev (.-value (.-target e))]
                               (reset! license ev)
                               (async/go
                                 (async/>! display-filter-chan {:license ev})
                                 (<! (async/timeout timeout))
                                 (async/>! filter-chan {:license ev}))))}]]
           [:div.level-item
            [:input.input
             {:size        12
              :value       (or @language
                               (:language @(re-frame/subscribe [:display-filter?])))
              :placeholder (i/i lang [:language])
              :on-change   (fn [e]
                             (let [ev (.-value (.-target e))]
                               (reset! language ev)
                               (async/go
                                 (async/>! display-filter-chan {:language ev})
                                 (<! (async/timeout timeout))
                                 (async/>! filter-chan {:language ev}))))}]]
           [:label.checkbox.level-item
            {:title (i/i lang [:only-forked-repos])}
            [:input {:type      "checkbox"
                     :on-change #(re-frame/dispatch [:filter! {:is-fork (.-checked (.-target %))}])}]
            (i/i lang [:only-forks])]
           [:label.checkbox.level-item {:title (i/i lang [:no-archived-repos])}
            [:input {:type      "checkbox"
                     :on-change #(re-frame/dispatch [:filter! {:is-archive (.-checked (.-target %))}])}]
            (i/i lang [:no-archives])]
           [:label.checkbox.level-item {:title (i/i lang [:only-with-description-repos])}
            [:input {:type      "checkbox"
                     :on-change #(re-frame/dispatch [:filter! {:has-description (.-checked (.-target %))}])}]
            (i/i lang [:with-description])]
           [:label.checkbox.level-item {:title (i/i lang [:only-with-license])}
            [:input {:type      "checkbox"
                     :on-change #(re-frame/dispatch [:filter! {:is-licensed (.-checked (.-target %))}])}]
            (i/i lang [:with-license])]
           [:span.button.is-static.level-item
            (let [rps (count repos)]
              (if (< rps 2)
                (str rps (i/i lang [:repo]))
                (str rps (i/i lang [:repos]))))]
           [:nav.pagination.level-item {:role "navigation" :aria-label "pagination"}
            [:a.pagination-previous
             {:on-click #(change-repos-page "first")
              :disabled first-disabled}
             (fa "fa-fast-backward")]
            [:a.pagination-previous
             {:on-click #(change-repos-page nil)
              :disabled first-disabled}
             (fa "fa-step-backward")]
            [:a.pagination-next
             {:on-click #(change-repos-page true)
              :disabled last-disabled}
             (fa "fa-step-forward")]
            [:a.pagination-next
             {:on-click #(change-repos-page "last")
              :disabled last-disabled}
             (fa "fa-fast-forward")]]
           [:a {:title (i/i lang [:download])
                :href  repos-csv-url}
            (fa "fa-file-csv")]]
          [:br]
          [repositories-page lang (count repos)]
          [:br]])
       ;; Table to display organizations
       (= view :orgas)
       [organizations-page-class lang]
       ;; Table to display statistiques
       (= view :stats)
       [stats-page-class lang]
       ;; Table to display a repository dependencies
       (= view :repo-deps)
       [repo-deps-page-class lang]
       ;; Table to display a group dependencies
       (= view :orga-deps)
       [orga-deps-page-class lang]
       ;; Fall back on the repository page
       :else
       (rfe/push-state :repos {:lang lang}))]))

(defn main-class []
  (let [q        (reagent/atom nil)
        license  (reagent/atom nil)
        language (reagent/atom nil)]
    (reagent/create-class
     {:component-will-mount
      (fn []
        (GET "/repos"
             :handler
             #(re-frame/dispatch
               [:update-repos! (map (comp bean clj->js) %)])))
      :reagent-render (fn [] (main-page q license language))})))

(defn on-navigate [match]
  (let [lang (:lang (:path-params match))]
    (when (string? lang) (re-frame/dispatch [:lang! lang]))
    (re-frame/dispatch [:path-params! (:path-params match)])
    (re-frame/dispatch [:view! (keyword (:name (:data match))) (:query-params match)])))

(defn ^:export init []
  (re-frame/clear-subscription-cache!)
  (re-frame/dispatch-sync [:initialize-db!])
  (re-frame/dispatch
   [:lang! (subs (or js/navigator.language "en") 0 2)])
  (rfe/start!
   (rf/router routes {:conflicts nil})
   on-navigate
   {:use-fragment false})
  (start-filter-loop)
  (start-display-filter-loop)
  (reagent/render
   [main-class]
   (. js/document (getElementById "app"))))
