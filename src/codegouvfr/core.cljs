;; Copyright (c) 2019-2022 DINUM, Bastien Guerry <bastien.guerry@data.gouv.fr>
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
(defonce libs-per-page 100)
(defonce sill-per-page 100)
(defonce papillon-per-page 100)
(defonce orgas-per-page 20)
(defonce deps-per-page 100)
(defonce timeout 100)

;; FIXME: Setting this here is a hack
(def dp-filter (reagent/atom nil))

(defonce init-filter
  {:d        nil
   :g        nil
   :platform ""
   :ministry ""
   :dep-type ""
   :lib-type ""})

(defonce urls
  {;; :annuaire-prefix "https://lannuaire.service-public.fr/"
   :swh-baseurl  "https://archive.softwareheritage.org/browse/origin/"
   :cdl-baseurl  "https://comptoir-du-libre.org/fr/softwares/"
   :sill-baseurl "https://sill.etalab.gouv.fr/"
   :support-url "https://communs.numerique.gouv.fr/utiliser/marches-interministeriels-support-expertise-logiciels-libres/"})

(defonce filter-chan (async/chan 100))

(defonce display-filter-chan (async/chan 100))

;; Mappings used when exporting displayed data to csv files
(defonce mappings
  {:repos    {:u  :last_update
              :d  :description
              :a? :is_archived
              :f? :is_fork
              :e? :is_esr
              :l? :is_lib
              :l  :language
              :li :license
              :n  :name
              :f  :forks_count
              :s  :stars_count
              :o  :organization_name
              :p  :platform
              :re :reuses
              :r  :repository_url}
   :orgas    {:d  :description
              :a  :location
              :e  :email
              :n  :name
              :p  :platform
              :h  :website
              :v? :is_verified
              :l  :login
              :c  :creation_date
              :r  :repositories_count
              :o  :organization_url
              :au :avatar_url}
   :deps     {:n :name
              :t :type
              :d :description
              :l :link
              :u :updated
              :r :repositories}
   :sill     {:n :name
              :f :description
              :l :license
              :u :added}
   :papillon {:a :agencyName
              :p :publicSector
              :n :serviceName
              :d :description
              :l :serviceUrl
              :i :sillId
              :c :comptoirDuLibreId}
   :libs     {:n :name
              :t :type
              :d :description
              :l :link
              :u :updated}})

;; Utility functions

(defn new-tab [s lang]
  (str s " - " (i/i lang [:new-tab])))

(defn set-item!
  "Set `key` in browser's localStorage to `val`."
  [key val]
  (.setItem (.-localStorage js/window) key (.stringify js/JSON (clj->js val))))

(defn get-item
  "Return the value of `key` from browser's localStorage."
  [key]
  (js->clj (.parse js/JSON (.getItem (.-localStorage js/window) key))))

(defn remove-item!
  "Remove the browser's localStorage value for the given `key`."
  [key]
  (.removeItem (.-localStorage js/window) key))

;; (def to-hiccup-repl
;;   {:h2 :h2.fr-h2.fr-mt-3w
;;    :p  :p.fr-my-3w
;;    :li :li.fr-ml-2w})

;; (defn to-hiccup
;;   "Convert a markdown `s` string to hiccup structure."
;;   [s]
;;   (->> s
;;        (md/md->hiccup)
;;        (md/component)
;;        (walk/prewalk-replace to-hiccup-repl)))

(defn to-locale-date [s]
  (when (string? s)
    (.toLocaleDateString
     (js/Date. (.parse js/Date s)))))

(defn todays-date []
  (s/replace
   (.toLocaleDateString (js/Date.))
   "/" "-"))

