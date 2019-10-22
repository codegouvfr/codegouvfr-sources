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
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]))

(defonce dev? false)
(defonce pages 200) ;; FIXME: Make customizable?
(defonce init-filter {:q nil :g nil :language nil :license nil})
(defonce annuaire-prefix "https://lannuaire.service-public.fr/")

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
    :filter        init-filter
    :lang          "en"}))

(re-frame/reg-event-db
 :lang!
 (fn [db [_ lang]]
   (assoc db :lang lang)))

(re-frame/reg-sub
 :lang?
 (fn [db _] (:lang db)))

(re-frame/reg-event-db
 :update-repos!
 (fn [db [_ repos]] (assoc db :repos repos)))

(re-frame/reg-event-db
 :update-stats!
 (fn [db [_ stats]] (assoc db :stats stats)))

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
   (when (= k (:sort-orgas-by db))
     (re-frame/dispatch [:reverse-sort!]))
   (assoc db :sort-orgas-by k)))

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

(def filter-chan (async/chan 100))

(defn start-filter-loop []
  (async/go
    (loop [f (async/<! filter-chan)]
      (let [v  @(re-frame/subscribe [:view?])
            l  @(re-frame/subscribe [:lang?])
            fs @(re-frame/subscribe [:filter?])]
        (re-frame/dispatch [:filter! f])
        (rfe/push-state v {:lang l}
                        (filter #(and (string? (val %))
                                      (not-empty (val %)))
                                (merge fs f))))
      (recur (async/<! filter-chan)))))

(re-frame/reg-sub
 :stats?
 (fn [db _] (:stats db)))

(re-frame/reg-sub
 :repos?
 (fn [db _]
   (let [repos0 (:repos db)
         repos  (case @(re-frame/subscribe [:sort-repos-by?])
                  :name   (sort-by :n repos0)
                  :forks  (sort-by :f repos0)
                  :stars  (sort-by :s repos0)
                  :issues (sort-by :i repos0)
                  :date   (sort #(compare (js/Date. (.parse js/Date (:u %1)))
                                          (js/Date. (.parse js/Date (:u %2))))
                                repos0)
                  :desc   (sort #(compare (count (:d %1))
                                          (count (:d %2)))
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
                 :repos (sort-by :r orgs)
                 :date  (sort #(compare (js/Date. (.parse js/Date (:c %1)))
                                        (js/Date. (.parse js/Date (:c %2))))
                              orgs)
                 :name  (sort #(compare (or-kwds %1 [:n :l])
                                        (or-kwds %2 [:n :l]))
                              orgs)
                 orgs)]
     (apply-orgas-filters (if @(re-frame/subscribe [:reverse-sort?])
                            (reverse orgas)
                            orgas)))))

