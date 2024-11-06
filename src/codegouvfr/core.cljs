;; Copyright (c) 2019-2024 DINUM, Bastien Guerry <bastien.guerry@code.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.core
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.dom.client :as rdc]
            [cljs-bean.core :refer [bean]]
            [goog.string :as gstring]
            [codegouvfr.i18n :as i]
            [clojure.string :as s]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [goog.labs.format.csv :as csv]
            [semantic-csv.core :as sc]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [cljsjs.highcharts])
  (:require-macros [codegouvfr.macros :refer [inline-page]]))

;; Set defaults

(def ^:const UNIX-EPOCH "1970-01-01T00:00:00Z")
(def ^:const TIMEOUT 200)
(def ^:const REPOS-PER-PAGE 100)
(def ^:const ORGAS-PER-PAGE 20)
(def ^:const ecosystem-prefix-url "https://data.code.gouv.fr/api/v1/hosts/")
(def ^:const swh-baseurl "https://archive.softwareheritage.org/browse/origin/")

(def q (reagent/atom nil))
(def license (reagent/atom nil))
(def lang (reagent/atom nil))
(def language (reagent/atom nil))

(defonce init-filter
  {:q                 nil
   :group             nil
   :license           nil
   :language          nil
   :forge             nil
   :fork              false
   :floss             false
   :template          false
   :with-publiccode   false
   :with-contributing false
   :ministry          nil})

;; Mappings used when exporting displayed data to csv files

(defonce mappings
  {:repos {:d  :description
           :f  :forks_count
           :f? :is_fork
           :l  :language
           :li :license
           :n  :name
           :o  :organization_url
           :s  :subscribers_count
           :t? :is_template
           :u  :last_update}
   :orgas {:au :avatar_url
           :d  :description
           :ps :public_sector_organization
           :id :organization_url
           :m  :ministry
           :n  :name
           :r  :repositories_count
           :s  :subscribers_count}})

;; Utility functions

(defn debounce [f]
  (let [timeout (atom nil)]
    (fn [& args]
      (when @timeout (js/clearTimeout @timeout))
      (reset! timeout (js/setTimeout #(apply f args) TIMEOUT)))))

(defn new-tab [s]
  (str s " - " (i/i @lang :new-tab)))

(defn to-locale-date [^String s]
  (when (string? s)
    (.toLocaleDateString (js/Date. (.parse js/Date s)) @lang)))

(defn todays-date []
  (s/replace (.toLocaleDateString (js/Date.) @lang) "/" "-"))

(defn or-kwds [m ks]
  (first (remove nil? (map #(apply % [m]) ks))))

(defn s-includes? [^String s ^String sub]
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
                  (s/replace #"(?i)[ûùu]" "[ûùu]")
                  s/lower-case
                  re-pattern)]
      (re-find sub (s/lower-case s)))))

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
        link        (doto (js/document.createElement "a")
                      (-> .-href (set! (js/URL.createObjectURL data-blob)))
                      (.setAttribute "download" file-name))]
    (-> js/document .-body (.appendChild link))
    (.click link)
    (-> js/document .-body (.removeChild link))))

(defn reset-queries []
  (reset! q nil)
  (reset! license nil)
  (reset! language nil)
  (re-frame/dispatch [:reset-filter!]))

(defn top-clean-up-orgas [data param]
  (sequence
   (map #(let [[[n l] v] %]
           [[:a.fr-raw-link.fr-link
             {:title      (i/i @lang :go-to-repos)
              :aria-label (i/i @lang :go-to-repos)
              :href       (rfe/href :repos nil {param l})} n] v]))
   data))

(defn html-url-from-p-and-fn [p fn]
  (str "https://" p "/" fn))

(defn- table-header [what k]
  [:strong.fr-m-auto
   (let [cnt (count what)]
     (if (< cnt 2)
       (str cnt (i/i @lang k))
       (str cnt (i/i @lang (keyword (str (name k) "s"))))))])

(defn repo-orga-data-url [p n t]
  (let [fmt-string
        (str ecosystem-prefix-url "%s/" (if (= t :repos) "repositories" "owners") "/%s")
        platform (if (re-matches #"(?i)github\.com" p) "github" (or p ""))]
    (gstring/format fmt-string platform n)))

;; Filters

(defn if-a-b-else-true
  "Not a true and b false."
  [a b]
  (if a b true))

(def memoized-apply-repos-filters
  (memoize
   (fn [repos {:keys [fork with-contributing with-publiccode
                      template floss license language forge group q]}]
     (->> repos
          (sequence
           (filter
            #(and
              (if-a-b-else-true fork (:f? %))
              (if-a-b-else-true with-contributing (:c? %))
              (if-a-b-else-true with-publiccode (:p? %))
              (if-a-b-else-true template (:t? %))
              (if-a-b-else-true floss
                (let [l (:li %)] (and (not-empty l) (not= l "Other"))))
              (if-a-b-else-true license (s-includes? (:li %) license))
              (if (not-empty language)
                (some (into #{} (list (s/lower-case (or (:l %) ""))))
                      (s/split (s/lower-case language) #" +"))
                true)
              (if (empty? forge) true (= (:p %) forge))
              (if-a-b-else-true group (= (:o %) group))
              (if-a-b-else-true q (s-includes? (s/join " " [(:id %) (:d %)]) q)))))))))

(def memoized-apply-orgas-filters
  (memoize
   (fn [orgas q ministry]
     (->>
      orgas
      (sequence
       (filter
        #(and
          (if-a-b-else-true q
            (s-includes?
             (s/join " " [(:id %) (:n %) (:d %) (:ps %) (:os %)])
             q))
          (if (empty? ministry) true (= (:m %) ministry)))))))))

(defn apply-orgas-filters [orgas]
  (let [{:keys [q ministry]} @(re-frame/subscribe [:filter?])]
    (memoized-apply-orgas-filters orgas q ministry)))

(defn not-empty-string-or-true [[_ v]]
  (or (and (boolean? v) (true? v))
      (and (string? v) (not-empty v))))

;; Events

(re-frame/reg-event-db
 :initialize-db!
 (fn [_ _]
   {:repos-page    0
    :orgas-page    0
    :sort-repos-by :score
    :sort-orgas-by :subscribers
    :reverse-sort  false
    :filter        init-filter
    ;; :lang          "en"
    :path          ""}))

;; Define events for each API call

(re-frame/reg-event-fx
 :fetch-repositories
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             "/data/repos_preprod.json"
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:set-repositories]
                 :on-failure      [:api-request-error :repositories]
                 :timeout         30000
                 :retry-count     3
                 :retry-delay     1000}}))

(re-frame/reg-event-fx
 :fetch-owners
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             "/data/owners.json"
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:set-owners]
                 :on-failure      [:api-request-error :owners]
                 :timeout         30000
                 :retry-count     3
                 :retry-delay     1000}}))

(re-frame/reg-event-fx
 :fetch-awesome
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             "/data/awesome.json"
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:set-awesome]
                 :on-failure      [:api-request-error :awesome]
                 :timeout         30000
                 :retry-count     3
                 :retry-delay     1000}}))

(re-frame/reg-event-fx
 :fetch-platforms
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             "/data/codegouvfr-forges.csv"
                 :response-format (ajax/raw-response-format)
                 :on-success      [:set-platforms]
                 :on-failure      [:api-request-error :platforms]
                 :timeout         30000
                 :retry-count     3
                 :retry-delay     1000}}))

(re-frame/reg-event-fx
 :fetch-stats
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             "/data/stats_preprod.json"
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:set-stats]
                 :on-failure      [:api-request-error :stats]
                 :timeout         30000
                 :retry-count     3
                 :retry-delay     1000}}))

(re-frame/reg-event-fx
 :fetch-repo-or-orga-data
 (fn [_ [_ platform name-or-login repo-or-orga]]
   {:http-xhrio {:method          :get
                 :uri             (repo-orga-data-url platform name-or-login repo-or-orga)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:set-repo-or-orga-data]
                 :on-failure      [:api-request-error :current-repo-or-orga-data]
                 :timeout         30000
                 :retry-count     3
                 :retry-delay     1000}}))

;; Define events to handle successful responses

(re-frame/reg-event-db
 :set-repositories
 (fn [db [_ response]]
   (assoc db :repositories (map (comp bean clj->js) response))))

(re-frame/reg-event-db
 :set-repo-or-orga-data
 (fn [db [_ response]]
   (assoc db :current-repo-or-orga-data response)))

(re-frame/reg-event-db
 :set-owners
 (fn [db [_ response]]
   (assoc db :owners (map (comp bean clj->js) response))))

(re-frame/reg-event-db
 :set-awesome
 (fn [db [_ response]]
   (assoc db :awesome response)))

(re-frame/reg-event-db
 :set-platforms
 (fn [db [_ response]]
   (assoc db :platforms (map first (next (js->clj (csv/parse response)))))))

(re-frame/reg-event-db
 :set-stats
 (fn [db [_ response]]
   (assoc db :stats response)))

