;; Copyright (c) 2019-2021 DINUM, Bastien Guerry <bastien.guerry@data.gouv.fr>
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
            [markdown-to-hiccup.core :as md]
            [semantic-csv.core :as sc])
  (:require-macros [codegouvfr.macros :refer [inline-resource]]))

;; Defaults

(defonce repos-per-page 100)

(defonce orgas-per-page 20)

(defonce deps-per-page 100)

(defonce timeout 100)

(def unix-epoch "1970-01-01T00:00:00Z")

(defonce init-filter {:q nil :g nil :d nil :repo nil :orga nil :language nil :license nil :platform "all"})

(defonce annuaire-prefix "https://lannuaire.service-public.fr/")

(defonce srht-repo-basedir-prefix "https://git.sr.ht/~etalab/code.gouv.fr/tree/master/item/")

(defonce filter-chan (async/chan 100))

(defonce display-filter-chan (async/chan 100))

(defonce repos-mapping
  {:u  :last_update
   :d  :description
   :a? :is_archived
   :f? :is_fork
   :l  :language
   :li :license
   :n  :name
   :f  :forks_count
   :i  :open_issues_count
   :s  :stars_count
   :o  :organization_name
   :p  :platform
   :r  :repository_url
   :t  :topics})

(defonce orgas-mapping
  {:d  :description
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
   :au :avatar_url})

(defonce deps-mapping
  {:n :name
   :t :type
   :d :description
   :l :link
   :u :updated
   :r :repositories})

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

(def to-hiccup-repl
  {:h2 :h2.fr-h2.fr-mt-3w
   :p  :p.fr-my-3w
   :li :li.fr-ml-2w})

(defn to-hiccup
  "Convert a markdown `s` string to hiccup structure."
  [s]
  (->> s
       (md/md->hiccup)
       (md/component)
       (walk/prewalk-replace to-hiccup-repl)))

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

(defn- get-first-match-s-for-k-in-m [s k m]
  (reduce #(when (= (k %2) s) (reduced %2)) nil m))

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

(defn top-clean-up [top param title]
  (let [total (reduce + (map val top))]
    (apply merge
           (sequence
            (comp
             (map (fn [[k v]]
                    [k (js/parseFloat
                        (gstring/format "%.2f" (* (/ v total) 100)))]))
             (map #(let [[k v] %]
                     {[:a
                       {:title title
                        :href  (str "#/repos?" param "=" k)} k] v})))
            top))))

;; Filters

(defn apply-repos-filters [m]
  (let [f        @(re-frame/subscribe [:filter?])
        deps-raw @(re-frame/subscribe [:deps-raw?])
        dp       (:d f)
        s        (:q f)
        g        (:g f)
        la       (:language f)
        pl       (:platform f)
        lic      (:license f)
        e        (:is-esr f)
        fk       (:is-not-fork f)
        li       (:is-licensed f)]
    (filter
     #(and (if dp (contains?
                   (into #{}
                         (:r (get-first-match-s-for-k-in-m
                              dp :n
                              deps-raw)))
                   (:r %)) true)
           (if e (:e %) true)
           (if fk (not (:f? %)) true)
           (if li (let [l (:li %)] (and l (not= l "Other"))) true)
           (if lic (s-includes? (:li %) lic) true)
           (if la
             (some (into #{} (list (s/lower-case (or (:l %) ""))))
                   (s/split (s/lower-case la) #" +"))
             true)
           (if (= pl "all") true (s-includes? (:r %) pl))
           (if g (s-includes? (:r %) g) true)
           (if s (s-includes?
                  (s/join " " [(:n %) (:r %) (:o %) (:t %) (:d %)])
                  s)
               true))
     m)))

(defn apply-deps-filters [m]
  (let [f @(re-frame/subscribe [:filter?])
        s (:q f)]
    (filter
     #(if s (s-includes?
             (s/join " " [(:n %) (:t %) (:d %)]) s)
          true)
     m)))

(defn apply-orgas-filters [m]
  (let [f @(re-frame/subscribe [:filter?])
        s (:q f)]
    (filter
     #(if s (s-includes?
             (s/join " " [(:n %) (:l %) (:d %) (:h %) (:o %)])
             s)
          true)
     m)))

