;; Copyright (c) 2019-2024 DINUM, Bastien Guerry <bastien.guerry@code.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.core
  (:require [cljs.core.async :as async]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.dom.client :as rdc]
            [cljs-bean.core :refer [bean]]
            [clojure.browser.dom :as dom]
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
            ["recharts" :refer [ResponsiveContainer ScatterChart Scatter
                                XAxis YAxis ZAxis Tooltip Legend CartesianGrid
                                Pie PieChart Cell]])
  (:require-macros [codegouvfr.macros :refer [inline-page]]))

;; Defaults

(def ^:const UNIX-EPOCH "1970-01-01T00:00:00Z")
(def ^:const REPOS-PER-PAGE 100)
(def ^:const ORGAS-PER-PAGE 20)
(def ^:const TIMEOUT 200)
(def ^:const ecosystem-prefix-url "https://data.code.gouv.fr/api/v1/hosts/")
(def ^:const swh-baseurl "https://archive.softwareheritage.org/browse/origin/")

(defonce init-filter
  {:q                 nil
   :group             nil
   :license           nil
   :language          nil
   :forge             ""
   :fork              false
   :floss             false
   :with-publiccode   false
   :with-contributing false
   :ministry          ""})

(defonce filter-chan (async/chan 100))

;; Mappings used when exporting displayed data to csv files
(defonce mappings
  {:repos {
           :d  :description
           :f  :forks_count
           :f? :is_fork
           :l  :language
           :li :license
           :n  :name
           :o  :organization_url
           :s  :subscribers_count
           :t? :is_template
           :u  :last_update
           }
   :orgas {
           :au :avatar_url
           :d  :description
           :ps :public_sector_organization
           :id :organization_url
           :m  :ministry
           :n  :name
           :r  :repositories_count
           :s  :subscribers_count
           }})

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

(defn top-clean-up-repos [data param]
  (let [total (reduce + (map last data))]
    (->>
     data
     (sequence
      (comp
       (map (fn [[k v]]
              [k (if (some #{"license" "language"} (list param))
                   (js/parseFloat
                    (gstring/format "%.2f" (* (/ v total) 100)))
                   v)]))
       (map #(let [[k v] %]
               [[:a {:href (rfe/href :repos nil {param k})} k] v])))))))

(defn top-clean-up-orgas [data param]
  (sequence
   (map #(let [[k v] %
               k0    (s/replace k #" \([^)]+\)" "")]
           [[:a {:href (rfe/href :orgas nil {param k0})} k] v]))
   data))

(defn html-url-from-p-and-fn [p fn]
  (str "https://" p "/" fn))

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

(defn if-a-b-else-true
  "Not a true and b false."
  [a b]
  (if a b true))

(defn apply-repos-filters [repos]
  (let [{:keys [q group language forge license template
                with-contributing with-publiccode fork floss]}
        @(re-frame/subscribe [:filter?])]
    (->>
     repos
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
         (if language
           (some (into #{} (list (s/lower-case (or (:l %) ""))))
                 (s/split (s/lower-case language) #" +"))
           true)
         (if (= forge "") true (= (:p %) forge))
         (if-a-b-else-true group (= (:o %) group))
         (if-a-b-else-true q (s-includes? (s/join " " [(:id %) (:d %)]) q))))))))

(defn apply-orgas-filters [orgas]
  (let [{:keys [q ministry]} @(re-frame/subscribe [:filter?])]
    (->>
     orgas
     (sequence
      (filter
       #(and
         (if-a-b-else-true q
           (s-includes?
            (s/join " " [(:id %) (:d %) (:ps %) (:os %)]) q))
         (if (= ministry "") true (= (:m %) ministry))))))))

(defn not-empty-string-or-true [[_ v]]
  (or (and (boolean? v) (true? v))
      (and (string? v) (not-empty v))))

(defn start-filter-loop []
  (async/go
    (loop [f (async/<! filter-chan)]
      (let [v  @(re-frame/subscribe [:view?])
            fs @(re-frame/subscribe [:filter?])]
        (rfe/push-state v nil (filter not-empty-string-or-true (merge fs f))))
      (re-frame/dispatch [:filter! f])
      (recur (async/<! filter-chan)))))

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
    :lang          "en"
    :path          ""}))

;; Define events for each API call
(re-frame/reg-event-fx
 :fetch-repositories
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             "/data/repos_preprod.json"
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:set-repositories]
                 :on-failure      [:api-request-error :repositories]}}))

(re-frame/reg-event-fx
 :fetch-owners
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             "/data/owners.json"
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:set-owners]
                 :on-failure      [:api-request-error :owners]}}))

(re-frame/reg-event-fx
 :fetch-awesome
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             "/data/awesome.json"
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:set-awesome]
                 :on-failure      [:api-request-error :awesome]}}))