(defn or-kwds [m ks]
  (first (remove nil? (map #(apply % [m]) ks))))

(defn s-includes? [s sub]
  (when (and (string? s) (string? sub))
    (s/includes? (s/lower-case s) (s/lower-case sub))))

(defn vec-to-csv-string [data]
  (->> data
       ;; FIXME: Take care of escaping quotes?
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

;; Filters

(defn ntaf
  "Not a true and b false."
  [a b] (if a b true))

(defn apply-repos-filters [m]
  (let [{:keys [d q g language platform license
                is-esr is-contrib is-lib is-fork is-licensed]}
        @(re-frame/subscribe [:filter?])]
    (filter
     #(and
       (if (and d @dp-filter) (some @dp-filter [(:r %)]) true)
       (ntaf is-esr (:e? %))
       (ntaf is-fork (:f? %))
       (ntaf is-contrib (:c? %))
       (ntaf is-lib (:l? %))
       (ntaf is-licensed (let [l (:li %)] (and l (not= l "Other"))))
       (ntaf license (s-includes? (:li %) license))
       (if language
         (some (into #{} (list (s/lower-case (or (:l %) ""))))
               (s/split (s/lower-case language) #" +"))
         true)
       (if (= platform "") true (s-includes? (:r %) platform))
       (ntaf g (s-includes? (:r %) g))
       (ntaf q (s-includes? (s/join " " [(:n %) (:r %) (:o %) (:t %) (:d %)]) q)))
     m)))

(defn apply-deps-filters [m]
  (let [{:keys [dep-type q]} @(re-frame/subscribe [:filter?])]
    (filter
     #(and
       (if (= dep-type "") true (= (:t %) dep-type))
       (ntaf q (s-includes? (s/join " " [(:n %) (:t %) (:d %)]) q)))
     m)))

(defn apply-orgas-filters [m]
  (let [{:keys [q ministry]} @(re-frame/subscribe [:filter?])]
    (filter
     #(and (ntaf q (s-includes? (s/join " " [(:n %) (:l %) (:d %) (:h %) (:o %)]) q))
           (if (= ministry "") true (= (:m %) ministry)))
     m)))

(defn apply-libs-filters [m]
  (let [{:keys [q lib-type]} @(re-frame/subscribe [:filter?])]
    (filter
     #(and
       (if (= lib-type "") true (= (:t %) lib-type))
       (ntaf q (s-includes? (s/join " " [(:n %) (:d %)]) q)))
     m)))

(defn apply-sill-filters [m]
  (let [{:keys [q]} @(re-frame/subscribe [:filter?])]
    (filter
     #(ntaf q (s-includes? (s/join " " [(:n %) (:f %)]) q))
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

;; Events

(re-frame/reg-event-db
 :initialize-db!
 (fn [_ _]
   {:repos-page       0
    :orgas-page       0
    :libs-page        0
    :deps-page        0
    :sill-page        0
    :papillon-page    0
    :sort-repos-by    :reused
    :sort-orgas-by    :repos
    :sort-deps-by     :repos
    :sort-libs-by     :name
    :sort-papillon-by :agency
    :reverse-sort     false
    :filter           init-filter
    :display-filter   init-filter
    :lang             "en"
    :path             ""}))

(def repos (reagent/atom nil))
(def libs (reagent/atom nil))
(def sill (reagent/atom nil))
(def papillon (reagent/atom nil))
(def deps (reagent/atom nil))
(def orgas (reagent/atom nil))
(def platforms (reagent/atom nil))
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
   (re-frame/dispatch [:deps-page! 0])
   (re-frame/dispatch [:libs-page! 0])
   (re-frame/dispatch [:sill-page! 0])
   (re-frame/dispatch [:papillon-page! 0])
   (update-in db [:filter] merge s)))

(re-frame/reg-event-db
 :display-filter!
 (fn [db [_ s]]
   (update-in db [:display-filter] merge s)))

(re-frame/reg-event-db
 :repos-page!
 (fn [db [_ n]] (assoc db :repos-page n)))

(re-frame/reg-event-db
 :libs-page!
 (fn [db [_ n]] (assoc db :libs-page n)))

(re-frame/reg-event-db
 :sill-page!
 (fn [db [_ n]] (assoc db :sill-page n)))

(re-frame/reg-event-db
 :papillon-page!
 (fn [db [_ n]] (assoc db :papillon-page n)))

(re-frame/reg-event-db
 :orgas-page!
 (fn [db [_ n]] (assoc db :orgas-page n)))

(re-frame/reg-event-db
 :deps-page!
 (fn [db [_ n]] (assoc db :deps-page n)))

(re-frame/reg-event-db
 :view!
 (fn [db [_ view query-params]]
   (re-frame/dispatch [:repos-page! 0])
   (re-frame/dispatch [:orgas-page! 0])
   (re-frame/dispatch [:deps-page! 0])
   (re-frame/dispatch [:libs-page! 0])
   (re-frame/dispatch [:papillon-page! 0])
   (re-frame/dispatch [:filter! (merge init-filter query-params)])
   (re-frame/dispatch [:display-filter! (merge init-filter query-params)])
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
 :sort-libs-by!
 (fn [db [_ k]]
   (re-frame/dispatch [:libs-page! 0])
   (when (= k (:sort-libs-by db))
     (re-frame/dispatch [:reverse-sort!]))
   (assoc db :sort-libs-by k)))

(re-frame/reg-event-db
 :sort-sill-by!
 (fn [db [_ k]]
   (re-frame/dispatch [:sill-page! 0])
   (when (= k (:sort-sill-by db))
     (re-frame/dispatch [:reverse-sort!]))
   (assoc db :sort-sill-by k)))

(re-frame/reg-event-db
 :sort-papillon-by!
 (fn [db [_ k]]
   (re-frame/dispatch [:papillon-page! 0])
   (when (= k (:sort-papillon-by db))
     (re-frame/dispatch [:reverse-sort!]))
   (assoc db :sort-papillon-by k)))

(re-frame/reg-event-db
 :sort-orgas-by!
 (fn [db [_ k]]
   (re-frame/dispatch [:orgas-page! 0])
   (when (= k (:sort-orgas-by db))
     (re-frame/dispatch [:reverse-sort!]))
   (assoc db :sort-orgas-by k)))

(re-frame/reg-event-db
 :sort-deps-by!
 (fn [db [_ k]]
   (re-frame/dispatch [:deps-page! 0])
   (when (= k (:sort-deps-by db))
     (re-frame/dispatch [:reverse-sort!]))
   (assoc db :sort-deps-by k)))

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
 :sort-libs-by?
 (fn [db _] (:sort-libs-by db)))

(re-frame/reg-sub
 :sort-sill-by?
 (fn [db _] (:sort-sill-by db)))

(re-frame/reg-sub
 :sort-papillon-by?
 (fn [db _] (:sort-papillon-by db)))

(re-frame/reg-sub
 :sort-orgas-by?
 (fn [db _] (:sort-orgas-by db)))

(re-frame/reg-sub
 :sort-deps-by?
 (fn [db _] (:sort-deps-by db)))

(re-frame/reg-sub
 :repos-page?
 (fn [db _] (:repos-page db)))

(re-frame/reg-sub
 :libs-page?
 (fn [db _] (:libs-page db)))

(re-frame/reg-sub
 :sill-page?
 (fn [db _] (:sill-page db)))

(re-frame/reg-sub
 :papillon-page?
 (fn [db _] (:papillon-page db)))

(re-frame/reg-sub
 :deps-page?
 (fn [db _] (:deps-page db)))

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

(re-frame/reg-sub
 :deps-types?
 (fn [_ _] (distinct (map :t @deps))))

(re-frame/reg-sub
 :libs-types?
 (fn [_ _] (distinct (map :t @libs))))

(re-frame/reg-sub
 :repos?
 (fn []
   (let [repos0 @repos
         repos  (case @(re-frame/subscribe [:sort-repos-by?])
                  :name   (reverse (sort-by :n repos0))
                  :forks  (sort-by :f repos0)
                  :stars  (sort-by :s repos0)
                  ;; :issues (sort-by :i repos0)
                  :reused (sort-by :re repos0)
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
 :libs?
 (fn []
   (let [libs0 @libs
         libs  (case @(re-frame/subscribe [:sort-libs-by?])
                 :name (reverse (sort-by :name libs0))
                 libs0)]
     (apply-libs-filters (if @(re-frame/subscribe [:reverse-sort?])
                           libs
                           (reverse libs))))))

(re-frame/reg-sub
 :sill?
 (fn []
   (let [sill0 @sill
         sill  (case @(re-frame/subscribe [:sort-sill-by?])
                 :name (reverse (sort-by :n sill0))
                 :date (sort #(compare (js/Date. (.parse js/Date (:u %1)))
                                       (js/Date. (.parse js/Date (:u %2))))
                             sill0)
                 sill0)]
     (apply-sill-filters (if @(re-frame/subscribe [:reverse-sort?])
                           sill
                           (reverse sill))))))

(re-frame/reg-sub
 :papillon?
 (fn []
   (let [papillon0 @papillon
         papillon  (case @(re-frame/subscribe [:sort-papillon-by?])
                     :name   (reverse (sort-by :n papillon0))
                     :agency (reverse (sort-by :a papillon0))
                     papillon0)]
     (apply-papillon-filters (if @(re-frame/subscribe [:reverse-sort?])
                               papillon
                               (reverse papillon))))))

(re-frame/reg-sub
 :deps?
 (fn []
   (let [deps0 @deps
         deps  (case @(re-frame/subscribe [:sort-deps-by?])
                 :name  (reverse (sort-by :n deps0))
                 :repos (sort-by #(count (:r %)) deps0)
                 deps0)]
     (apply-deps-filters
      (if @(re-frame/subscribe [:reverse-sort?])
        deps (reverse deps))))))

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
                 :name  (reverse
                         (sort #(compare (or-kwds %1 [:n :l])
                                         (or-kwds %2 [:n :l]))
                               orgs))
                 orgs)]
     (apply-orgas-filters
      (if @(re-frame/subscribe [:reverse-sort?])
        orgas
        (reverse orgas))))))

;; Pagination

(defn change-page [type next]
  (let [conf
        (condp = type
          :repos    {:sub :repos-page? :evt      :repos-page!
                     :cnt :repos?      :per-page repos-per-page}
          :libs     {:sub :libs-page? :evt      :libs-page!
                     :cnt :libs?      :per-page libs-per-page}
          :sill     {:sub :sill-page? :evt      :sill-page!
                     :cnt :sill?      :per-page sill-per-page}
          :papillon {:sub :papillon-page? :evt      :papillon-page!
                     :cnt :papillon?      :per-page papillon-per-page}
          :deps     {:sub :deps-page? :evt      :deps-page!
                     :cnt :deps?      :per-page deps-per-page}
          :orgas    {:sub :orgas-page? :evt      :orgas-page!
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

;; Main structure - repos

(defn repos-table [lang repos-cnt]
  (if (zero? repos-cnt)
    [:div.fr-m-3w [:p (i/i lang [:no-repo-found])]]
    (let [rep-f      @(re-frame/subscribe [:sort-repos-by?])
          repos-page @(re-frame/subscribe [:repos-page?])
          repos      @(re-frame/subscribe [:repos?])]
      [:div.fr-table.fr-table--no-caption.fr-table--layout-fixed
       [:table
        [:caption (i/i lang [:repos-of-source-code])]
        [:thead.fr-grid.fr-col-12
         [:tr
          [:th.fr-col
           [:button
            {:class    (when (= rep-f :name) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-repos-alpha])
             :on-click #(re-frame/dispatch [:sort-repos-by! :name])}
            (i/i lang [:orga-repo])]]
          [:th.fr-col (i/i lang [:description])]
          [:th.fr-col-1
           [:button
            {:class    (when (= rep-f :date) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-update-date])
             :on-click #(re-frame/dispatch [:sort-repos-by! :date])}
            (i/i lang [:update-short])]]
          [:th.fr-col-1
           [:button
            {:class    (when (= rep-f :forks) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-forks])
             :on-click #(re-frame/dispatch [:sort-repos-by! :forks])}
            (i/i lang [:forks])]]
          [:th.fr-col-1
           [:button
            {:class    (when (= rep-f :stars) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-stars])
             :on-click #(re-frame/dispatch [:sort-repos-by! :stars])}
            (i/i lang [:Stars])]]
          [:th.fr-col-1
           [:button
            {:class    (when (= rep-f :reused) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-reused])
             :on-click #(re-frame/dispatch [:sort-repos-by! :reused])}
            (i/i lang [:reused])]]]]
        (into [:tbody]
              (for [dd (take repos-per-page
                             (drop (* repos-per-page repos-page) repos))]
                ^{:key dd}
                (let [{:keys [a? ; is_archived
                              d  ; description
                              f  ; forks_count
                              li ; license
                              n  ; name
                              o  ; organization_name
                              r  ; repository_url
                              s  ; stars_count
                              u  ; last_update
                              re ; reuses
                              ]}
                      dd
                      group (subs r 0 (- (count r) (inc (count n))))]
                  [:tr
                   ;; Repo (orga)
                   [:td
                    [:div
                     [:a.fr-link
                      {:href  (str (:swh-baseurl urls) r)
                       :title (new-tab (i/i lang [:swh-link]) lang)
                       :rel   "noreferrer noopener"}
                      [:img {:width "18px" :src "/img/swh-logo.png"
                             :alt   "Software Heritage logo"}]]
                     [:span " "]
                     [:a.fr-link
                      {:href   r
                       :target "new"
                       :rel    "noreferrer noopener"
                       :title  (new-tab
                                (str
                                 (i/i lang [:go-to-repo])
                                 (when li (str (i/i lang [:under-license]) li))) lang)}
                      n]
                     " ("
                     [:a.fr-link
                      {:href  (rfe/href :repos {:lang lang} {:g group})
                       :title (i/i lang [:browse-repos-orga])}
                      o]
                     ")"]]
                   ;; Description
                   [:td {:title (when a? (i/i lang [:repo-archived]))}
                    [:span (if a? [:em d] d)]]
                   ;; Update
                   [:td {:style {:text-align "center"}}
                    (or (to-locale-date u) "N/A")]
                   ;; Forks
                   [:td {:style {:text-align "center"}} f]
                   ;; Stars
                   [:td {:style {:text-align "center"}} s]
                   ;; Reused
                   [:td
                    {:style {:text-align "center"}}
                    [:a.fr-link
                     {:title  (new-tab (i/i lang [:reuses-expand]) lang)
                      :rel    "noreferrer noopener"
                      :target "new"
                      :href   (str r "/network/dependents")}
                     re]]])))]])))

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
      [:button.fr-link
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
                    (str "codegouvfr-repositories-" (todays-date) ".csv"))}
       [:span.fr-icon-download-line {:aria-hidden true}]]
      ;; General information
      [:strong.fr-m-auto
       (let [rps (count repos)]
         (if (< rps 2)
           (str rps (i/i lang [:repo]))
           (str rps (i/i lang [:repos]))))]
      ;; Top pagination block
      [navigate-pagination :repos first-disabled last-disabled repos-pages count-pages]]
     ;; Specific repos search filters and options
     [:div.fr-grid-row
      [:input.fr-input.fr-col.fr-m-1w
       {:placeholder (i/i lang [:license])
        :value       (or @license
                         (:license @(re-frame/subscribe [:display-filter?])))
        :on-change   (fn [e]
                       (let [ev (.-value (.-target e))]
                         (reset! license ev)
                         (async/go
                           (async/>! display-filter-chan {:license ev})
                           (async/<! (async/timeout timeout))
                           (async/>! filter-chan {:license ev}))))}]
      [:input.fr-input.fr-col.fr-m-1w
       {:value       (or @language
                         (:language @(re-frame/subscribe [:display-filter?])))
        :placeholder (i/i lang [:language])
        :on-change   (fn [e]
                       (let [ev (.-value (.-target e))]
                         (reset! language ev)
                         (async/go
                           (async/>! display-filter-chan {:language ev})
                           (async/<! (async/timeout timeout))
                           (async/>! filter-chan {:language ev}))))}]
      [:select.fr-select.fr-col-3
       {:value (or platform "")
        :on-change
        (fn [e]
          (let [ev (.-value (.-target e))]
            (re-frame/dispatch [:filter! {:platform ev}])
            (async/go
              (async/>! display-filter-chan {:platform ev})
              (async/<! (async/timeout timeout))
              (async/>! filter-chan {:platform ev}))))}
       [:option#default {:value ""} (i/i lang [:all-forges])]
       (for [x @platforms]
         ^{:key x}
         [:option {:value x} x])]
      [:div.fr-checkbox-group.fr-col.fr-m-1w
       [:input#1 {:type      "checkbox" :name "1"
                  :on-change #(let [v (.-checked (.-target %))]
                                (set-item! :is-fork v)
                                (re-frame/dispatch [:filter! {:is-fork v}]))}]
       [:label.fr-label
        {:for   "1"
         :title (i/i lang [:only-fork-title])}
        (i/i lang [:only-fork])]]
      [:div.fr-checkbox-group.fr-col.fr-m-1w
       [:input#2 {:type      "checkbox" :name "2"
                  :on-change #(let [v (.-checked (.-target %))]
                                (set-item! :is-licensed v)
                                (re-frame/dispatch [:filter! {:is-licensed v}]))}]
       [:label.fr-label {:for "2" :title (i/i lang [:only-with-license-title])}
        (i/i lang [:only-with-license])]]
      [:div.fr-checkbox-group.fr-col.fr-m-1w
       [:input#3 {:type      "checkbox" :name "3"
                  :on-change #(let [v (.-checked (.-target %))]
                                (set-item! :is-esr v)
                                (re-frame/dispatch [:filter! {:is-esr v}]))}]
       [:label.fr-label
        {:for "3" :title (i/i lang [:only-her-title])}
        (i/i lang [:only-her])]]
      [:div.fr-checkbox-group.fr-col.fr-m-1w
       [:input#4 {:type      "checkbox" :name "4"
                  :on-change #(let [v (.-checked (.-target %))]
                                (set-item! :is-lib v)
                                (re-frame/dispatch [:filter! {:is-lib v}]))}]
       [:label.fr-label
        {:for "4" :title (i/i lang [:only-lib-title])}
        (i/i lang [:only-lib])]]
      [:div.fr-checkbox-group.fr-col.fr-m-1w
       [:input#5 {:type      "checkbox" :name "5"
                  :on-change #(let [v (.-checked (.-target %))]
                                (set-item! :is-contrib v)
                                (re-frame/dispatch [:filter! {:is-contrib v}]))}]
       [:label.fr-label
        {:for "5" :title (i/i lang [:only-contrib-title])}
        (i/i lang [:only-contrib])]]]
     ;; Main repos table display
     [repos-table lang (count repos)]
     ;; Bottom pagination block
     [navigate-pagination :repos first-disabled last-disabled repos-pages count-pages]]))