(defn repositories-page [lang repos-cnt]
  (if (= repos-cnt 0)
    [:div [:p (i/i lang [:no-repo-found])] [:br]]
    (let [rep-f @(re-frame/subscribe [:sort-repos-by?])]
      [:div {:class "table-container"}
       [:table {:class "table is-hoverable is-fullwidth"}
        [:thead
         [:tr
          [:th [:abbr {:title (i/i lang [:orga-repo])}
                [:a {:class    (str "button" (when (= rep-f :name) " is-light"))
                     :title    (i/i lang [:sort-repos-alpha])
                     :on-click #(re-frame/dispatch [:sort-repos-by! :name])} (i/i lang [:orga-repo])]]]
          [:th [:abbr {:title (i/i lang [:archive])}
                [:a {:class "button is-static"
                     :title (i/i lang [:swh-link])} (i/i lang [:archive])]]]
          [:th [:abbr {:title (i/i lang [:description])}
                [:a {:class    (str "button" (when (= rep-f :desc) " is-light"))
                     :title    (i/i lang [:sort-description-length])
                     :on-click #(re-frame/dispatch [:sort-repos-by! :desc])} (i/i lang [:description])]]]
          [:th [:abbr {:title (i/i lang [:update])}
                [:a {:class    (str "button" (when (= rep-f :date) " is-light"))
                     :title    (i/i lang [:sort-update-date])
                     :on-click #(re-frame/dispatch [:sort-repos-by! :date])} (i/i lang [:update-short])]]]
          [:th [:abbr {:title (i/i lang [:forks])}
                [:a {:class    (str "button" (when (= rep-f :forks) " is-light"))
                     :title    (i/i lang [:sort-forks])
                     :on-click #(re-frame/dispatch [:sort-repos-by! :forks])} (i/i lang [:forks])]]]
          [:th [:abbr {:title (i/i lang [:stars])}
                [:a {:class    (str "button" (when (= rep-f :stars) " is-light"))
                     :title    (i/i lang [:sort-stars])
                     :on-click #(re-frame/dispatch [:sort-repos-by! :stars])} (i/i lang [:stars])]]]
          [:th [:abbr {:title (i/i lang [:issues])}
                [:a {:class    (str "button" (when (= rep-f :issues) " is-light"))
                     :title    (i/i lang [:sort-issues])
                     :on-click #(re-frame/dispatch [:sort-repos-by! :issues])} (i/i lang [:issues])]]]]]
        (into [:tbody]
              (for [dd (take pages (drop (* pages @(re-frame/subscribe [:repos-page?]))
                                         @(re-frame/subscribe [:repos?])))]
                ^{:key dd}
                (let [{:keys [li r n o d u  f s i a?]} dd]
                  [:tr
                   [:td [:div
                         [:a {:href   r
                              :target "new"
                              :title  (str (i/i lang [:go-to-repo]) (if li (str (i/i lang [:under-license]) li)))}
                          n]
                         " / "
                         [:a {:href  (rfe/href :repos {:lang lang}
                                               {:g (subs r 0 (- (count r) (+ 1 (count n))))})
                              :title (i/i lang [:browse-repos-orga])}
                          o]]]
                   [:td {:class "has-text-centered"}
                    [:a {:href   (str "https://archive.softwareheritage.org/browse/origin/" r)
                         :title  (i/i lang [:swh-link])
                         :target "new"}
                     [:img {:width "18px" :src "/images/swh-logo.png"}]]]
                   [:td {:class (when a? "has-text-grey")
                         :title (when a? (i/i lang [:repo-archived]))} d]
                   [:td (or (to-locale-date u) "N/A")]
                   [:td {:class "has-text-right"} f]
                   [:td {:class "has-text-right"} s]
                   [:td {:class "has-text-right"} i]])))]])))

(defn organizations-page [lang orgs-cnt]
  (into
   [:div]
   (if (= orgs-cnt 0)
     [[:p (i/i lang [:no-orga-found])] [:br]]
     (for [dd (partition-all 3 @(re-frame/subscribe [:orgas?]))]
       ^{:key dd}
       [:div {:class "columns"}
        (for [{:keys [n l o h c d r e au p an]
               :as   o} dd]
          ^{:key o}
          [:div {:class "column is-4"}
           [:div {:class "card"}
            [:div {:class "card-content"}
             [:div {:class "media"}
              (if au
                [:div {:class "media-left"}
                 [:figure {:class "image is-48x48"}
                  [:img {:src au}]]])
              [:div {:class "media-content"}
               [:p
                [:a {:class  "title is-4"
                     :target "new"
                     :title  (i/i lang [:go-to-orga])
                     :href   o} (or n l)]]
               (let [date (to-locale-date c)]
                 (if date
                   [:p {:class "subtitle is-6"}
                    (str (i/i lang [:created-at]) date)]))]]
             [:div {:class "content"}
              [:p d]]]
            [:div {:class "card-footer"}
             (if r
               [:div {:class "card-footer-item"
                      :title (i/i lang [:repos-number])}
                [:a {:title (i/i lang [:go-to-repos])
                     :href  (rfe/href :repos {:lang lang} {:g o})}
                 r
                 (if (< r 2)
                   (i/i lang [:repo]) (i/i lang [:repos]))]])
             (cond (= p "GitHub")
                   [:a {:class "card-footer-item"
                        :title (i/i lang [:visit-on-github])
                        :href  o}
                    (fab "fa-github")]
                   (= p "GitLab")
                   [:a {:class "card-footer-item"
                        :title (i/i lang [:visit-on-gitlab])
                        :href  o}
                    (fab "fa-gitlab")])
             (if e [:a {:class "card-footer-item"
                        :title (i/i lang [:contact-by-email])
                        :href  (str "mailto:" e)}
                    (fa "fa-envelope")])
             (if h [:a {:class  "card-footer-item"
                        :title  (i/i lang [:go-to-website])
                        :target "new"
                        :href   h} (fa "fa-globe")])
             (if an [:a {:class  "card-footer-item"
                         :title  (i/i lang [:go-to-sig-website])
                         :target "new"
                         :href   (str annuaire-prefix an)}
                     (fa "fa-link")])]]])]))))

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

(defn stats-page [lang]
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
        (into {} (map #(let [[k v] %]
                         [[:a {:href (str "/" lang "/repos?license=" k)} k] v])
                      (clojure.walk/stringify-keys top_licenses)))]
    [:div
     [:div {:class "level"}
      (figure [:span [:a {:href  (str "/" lang "/glossary#repository")
                          :title (i/i lang [:go-to-glossary])} (i/i lang [:repos-from])]
               [:a {:href  (str "/" lang "/glossary#source-code")
                    :title (i/i lang [:go-to-glossary])}
                (i/i lang [:source-code])]] nb_repos)
      (figure [:span [:a {:href  (str "/" lang "/glossary#organization-group")
                          :title (i/i lang [:go-to-glossary])}
                      (i/i lang [:orgas-or-groups])]] nb_orgs)
      (figure (i/i lang [:mean-repos-by-orga]) avg_nb_repos)
      (figure (i/i lang [:median-repos-by-orga]) median_nb_repos)]
     [:br]
     [:div {:class "columns"}
      (stats-card [:span [:a {:href  (str "/" lang "/glossary#organization-group")
                              :title (i/i lang [:go-to-glossary])} (i/i lang [:orgas-or-groups])]
                   (i/i lang [:with-more-of])
                   [:a {:href  (str "/" lang "/glossary#repository")
                        :title (i/i lang [:go-to-glossary])} (i/i lang [:repos])]] top_orgs_by_repos_0)
      (stats-card (i/i lang [:orgas-with-more-stars]) top_orgs_by_stars)]
     [:div {:class "columns"}
      (stats-card [:span [:a {:href  (str "/" lang "/glossary#license")
                              :title (i/i lang [:go-to-glossary])} (i/i lang [:licenses])]
                   (i/i lang [:more-used])]
                  top_licenses_0)]
     [:div {:class "columns"}
      (stats-card (i/i lang [:distribution-by-platform]) platforms)
      (stats-card [:span (i/i lang [:archive-on])
                   [:a {:href  (str "/" lang "/glossary#software-heritage")
                        :title (i/i lang [:go-to-glossary])}
                    "Software Heritage"]]
                  {(i/i lang [:repos-on-swh])
                   (:repos_in_archive software_heritage)
                   (i/i lang [:percent-of-repos-archived])
                   (:ratio_in_archive software_heritage)})]
     [:br]]))

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
  (let [lang @(re-frame/subscribe [:lang?])]
    [:div
     [:div {:class "field is-grouped"}
      ;; FIXME: why :p here? Use level?
      [:p {:class "control"}
       [:a {:class "button is-success"
            :href  (rfe/href :repos {:lang lang})} (i/i lang [:repos-of-source-code])]]
      [:p {:class "control"}
       [:a {:class "button is-danger"
            :title (i/i lang [:github-gitlab-etc])
            :href  (rfe/href :orgas {:lang lang})} (i/i lang [:orgas-or-groups])]]
      [:p {:class "control"}
       [:a {:class "button is-info"
            :href  (rfe/href :stats {:lang lang})} (i/i lang [:stats])]]
      [:p {:class "control"}
       [:input {:class       "input"
                :size        20
                :placeholder (i/i lang [:free-search])
                :value       (:q @(re-frame/subscribe [:filter?]))
                :on-change   (fn [e]
                               (let [ev (.-value (.-target e))]
                                 (async/go (async/>! filter-chan {:q ev}))))}]]
      (let [flt @(re-frame/subscribe [:filter?])]
        (if (seq (:g flt))
          [:p {:class "control"}
           [:a {:class "button is-outlined is-warning"
                :title (i/i lang [:remove-filter])
                :href  (rfe/href :repos {:lang lang})}
            [:span (:g flt)]
            (fa "fa-times")]]))]
     [:br]
     (cond
       (= @(re-frame/subscribe [:view?]) :home-redirect)
       (if dev?
         [:p "Testing."]
         (if (contains? i/supported-languages lang)
           (do (set! (.-location js/window) (str "/" lang "/repos")) "")
           (do (set! (.-location js/window) (str "/en/repos")) "")))

       (= @(re-frame/subscribe [:view?]) :repos)
       (let [repos          @(re-frame/subscribe [:repos?])
             repos-pages    @(re-frame/subscribe [:repos-page?])
             count-pages    (count (partition-all pages repos))
             first-disabled (= repos-pages 0)
             last-disabled  (= repos-pages (dec count-pages))]
         [:div
          [:div {:class "level-left"}
           [:div {:class "level-item"}
            [:input {:class       "input"
                     :size        12
                     :placeholder (i/i lang [:license])
                     :value       (:license @(re-frame/subscribe [:filter?]))
                     :on-change   (fn [e]
                                    (let [ev (.-value (.-target e))]
                                      (async/go (async/>! filter-chan {:license ev}))))}]]
           [:div {:class "level-item"}
            [:input {:class       "input"
                     :size        12
                     :value       (:language @(re-frame/subscribe [:filter?]))
                     :placeholder (i/i lang [:language])
                     :on-change   (fn [e]
                                    (let [ev (.-value (.-target e))]
                                      (async/go (async/>! filter-chan {:language ev}))))}]]
           [:label {:class "checkbox level-item" :title (i/i lang [:only-forked-repos])}
            [:input {:type      "checkbox"
                     :on-change #(re-frame/dispatch [:filter! {:is-fork (.-checked (.-target %))}])}]
            (i/i lang [:only-forks])]
           [:label {:class "checkbox level-item" :title (i/i lang [:no-archived-repos])}
            [:input {:type      "checkbox"
                     :on-change #(re-frame/dispatch [:filter! {:is-archive (.-checked (.-target %))}])}]
            (i/i lang [:no-archives])]
           [:label {:class "checkbox level-item" :title (i/i lang [:only-with-description-repos])}
            [:input {:type      "checkbox"
                     :on-change #(re-frame/dispatch [:filter! {:has-description (.-checked (.-target %))}])}]
            (i/i lang [:with-description])]
           [:label {:class "checkbox level-item" :title (i/i lang [:only-with-license])}
            [:input {:type      "checkbox"
                     :on-change #(re-frame/dispatch [:filter! {:is-licensed (.-checked (.-target %))}])}]
            (i/i lang [:with-license])]
           [:span {:class "button is-static level-item"}
            (let [rps (count repos)]
              (if (< rps 2) (i/i lang [:one-repo]) (str rps (i/i lang [:repos]))))]
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
             (fa "fa-fast-forward")]]]
          [:br]
          [repositories-page lang (count repos)]
          [:br]])

       (= @(re-frame/subscribe [:view?]) :orgas)
       (let [org-f @(re-frame/subscribe [:sort-orgas-by?])
             orgas @(re-frame/subscribe [:orgas?])]
         [:div
          [:div {:class "level-left"}
           [:label {:class "checkbox level-item" :title (i/i lang [:only-orga-with-code])}
            [:input {:type      "checkbox"
                     :on-change #(re-frame/dispatch [:filter! {:has-at-least-one-repo
                                                               (.-checked (.-target %))}])}]
            (i/i lang [:with-code])]
           [:a {:class    (str "button level-item is-" (if (= org-f :name) "warning" "light"))
                :title    (i/i lang [:sort-orgas-alpha])
                :on-click #(re-frame/dispatch [:sort-orgas-by! :name])} (i/i lang [:sort-alpha])]
           [:a {:class    (str "button level-item is-" (if (= org-f :repos) "warning" "light"))
                :title    (i/i lang [:sort-repos])
                :on-click #(re-frame/dispatch [:sort-orgas-by! :repos])} (i/i lang [:sort-repos])]
           [:a {:class    (str "button level-item is-" (if (= org-f :date) "warning" "light"))
                :title    (i/i lang [:sort-orgas-creation])
                :on-click #(re-frame/dispatch [:sort-orgas-by! :date])} (i/i lang [:sort-creation])]
           [:span {:class "button is-static level-item"}
            (let [orgs (count orgas)]
              (if (< orgs 2) (i/i lang [:one-group]) (str orgs (i/i lang [:groups]))))]]
          [:br]
          [organizations-page lang (count orgas)]
          [:br]])

       (= @(re-frame/subscribe [:view?]) :stats)
       [stats-page lang]

       :else
       (rfe/push-state :repos {:lang lang}))]))

(defn main-class []
  (reagent/create-class
   {:component-will-mount
    (fn []
      (GET "/repos" :handler
           #(re-frame/dispatch
             [:update-repos! (map (comp bean clj->js) %)]))
      (GET "/orgas" :handler
           #(re-frame/dispatch
             [:update-orgas! (map (comp bean clj->js) %)]))
      (GET "/stats" :handler
           #(re-frame/dispatch
             [:update-stats! (clojure.walk/keywordize-keys %)])))
    :reagent-render main-page}))

(def routes
  [["/" :home-redirect]
   ["/:lang"
    ["/repos" :repos]
    ["/groups" :orgas]
    ["/stats" :stats]]])

(defn on-navigate [match]
  (let [target-page (:name (:data match))
        lang        (:lang (:path-params match))
        params      (:query-params match)]
    (when (string? lang) (re-frame/dispatch [:lang! lang]))
    (re-frame/dispatch [:view! (keyword target-page) params])))

(defn ^:export init []
  (re-frame/clear-subscription-cache!)
  (re-frame/dispatch-sync [:initialize-db!])
  (re-frame/dispatch
   [:lang! (subs (or (.-userLanguage js/navigator)
                     (.-language js/navigator)
                     (.-browserLanguage js/navigator)
                     (.-systemLanguage js/navigator)) 0 2)])
  (rfe/start!
   (rf/router routes {:conflicts nil})
   on-navigate
   {:use-fragment false})
  (start-filter-loop)
  (reagent/render
   [main-class]
   (. js/document (getElementById "app"))))