(re-frame/reg-event-fx
 :fetch-platforms
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             "/data/codegouvfr-forges.csv"
                 :response-format (ajax/raw-response-format)
                 :on-success      [:set-platforms]
                 :on-failure      [:api-request-error :platforms]}}))

(re-frame/reg-event-fx
 :fetch-stats
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             "/data/stats.json"
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:set-stats]
                 :on-failure      [:api-request-error :stats]}}))

;; Define events to handle successful responses
(re-frame/reg-event-db
 :set-repositories
 (fn [db [_ response]]
   (assoc db :repositories (map (comp bean clj->js) response))))

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

;; Define an event to handle API request errors
(re-frame/reg-event-db
 :api-request-error
 (fn [db [_ request-type response]]
   (assoc-in db [:errors request-type] response)))

;; Define other reframe events
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
   (update-in db [:filter] merge s)))

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
   (re-frame/dispatch [:filter! query-params])
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
 :ministries?
 (fn [db _] (filter not-empty (distinct (map :m (:owners db))))))

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
 :awes?
 (fn [db _] (:awesome db)))

(re-frame/reg-sub
 :platforms?
 (fn [db _] (:platforms db)))

(re-frame/reg-sub
 :repos?
 (fn [db]
   (let [repos0 (:repositories db)
         repos  (case @(re-frame/subscribe [:sort-repos-by?])
                  :forks (sort-by :f repos0)
                  :score (sort-by :a repos0)
                  :date  (sort #(compare (js/Date. (.parse js/Date (:u %1)))
                                         (js/Date. (.parse js/Date (:u %2))))
                               repos0)
                  repos0)]
     (apply-repos-filters (if @(re-frame/subscribe [:reverse-sort?])
                            repos
                            (reverse repos))))))