(defn repos-page-class [lang license language]
  (reagent/create-class
   {:display-name   "repos-page-class"
    :component-did-mount
    (fn []
      (GET "/data/repos.json"
           :handler
           #(reset! repos (map (comp bean clj->js) %))))
    :reagent-render (fn [] (repos-page lang license language))}))

;; Main structure - libs

(defn libs-table [lang libs-cnt]
  (if (zero? libs-cnt)
    [:div.fr-m-3w [:p (i/i lang [:no-lib-found])]]
    (let [lib-f     @(re-frame/subscribe [:sort-libs-by?])
          libs-page @(re-frame/subscribe [:libs-page?])
          libs      @(re-frame/subscribe [:libs?])]
      [:div.fr-table.fr-table--no-caption.fr-table--layout-fixed
       [:table
        [:caption (i/i lang [:Libraries])]
        [:thead.fr-grid.fr-col-12
         [:tr
          [:th.fr-col-5
           [:button
            {:class    (when (= lib-f :name) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-libs-alpha])
             :on-click #(re-frame/dispatch [:sort-libs-by! :name])}
            (i/i lang [:library])]]
          [:th.fr-col-1 (i/i lang [:lib-type])]
          [:th.fr-col (i/i lang [:description])]]]
        (into [:tbody]
              (for [dd (take libs-per-page
                             (drop (* libs-per-page libs-page) libs))]
                ^{:key dd}
                (let [{:keys [n ; name
                              l ; link
                              d ; description
                              t ; type
                              ]} dd]
                  [:tr
                   ;; Lib
                   [:td
                    [:div
                     [:a.fr-link
                      {:href   l
                       :rel    "noreferrer noopener"
                       :target "_blank"}
                      n]]]
                   ;; Type
                   [:td t]
                   ;; Description
                   [:td d]])))]])))

(defn libs-page [lang]
  (let [libs           @(re-frame/subscribe [:libs?])
        libs-pages     @(re-frame/subscribe [:libs-page?])
        libtypes       @(re-frame/subscribe [:libs-types?])
        count-pages    (count (partition-all libs-per-page libs))
        first-disabled (zero? libs-pages)
        last-disabled  (= libs-pages (dec count-pages))
        mapping        (:libs mappings)]
    [:div.fr-grid
     [:div.fr-grid-row
      ;; RSS feed
      [:button.fr-link
       {:title (i/i lang [:rss-feed])
        :href  "/data/latest-libraries.xml"}
       [:span.fr-icon-rss-line {:aria-hidden true}]]
      ;; Download link
      [:button.fr-link.fr-m-1w
       {:title    (i/i lang [:download])
        :on-click #(download-as-csv!
                    (map
                     (fn [r] (set/rename-keys (select-keys r (keys mapping)) mapping))
                     libs)
                    (str "codegouvfr-libraries-" (todays-date) ".csv"))}
       [:span.fr-icon-download-line {:aria-hidden true}]]
      ;; General information
      [:strong.fr-m-auto
       (let [rps (count libs)]
         (if (< rps 2)
           (str rps (i/i lang [:lib]))
           (str rps (i/i lang [:libs]))))]
      ;; Top pagination block
      [navigate-pagination :libs first-disabled last-disabled libs-pages count-pages]]
     [:div.fr-grid-row
      [:select.fr-select.fr-col.fr-m-1w
       {:value (:lib-type @(re-frame/subscribe [:filter?]))
        :on-change
        (fn [e]
          (let [ev (.-value (.-target e))]
            (re-frame/dispatch [:filter! {:lib-type ev}])
            (async/go
              (async/>! display-filter-chan {:lib-type ev})
              (async/<! (async/timeout timeout))
              (async/>! filter-chan {:lib-type ev}))))}
       [:option#default {:value ""} (i/i lang [:all-lib-types])]
       (for [x libtypes]
         ^{:key x}
         [:option {:value x} x])]]
     ;; Specific libs search filters and options
     ;; Main libs table display
     [libs-table lang (count libs)]
     ;; Bottom pagination block
     [navigate-pagination :libs first-disabled last-disabled libs-pages count-pages]]))

(defn libs-page-class [lang]
  (reagent/create-class
   {:display-name   "libs-page-class"
    :component-did-mount
    (fn []
      (GET "/data/libs.json"
           :handler
           #(reset! libs (map (comp bean clj->js) %))))
    :reagent-render (fn [] (libs-page lang))}))

;; Main structure - sill

(defn sill-table [lang sill-cnt]
  (if (zero? sill-cnt)
    [:div.fr-m-3w [:p (i/i lang [:no-lib-found])]]
    (let [sill-f    @(re-frame/subscribe [:sort-sill-by?])
          sill-page @(re-frame/subscribe [:sill-page?])
          sill      ((if sill-f identity shuffle)
                     @(re-frame/subscribe [:sill?]))]
      [:div.fr-table.fr-table--no-caption.fr-table--layout-fixed
       [:table
        [:caption (i/i lang [:Libraries])]
        [:thead.fr-grid.fr-col-12
         [:tr
          [:th.fr-col-1 "Logo"]
          [:th.fr-col-3
           [:button
            {:class    (when (= sill-f :name) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-sill-alpha])
             :on-click #(re-frame/dispatch [:sort-sill-by! :name])}
            (i/i lang [:library])]]
          [:th.fr-col (i/i lang [:description])]
          [:th.fr-col-1 (i/i lang [:workshop])]
          [:th.fr-col-2 (i/i lang [:license])]
          [:th.fr-col-1
           [:button
            {:class    (when (= sill-f :date) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-sill-date])
             :on-click #(re-frame/dispatch [:sort-sill-by! :date])}
            (i/i lang [:added])]]]]
        (into [:tbody]
              (for [dd (take sill-per-page
                             (drop (* sill-per-page sill-page) sill))]
                ^{:key dd}
                (let [{:keys [n   ; name
                              id  ; sill_id
                              i   ; wikidataDataLogoUrl
                              l   ; license
                              f   ; description
                              fr  ; isFromFrenchPublicService
                              u   ; referencedSinceTime
                              cl  ; comptoirDuLibreSoftwareId
                              clp ; comptoirDuLibreSoftwareProviders?
                              c   ; useCaseUrls
                              w   ; workShopUrls
                              s   ; :isPresentInSupportContract
                              ]} dd]
                  [:tr
                   ;; Logo
                   [:td [:img {:src i :width "100%" :alt ""}]]
                   ;; Name
                   [:td
                    [:a.fr-link
                     {:href   (str (:sill-baseurl urls) lang "/software?id=" id)
                      :rel    "noreferrer noopener"
                      :title  (new-tab (i/i lang [:more-info]) lang)
                      :target "_blank"}
                     (if fr (str "🇫🇷 " n) n)]]
                   ;; Description
                   [:td
                    [:span
                     f ; Function or description
                     (when clp
                       [:span " · "
                        [:a
                         {:href   (str (:cdl-baseurl urls) "servicesProviders/" cl)
                          :title  (i/i lang [:providers-adullact])
                          :rel    "noreferrer noopener"
                          :target "new"}
                         (i/i lang [:providers])]])
                     (when-let [c-url (first c)]
                       [:span " · "
                        [:a
                         {:href   c-url
                          :rel    "noreferrer noopener"
                          :target "new"}
                         (i/i lang [:details])]])]]
                   ;; Workshop
                   [:td (when-let [w-url (not-empty (first w))]
                          [:a.fr-link
                           {:href   w-url
                            :rel    "noreferrer noopener"
                            :target "_blank"}
                           (i/i lang [:workshop])])]
                   ;; License
                   [:td (if s
                          [:a
                           {:href   (str (:support-url urls) l)
                            :rel    "noreferrer noopener"
                            :title  (new-tab (i/i lang [:support]) lang)
                            :target "_blank"}
                           l] l)]
                   ;; Date when added
                   [:td (to-locale-date u)]])))]])))

(defn sill-page [lang]
  (let [sill           @(re-frame/subscribe [:sill?])
        sill-pages     @(re-frame/subscribe [:sill-page?])
        count-pages    (count (partition-all sill-per-page sill))
        first-disabled (zero? sill-pages)
        last-disabled  (= sill-pages (dec count-pages))
        mapping        (:sill mappings)]
    [:div.fr-grid
     [:div.fr-grid-row
      ;; RSS feed
      [:button.fr-link
       {:title (i/i lang [:rss-feed])
        :href  "/data/latest-sill.xml"}
       [:span.fr-icon-rss-line {:aria-hidden true}]]
      ;; Download link
      [:button.fr-link.fr-m-1w
       {:title    (i/i lang [:download])
        :on-click #(download-as-csv!
                    (map
                     (fn [r] (set/rename-keys (select-keys r (keys mapping)) mapping))
                     sill)
                    (str "codegouvfr-sill-" (todays-date) ".csv"))}
       [:span.fr-icon-download-line {:aria-hidden true}]]
      ;; General information
      [:strong.fr-m-auto
       (let [rps (count sill)]
         (if (< rps 2)
           (str rps (i/i lang [:sill0]))
           (str rps (i/i lang [:sill]))))]
      ;; Top pagination block
      [navigate-pagination :sill first-disabled last-disabled sill-pages count-pages]]
     ;; Specific sill search filters and options
     ;; Main sill table display
     [sill-table lang (count sill)]
     ;; Bottom pagination block
     [navigate-pagination :sill first-disabled last-disabled sill-pages count-pages]]))

(defn sill-page-class [lang]
  (reagent/create-class
   {:display-name   "sill-page-class"
    :component-did-mount
    (fn []
      (GET "/data/sill.json"
           :handler
           #(reset! sill (map (comp bean clj->js) %))))
    :reagent-render (fn [] (sill-page lang))}))

;; Main structure - papillon

(defn papillon-table [lang papillon-cnt]
  (if (zero? papillon-cnt)
    [:div.fr-m-3w [:p (i/i lang [:no-lib-found])]]
    (let [papillon-f    @(re-frame/subscribe [:sort-papillon-by?])
          papillon-page @(re-frame/subscribe [:papillon-page?])
          papillon      @(re-frame/subscribe [:papillon?])]
      [:div.fr-table.fr-table--no-caption.fr-table--layout-fixed
       [:table
        [:caption (i/i lang [:Papillon])]
        [:thead.fr-grid.fr-col-12
         [:tr
          [:th.fr-col-3
           [:button
            {:class    (when (= papillon-f :name) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-papillon-alpha])
             :on-click #(re-frame/dispatch [:sort-papillon-by! :name])}
            (i/i lang [:service])]]
          [:th.fr-col (i/i lang [:description])]
          [:th.fr-col
           [:button
            {:class    (when (= papillon-f :agency) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-papillon-agency])
             :on-click #(re-frame/dispatch [:sort-papillon-by! :agency])}
            (i/i lang [:papillon-agency])]]]]
        (into [:tbody]
              (for [dd (take papillon-per-page
                             (drop (* papillon-per-page papillon-page) papillon))]
                ^{:key dd}
                (let [{:keys [n   ; serviceName
                              d   ; description
                              ;; p   ; public sector scope
                              a   ; agencyName
                              l   ; serviceUrl
                              i   ; softwareSillId
                              c   ; comptoirDuLibreId
                              ]} dd]
                  [:tr
                   ;; service name
                   [:td [:span
                         [:a.fr-link {:href l} n]
                         (let [sill-link
                               [:span " · "
                                [:a.fr-link
                                 {:href (str (:sill-baseurl urls) lang "/software?id=" i)} "SILL"]]
                               cdl-link
                               [:span " · "
                                [:a.fr-link
                                 {:href (str (:cdl-baseurl urls) c)} "Comptoir du Libre"]]]
                           [:span
                            (when i sill-link)
                            (when c cdl-link)])]]
                   ;; Service description
                   [:td d]
                   ;; Agency name
                   [:td a]])))]])))

(defn papillon-page [lang]
  (let [papillon       @(re-frame/subscribe [:papillon?])
        papillon-pages @(re-frame/subscribe [:papillon-page?])
        count-pages    (count (partition-all papillon-per-page papillon))
        first-disabled (zero? papillon-pages)
        last-disabled  (= papillon-pages (dec count-pages))
        mapping        (:papillon mappings)]
    [:div.fr-grid
     [:div.fr-grid-row
      ;; Download link
      [:button.fr-link.fr-m-1w
       {:title    (i/i lang [:download])
        :on-click #(download-as-csv!
                    (map
                     (fn [r] (set/rename-keys (select-keys r (keys mapping)) mapping))
                     papillon)
                    (str "codegouvfr-papillon-" (todays-date) ".csv"))}
       [:span.fr-icon-download-line {:aria-hidden true}]]
      ;; General information
      [:strong.fr-m-auto
       (let [rps (count papillon)]
         (if (< rps 2)
           (str rps (i/i lang [:papillon0]))
           (str rps (i/i lang [:papillon]))))]
      ;; Top pagination block
      [navigate-pagination :papillon first-disabled last-disabled papillon-pages count-pages]]
     ;; Specific papillon search filters and options
     ;; Main papillon table display
     [papillon-table lang (count papillon)]
     ;; Bottom pagination block
     [navigate-pagination :papillon first-disabled last-disabled papillon-pages count-pages]]))

(defn papillon-page-class [lang]
  (reagent/create-class
   {:display-name   "papillon-page-class"
    :component-did-mount
    (fn []
      (GET "/data/papillon.json"
           :handler
           #(reset! papillon (map (comp bean clj->js) %))))
    :reagent-render (fn [] (papillon-page lang))}))

;; Main structure - orgas

(defn orgas-table [lang orgas-cnt]
  (if (zero? orgas-cnt)
    [:div.fr-m-3w [:p (i/i lang [:no-orga-found])]]
    (let [org-f @(re-frame/subscribe [:sort-orgas-by?])
          orgas @(re-frame/subscribe [:orgas?])]
      [:div.fr-table.fr-table--no-caption.fr-table--layout-fixed
       [:table
        [:caption (i/i lang [:orgas-or-groups])]
        [:thead.fr-grid.fr-col-12
         [:tr
          [:th.fr-col-1 "Image"]
          [:th.fr-col-2
           [:button
            {:class    (when (= org-f :name) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-orgas-alpha])
             :on-click #(re-frame/dispatch [:sort-orgas-by! :name])}
            (i/i lang [:orgas])]]
          [:th.fr-col-6 (i/i lang [:description])]
          [:th.fr-col-1
           [:button
            {:class    (when (= org-f :repos) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-repos])
             :on-click #(re-frame/dispatch [:sort-orgas-by! :repos])}
            (i/i lang [:Repos])]]
          [:th.fr-col-1
           [:button
            {:class    (when (= org-f :date) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-orgas-creation])
             :on-click #(re-frame/dispatch [:sort-orgas-by! :date])}
            (i/i lang [:created-at])]]]]
        (into [:tbody]
              (for [dd (take orgas-per-page
                             (drop (* orgas-per-page @(re-frame/subscribe [:orgas-page?]))
                                   orgas))]
                ^{:key dd}
                (let [{:keys [n ; name
                              l ; login
                              d ; description
                              o ; organization_url
                              ;; FIXME: Where to use this?
                              ;; h ; website
                              ;; an ; annuaire
                              f ; floss_policy
                              p ; platform
                              au ; avatar_url
                              c ; creation_date
                              r ; repositories_count
                              ]} dd]
                  [:tr
                   [:td (when au [:img {:src au :width "100%" :alt ""}])]
                   [:td
                    [:span
                     (when (not-empty f)
                       [:span
                        [:a {:target "new"
                             :rel    "noreferrer noopener"
                             :title  (new-tab (i/i lang [:floss-policy]) lang)
                             :href   f}
                         [:img {:src "/img/floss.png" :width "10%"}]]
                        " "])
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
                                                "GitLab"    (s/replace o "/groups/" "/"))})}
                     r]]
                   [:td {:style {:text-align "center"}}
                    (to-locale-date c)]])))]])))

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
      [:button.fr-link
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
                    (str "codegouvfr-organizations-" (todays-date) ".csv"))}
       [:span.fr-icon-download-line {:aria-hidden true}]]
      ;; General information
      [:strong.fr-m-auto
       (let [orgs (count orgas)]
         (if (< orgs 2)
           (str orgs (i/i lang [:one-group]))
           (str orgs (i/i lang [:groups]))))]
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
              (async/>! display-filter-chan {:ministry ev})
              (async/<! (async/timeout timeout))
              (async/>! filter-chan {:ministry ev}))))}
       [:option#default {:value ""} (i/i lang [:all-ministries])]
       (for [x @(re-frame/subscribe [:ministries?])]
         ^{:key x}
         [:option {:value x} x])]]
     [orgas-table lang orgas-cnt]
     [navigate-pagination :orgas first-disabled last-disabled orgas-pages count-pages]]))

