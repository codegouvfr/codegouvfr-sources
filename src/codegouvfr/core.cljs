;; Copyright (c) 2019-2024 DINUM, Bastien Guerry <bastien.guerry@code.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.core
  (:require [cljs.core.async :as async]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.dom]
            [cljs-bean.core :refer [bean]]
            [clojure.browser.dom :as dom]
            [goog.string :as gstring]
            [ajax.core :refer [GET]]
            [codegouvfr.i18n :as i]
            [clojure.string :as s]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [goog.labs.format.csv :as csv]
            [semantic-csv.core :as sc])
  (:require-macros [codegouvfr.macros :refer [inline-page]]))

;; Defaults

(defonce unix-epoch "1970-01-01T00:00:00Z")
(defonce repos-per-page 100)
(defonce orgas-per-page 20)
(defonce timeout 100)

;; FIXME: Setting this here is a hack
(def dp-filter (reagent/atom nil))

(defonce init-filter
  {:d        nil
   :g        nil
   :license  nil
   :language nil
   :platform ""
   :ministry ""})

(defonce urls
  {:swh-baseurl "https://archive.softwareheritage.org/browse/origin/"})

(defonce filter-chan (async/chan 100))

;; Mappings used when exporting displayed data to csv files
(defonce mappings
  {:repos {:u  :last_update
           :d  :description
           :f? :is_fork
           :t? :is_template
           :l  :language
           :li :license
           :n  :name
           :f  :forks_count
           :s  :subscribers_count
           :o  :organization_name
           :p  :platform
           :r  :repository_url}
   :orgas {:d  :description
           :a  :location
           :e  :email
           :n  :name
           :p  :platform
           :h  :website
           :l  :login
           :c  :creation_date
           :r  :repositories_count
           :o  :organization_url
           :au :avatar_url}})

;; Utility functions

(defn new-tab [s lang]
  (str s " - " (i/i lang [:new-tab])))

(defn to-locale-date [^String s lang]
  (when (string? s)
    (.toLocaleDateString (js/Date. (.parse js/Date s)) lang)))

(defn todays-date [lang]
  (s/replace (.toLocaleDateString (js/Date.) lang) "/" "-"))