(defn close-filter-button [lang ff t reinit]
  [:span
   [:a.fr-link.fr-fi-close-circle-line.fr-link--icon-right
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
   {:repos          nil
    :orgas          nil
    ;; :levent         nil
    :repos-page     0
    :orgas-page     0
    :deps-page      0
    :sort-repos-by  :reused
    :sort-orgas-by  :repos
    :sort-deps-by   :repos
    :view           :orgas
    :reverse-sort   false
    :filter         init-filter
    :display-filter init-filter
    :lang           "en"
    :path           ""}))

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
 :update-platforms!
 (fn [db [_ platforms]] (assoc db :platforms platforms)))

(re-frame/reg-event-db
 :update-repos!
 (fn [db [_ repos]] (assoc db :repos repos)))

(re-frame/reg-event-db
 :update-deps!
 (fn [db [_ deps]] (assoc db :deps deps)))

(re-frame/reg-event-db
 :update-deps-raw!
 (fn [db [_ deps-raw]] (assoc db :deps-raw deps-raw)))

(re-frame/reg-event-db
 :update-dep-repos!
 (fn [db [_ dep-repos]] (assoc db :dep-repos dep-repos)))

(re-frame/reg-event-db
 :filter!
 (fn [db [_ s]]
   (re-frame/dispatch [:repos-page! 0])
   (re-frame/dispatch [:orgas-page! 0])
   (re-frame/dispatch [:deps-page! 0])
   (update-in db [:filter] merge s)))

(re-frame/reg-event-db
 :display-filter!
 (fn [db [_ s]]
   (update-in db [:display-filter] merge s)))

(re-frame/reg-event-db
 :repos-page!
 (fn [db [_ n]] (assoc db :repos-page n)))

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
   (re-frame/dispatch [:filter! (merge init-filter query-params)])
   (re-frame/dispatch [:display-filter! (merge init-filter query-params)])
   (assoc db :view view)))

(re-frame/reg-event-db
 :update-orgas!
 (fn [db [_ orgas]] (when orgas (assoc db :orgas orgas))))

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
 :platforms?
 (fn [db _] (:platforms db)))