(re-frame/reg-sub
 :orgas?
 (fn [db]
   (let [orgs  (:owners db)
         orgas (case @(re-frame/subscribe [:sort-orgas-by?])
                 :repos       (sort-by :r orgs)
                 :floss       (sort-by :f orgs)
                 :subscribers (sort-by :s orgs)
                 ;; :date  (sort
                 ;;         #(compare
                 ;;           (js/Date. (.parse js/Date (or (:c %2) UNIX-EPOCH)))
                 ;;           (js/Date. (.parse js/Date (or (:c %1) UNIX-EPOCH))))
                 ;;         orgs)
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
  [:div.fr-grid-row.fr-grid-row--center
   [:div.fr-col-6.fr-p-2w
    [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
     [:div.fr-card__body
      [:div.fr-card__title
       [:a.fr-card__link
        {:href  (rfe/href :awes)
         :title (i/i lang [:Awesome-title])}
        (i/i lang [:Awesome])]]
      [:div.fr-card__desc (i/i lang [:Awesome-callout])]]
     [:div.fr-card__img.fr-col-3
      [:img.fr-responsive-img {:src "./img/awesome.webp" :alt ""}]]]]
   [:div.fr-col-6.fr-p-2w
    [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
     [:div.fr-card__body
      [:div.fr-card__title
       [:a.fr-card__link
        {:href  (rfe/href :repos)
         :title (i/i lang [:repos-of-source-code])}
        (i/i lang [:Repos])]]
      [:div.fr-card__desc (i/i lang [:home-repos-desc])]]
     [:div.fr-card__img.fr-col-3
      [:img.fr-responsive-img {:src "./img/repositories.webp" :alt ""}]]]]
   [:div.fr-col-6.fr-p-2w
    [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
     [:div.fr-card__body
      [:div.fr-card__title
       [:a.fr-card__link
        {:href  (rfe/href :orgas)
         :title (i/i lang [:Orgas])}
        (i/i lang [:Orgas])]]
      [:div.fr-card__desc (i/i lang [:home-orgas-desc])]]
     [:div.fr-card__img.fr-col-3
      [:img.fr-responsive-img {:src "./img/organizations.webp" :alt ""}]]]]
   [:div.fr-col-6.fr-p-2w
    [:div.fr-card.fr-card--horizontal.fr-enlarge-link.fr-card--neutral
     [:div.fr-card__body
      [:div.fr-card__title
       [:a.fr-card__link
        {:href  (rfe/href :stats)
         :title (i/i lang [:stats-expand])}
        (i/i lang [:Stats])]]
      [:div.fr-card__desc (i/i lang [:home-stats-desc])]]
     [:div.fr-card__img.fr-col-3
      [:img.fr-responsive-img {:src "./img/stats.webp" :alt ""}]]]]])

;; Main structure - repos

(defn repos-table [lang repos-cnt]
  (if (zero? repos-cnt)
    (if (zero? (count @(re-frame/subscribe [:repos?])))
      [:div.fr-m-3w [:p {:aria-live "polite"} (i/i lang [:Loading])]]
      [:div.fr-m-3w [:p {:aria-live "polite"} (i/i lang [:no-repo-found])]])
    (let [rep-f      @(re-frame/subscribe [:sort-repos-by?])
          repos-page @(re-frame/subscribe [:repos-page?])
          repos      @(re-frame/subscribe [:repos?])]
      [:div.fr-table.fr-table--no-caption
       {:role "region" :aria-label (i/i lang [:repos-of-source-code])}
       [:table
        [:caption {:id "repos-table-caption"} (i/i lang [:repos-of-source-code])]
        [:thead.fr-grid.fr-col-12
         [:tr
          [:th.fr-col {:scope "col"} (i/i lang [:Repos])]
          [:th.fr-col {:scope "col"} (i/i lang [:Orgas])]
          [:th.fr-col {:scope "col"} (i/i lang [:description])]
          [:th.fr-col-1 {:scope "col"}
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class        (when (= rep-f :date) "fr-btn--secondary")
             :title        (i/i lang [:sort-update-date])
             :on-click     #(re-frame/dispatch [:sort-repos-by! :date])
             :aria-pressed (if (= rep-f :date) "true" "false")
             :aria-label   (i/i lang [:sort-update-date])}
            (i/i lang [:update-short])]]
          [:th.fr-col-1 {:scope "col"}
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class        (when (= rep-f :forks) "fr-btn--secondary")
             :title        (i/i lang [:sort-forks])
             :on-click     #(re-frame/dispatch [:sort-repos-by! :forks])
             :aria-pressed (if (= rep-f :forks) "true" "false")
             :aria-label   (i/i lang [:sort-forks])}
            (i/i lang [:forks])]]
          [:th.fr-col-1 {:scope "col"}
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class        (when (= rep-f :score) "fr-btn--secondary")
             :title        (i/i lang [:sort-score])
             :on-click     #(re-frame/dispatch [:sort-repos-by! :score])
             :aria-pressed (if (= rep-f :score) "true" "false")
             :aria-label   (i/i lang [:sort-score])}
            (i/i lang [:Score])]]]]
        (into [:tbody]
              (for [repo (->> repos
                              (drop (* REPOS-PER-PAGE repos-page))
                              (take REPOS-PER-PAGE))]
                ^{:key (str (:o repo) "/" (:n repo))}
                (let [{:keys [d                  ; description
                              f                  ; forks_count
                              a                  ; codegouvfr "awesome" score
                              li                 ; license
                              n                  ; name
                              fn                 ; full-name
                              o                  ; owner
                              u                  ; last_update
                              p                  ; forge
                              ]} repo
                      html_url   (html-url-from-p-and-fn p fn)]
                  [:tr
                   [:td
                    [:span
                     [:a.fr-raw-link.fr-icon-terminal-box-line
                      {:title      (i/i lang [:go-to-data])
                       :target     "new"
                       :href       (str ecosystem-prefix-url
                                        (if (= p "github.com") "github" p)
                                        "/repositories/" fn)
                       :aria-label (str (i/i lang [:go-to-data]) " " n)}]
                     [:span " "]
                     [:a {:href       html_url
                          :target     "_blank"
                          :rel        "noreferrer noopener"
                          :aria-label (str n " - " (i/i lang [:go-to-repo])
                                           (when li (str " " (i/i lang [:under-license]) " " li)))}
                      n]]]
                   [:td [:a.fr-raw-link.fr-link
                         {:href       (rfe/href :repos nil {:group o})
                          :aria-label (i/i lang [:browse-repos-orga])}
                         (or (last (re-matches #".+/([^/]+)/?" o)) "")]]
                   [:td [:span {:aria-label (str (i/i lang [:description]) ": " d)} d]]
                   [:td
                    {:style {:text-align "center"}}
                    [:span
                     (if-let [d (to-locale-date u lang)]
                       [:a
                        {:href       (str swh-baseurl html_url)
                         :target     "new"
                         :title      (new-tab (i/i lang [:swh-link]) lang)
                         :rel        "noreferrer noopener"
                         :aria-label (i/i lang [:swh-link])}
                        d]
                       "N/A")]]
                   [:td {:style      {:text-align "center"}
                         :aria-label (str (i/i lang [:forks]) ": " f)} f]
                   [:td {:style      {:text-align "center"}
                         :aria-label (str (i/i lang [:Score]) ": " a)} a]])))]])))

(defn repos-page [lang license language]
  (let [repos          @(re-frame/subscribe [:repos?])
        repos-pages    @(re-frame/subscribe [:repos-page?])
        count-pages    (count (partition-all REPOS-PER-PAGE repos))
        f              @(re-frame/subscribe [:filter?])
        forge          (:forge f)
        first-disabled (zero? repos-pages)
        last-disabled  (= repos-pages (dec count-pages))
        mapping        (:repos mappings)]
    [:div
     [:div.fr-grid-row
      ;; RSS feed
      [:a.fr-raw-link.fr-link.fr-m-1w
       {:title (i/i lang [:rss-feed])
        :href  "/data/latest.xml"}
       [:span.fr-icon-rss-line {:aria-hidden true}]]
      ;; Download link
      [:button.fr-link.fr-m-1w
       {:title    (i/i lang [:download])
        :on-click (fn []
                    (download-as-csv!
                     (->> repos
                          (map #(set/rename-keys (select-keys % (keys mapping)) mapping))
                          (map #(conj % {:html_url (html-url-from-p-and-fn (:p %) (:fn %))})))
                     (str "codegouvfr-repositories-" (todays-date lang) ".csv")))}
       [:span.fr-icon-download-line {:aria-hidden true}]]
      ;; General information
      (table-header lang repos :repo)
      ;; Top pagination block
      [navigate-pagination :repos first-disabled last-disabled repos-pages count-pages]]
     ;; Specific repos search filters and options
     [:div.fr-grid-row
      [:input.fr-input.fr-col.fr-m-2w
       {:placeholder (i/i lang [:license])
        :value       (or @license (:license @(re-frame/subscribe [:filter?])))
        :on-change   (fn [e]
                       (let [ev (.-value (.-target e))]
                         (reset! license ev)
                         (async/go
                           (async/<! (async/timeout TIMEOUT))
                           (async/>! filter-chan {:license ev}))))}]
      [:input.fr-input.fr-col.fr-m-2w
       {:value       (or @language (:language @(re-frame/subscribe [:filter?])))
        :placeholder (i/i lang [:language])
        :on-change   (fn [e]
                       (let [ev (.-value (.-target e))]
                         (reset! language ev)
                         (async/go
                           (async/<! (async/timeout TIMEOUT))
                           (async/>! filter-chan {:language ev}))))}]
      [:select.fr-select.fr-col-3
       {:value (or forge "")
        :on-change
        (fn [e]
          (let [ev (.-value (.-target e))]
            (re-frame/dispatch [:filter! {:forge ev}])
            (async/go
              (async/>! filter-chan {:forge ev}))))}
       [:option#default {:value ""} (i/i lang [:all-forges])]
       (for [x @(re-frame/subscribe [:platforms?])]
         ^{:key x}
         [:option {:value x} x])]
      [:div.fr-checkbox-group.fr-col.fr-m-2w
       [:input#1 {:type "checkbox" :name "1"
                  :on-change
                  (fn [e]
                    (let [ev (.-checked (.-target e))]
                      (re-frame/dispatch [:filter! {:fork ev}])
                      (async/go
                        (async/>! filter-chan {:fork ev}))))}]
       [:label.fr-label
        {:for   "1"
         :title (i/i lang [:only-fork-title])}
        (i/i lang [:only-fork])]]
      [:div.fr-checkbox-group.fr-col.fr-m-2w
       [:input#2 {:type "checkbox" :name "2"
                  :on-change
                  (fn [e]
                    (let [ev (.-checked (.-target e))]
                      (re-frame/dispatch [:filter! {:floss ev}])
                      (async/go
                        (async/>! filter-chan {:floss ev}))))}]
       [:label.fr-label {:for "2" :title (i/i lang [:only-with-license-title])}
        (i/i lang [:only-with-license])]]
      [:div.fr-checkbox-group.fr-col.fr-m-2w
       [:input#4 {:type "checkbox" :name "4"
                  :on-change
                  (fn [e]
                    (let [ev (.-checked (.-target e))]
                      (re-frame/dispatch [:filter! {:template ev}])
                      (async/go
                        (async/>! filter-chan {:template ev}))))}]
       [:label.fr-label
        {:for "4" :title (i/i lang [:only-template-title])}
        (i/i lang [:only-template])]]
      [:div.fr-checkbox-group.fr-col.fr-m-2w
       [:input#5 {:type "checkbox" :name "5"
                  :on-change
                  (fn [e]
                    (let [ev (.-checked (.-target e))]
                      (re-frame/dispatch [:filter! {:with-contributing ev}])
                      (async/go
                        (async/>! filter-chan {:with-contributing ev}))))}]
       [:label.fr-label
        {:for "5" :title (i/i lang [:only-contrib-title])}
        (i/i lang [:only-contrib])]]
      [:div.fr-checkbox-group.fr-col.fr-m-2w
       [:input#6 {:type "checkbox" :name "6"
                  :on-change
                  (fn [e]
                    (let [ev (.-checked (.-target e))]
                      (re-frame/dispatch [:filter! {:with-publiccode ev}])
                      (async/go
                        (async/>! filter-chan {:with-publiccode ev}))))}]
       [:label.fr-label
        {:for "6" :title (i/i lang [:only-publiccode-title])}
        (i/i lang [:only-publiccode])]]]
     ;; Main repos table display
     [repos-table lang (count repos)]
     ;; Bottom pagination block
     [navigate-pagination :repos first-disabled last-disabled repos-pages count-pages]]))

;; Main structure - awesome

(defn awes-table [lang]
  (into
   [:div.fr-grid-row.fr-grid-row--gutters]
   (for [awesome (shuffle @(re-frame/subscribe [:awes?]))]
     ^{:key (:name awesome)}
     (let [{:keys [name url logo legal description fundedBy]}
           awesome
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
             [:li [:p.fr-tag (str (i/i lang [:license]) ": " (:license legal))]]]]
           [:h3.fr-card__title
            [:a {:href url} name]]
           [:p.fr-card__desc desc]
           [:div.fr-card__end
            (when (not-empty fundedBy)
              [:p.fr-card__detail.fr-icon-warning-fill
               (str (i/i lang [:Funded-by]) ": "
                    (s/join ", " (map :name fundedBy)))])]]]]]))))