;; Main structure - deps

(defn deps-table [lang deps repo]
  (let [dep-f     @(re-frame/subscribe [:sort-deps-by?])
        deps-page @(re-frame/subscribe [:deps-page?])]
    [:div.fr-table.fr-table--no-caption.fr-table--layout-fixed
     [:table
      [:caption (i/i lang [:deps])]
      [:thead.fr-grid.fr-col-12
       [:tr
        [:th.fr-col-3
         [:button
          {:class    (when (= dep-f :name) "fr-icon-checkbox-circle-line fr-link--icon-left")
           :title    (i/i lang [:sort-name])
           :on-click #(re-frame/dispatch [:sort-deps-by! :name])}
          (i/i lang [:name])]]
        [:th.fr-col-1 (i/i lang [:type])]
        [:th.fr-col-5 (i/i lang [:description])]
        (when-not repo
          [:th.fr-col-1
           [:button
            {:class    (when (= dep-f :repos) "fr-icon-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-repos])
             :on-click #(re-frame/dispatch [:sort-deps-by! :repos])}
            (i/i lang [:Repos])]])]]
      (into
       [:tbody]
       (for [dd (take deps-per-page
                      (drop (* deps-per-page deps-page) deps))]
         ^{:key dd}
         (let [{:keys [t ; type
                       n ; name
                       d ; description
                       l ; link
                       r ; repositories
                       ]} dd]
           [:tr
            [:td
             [:a.fr-link
              {:href   l
               :target "_blank"
               :rel    "noreferrer noopener"
               :title  (new-tab (i/i lang [:more-info]) lang)} n]]
            [:td t]
            [:td d]
            (when-not repo
              [:td {:style {:text-align "center"}}
               [:button.fr-link
                {:title    (i/i lang [:list-repos-depending-on-dep])
                 :on-click #(do (reset! dp-filter (into #{} (js->clj r)))
                                (rfe/push-state :repos {:lang l} {:d n}))}
                (count r)]])])))]]))

(defn deps-page [lang]
  (let [{:keys [repo]} @(re-frame/subscribe [:filter?])
        deps0          @(re-frame/subscribe [:deps?])
        deps           (if-let [s repo]
                         (filter #(s-includes? (s/join " " (:r %)) s) deps0)
                         deps0)
        deptypes       @(re-frame/subscribe [:deps-types?])
        deps-pages     @(re-frame/subscribe [:deps-page?])
        count-pages    (count (partition-all deps-per-page deps))
        first-disabled (zero? deps-pages)
        last-disabled  (= deps-pages (dec count-pages))
        mapping        (:deps mappings)]
    [:div.fr-grid
     [:div.fr-grid-row
      ;; RSS feed
      [:button.fr-link
       {:title (i/i lang [:rss-feed])
        :href  "/data/latest-dependencies.xml"}
       [:span.fr-icon-rss-line {:aria-hidden true}]]
      ;; Download link
      [:button.fr-link.fr-m-1w
       {:title    (i/i lang [:download])
        :on-click #(download-as-csv!
                    (map
                     (fn [r] (set/rename-keys (select-keys r (keys mapping)) mapping))
                     deps)
                    (str "codegouvfr-dependencies-" (todays-date) ".csv"))}
       [:span.fr-icon-download-line {:aria-hidden true}]]
      ;; General informations
      [:strong.fr-m-auto
       (let [deps (count deps)]
         (if (< deps 2)
           (str deps (i/i lang [:dep]))
           (str deps (i/i lang [:deps]))))]
      ;; Top pagination block
      [navigate-pagination :deps first-disabled last-disabled deps-pages count-pages]]
     [:div.fr-grid-row
      [:select.fr-select.fr-col.fr-m-1w
       {:value (:dep-type @(re-frame/subscribe [:filter?]))
        :on-change
        (fn [e]
          (let [ev (.-value (.-target e))]
            (re-frame/dispatch [:filter! {:dep-type ev}])
            (async/go
              (async/>! display-filter-chan {:dep-type ev})
              (async/<! (async/timeout timeout))
              (async/>! filter-chan {:dep-type ev}))))}
       [:option#default {:value ""} (i/i lang [:all-dep-types])]
       (for [x deptypes]
         ^{:key x}
         [:option {:value x} x])]]
     ;; Main deps display
     (if (pos? (count deps))
       [deps-table lang deps repo]
       [:div.fr-m-3w [:p (i/i lang [:no-dep-found])]])
     ;; Bottom pagination block
     [navigate-pagination :deps first-disabled last-disabled deps-pages count-pages]]))

;; Main structure - stats

(defn stats-table [heading data thead]
  [:div.fr-m-3w
   [:h4.fr-h4 heading]
   [:div.fr-table.fr-table--layout-fixed
    [:table
     thead
     [:tbody
      (for [[k v] (walk/stringify-keys data)]
        ^{:key k}
        [:tr [:td k] [:td v]])]]]])

(defn stats-tile [l i s]
  [:div.fr-tile.fr-col-2.fr-m-1w
   [:div.fr-tile__body
    [:p.fr-tile__title (i/i l [i])]
    [:div.fr-tile__desc
     [:p.fr-h4 s]]]])

(defn stats-page
  [lang stats]
  (let [{:keys [repos_cnt orgas_cnt deps_cnt libs_cnt sill_cnt
                papillon_cnt avg_repos_cnt median_repos_cnt
                top_orgs_by_repos top_orgs_by_stars
                top_licenses top_languages top_topics
                top_forges top_ministries]} stats]
    [:div
     [:div.fr-grid-row.fr-grid-row--center {:style {:height "180px"}}
      (stats-tile lang :orgas-or-groups orgas_cnt)
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
                     (i/i lang [:orgas])
                     (i/i lang [:with-more-of])
                     (i/i lang [:repos])]
                    (top-clean-up-orgas top_orgs_by_repos "q")
                    [:thead [:tr [:th.fr-col-10 (i/i lang [:orgas])]
                             [:th (i/i lang [:Repos])]]])]
      [:div.fr-col-6
       (stats-table [:span
                     (i/i lang [:orgas])
                     (i/i lang [:with-more-of*])
                     (i/i lang [:stars])]
                    (top-clean-up-orgas top_orgs_by_stars "q")
                    [:thead [:tr [:th.fr-col-10 (i/i lang [:orgas])]
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
                             [:th (i/i lang [:Repos])]]])]]
     [:div.fr-grid-row.fr-grid-row--center {:style {:height "180px"}}
      (stats-tile lang :sill-stats sill_cnt)
      (stats-tile lang :papillon-stats papillon_cnt)
      (stats-tile lang :deps-stats deps_cnt)
      (stats-tile lang :libs-stats libs_cnt)]]))