(defn or-kwds [m ks]
  (first (remove nil? (map #(apply % [m]) ks))))

(defn s-includes? [s sub]
  (when (and (string? s) (string? sub))
    (let [sub (-> sub
                  s/trim
                  (s/replace #"[\(\)\*\!\+\?\[\]\\]" #(str "\\" %1))
                  (s/replace #"\s+" " ")
                  (s/replace #"\s+" " ")
                  (s/replace #"(?i)[éèëêe]" "[éèëêe]")
                  (s/replace #"(?i)[æàâa]" "[æàâa]")
                  (s/replace #"(?i)[œöôo]" "[œöôo]")
                  (s/replace #"(?i)[çc]" "[çc]")
                  (s/replace #"(?i)[ûùu]" "[ûùu]"))]
      (re-find (re-pattern (s/lower-case sub)) (s/lower-case s)))))

(defn vec-to-csv-string [data]
  (->> data
       (map #(map (fn [s] (gstring/replaceAll (str s) "\"" "\"\"")) %))
       (map #(map (fn [s] (gstring/format "\"%s\"" s)) %))
       (map #(s/join ", " %))
       (s/join "\n")))

(defn download-as-csv!
  [data file-name]
  (let [data-string (-> data sc/vectorize vec-to-csv-string)
        data-blob   (js/Blob. #js [data-string] #js {:type "text/csv"})
        link        (js/document.createElement "a")]
    (set! (.-href link) (js/URL.createObjectURL data-blob))
    (.setAttribute link "download" file-name)
    (js/document.body.appendChild link)
    (.click link)
    (js/document.body.removeChild link)))

(defn top-clean-up-repos [data param]
  (let [total (reduce + (map last data))]
    (sequence
     (comp
      (map (fn [[k v]]
             [k (if (some #{"license" "language"} (list param))
                  (js/parseFloat
                   (gstring/format "%.2f" (* (/ v total) 100)))
                  v)]))
      (map #(let [[k v] %]
              [[:a {:href (rfe/href :repos {} {param k})} k] v])))
     data)))

(defn top-clean-up-orgas [data param]
  (sequence
   (comp
    (map #(let [[k v] %
                k0    (s/replace k #" \([^)]+\)" "")]
            [[:a {:href (rfe/href :orgas {} {param k0})} k] v])))
   data))

(defn- table-header [lang what k]
  (let [glossary-url "https://code.gouv.fr/documentation/#glossaire"]
    [:strong.fr-m-auto
     (let [rps (count what)]
       (if (< rps 2)
         (str rps (i/i lang [k]))
         (str rps (i/i lang [(keyword (str (name k) "s"))]))))
     " "
     [:a.fr-raw-link.fr-link
      {:href   glossary-url
       :target "new"
       :rel    "noreferrer noopener"
       :title  (i/i lang [:glossary])}
      [:span.fr-icon-info-line]]]))

;; Filters

(defn ntaf
  "Not a true and b false."
  [a b] (if a b true))

(defn apply-repos-filters [m]
  (let [{:keys [d q g language platform license
                is-template is-contrib is-publiccode
                is-fork is-licensed]}
        @(re-frame/subscribe [:filter?])]
    (filter
     #(let [o (:o %)
            n (:n %)
            t (:t? %)
            r (str o "/" n)]
        (and
         (if (and d @dp-filter) (some @dp-filter [r]) true)
         (ntaf is-fork (:f? %))
         (ntaf is-contrib (:c? %))
         (ntaf is-publiccode (:p? %))
         (ntaf is-template t)
         (ntaf is-licensed (let [l (:li %)] (and l (not= l "Other"))))
         (ntaf license (s-includes? (:li %) license))
         (if language
           (some (into #{} (list (s/lower-case (or (:l %) ""))))
                 (s/split (s/lower-case language) #" +"))
           true)
         (if (= platform "") true (s-includes? r platform))
         (ntaf g (s-includes? r g))
         (ntaf q (s-includes? (s/join " " [n r o (:d %)]) q))))
     m)))

(defn apply-orgas-filters [m]
  (let [{:keys [q ministry]} @(re-frame/subscribe [:filter?])]
    (filter
     #(and (ntaf q (s-includes? (s/join " " [(:n %) (:l %) (:d %) (:h %) (:o %) (:pso %)]) q))
           (if (= ministry "") true (= (:m %) ministry)))
     m)))

(defn apply-sill-filters [m]
  (let [{:keys [q]} @(re-frame/subscribe [:filter?])]
    (filter
     #(ntaf q (s-includes? (s/join " " [(:n %) (:f %) (:t %)]) q))
     m)))

(defn apply-papillon-filters [m]
  (let [{:keys [q]} @(re-frame/subscribe [:filter?])]
    (filter
     #(ntaf q (s-includes? (s/join " " [(:n %) (:a %) (:d %)]) q))
     m)))

(defn close-filter-button [lang ff t reinit]
  [:span
   [:a.fr-link.fr-icon-close-circle-line.fr-link--icon-right
    {:title (i/i lang [:remove-filter])
     :href  (rfe/href t {:lang lang} (filter #(not-empty (val %)) reinit))}
    [:span ff]]])

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

;; Events

(re-frame/reg-event-db
 :initialize-db!
 (fn [_ _]
   {:repos-page    0
    :awes-page     0
    :orgas-page    0
    :sort-repos-by :score
    :sort-orgas-by :repos
    :reverse-sort  false
    :filter        init-filter
    :lang          "en"
    :path          ""}))

(def repos (reagent/atom nil))
(def awes (reagent/atom nil))
(def orgas (reagent/atom nil))
(def platforms (reagent/atom nil))
(def tags (reagent/atom nil))
(def ministries (filter not-empty (distinct (map :m @orgas))))

(re-frame/reg-sub
 :ministries?
 (fn [] (filter not-empty (distinct (map :m @orgas)))))

(re-frame/reg-event-db
 :lang!
 (fn [db [_ lang]]
   (dom/set-properties
    (dom/get-element "html")
    {"lang" lang})
   (assoc db :lang lang)))

(re-frame/reg-event-db
 :path!
 (fn [db [_ path]]
   (assoc db :path path)))

(re-frame/reg-event-db
 :filter!
 (fn [db [_ s]]
   ;; FIXME: Necessary?
   (re-frame/dispatch [:repos-page! 0])
   (re-frame/dispatch [:orgas-page! 0])
   (update-in db [:filter] merge s)))

(re-frame/reg-event-db
 :repos-page!
 (fn [db [_ n]] (assoc db :repos-page n)))

(re-frame/reg-event-db
 :awes-page!
 (fn [db [_ n]] (assoc db :awes-page n)))

(re-frame/reg-event-db
 :orgas-page!
 (fn [db [_ n]] (assoc db :orgas-page n)))

(re-frame/reg-event-db
 :view!
 (fn [db [_ view query-params]]
   (re-frame/dispatch [:repos-page! 0])
   (re-frame/dispatch [:orgas-page! 0])
   (re-frame/dispatch [:awes-page! 0])
   (re-frame/dispatch [:filter! (merge init-filter query-params)])
   (assoc db :view view)))

(re-frame/reg-event-db
 :reverse-sort!
 (fn [db _] (update-in db [:reverse-sort] not)))

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

;; Subscriptions

(re-frame/reg-sub
 :lang?
 (fn [db _] (:lang db)))

(re-frame/reg-sub
 :path?
 (fn [db _] (:path db)))

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
 :awes-page?
 (fn [db _] (:awes-page db)))

(re-frame/reg-sub
 :filter?
 (fn [db _] (:filter db)))

(re-frame/reg-sub
 :view?
 (fn [db _] (:view db)))

(re-frame/reg-sub
 :reverse-sort?
 (fn [db _] (:reverse-sort db)))

(re-frame/reg-sub
 :repos?
 (fn []
   (let [repos0 @repos
         repos  (case @(re-frame/subscribe [:sort-repos-by?])
                  :forks (sort-by :f repos0)
                  :score (sort-by :a repos0)
                  ;; FIXME: remove useless
                  ;; :issues (sort-by :i repos0)
                  :date  (sort #(compare (js/Date. (.parse js/Date (:u %1)))
                                           (js/Date. (.parse js/Date (:u %2))))
                                 repos0)
                  repos0)]
     (apply-repos-filters (if @(re-frame/subscribe [:reverse-sort?])
                            repos
                            (reverse repos))))))

(re-frame/reg-sub
 :orgas?
 (fn []
   (let [orgs  @orgas
         orgas (case @(re-frame/subscribe [:sort-orgas-by?])
                 :repos (sort-by :r orgs)
                 :date  (sort
                         #(compare
                           (js/Date. (.parse js/Date (or (:c %2) unix-epoch)))
                           (js/Date. (.parse js/Date (or (:c %1) unix-epoch))))
                         orgs)
                 orgs)]
     (apply-orgas-filters
      (if @(re-frame/subscribe [:reverse-sort?])
        orgas
        (reverse orgas))))))

;; Pagination

(defn change-page [type next]
  (let [conf
        (condp = type
          :repos {:sub :repos-page? :evt      :repos-page!
                  :cnt :repos?      :per-page repos-per-page}
          :orgas {:sub :orgas-page? :evt      :orgas-page!
                  :cnt :orgas?      :per-page orgas-per-page})
        evt         (:evt conf)
        per-page    (:per-page conf)
        cnt         @(re-frame/subscribe [(:cnt conf)])
        page        @(re-frame/subscribe [(:sub conf)])
        count-pages (count (partition-all per-page cnt))]
    (cond
      (= next "first")
      (re-frame/dispatch [evt 0])
      (= next "last")
      (re-frame/dispatch [evt (dec count-pages)])
      (and (< page (dec count-pages)) next)
      (re-frame/dispatch [evt (inc page)])
      (and (pos? page) (not next))
      (re-frame/dispatch [evt (dec page)]))))

(defn navigate-pagination [type first-disabled last-disabled current-page total-pages]
  [:div.fr-grid-row.fr-grid-row--center
   [:nav.fr-pagination {:role "navigation" :aria-label "Pagination"}
    [:ul.fr-pagination__list
     [:li
      [:button.fr-pagination__link.fr-pagination__link--first
       {:on-click #(change-page type "first")
        :disabled first-disabled}]]
     [:li
      [:button.fr-pagination__link.fr-pagination__link--prev
       {:on-click #(change-page type nil)
        :disabled first-disabled}]]
     [:li
      [:button.fr-pagination__link.fr
       {:disabled true}
       (str (inc current-page) "/"
            (if (> total-pages 0) total-pages 1))]]
     [:li
      [:button.fr-pagination__link.fr-pagination__link--next
       {:on-click #(change-page type true)
        :disabled last-disabled}]]
     [:li
      [:button.fr-pagination__link.fr-pagination__link--last
       {:on-click #(change-page type "last")
        :disabled last-disabled}]]]]])

;; Home page

(defn home-page [lang]
  [:div.fr-grid
   [:div.fr-grid-row.fr-grid-row--center
    [:div.fr-col-6.fr-p-2w
     [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
      [:div.fr-card__body
       [:div.fr-card__title
        [:a.fr-card__link
         {:href  "#/repos"
          :title (i/i lang [:repos-of-source-code])}
         (i/i lang [:Repos])]]
       [:div.fr-card__desc (i/i lang [:home-repos-desc])]]
      [:div.fr-card__img.fr-col-3
       [:img.fr-responsive-img {:src "./img/repositories.jpg" :alt ""}]]]]
    [:div.fr-col-6.fr-p-2w
     [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
      [:div.fr-card__body
       [:div.fr-card__title
        [:a.fr-card__link
         {:href  "#/groups"
          :title (i/i lang [:Orgas])}
         (i/i lang [:Orgas])]]
       [:div.fr-card__desc (i/i lang [:home-orgas-desc])]]
      [:div.fr-card__img.fr-col-3
       [:img.fr-responsive-img {:src "./img/organizations.jpg" :alt ""}]]]]
    [:div.fr-col-6.fr-p-2w
     [:div.fr-card.fr-enlarge-link
      [:div.fr-card__body
       [:div.fr-card__title
        [:a.fr-card__link
         {:href  "#/stats"
          :title (i/i lang [:stats-expand])}
         (i/i lang [:Stats])]]
       [:div.fr-card__desc (i/i lang [:home-stats-desc])]]]]
    [:div.fr-col-6.fr-p-2w
     [:div.fr-card.fr-enlarge-link
      [:div.fr-card__body
       [:div.fr-card__title
        [:a.fr-card__link {:href "#/about"} (i/i lang [:About])]]
       [:div.fr-card__desc  (i/i lang [:home-about-desc])]]]]]])

;; Main structure - repos

(defn repos-table [lang repos-cnt]
  (if (zero? repos-cnt)
    (if (zero? (count @repos))
      [:div.fr-m-3w [:p (i/i lang [:Loading])]]
      [:div.fr-m-3w [:p (i/i lang [:no-repo-found])]])
    (let [rep-f      @(re-frame/subscribe [:sort-repos-by?])
          repos-page @(re-frame/subscribe [:repos-page?])
          repos      @(re-frame/subscribe [:repos?])]
      [:div.fr-table.fr-table--no-caption
       [:table
        [:caption (i/i lang [:repos-of-source-code])]
        [:thead.fr-grid.fr-col-12
         [:tr
          [:th.fr-col (i/i lang [:Repos])]
          [:th.fr-col (i/i lang [:Orgas])]
          [:th.fr-col (i/i lang [:description])]
          [:th.fr-col-1
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class    (when (= rep-f :date) "fr-btn--secondary")
             :title    (i/i lang [:sort-update-date])
             :on-click #(re-frame/dispatch [:sort-repos-by! :date])}
            (i/i lang [:update-short])]]
          [:th.fr-col-1
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class    (when (= rep-f :forks) "fr-btn--secondary")
             :title    (i/i lang [:sort-forks])
             :on-click #(re-frame/dispatch [:sort-repos-by! :forks])}
            (i/i lang [:forks])]]
          [:th.fr-col-1
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class    (when (= rep-f :score) "fr-btn--secondary")
             :title    (i/i lang [:sort-score])
             :on-click #(re-frame/dispatch [:sort-repos-by! :score])}
            (i/i lang [:Score])]]]]
        (into [:tbody]
              (for [dd (take repos-per-page
                             (drop (* repos-per-page repos-page) repos))]
                ^{:key dd}
                (let [{:keys [d                  ; description
                              f                  ; forks_count
                              a                  ; codegouvfr "awesome" score
                              li                 ; license
                              n                  ; name
                              fn                 ; full-name
                              o                  ; organization_name
                              u                  ; last_update
                              p                  ; forge
                              ]} dd
                      r          (str o "/" n)
                      group      (subs r 0 (- (count r) (inc (count n))))]
                  [:tr
                   ;; Repo (orga)
                   [:td
                    [:span
                     [:a.fr-raw-link.fr-icon-terminal-box-line
                      {:title  (i/i lang [:go-to-data])
                       :target "new"
                       :href   (str "https://data.code.gouv.fr/api/v1/hosts/" p "/repositories/" fn)}]
                     [:span " "]
                     [:a {:href   r
                          :target "_blank"
                          :rel    "noreferrer noopener"
                          :title  (new-tab
                                   (str
                                    (i/i lang [:go-to-repo])
                                    (when li (str (i/i lang [:under-license]) li))) lang)}
                      n]]]
                   [:td [:a.fr-raw-link.fr-link
                         {:href  (rfe/href :repos {:lang lang} {:g group})
                          :title (i/i lang [:browse-repos-orga])}
                         (or (last (re-matches #".+/([^/]+)/?" o)) "")]]
                   ;; Description
                   [:td [:span d]]
                   ;; Update
                   [:td
                    {:style {:text-align "center"}}
                    [:span
                     (if-let [d (to-locale-date u lang)]
                       [:a
                        {:href   (str (:swh-baseurl urls) r)
                         :target "new"
                         :title  (new-tab (i/i lang [:swh-link]) lang)
                         :rel    "noreferrer noopener"}
                        d]
                       "N/A")]]
                   ;; Forks
                   [:td {:style {:text-align "center"}} f]
                   ;; Awesome codegouvfr score
                   [:td {:style {:text-align "center"}} a]])))]])))

(defn repos-page [lang license language]
  (let [repos          @(re-frame/subscribe [:repos?])
        repos-pages    @(re-frame/subscribe [:repos-page?])
        count-pages    (count (partition-all repos-per-page repos))
        f              @(re-frame/subscribe [:filter?])
        platform       (:platform f)
        first-disabled (zero? repos-pages)
        last-disabled  (= repos-pages (dec count-pages))
        mapping        (:repos mappings)]
    [:div.fr-grid
     [:div.fr-grid-row
      ;; RSS feed
      [:a.fr-raw-link.fr-link.fr-m-1w
       {:title (i/i lang [:rss-feed])
        :href  "/data/latest.xml"}
       [:span.fr-icon-rss-line {:aria-hidden true}]]
      ;; Download link
      [:button.fr-link.fr-m-1w
       {:title    (i/i lang [:download])
        :on-click #(download-as-csv!
                    (map
                     (fn [r] (set/rename-keys (select-keys r (keys mapping)) mapping))
                     repos)
                    (str "codegouvfr-repositories-" (todays-date lang) ".csv"))}
       [:span.fr-icon-download-line {:aria-hidden true}]]
      ;; General information
      (table-header lang repos :repo)
      ;; Top pagination block
      [navigate-pagination :repos first-disabled last-disabled repos-pages count-pages]]
     ;; Specific repos search filters and options
     [:div.fr-grid-row
      [:input.fr-input.fr-col.fr-m-1w
       {:placeholder (i/i lang [:license])
        :value       @license
        :on-change   (fn [e]
                       (let [ev (.-value (.-target e))]
                         (reset! license ev)
                         (async/go
                           (async/<! (async/timeout timeout))
                           (async/>! filter-chan {:license ev}))))}]
      [:input.fr-input.fr-col.fr-m-1w
       {:value       @language
        :placeholder (i/i lang [:language])
        :on-change   (fn [e]
                       (let [ev (.-value (.-target e))]
                         (reset! language ev)
                         (async/go
                           (async/<! (async/timeout timeout))
                           (async/>! filter-chan {:language ev}))))}]
      [:select.fr-select.fr-col-3
       {:value (or platform "")
        :on-change
        (fn [e]
          (let [ev (.-value (.-target e))]
            (re-frame/dispatch [:filter! {:platform ev}])
            (async/go
              (async/<! (async/timeout timeout))
              (async/>! filter-chan {:platform ev}))))}
       [:option#default {:value ""} (i/i lang [:all-forges])]
       (for [x @platforms]
         ^{:key x}
         [:option {:value x} x])]
      [:div.fr-checkbox-group.fr-col.fr-m-1w
       [:input#1 {:type      "checkbox" :name "1"
                  :on-change #(let [v (.-checked (.-target %))]
                                (re-frame/dispatch [:filter! {:is-fork v}]))}]
       [:label.fr-label
        {:for   "1"
         :title (i/i lang [:only-fork-title])}
        (i/i lang [:only-fork])]]
      [:div.fr-checkbox-group.fr-col.fr-m-1w
       [:input#2 {:type      "checkbox" :name "2"
                  :on-change #(let [v (.-checked (.-target %))]
                                (re-frame/dispatch [:filter! {:is-licensed v}]))}]
       [:label.fr-label {:for "2" :title (i/i lang [:only-with-license-title])}
        (i/i lang [:only-with-license])]]
      [:div.fr-checkbox-group.fr-col.fr-m-1w
       [:input#4 {:type      "checkbox" :name "4"
                  :on-change #(let [v (.-checked (.-target %))]
                                (re-frame/dispatch [:filter! {:is-template v}]))}]
       [:label.fr-label
        {:for "4" :title (i/i lang [:only-template-title])}
        (i/i lang [:only-template])]]
      [:div.fr-checkbox-group.fr-col.fr-m-1w
       [:input#5 {:type      "checkbox" :name "5"
                  :on-change #(let [v (.-checked (.-target %))]
                                (re-frame/dispatch [:filter! {:is-contrib v}]))}]
       [:label.fr-label
        {:for "5" :title (i/i lang [:only-contrib-title])}
        (i/i lang [:only-contrib])]]
      [:div.fr-checkbox-group.fr-col.fr-m-1w
       [:input#6 {:type      "checkbox" :name "6"
                  :on-change #(let [v (.-checked (.-target %))]
                                (re-frame/dispatch [:filter! {:is-publiccode v}]))}]
       [:label.fr-label
        {:for "6" :title (i/i lang [:only-publiccode-title])}
        (i/i lang [:only-publiccode])]]]
     ;; Main repos table display
     [repos-table lang (count repos)]
     ;; Bottom pagination block
     [navigate-pagination :repos first-disabled last-disabled repos-pages count-pages]]))

(defn repos-page-class [lang license language]
  (reagent/create-class
   {:display-name   "repos-page-class"
    :component-did-mount
    (fn []
      (GET "/data/repositories.json"
           :handler
           #(reset! repos (map (comp bean clj->js) %))))
    :reagent-render (fn [] (repos-page lang license language))}))

;; Main structure - awesome

(defn awes-table [lang]
  (into
   [:div.fr-grid-row.fr-grid-row--gutters]
   (for [dd (shuffle @awes)]
     ^{:key dd}
     (let [{:keys [name url logo legal lastUpdated description fundedBy]}
           dd
           desc (:shortDescription (get description (keyword lang)))]
       [:div.fr-col-12.fr-col-md-3
        [:div.fr-card.fr-enlarge-link
         [:div.fr-card__header
          [:div.fr-card__img
           [:img.fr-responsive-img {:src logo :alt "" :data-fr-js-ratio true}]]]
         [:div.fr-card__body
          [:div.fr-card__content
           [:div.fr-card__start
            [:ul.fr-tags-group
             [:li [:p.fr-tag (str "License: " (:license legal))]]
             [:li [:p.fr-tag (str "Last updated: " lastUpdated)]]]]
           [:h3.fr-card__title
            [:a {:href url} name]]
           [:p.fr-card__desc desc]
           [:div.fr-card__end
            (when (not-empty fundedBy)
              [:p.fr-card__detail.fr-icon-warning-fill
               (str "Financé par : " (s/join ", " (map :name fundedBy)))])]]]]]))))

(defn awes-page [lang]
  [:div.fr-container.fr-mt-6w
   [:div.fr-grid-row
    [:div.fr-col-12
     [:div.fr-callout
      [:p.fr-callout__text (i/i lang [:Awesome-callout])]]
     [:div.fr-my-6w
      [awes-table lang]]]]])

(defn awes-page-class [lang]
  (reagent/create-class
   {:display-name   "awes-page-class"
    :component-did-mount
    (fn []
      (GET "/data/awesome-codegouvfr.json"
           :handler
           #(reset! awes (walk/keywordize-keys %))))
    :reagent-render (fn [] (awes-page lang))}))

;; Main structure - orgas

(defn orgas-table [lang orgas-cnt]
  (if (zero? orgas-cnt)
    (if (zero? (count @orgas))
      [:div.fr-m-3w [:p (i/i lang [:Loading])]]
      [:div.fr-m-3w [:p (i/i lang [:no-orga-found])]])
    (let [org-f @(re-frame/subscribe [:sort-orgas-by?])
          orgas @(re-frame/subscribe [:orgas?])]
      [:div.fr-table.fr-table--no-caption
       [:table
        [:caption (i/i lang [:Orgas])]
        [:thead.fr-grid.fr-col-12
         [:tr
          [:th.fr-col-1 "Image"]
          [:th.fr-col-2 (i/i lang [:Orgas])]
          [:th.fr-col-6 (i/i lang [:description])]
          [:th.fr-col-1
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class    (when (= org-f :repos) "fr-btn--secondary")
             :title    (i/i lang [:sort-repos])
             :on-click #(re-frame/dispatch [:sort-orgas-by! :repos])}
            (i/i lang [:Repos])]]
          [:th.fr-col-1
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class    (when (= org-f :date) "fr-btn--secondary")
             :title    (i/i lang [:sort-orgas-creation])
             :on-click #(re-frame/dispatch [:sort-orgas-by! :date])}
            (i/i lang [:created-at])]]]]
        (into [:tbody]
              (for [dd (take orgas-per-page
                             (drop (* orgas-per-page @(re-frame/subscribe [:orgas-page?]))
                                   orgas))]
                ^{:key dd}
                (let [{:keys [n        ; name
                              l        ; login
                              d        ; description
                              o        ; organization_url
                              ;; FIXME: Where to use this?
                              h        ; website
                              ;; FIXME: floss_policy missing?
                              f         ; floss_policy
                              ;; FIXME: used?
                              p         ; platform
                              au        ; avatar_url
                              c         ; creation_date
                              r         ; repositories_count
                              id        ; owner_url (data)
                              ]} dd]
                  [:tr
                   [:td (if au
                          (if (not-empty h)
                            [:a.fr-raw-link.fr-link
                             {:title (i/i lang [:orga-homepage])
                              :href  h}
                             [:img {:src au :width "100%" :alt ""}]]
                            [:img {:src au :width "100%" :alt ""}])
                          (when (not-empty h)
                            [:a.fr-raw-link.fr-link
                             {:title (i/i lang [:orga-homepage])
                              :href  h}
                             (i/i lang [:website])]))]
                   [:td
                    [:span
                     (when (not-empty f)
                       [:span
                        [:a.fr-raw-link
                         {:target "new"
                          :rel    "noreferrer noopener"
                          :title  (new-tab (i/i lang [:floss-policy]) lang)
                          :href   f}
                         [:img {:src "./img/floss.png" :width "25px"}]]
                        " "])
                     [:a.fr-raw-link.fr-icon-terminal-box-line
                      {:title  (i/i lang [:go-to-data])
                       :target "new"
                       :href   id}]
                     [:span " "]
                     [:a {:target "_blank"
                          :rel    "noreferrer noopener"
                          :title  (new-tab (i/i lang [:go-to-orga]) lang)
                          :href   o}
                      (or (not-empty n) l)]]]
                   [:td d]
                   [:td
                    {:style {:text-align "center"}}
                    [:a {:title (i/i lang [:go-to-repos])
                         :href  (rfe/href :repos {:lang lang}
                                          {:g (condp = p
                                                "GitHub"    o
                                                "SourceHut" (s/replace o "//" "//git.")
                                                ;; FIXME: what's the rationale?
                                                "GitLab"    (s/replace o "/groups/" "/")
                                                o)})}
                     r]]
                   [:td {:style {:text-align "center"}}
                    (to-locale-date c lang)]])))]])))

(defn orgas-page [lang]
  (let [orgas          @(re-frame/subscribe [:orgas?])
        ministry       (:ministry @(re-frame/subscribe [:filter?]))
        orgas-cnt      (count orgas)
        orgas-pages    @(re-frame/subscribe [:orgas-page?])
        count-pages    (count (partition-all orgas-per-page orgas))
        first-disabled (zero? orgas-pages)
        last-disabled  (= orgas-pages (dec count-pages))
        mapping        (:orgas mappings)]
    [:div.fr-grid
     [:div.fr-grid-row
      ;; RSS feed
      [:a.fr-raw-link.fr-link.fr-m-1w
       {:title (i/i lang [:rss-feed])
        :href  "/data/latest-organizations.xml"}
       [:span.fr-icon-rss-line {:aria-hidden true}]]
      ;; Download link
      [:button.fr-link.fr-m-1w
       {:title    (i/i lang [:download])
        :on-click #(download-as-csv!
                    (map
                     (fn [r] (set/rename-keys (select-keys r (keys mapping)) mapping))
                     orgas)
                    (str "codegouvfr-organizations-" (todays-date lang) ".csv"))}
       [:span.fr-icon-download-line {:aria-hidden true}]]
      ;; General information
      (table-header lang orgas :orga)
      ;; Top pagination block
      [navigate-pagination :orgas first-disabled last-disabled orgas-pages count-pages]]
     [:div.fr-grid-row
      [:select.fr-select.fr-col.fr-m-1w
       {:value (or ministry "")
        :on-change
        (fn [e]
          (let [ev (.-value (.-target e))]
            (re-frame/dispatch [:filter! {:ministry ev}])
            (async/go
              (async/<! (async/timeout timeout))
              (async/>! filter-chan {:ministry ev}))))}
       [:option#default {:value ""} (i/i lang [:all-ministries])]
       (for [x @(re-frame/subscribe [:ministries?])]
         ^{:key x}
         [:option {:value x} x])]]
     [orgas-table lang orgas-cnt]
     [navigate-pagination :orgas first-disabled last-disabled orgas-pages count-pages]]))

;; Tags page

(defn tags-page [lang]
  [:div.fr-grid
   [:div.fr-grid-row
    ;; RSS feed
    [:a.fr-raw-link.fr-link.fr-m-1w
     {:title (i/i lang [:rss-feed])
      :href  "/data/latest-tags.xml"}
     [:span.fr-icon-rss-line {:aria-hidden true}]]
    ;; General informations
    (table-header lang @tags :tag)]
   ;; Main tags display
   [:div.fr-table.fr-table--no-caption
    [:table
     [:caption (i/i lang [:Tags])]
     [:thead.fr-grid.fr-col-12
      [:tr
       [:th.fr-col-1 (i/i lang [:Repo])]
       [:th.fr-col-2 (i/i lang [:description])]
       [:th.fr-col-1 (i/i lang [:Tagname])]
       [:th.fr-col-1 (i/i lang [:update-short])]]]
     (into
      [:tbody]
      (for [dd @tags]
        ^{:key dd}
        (let [{:keys [repo_name repository title name date]} dd]
          [:tr
           [:td
            [:a.fr-link
             {:href   repository
              :target "_blank"
              :title  (i/i lang [:Repo])
              :rel    "noreferrer noopener"} repo_name]]
           [:td title]
           [:td
            [:a.fr-link
             {:href   (str repository "/releases/tag/" name)
              :target "_blank"
              :title  (i/i lang [:Tag])
              :rel    "noreferrer noopener"} name]]
           [:td (to-locale-date date lang)]])))]]])

;; Stats page

(defn stats-table [heading data thead]
  [:div.fr-m-3w
   [:h4.fr-h4 heading]
   [:div.fr-table.fr-table--no-caption
    [:table
     thead
     [:tbody
      (for [[k v] (walk/stringify-keys data)]
        ^{:key k}
        [:tr [:td k] [:td v]])]]]])

(defn stats-tile [l i s]
  [:div.fr-tile.fr-col-3
   [:div.fr-tile__body
    [:p.fr-tile__title (i/i l [i])]
    [:div.fr-tile__desc [:p.fr-h4 s]]]])

(defn stats-page
  [lang stats]
  (let [{:keys [repos_cnt orgas_cnt ;; libs_cnt deps_cnt
                avg_repos_cnt median_repos_cnt
                top_orgs_by_repos top_orgs_by_stars
                top_licenses top_languages top_topics
                top_forges top_ministries]} stats]
    [:div
     [:div.fr-grid-row.fr-grid-row--center
      {:style {:height "180px"}}
      (stats-tile lang :Orgas orgas_cnt)
      (stats-tile lang :repos-of-source-code repos_cnt)
      (stats-tile lang :mean-repos-by-orga avg_repos_cnt)
      (stats-tile lang :median-repos-by-orga median_repos_cnt)]
     [:div.fr-grid-row
      [:div.fr-col-6
       (stats-table [:span (i/i lang [:most-used-languages])]
                    (top-clean-up-repos top_languages "language")
                    [:thead [:tr [:th.fr-col-10 (i/i lang [:language])] [:th "%"]]])]
      [:div.fr-col-6
       (stats-table
        [:span (i/i lang [:topics])]
        top_topics
        [:thead [:tr [:th.fr-col-10 (i/i lang [:topics])]
                 [:th (i/i lang [:occurrences])]]])]]
     [:div.fr-grid-row
      [:div.fr-col-6
       (stats-table [:span (i/i lang [:most-used-identified-licenses])]
                    (top-clean-up-repos top_licenses "license")
                    [:thead [:tr [:th.fr-col-10 (i/i lang [:license])] [:th "%"]]])]
      [:div.fr-col-6
       [:div.fr-m-3w
        [:h4.fr-h4 (i/i lang [:most-used-identified-licenses])]
        [:img {:src      "/data/top_licenses.svg" :width "100%"
               :longdesc (i/i lang [:most-used-identified-licenses])
               :alt      (i/i lang [:most-used-identified-licenses])}]]]]
     [:div.fr-grid-row
      [:div.fr-col-6
       (stats-table [:span
                     (i/i lang [:Orgas])
                     (i/i lang [:with-more-of])
                     (i/i lang [:repos])]
                    (top-clean-up-orgas top_orgs_by_repos "q")
                    [:thead [:tr [:th.fr-col-10 (i/i lang [:Orgas])]
                             [:th (i/i lang [:Repos])]]])]
      [:div.fr-col-6
       (stats-table [:span
                     (i/i lang [:Orgas])
                     (i/i lang [:with-more-of*])
                     (i/i lang [:stars])]
                    (top-clean-up-orgas top_orgs_by_stars "q")
                    [:thead [:tr [:th.fr-col-10 (i/i lang [:Orgas])]
                             [:th (i/i lang [:Stars])]]])]]
     [:div.fr-grid-row
      [:div.fr-col-6
       (stats-table (i/i lang [:top-forges])
                    (top-clean-up-repos top_forges "platform")
                    [:thead [:tr [:th.fr-col-10 (i/i lang [:forge])]
                             [:th (i/i lang [:Repos])]]])]
      [:div.fr-col-6
       (stats-table (i/i lang [:top-ministries])
                    (top-clean-up-orgas top_ministries "ministry")
                    [:thead [:tr [:th.fr-col-10 (i/i lang [:ministry])]
                             [:th (i/i lang [:Repos])]]])]]]))

(defn stats-page-class [lang]
  (let [stats (reagent/atom nil)]
    (reagent/create-class
     {:display-name   "stats-page-class"
      :component-did-mount
      (fn []
        (GET "/data/stats.json"
             :handler #(reset! stats (walk/keywordize-keys %))))
      :reagent-render (fn [] (stats-page lang @stats))})))

;; Main structure elements

(def q (reagent/atom nil))
(def license (reagent/atom nil))
(def language (reagent/atom nil))

(defn reset-queries []
  (reset! q nil)
  (reset! license nil)
  (reset! language nil)
  (reset! dp-filter nil)
  (re-frame/dispatch [:filter! {:q "" :license "" :language ""}]))

(defn banner [lang]
  (let [path @(re-frame/subscribe [:path?])]
    [:header.fr-header {:role "banner"}
     [:div.fr-header__body
      [:div.fr-container
       [:div.fr-header__body-row
        [:div.fr-header__brand.fr-enlarge-link
         [:div.fr-header__brand-top
          [:div.fr-header__logo
           [:p.fr-logo "République" [:br] "Française"]]
          [:div.fr-header__navbar
           [:button#fr-btn-menu-mobile.fr-btn--menu.fr-btn
            {:data-fr-opened false
             :aria-controls  "modal-833"
             :aria-haspopup  "menu"
             :title          "Menu"}
            "Menu"]]]
         [:div.fr-header__service
          [:a {:href "./"} ;; FIXME
           [:div.fr-header__service-title
            [:svg {:width "240px" :viewBox "0 0 299.179 49.204"}
             [:path {:fill "#808080" :d "M5.553 2.957v2.956h4.829V0H5.553Zm5.554 0v2.956h4.829V0h-4.829Zm5.553 0v2.956h4.587V0H16.66zm5.553 0v2.956h4.829V0h-4.829zm76.057 0v2.956h4.829V0H98.27zm5.553 0v2.956h4.829V0h-4.829zm53.843 0v2.956h4.829V0h-4.829zm5.794 0v2.956h4.588V0h-4.587zm5.313 0v2.956h4.829V0h-4.829zm5.794 0v2.956h4.588V0h-4.588zM0 10.27v3.112h4.854l-.073-3.05-.073-3.018-2.342-.094L0 7.127zm5.553 0v3.143l2.367-.093 2.342-.093V7.314L7.92 7.22l-2.367-.093zm16.66 0v3.112h4.853l-.072-3.05-.072-3.018-2.343-.094-2.366-.093zm5.554 0v3.112h4.587V7.158h-4.587zm70.672-2.894c-.097.093-.17 1.494-.17 3.112v2.894h4.83V7.158h-2.246c-1.255 0-2.342.093-2.414.218zm5.553-.031c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm48.362 2.925v3.112h4.588V7.158h-4.588zm5.481-2.894c-.097.093-.17 1.494-.17 3.112v2.894h4.83V7.158h-2.246c-1.255 0-2.342.093-2.414.218zm16.732 2.894v3.112h4.588V7.158h-4.588zm5.553 0v3.112h4.588V7.158h-4.587zM0 17.428v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019H0Zm5.553 0v3.143l2.367-.093 2.342-.093v-5.913l-2.342-.094-2.367-.093zm38.197-.093.073 3.05h4.587l.073-3.05.072-3.019h-4.877zm5.554 0 .072 3.05h4.588l.072-3.05.073-3.019H49.23zm5.505.093v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019h-4.853zm5.601-.093.073 3.05h4.587l.073-3.05.072-3.019h-4.877zm21.248 0 .072 3.05h4.588l.072-3.05.073-3.019h-4.878zm5.553 0 .073 3.05 2.366.093 2.342.093v-6.255h-4.853zm5.505.093v3.143l2.366-.093 2.343-.093.072-3.05.072-3.019h-4.853zm5.602-.093.072 3.05 2.367.093 2.342.093v-6.255h-4.854zm5.553 0 .073 3.05h4.587l.073-3.05.072-3.019h-4.877zm15.936 0 .072 3.05h4.588l.072-3.05.073-3.019h-4.878zm5.553 0 .073 3.05 2.366.093 2.342.093v-6.255h-4.853zm5.553 0 .073 3.05h4.587l.073-3.05.072-3.019h-4.877zm5.747.093v3.112h4.587v-6.224h-4.587zm15.694 0v3.112h4.588v-6.224h-4.588zm5.36-.093.073 3.05h4.587l.073-3.05.072-3.019h-4.877zm38.342.093v3.112h4.588v-6.224h-4.588zm5.554 0v3.112h4.587v-6.224h-4.587zm5.553 0v3.112h4.587v-6.224h-4.587zm5.553 0v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019h-4.853zm15.936 0v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019h-4.853zm5.601-.093.073 3.05h4.587l.073-3.05.072-3.019h-4.877zm16.66 0 .073 3.05h4.587l.073-3.05.072-3.019h-4.877zm5.505.093v3.143l2.367-.093 2.342-.093.072-3.05.073-3.019h-4.854zm10.142 0v3.143l2.365-.093 2.343-.093.072-3.05.072-3.019h-4.853zm5.6-.093.073 3.05h4.588l.072-3.05.073-3.019h-4.878zm16.66 0 .073 3.05 2.366.093 2.342.093v-6.255h-4.853zm5.506.093v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019h-4.853zM0 24.742v2.956h4.829v-5.913H0Zm5.553 0v2.956h4.829v-5.913H5.553Zm32.596 0v2.956h4.829v-5.913h-4.829zm5.553 0v2.956h4.829v-5.913h-4.829zm16.66 0v2.956h4.829v-5.913h-4.829zm5.553 0v2.956h4.829v-5.913h-4.829zm10.141 0v2.956h4.829v-5.913h-4.829zm5.554 0v2.956h4.829v-5.913H81.61zm16.66 0v2.956h4.829v-5.913H98.27zm5.553 0v2.956h4.829v-5.913h-4.829zm10.382 0v2.956h4.829v-5.913h-4.829zm5.554 0v2.956h4.828v-5.913h-4.828zm16.901 0v2.956h4.587v-5.913h-4.587zm5.312 0v2.956h4.828v-5.913h-4.828zm10.382 0v2.956h4.588v-5.913h-4.588zm5.312 0v2.956h4.829v-5.913h-4.829zm11.107 0v2.956h4.829v-5.913h-4.829zm5.794 0v2.956h4.588v-5.913h-4.588zm5.553 0v2.956h4.588v-5.913h-4.587zm10.383 0v2.956h4.829v-5.913h-4.829zm5.553 0v2.956h4.588v-5.913h-4.588zm16.66 0v2.956h4.829v-5.913h-4.829zm5.554 0v2.956h4.587v-5.913h-4.587zm10.382 0v2.956h4.828v-5.913h-4.828zm5.553 0v2.956h4.829v-5.913h-4.829zm16.66 0v2.956h4.829v-5.913h-4.829zm5.553 0v2.956h4.829v-5.913h-4.829zm10.142 0v2.956h4.828v-5.913h-4.828zm5.553 0v2.956h4.829v-5.913h-4.829zm16.66 0v2.956h4.828v-5.913h-4.828zm5.553 0v2.956h4.829v-5.913h-4.829zM0 31.744v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094L0 28.601zm5.553 0v3.144l2.367-.094 2.342-.093v-5.913l-2.342-.094-2.367-.093zm32.596 0v3.112h4.829v-6.224h-4.829zm5.553 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm16.66 0v3.112h4.829v-6.224h-4.829zm5.553 0v3.144l2.367-.094 2.342-.093v-5.913l-2.342-.094-2.367-.093zm10.141 0v3.112h4.829v-6.224h-4.829zm5.554 0v3.112h4.829v-6.224H81.61zm16.756-2.707c-.072.249-.096 1.618-.048 3.05l.072 2.614 2.367.093 2.342.094v-6.256h-2.294c-1.714 0-2.342.125-2.439.405zm5.457 2.707v3.112h4.829v-6.224h-4.829zm10.479-2.707c-.073.249-.097 1.618-.049 3.05l.073 2.614 2.366.093 2.342.094v-6.256h-2.294c-1.714 0-2.342.125-2.438.405zm5.457 2.707v3.112h4.828v-6.224h-4.828zm5.649-2.707c-.072.249-.096 1.618-.048 3.05l.073 2.614 2.366.093 2.342.094v-6.256h-2.294c-1.714 0-2.342.125-2.439.405zm5.457 2.707v3.112h4.829v-6.224h-4.829zm5.795 0v3.112h4.587v-6.224h-4.587zm5.408-2.707c-.072.249-.096 1.618-.048 3.05l.073 2.614 2.366.093 2.342.094v-6.256h-2.294c-1.714 0-2.342.125-2.439.405zm10.286 2.707v3.112h4.588v-6.224h-4.588zm5.409-2.707c-.073.249-.097 1.618-.049 3.05l.073 2.614 2.366.093 2.342.094v-6.256H160.2c-1.714 0-2.342.125-2.438.405zm16.804 2.707v3.112h4.588v-6.224h-4.588zm5.553 0v3.112h4.588v-6.224h-4.587zm10.383 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm5.553 0v3.112h4.588v-6.224h-4.588zm16.66 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm5.554 0v3.112h4.587v-6.224h-4.587zm10.382 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm5.553 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm16.66 0v3.112h4.829v-6.224h-4.829zm5.553 0v3.144l2.367-.094 2.342-.093v-5.913l-2.342-.094-2.367-.093zm10.142 0v3.112h4.828v-6.224h-4.828zm5.553 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm16.66 0v3.112h4.828v-6.224h-4.828zm5.553 0v3.112h4.829v-6.224h-4.829zM0 38.747v2.956h4.829V35.79H0Zm5.553 0v2.956h4.829V35.79H5.553Zm16.66 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.587V35.79h-4.587zm10.382 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.829V35.79h-4.829zm16.66 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.829V35.79h-4.829zm10.141 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.829V35.79H81.61zm16.66 0v2.956h4.829V35.79H98.27zm5.553 0v2.956h4.829V35.79h-4.829zm10.382 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.828V35.79h-4.828zm32.595 0v2.956h4.588V35.79h-4.588zm5.312 0v2.956h4.829V35.79h-4.829zm16.901 0v2.956h4.588V35.79h-4.588zm5.553 0v2.956h4.588V35.79h-4.587zm10.383 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.588V35.79h-4.588zm16.66 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.587V35.79h-4.587zm10.382 0v2.956h4.828V35.79h-4.828zm5.553 0v2.956h4.829V35.79h-4.829zm16.66 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.829V35.79h-4.829zm15.695 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.828V35.79h-4.828zm5.553 0v2.956h4.828V35.79h-4.828zM5.553 46.06v3.144l2.367-.094 2.342-.093v-5.913L7.92 43.01l-2.367-.093zm5.554 0v3.112h4.853l-.073-3.05-.072-3.018-2.342-.094-2.366-.093zm5.553 0v3.112h4.587v-6.224H16.66zm5.722-2.925c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.072-3.018-2.174-.094c-1.207-.03-2.27 0-2.366.125zm21.489 0c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.554 0c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.073-3.05-.072-3.018-2.173-.094c-1.208-.03-2.27 0-2.366.125zm5.384 2.925v3.112h4.853l-.072-3.05-.073-3.018-2.342-.094-2.366-.093zm5.722-2.925c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm21.248 0c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.073-3.05-.072-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.553.031c-.097.093-.17 1.494-.17 3.112v2.894h4.83v-6.224h-2.246c-1.255 0-2.342.093-2.414.218zm5.384 2.894v3.112h4.853l-.072-3.05-.072-3.018-2.343-.094-2.366-.093zm5.723-2.894c-.097.093-.17 1.494-.17 3.112v2.894h4.83v-6.224h-2.246c-1.255 0-2.342.093-2.414.218zm5.553-.031c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm15.936 0c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.073-3.05-.072-3.018-2.173-.094c-1.208-.03-2.27 0-2.366.125zm5.552.031c-.096.093-.168 1.494-.168 3.112v2.894h4.828v-6.224h-2.246c-1.255 0-2.342.093-2.414.218zm5.554-.031c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.626 2.925v3.112h4.587v-6.224h-4.587zm21.175-2.925c-.097.124-.17 1.525-.17 3.143v2.894h4.855l-.073-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.625 2.925v3.112h4.588v-6.224h-4.587zm5.482-2.894c-.097.093-.17 1.494-.17 3.112v2.894h4.83v-6.224h-2.246c-1.255 0-2.342.093-2.414.218zm5.625 2.894v3.112h4.588v-6.224h-4.588zm21.489 0v3.112h4.588v-6.224h-4.588zm5.554 0v3.112h4.587v-6.224h-4.587zm5.553 0v3.112h4.587v-6.224h-4.587zm5.553 0v3.112h4.853l-.072-3.05-.073-3.018-2.342-.094-2.366-.093zm21.658-2.925c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.384 2.925v3.112h4.854l-.073-3.05-.072-3.018-2.342-.094-2.367-.093zm5.554 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm5.722-2.925c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm26.801 0c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.385 2.925v3.112h4.852l-.072-3.05-.073-3.018-2.342-.094-2.366-.093z"}]]]]
          [:p.fr-header__service-tagline (i/i lang [:index-title])]]]
        [:div.fr-header__tools
         [:div.fr-header__tools-links
          [:ul.fr-links-group
           [:li [:a.fr-link.fr-icon-mastodon-line
                 {:rel   "me"
                  :href  "https://social.numerique.gouv.fr/@codegouvfr"
                  :title (i/i lang [:mastodon-follow])} "@codegouvfr"]]
           [:li [:a.fr-link.fr-icon-twitter-x-line
                 {:rel   "me"
                  :href  "https://x.com/codegouvfr"
                  :title (i/i lang [:twitter-follow])} "@codegouvfr"]]
           [:li [:a.fr-link {:href "#/feeds"} (i/i lang [:rss-feed])]]
           [:li [:button.fr-link.fr-icon-theme-fill.fr-link--icon-left
                 {:aria-controls  "fr-theme-modal"
                  :title          (str (i/i lang [:modal-title]) " - "
                                       (i/i lang [:new-modal]))
                  :data-fr-opened false}
                 (i/i lang [:modal-title])]]]]]]]]
     ;; Header menu
     [:div#modal-833.fr-header__menu.fr-modal
      {:aria-labelledby "fr-btn-menu-mobile"}
      [:div.fr-container
       [:button.fr-link--close.fr-link
        {:aria-controls "modal-833"} (i/i lang [:close])]
       [:div.fr-header__menu-links]
       [:nav#navigation-832.fr-nav {:role "navigation" :aria-label "Principal"}
        [:ul.fr-nav__list
         [:li.fr-nav__item
          [:button.fr-nav__link
           {:aria-current (when (= path "/") "page")
            :on-click     #(rfe/push-state :home)}
           (i/i lang [:home])]]
         [:li.fr-nav__item
          [:button.fr-nav__link
           {:aria-current (when (= path "/awesome") "page")
            :title        "Awesome"
            :on-click
            #(do (reset-queries) (rfe/push-state :awes {:lang lang}))}
           "Awesome"]]
         [:li.fr-nav__item
          [:button.fr-nav__link
           {:aria-current (when (= path "/repos") "page")
            :title        (i/i lang [:repos-of-source-code])
            :on-click
            #(do (reset-queries) (rfe/push-state :repos {:lang lang}))}
           (i/i lang [:Repos])]]
         [:li.fr-nav__item
          [:button.fr-nav__link
           {:aria-current (when (= path "/groups") "page")
            :on-click
            #(do (reset-queries) (rfe/push-state :orgas {:lang lang}))}
           (i/i lang [:Orgas])]]
         [:li.fr-nav__item
          [:a.fr-nav__link
           {:aria-current (when (= path "/tags") "page")
            :title        (i/i lang [:Tags])
            :href         "#/tags"}
           (i/i lang [:Tags])]]
         [:li.fr-nav__item
          [:a.fr-nav__link
           {:aria-current (when (= path "/stats") "page")
            :title        (i/i lang [:stats-expand])
            :href         "#/stats"}
           (i/i lang [:Stats])]]
         [:li.fr-nav__item
          [:a.fr-nav__link
           {:aria-current (when (= path "/about") "page")
            :href         "#/about"}
           (i/i lang [:About])]]
         [:li.fr-nav__item
          [:a.fr-nav__link
           {:href   "/fr/mission"
            :target "_blank"
            :rel    "noreferrer noopener"}
           (i/i lang [:codegouvfr])]]]]]]]))

(defn subscribe [lang]
  [:div.fr-follow
   [:div.fr-container
    [:div.fr-grid-row
     [:div.fr-col-12.fr-col-md-2
      [:div.fr-follow__special
       [:div
        [:h1.fr-h5.fr-follow__title (i/i lang [:contact])]
        [:div.fr-text--sm.fr-follow__desc
         (i/i lang [:contact-title])]]]]
     [:div.fr-col-12.fr-col-md-4
      [:div.fr-follow__newsletter
       [:div
        [:h1.fr-h5.fr-follow__title (i/i lang [:bluehats])]
        [:p.fr-text--sm.fr-follow__desc
         (i/i lang [:bluehats-desc])]
        [:a.fr-btn
         {:type "button"
          :href "https://code.gouv.fr/newsletters/subscribe/bluehats@mail.codegouv.fr"}
         (i/i lang [:subscribe])]]]]
     [:div.fr-col-12.fr-col-md-4
      [:div.fr-follow__newsletter
       [:div
        [:h1.fr-h5.fr-follow__title (i/i lang [:cio-floss])]
        [:p.fr-text--sm.fr-follow__desc
         (i/i lang [:cio-floss-desc])]
        [:a.fr-btn
         {:type "button"
          :href "https://code.gouv.fr/newsletters/subscribe/logiciels-libres-dsi@mail.codegouv.fr"}
         (i/i lang [:subscribe])]]]]
     ;; Follow elsewhere
     [:div.fr-col-12.fr-col-md-2
      [:div.fr-share
       [:p.fr-h5.fr-mb-3v (i/i lang [:find-us])]
       [:div.fr-share__group
        [:a.fr-share__link
         {:href       "https://social.numerique.gouv.fr/@codegouvfr"
          :aria-label (i/i lang [:mastodon-follow])
          :title      (new-tab (i/i lang [:mastodon-follow]) lang)
          :rel        "noreferrer noopener me"
          :target     "_blank"}
         "Mastodon"]
        [:a.fr-share__link
         {:href       "https://x.com/codegouvfr"
          :aria-label (i/i lang [:twitter-follow])
          :title      (new-tab (i/i lang [:twitter-follow]) lang)
          :rel        "noreferrer noopener me"
          :target     "_blank"}
         "Twitter"]]]]]]])

(defn footer [lang]
  [:footer.fr-footer {:role "contentinfo"}
   [:div.fr-container
    [:div.fr-footer__body
     [:div.fr-footer__brand.fr-enlarge-link
      [:a {:on-click #(rfe/push-state :home)
           :title    (i/i lang [:home])}
       [:p.fr-logo "République" [:br] "Française"]]]
     [:div.fr-footer__content
      [:p.fr-footer__content-desc (i/i lang [:footer-desc])
       [:a {:href "https://code.gouv.fr"}
        (i/i lang [:footer-desc-link])]]
      [:ul.fr-footer__content-list
       [:li.fr-footer__content-item
        [:a.fr-footer__content-link
         {:href "https://info.gouv.fr"} "info.gouv.fr"]]
       [:li.fr-footer__content-item
        [:a.fr-footer__content-link
         {:href "https://service-public.fr"} "service-public.fr"]]
       [:li.fr-footer__content-item
        [:a.fr-footer__content-link
         {:href "https://legifrance.gouv.fr"} "legifrance.gouv.fr"]]
       [:li.fr-footer__content-item
        [:a.fr-footer__content-link
         {:href "https://data.gouv.fr"} "data.gouv.fr"]]]]]
    [:div.fr-footer__bottom
     [:ul.fr-footer__bottom-list
      [:li.fr-footer__bottom-item
       [:button.fr-footer__bottom-link
        {:lang     (if (= lang "fr") "en" "fr")
         :on-click #(re-frame/dispatch
                     [:lang! (if (= lang "fr") "en" "fr")])}
        (i/i lang [:switch-lang])]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href "#/a11y"}
        (i/i lang [:accessibility])]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href "#/legal"}
        (i/i lang [:legal])]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href "#/legal"}
        (i/i lang [:personal-data])]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href "#/sitemap"}
        (i/i lang [:sitemap])]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href  "#/feeds"
         :title (i/i lang [:subscribe-rss-flux])}
        (i/i lang [:rss-feed])]]
      [:li.fr-footer__bottom-item
       [:button.fr-footer__bottom-link.fr-icon-theme-fill.fr-link--icon-left
        {:aria-controls  "fr-theme-modal"
         :title          (str (i/i lang [:modal-title]) " - "
                              (i/i lang [:new-modal]))
         :data-fr-opened false}
        (i/i lang [:modal-title])]]]]]])

(defn display-parameters-modal [lang]
  [:dialog#fr-theme-modal.fr-modal
   {:role "dialog" :aria-labelledby "fr-theme-modal-title"}
   [:div.fr-container.fr-container--fluid.fr-container-md
    [:div.fr-grid-row.fr-grid-row--center
     [:div.fr-col-12.fr-col-md-6.fr-col-lg-4
      [:div.fr-modal__body
       [:div.fr-modal__header
        [:button.fr-link--close.fr-link {:aria-controls "fr-theme-modal"}
         (i/i lang [:modal-close])]]
       [:div.fr-modal__content
        [:h1#fr-theme-modal-title.fr-modal__title
         (i/i lang [:modal-title])]
        [:div#fr-display.fr-form-group.fr-display
         [:fieldset.fr-fieldset
          [:legend#-legend.fr-fieldset__legend.fr-text--regular
           (i/i lang [:modal-select-theme])]
          [:div.fr-fieldset__content
           [:div.fr-radio-group.fr-radio-rich
            [:input#fr-radios-theme-light
             {:type "radio" :name "fr-radios-theme" :value "light"}]
            [:label.fr-label {:for "fr-radios-theme-light"}
             (i/i lang [:modal-theme-light])]
            [:div.fr-radio-rich__img {:data-fr-inject true}
             [:img {:src "./img/artwork/light.svg"}]]]
           [:div.fr-radio-group.fr-radio-rich
            [:input#fr-radios-theme-dark
             {:type "radio" :name "fr-radios-theme" :value "dark"}]
            [:label.fr-label {:for "fr-radios-theme-dark"}
             (i/i lang [:modal-theme-dark])]
            [:div.fr-radio-rich__img {:data-fr-inject true}
             [:img {:src "./img/artwork/dark.svg"}]]]
           [:div.fr-radio-group.fr-radio-rich
            [:input#fr-radios-theme-system
             {:type "radio" :name "fr-radios-theme" :value "system"}]
            [:label.fr-label {:for "fr-radios-theme-system"}
             (i/i lang [:modal-theme-system])]
            [:div.fr-radio-rich__img {:data-fr-inject true}
             [:img {:src "./img/artwork/system.svg"}]]]]]]]]]]]])

;; Main pages functions

(defn main-menu [lang view]
  [:div
   [:div.fr-grid-row.fr-mt-2w
    [:div.fr-col-12
     (when (some #{:repos :orgas} [view])
       [:input.fr-input
        {:placeholder (i/i lang [:free-search])
         :aria-label  (i/i lang [:free-search])
         :value       @q
         :on-change   (fn [e]
                        (let [ev (.-value (.-target e))]
                          (reset! q ev)
                          (async/go
                            (async/<! (async/timeout timeout))
                            (async/>! filter-chan {:q ev}))))}])]
    (when-let [flt (-> @(re-frame/subscribe [:filter?])
                       (dissoc :is-fork :is-publiccode :is-contrib
                               :is-template :is-licensed :is-esr))]
      [:div.fr-col-8.fr-grid-row.fr-m-1w
       (when-let [ff (not-empty (:g flt))]
         (close-filter-button lang ff :repos (merge flt {:g nil})))])]])

(defn main-page []
  (let [lang @(re-frame/subscribe [:lang?])
        view @(re-frame/subscribe [:view?])]
    [:div
     (banner lang)
     [:main#main.fr-container.fr-container--fluid.fr-mb-3w
      {:role "main"}
      [main-menu lang view]
      (condp = view
        :home    [home-page lang]
        :orgas   [orgas-page lang]
        :repos   [repos-page-class lang license language]
        :awes    [awes-page-class lang]
        :stats   [stats-page-class lang]
        :tags    [tags-page lang]
        :legal   (condp = lang "fr" (inline-page "legal.fr.md")
                        (inline-page "legal.en.md"))
        :a11y    (condp = lang "fr" (inline-page "a11y.fr.md")
                        (inline-page "a11y.en.md"))
        :sitemap (condp = lang "fr" (inline-page "sitemap.fr.md")
                        (inline-page "sitemap.en.md"))
        :feeds   (condp = lang "fr" (inline-page "feeds.fr.md")
                        (inline-page "feeds.en.md"))
        :about   (condp = lang "fr" (inline-page "about.fr.md")
                        (inline-page "about.en.md"))
        nil)]
     (subscribe lang)
     (footer lang)
     (display-parameters-modal lang)]))

(defn main-class []
  (reagent/create-class
   {:display-name   "main-class"
    :component-did-mount
    (fn []
      (GET "/data/forges.csv"
           :handler
           #(reset! platforms (conj (map first (next (js->clj (csv/parse %)))) "sr.ht")))
      (GET "/data/tags.json"
           :handler
           #(reset! tags (map (comp bean clj->js) %)))
      (GET "/data/owners.json"
           :handler
           #(reset! orgas (map (comp bean clj->js) %))))
    :reagent-render (fn [] (main-page))}))

;; Setup router and init

(defn on-navigate [match]
  (let [title-prefix  "code.gouv.fr ─ "
        title-default "Codes sources du secteur public ─ Source code from the French public sector"
        page          (keyword (:name (:data match)))]
    ;; Rely on the server to handle /not-found as a 404
    (when (not (seq match)) (set! (.-location js/window) "/not-found"))
    (set! (. js/document -title)
          (str title-prefix
               (condp = page
                 :awes    "Awesome"
                 :orgas   "Organisations ─ Organizations"
                 :repos   "Dépôts de code source ─ Source code repositories"
                 :home    title-default
                 :legal   "Mentions légales ─ Legal mentions"
                 :tags    "Versions"
                 :stats   "Chiffres ─ Stats"
                 :a11y    "Accessibilité ─ Accessibility"
                 :feeds   "Flux RSS ─ RSS Feeds"
                 :sitemap "Pages du site ─ Sitemap"
                 :about   "À propos ─ About"
                 nil)))
    (re-frame/dispatch [:path! (:path match)])
    (re-frame/dispatch [:view! page (:query-params match)])))

(defonce routes
  ["/"
   ["" :home]
   ["groups" :orgas]
   ["repos" :repos]
   ["awesome" :awes]
   ["tags" :tags]
   ["stats" :stats]
   ["legal" :legal]
   ["a11y" :a11y]
   ["about" :about]
   ["sitemap" :sitemap]
   ["feeds" :feeds]])

(defn ^:export init []
  (re-frame/clear-subscription-cache!)
  (re-frame/dispatch-sync [:initialize-db!])
  (let [browser-lang (subs (or js/navigator.language "en") 0 2)]
    (re-frame/dispatch
     [:lang!
      (if (contains? i/supported-languages browser-lang)
        browser-lang
        "en")])
    (rfe/start!
     (rf/router routes {:conflicts nil})
     on-navigate
     {:use-fragment true})
    (start-filter-loop)
    (reagent.dom/render
     [main-class]
     (.getElementById js/document "app"))))
