;; Copyright (c) 2019-2021 DINUM, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.core
  (:require [cljs.core.async :as async]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.dom]
            [cljs-bean.core :refer [bean]]
            [goog.string :as gstring]
            [ajax.core :refer [GET]]
            [codegouvfr.i18n :as i]
            [clojure.string :as s]
            [clojure.walk :as walk]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [goog.labs.format.csv :as csv]
            ))

(defonce repos-per-page 50) ;; FIXME: Make customizable?
(defonce orgas-per-page 50) ;; FIXME: Make customizable?
(defonce deps-per-page 50) ;; FIXME: Make customizable?
(defonce timeout 100)
(defonce init-filter {:q nil :g nil :d nil :repo nil :orga nil :language nil :license nil :platform "all"})
(defonce annuaire-prefix "https://lannuaire.service-public.fr/")
(defonce repos-csv-url "https://www.data.gouv.fr/fr/datasets/r/54a38a62-411f-4ea7-9631-ae78d1cef34c")
(defonce orgas-csv-url "https://www.data.gouv.fr/fr/datasets/r/79f8975b-a747-445c-85d0-2cf707e12200")
(defonce platforms-csv-url "https://raw.githubusercontent.com/etalab/data-codes-sources-fr/master/platforms.csv")
;; (defonce platforms-csv-url "https://git.sr.ht/~etalab/codegouvfr-fetch-data/blob/master/platforms.csv")
(defonce stats-url "https://api-code.etalab.gouv.fr/api/stats/general")
(defonce filter-chan (async/chan 100))
(defonce display-filter-chan (async/chan 100))

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
 :update-platforms!
 (fn [db [_ platforms]] (assoc db :platforms platforms)))

(re-frame/reg-sub
 :platforms?
 (fn [db _] (:platforms db)))

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