(defn stats-page-class [lang]
  (let [stats (reagent/atom nil)]
    (reagent/create-class
     {:display-name   "stats-page-class"
      :component-did-mount
      (fn []
        (GET "/data/stats.json"
             :handler #(reset! stats (walk/keywordize-keys %))))
      :reagent-render (fn [] (stats-page lang @stats))})))

;; Main structure - menu, banner

(defn main-menu [q lang view]
  [:div
   [:div.fr-grid-row.fr-mt-2w
    [:div.fr-col-12
     (when (some #{:repos :orgas :deps :libs :sill :papillon} [view])
       [:input.fr-input
        {:placeholder (i/i lang [:free-search])
         :aria-label  (i/i lang [:free-search])
         :value       (or @q (:q @(re-frame/subscribe [:display-filter?])))
         :on-change   (fn [e]
                        (let [ev (.-value (.-target e))]
                          (reset! q ev)
                          (async/go
                            (async/>! display-filter-chan {:q ev})
                            (async/<! (async/timeout timeout))
                            (async/>! filter-chan {:q ev}))))}])]
    (when-let [flt @(re-frame/subscribe [:filter?])]
      [:div.fr-col-8.fr-grid-row.fr-m-1w
       (when-let [ff (not-empty (:g flt))]
         (close-filter-button lang ff :repos (merge flt {:g nil})))
       (when-let [ff (not-empty (:d flt))]
         (close-filter-button lang ff :deps (merge flt {:d nil})))
       (when-let [ff (not-empty (:repo flt))]
         (close-filter-button lang ff :deps (merge flt {:repo nil})))])]])

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
          [:a {:href "/"} ;; FIXME
           [:div.fr-header__service-title
            [:svg {:width "240px" :viewBox "0 0 299.179 49.204"}
             [:path {:d "M5.553 2.957v2.956h4.829V0H5.553Zm5.554 0v2.956h4.829V0h-4.829Zm5.553 0v2.956h4.587V0H16.66zm5.553 0v2.956h4.829V0h-4.829zm76.057 0v2.956h4.829V0H98.27zm5.553 0v2.956h4.829V0h-4.829zm53.843 0v2.956h4.829V0h-4.829zm5.794 0v2.956h4.588V0h-4.587zm5.313 0v2.956h4.829V0h-4.829zm5.794 0v2.956h4.588V0h-4.588zM0 10.27v3.112h4.854l-.073-3.05-.073-3.018-2.342-.094L0 7.127zm5.553 0v3.143l2.367-.093 2.342-.093V7.314L7.92 7.22l-2.367-.093zm16.66 0v3.112h4.853l-.072-3.05-.072-3.018-2.343-.094-2.366-.093zm5.554 0v3.112h4.587V7.158h-4.587zm70.672-2.894c-.097.093-.17 1.494-.17 3.112v2.894h4.83V7.158h-2.246c-1.255 0-2.342.093-2.414.218zm5.553-.031c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm48.362 2.925v3.112h4.588V7.158h-4.588zm5.481-2.894c-.097.093-.17 1.494-.17 3.112v2.894h4.83V7.158h-2.246c-1.255 0-2.342.093-2.414.218zm16.732 2.894v3.112h4.588V7.158h-4.588zm5.553 0v3.112h4.588V7.158h-4.587zM0 17.428v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019H0Zm5.553 0v3.143l2.367-.093 2.342-.093v-5.913l-2.342-.094-2.367-.093zm38.197-.093.073 3.05h4.587l.073-3.05.072-3.019h-4.877zm5.554 0 .072 3.05h4.588l.072-3.05.073-3.019H49.23zm5.505.093v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019h-4.853zm5.601-.093.073 3.05h4.587l.073-3.05.072-3.019h-4.877zm21.248 0 .072 3.05h4.588l.072-3.05.073-3.019h-4.878zm5.553 0 .073 3.05 2.366.093 2.342.093v-6.255h-4.853zm5.505.093v3.143l2.366-.093 2.343-.093.072-3.05.072-3.019h-4.853zm5.602-.093.072 3.05 2.367.093 2.342.093v-6.255h-4.854zm5.553 0 .073 3.05h4.587l.073-3.05.072-3.019h-4.877zm15.936 0 .072 3.05h4.588l.072-3.05.073-3.019h-4.878zm5.553 0 .073 3.05 2.366.093 2.342.093v-6.255h-4.853zm5.553 0 .073 3.05h4.587l.073-3.05.072-3.019h-4.877zm5.747.093v3.112h4.587v-6.224h-4.587zm15.694 0v3.112h4.588v-6.224h-4.588zm5.36-.093.073 3.05h4.587l.073-3.05.072-3.019h-4.877zm38.342.093v3.112h4.588v-6.224h-4.588zm5.554 0v3.112h4.587v-6.224h-4.587zm5.553 0v3.112h4.587v-6.224h-4.587zm5.553 0v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019h-4.853zm15.936 0v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019h-4.853zm5.601-.093.073 3.05h4.587l.073-3.05.072-3.019h-4.877zm16.66 0 .073 3.05h4.587l.073-3.05.072-3.019h-4.877zm5.505.093v3.143l2.367-.093 2.342-.093.072-3.05.073-3.019h-4.854zm10.142 0v3.143l2.365-.093 2.343-.093.072-3.05.072-3.019h-4.853zm5.6-.093.073 3.05h4.588l.072-3.05.073-3.019h-4.878zm16.66 0 .073 3.05 2.366.093 2.342.093v-6.255h-4.853zm5.506.093v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019h-4.853zM0 24.742v2.956h4.829v-5.913H0Zm5.553 0v2.956h4.829v-5.913H5.553Zm32.596 0v2.956h4.829v-5.913h-4.829zm5.553 0v2.956h4.829v-5.913h-4.829zm16.66 0v2.956h4.829v-5.913h-4.829zm5.553 0v2.956h4.829v-5.913h-4.829zm10.141 0v2.956h4.829v-5.913h-4.829zm5.554 0v2.956h4.829v-5.913H81.61zm16.66 0v2.956h4.829v-5.913H98.27zm5.553 0v2.956h4.829v-5.913h-4.829zm10.382 0v2.956h4.829v-5.913h-4.829zm5.554 0v2.956h4.828v-5.913h-4.828zm16.901 0v2.956h4.587v-5.913h-4.587zm5.312 0v2.956h4.828v-5.913h-4.828zm10.382 0v2.956h4.588v-5.913h-4.588zm5.312 0v2.956h4.829v-5.913h-4.829zm11.107 0v2.956h4.829v-5.913h-4.829zm5.794 0v2.956h4.588v-5.913h-4.588zm5.553 0v2.956h4.588v-5.913h-4.587zm10.383 0v2.956h4.829v-5.913h-4.829zm5.553 0v2.956h4.588v-5.913h-4.588zm16.66 0v2.956h4.829v-5.913h-4.829zm5.554 0v2.956h4.587v-5.913h-4.587zm10.382 0v2.956h4.828v-5.913h-4.828zm5.553 0v2.956h4.829v-5.913h-4.829zm16.66 0v2.956h4.829v-5.913h-4.829zm5.553 0v2.956h4.829v-5.913h-4.829zm10.142 0v2.956h4.828v-5.913h-4.828zm5.553 0v2.956h4.829v-5.913h-4.829zm16.66 0v2.956h4.828v-5.913h-4.828zm5.553 0v2.956h4.829v-5.913h-4.829zM0 31.744v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094L0 28.601zm5.553 0v3.144l2.367-.094 2.342-.093v-5.913l-2.342-.094-2.367-.093zm32.596 0v3.112h4.829v-6.224h-4.829zm5.553 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm16.66 0v3.112h4.829v-6.224h-4.829zm5.553 0v3.144l2.367-.094 2.342-.093v-5.913l-2.342-.094-2.367-.093zm10.141 0v3.112h4.829v-6.224h-4.829zm5.554 0v3.112h4.829v-6.224H81.61zm16.756-2.707c-.072.249-.096 1.618-.048 3.05l.072 2.614 2.367.093 2.342.094v-6.256h-2.294c-1.714 0-2.342.125-2.439.405zm5.457 2.707v3.112h4.829v-6.224h-4.829zm10.479-2.707c-.073.249-.097 1.618-.049 3.05l.073 2.614 2.366.093 2.342.094v-6.256h-2.294c-1.714 0-2.342.125-2.438.405zm5.457 2.707v3.112h4.828v-6.224h-4.828zm5.649-2.707c-.072.249-.096 1.618-.048 3.05l.073 2.614 2.366.093 2.342.094v-6.256h-2.294c-1.714 0-2.342.125-2.439.405zm5.457 2.707v3.112h4.829v-6.224h-4.829zm5.795 0v3.112h4.587v-6.224h-4.587zm5.408-2.707c-.072.249-.096 1.618-.048 3.05l.073 2.614 2.366.093 2.342.094v-6.256h-2.294c-1.714 0-2.342.125-2.439.405zm10.286 2.707v3.112h4.588v-6.224h-4.588zm5.409-2.707c-.073.249-.097 1.618-.049 3.05l.073 2.614 2.366.093 2.342.094v-6.256H160.2c-1.714 0-2.342.125-2.438.405zm16.804 2.707v3.112h4.588v-6.224h-4.588zm5.553 0v3.112h4.588v-6.224h-4.587zm10.383 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm5.553 0v3.112h4.588v-6.224h-4.588zm16.66 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm5.554 0v3.112h4.587v-6.224h-4.587zm10.382 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm5.553 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm16.66 0v3.112h4.829v-6.224h-4.829zm5.553 0v3.144l2.367-.094 2.342-.093v-5.913l-2.342-.094-2.367-.093zm10.142 0v3.112h4.828v-6.224h-4.828zm5.553 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm16.66 0v3.112h4.828v-6.224h-4.828zm5.553 0v3.112h4.829v-6.224h-4.829zM0 38.747v2.956h4.829V35.79H0Zm5.553 0v2.956h4.829V35.79H5.553Zm16.66 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.587V35.79h-4.587zm10.382 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.829V35.79h-4.829zm16.66 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.829V35.79h-4.829zm10.141 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.829V35.79H81.61zm16.66 0v2.956h4.829V35.79H98.27zm5.553 0v2.956h4.829V35.79h-4.829zm10.382 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.828V35.79h-4.828zm32.595 0v2.956h4.588V35.79h-4.588zm5.312 0v2.956h4.829V35.79h-4.829zm16.901 0v2.956h4.588V35.79h-4.588zm5.553 0v2.956h4.588V35.79h-4.587zm10.383 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.588V35.79h-4.588zm16.66 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.587V35.79h-4.587zm10.382 0v2.956h4.828V35.79h-4.828zm5.553 0v2.956h4.829V35.79h-4.829zm16.66 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.829V35.79h-4.829zm15.695 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.828V35.79h-4.828zm5.553 0v2.956h4.828V35.79h-4.828zM5.553 46.06v3.144l2.367-.094 2.342-.093v-5.913L7.92 43.01l-2.367-.093zm5.554 0v3.112h4.853l-.073-3.05-.072-3.018-2.342-.094-2.366-.093zm5.553 0v3.112h4.587v-6.224H16.66zm5.722-2.925c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.072-3.018-2.174-.094c-1.207-.03-2.27 0-2.366.125zm21.489 0c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.554 0c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.073-3.05-.072-3.018-2.173-.094c-1.208-.03-2.27 0-2.366.125zm5.384 2.925v3.112h4.853l-.072-3.05-.073-3.018-2.342-.094-2.366-.093zm5.722-2.925c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm21.248 0c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.073-3.05-.072-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.553.031c-.097.093-.17 1.494-.17 3.112v2.894h4.83v-6.224h-2.246c-1.255 0-2.342.093-2.414.218zm5.384 2.894v3.112h4.853l-.072-3.05-.072-3.018-2.343-.094-2.366-.093zm5.723-2.894c-.097.093-.17 1.494-.17 3.112v2.894h4.83v-6.224h-2.246c-1.255 0-2.342.093-2.414.218zm5.553-.031c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm15.936 0c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.073-3.05-.072-3.018-2.173-.094c-1.208-.03-2.27 0-2.366.125zm5.552.031c-.096.093-.168 1.494-.168 3.112v2.894h4.828v-6.224h-2.246c-1.255 0-2.342.093-2.414.218zm5.554-.031c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.626 2.925v3.112h4.587v-6.224h-4.587zm21.175-2.925c-.097.124-.17 1.525-.17 3.143v2.894h4.855l-.073-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.625 2.925v3.112h4.588v-6.224h-4.587zm5.482-2.894c-.097.093-.17 1.494-.17 3.112v2.894h4.83v-6.224h-2.246c-1.255 0-2.342.093-2.414.218zm5.625 2.894v3.112h4.588v-6.224h-4.588zm21.489 0v3.112h4.588v-6.224h-4.588zm5.554 0v3.112h4.587v-6.224h-4.587zm5.553 0v3.112h4.587v-6.224h-4.587zm5.553 0v3.112h4.853l-.072-3.05-.073-3.018-2.342-.094-2.366-.093zm21.658-2.925c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.384 2.925v3.112h4.854l-.073-3.05-.072-3.018-2.342-.094-2.367-.093zm5.554 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm5.722-2.925c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm26.801 0c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.385 2.925v3.112h4.852l-.072-3.05-.073-3.018-2.342-.094-2.366-.093z"}]]]]
          [:p.fr-header__service-tagline (i/i lang [:index-title])]]]
        [:div.fr-header__tools
         [:div.fr-header__tools-links
          [:ul.fr-links-group
           [:li [:a.fr-link {:href "https://twitter.com/codegouvfr"} "@codegouvfr"]]
           [:li [:a.fr-link {:href "/#/feeds"} (i/i lang [:rss-feed])]]
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
          [:a.fr-nav__link
           {:aria-current (when (= path "/groups") "page")
            :href         "#/groups"}
           (i/i lang [:orgas-or-groups])]]
         [:li.fr-nav__item
          [:a.fr-nav__link
           {:aria-current (when (= path "/repos") "page")
            :title        (i/i lang [:repos-of-source-code])
            :href         "#/repos"}
           (i/i lang [:Repos])]]
         [:li.fr-nav__item
          [:a.fr-nav__link
           {:aria-current (when (= path "/libs") "page")
            :title        (i/i lang [:Libraries])
            :href         "#/libs"}
           (i/i lang [:Libraries])]]
         [:li.fr-nav__item
          [:a.fr-nav__link
           {:aria-current (when (= path "/deps") "page")
            :title        (i/i lang [:deps-stats])
            :href         "#/deps"}
           (i/i lang [:Deps])]]
         [:li.fr-nav__item
          [:a.fr-nav__link
           {:aria-current (when (= path "/sill") "page")
            :title        (i/i lang [:sill-stats])
            :href         "#/sill"}
           (i/i lang [:Sill])]]
         [:li.fr-nav__item
          [:a.fr-nav__link
           {:aria-current (when (= path "/services") "page")
            :title        (i/i lang [:papillon-title])
            :href         "#/services"}
           (i/i lang [:Papillon])]]
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
           (i/i lang [:About])]]]]]]]))