(defn awes-page [lang]
  [:div.fr-container.fr-mt-6w
   [:div.fr-grid-row
    [:div.fr-col-12
     [:div.fr-callout
      [:p.fr-callout__text
       [:span
        (i/i lang [:Awesome-callout])
        " (" [:a {:href (rfe/href :releases)}
              (i/i lang [:release-check-latest])] ")"]]]
     [:div.fr-my-6w
      [awes-table lang]]]]])

;; Main structure - orgas

(defn orgas-table [lang orgas-cnt]
  (if (zero? orgas-cnt)
    (if (zero? (count @(re-frame/subscribe [:orgas?])))
      [:div.fr-m-3w [:p {:aria-live "polite"} (i/i lang [:Loading])]]
      [:div.fr-m-3w [:p {:aria-live "polite"} (i/i lang [:no-orga-found])]])
    (let [org-f @(re-frame/subscribe [:sort-orgas-by?])
          orgas @(re-frame/subscribe [:orgas?])]
      [:div.fr-table.fr-table--no-caption
       {:role "region" :aria-label (i/i lang [:Orgas])}
       [:table
        [:caption {:id "orgas-table-caption"} (i/i lang [:Orgas])]
        [:thead.fr-grid.fr-col-12
         [:tr
          [:th.fr-col-1 {:scope "col"} "Image"]
          [:th.fr-col-2 {:scope "col"} (i/i lang [:Orgas])]
          [:th.fr-col-6 {:scope "col"} (i/i lang [:description])]
          [:th.fr-col-1 {:scope "col"}
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class        (when (= org-f :repos) "fr-btn--secondary")
             :title        (i/i lang [:sort-repos])
             :on-click     #(re-frame/dispatch [:sort-orgas-by! :repos])
             :aria-pressed (if (= org-f :repos) "true" "false")
             :aria-label   (i/i lang [:sort-repos])}
            (i/i lang [:Repos])]]
          [:th.fr-col-1 {:scope "col"}
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class        (when (= org-f :subscribers) "fr-btn--secondary")
             :title        (i/i lang [:sort-subscribers])
             :on-click     #(re-frame/dispatch [:sort-orgas-by! :subscribers])
             :aria-pressed (if (= org-f :subscribers) "true" "false")
             :aria-label   (i/i lang [:sort-subscribers])}
            (i/i lang [:Subscribers])]]
          [:th.fr-col-1 {:scope "col"}
           [:button.fr-btn.fr-btn--tertiary-no-outline
            {:class        (when (= org-f :floss) "fr-btn--secondary")
             :title        (i/i lang [:sort-orgas-floss-policy])
             :on-click     #(re-frame/dispatch [:sort-orgas-by! :floss])
             :aria-pressed (if (= org-f :floss) "true" "false")
             :aria-label   (i/i lang [:sort-orgas-floss-policy])}
            (i/i lang [:floss])]]]]
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
                             {:title      (i/i lang [:orga-homepage])
                              :href       h
                              :aria-label (str (i/i lang [:orga-homepage]) " " n)}
                             [:img {:src au :width "100%" :alt (str n " " (i/i lang [:logo]))}]]
                            [:img {:src au :width "100%" :alt (str n " " (i/i lang [:logo]))}])
                          (when (not-empty h)
                            [:a.fr-raw-link.fr-link
                             {:title      (i/i lang [:orga-homepage])
                              :href       h
                              :aria-label (str (i/i lang [:orga-homepage]) " " n)}
                             (i/i lang [:website])]))]
                   [:td
                    [:span
                     [:a.fr-raw-link.fr-icon-terminal-box-line
                      {:title      (i/i lang [:go-to-data])
                       :target     "new"
                       :href       (str ecosystem-prefix-url
                                        (when-let [p (last (re-matches #"^https://([^/]+).*$" id))]
                                          (if (re-matches #"^github.*" p) "GitHub" p))
                                        "/owners/" l)
                       :aria-label (str (i/i lang [:go-to-data]) " " (or (not-empty n) l))}]
                     [:span " "]
                     [:a {:target     "_blank"
                          :rel        "noreferrer noopener"
                          :href       id
                          :aria-label (str (i/i lang [:go-to-orga]) " " (or (not-empty n) l))}
                      (or (not-empty n) l)]]]
                   [:td {:aria-label (str (i/i lang [:description]) ": " d)} d]
                   [:td
                    {:style {:text-align "center"}}
                    [:a {:title      (i/i lang [:go-to-repos])
                         :href       (rfe/href :repos nil {:group id})
                         :aria-label (str r " " (i/i lang [:repos-of]) " " (or (not-empty n) l))}
                     r]]
                   [:td {:style      {:text-align "center"}
                         :aria-label (str s " " (i/i lang [:Subscribers]))}
                    s]
                   [:td {:style {:text-align "center"}}
                    (when (not-empty f)
                      [:a {:target     "new"
                           :rel        "noreferrer noopener"
                           :href       f
                           :aria-label (str (i/i lang [:floss-policy]) " " (i/i lang [:for]) " " (or (not-empty n) l))}
                       (i/i lang [:floss])])]])))]])))