(re-frame/reg-event-db
 :api-request-error
 (fn [db _]
   ;; [db [_ request-type response]]
   ;; (assoc-in db [:errors request-type] response)
   (assoc db :current-repo-or-orga-data nil)))

;; Define other reframe events

;; (re-frame/reg-event-db
;;  :lang!
;;  (fn [db [_ lang]]
;;    (dom/set-properties
;;     (dom/get-element "html")
;;     {"lang" lang})
;;    (assoc db :lang lang)))

(re-frame/reg-event-db
 :path!
 (fn [db [_ path]]
   (assoc db :path path)))

(re-frame/reg-event-db
 :path-params!
 (fn [db [_ path-params]]
   (assoc db :path-params path-params)))

(re-frame/reg-event-db
 :query-params!
 (fn [db [_ query-params]]
   (assoc db :query-params query-params)))

(re-frame/reg-event-db
 :reset-filter!
 (fn [db [_ _]]
   (update-in db [:filter] init-filter)))

(re-frame/reg-event-db
 :filter!
 (fn [db [_ s]]
   (update-in db [:filter] merge s)))

(re-frame/reg-event-db
 :repos-page!
 (fn [db [_ n]] (assoc db :repos-page n)))

(re-frame/reg-event-db
 :orgas-page!
 (fn [db [_ n]] (assoc db :orgas-page n)))

(re-frame/reg-event-fx
 :view!
 (fn [{:keys [db]} [_ view query-params]]
   {:db         (assoc db :view view)
    :dispatch-n [[:repos-page! 0]
                 [:orgas-page! 0]
                 [:filter! query-params]]}))

(re-frame/reg-event-db
 :reverse-sort!
 (fn [db _] (update-in db [:reverse-sort] not)))

(re-frame/reg-event-fx
 :sort-repos-by!
 (fn [{:keys [db]} [_ k]]
   (let [effects {:db       (assoc db :sort-repos-by k)
                  :dispatch [:repos-page! 0]}]
     (if (= k (:sort-repos-by db))
       (update effects :dispatch-n (fnil conj []) [:reverse-sort!])
       effects))))

(re-frame/reg-event-fx
 :sort-orgas-by!
 (fn [{:keys [db]} [_ k]]
   (let [effects {:db       (assoc db :sort-orgas-by k)
                  :dispatch [:orgas-page! 0]}]
     (if (= k (:sort-orgas-by db))
       (update effects :dispatch-n (fnil conj []) [:reverse-sort!])
       effects))))

(re-frame/reg-event-fx
 :update-filter
 (fn [{:keys [db]} [_ value filter-key]]
   {:db       (assoc-in db [:filter filter-key] value)
    :dispatch [:update-filter-chan {filter-key value}]}))

(re-frame/reg-event-fx
 :update-filter-chan
 (fn [{:keys [db]} [_ filter-update]]
   (rfe/push-state (:view db) nil
                   (->> db :filter
                        (merge filter-update)
                        (filter not-empty-string-or-true)))))

;; Subscriptions

(re-frame/reg-sub
 :ministries?
 (fn [db _] (filter not-empty (distinct (map :m (:owners db))))))

(re-frame/reg-sub
 :path?
 (fn [db _] (:path db)))

(re-frame/reg-sub
 :path-params?
 (fn [db _] (:path-params db)))

(re-frame/reg-sub
 :query-params?
 (fn [db _] (:query-params db)))

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
 :view?
 (fn [db _] (:view db)))

(re-frame/reg-sub
 :reverse-sort?
 (fn [db _] (:reverse-sort db)))

(re-frame/reg-sub
 :stats?
 (fn [db _] (:stats db)))

(re-frame/reg-sub
 :awesome?
 (fn [db _] (:awesome db)))

(re-frame/reg-sub
 :platforms?
 (fn [db _] (:platforms db)))

(re-frame/reg-sub
 :repos?
 (fn [db]
   (let [repos0     (:repositories db)
         filter-map @(re-frame/subscribe [:filter?])
         repos      (case @(re-frame/subscribe [:sort-repos-by?])
                      :forks (sort-by :f repos0)
                      :score (sort-by :a repos0)
                      :date  (sort #(compare (js/Date. (.parse js/Date (:u %1)))
                                             (js/Date. (.parse js/Date (:u %2))))
                                   repos0)
                      repos0)]
     (memoized-apply-repos-filters
      (if @(re-frame/subscribe [:reverse-sort?])
        repos
        (reverse repos))
      filter-map))))

(re-frame/reg-sub
 :orgas?
 #(let [orgs  (:owners %)
        orgas (case @(re-frame/subscribe [:sort-orgas-by?])
                :repos       (sort-by :r orgs)
                :floss       (sort-by :f orgs)
                :subscribers (sort-by :s orgs)
                orgs)]
    (apply-orgas-filters
     (if @(re-frame/subscribe [:reverse-sort?])
       orgas
       (reverse orgas)))))

(re-frame/reg-sub
 :current-repo-or-orga-data?
 (fn [db _] (:current-repo-or-orga-data db)))

;; Pagination

(defn change-page [type next]
  (let [conf        (condp = type
                      :repos {:sub :repos-page? :evt      :repos-page!
                              :cnt :repos?      :per-page REPOS-PER-PAGE}
                      :orgas {:sub :orgas-page? :evt      :orgas-page!
                              :cnt :orgas?      :per-page ORGAS-PER-PAGE})
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
   [:nav.fr-pagination
    {:role       "navigation"
     :aria-label (i/i @lang :pagination)}
    [:ul.fr-pagination__list
     [:li
      [:button.fr-pagination__link.fr-pagination__link--first
       {:on-click      #(change-page type "first")
        :disabled      first-disabled
        :aria-label    (i/i @lang :first-page)
        :aria-disabled first-disabled}]]
     [:li
      [:button.fr-pagination__link.fr-pagination__link--prev
       {:on-click      #(change-page type nil)
        :disabled      first-disabled
        :aria-label    (i/i @lang :previous-page)
        :aria-disabled first-disabled}]]
     [:li
      [:button.fr-pagination__link.fr
       {:disabled     true
        :aria-current "page"
        :aria-label   (i/i @lang :current-page-of-total)}
       (str (inc current-page) "/"
            (if (> total-pages 0) total-pages 1))]]
     [:li
      [:button.fr-pagination__link.fr-pagination__link--next
       {:on-click      #(change-page type true)
        :disabled      last-disabled
        :aria-label    (i/i @lang :next-page)
        :aria-disabled last-disabled}]]
     [:li
      [:button.fr-pagination__link.fr-pagination__link--last
       {:on-click      #(change-page type "last")
        :disabled      last-disabled
        :aria-label    (i/i @lang :last-page)
        :aria-disabled last-disabled}]]]]])

;; Home page

(defn home-page []
  [:div.fr-grid-row.fr-grid-row--center
   [:div.fr-col-6.fr-p-2w
    [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
     [:div.fr-card__body
      [:div.fr-card__title
       [:a.fr-card__link
        {:href  (rfe/href :awesome)
         :title (i/i @lang :Awesome-title)}
        (i/i @lang :Awesome)]]
      [:div.fr-card__desc (i/i @lang :Awesome-callout)]]
     [:div.fr-card__img.fr-col-3
      [:img.fr-responsive-img {:src "./img/awesome.webp" :alt ""}]]]]
   [:div.fr-col-6.fr-p-2w
    [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
     [:div.fr-card__body
      [:div.fr-card__title
       [:a.fr-card__link
        {:href  (rfe/href :repos)
         :title (i/i @lang :repos-of-source-code)}
        (i/i @lang :Repos)]]
      [:div.fr-card__desc (i/i @lang :home-repos-desc)]]
     [:div.fr-card__img.fr-col-3
      [:img.fr-responsive-img {:src "./img/repositories.webp" :alt ""}]]]]
   [:div.fr-col-6.fr-p-2w
    [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
     [:div.fr-card__body
      [:div.fr-card__title
       [:a.fr-card__link
        {:href  (rfe/href :orgas)
         :title (i/i @lang :Orgas)}
        (i/i @lang :Orgas)]]
      [:div.fr-card__desc (i/i @lang :home-orgas-desc)]]
     [:div.fr-card__img.fr-col-3
      [:img.fr-responsive-img {:src "./img/organizations.webp" :alt ""}]]]]
   [:div.fr-col-6.fr-p-2w
    [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
     [:div.fr-card__body
      [:div.fr-card__title
       [:a.fr-card__link
        {:href  (rfe/href :stats)
         :title (i/i @lang :stats-expand)}
        (i/i @lang :Stats)]]
      [:div.fr-card__desc (i/i @lang :home-stats-desc)]]
     [:div.fr-card__img.fr-col-3
      [:img.fr-responsive-img {:src "./img/stats.webp" :alt ""}]]]]])

;; Main structure - repos

(defn repos-table [repos]
  (if (zero? (count repos))
    [:div.fr-m-3w [:p {:aria-live "polite"} (i/i @lang :no-repo-found)]]
    (let [rep-f      @(re-frame/subscribe [:sort-repos-by?])
          repos-page @(re-frame/subscribe [:repos-page?])]
      [:div.fr-table.fr-table--no-caption
       {:role "region" :aria-label (i/i @lang :repos-of-source-code)}
       [:table
        [:caption {:id "repos-table-caption"} (i/i @lang :repos-of-source-code)]
        [:thead.fr-grid.fr-col-12
         [:tr
          [:th.fr-col {:scope "col"} (i/i @lang :Repo)]
          [:th.fr-col {:scope "col"} (i/i @lang :Orga)]
          [:th.fr-col {:scope "col"} (i/i @lang :description)]
          [:th.fr-col-1 {:scope "col"}
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class        (when (= rep-f :date) "fr-btn--secondary")
             :title        (i/i @lang :sort-update-date)
             :on-click     #(re-frame/dispatch [:sort-repos-by! :date])
             :aria-pressed (if (= rep-f :date) "true" "false")
             :aria-label   (i/i @lang :sort-update-date)}
            (i/i @lang :update-short)]]
          [:th.fr-col-1 {:scope "col"}
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class        (when (= rep-f :forks) "fr-btn--secondary")
             :title        (i/i @lang :sort-forks)
             :on-click     #(re-frame/dispatch [:sort-repos-by! :forks])
             :aria-pressed (if (= rep-f :forks) "true" "false")
             :aria-label   (i/i @lang :sort-forks)}
            (i/i @lang :forks)]]
          [:th.fr-col-1 {:scope "col"}
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class        (when (= rep-f :score) "fr-btn--secondary")
             :title        (i/i @lang :sort-score)
             :on-click     #(re-frame/dispatch [:sort-repos-by! :score])
             :aria-pressed (if (= rep-f :score) "true" "false")
             :aria-label   (i/i @lang :sort-score)}
            (i/i @lang :Score)]]]]
        (into [:tbody]
              (for [repo (->> repos
                              (drop (* REPOS-PER-PAGE repos-page))
                              (take REPOS-PER-PAGE))]
                ^{:key (str (:o repo) "/" (:n repo))}
                (let [{:keys [d         ; description
                              f         ; forks_count
                              a         ; codegouvfr "awesome" score
                              a?        ; archived
                              ;; FIXME: useless?
                              ;; li        ; license
                              n         ; name
                              fn        ; full-name
                              o         ; owner
                              u         ; last_update
                              p         ; forge
                              ]} repo
                      html_url   (html-url-from-p-and-fn p fn)]
                  [:tr
                   [:td
                    [:a.fr-raw-link.fr-link
                     {:title      (i/i @lang :go-to-data)
                      :href       (rfe/href :repo-page {:platform p :orga-repo-name fn})
                      :aria-label (str (i/i @lang :go-to-data) " " n)}
                     n]]
                   [:td [:button.fr-raw-link.fr-link
                         {:on-click   #(do (reset-queries) (rfe/push-state :repos nil {:group o}))
                          :aria-label (i/i @lang :browse-repos-orga)}
                         (or (last (re-matches #".+/([^/]+)/?" o)) "")]]
                   [:td [:span {:aria-label (str (i/i @lang :description) ": " d)}
                         (if a? [:em {:title (i/i @lang :repo-archived)} d] d)]]
                   [:td
                    {:style {:text-align "center"}}
                    [:span
                     (if-let [d (to-locale-date u)]
                       [:a
                        {:href       (str swh-baseurl html_url)
                         :target     "new"
                         :title      (new-tab (i/i @lang :swh-link))
                         :rel        "noreferrer noopener"
                         :aria-label (i/i @lang :swh-link)}
                        d]
                       "N/A")]]
                   [:td {:style      {:text-align "center"}
                         :aria-label (str (i/i @lang :forks) ": " f)} f]
                   [:td {:style      {:text-align "center"}
                         :aria-label (str (i/i @lang :Score) ": " a)} a]])))]])))