(defn subscribe [lang]
  [:div.fr-follow
   [:div.fr-container
    [:div.fr-grid-row
     [:div.fr-col-12.fr-col-md-4
      [:div.fr-follow__special
       [:div
        [:h1.fr-h5.fr-follow__title (i/i lang [:contact])]
        [:div.fr-text--sm.fr-follow__desc
         (i/i lang [:contact-title])]]]]
     [:div.fr-col-12.fr-col-md-5
      [:div.fr-follow__newsletter
       [:div
        [:h1.fr-h5.fr-follow__title (i/i lang [:bluehats])]
        [:p.fr-text--sm.fr-follow__desc
         (i/i lang [:bluehats-desc])]
        [:a.fr-btn
         {:type "button"
          :href "https://infolettres.etalab.gouv.fr/subscribe/bluehats@mail.etalab.studio"} (i/i lang [:subscribe])]]]]
     ;; Follow elsewhere
     [:div.fr-col-12.fr-col-md-3
      [:div.fr-share
       [:p.fr-h5.fr-mb-3v (i/i lang [:find-us])]
       [:div.fr-share__group
        [:a.fr-share__link
         {:href       "https://sr.ht/~etalab/"
          :aria-label (i/i lang [:sourcehut-link])
          :title      (new-tab (i/i lang [:sourcehut-link]) lang)
          :rel        "noreferrer noopener"
          :target     "_blank"}
         "SourceHut"]
        [:a.fr-share__link
         {:href       "https://mastodon.social/@codegouvfr"
          :aria-label (i/i lang [:mastodon-follow])
          :title      (new-tab (i/i lang [:mastodon-follow]) lang)
          :rel        "noreferrer noopener"
          :target     "_blank"}
         "Mastodon"]
        [:a.fr-share__link
         {:href       "https://twitter.com/codegouvfr"
          :aria-label (i/i lang [:twitter-follow])
          :title      (new-tab (i/i lang [:twitter-follow]) lang)
          :rel        "noreferrer noopener"
          :target     "_blank"}
         "Twitter"]]]]]]])

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
             [:img {:src "/img/artwork/light.svg"}]]]
           [:div.fr-radio-group.fr-radio-rich
            [:input#fr-radios-theme-dark
             {:type "radio" :name "fr-radios-theme" :value "dark"}]
            [:label.fr-label {:for "fr-radios-theme-dark"}
             (i/i lang [:modal-theme-dark])]
            [:div.fr-radio-rich__img {:data-fr-inject true}
             [:img {:src "/img/artwork/dark.svg"}]]]
           [:div.fr-radio-group.fr-radio-rich
            [:input#fr-radios-theme-system
             {:type "radio" :name "fr-radios-theme" :value "system"}]
            [:label.fr-label {:for "fr-radios-theme-system"}
             (i/i lang [:modal-theme-system])]
            [:div.fr-radio-rich__img {:data-fr-inject true}
             [:img {:src "/img/artwork/system.svg"}]]]]]]]]]]]])

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
       [:a {:href "https://communs.numerique.gouv.fr"}
        (i/i lang [:footer-desc-link])]]
      [:ul.fr-footer__content-list
       [:li.fr-footer__content-item
        [:a.fr-footer__content-link
         {:href "https://data.gouv.fr"} "data.gouv.fr"]]
       [:li.fr-footer__content-item
        [:a.fr-footer__content-link
         {:href "https://etalab.gouv.fr"} "etalab.gouv.fr"]]
       [:li.fr-footer__content-item
        [:a.fr-footer__content-link
         {:href "https://numerique.gouv.fr"} "numerique.gouv.fr"]]
       [:li.fr-footer__content-item
        [:a.fr-footer__content-link
         {:href "https://gouvernement.fr"} "gouvernement.fr"]]]]]
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
      [:div.fr-card__img
       [:img.fr-responsive-img {:src "/img/repositories.jpg" :alt ""}]]]]
    [:div.fr-col-6.fr-p-2w
     [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
      [:div.fr-card__body
       [:div.fr-card__title
        [:a.fr-card__link
         {:href  "#/groups"
          :title (i/i lang [:orgas-or-groups])}
         (i/i lang [:orgas])]]
       [:div.fr-card__desc (i/i lang [:home-groups-desc])]]
      [:div.fr-card__img
       [:img.fr-responsive-img {:src "/img/organizations.jpg" :alt ""}]]]]
    [:div.fr-col-6.fr-p-2w
     [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
      [:div.fr-card__body
       [:div.fr-card__title
        [:a.fr-card__link
         {:href "#/libs"}
         (i/i lang [:Libraries])]]
       [:div.fr-card__desc (i/i lang [:home-sill-desc])]]
      [:div.fr-card__img
       [:img.fr-responsive-img {:src "/img/libraries.jpg" :alt ""}]]]]
    [:div.fr-col-6.fr-p-2w
     [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
      [:div.fr-card__body
       [:div.fr-card__title
        [:a.fr-card__link
         {:href  "#/deps"
          :title (i/i lang [:deps-stats])}
         (i/i lang [:Deps])]]
       [:div.fr-card__desc (i/i lang [:home-deps-desc])]]
      [:div.fr-card__img
       [:img.fr-responsive-img {:src "/img/dependencies.jpg" :alt ""}]]]]
    [:div.fr-col-6.fr-p-2w
     [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
      [:div.fr-card__body
       [:div.fr-card__title
        [:a.fr-card__link
         {:href "#/sill"}
         (i/i lang [:sill-stats])]]
       [:div.fr-card__desc (i/i lang [:home-sill-desc])]]
      [:div.fr-card__img
       [:img.fr-responsive-img {:src "/img/sill.jpg" :alt ""}]]]]
    [:div.fr-col-6.fr-p-2w
     [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
      [:div.fr-card__body
       [:div.fr-card__title
        [:a.fr-card__link
         {:href "#/services"}
         (i/i lang [:papillon-title])]]
       [:div.fr-card__desc (i/i lang [:home-papillon-desc])]]
      [:div.fr-card__img
       [:img.fr-responsive-img {:src "/img/services.jpg" :alt ""}]]]]
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