(re-frame/reg-sub
 :sort-repos-by?
 (fn [db _] (:sort-repos-by db)))

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
 :repos?
 (fn [db _]
   (let [repos0 (:repos db)
         repos  (case @(re-frame/subscribe [:sort-repos-by?])
                  :name   (reverse (sort-by :n repos0))
                  :forks  (sort-by :f repos0)
                  :stars  (sort-by :s repos0)
                  ;; :issues (sort-by :i repos0)
                  :reused (sort-by :g repos0)
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
 :deps?
 (fn [db _]
   (let [orga  (:orga @(re-frame/subscribe [:filter?]))
         deps0 (:deps db)
         deps  (case @(re-frame/subscribe [:sort-deps-by?])
                 :name        (reverse (sort-by :n deps0))
                 :type        (reverse (sort-by :t deps0))
                 :description (sort-by :d deps0)
                 :repos       (sort-by
                               #(if orga
                                  (count (filter (fn [a] (re-find (re-pattern orga) a)) (:r %)))
                                  (count (:r %)))
                               deps0)
                 deps0)]
     (apply-deps-filters
      (if @(re-frame/subscribe [:reverse-sort?])
        deps (reverse deps))))))

(re-frame/reg-sub
 :deps-raw?
 (fn [db _] (:deps-raw db)))

(re-frame/reg-sub
 :orgas?
 (fn [db _]
   (let [orgs  (:orgas db)
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
          :repos {:sub :repos-page? :evt      :repos-page!
                  :cnt :repos?      :per-page repos-per-page}
          :deps  {:sub :deps-page? :evt      :deps-page!
                  :cnt :deps?      :per-page deps-per-page}
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

;; Main structure

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
           [:a.fr-link
            {:class    (when (= rep-f :name) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-repos-alpha])
             :on-click #(re-frame/dispatch [:sort-repos-by! :name])}
            (i/i lang [:orga-repo])]]
          [:th.fr-col (i/i lang [:description])]
          [:th.fr-col-1
           [:a.fr-link
            {:class    (when (= rep-f :date) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-update-date])
             :on-click #(re-frame/dispatch [:sort-repos-by! :date])}
            (i/i lang [:update-short])]]
          [:th.fr-col-1
           [:a.fr-link
            {:class    (when (= rep-f :forks) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-forks])
             :on-click #(re-frame/dispatch [:sort-repos-by! :forks])}
            (i/i lang [:forks])]]
          [:th.fr-col-1
           [:a.fr-link
            {:class    (when (= rep-f :stars) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-stars])
             :on-click #(re-frame/dispatch [:sort-repos-by! :stars])}
            (i/i lang [:Stars])]]
          [:th.fr-col-1
           [:a.fr-link
            {:class    (when (= rep-f :reused) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-reused])
             :on-click #(re-frame/dispatch [:sort-repos-by! :reused])}
            (i/i lang [:reused])]]]]
        (into [:tbody]
              (for [dd (take repos-per-page
                             (drop (* repos-per-page repos-page) repos))]
                ^{:key dd}
                (let [{:keys [a? d f li n o r s u g]}
                      dd
                      group (subs r 0 (- (count r) (inc (count n))))]
                  [:tr
                   ;; Repo (orga)
                   [:td
                    [:div
                     [:a.fr-link
                      {:href   (str "https://archive.softwareheritage.org/browse/origin/" r)
                       :title  (new-tab (i/i lang [:swh-link]) lang)
                       :rel    "noreferrer noopener"
                       :target "_blank"}
                      [:img {:width "18px" :src "/img/swh-logo.png"
                             :alt   "Software Heritage logo"}]]
                     [:a {:href   r
                          :target "_blank"
                          :rel    "noreferrer noopener"
                          :title  (new-tab
                                   (str
                                    (i/i lang [:go-to-repo])
                                    (when li (str (i/i lang [:under-license]) li))) lang)}
                      n]
                     " ("
                     [:a {:href  (rfe/href :repos {:lang lang} {:g group})
                          :title (i/i lang [:browse-repos-orga])}
                      o]
                     ")"]]
                   ;; Description
                   [:td {:title (when a? (i/i lang [:repo-archived]))}
                    [:span
                     ;; FIXME: not working?
                     ;; (when dp
                     ;;   [:span
                     ;;    [:a.fr-link
                     ;;     {:title (i/i lang [:Deps])
                     ;;      :href  (rfe/href :deps {:lang lang} {:repo r})}
                     ;;     [:span.fr-fi-search-line {:aria-hidden true}]]
                     ;;    " "])
                     (if a? [:em d] d)]]
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
                      :target "_blank"
                      :href   (str r "/network/dependents")}
                     g]]])))]])))

(defn repos-page [lang license language platform]
  (let [repos          @(re-frame/subscribe [:repos?])
        repos-pages    @(re-frame/subscribe [:repos-page?])
        count-pages    (count (partition-all repos-per-page repos))
        first-disabled (zero? repos-pages)
        last-disabled  (= repos-pages (dec count-pages))]
    [:div.fr-grid
     [:div.fr-grid-row
      ;; Download link
      [:a.fr-link
       {:title    (i/i lang [:download])
        :on-click #(download-as-csv!
                    (map
                     (fn [r] (set/rename-keys
                              (select-keys r (keys repos-mapping))
                              repos-mapping))
                     repos)
                    (str "codegouvfr-repositories-" (todays-date) ".csv"))}
       [:span.fr-fi-download-line {:aria-hidden true}]]
      ;; Generaltion information
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
      [:select.fr-select.fr-col.fr-m-1w
       {:value (or @platform "all")
        :on-change
        (fn [e]
          (let [ev (.-value (.-target e))]
            (reset! platform ev)
            (async/go
              (async/>! display-filter-chan {:platform ev})
              (async/<! (async/timeout timeout))
              (async/>! filter-chan {:platform ev}))))}
       [:option {:value "all"} (i/i lang [:all-forges])]
       (for [x @(re-frame/subscribe [:platforms?])]
         ^{:key x}
         [:option {:value x} x])]
      [:div.fr-checkbox-group.fr-col.fr-m-1w
       [:input#1 {:type      "checkbox" :name "1"
                  :on-change #(let [v (.-checked (.-target %))]
                                (set-item! :is-not-fork v)
                                (re-frame/dispatch [:filter! {:is-not-fork v}]))}]
       [:label.fr-label
        {:for   "1"
         :title (i/i lang [:only-not-fork-title])}
        (i/i lang [:only-not-fork])]]
      [:div.fr-checkbox-group.fr-col.fr-m-1w
       [:input#2 {:type      "checkbox" :name "2"
                  :on-change #(let [v (.-checked (.-target %))]
                                (set-item! :is-licensed v)
                                (re-frame/dispatch [:filter! {:is-licensed v}]))}]
       [:label.fr-label {:for "2"} (i/i lang [:only-with-license])]]

      [:div.fr-checkbox-group.fr-col.fr-m-1w
       [:input#3 {:type      "checkbox" :name "3"
                  :on-change #(let [v (.-checked (.-target %))]
                                (set-item! :is-esr v)
                                (re-frame/dispatch [:filter! {:is-esr v}]))}]
       [:label.fr-label
        {:for "3" :title (i/i lang [:only-her-title])}
        (i/i lang [:only-her])]]]
     ;; Main repos table display
     [repos-table lang (count repos)]
     ;; Bottom pagination block
     [navigate-pagination :repos first-disabled last-disabled repos-pages count-pages]]))