(defn orgas-page [lang]
  (let [orgas          @(re-frame/subscribe [:orgas?])
        orgas-cnt      (count orgas)
        orgas-pages    @(re-frame/subscribe [:orgas-page?])
        count-pages    (count (partition-all ORGAS-PER-PAGE orgas))
        first-disabled (zero? orgas-pages)
        last-disabled  (= orgas-pages (dec count-pages))
        mapping        (:orgas mappings)]
    [:div
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
       {:value (or (:ministry @(re-frame/subscribe [:filter?])) "")
        :on-change
        (fn [e]
          (let [ev (.-value (.-target e))]
            (re-frame/dispatch [:filter! {:ministry ev}])
            (async/go
              (async/>! filter-chan {:ministry ev}))))}
       [:option#default {:value ""} (i/i lang [:all-ministries])]
       (for [x @(re-frame/subscribe [:ministries?])]
         ^{:key x}
         [:option {:value x} x])]]
     [orgas-table lang orgas-cnt]
     [navigate-pagination :orgas first-disabled last-disabled orgas-pages count-pages]]))

;; Releases page

(defn releases-page [lang]
  (let [awes     @(re-frame/subscribe [:awes?])
        releases (flatten (map :releases awes))]
    [:div
     [:div.fr-grid-row
      ;; RSS feed
      [:a.fr-raw-link.fr-link.fr-m-1w
       {:title (i/i lang [:rss-feed])
        :href  "/data/latest-releases.xml"}
       [:span.fr-icon-rss-line {:aria-hidden true}]]
      ;; General informations
      (table-header lang releases :release)]
     ;; Main releases display
     [:div.fr-table.fr-table--no-caption
      [:table
       [:caption (i/i lang [:Releases])]
       [:thead.fr-grid.fr-col-12
        [:tr
         [:th.fr-col-1 (i/i lang [:Repo])]
         [:th.fr-col-2 (i/i lang [:description])]
         [:th.fr-col-1 (i/i lang [:Releasename])]
         [:th.fr-col-1 (i/i lang [:update-short])]]]
       (into
        [:tbody]
        (for [release (reverse (sort-by :published_at releases))]
          ^{:key (:html_url release)}
          (let [{:keys [repo_name html_url body tag_name published_at]} release]
            [:tr
             [:td
              [:a.fr-link
               {:href   html_url
                :target "_blank"
                :title  (i/i lang [:Repo])
                :rel    "noreferrer noopener"} repo_name]]
             [:td body]
             [:td tag_name]
             [:td (to-locale-date published_at lang)]])))]]]))