(defn main-page [q license language]
  (let [lang @(re-frame/subscribe [:lang?])
        view @(re-frame/subscribe [:view?])]
    [:div
     (banner lang)
     [:main#main.fr-container.fr-container--fluid.fr-mb-3w
      {:role "main"}
      [main-menu q lang view]
      (condp = view
        :home     [home-page lang]
        :orgas    [orgas-page lang]
        :repos    [repos-page-class lang license language]
        :libs     [libs-page-class lang]
        :sill     [sill-page-class lang]
        :papillon [papillon-page-class lang]
        :stats    [stats-page-class lang]
        :deps     [deps-page lang]
        :legal    (condp = lang "fr" (inline-page "legal.fr.md")
                         (inline-page "legal.en.md"))
        :a11y     (condp = lang "fr" (inline-page "a11y.fr.md")
                         (inline-page "a11y.en.md"))
        :about    (condp = lang "fr" (inline-page "about.fr.md")
                         (inline-page "about.en.md"))
        :about    (condp = lang "fr" (inline-page "about.fr.md")
                         (inline-page "about.en.md"))
        :sitemap  (condp = lang "fr" (inline-page "sitemap.fr.md")
                         (inline-page "sitemap.en.md"))
        :feeds    (condp = lang "fr" (inline-page "feeds.fr.md")
                         (inline-page "feeds.en.md"))
        :error    (condp = lang "fr" (inline-page "error.fr.md")
                         (inline-page "error.en.md"))
        (condp = lang "fr" (inline-page "error.fr.md")
               (inline-page "error.en.md")))]
     (subscribe lang)
     (footer lang)
     (display-parameters-modal lang)]))