(defn repos-page-class [lang license language platform]
  (reagent/create-class
   {:display-name   "repos-page-class"
    :component-did-mount
    (fn []
      (GET "/data/repos.json"
           :handler
           #(re-frame/dispatch
             [:update-repos! (map (comp bean clj->js) %)])))
    :reagent-render (fn [] (repos-page lang license language platform))}))

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
           [:a.fr-link
            {:class    (when (= org-f :name) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-orgas-alpha])
             :on-click #(re-frame/dispatch [:sort-orgas-by! :name])}
            (i/i lang [:orgas])]]
          [:th.fr-col-6 (i/i lang [:description])]
          [:th.fr-col-1
           [:a.fr-link
            {:class    (when (= org-f :repos) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-repos])
             :on-click #(re-frame/dispatch [:sort-orgas-by! :repos])}
            (i/i lang [:Repos])]]
          [:th.fr-col-1
           [:a.fr-link
            {:class    (when (= org-f :date) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-orgas-creation])
             :on-click #(re-frame/dispatch [:sort-orgas-by! :date])}
            (i/i lang [:created-at])]]]]

        (into [:tbody]
              (for [dd (take orgas-per-page
                             (drop (* orgas-per-page @(re-frame/subscribe [:orgas-page?]))
                                   orgas))]
                ^{:key dd}
                (let [{:keys [n l d o h an au c r]} dd]
                  [:tr
                   [:td
                    (if (or h an)
                      (let [w (if h h (str annuaire-prefix an))]
                        [:a.fr-link
                         {:title  (new-tab (i/i lang [:go-to-website]) lang)
                          :target "_blank"
                          :rel    "noreferrer noopener"
                          :href   w}
                         [:img {:src au :width "100%" :alt ""}]])
                      [:img {:src au :width "100%" :alt ""}])]
                   [:td
                    [:a {:target "_blank"
                         :rel    "noreferrer noopener"
                         :title  (new-tab (i/i lang [:go-to-orga]) lang)
                         :href   o} (or n l)]]
                   [:td d]
                   [:td
                    {:style {:text-align "center"}}
                    [:a {:title (i/i lang [:go-to-repos])
                         :href  (rfe/href :repos {:lang lang}
                                          {:g (s/replace o "/groups/" "/")})}
                     r]]
                   [:td {:style {:text-align "center"}}
                    (to-locale-date c)]])))]])))

(defn orgas-page [lang]
  (let [orgas          @(re-frame/subscribe [:orgas?])
        orgas-cnt      (count orgas)
        orgas-pages    @(re-frame/subscribe [:orgas-page?])
        count-pages    (count (partition-all orgas-per-page orgas))
        first-disabled (zero? orgas-pages)
        last-disabled  (= orgas-pages (dec count-pages))]
    [:div.fr-grid
     [:div.fr-grid-row
      ;; Download link
      [:a.fr-link.fr-m-1w
       {:title    (i/i lang [:download])
        :on-click #(download-as-csv!
                    (map
                     (fn [r] (set/rename-keys
                              (select-keys r (keys orgas-mapping))
                              orgas-mapping))
                     orgas)
                    (str "codegouvfr-organizations-" (todays-date) ".csv"))}
       [:span.fr-fi-download-line {:aria-hidden true}]]
      ;; General information
      [:strong.fr-m-auto
       (let [orgs (count orgas)]
         (if (< orgs 2)
           (str orgs (i/i lang [:one-group]))
           (str orgs (i/i lang [:groups]))))]
      ;; Top pagination block
      [navigate-pagination :orgas first-disabled last-disabled orgas-pages count-pages]]

     [orgas-table lang orgas-cnt]
     [navigate-pagination :orgas first-disabled last-disabled orgas-pages count-pages]]))