;; Stats page

(def color-palette ["#FF6384" "#36A2EB" "#FFCE56" "#4BC0C0" "#9966FF"])

(defn pie-chart [data {:keys [data-key name-key]}]
  [:> ResponsiveContainer
   {:width "100%" :height 400}
   [:> PieChart
    [:> Pie
     {:data        data
      :dataKey     data-key
      :nameKey     name-key
      :cx          "50%"
      :cy          "50%"
      :outerRadius 150
      :fill        "#8884d8"
      :label       true}
     (map-indexed  (fn [index _]
                     [:> Cell
                      {:key  (str "cell-" index)
                       :fill (nth color-palette index)}])
                   data)]
    [:> Tooltip]
    [:> Legend]]])

(defn scatter-chart [stats]
  [:> ResponsiveContainer {:width "100%" :height 400}
   [:> ScatterChart
    {:margin {:top 20 :right 20 :bottom 20 :left 20}}
    [:> CartesianGrid {:strokeDasharray "3 3"}]
    [:> XAxis {:type    "number"
               :dataKey "total_stars"
               :name    "Stars"
               :unit    ""
               :domain  ["dataMin - 5", "dataMax + 5"]}]
    [:> YAxis {:type    "number"
               :dataKey "repositories_count"
               :name    "Repositories"
               :unit    ""
               :domain  ["dataMin - 5", "dataMax + 5"]}]
    [:> ZAxis {:type    "category"
               :dataKey "owner"
               :name    "Organization"}]
    [:> Tooltip {:cursor {:strokeDasharray "3 3"}}]
    [:> Legend]
    [:> Scatter {:name "Repositories vs Starts"
                 :data (:top_orgs_repos_stars stats)
                 :fill "#8884d8"}]]])