(defn or-kwds [m ks]
  (first (remove nil? (map #(apply % [m]) ks))))

(defn fa [s]
  [:span.icon
   [:i {:class (str "fas " s)}]])

(defn fab [s]
  [:span.icon
   [:i {:class (str "fab " s)}]])

(defn to-locale-date [s]
  (when (string? s)
    (.toLocaleDateString
     (js/Date. (.parse js/Date s)))))

(defn s-includes? [s sub]
  (when (and (string? s) (string? sub))
    (s/includes? (s/lower-case s) (s/lower-case sub))))

(defn- get-first-match-s-for-k-in-m [s k m]
  (reduce #(when (= (k %2) s) (reduced %2)) nil m))

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
        de       (:has-description f)
        fk       (:is-fork f)
        ar       (:is-archive f)
        li       (:is-licensed f)]
    (filter
     #(and (if dp (contains?
                   (into #{}
                         (:r (get-first-match-s-for-k-in-m
                              dp :n
                              deps-raw)))
                   (:r %)) true)
           (if e (:e %) true)
           (if fk (:f? %) true)
           (if ar (not (:a? %)) true)
           (if li (let [l (:li %)] (and l (not= l "Other"))) true)
           (if lic (s-includes? (:li %) lic) true)
           (if la
             (some (into #{} (list (s/lower-case (or (:l %) ""))))
                   (s/split (s/lower-case la) #" +"))
             true)
           (if (= pl "all") true (s-includes? (:r %) pl))
           (if de (seq (:d %)) true)
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
     #(and (if s (s-includes?
                  (s/join " " [(:n %) (:t %) (:d %)]) s)
               true))
     m)))

(defn apply-orgas-filters [m]
  (let [f  @(re-frame/subscribe [:filter?])
        s  (:q f)
        de (:has-description f)]
    (filter
     #(and (if de (seq (:d %)) true)
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
         ;; favs   (get-item :favs)
         repos  (case @(re-frame/subscribe [:sort-repos-by?])
                  :name   (reverse (sort-by :n repos0))
                  :forks  (sort-by :f repos0)
                  :stars  (sort-by :s repos0)
                  :issues (sort-by :i repos0)
                  :reused (sort-by :g repos0)
                  ;; :favs   (concat (filter #(not-any? #{(:n %)} favs) repos0)
                  ;;                 (filter #(some #{(:n %)} favs) repos0))
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
                 :date  (sort #(compare (js/Date. (.parse js/Date (:c %1)))
                                        (js/Date. (.parse js/Date (:c %2))))
                              orgs)
                 :name  (reverse (sort #(compare (or-kwds %1 [:n :l])
                                                 (or-kwds %2 [:n :l]))
                                       orgs))
                 orgs)]
     (apply-orgas-filters
      (if @(re-frame/subscribe [:reverse-sort?])
        orgas
        (reverse orgas))))))

(defn change-page [type next]
  (let [sub         (condp = type
                      :repos :repos-page?
                      :deps  :deps-page?
                      :orgas :orgas-page?)
        evt         (condp = type
                      :repos :repos-page!
                      :deps  :deps-page!
                      :orgas :orgas-page!)
        cnt         (condp = type
                      :repos :repos?
                      :deps  :deps?
                      :orgas :orgas?)
        repos-page  @(re-frame/subscribe [sub])
        count-pages (count (partition-all
                            repos-per-page @(re-frame/subscribe [cnt])))]
    (cond
      (= next "first")
      (re-frame/dispatch [evt 0])
      (= next "last")
      (re-frame/dispatch [evt (dec count-pages)])
      (and (< repos-page (dec count-pages)) next)
      (re-frame/dispatch [evt (inc repos-page)])
      (and (pos? repos-page) (not next))
      (re-frame/dispatch [evt (dec repos-page)]))))

(defn repos-table [lang repos-cnt]
  (if (zero? repos-cnt)
    [:div.fr-m-3w [:p (i/i lang [:no-repo-found])] [:br]]
    (let [rep-f      @(re-frame/subscribe [:sort-repos-by?])
          repos-page @(re-frame/subscribe [:repos-page?])
          repos      @(re-frame/subscribe [:repos?])]
      [:div.fr-grid-row
       [:table.fr-table.fr-table--bordered.fr-table--layout-fixed.fr-col
        [:thead.fr-grid.fr-col-12
         [:tr
          [:th.fr-col-2
           [:a.fr-link
            {:class    (when (= rep-f :name) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-repos-alpha])
             :href     "#"
             :on-click #(re-frame/dispatch [:sort-repos-by! :name])}
            (i/i lang [:orga-repo])]]
          [:th.fr-col-1
           [:a.fr-link {:title (i/i lang [:swh-link])} (i/i lang [:archive])]]
          [:th.fr-col-3
           [:a.fr-link
            {:class    (when (= rep-f :desc) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :href     "#"
             :title    (i/i lang [:sort-description-length])
             :on-click #(re-frame/dispatch [:sort-repos-by! :desc])}
            (i/i lang [:description])]]
          [:th.fr-col-1
           [:a.fr-link
            {:class    (when (= rep-f :date) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-update-date])
             :href     "#"
             :on-click #(re-frame/dispatch [:sort-repos-by! :date])}
            (i/i lang [:update-short])]]
          [:th.fr-col-1
           [:a.fr-link
            {:class    (when (= rep-f :forks) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-forks])
             :href     "#"
             :on-click #(re-frame/dispatch [:sort-repos-by! :forks])}
            (i/i lang [:forks])]]
          [:th.fr-col-1
           [:a.fr-link
            {:class    (when (= rep-f :stars) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-stars])
             :href     "#"
             :on-click #(re-frame/dispatch [:sort-repos-by! :stars])}
            (i/i lang [:stars])]]
          [:th.fr-col-1
           [:a.fr-link
            {:class    (when (= rep-f :issues) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-issues])
             :href     "#"
             :on-click #(re-frame/dispatch [:sort-repos-by! :issues])}
            (i/i lang [:issues])]]
          [:th.fr-col-1
           [:a.fr-link
            {:class    (when (= rep-f :reused) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :title    (i/i lang [:sort-reused])
             :href     "#"
             :on-click #(re-frame/dispatch [:sort-repos-by! :reused])}
            (i/i lang [:reused])]]]]
        (into [:tbody]
              (for [dd (take repos-per-page
                             (drop (* repos-per-page repos-page) repos))]
                ^{:key dd}
                (let [{:keys [a? d f i li n o r s u dp g]}
                      dd
                      group (subs r 0 (- (count r) (inc (count n))))]
                  [:tr
                   ;; Repo < orga
                   [:td [:div
                         [:a {:href   r
                              :target "new"
                              :title  (str (i/i lang [:go-to-repo])
                                           (when li (str (i/i lang [:under-license]) li)))}
                          n]
                         " ("
                         [:a {:href  (rfe/href :repos {:lang lang} {:g group})
                              :title (i/i lang [:browse-repos-orga])}
                          o]
                         ")"]]
                   ;; SWH link
                   [:td
                    [:a.fr-link
                     {:href   (str "https://archive.softwareheritage.org/browse/origin/" r)
                      :title  (i/i lang [:swh-link])
                      :target "new"}
                     [:img {:width "18px" :src "/images/swh-logo.png"}]]]
                   ;; Description
                   [:td {:class (when a? "has-text-grey")
                         :title (when a? (i/i lang [:repo-archived]))}
                    [:span
                     (when dp
                       [:span
                        [:a.fr-link
                         {:title (i/i lang [:Deps])
                          :href  (rfe/href :deps {:lang lang} {:repo r})}
                         (fa "fa-cubes")]
                        " "])
                     d]]
                   ;; Update
                   [:td (or (to-locale-date u) "N/A")]
                   ;; Forks
                   [:td f]
                   ;; Stars
                   [:td s]
                   ;; Issues
                   [:td i]
                   ;; Reused
                   [:td
                    [:a {:title  (i/i lang [:reuses-expand])
                         :target "new"
                         :href   (str r "/network/dependents")}
                     g]]])))]])))

(defn navigate-pagination [type first-disabled last-disabled]
  [:div.fr-grid-row.fr-grid-row--center
   [:nav.fr-pagination {:role "navigation" :aria-label "Pagination"}
    [:ul.fr-pagination__list
     [:li.fr-pagination__link.fr-pagination__link--first
      [:a
       {:on-click #(change-page type "first")
        :disabled first-disabled}
       (fa "fa-fast-backward")]]
     [:li.fr-pagination__link.fr-pagination__link--prev
      [:a
       {:on-click #(change-page type nil)
        :disabled first-disabled}
       (fa "fa-step-backward")]]
     [:li.fr-pagination__link.fr-pagination__link--next
      [:a
       {:on-click #(change-page type true)
        :disabled last-disabled}
       (fa "fa-step-forward")]]
     [:li.fr-pagination__link.fr-pagination__link--last
      [:a
       {:on-click #(change-page type "last")
        :disabled last-disabled}
       (fa "fa-fast-forward")]]]]])

(defn repos-page [lang license language platform]
  (let [repos          @(re-frame/subscribe [:repos?])
        filter?        @(re-frame/subscribe [:filter?])
        repos-pages    @(re-frame/subscribe [:repos-page?])
        count-pages    (count (partition-all repos-per-page repos))
        first-disabled (zero? repos-pages)
        last-disabled  (= repos-pages (dec count-pages))]
    [:div.fr-grid
     [:div.fr-grid-row
      [:a.fr-link
       {:title (i/i lang [:download])
        :href  (->>
                (map (fn [[k v :as kv]]
                       (when (not-empty kv)
                         (str (name k) "=" v)))
                     filter?)
                (s/join "&")
                (str "/repos-csv?"))}
       [:span.fr-fi-download-line {:aria-hidden true}]
       ;; <span class="fr-fi-download-line" aria-hidden="true"></span>
       ;; (fa "fa-download")
       ]
      ;; [:div.dropdown
      ;;  [:div.dropdown-trigger
      ;;   [:button.button {:aria-haspopup true :aria-controls "dropdown-menu3"}
      ;;    [:span (i/i lang [:options])] (fa "fa-angle-down")]]
      ;;  [:div.dropdown-menu {:role "menu" :id "dropdown-menu3"}
      ;;   [:div.dropdown-content
      ;;    [:div.dropdown-item
      ;;     [:label
      ;;      [:input.fr-input
      ;;       {:type      "checkbox"
      ;;        :checked   (get-item :is-fork)
      ;;        :on-change #(let [v (.-checked (.-target %))]
      ;;                      (set-item! :is-fork v)
      ;;                      (re-frame/dispatch [:filter! {:is-fork v}]))}]
      ;;      (i/i lang [:only-forks])]]
      ;;    [:div.dropdown-item
      ;;     [:label.checkbox.level {:title (i/i lang [:no-archived-repos])}
      ;;      [:input.fr-input
      ;;       {:type      "checkbox"
      ;;        :checked   (get-item :is-archive)
      ;;        :on-change #(let [v (.-checked (.-target %))]
      ;;                      (set-item! :is-archive v)
      ;;                      (re-frame/dispatch [:filter! {:is-archive v}]))}]
      ;;      (i/i lang [:no-archives])]]
      ;;    [:div.dropdown-item
      ;;     [:label.checkbox.level {:title (i/i lang [:only-with-description-repos])}
      ;;      [:input.fr-input
      ;;       {:type      "checkbox"
      ;;        :checked   (get-item :has-description)
      ;;        :on-change #(let [v (.-checked (.-target %))]
      ;;                      (set-item! :has-description v)
      ;;                      (re-frame/dispatch [:filter! {:has-description v}]))}]
      ;;      (i/i lang [:with-description])]]
      ;;    [:div.dropdown-item
      ;;     [:label.checkbox.level {:title (i/i lang [:only-with-license])}
      ;;      [:input.fr-input
      ;;       {:type      "checkbox"
      ;;        :checked   (get-item :is-licensed)
      ;;        :on-change #(let [v (.-checked (.-target %))]
      ;;                      (set-item! :is-licensed v)
      ;;                      (re-frame/dispatch [:filter! {:is-licensed v}]))}]
      ;;      (i/i lang [:with-license])]]
      ;;    [:div.dropdown-item
      ;;     [:label.checkbox.level
      ;;      [:input.fr-input
      ;;       {:type      "checkbox"
      ;;        :checked   (get-item :is-esr)
      ;;        :on-change #(let [v (.-checked (.-target %))]
      ;;                      (set-item! :is-esr v)
      ;;                      (re-frame/dispatch [:filter! {:is-esr v}]))}]
      ;;      (i/i lang [:only-her])]]]]]
      [:input.fr-input.fr-col-2.fr-m-1w
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
      [:input.fr-input.fr-col-2.fr-m-1w
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
      [:select.fr-select.fr-col-2.fr-m-1w
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
      [:button.fr-btn.fr-btn--secondary.fr-col-2.fr-m-1w
       {:disabled true}
       (let [rps (count repos)]
         (if (< rps 2)
           (str rps (i/i lang [:repo]))
           (str rps (i/i lang [:repos]))))]]

     [repos-table lang (count repos)]
     [navigate-pagination :repos first-disabled last-disabled]]))

(defn organizations-page [lang]
  (let [org-f          @(re-frame/subscribe [:sort-orgas-by?])
        orgas          @(re-frame/subscribe [:orgas?])
        filter?        @(re-frame/subscribe [:filter?])
        orgs-cnt       (count orgas)
        orgas-pages    @(re-frame/subscribe [:orgas-page?])
        count-pages    (count (partition-all orgas-per-page orgas))
        first-disabled (zero? orgas-pages)
        last-disabled  (= orgas-pages (dec count-pages))]
    [:div.fr-grid
     [:div.fr-grid-row
      [:a.fr-link.fr-m-1w
       {:title (i/i lang [:download])
        :href  (->>
                (map (fn [[k v :as kv]]
                       (when (not-empty kv)
                         (str (name k) "=" v)))
                     filter?)
                (s/join "&")
                (str "/orgas-csv?"))}
       (fa "fa-download")]
      [:a.fr-link.fr-col-3.fr-m-1w
       {:class    (if (= org-f :name) "fr-fi-checkbox-circle-line fr-link--icon-left")
        :href     "#"
        :title    (i/i lang [:sort-orgas-alpha])
        :on-click #(re-frame/dispatch [:sort-orgas-by! :name])}
       (i/i lang [:sort-alpha])]
      [:a.fr-link.fr-col-3.fr-m-1w
       {:class    (if (= org-f :repos) "fr-fi-checkbox-circle-line fr-link--icon-left")
        :title    (i/i lang [:sort-repos])
        :href     "#"
        :on-click #(re-frame/dispatch [:sort-orgas-by! :repos])}
       (i/i lang [:sort-repos])]
      [:a.fr-link.fr-col-3.fr-m-1w
       {:class    (if (= org-f :date) "fr-fi-checkbox-circle-line fr-link--icon-left")
        :title    (i/i lang [:sort-orgas-creation])
        :href     "#"
        :on-click #(re-frame/dispatch [:sort-orgas-by! :date])}
       (i/i lang [:sort-creation])]
      [:button.fr-btn.fr-btn--secondary
       {:disabled true}
       (let [orgs (count orgas)]
         (if (< orgs 2)
           (str orgs (i/i lang [:one-group]))
           (str orgs (i/i lang [:groups]))))]]

     [:br]
     (into
      [:div]
      (if (zero? orgs-cnt)
        [[:p (i/i lang [:no-orga-found])] [:br]] ;; FIXME: Why [[ ?
        (for [dd (partition-all
                  2
                  (take orgas-per-page
                        (drop (* orgas-per-page @(re-frame/subscribe [:orgas-page?]))
                              @(re-frame/subscribe [:orgas?]))))]
          ^{:key dd}
          [:div.fr-grid-row.fr-m-4w
           (for [{:keys [n l o h c d r e au p an dp fp] :as oo} dd]
             ^{:key oo}
             [:div.fr-col-6
              [:div.fr-grid-row.fr-mb-2w
               (when au [:img.fr-col-2.fr-responsive-img {:src au}])
               [:a.fr-link.fr-col-4.fr-m-2w
                {:target "new"
                 :title  (i/i lang [:go-to-orga])
                 :href   o}
                (or n l)]
               [:div.fr-col-5.fr-m-auto
                (when dp
                  [:a.fr-link
                   {:title (i/i lang [:Deps])
                    :href  (rfe/href :deps {:lang lang} {:orga o})}
                   (fa "fa-cubes")])
                (when e [:a.fr-link
                         {:title (i/i lang [:contact-by-email])
                          :href  (str "mailto:" e)}
                         (fa "fa-envelope")])
                (when h [:a.fr-link
                         {:title  (i/i lang [:go-to-website])
                          :target "new"
                          :href   h} (fa "fa-globe")])
                (when an [:a.fr-link
                          {:title  (i/i lang [:go-to-sig-website])
                           :target "new"
                           :href   (str annuaire-prefix an)}
                          (fa "fa-link")])
                (when an [:a.fr-link
                          {:title  (i/i lang [:go-to-sig-website])
                           :target "new"
                           :href   (str annuaire-prefix an)}
                          (fa "fa-link")])]]
              [:div.fr-grid-row [:p d]]])])))

     [navigate-pagination :orgas first-disabled last-disabled]]))

(defn deps-table [lang deps repo orga]
  (let [dep-f     @(re-frame/subscribe [:sort-deps-by?])
        deps-page @(re-frame/subscribe [:deps-page?])]
    [:div.fr-grid-row
     [:table.fr-table.fr-table--bordered.fr-table--layout-fixed.fr-col
      [:thead.fr-grid.fr-col-12
       [:tr
        [:th.fr-col-3
         [:a.fr-link
          {:class    (when (= dep-f :name) "fr-fi-checkbox-circle-line fr-link--icon-left")
           :href     "#"
           :on-click #(re-frame/dispatch [:sort-deps-by! :name])}
          (i/i lang [:name])]]
        [:th.fr-col-1
         [:a.fr-link
          {:class    (when (= dep-f :type) "fr-fi-checkbox-circle-line fr-link--icon-left")
           :href     "#"
           :on-click #(re-frame/dispatch [:sort-deps-by! :type])}
          (i/i lang [:type])]]
        [:th.fr-col-5
         [:a.fr-link
          {:class    (when (= dep-f :description) "fr-fi-checkbox-circle-line fr-link--icon-left")
           :href     "#"
           :on-click #(re-frame/dispatch [:sort-deps-by! :description])}
          (i/i lang [:description])]]
        (when-not repo
          [:th.fr-col-1
           [:a.fr-link
            {:class    (when (= dep-f :repos) "fr-fi-checkbox-circle-line fr-link--icon-left")
             :href     "#"
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
             [:a {:href  l :target "new"
                  :title (i/i lang [:more-info])} n]]
            [:td t]
            [:td d]
            (when-not repo
              [:td
               [:a {:title (i/i lang [:list-repos-depending-on-dep])
                    :href  (rfe/href :repos {:lang lang}
                                     (if orga {:d n :g orga} {:d n}))}
                (if-not orga
                  (count r)
                  (count (filter #(re-find (re-pattern orga) %) r)))]])])))]]))

(defn deps-page [lang repos-sim]
  (let [{:keys [repo orga]} @(re-frame/subscribe [:filter?])
        deps0               @(re-frame/subscribe [:deps?])
        deps                (if-let [s (or repo orga)]
                              (filter #(s-includes? (s/join " " (:r %)) s) deps0)
                              deps0)
        deps-pages          @(re-frame/subscribe [:deps-page?])
        count-pages         (count (partition-all deps-per-page deps))
        first-disabled      (zero? deps-pages)
        last-disabled       (= deps-pages (dec count-pages))
        dep-f               @(re-frame/subscribe [:sort-deps-by?])]
    [:div.fr-grid
     [:div.fr-grid-row
      [:button.fr-btn.fr-btn--secondary.fr-btn--secondary.fr-m-1w
       {:disabled true}
       (let [deps (count deps)]
         (if (< deps 2)
           (str deps (i/i lang [:dep]))
           (str deps (i/i lang [:deps]))))]]
     (if (pos? (count deps))
       [deps-table lang deps repo orga]
       [:div.fr-m-3w [:p (i/i lang [:no-dep-found])]])
     [navigate-pagination :deps first-disabled last-disabled]
     (when-let [sims (get repos-sim repo)]
       [:div
        [:h2 (i/i lang [:Repos-deps-sim])]
        [:br]
        [:ul
         (for [s sims]
           ^{:key s}
           [:li [:a {:href (rfe/href :deps {:lang lang} {:repo s})} s]])]
        [:br]])]))

(defn repos-page-class [lang license language platform]
  (reagent/create-class
   {:display-name   "repos-page-class"
    :component-did-mount
    (fn []
      (GET "/repos.json"
           :handler
           #(re-frame/dispatch
             [:update-repos! (map (comp bean clj->js) %)])))
    :reagent-render (fn [] (repos-page lang license language platform))}))

(defn stats-card [heading data & [thead]]
  [:div.fr-m-3w
   [:h3 heading]
   [:table.fr-table.fr-table--bordered.fr-table--layout-fixed.fr-col-12
    thead
    [:tbody
     (for [o (reverse (walk/stringify-keys (sort-by val data)))]
       ^{:key (key o)}
       [:tr [:td (key o)] [:td (val o)]])]]])

(defn deps-card [heading deps lang]
  [:div.fr-m-3w
   [:h3
    heading
    ;; [:a.fr-link.fr-link--icon-right.fr-fi-question-line
    ;;  {:href  (str "/" lang "/glossary#dependencies")
    ;;   :title (i/i lang [:go-to-glossary])}]
    ]
   [:table.fr-table.fr-table--bordered.fr-table--layout-fixed.fr-col-12
    [:thead [:tr
             [:th (i/i lang [:name])]
             [:th (i/i lang [:type])]
             [:th (i/i lang [:description])]
             [:th (i/i lang [:Repos])]]]
    [:tbody
     (for [{:keys [n t d l r] :as o} deps]
       ^{:key o}
       [:tr
        [:td [:a {:href  l :target "new"
                  :title (i/i lang [:more-info])} n]]
        [:td t]
        [:td d]
        [:td
         [:a {:title (i/i lang [:list-repos-depending-on-dep])
              :href  (rfe/href :repos {:lang lang} {:d n})}
          (count r)]]])]]])

(defn top-clean-up [top lang param title]
  (let [total (reduce + (map val top))]
    (apply merge
           (sequence
            (comp
             (filter (fn [[k _]] (not= k "Inconnu")))
             (map (fn [[k v]]
                    [k (js/parseFloat
                        (gstring/format "%.2f" (* (/ v total) 100)))]))
             (map #(let [[k v] %]
                     {[:a {:title title
                           :href  (str "/" lang "/repos?" param "=" k)} k] v})))
            top))))

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
        top_languages_1
        (top-clean-up (walk/stringify-keys top_languages)
                      lang "language" (i/i lang [:list-repos-with-language]))
        top_licenses_0
        (take 10 (top-clean-up
                  (walk/stringify-keys
                   (-> top_licenses
                       (dissoc :Inconnue)
                       (dissoc :Other)))
                  lang "license"
                  (i/i lang [:list-repos-using-license])))]
    [:div
     [:div.fr-grid-row.fr-grid-row--center
      [:div.fr-tile.fr-tile--horizontal.fr-m-1w
       [:div.fr-tile__body
        [:h1.fr-tile__title (i/i lang [:repos-of-source-code])]
        [:p.fr-tile__desc nb_repos]]]

      [:div.fr-tile.fr-tile--horizontal.fr-col-2.fr-m-1w
       [:div.fr-tile__body
        [:h1.fr-tile__title (i/i lang [:orgas-or-groups])]
        [:div.fr-tile__desc
         [:h3 nb_orgs]]]]

      [:div.fr-tile.fr-tile--horizontal.fr-col-2.fr-m-1w
       [:div.fr-tile__body
        [:h1.fr-tile__title (i/i lang [:mean-repos-by-orga])]
        [:div.fr-tile__desc
         [:h3 avg_nb_repos]]]]
      
      [:div.fr-tile.fr-tile--horizontal.fr-col-2.fr-m-1w
       [:div.fr-tile__body
        [:h1.fr-tile__title (i/i lang [:median-repos-by-orga])]
        [:div.fr-tile__desc
         [:h3 median_nb_repos]]]]
      
      [:div.fr-tile.fr-tile--horizontal.fr-col-2.fr-m-1w
       [:div.fr-tile__body
        [:h1.fr-tile__title (i/i lang [:deps-stats])]
        [:div.fr-tile__desc
         [:h3
          (:deps-total deps-total)]]]]]

     [:div.fr-grid-row
      [:div.fr-col-6
       (stats-card [:span (i/i lang [:most-used-languages])]
                   top_languages_1
                   [:thead [:tr [:th (i/i lang [:language])] [:th "%"]]])]
      [:div.fr-col-6
       (stats-card [:span (i/i lang [:most-used-identified-licenses])]
                   top_licenses_0
                   [:thead [:tr [:th (i/i lang [:license])] [:th "%"]]])]]

     [:div.fr-grid-row
      [:div.fr-col-6
       (stats-card [:span
                    (i/i lang [:orgas-or-groups])
                    " "
                    (i/i lang [:with-more-of])
                    (i/i lang [:repos])]
                   top_orgs_by_repos_0)]
      [:div.fr-col-6
       (stats-card [:span
                    (i/i lang [:orgas-with-more-stars])]
                   top_orgs_by_stars)]]
     
     [:div.fr-grid-row
      [:div.fr-col-6
       (deps-card (i/i lang [:Deps]) deps lang)]
      [:div.fr-col-6
       (stats-card (i/i lang [:distribution-by-platform]) platforms)
       [:img {:src "/top_licenses.svg" :width "100%"}]]]
     
     ;; [:div
     ;;  (stats-card [:span (i/i lang [:archive-on])
     ;;               "Software Heritage"
     ;;               ;; [:a.fr-link.fr-link--icon-right.fr-fi-question-line
     ;;               ;;  {:href  (str "/" lang "/glossary#software-heritage")
     ;;               ;;   :title (i/i lang [:go-to-glossary])}]
     ;;               ]
     ;;              {(i/i lang [:repos-on-swh])
     ;;               (:repos_in_archive software_heritage)
     ;;               (i/i lang [:percent-of-repos-archived])
     ;;               (:ratio_in_archive software_heritage)})]
     ]))

(defn deps-page-class [lang]
  (let [deps-repos-sim (reagent/atom nil)]
    (reagent/create-class
     {:display-name   "deps-page-class"
      :component-did-mount
      (fn []
        (GET "/deps-repos-sim.json"
             :handler #(reset! deps-repos-sim %)))
      :reagent-render (fn [] (deps-page lang @deps-repos-sim))})))

(defn stats-page-class [lang]
  (let [deps       (reagent/atom nil)
        stats      (reagent/atom nil)
        deps-total (reagent/atom nil)]
    (reagent/create-class
     {:display-name   "stats-page-class"
      :component-did-mount
      (fn []
        (GET "/deps-total.json"
             :handler #(reset! deps-total (walk/keywordize-keys %)))
        (GET "/deps-top.json"
             :handler #(reset! deps (take 10 (map (comp bean clj->js) %))))
        (GET stats-url
             :handler #(reset! stats (walk/keywordize-keys %))))
      :reagent-render (fn [] (stats-page lang @stats @deps @deps-total))})))

(defn close-filter-button [lang ff t reinit]
  [:span
   [:a.fr-link.fr-fi-close-circle-line.fr-link--icon-right
    {:title (i/i lang [:remove-filter])
     :href  (rfe/href t {:lang lang} (filter #(not-empty (val %)) reinit))}
    [:span ff]]])

(defn main-menu [q lang view]
  [:div
   [:div.fr-grid-row.fr-mt-2w
    [:div.fr-col-12
     (when (or (= view :repos) (= view :orgas) (= view :deps))
       [:input.fr-input
        {:placeholder (i/i lang [:free-search])
         :value       (or @q (:q @(re-frame/subscribe [:display-filter?])))
         :on-change   (fn [e]
                        (let [ev (.-value (.-target e))]
                          (reset! q ev)
                          (async/go
                            (async/>! display-filter-chan {:q ev})
                            (async/<! (async/timeout timeout))
                            (async/>! filter-chan {:q ev}))))}])]
    (when-let [flt (not-empty  @(re-frame/subscribe [:filter?]))]
      [:div.fr-col-4.fr-grid-row.fr-m-1w
       (when-let [ff (not-empty (:g flt))]
         (close-filter-button lang ff :repos (merge flt {:g nil})))
       (when-let [ff (not-empty (:d flt))]
         (close-filter-button lang ff :repos (merge flt {:d nil})))
       (when-let [ff (not-empty (:orga flt))]
         (close-filter-button lang ff :deps (merge flt {:orga nil})))
       (when-let [ff (not-empty (:repo flt))]
         (close-filter-button lang ff :deps (merge flt {:repo nil})))])]])

(defn main-page [q license language platform]
  (let [lang @(re-frame/subscribe [:lang?])
        view @(re-frame/subscribe [:view?])]
    [:div.fr-container.fr-container--fluid
     [main-menu q lang view]
     (condp = view
       :home-redirect
       (if (contains? i/supported-languages lang)
         (do (set! (.-location js/window) (str "/" lang "/repos")) "")
         (do (set! (.-location js/window) (str "/en/repos")) ""))
       ;; Table to display organizations
       :orgas [organizations-page lang]
       ;; Table to display repositories
       :repos [repos-page-class lang license language platform]
       ;; Table to display statistics
       :stats [stats-page-class lang]
       ;; Table to display all dependencies
       :deps  [deps-page-class lang]
       ;; :live      [live lang]
       ;; Fall back on the organizations page
       :else  (rfe/push-state :orgas {:lang lang}))]))

(defn main-class []
  (let [q        (reagent/atom nil)
        license  (reagent/atom nil)
        language (reagent/atom nil)
        platform (reagent/atom nil)]
    (reagent/create-class
     {:display-name   "main-class"
      :component-did-mount
      (fn []
        (GET platforms-csv-url
             :handler
             #(re-frame/dispatch
               [:update-platforms! (map first (next (js->clj (csv/parse %))))]))
        (GET "/deps.json"
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
        (GET "/orgas.json"
             :handler
             #(re-frame/dispatch
               [:update-orgas! (map (comp bean clj->js) %)])))
      :reagent-render (fn [] (main-page q license language platform))})))

(defn on-navigate [match]
  (let [lang (:lang (:path-params match))]
    (when (string? lang) (re-frame/dispatch [:lang! lang]))
    (re-frame/dispatch [:path-params! (:path-params match)])
    (re-frame/dispatch [:view!
                        (keyword (:name (:data match)))
                        (:query-params match)])))

(defonce routes
  [["/" :home-redirect]
   ["/:lang"
    ["/repos" :repos]
    ["/groups" :orgas]
    ["/stats" :stats]
    ["/deps" :deps]
    ]])

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
  (reagent.dom/render
   [main-class]
   (.getElementById js/document "app"))
  )