(defn deps-table [lang deps repo orga]
  (let [dep-f     @(re-frame/subscribe [:sort-deps-by?])
        deps-page @(re-frame/subscribe [:deps-page?])]
    [:div.fr-table.fr-table--no-caption.fr-table--layout-fixed
     [:table
      [:caption (i/i lang [:deps])]
      [:thead.fr-grid.fr-col-12
       [:tr
        [:th.fr-col-3
         [:a.fr-link
          {:class    (when (= dep-f :name) "fr-fi-checkbox-circle-line fr-link--icon-left")
           :title    (i/i lang [:sort-name])
           :on-click #(re-frame/dispatch [:sort-deps-by! :name])}
          (i/i lang [:name])]]
        [:th.fr-col-1
         [:a.fr-link
          {:class    (when (= dep-f :type) "fr-fi-checkbox-circle-line fr-link--icon-left")
           :title    (i/i lang [:sort-type])
           :on-click #(re-frame/dispatch [:sort-deps-by! :type])}
          (i/i lang [:type])]]
        [:th.fr-col-5
         [:a.fr-link
          {:class    (when (= dep-f :description) "fr-fi-checkbox-circle-line fr-link--icon-left")
           :title    (i/i lang [:sort-description-length])
           :on-click #(re-frame/dispatch [:sort-deps-by! :description])}
          (i/i lang [:description])]]
        (when-not repo
          [:th.fr-col-1
           [:a.fr-link
            {:class    (when (= dep-f :repos) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-repos])
             :on-click #(re-frame/dispatch [:sort-deps-by! :repos])}
            (i/i lang [:Repos])]])]]
      (into
       [:tbody]
       (for [dd (take deps-per-page
                      (drop (* deps-per-page deps-page) deps))]
         ^{:key dd}
         (let [{:keys [t n d l r]} dd]
           [:tr
            [:td
             [:a {:href   l
                  :target "_blank"
                  :rel    "noreferrer noopener"
                  :title  (new-tab (i/i lang [:more-info]) lang)} n]]
            [:td t]
            [:td d]
            (when-not repo
              [:td {:style {:text-align "center"}}
               [:a {:title (i/i lang [:list-repos-depending-on-dep])
                    :href  (rfe/href :repos {:lang lang}
                                     (if orga {:d n :g orga} {:d n}))}
                (if-not orga
                  (count r)
                  (count (filter #(re-find (re-pattern orga) %) r)))]])])))]]))

;; (defn stats-deps-table [heading deps lang]
;;   [:div.fr-m-3w
;;    [:h4.fr-h4 heading]
;;    [:div.fr-table
;;     [:table.fr-col-12
;;      [:thead [:tr
;;               [:th (i/i lang [:name])]
;;               [:th (i/i lang [:type])]
;;               [:th (i/i lang [:description])]
;;               [:th (i/i lang [:Repos])]]]
;;      [:tbody
;;       (for [{:keys [n t d l r] :as o} deps]
;;         ^{:key o}
;;         [:tr
;;          [:td [:a {:href   l
;;                    :target "_blank"
;;                    :rel    "noreferrer noopener"
;;                    :title  (new-tab (i/i lang [:more-info]) lang)} n]]
;;          [:td t]
;;          [:td d]
;;          [:td
;;           [:a {:title (i/i lang [:list-repos-depending-on-dep])
;;                :href  (rfe/href :repos {:lang lang} {:d n})}
;;            (count r)]]])]]]])

(defn deps-page [lang repos-sim]
  (let [{:keys [repo orga]} @(re-frame/subscribe [:filter?])
        deps0               @(re-frame/subscribe [:deps?])
        deps                (if-let [s (or repo orga)]
                              (filter #(s-includes? (s/join " " (:r %)) s) deps0)
                              deps0)
        deps-pages          @(re-frame/subscribe [:deps-page?])
        count-pages         (count (partition-all deps-per-page deps))
        first-disabled      (zero? deps-pages)
        last-disabled       (= deps-pages (dec count-pages))]
    [:div.fr-grid
     [:div.fr-grid-row
      ;; Download link
      [:a.fr-link
       {:title    (i/i lang [:download])
        :on-click #(download-as-csv!
                    (map
                     (fn [r] (set/rename-keys
                              (select-keys r (keys deps-mapping))
                              deps-mapping))
                     deps)
                    (str "codegouvfr-dependencies-" (todays-date) ".csv"))}
       [:span.fr-fi-download-line {:aria-hidden true}]]
      ;; General informations
      [:strong.fr-m-auto
       (let [deps (count deps)]
         (if (< deps 2)
           (str deps (i/i lang [:dep]))
           (str deps (i/i lang [:deps]))))]
      ;; Top pagination block
      [navigate-pagination :deps first-disabled last-disabled deps-pages count-pages]]
     ;; Main deps display
     (if (pos? (count deps))
       [deps-table lang deps repo orga]
       [:div.fr-m-3w [:p (i/i lang [:no-dep-found])]])
     ;; Bottom pagination block
     [navigate-pagination :deps first-disabled last-disabled deps-pages count-pages]
     ;; Additional informations
     (when-let [sims (get repos-sim repo)]
       [:div.fr-grid
        [:h4.fr-h4 (i/i lang [:Repos-deps-sim])]
        [:ul
         (for [s sims]
           ^{:key s}
           [:li [:a {:href (rfe/href :deps {:lang lang} {:repo s})} s]])]])]))

(defn deps-page-class [lang]
  (let [deps-repos-sim (reagent/atom nil)]
    (reagent/create-class
     {:display-name   "deps-page-class"
      :component-did-mount
      (fn []
        (GET "/data/deps-repos-sim.json"
             :handler #(reset! deps-repos-sim %)))
      :reagent-render (fn [] (deps-page lang @deps-repos-sim))})))

(defn stats-table [heading data & [thead]]
  [:div.fr-m-3w
   [:h4.fr-h4 heading]
   [:div.fr-table.fr-table--layout-fixed
    [:table
     thead
     [:tbody
      (for [o (reverse (walk/stringify-keys (sort-by val data)))]
        ^{:key (key o)}
        [:tr [:td (key o)] [:td (val o)]])]]]])

(defn stats-tile [l i s]
  [:div.fr-tile.fr-col-2.fr-m-1w
   [:div.fr-tile__body
    [:p.fr-tile__title (i/i l [i])]
    [:div.fr-tile__desc
     [:p.fr-h4 s]]]])

(defn stats-page
  [lang stats deps-total]
  (let [{:keys [repos_cnt orgs_cnt avg_repos_cnt median_repos_cnt
                top_orgs_by_repos top_orgs_by_stars top_licenses
                platforms top_languages]} stats
        ;; Use software_heritage ?
        top_orgs_by_repos_0
        (into {} (map #(vector (str (:organization_name %)
                                    " (" (:platform %) ")")
                               (:count %))
                      top_orgs_by_repos))
        top_languages_1
        (top-clean-up (walk/stringify-keys (-> top_languages
                                               (dissoc :Inconnu)))
                      "language" (i/i lang [:list-repos-with-language]))

        top_licenses_0
        (->> (top-clean-up
              (walk/stringify-keys
               (-> top_licenses
                   (dissoc :Inconnue)
                   (dissoc :Other)))
              "license"
              (i/i lang [:list-repos-using-license]))
             (sort-by val >)
             (take 10))]
    [:div
     [:div.fr-grid-row.fr-grid-row--center {:style {:height "180px"}}
      (stats-tile lang :mean-repos-by-orga avg_repos_cnt)
      (stats-tile lang :orgas-or-groups orgs_cnt)
      (stats-tile lang :repos-of-source-code repos_cnt)
      (stats-tile lang :deps-stats (:deps-total deps-total))
      (stats-tile lang :median-repos-by-orga median_repos_cnt)]

     [:div.fr-grid-row
      [:div.fr-col-6
       (stats-table [:span (i/i lang [:most-used-languages])]
                    top_languages_1
                    [:thead [:tr [:th (i/i lang [:language])] [:th "%"]]])]
      [:div.fr-col-6
       (stats-table [:span (i/i lang [:most-used-identified-licenses])]
                    top_licenses_0
                    [:thead [:tr [:th (i/i lang [:license])] [:th "%"]]])]]

     [:div.fr-grid-row
      [:div.fr-col-6
       (stats-table [:span
                     (i/i lang [:orgas])
                     (i/i lang [:with-more-of])
                     (i/i lang [:repos])]
                    top_orgs_by_repos_0
                    [:thead [:tr [:th (i/i lang [:orgas])] [:th (i/i lang [:Repos])]]])]
      [:div.fr-col-6
       (stats-table [:span
                     (i/i lang [:orgas])
                     (i/i lang [:with-more-of*])
                     (i/i lang [:stars])]
                    top_orgs_by_stars
                    [:thead [:tr [:th (i/i lang [:orgas])] [:th (i/i lang [:Stars])]]])]]

     

     ;; FIXME: the source data are wrong
     ;; [:div.fr-grid-row.fr-grid-row--center
     ;;  [:div.fr-col-12
     ;;   (stats-deps-table (i/i lang [:Deps]) deps lang)]]

     [:div.fr-grid-row
      [:div.fr-col-6
       (stats-table (i/i lang [:distribution-by-platform]) platforms)]
      [:div.fr-col-6
       [:img {:src      "/data/top_licenses.svg" :width "100%"
              :longdesc (i/i lang [:most-used-licenses])
              :alt      (i/i lang [:most-used-licenses])}]]]]))

(defn stats-page-class [lang]
  (let [deps       (reagent/atom nil)
        stats      (reagent/atom nil)
        deps-total (reagent/atom nil)]
    (reagent/create-class
     {:display-name   "stats-page-class"
      :component-did-mount
      (fn []
        (GET "/data/deps-total.json"
             :handler #(reset! deps-total (walk/keywordize-keys %)))
        (GET "/data/deps-top.json"
             :handler #(reset! deps (take 10 (map (comp bean clj->js) %))))
        (GET "/data/stats.json"
             :handler #(reset! stats (walk/keywordize-keys %))))
      :reagent-render (fn [] (stats-page lang @stats @deps-total))})))

(defn main-menu [q lang view]
  [:div
   [:div.fr-grid-row.fr-mt-2w
    [:div.fr-col-12
     (when (or (= view :repos) (= view :orgas) (= view :deps))
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
         (close-filter-button lang ff :repos (merge flt {:d nil})))
       (when-let [ff (not-empty (:orga flt))]
         (close-filter-button lang ff :deps (merge flt {:orga nil})))
       (when-let [ff (not-empty (:repo flt))]
         (close-filter-button lang ff :deps (merge flt {:repo nil})))])]])

(defn banner [lang]
  (let [path @(re-frame/subscribe [:path?])]
    [:header.fr-header {:role "banner"}
     ;; Header body
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
           [:p.fr-header__service-title (i/i lang [:index-title])]]
          [:p.fr-header__service-tagline (i/i lang [:index-subtitle])]]]]]]

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
          [:a.fr-nav__link
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
           {:aria-current (when (= path "/deps") "page")
            :title        (i/i lang [:deps-stats])
            :href         "#/deps"}
           (i/i lang [:Deps])]]
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
      ;; Contact by email
      [:div.fr-follow__special
       [:div
        [:h1.fr-h5.fr-follow__title (i/i lang [:contact])]
        [:div.fr-text--sm.fr-follow__desc
         (to-hiccup (i/i lang [:contact-title]))]]]]
     ;; Subscribe to he newsletter
     [:div.fr-col-12.fr-col-md-5
      [:div.fr-follow__newsletter
       [:div
        [:h1.fr-h5.fr-follow__title (i/i lang [:bluehats])]
        [:p.fr-text--sm.fr-follow__desc
         (i/i lang [:bluehats-desc])]
        [:a
         {:href "https://infolettres.etalab.gouv.fr/subscribe/bluehats@mail.etalab.studio"}
         [:button.fr-btn {:type "button"} (i/i lang [:subscribe])]]]]]
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
       [:a.fr-footer__bottom-link
        {:lang     (if (= lang "fr") "en" "fr")
         :on-click #(re-frame/dispatch
                     [:lang! (if (= lang "fr") "en" "fr")])}
        (i/i lang [:switch-lang])]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href "#/legal"}
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
        {:href "/data/latest.xml" :title (i/i lang [:subscribe-rss-flux])}
        (i/i lang [:rss-feed])]]
      [:li.fr-footer__bottom-item
       [:button.fr-footer__bottom-link.fr-fi-theme-fill.fr-link--icon-left
        {:aria-controls  "fr-theme-modal"
         :title          (str (i/i lang [:modal-title]) " - "
                              (i/i lang [:new-modal]))
         :data-fr-opened false}
        (i/i lang [:modal-title])]]]]]])

;; Pages from md

(defn legal-page [lang]
  [:div.fr-container
   (to-hiccup
    (condp = lang
      "fr" (inline-resource "public/md/legal.fr.md")
      (inline-resource "public/md/legal.en.md")))])

(defn about-page [lang]
  [:div.fr-container.fr-grid.fr-grid--row
   [:div.fr-col-10.fr-col-md-10
    (to-hiccup
     (condp = lang
       "fr" (inline-resource "public/md/about.fr.md")
       (inline-resource "public/md/about.en.md")))]])

(defn sitemap-page [lang]
  [:div.fr-container.fr-grid.fr-grid--row
   [:div.fr-col-10.fr-col-md-10
    (to-hiccup
     (condp = lang
       "fr" (inline-resource "public/md/sitemap.fr.md")
       (inline-resource "public/md/sitemap.en.md")))]])

;; #00AC8C
;; #FF8D7E
;; #FDCF41
;; #484D7A
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
       [:img.fr-responsive-img {:src "/img/keyboard.jpg" :alt ""}]]]]
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
       [:img.fr-responsive-img {:src "/img/rocks.jpg" :alt ""}]]]]]
   [:div.fr-grid-row.fr-grid-row--center
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

(defn error-page  [lang]
  [:div.fr-container.fr-m-3w
   [:h3.fr-h3 (i/i lang [:sorry])]
   [:p (i/i lang [:nothing-here])]
   [:p [:button.fr-btn.fr-btn--secondary
        {:on-click #(rfe/push-state :home)}
        (i/i lang [:back-to-homepage])]]])

(defn main-page [q license language platform]
  (let [lang @(re-frame/subscribe [:lang?])
        view @(re-frame/subscribe [:view?])]
    [:div
     (banner lang)
     [:main#main.fr-container.fr-container--fluid.fr-mb-3w
      {:role "main"}
      [main-menu q lang view]
      (condp = view
        ;; Default page
        :home    [home-page lang]
        ;; Table to display organizations
        :orgas   [orgas-page lang]
        ;; Table to display repositories
        :repos   [repos-page-class lang license language platform]
        ;; Table to display statistics
        :stats   [stats-page-class lang]
        ;; Table to display all dependencies
        :deps    [deps-page-class lang]
        ;; Page for legal mentions
        :legal   [legal-page lang]
        ;; About page
        :about   [about-page lang]
        ;; Sitemap
        :sitemap [sitemap-page lang]
        ;; Fall back on the organizations page
        [error-page lang])]
     (subscribe lang)
     (footer lang)
     (display-parameters-modal lang)]))

(defn main-class []
  (let [q        (reagent/atom nil)
        license  (reagent/atom nil)
        language (reagent/atom nil)
        platform (reagent/atom nil)]
    (reagent/create-class
     {:display-name   "main-class"
      :component-did-mount
      (fn []
        (GET "/data/platforms.csv"
             :handler
             #(re-frame/dispatch
               [:update-platforms! (map first (next (js->clj (csv/parse %))))]))
        (GET "/data/deps.json"
             :handler
             #(do
                (re-frame/dispatch
                 [:update-deps! (map (comp bean clj->js) %)])
                (re-frame/dispatch
                 [:update-deps-raw!
                  (map (comp bean
                             clj->js
                             (fn [e] (dissoc e :t :d :l :r)))
                       %)])))
        (GET "/data/orgas.json"
             :handler
             #(re-frame/dispatch
               [:update-orgas! (map (comp bean clj->js) %)])))
      :reagent-render (fn [] (main-page q license language platform))})))

;; Setup router and init

(defn on-navigate [match]
  (re-frame/dispatch [:path! (:path match)])
  (re-frame/dispatch [:view!
                      (keyword (:name (:data match)))
                      (:query-params match)]))

(defonce routes
  ["/"
   ["" :home]
   ["groups" :orgas]
   ["repos" :repos]
   ["stats" :stats]
   ["deps" :deps]
   ["legal" :legal]
   ["about" :about]
   ["sitemap" :sitemap]
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