(defn repos-page []
  (let [repos              @(re-frame/subscribe [:repos?])
        repos-pages        @(re-frame/subscribe [:repos-page?])
        query-params       @(re-frame/subscribe [:query-params?])
        count-pages        (count (partition-all REPOS-PER-PAGE repos))
        f                  @(re-frame/subscribe [:filter?])
        first-disabled     (zero? repos-pages)
        last-disabled      (= repos-pages (dec count-pages))
        mapping            (:repos mappings)
        debounced-license  (debounce #(re-frame/dispatch [:update-filter % :license]))
        debounced-language (debounce #(re-frame/dispatch [:update-filter % :language]))]
    [:div
     [:div.fr-grid-row
      ;; RSS feed
      [:a.fr-raw-link.fr-link.fr-m-1w
       {:title (i/i @lang :rss-feed)
        :href  "/data/latest.xml"}
       [:span.fr-icon-rss-line {:aria-hidden true}]]
      ;; Download link
      [:button.fr-link.fr-m-1w
       {:title    (i/i @lang :download)
        :on-click (fn []
                    (download-as-csv!
                     (->> repos
                          (map #(set/rename-keys (select-keys % (keys mapping)) mapping))
                          (map #(conj % {:html_url (html-url-from-p-and-fn (:p %) (:fn %))})))
                     (str "codegouvfr-repositories-" (todays-date) ".csv")))}
       [:span.fr-icon-download-line {:aria-hidden true}]]
      ;; General information
      (table-header repos :repo)
      ;; Top pagination block
      [navigate-pagination :repos first-disabled last-disabled repos-pages count-pages]]
     ;; Specific repos search filters and options
     [:div.fr-grid-row
      [:input.fr-input.fr-col.fr-m-2w
       {:placeholder (i/i @lang :license)
        :value       (or @license (:license query-params))
        :aria-label  (i/i @lang :license)
        :on-change   #(let [v (.. % -target -value)]
                        (reset! license v)
                        (debounced-license v))}]
      [:input.fr-input.fr-col.fr-m-2w
       {:placeholder (i/i @lang :language)
        :value       (or @language (:language query-params))
        :aria-label  (i/i @lang :language)
        :on-change   #(let [v (.. % -target -value)]
                        (reset! language v)
                        (debounced-language v))}]
      [:select.fr-select.fr-col-3
       {:value     (or (:forge f) "")
        :on-change #(re-frame/dispatch [:update-filter (.. % -target -value) :forge])}
       [:option#default {:value ""} (i/i @lang :all-forges)]
       (for [x (sort @(re-frame/subscribe [:platforms?]))]
         ^{:key x}
         [:option {:value x} x])]
      [:div.fr-checkbox-group.fr-col.fr-m-2w
       [:input#1 {:type      "checkbox" :name "1"
                  :checked   (= "true" (:fork @(re-frame/subscribe [:query-params?])))
                  :on-change #(re-frame/dispatch [:update-filter (.. % -target -checked) :fork])}]
       [:label.fr-label {:for "1" :title (i/i @lang :only-fork-title)}
        (i/i @lang :only-fork)]]
      [:div.fr-checkbox-group.fr-col.fr-m-2w
       [:input#2 {:type      "checkbox" :name "2"
                  :checked   (= "true" (:floss @(re-frame/subscribe [:query-params?])))
                  :on-change #(re-frame/dispatch [:update-filter (.. % -target -checked) :floss])}]
       [:label.fr-label {:for "2" :title (i/i @lang :only-with-license-title)}
        (i/i @lang :only-with-license)]]
      [:div.fr-checkbox-group.fr-col.fr-m-2w
       [:input#4 {:type      "checkbox" :name "4"
                  :checked   (= "true" (:template @(re-frame/subscribe [:query-params?])))
                  :on-change #(re-frame/dispatch [:update-filter (.. % -target -checked) :template])}]
       [:label.fr-label
        {:for "4" :title (i/i @lang :only-template-title)}
        (i/i @lang :only-template)]]
      [:div.fr-checkbox-group.fr-col.fr-m-2w
       [:input#5 {:type      "checkbox" :name "5"
                  :checked   (= "true" (:with-contributing @(re-frame/subscribe [:query-params?])))
                  :on-change #(re-frame/dispatch [:update-filter (.. % -target -checked) :with-contributing])}]
       [:label.fr-label
        {:for "5" :title (i/i @lang :only-contrib-title)}
        (i/i @lang :only-contrib)]]
      [:div.fr-checkbox-group.fr-col.fr-m-2w
       [:input#6
        {:type      "checkbox" :name "6"
         :checked   (= "true" (:with-publiccode @(re-frame/subscribe [:query-params?])))
         :on-change #(re-frame/dispatch [:update-filter (.. % -target -checked) :with-publiccode])}]
       [:label.fr-label
        {:for "6" :title (i/i @lang :only-publiccode-title)}
        (i/i @lang :only-publiccode)]]]
     ;; Main repos table display
     [repos-table repos]
     ;; Bottom pagination block
     [navigate-pagination :repos first-disabled last-disabled repos-pages count-pages]]))

(defn repo-page []
  (let [{:keys [orga-repo-name platform]} @(re-frame/subscribe [:path-params?])]
    (re-frame/dispatch [:fetch-repo-or-orga-data platform orga-repo-name :repos])
    (let [{:keys [full_name description icon_url]}
          @(re-frame/subscribe [:current-repo-or-orga-data?])]
      (if-not (not-empty full_name)
        [:div "Sorry no data"]
        [:div.fr-container.fr-py-6w
         [:div.fr-grid-row.fr-grid-row--gutters
          [:div.fr-col-12
           [:div.fr-grid-row.fr-grid-row--gutters
            [:div.fr-col-9
             [:h2.fr-h2 full_name]
             [:p description]]
            [:img.fr-responsive-img.fr-col-3 {:src icon_url :data-fr-js-ratio true}]]
           ;; [:div.fr-grid-row.fr-grid-row--gutters
           ;;  [:div.fr-col-12]]
           ]]]))))

;; Main structure - awesome

(defn awesome-table []
  (into
   [:div.fr-grid-row.fr-grid-row--gutters]
   (for [awesome (shuffle @(re-frame/subscribe [:awesome?]))]
     ^{:key (:name awesome)}
     (let [{:keys [name logo legal description]}
           awesome
           desc (not-empty (:shortDescription (get description (keyword @lang))))]
       [:div.fr-col-12.fr-col-md-3
        [:div.fr-card.fr-enlarge-link
         [:div.fr-card__header
          [:div.fr-card__img
           [:img.fr-responsive-img {:src logo :alt "" :data-fr-js-ratio true}]]]
         [:div.fr-card__body
          [:div.fr-card__content
           [:div.fr-card__start
            [:ul.fr-tags-group
             [:li [:p.fr-tag (str (i/i @lang :license) ": " (:license legal))]]]]
           [:h3.fr-card__title
            [:a {:href (rfe/href :awesome-project-page {:awesome-project-name name})} name]]
           [:p.fr-card__desc desc]]]]]))))

(defn awesome-page []
  [:div.fr-container.fr-mt-6w
   [:div.fr-grid-row
    [:div.fr-col-12
     [:div.fr-callout
      [:p.fr-callout__text
       [:span
        (i/i @lang :Awesome-callout)
        ": " [:a.fr-link {:href (rfe/href :releases)}
              (i/i @lang :release-check-latest)]]]]
     [:div.fr-my-6w
      [awesome-table]]]]])

(defn awesome-project-page []
  (let [project-name (:awesome-project-name @(re-frame/subscribe [:path-params?]))
        awesome      @(re-frame/subscribe [:awesome?])
        {:keys [name logo description legal landingURL
                url usedBy fundedBy]}
        (first (filter #(= (:name %) project-name) awesome))
        desc         (or (get description (keyword @lang))
                         (get description :en))]
    [:div.fr-container.fr-py-6w
     [:div.fr-grid-row.fr-grid-row--gutters
      [:div.fr-col-12
       [:div.fr-grid-row.fr-grid-row--gutters
        [:div.fr-col-9
         [:h2.fr-h2 name]
         (if-let [longDesc (:longDescription desc)]
           [:p longDesc]
           [:p (:shortDescription desc)])
         (when (not-empty landingURL)
           [:p [:a.fr-raw-link.fr-icon-global-line
                {:href       landingURL
                 :target     "new"
                 :rel        "noreferrer noopener"
                 :aria-label (i/i @lang :go-to-website)}
                " " (i/i @lang :go-to-website)]])
         [:p [:a.fr-raw-link.fr-icon-code-box-line
              {:href       url
               :target     "new"
               :rel        "noreferrer noopener"
               :aria-label (i/i @lang :go-to-source)}
              " " (i/i @lang :go-to-source)]]]
        [:img.fr-responsive-img.fr-col-3 {:src logo :data-fr-js-ratio true}]]
       [:div.fr-grid-row.fr-grid-row--gutters
        [:div.fr-col-12
         (when-let [license (not-empty (:license legal))]
           [:h4.fr-icon-scales-3-line " " (i/i @lang :license) ": " license])
         (when-let [used (not-empty usedBy)]
           [:div
            [:h4.fr-icon-user-line " " (i/i @lang :Users)]
            [:ul (for [u used] ^{:key u} [:li u])]])
         (when-let [funded (not-empty fundedBy)]
           [:div
            [:br]
            [:h4.fr-icon-government-line " " (i/i @lang :Funders)]
            [:ul (for [{:keys [name url]} funded] ^{:key url}
                   [:li [:a {:href url} name]])]])]]]]]))

;; Main structure - orgas

(defn orgas-table [orgas]
  (if (zero? (count orgas))
    [:div.fr-m-3w [:p {:aria-live "polite"} (i/i @lang :no-orga-found)]]
    (let [org-f @(re-frame/subscribe [:sort-orgas-by?])]
      [:div.fr-table.fr-table--no-caption
       {:role "region" :aria-label (i/i @lang :Orgas)}
       [:table
        [:caption {:id "orgas-table-caption"} (i/i @lang :Orgas)]
        [:thead.fr-grid.fr-col-12
         [:tr
          [:th.fr-col-1 {:scope "col"} "Image"]
          [:th.fr-col-2 {:scope "col"} (i/i @lang :Orgas)]
          [:th.fr-col-6 {:scope "col"} (i/i @lang :description)]
          [:th.fr-col-1 {:scope "col"}
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class        (when (= org-f :repos) "fr-btn--secondary")
             :title        (i/i @lang :sort-repos)
             :on-click     #(re-frame/dispatch [:sort-orgas-by! :repos])
             :aria-pressed (if (= org-f :repos) "true" "false")
             :aria-label   (i/i @lang :sort-repos)}
            (i/i @lang :Repos)]]
          [:th.fr-col-1 {:scope "col"}
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class        (when (= org-f :subscribers) "fr-btn--secondary")
             :title        (i/i @lang :sort-subscribers)
             :on-click     #(re-frame/dispatch [:sort-orgas-by! :subscribers])
             :aria-pressed (if (= org-f :subscribers) "true" "false")
             :aria-label   (i/i @lang :sort-subscribers)}
            (i/i @lang :Subscribers)]]
          [:th.fr-col-1 {:scope "col"}
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class        (when (= org-f :floss) "fr-btn--secondary")
             :title        (i/i @lang :sort-orgas-floss-policy)
             :on-click     #(re-frame/dispatch [:sort-orgas-by! :floss])
             :aria-pressed (if (= org-f :floss) "true" "false")
             :aria-label   (i/i @lang :sort-orgas-floss-policy)}
            (i/i @lang :floss)]]]]
        (into [:tbody]
              (for [orga (take ORGAS-PER-PAGE
                               (drop (* ORGAS-PER-PAGE @(re-frame/subscribe [:orgas-page?]))
                                     orgas))]
                ^{:key (:l orga)}
                (let [{:keys [n l d id h f au r s]} orga]
                  [:tr
                   [:td (if au
                          (if (not-empty h)
                            [:a.fr-raw-link.fr-link
                             {:title      (i/i @lang :orga-homepage)
                              :href       h
                              :aria-label (str (i/i @lang :orga-homepage) " " n)}
                             [:img {:src au :width "100%" :alt (str n " " (i/i @lang :logo))}]]
                            [:img {:src au :width "100%" :alt (str n " " (i/i @lang :logo))}])
                          (when (not-empty h)
                            [:a.fr-raw-link.fr-link
                             {:title      (i/i @lang :orga-homepage)
                              :href       h
                              :aria-label (str (i/i @lang :orga-homepage) " " n)}
                             (i/i @lang :website)]))]
                   [:td
                    [:a.fr-raw-link.fr-link
                     {:title      (i/i @lang :go-to-data)
                      :href       (let [p (last (re-matches #"^https://([^/]+).*$" id))]
                                    (rfe/href :orga-page {:platform p :orga-login l}))
                      :aria-label (str (i/i @lang :go-to-data) " " (or (not-empty n) l))}
                     (or (not-empty n) l)]]
                   [:td {:aria-label (str (i/i @lang :description) ": " d)} d]
                   [:td {:style {:text-align "center"}}
                    [:button..fr-raw-link.fr-link
                     {:title      (i/i @lang :go-to-repos)
                      :on-click   #(do (reset-queries) (rfe/push-state :repos nil {:group id}))
                      :aria-label (str r " " (i/i @lang :repos-of) " " (or (not-empty n) l))} r]]
                   [:td {:style      {:text-align "center"}
                         :aria-label (str s " " (i/i @lang :Subscribers))} s]
                   [:td {:style {:text-align "center"}}
                    (when (not-empty f)
                      [:a {:target     "new"
                           :rel        "noreferrer noopener"
                           :href       f
                           :aria-label (str (i/i @lang :floss-policy) " " (i/i @lang :for) " " (or (not-empty n) l))}
                       (i/i @lang :floss)])]])))]])))

(defn orgas-page []
  (let [orgas          @(re-frame/subscribe [:orgas?])
        orgas-pages    @(re-frame/subscribe [:orgas-page?])
        f              @(re-frame/subscribe [:filter?])
        count-pages    (count (partition-all ORGAS-PER-PAGE orgas))
        first-disabled (zero? orgas-pages)
        last-disabled  (= orgas-pages (dec count-pages))
        mapping        (:orgas mappings)]
    [:div
     [:div.fr-grid-row
      ;; RSS feed
      [:a.fr-raw-link.fr-link.fr-m-1w
       {:title (i/i @lang :rss-feed)
        :href  "/data/latest-organizations.xml"}
       [:span.fr-icon-rss-line {:aria-hidden true}]]
      ;; Download link
      [:button.fr-link.fr-m-1w
       {:title    (i/i @lang :download)
        :on-click #(download-as-csv!
                    (map
                     (fn [r] (set/rename-keys (select-keys r (keys mapping)) mapping))
                     orgas)
                    (str "codegouvfr-organizations-" (todays-date) ".csv"))}
       [:span.fr-icon-download-line {:aria-hidden true}]]
      ;; General information
      (table-header orgas :orga)
      ;; Top pagination block
      [navigate-pagination :orgas first-disabled last-disabled orgas-pages count-pages]]
     [:div.fr-grid-row
      [:select.fr-select.fr-col.fr-m-1w
       {:value     (or (:ministry f) "")
        :on-change #(re-frame/dispatch [:update-filter (.. % -target -value) :ministry])}
       [:option#default {:value ""} (i/i @lang :all-ministries)]
       (for [x @(re-frame/subscribe [:ministries?])]
         ^{:key x}
         [:option {:value x} x])]]
     [orgas-table orgas]
     [navigate-pagination :orgas first-disabled last-disabled orgas-pages count-pages]]))

(defn orga-page []
  (let [{:keys [orga-login platform]} @(re-frame/subscribe [:path-params?])]
    (re-frame/dispatch [:fetch-repo-or-orga-data platform orga-login :orgas])
    (let [{:keys [name description icon_url]}
          @(re-frame/subscribe [:current-repo-or-orga-data?])]
      (if-not (not-empty name)
        [:div "Sorry no data"]
        [:div.fr-container.fr-py-6w
         [:div.fr-grid-row.fr-grid-row--gutters
          [:div.fr-col-12
           [:div.fr-grid-row.fr-grid-row--gutters
            [:div.fr-col-9
             [:h2.fr-h2 name]
             [:p description]]
            [:img.fr-responsive-img.fr-col-3 {:src icon_url :data-fr-js-ratio true}]]
           ;; [:div.fr-grid-row.fr-grid-row--gutters
           ;;  [:div.fr-col-12]]
           ]]]))))

;; Releases page

(defn releases-page []
  (let [awes     @(re-frame/subscribe [:awesome?])
        releases (flatten (map :releases awes))]
    [:div
     [:div.fr-grid-row
      ;; RSS feed
      [:a.fr-raw-link.fr-link.fr-m-1w
       {:title (i/i @lang :rss-feed)
        :href  "/data/latest-releases.xml"}
       [:span.fr-icon-rss-line {:aria-hidden true}]]
      ;; General informations
      (table-header releases :release)]
     ;; Main releases display
     [:div.fr-table.fr-table--no-caption
      [:table
       [:caption (i/i @lang :Releases)]
       [:thead.fr-grid.fr-col-12
        [:tr
         [:th.fr-col-1 (i/i @lang :Repo)]
         [:th.fr-col-2 (i/i @lang :description)]
         [:th.fr-col-1 (i/i @lang :Releasename)]
         [:th.fr-col-1 (i/i @lang :update-short)]]]
       (into
        [:tbody]
        (for [release (reverse (sort-by :published_at releases))]
          ^{:key (:html_url release)}
          (let [{:keys [repo_name html_url body tag_name published_at]} release]
            [:tr [:td
                  [:a.fr-link
                   {:href   html_url
                    :target "_blank"
                    :title  (i/i @lang :Repo)
                    :rel    "noreferrer noopener"} repo_name]]
             [:td body]
             [:td tag_name]
             [:td (to-locale-date published_at)]])))]]]))

;; Stats page

(defn pie-chart [{:keys [data data-key name-key title-i18n-keyword]}]
  (let [formatted-data
        (map (fn [i] {:name (get i name-key) :y (get i data-key)}) data)
        chart-options
        {:chart       {:type "pie"}
         :title       {:text (i/i @lang title-i18n-keyword)}
         :tooltip     {:pointFormat "<b>{point.percentage:.1f}%</b>"}
         :plotOptions {:pie {:allowPointSelect true
                             :cursor           "pointer"
                             :dataLabels
                             {:enabled true
                              :format  "<b>{point.name}</b>: {point.percentage:.1f} %"}}}
         :series      [{:name         (i/i @lang title-i18n-keyword)
                        :colorByPoint true
                        :data         formatted-data}]
         :credits     {:enabled false}}]
    (when (seq formatted-data)  ; Only render if we have data
      [:div.fr-col-6
       {:style {:width "100%" :height "400px"}
        :ref   #(when % (js/Highcharts.chart % (clj->js chart-options)))}])))

(defn repos-by-score-bar-chart [stats]
  (let [data (:top_repos_by_score_range stats)
        chart-options
        {:chart   {:type "column"}
         :title   {:text (i/i @lang :repos-vs-score)}
         :xAxis   {:type       "category"
                   :title      {:text (i/i @lang :Score)}
                   :categories (map (comp str first) data)
                   :labels     {:rotation -45 :style {:fontSize "13px"}}}
         :yAxis   {:type  "logarithmic" :min 1 :max 10000
                   :title {:text (i/i @lang :number-of-repos)}}
         :series  [{:data (map second data)}]
         :tooltip {:pointFormat (str (i/i @lang :number-of-repos) ": <b>{point.y}</b>")}
         :legend  {:enabled false}
         :credits {:enabled false}}]
    [:div.fr-col-12
     {:style {:width "100%" :height "400px"}
      :ref   #(when % (js/Highcharts.chart % (clj->js chart-options)))}]))

(defn scatter-chart [stats chart-options]
  (let [{:keys [title x-axis tooltip-x data-key]}
        chart-options
        chart-data (get stats data-key)
        chart-options
        {:chart       {:type "scatter" :zoomType "xy"}
         :title       {:text (i/i @lang title)}
         :xAxis       {:title         {:enabled true :text (i/i @lang tooltip-x)}
                       :startOnTick   true
                       :endOnTick     true
                       :showLastLabel true}
         :yAxis       {:title {:enabled true :text (i/i @lang :number-of-repos)}}
         :legend      {:enabled false}
         :plotOptions {:scatter {:tooltip {:headerFormat "<b>{point.key}</b><br>"
                                           :pointFormat  (str
                                                          (i/i @lang tooltip-x)
                                                          ": {point.x}<br>"
                                                          (i/i @lang :Repos)
                                                          ": {point.y}")}}}
         :series      [{:name  "Organizations"
                        :color "rgba(223, 83, 83, .5)"
                        :data  (map (fn [{:keys [owner repositories_count] :as item}]
                                      {:x    (get item x-axis)
                                       :y    repositories_count
                                       :name owner})
                                    chart-data)}]
         :credits     {:enabled false}}]
    [:div.fr-col-6
     {:style {:width "100%" :height "400px"}
      :ref   #(when % (js/Highcharts.chart % (clj->js chart-options)))}]))

(defn scatter-chart-repos-vs-stars [stats]
  (scatter-chart
   stats
   {:title     :repos-vs-stars
    :x-axis    :total_stars
    :tooltip-x :Stars-total
    :data-key  :top_orgs_repos_stars}))

(defn scatter-chart-repos-vs-followers [stats]
  (scatter-chart
   stats
   {:title     :repos-vs-followers
    :x-axis    :followers
    :tooltip-x :Followers-total
    :data-key  :top_orgs_repos_followers}))

(defn to-percent [num total]
  (js/parseFloat (gstring/format "%.2f" (* 100 (/ (* num 1.0) total)))))

(defn languages-chart [stats]
  (let [top_languages (take 10 (:top_languages stats))
        repos_total   (reduce + (map second top_languages))
        data          (for [[language count] top_languages]
                        {:language   language
                         :percentage (to-percent count repos_total)})]
    (pie-chart
     {:data               data
      :data-key           :percentage
      :name-key           :language
      :title-i18n-keyword :most-used-languages})))

(defn licenses-chart [stats]
  (let [top_licenses (take 10 (:top_licenses stats))
        repos_total  (reduce + (map second top_licenses))
        data         (for [[license count] top_licenses]
                       {:license    license
                        :percentage (to-percent count repos_total)})]
    (pie-chart
     {:data               data
      :data-key           :percentage
      :name-key           :license
      :title-i18n-keyword :most-used-licenses})))

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

(defn stats-page []
  (let [stats
        @(re-frame/subscribe [:stats?])
        {:keys [top_orgs_by_repos top_orgs_by_stars]} stats]
    [:div.fr-grid
     [:div.fr-grid-row.fr-grid-row--center.fr-m-3w
      [languages-chart stats]
      [licenses-chart stats]]
     [:div.fr-grid-row.fr-grid-row--center.fr-m-3w
      [scatter-chart-repos-vs-stars stats]
      [scatter-chart-repos-vs-followers stats]
      [repos-by-score-bar-chart stats]]
     [:div.fr-grid-row
      [:div.fr-col-6.fr-grid-row.fr-grid-row--center
       (stats-table [:span
                     (i/i @lang :Orgas)
                     (i/i @lang :with-more-of)
                     (i/i @lang :repos)]
                    (top-clean-up-orgas top_orgs_by_repos "group")
                    [:thead [:tr [:th.fr-col-10 (i/i @lang :Orgas)]
                             [:th (i/i @lang :Repos)]]])]
      [:div.fr-col-6.fr-grid-row.fr-grid-row--center
       (stats-table [:span (i/i @lang :most-starred-orgas)]
                    (top-clean-up-orgas top_orgs_by_stars "group")
                    [:thead [:tr [:th.fr-col-10 (i/i @lang :Orgas)]
                             [:th (i/i @lang :Stars)]]])]]]))

;; Main structure elements

(defn banner []
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
          [:a {:href "https://code.gouv.fr"}
           [:div.fr-header__service-title
            [:svg {:width "240px" :viewBox "0 0 299.179 49.204"}
             [:path {:fill "#808080" :d "M5.553 2.957v2.956h4.829V0H5.553Zm5.554 0v2.956h4.829V0h-4.829Zm5.553 0v2.956h4.587V0H16.66zm5.553 0v2.956h4.829V0h-4.829zm76.057 0v2.956h4.829V0H98.27zm5.553 0v2.956h4.829V0h-4.829zm53.843 0v2.956h4.829V0h-4.829zm5.794 0v2.956h4.588V0h-4.587zm5.313 0v2.956h4.829V0h-4.829zm5.794 0v2.956h4.588V0h-4.588zM0 10.27v3.112h4.854l-.073-3.05-.073-3.018-2.342-.094L0 7.127zm5.553 0v3.143l2.367-.093 2.342-.093V7.314L7.92 7.22l-2.367-.093zm16.66 0v3.112h4.853l-.072-3.05-.072-3.018-2.343-.094-2.366-.093zm5.554 0v3.112h4.587V7.158h-4.587zm70.672-2.894c-.097.093-.17 1.494-.17 3.112v2.894h4.83V7.158h-2.246c-1.255 0-2.342.093-2.414.218zm5.553-.031c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm48.362 2.925v3.112h4.588V7.158h-4.588zm5.481-2.894c-.097.093-.17 1.494-.17 3.112v2.894h4.83V7.158h-2.246c-1.255 0-2.342.093-2.414.218zm16.732 2.894v3.112h4.588V7.158h-4.588zm5.553 0v3.112h4.588V7.158h-4.587zM0 17.428v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019H0Zm5.553 0v3.143l2.367-.093 2.342-.093v-5.913l-2.342-.094-2.367-.093zm38.197-.093.073 3.05h4.587l.073-3.05.072-3.019h-4.877zm5.554 0 .072 3.05h4.588l.072-3.05.073-3.019H49.23zm5.505.093v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019h-4.853zm5.601-.093.073 3.05h4.587l.073-3.05.072-3.019h-4.877zm21.248 0 .072 3.05h4.588l.072-3.05.073-3.019h-4.878zm5.553 0 .073 3.05 2.366.093 2.342.093v-6.255h-4.853zm5.505.093v3.143l2.366-.093 2.343-.093.072-3.05.072-3.019h-4.853zm5.602-.093.072 3.05 2.367.093 2.342.093v-6.255h-4.854zm5.553 0 .073 3.05h4.587l.073-3.05.072-3.019h-4.877zm15.936 0 .072 3.05h4.588l.072-3.05.073-3.019h-4.878zm5.553 0 .073 3.05 2.366.093 2.342.093v-6.255h-4.853zm5.553 0 .073 3.05h4.587l.073-3.05.072-3.019h-4.877zm5.747.093v3.112h4.587v-6.224h-4.587zm15.694 0v3.112h4.588v-6.224h-4.588zm5.36-.093.073 3.05h4.587l.073-3.05.072-3.019h-4.877zm38.342.093v3.112h4.588v-6.224h-4.588zm5.554 0v3.112h4.587v-6.224h-4.587zm5.553 0v3.112h4.587v-6.224h-4.587zm5.553 0v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019h-4.853zm15.936 0v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019h-4.853zm5.601-.093.073 3.05h4.587l.073-3.05.072-3.019h-4.877zm16.66 0 .073 3.05h4.587l.073-3.05.072-3.019h-4.877zm5.505.093v3.143l2.367-.093 2.342-.093.072-3.05.073-3.019h-4.854zm10.142 0v3.143l2.365-.093 2.343-.093.072-3.05.072-3.019h-4.853zm5.6-.093.073 3.05h4.588l.072-3.05.073-3.019h-4.878zm16.66 0 .073 3.05 2.366.093 2.342.093v-6.255h-4.853zm5.506.093v3.143l2.366-.093 2.342-.093.073-3.05.072-3.019h-4.853zM0 24.742v2.956h4.829v-5.913H0Zm5.553 0v2.956h4.829v-5.913H5.553Zm32.596 0v2.956h4.829v-5.913h-4.829zm5.553 0v2.956h4.829v-5.913h-4.829zm16.66 0v2.956h4.829v-5.913h-4.829zm5.553 0v2.956h4.829v-5.913h-4.829zm10.141 0v2.956h4.829v-5.913h-4.829zm5.554 0v2.956h4.829v-5.913H81.61zm16.66 0v2.956h4.829v-5.913H98.27zm5.553 0v2.956h4.829v-5.913h-4.829zm10.382 0v2.956h4.829v-5.913h-4.829zm5.554 0v2.956h4.828v-5.913h-4.828zm16.901 0v2.956h4.587v-5.913h-4.587zm5.312 0v2.956h4.828v-5.913h-4.828zm10.382 0v2.956h4.588v-5.913h-4.588zm5.312 0v2.956h4.829v-5.913h-4.829zm11.107 0v2.956h4.829v-5.913h-4.829zm5.794 0v2.956h4.588v-5.913h-4.588zm5.553 0v2.956h4.588v-5.913h-4.587zm10.383 0v2.956h4.829v-5.913h-4.829zm5.553 0v2.956h4.588v-5.913h-4.588zm16.66 0v2.956h4.829v-5.913h-4.829zm5.554 0v2.956h4.587v-5.913h-4.587zm10.382 0v2.956h4.828v-5.913h-4.828zm5.553 0v2.956h4.829v-5.913h-4.829zm16.66 0v2.956h4.829v-5.913h-4.829zm5.553 0v2.956h4.829v-5.913h-4.829zm10.142 0v2.956h4.828v-5.913h-4.828zm5.553 0v2.956h4.829v-5.913h-4.829zm16.66 0v2.956h4.828v-5.913h-4.828zm5.553 0v2.956h4.829v-5.913h-4.829zM0 31.744v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094L0 28.601zm5.553 0v3.144l2.367-.094 2.342-.093v-5.913l-2.342-.094-2.367-.093zm32.596 0v3.112h4.829v-6.224h-4.829zm5.553 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm16.66 0v3.112h4.829v-6.224h-4.829zm5.553 0v3.144l2.367-.094 2.342-.093v-5.913l-2.342-.094-2.367-.093zm10.141 0v3.112h4.829v-6.224h-4.829zm5.554 0v3.112h4.829v-6.224H81.61zm16.756-2.707c-.072.249-.096 1.618-.048 3.05l.072 2.614 2.367.093 2.342.094v-6.256h-2.294c-1.714 0-2.342.125-2.439.405zm5.457 2.707v3.112h4.829v-6.224h-4.829zm10.479-2.707c-.073.249-.097 1.618-.049 3.05l.073 2.614 2.366.093 2.342.094v-6.256h-2.294c-1.714 0-2.342.125-2.438.405zm5.457 2.707v3.112h4.828v-6.224h-4.828zm5.649-2.707c-.072.249-.096 1.618-.048 3.05l.073 2.614 2.366.093 2.342.094v-6.256h-2.294c-1.714 0-2.342.125-2.439.405zm5.457 2.707v3.112h4.829v-6.224h-4.829zm5.795 0v3.112h4.587v-6.224h-4.587zm5.408-2.707c-.072.249-.096 1.618-.048 3.05l.073 2.614 2.366.093 2.342.094v-6.256h-2.294c-1.714 0-2.342.125-2.439.405zm10.286 2.707v3.112h4.588v-6.224h-4.588zm5.409-2.707c-.073.249-.097 1.618-.049 3.05l.073 2.614 2.366.093 2.342.094v-6.256H160.2c-1.714 0-2.342.125-2.438.405zm16.804 2.707v3.112h4.588v-6.224h-4.588zm5.553 0v3.112h4.588v-6.224h-4.587zm10.383 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm5.553 0v3.112h4.588v-6.224h-4.588zm16.66 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm5.554 0v3.112h4.587v-6.224h-4.587zm10.382 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm5.553 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm16.66 0v3.112h4.829v-6.224h-4.829zm5.553 0v3.144l2.367-.094 2.342-.093v-5.913l-2.342-.094-2.367-.093zm10.142 0v3.112h4.828v-6.224h-4.828zm5.553 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm16.66 0v3.112h4.828v-6.224h-4.828zm5.553 0v3.112h4.829v-6.224h-4.829zM0 38.747v2.956h4.829V35.79H0Zm5.553 0v2.956h4.829V35.79H5.553Zm16.66 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.587V35.79h-4.587zm10.382 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.829V35.79h-4.829zm16.66 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.829V35.79h-4.829zm10.141 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.829V35.79H81.61zm16.66 0v2.956h4.829V35.79H98.27zm5.553 0v2.956h4.829V35.79h-4.829zm10.382 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.828V35.79h-4.828zm32.595 0v2.956h4.588V35.79h-4.588zm5.312 0v2.956h4.829V35.79h-4.829zm16.901 0v2.956h4.588V35.79h-4.588zm5.553 0v2.956h4.588V35.79h-4.587zm10.383 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.588V35.79h-4.588zm16.66 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.587V35.79h-4.587zm10.382 0v2.956h4.828V35.79h-4.828zm5.553 0v2.956h4.829V35.79h-4.829zm16.66 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.829V35.79h-4.829zm15.695 0v2.956h4.829V35.79h-4.829zm5.553 0v2.956h4.829V35.79h-4.829zm5.554 0v2.956h4.828V35.79h-4.828zm5.553 0v2.956h4.828V35.79h-4.828zM5.553 46.06v3.144l2.367-.094 2.342-.093v-5.913L7.92 43.01l-2.367-.093zm5.554 0v3.112h4.853l-.073-3.05-.072-3.018-2.342-.094-2.366-.093zm5.553 0v3.112h4.587v-6.224H16.66zm5.722-2.925c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.072-3.018-2.174-.094c-1.207-.03-2.27 0-2.366.125zm21.489 0c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.554 0c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.073-3.05-.072-3.018-2.173-.094c-1.208-.03-2.27 0-2.366.125zm5.384 2.925v3.112h4.853l-.072-3.05-.073-3.018-2.342-.094-2.366-.093zm5.722-2.925c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm21.248 0c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.073-3.05-.072-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.553.031c-.097.093-.17 1.494-.17 3.112v2.894h4.83v-6.224h-2.246c-1.255 0-2.342.093-2.414.218zm5.384 2.894v3.112h4.853l-.072-3.05-.072-3.018-2.343-.094-2.366-.093zm5.723-2.894c-.097.093-.17 1.494-.17 3.112v2.894h4.83v-6.224h-2.246c-1.255 0-2.342.093-2.414.218zm5.553-.031c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm15.936 0c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.073-3.05-.072-3.018-2.173-.094c-1.208-.03-2.27 0-2.366.125zm5.552.031c-.096.093-.168 1.494-.168 3.112v2.894h4.828v-6.224h-2.246c-1.255 0-2.342.093-2.414.218zm5.554-.031c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.626 2.925v3.112h4.587v-6.224h-4.587zm21.175-2.925c-.097.124-.17 1.525-.17 3.143v2.894h4.855l-.073-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.625 2.925v3.112h4.588v-6.224h-4.587zm5.482-2.894c-.097.093-.17 1.494-.17 3.112v2.894h4.83v-6.224h-2.246c-1.255 0-2.342.093-2.414.218zm5.625 2.894v3.112h4.588v-6.224h-4.588zm21.489 0v3.112h4.588v-6.224h-4.588zm5.554 0v3.112h4.587v-6.224h-4.587zm5.553 0v3.112h4.587v-6.224h-4.587zm5.553 0v3.112h4.853l-.072-3.05-.073-3.018-2.342-.094-2.366-.093zm21.658-2.925c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.384 2.925v3.112h4.854l-.073-3.05-.072-3.018-2.342-.094-2.367-.093zm5.554 0v3.144l2.366-.094 2.342-.093v-5.913l-2.342-.094-2.366-.093zm5.722-2.925c-.096.124-.169 1.525-.169 3.143v2.894h4.853l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm26.801 0c-.097.124-.17 1.525-.17 3.143v2.894h4.854l-.072-3.05-.073-3.018-2.173-.094c-1.207-.03-2.27 0-2.366.125zm5.385 2.925v3.112h4.852l-.072-3.05-.073-3.018-2.342-.094-2.366-.093z"}]]]]
          [:p.fr-header__service-tagline (i/i @lang :index-title)]]]
        [:div.fr-header__tools
         [:div.fr-header__tools-links
          [:ul.fr-links-group
           [:li [:a.fr-link.fr-icon-mastodon-line
                 {:rel   "me"
                  :href  "https://social.numerique.gouv.fr/@codegouvfr"
                  :title (i/i @lang :mastodon-follow)} "@codegouvfr"]]
           [:li [:a.fr-link.fr-icon-twitter-x-line
                 {:rel   "me"
                  :href  "https://x.com/codegouvfr"
                  :title (i/i @lang :twitter-follow)} "@codegouvfr"]]
           [:li [:a.fr-link {:href (rfe/href :feeds)} (i/i @lang :rss-feed)]]
           [:li [:button.fr-link.fr-icon-theme-fill.fr-link--icon-left
                 {:aria-controls  "fr-theme-modal"
                  :title          (str (i/i @lang :modal-title) " - "
                                       (i/i @lang :new-modal))
                  :data-fr-opened false}
                 (i/i @lang :modal-title)]]]]]]]]
     ;; Header menu
     [:div#modal-833.fr-header__menu.fr-modal
      {:aria-labelledby "fr-btn-menu-mobile"}
      [:div.fr-container
       [:button.fr-link--close.fr-link
        {:aria-controls "modal-833"} (i/i @lang :close)]
       [:div.fr-header__menu-links]
       [:nav#navigation-832.fr-nav {:role "navigation" :aria-label "Principal"}
        [:ul.fr-nav__list
         [:li.fr-nav__item
          [:button.fr-nav__link
           {:aria-current (when (= path "/") "page")
            :on-click     #(rfe/push-state :home)}
           (i/i @lang :home)]]
         [:li.fr-nav__item
          [:button.fr-nav__link
           {:aria-current (when (= path "/awesome") "page")
            :title        "Awesome"
            :on-click
            #(do (reset-queries) (rfe/push-state :awesome))}
           "Awesome"]]
         [:li.fr-nav__item
          [:button.fr-nav__link
           {:aria-current (when (= path "/repos") "page")
            :title        (i/i @lang :repos-of-source-code)
            :on-click
            #(do (reset-queries) (rfe/push-state :repos))}
           (i/i @lang :Repos)]]
         [:li.fr-nav__item
          [:button.fr-nav__link
           {:aria-current (when (= path "/groups") "page")
            :on-click
            #(do (reset-queries) (rfe/push-state :orgas))}
           (i/i @lang :Orgas)]]
         [:li.fr-nav__item
          [:a.fr-nav__link
           {:aria-current (when (= path "/stats") "page")
            :title        (i/i @lang :stats-expand)
            :href         (rfe/href :stats)}
           (i/i @lang :Stats)]]
         [:li.fr-nav__item
          [:a.fr-nav__link
           {:aria-current (when (= path "/about") "page")
            :href         (rfe/href :about)}
           (i/i @lang :rien/tout)]]]]]]]))

(defn subscribe []
  [:div.fr-follow
   [:div.fr-container
    [:div.fr-grid-row
     [:div.fr-col-12.fr-col-md-4
      [:div.fr-follow__special
       [:div
        [:h1.fr-h5.fr-follow__title (i/i @lang :contact)]
        [:div.fr-text--sm.fr-follow__desc
         (i/i @lang :contact-title)]]]]
     [:div.fr-col-12.fr-col-md-4
      [:div.fr-follow__newsletter
       [:div
        [:h1.fr-h5.fr-follow__title (i/i @lang :bluehats)]
        [:p.fr-text--sm.fr-follow__desc
         (i/i @lang :bluehats-desc)]
        [:a.fr-btn
         {:type "button"
          :href "https://code.gouv.fr/newsletters"}
         (i/i @lang :subscribe)]]]]
     [:div.fr-col-12.fr-col-md-4
      [:div.fr-share
       [:p.fr-h5.fr-mb-3v (i/i @lang :find-us)]
       [:div.fr-share__group
        [:a.fr-share__link
         {:href       "https://social.numerique.gouv.fr/@codegouvfr"
          :aria-label (i/i @lang :mastodon-follow)
          :title      (new-tab (i/i @lang :mastodon-follow))
          :rel        "noreferrer noopener me"
          :target     "_blank"}
         "Mastodon"]
        [:a.fr-share__link
         {:href       "https://x.com/codegouvfr"
          :aria-label (i/i @lang :twitter-follow)
          :title      (new-tab (i/i @lang :twitter-follow))
          :rel        "noreferrer noopener me"
          :target     "_blank"}
         "Twitter"]]]]]]])

(defn footer []
  [:footer.fr-footer {:role "contentinfo"}
   [:div.fr-container
    [:div.fr-footer__body
     [:div.fr-footer__brand.fr-enlarge-link
      [:a {:on-click #(rfe/push-state :home)
           :title    (i/i @lang :home)}
       [:p.fr-logo "République" [:br] "Française"]]]
     [:div.fr-footer__content
      [:p.fr-footer__content-desc (i/i @lang :footer-desc)
       [:a {:href "https://code.gouv.fr"}
        (i/i @lang :footer-desc-link)]]
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
        {:on-click #(reset! lang (if (= @lang "fr") "en" "fr"))}
        (i/i @lang :switch-lang)]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href (rfe/href :a11y)}
        (i/i @lang :accessibility)]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href (rfe/href :legal)}
        (i/i @lang :legal)]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href (rfe/href :legal)}
        (i/i @lang :personal-data)]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href (rfe/href :sitemap)}
        (i/i @lang :sitemap)]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href  (rfe/href :feeds)
         :title (i/i @lang :subscribe-rss-flux)}
        (i/i @lang :rss-feed)]]
      [:li.fr-footer__bottom-item
       [:button.fr-footer__bottom-link.fr-icon-theme-fill.fr-link--icon-left
        {:aria-controls  "fr-theme-modal"
         :title          (str (i/i @lang :modal-title) " - "
                              (i/i @lang :new-modal))
         :data-fr-opened false}
        (i/i @lang :modal-title)]]]]]])

(defn display-parameters-modal []
  [:dialog#fr-theme-modal.fr-modal
   {:role "dialog" :aria-labelledby "fr-theme-modal-title"}
   [:div.fr-container.fr-container--fluid.fr-container-md
    [:div.fr-grid-row.fr-grid-row--center
     [:div.fr-col-12.fr-col-md-6.fr-col-lg-4
      [:div.fr-modal__body
       [:div.fr-modal__header
        [:button.fr-link--close.fr-link {:aria-controls "fr-theme-modal"}
         (i/i @lang :modal-close)]]
       [:div.fr-modal__content
        [:h1#fr-theme-modal-title.fr-modal__title
         (i/i @lang :modal-title)]
        [:div#fr-display.fr-form-group.fr-display
         [:fieldset.fr-fieldset
          [:legend#-legend.fr-fieldset__legend.fr-text--regular
           (i/i @lang :modal-select-theme)]
          [:div.fr-fieldset__content
           [:div.fr-radio-group.fr-radio-rich
            [:input#fr-radios-theme-light
             {:type "radio" :name "fr-radios-theme" :value "light"}]
            [:label.fr-label {:for "fr-radios-theme-light"}
             (i/i @lang :modal-theme-light)]
            [:div.fr-radio-rich__img {:data-fr-inject true}
             [:img {:src "./img/artwork/light.svg"}]]]
           [:div.fr-radio-group.fr-radio-rich
            [:input#fr-radios-theme-dark
             {:type "radio" :name "fr-radios-theme" :value "dark"}]
            [:label.fr-label {:for "fr-radios-theme-dark"}
             (i/i @lang :modal-theme-dark)]
            [:div.fr-radio-rich__img {:data-fr-inject true}
             [:img {:src "./img/artwork/dark.svg"}]]]
           [:div.fr-radio-group.fr-radio-rich
            [:input#fr-radios-theme-system
             {:type "radio" :name "fr-radios-theme" :value "system"}]
            [:label.fr-label {:for "fr-radios-theme-system"}
             (i/i @lang :modal-theme-system)]
            [:div.fr-radio-rich__img {:data-fr-inject true}
             [:img {:src "./img/artwork/system.svg"}]]]]]]]]]]]])

;; Main pages functions

(defn main-menu [view]
  (let [f            @(re-frame/subscribe [:filter?])
        query-params @(re-frame/subscribe [:query-params?])
        free-search  (i/i @lang :free-search)
        debounced-q  (debounce #(re-frame/dispatch [:update-filter % :q]))]
    [:div
     [:div.fr-grid-row.fr-mt-2w
      [:div.fr-col-12
       (when (some #{:repos :orgas} [view])
         [:input.fr-input
          {:placeholder free-search
           :value       (or @q (:q query-params))
           :aria-label  free-search
           :on-change   #(let [v (.. % -target -value)]
                           (reset! q v)
                           (debounced-q v))}])]
      (when-let [flt (-> f (dissoc :fork :with-publiccode :with-contributing
                                   :template :floss))]
        [:div.fr-col-8.fr-grid-row.fr-m-1w
         (when-let [ff (not-empty (:group flt))]
           [:span
            [:button.fr-link.fr-icon-close-circle-line.fr-link--icon-right
             {:title    (i/i @lang :remove-filter)
              :on-click #(do (re-frame/dispatch [:filter! {:group nil}])
                             (rfe/push-state :repos))}
             [:span ff]]])])]]))

(defn main-page []
  (let [view @(re-frame/subscribe [:view?])]
    [:div
     (banner)
     [:main#main.fr-container.fr-container--fluid.fr-mb-3w
      {:role "main"}
      [main-menu view]
      (condp = view
        :home                 [home-page]
        :orgas                [orgas-page]
        :orga-page            [orga-page]
        :repos                [repos-page]
        :repo-page            [repo-page]
        :releases             [releases-page]
        :awesome              [awesome-page]
        :awesome-project-page [awesome-project-page]
        :stats                [stats-page]
        :legal                (condp = @lang "fr" (inline-page "legal.fr.md")
                                     (inline-page "legal.en.md"))
        :a11y                 (condp = @lang "fr" (inline-page "a11y.fr.md")
                                     (inline-page "a11y.en.md"))
        :sitemap              (condp = @lang "fr" (inline-page "sitemap.fr.md")
                                     (inline-page "sitemap.en.md"))
        :feeds                (condp = @lang "fr" (inline-page "feeds.fr.md")
                                     (inline-page "feeds.en.md"))
        :about                (condp = @lang "fr" (inline-page "about.fr.md")
                                     (inline-page "about.en.md"))
        nil)]
     (subscribe)
     (footer)
     (display-parameters-modal)]))

;; Setup router and init

(defn on-navigate [match]
  (let [page (keyword (:name (:data match)))]
    ;; Rely on the server to handle /not-found as a 404
    (when (not (seq match)) (set! (.-location js/window) "/not-found"))
    (let [path-params (:path (:parameters match))]
      (set! (. js/document -title)
            (str (i/i @lang :title-prefix)
                 (case page
                   :awesome              (i/i @lang :Awesome)
                   :orgas                (i/i @lang :Orgas)
                   :orga-page            (str (i/i @lang :Orga) " - "
                                              (:orga-login path-params))
                   :repo-page            (str (i/i @lang :Repo) " - "
                                              (:orga-repo-name path-params))
                   :awesome-project-page (str (i/i @lang :Awesome) " - "
                                              (:awesome-project-page path-params))
                   :repos                (i/i @lang :Repos)
                   :home                 (i/i @lang :title-default)
                   :legal                (i/i @lang :legal)
                   :releases             (i/i @lang :Releases)
                   :stats                (i/i @lang :Stats)
                   :a11y                 (i/i @lang :accessibility)
                   :feeds                (i/i @lang :rss-feed)
                   :sitemap              (i/i @lang :sitemap)
                   :about                (i/i @lang :About)
                   nil)))
      (re-frame/dispatch [:path-params! path-params])
      (re-frame/dispatch [:path! (:path match)])
      (let [query-params (:query-params match)]
        (re-frame/dispatch [:query-params! query-params])
        (re-frame/dispatch [:filter! (merge init-filter query-params)])
        (re-frame/dispatch [:view! page query-params])))))

(defonce routes
  ["/"
   ["" :home]
   ["groups" :orgas]
   ["groups/:platform/:orga-login" :orga-page]
   ["repos" :repos]
   ["repos/:platform/:orga-repo-name" :repo-page]
   ["awesome" :awesome]
   ["awesome/:awesome-project-name" :awesome-project-page]
   ["releases" :releases]
   ["stats" :stats]
   ["legal" :legal]
   ["a11y" :a11y]
   ["about" :about]
   ["sitemap" :sitemap]
   ["feeds" :feeds]])

(defonce root (atom nil))

(defn ^:export init []
  (re-frame/clear-subscription-cache!)
  (re-frame/dispatch-sync [:initialize-db!])
  (re-frame/dispatch [:fetch-repositories])
  (re-frame/dispatch [:fetch-owners])
  (re-frame/dispatch [:fetch-awesome])
  (re-frame/dispatch [:fetch-platforms])
  (re-frame/dispatch [:fetch-stats])
  (let [browser-lang (subs (or js/navigator.language "en") 0 2)
        language     (if (contains? i/languages browser-lang) browser-lang "en")]
    (reset! lang language))
  (rfe/start! (rf/router routes {:conflicts nil}) on-navigate {:use-fragment true})
  (when (nil? @root)
    (reset! root (rdc/create-root (js/document.getElementById "app"))))
  (rdc/render @root [main-page]))