(defn languages-chart [lang stats]
  (let [data (for [[k v] (take 5 (:top_languages stats))]
               {:language k :percentage v})]
    [:div.fr-col-6
     [:span.fr-h4 (i/i lang [:most-used-languages])]
     (pie-chart data {:data-key "percentage" :name-key "language"})]))

(defn licenses-chart [lang stats]
  (let [data (for [[k v] (take 5 (:top_licenses stats))]
               {:license k :percentage v})]
    [:div.fr-col-6
     [:span.fr-h4 (i/i lang [:most-used-licenses])]
     (pie-chart data {:data-key "percentage" :name-key "license"})]))

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

(defn stats-page [lang]
  (let [stats
        @(re-frame/subscribe [:stats?])
        {:keys [top_orgs_by_repos top_orgs_by_stars]} stats]
    [:div.fr-grid
     [:div.fr-grid-row.fr-grid-row--center.fr-m-3w
      [languages-chart lang stats]
      [licenses-chart lang stats]]
     [:div.fr-grid-row
      [:div.fr-col-6.fr-grid-row.fr-grid-row--center
       (stats-table [:span
                     (i/i lang [:Orgas])
                     (i/i lang [:with-more-of])
                     (i/i lang [:repos])]
                    (top-clean-up-orgas top_orgs_by_repos "q")
                    [:thead [:tr [:th.fr-col-10 (i/i lang [:Orgas])]
                             [:th (i/i lang [:Repos])]]])]
      [:div.fr-col-6.fr-grid-row.fr-grid-row--center
       (stats-table [:span (i/i lang [:most-starred-orgas])]
                    (top-clean-up-orgas top_orgs_by_stars "q")
                    [:thead [:tr [:th.fr-col-10 (i/i lang [:Orgas])]
                             [:th (i/i lang [:Stars])]]])]]
     [:div.fr-grid-row.fr-grid-row--center.fr-m-3w
      [scatter-chart stats]]]))

;; Main structure elements

(def q (reagent/atom nil))
(def license (reagent/atom nil))
(def language (reagent/atom nil))
(def forge (reagent/atom nil))