(defn main-class []
  (let [q        (reagent/atom nil)
        license  (reagent/atom nil)
        language (reagent/atom nil)]
    (reagent/create-class
     {:display-name   "main-class"
      :component-did-mount
      (fn []
        (GET "/data/platforms.csv"
             :handler
             #(reset! platforms (conj (map first (next (js->clj (csv/parse %)))) "sr.ht")))
        (GET "/data/deps.json"
             :handler
             #(reset! deps (map (comp bean clj->js) %)))
        (GET "/data/orgas.json"
             :handler
             #(reset! orgas (map (comp bean clj->js) %))))
      :reagent-render (fn [] (main-page q license language))})))

;; Setup router and init

(defn on-navigate [match]
  (let [page (keyword (:name (:data match)))]
    ;; FIXME: When returning to :deps, ensure dp-filter is nil
    (when (= page :deps) (reset! dp-filter nil))
    (re-frame/dispatch [:filter! {:q ""}])
    (re-frame/dispatch [:path! (:path match)])
    (re-frame/dispatch [:view! page (:query-params match)])))

(defonce routes
  ["/"
   ["" :home]
   ["groups" :orgas]
   ["repos" :repos]
   ["libs" :libs]
   ["sill" :sill]
   ["services" :papillon]
   ["stats" :stats]
   ["deps" :deps]
   ["legal" :legal]
   ["a11y" :a11y]
   ["about" :about]
   ["sitemap" :sitemap]
   ["feeds" :feeds]
   ["error" :error]])

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
    (start-display-filter-loop)
    (reagent.dom/render
     [main-class]
     (.getElementById js/document "app"))))