(defn reset-queries []
  (reset! q nil)
  (reset! license nil)
  (reset! language nil)
  (reset! forge "")
  (re-frame/dispatch [:filter! {:q "" :license "" :language "" :forge ""}]))

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
          [:a {:href "https://code.gouv.fr"}
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
           [:li [:a.fr-link {:href (rfe/href :feeds)} (i/i lang [:rss-feed])]]
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
            #(do (reset-queries) (rfe/push-state :awes))}
           "Awesome"]]
         [:li.fr-nav__item
          [:button.fr-nav__link
           {:aria-current (when (= path "/repos") "page")
            :title        (i/i lang [:repos-of-source-code])
            :on-click
            #(do (reset-queries) (rfe/push-state :repos))}
           (i/i lang [:Repos])]]
         [:li.fr-nav__item
          [:button.fr-nav__link
           {:aria-current (when (= path "/groups") "page")
            :on-click
            #(do (reset-queries) (rfe/push-state :orgas))}
           (i/i lang [:Orgas])]]
         [:li.fr-nav__item
          [:a.fr-nav__link
           {:aria-current (when (= path "/stats") "page")
            :title        (i/i lang [:stats-expand])
            :href         (rfe/href :stats)}
           (i/i lang [:Stats])]]
         [:li.fr-nav__item
          [:a.fr-nav__link
           {:aria-current (when (= path "/about") "page")
            :href         (rfe/href :about)}
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
     [:div.fr-col-12.fr-col-md-4
      [:div.fr-follow__newsletter
       [:div
        [:h1.fr-h5.fr-follow__title (i/i lang [:bluehats])]
        [:p.fr-text--sm.fr-follow__desc
         (i/i lang [:bluehats-desc])]
        [:a.fr-btn
         {:type "button"
          :href "https://code.gouv.fr/newsletters"}
         (i/i lang [:subscribe])]]]]
     [:div.fr-col-12.fr-col-md-4
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
         :on-click #(re-frame/dispatch [:lang! (if (= lang "fr") "en" "fr")])}
        (i/i lang [:switch-lang])]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href (rfe/href :a11y)}
        (i/i lang [:accessibility])]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href (rfe/href :legal)}
        (i/i lang [:legal])]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href (rfe/href :legal)}
        (i/i lang [:personal-data])]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href (rfe/href :sitemap)}
        (i/i lang [:sitemap])]]
      [:li.fr-footer__bottom-item
       [:a.fr-footer__bottom-link
        {:href  (rfe/href :feeds)
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
         :value       (or @q (:q @(re-frame/subscribe [:filter?])))
         :on-change   (fn [e]
                        (let [ev (.-value (.-target e))]
                          (reset! q ev)
                          (async/go
                            (async/<! (async/timeout TIMEOUT))
                            (async/>! filter-chan {:q ev}))))}])]
    (when-let [flt (-> @(re-frame/subscribe [:filter?])
                       (dissoc :fork :with-publiccode :with-contributing
                               :template :floss))]
      [:div.fr-col-8.fr-grid-row.fr-m-1w
       (when-let [ff (not-empty (:group flt))]
         [:span
          [:button.fr-link.fr-icon-close-circle-line.fr-link--icon-right
           {:title    (i/i lang [:remove-filter])
            :on-click #(do (re-frame/dispatch [:filter! {:group nil}])
                           (rfe/push-state :repos))}
           [:span ff]]])])]])

(defn main-page []
  (let [lang @(re-frame/subscribe [:lang?])
        view @(re-frame/subscribe [:view?])]
    [:div
     (banner lang)
     [:main#main.fr-container.fr-container--fluid.fr-mb-3w
      {:role "main"}
      [main-menu lang view]
      (condp = view
        :home     [home-page lang]
        :orgas    [orgas-page lang]
        :repos    [repos-page lang license language]
        :releases [releases-page lang]
        :awes     [awes-page lang]
        :stats    [stats-page lang]
        :legal    (condp = lang "fr" (inline-page "legal.fr.md")
                         (inline-page "legal.en.md"))
        :a11y     (condp = lang "fr" (inline-page "a11y.fr.md")
                         (inline-page "a11y.en.md"))
        :sitemap  (condp = lang "fr" (inline-page "sitemap.fr.md")
                         (inline-page "sitemap.en.md"))
        :feeds    (condp = lang "fr" (inline-page "feeds.fr.md")
                         (inline-page "feeds.en.md"))
        :about    (condp = lang "fr" (inline-page "about.fr.md")
                         (inline-page "about.en.md"))
        nil)]
     (subscribe lang)
     (footer lang)
     (display-parameters-modal lang)]))

;; Setup router and init

(defn on-navigate [match]
  (let [title-prefix  "code.gouv.fr ─ "
        title-default "Codes sources du secteur public ─ Source code from the French public sector"
        page          (keyword (:name (:data match)))]
    ;; Rely on the server to handle /not-found as a 404
    (when (not (seq match)) (set! (.-location js/window) "/not-found"))
    (set! (. js/document -title)
          (str title-prefix
               (case page
                 :awes     "Awesome"
                 :orgas    "Organisations ─ Organizations"
                 :repos    "Dépôts de code source ─ Source code repositories"
                 :home     title-default
                 :legal    "Mentions légales ─ Legal mentions"
                 :releases "Versions"
                 :stats    "Chiffres ─ Stats"
                 :a11y     "Accessibilité ─ Accessibility"
                 :feeds    "Flux RSS ─ RSS Feeds"
                 :sitemap  "Pages du site ─ Sitemap"
                 :about    "À propos ─ About"
                 nil)))
    (re-frame/dispatch [:path! (:path match)])
    (re-frame/dispatch [:view! page (:query-params match)])))

(defonce routes
  ["/"
   ["" :home]
   ["groups" :orgas]
   ["repos" :repos]
   ["awesome" :awes]
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
  (let [browser-lang (subs (or js/navigator.language "en") 0 2)]
    (re-frame/dispatch
     [:lang!
      (if (contains? i/supported-languages browser-lang)
        browser-lang
        "en")]))
  (rfe/start! (rf/router routes {:conflicts nil})
              on-navigate
              {:use-fragment true})
  (start-filter-loop)
  (when (nil? @root)
    (reset! root (rdc/create-root (js/document.getElementById "app"))))
  (rdc/render @root [main-page]))
