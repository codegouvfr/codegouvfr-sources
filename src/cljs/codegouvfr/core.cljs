;; Copyright (c) 2019-2020 DINUM, Bastien Guerry <bastien.guerry@data.gouv.fr>
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
            [taoensso.sente  :as sente]))

(def ?csrf-token
  (when-let [el (.getElementById js/document "sente-csrf-token")]
    (.getAttribute el "data-csrf-token")))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ?csrf-token {:type :auto})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(defn event-msg-handler [{:keys [event]}]
  ;; (.log js/console (pr-str event)) ; FIXME
  (let [recv (:chsk/recv (apply hash-map event))
        push (:event/PushEvent (apply hash-map recv))]
    (when (not-empty (:u push))
      (println push)
      (re-frame/dispatch [:levent! push]))))

(defonce dev? false)
(defonce repos-per-page 100) ;; FIXME: Make customizable?
(defonce orgas-per-page 100) ;; FIXME: Make customizable?
(defonce deps-per-page 100) ;; FIXME: Make customizable?
(defonce timeout 100)
(defonce init-filter {:q nil :g nil :d nil :language nil :license nil})
(defonce annuaire-prefix "https://lannuaire.service-public.fr/")
(defonce repos-csv-url "https://www.data.gouv.fr/fr/datasets/r/54a38a62-411f-4ea7-9631-ae78d1cef34c")
(defonce orgas-csv-url "https://www.data.gouv.fr/fr/datasets/r/79f8975b-a747-445c-85d0-2cf707e12200")
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
    :dep-repos      nil
    :levent         nil
    :repos-page     0
    :orgas-page     0
    :deps-page      0
    :sort-repos-by  :reused
    :sort-orgas-by  :repos
    :sort-deps-by   :production
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
 :levent?
 (fn [db _] (:levent db)))

(re-frame/reg-event-db
 :levent!
 (fn [db [_ le]] (update-in db [:levent] conj le)))

(re-frame/reg-sub
 :path-params?
 (fn [db _] (:path-params db)))

(re-frame/reg-event-db
 :update-repos!
 (fn [db [_ repos]] (assoc db :repos repos)))

(re-frame/reg-event-db
 :update-deps!
 (fn [db [_ deps]] (assoc db :deps deps)))

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

(defn apply-repos-filters [m]
  (let [f   @(re-frame/subscribe [:filter?])
        s   (:q f)
        g   (:g f)
        la  (:language f)
        lic (:license f)
        de  (:has-description f)
        fk  (:is-fork f)
        ar  (:is-archive f)
        li  (:is-licensed f)
        h   (:include-html-repos f)]
    (filter
     #(and (if fk (:f? %) true)
           (if ar (not (:a? %)) true)
           (if li (let [l (:li %)] (and l (not= l "Other"))) true)
           (if lic (s-includes? (:li %) lic) true)
           (if la (s-includes? (:l %) la)
               (if h true (not (s-includes? (:l %) "HTML"))))
           (if h true (not (s-includes? (:l %) "HTML")))
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
     #(and (if s (s-includes? (s/join " " [(:n %) (:t %)]) s)
               true))
     m)))

(defn apply-orgas-filters [m]
  (let [f  @(re-frame/subscribe [:filter?])
        s  (:q f)
        de (:has-description f)
        re (:has-at-least-one-repo f)]
    (filter
     #(and (if de (seq (:d %)) true)
           (if re (pos? (:r %)) true)
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
 :dep-repos?
 (fn [db _]
   (let [repos0 (:dep-repos db)
         favs   (get-item :favs)
         repos  (case @(re-frame/subscribe [:sort-repos-by?])
                  :name   (reverse (sort-by :n repos0))
                  :forks  (sort-by :f repos0)
                  :stars  (sort-by :s repos0)
                  :issues (sort-by :i repos0)
                  :reused (sort-by :g repos0)
                  :favs   (concat (filter #(not-any? #{(:n %)} favs) repos0)
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
 :repos?
 (fn [db _]
   (let [repos0 (:repos db)
         favs   (get-item :favs)
         repos  (case @(re-frame/subscribe [:sort-repos-by?])
                  :name   (reverse (sort-by :n repos0))
                  :forks  (sort-by :f repos0)
                  :stars  (sort-by :s repos0)
                  :issues (sort-by :i repos0)
                  :reused (sort-by :g repos0)
                  :favs   (concat (filter #(not-any? #{(:n %)} favs) repos0)
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
 :deps?
 (fn [db _]
   (let [deps0 (:deps db)
         deps  (case @(re-frame/subscribe [:sort-deps-by?])
                 :name        (reverse (sort-by :n deps0))
                 :type        (reverse (sort-by :t deps0))
                 :production  (sort-by :c deps0)
                 :development (sort-by :d deps0)
                 deps0)]
     (apply-deps-filters
      (if @(re-frame/subscribe [:reverse-sort?])
        deps (reverse deps))))))

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

(defn repositories-page [lang repos-cnt]
  (if (zero? repos-cnt)
    [:div [:p (i/i lang [:no-repo-found])] [:br]]
    (let [rep-f      @(re-frame/subscribe [:sort-repos-by?])
          repos-page @(re-frame/subscribe [:repos-page?])
          repos      @(re-frame/subscribe [:repos?])]
      [:div.table-container
       [:table.table.is-hoverable.is-fullwidth
        [:thead
         [:tr
          [:th [:abbr
                [:a {:class    (when-not (= rep-f :favs) "has-text-grey")
                     :title    (i/i lang [:fav-sort])
                     :on-click #(re-frame/dispatch [:sort-repos-by! :favs])}
                 (fa "fa-star")]]]
          [:th.has-text-left
           [:abbr
            [:a.button {:class    (when (= rep-f :name) "is-light")
                        :title    (i/i lang [:sort-repos-alpha])
                        :on-click #(re-frame/dispatch [:sort-repos-by! :name])}
             (i/i lang [:orga-repo])]]]
          [:th.has-text-centered
           [:abbr
            [:a.button.is-static {:title (i/i lang [:swh-link])}
             (i/i lang [:archive])]]]
          [:th.has-text-left
           [:abbr
            [:a.button {:class    (when (= rep-f :desc) "is-light")
                        :title    (i/i lang [:sort-description-length])
                        :on-click #(re-frame/dispatch [:sort-repos-by! :desc])}
             (i/i lang [:description])]]]
          [:th.has-text-right
           [:abbr
            [:a.button {:class    (when (= rep-f :date) "is-light")
                        :title    (i/i lang [:sort-update-date])
                        :on-click #(re-frame/dispatch [:sort-repos-by! :date])}
             (i/i lang [:update-short])]]]
          [:th.has-text-right
           [:abbr
            [:a.button {:class    (when (= rep-f :forks) "is-light")
                        :title    (i/i lang [:sort-forks])
                        :on-click #(re-frame/dispatch [:sort-repos-by! :forks])}
             (i/i lang [:forks])]]]
          [:th.has-text-right
           [:abbr
            [:a.button {:class    (when (= rep-f :stars) "is-light")
                        :title    (i/i lang [:sort-stars])
                        :on-click #(re-frame/dispatch [:sort-repos-by! :stars])}
             (i/i lang [:stars])]]]
          [:th.has-text-right
           [:abbr
            [:a.button {:class    (when (= rep-f :issues) "is-light")
                        :title    (i/i lang [:sort-issues])
                        :on-click #(re-frame/dispatch [:sort-repos-by! :issues])}
             (i/i lang [:issues])]]]
          [:th.has-text-right
           [:abbr
            [:a.button {:class    (when (= rep-f :reused) "is-light")
                        :title    (i/i lang [:sort-reused])
                        :on-click #(re-frame/dispatch [:sort-repos-by! :reused])}
             (i/i lang [:reused])]]]]]
        (into [:tbody]
              (for [dd (take repos-per-page
                             (drop (* repos-per-page repos-page) repos))]
                ^{:key dd}
                (let [{:keys [a? d f i li n o r s u dp g]}
                      dd
                      group (subs r 0 (- (count r) (inc (count n))))]
                  [:tr
                   ;; Favorite star
                   [:td [favorite lang n]]
                   ;; Repo < orga
                   [:td [:div
                         [:a {:href   r
                              :target "new"
                              :title  (str (i/i lang [:go-to-repo])
                                           (when li (str (i/i lang [:under-license]) li)))}
                          n]
                         " < "
                         [:a {:href  (rfe/href :repos {:lang lang} {:g group})
                              :title (i/i lang [:browse-repos-orga])}
                          o]]]
                   ;; SWH link
                   [:td.has-text-centered
                    [:a {:href   (str "https://archive.softwareheritage.org/browse/origin/" r)
                         :title  (i/i lang [:swh-link])
                         :target "new"}
                     [:img {:width "18px" :src "/images/swh-logo.png"}]]]
                   ;; Description
                   [:td {:class (when a? "has-text-grey")
                         :title (when a? (i/i lang [:repo-archived]))}
                    [:span
                     (when dp
                       [:span
                        [:a.has-text-grey
                         {:title (i/i lang [:Deps])
                          :href  (rfe/href
                                  :repo-deps
                                  {:lang lang
                                   :orga o ;; (s/replace o "https://github.com/" "")
                                   :repo n})}
                         (fa "fa-cubes")]
                        " "]) d]]
                   ;; Update
                   [:td (or (to-locale-date u) "N/A")]
                   ;; Forks
                   [:td.has-text-right f]
                   ;; Stars
                   [:td.has-text-right s]
                   ;; Issues
                   [:td.has-text-right i]
                   ;; Reused
                   [:td.has-text-right g]])))]])))

(defn navigate-pagination [type first-disabled last-disabled]
  [:nav.level-item {:role "navigation" :aria-label "pagination"}
   [:a.pagination-previous
    {:on-click #(change-page type "first")
     :disabled first-disabled}
    (fa "fa-fast-backward")]
   [:a.pagination-previous
    {:on-click #(change-page type nil)
     :disabled first-disabled}
    (fa "fa-step-backward")]
   [:a.pagination-next
    {:on-click #(change-page type true)
     :disabled last-disabled}
    (fa "fa-step-forward")]
   [:a.pagination-next
    {:on-click #(change-page type "last")
     :disabled last-disabled}
    (fa "fa-fast-forward")]])

(defn repos-page [lang license language]
  (let [repos          @(re-frame/subscribe [:repos?])
        repos-pages    @(re-frame/subscribe [:repos-page?])
        count-pages    (count (partition-all repos-per-page repos))
        first-disabled (zero? repos-pages)
        last-disabled  (= repos-pages (dec count-pages))]
    [:div
     [:div.level-left
      [:div.dropdown.level-item.is-hoverable
       [:div.dropdown-trigger
        [:button.button {:aria-haspopup true :aria-controls "dropdown-menu3"}
         [:span (i/i lang [:options])] (fa "fa-angle-down")]]
       [:div.dropdown-menu {:role "menu" :id "dropdown-menu3"}
        [:div.dropdown-content
         [:div.dropdown-item
          [:label.checkbox.level
           [:input {:type      "checkbox"
                    :checked   (get-item :is-fork)
                    :on-change #(let [v (.-checked (.-target %))]
                                  (set-item! :is-fork v)
                                  (re-frame/dispatch [:filter! {:is-fork v}]))}]
           (i/i lang [:only-forks])]]
         [:div.dropdown-item
          [:label.checkbox.level {:title (i/i lang [:no-archived-repos])}
           [:input {:type      "checkbox"
                    :checked   (get-item :is-archive)
                    :on-change #(let [v (.-checked (.-target %))]
                                  (set-item! :is-archive v)
                                  (re-frame/dispatch [:filter! {:is-archive v}]))}]
           (i/i lang [:no-archives])]]
         [:div.dropdown-item
          [:label.checkbox.level {:title (i/i lang [:only-with-description-repos])}
           [:input {:type      "checkbox"
                    :checked   (get-item :has-description)
                    :on-change #(let [v (.-checked (.-target %))]
                                  (set-item! :has-description v)
                                  (re-frame/dispatch [:filter! {:has-description v}]))}]
           (i/i lang [:with-description])]]
         [:div.dropdown-item
          [:label.checkbox.level {:title (i/i lang [:only-with-license])}
           [:input {:type      "checkbox"
                    :checked   (get-item :is-licensed)
                    :on-change #(let [v (.-checked (.-target %))]
                                  (set-item! :is-licensed v)
                                  (re-frame/dispatch [:filter! {:is-licensed v}]))}]
           (i/i lang [:with-license])]]
         [:div.dropdown-item
          [:label.checkbox.level {:title (i/i lang [:with-html])}
           [:input {:type      "checkbox"
                    :checked   (get-item :include-html-repos)
                    :on-change #(let [v (.-checked (.-target %))]
                                  (set-item! :include-html-repos v)
                                  (re-frame/dispatch [:filter! {:include-html-repos v}]))}]
           (i/i lang [:with-html])]]]]]
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
                            (async/<! (async/timeout timeout))
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
                            (async/<! (async/timeout timeout))
                            (async/>! filter-chan {:language ev}))))}]]

      [:span.button.is-static.level-item
       (let [rps (count repos)]
         (if (< rps 2)
           (str rps (i/i lang [:repo]))
           (str rps (i/i lang [:repos]))))]
      [navigate-pagination :repos first-disabled last-disabled]
      [:a.level-item {:title (i/i lang [:download])
                      :href  repos-csv-url}
       (fa "fa-file-csv")]]
     [:br]
     [repositories-page lang (count repos)]
     [:br]]))

(defn organizations-page [lang]
  (let [org-f          @(re-frame/subscribe [:sort-orgas-by?])
        orgas          @(re-frame/subscribe [:orgas?])
        orgs-cnt       (count orgas)
        orgas-pages    @(re-frame/subscribe [:orgas-page?])
        count-pages    (count (partition-all orgas-per-page orgas))
        first-disabled (zero? orgas-pages)
        last-disabled  (= orgas-pages (dec count-pages))]
    [:div
     [:div.level-left
      [:label.checkbox.level-item {:title (i/i lang [:only-orga-with-code])}
       [:input {:type      "checkbox"
                :checked   (get-item :has-at-least-one-repo)
                :on-change #(let [v (.-checked (.-target %))]
                              (set-item! :has-at-least-one-repo v)
                              (re-frame/dispatch [:filter! {:has-at-least-one-repo v}]))}]
       (i/i lang [:with-code])]
      [:a.button.level-item
       {:class    (str "is-" (if (= org-f :name) "info is-light" "light"))
        :title    (i/i lang [:sort-orgas-alpha])
        :on-click #(re-frame/dispatch [:sort-orgas-by! :name])} (i/i lang [:sort-alpha])]
      [:a.button.level-item
       {:class    (str "is-" (if (= org-f :repos) "info is-light" "light"))
        :title    (i/i lang [:sort-repos])
        :on-click #(re-frame/dispatch [:sort-orgas-by! :repos])} (i/i lang [:sort-repos])]
      [:a.button.level-item
       {:class    (str "is-" (if (= org-f :date) "info is-light" "light"))
        :title    (i/i lang [:sort-orgas-creation])
        :on-click #(re-frame/dispatch [:sort-orgas-by! :date])} (i/i lang [:sort-creation])]
      [:span.button.is-static.level-item
       (let [orgs (count orgas)]
         (if (< orgs 2)
           (str orgs (i/i lang [:one-group]))
           (str orgs (i/i lang [:groups]))))]
      [navigate-pagination :orgas first-disabled last-disabled]
      [:a {:title (i/i lang [:download])
           :href  orgas-csv-url}
       (fa "fa-file-csv")]]
     [:br]
     (into
      [:div]
      (if (zero? orgs-cnt)
        [[:p (i/i lang [:no-orga-found])] [:br]]
        (for [dd (partition-all
                  3
                  (take orgas-per-page
                        (drop (* orgas-per-page @(re-frame/subscribe [:orgas-page?]))
                              @(re-frame/subscribe [:orgas?]))))]
          ^{:key dd}
          [:div.columns
           (for [{:keys [n l o h c d r e au p an dp] :as oo} dd]
             ^{:key oo}
             [:div.column.is-4
              [:div.card
               [:div.card-content
                [:div.media
                 (when au
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
                    (when date
                      [:p (str (i/i lang [:created-at]) date)]))
                  (when r
                    ;; FIXME: hackish, orgas-mapping should give
                    ;; the forge base on top of "plateforme".
                    [:a {:title (i/i lang [:go-to-repos])
                         :href  (rfe/href :repos {:lang lang}
                                          {:g (s/replace o "/groups/" "/")})}
                     r (if (< r 2)
                         (i/i lang [:repo])
                         (i/i lang [:repos]))])]]
                [:div.content
                 [:p d]]]
               [:div.card-footer
                (when dp
                  [:a.card-footer-item
                   {:title (i/i lang [:Deps])
                    :href  (rfe/href
                            :orga-deps
                            {:lang lang
                             :orga (s/replace o "https://github.com/" "")})}
                   (fa "fa-cubes")])
                (when e [:a.card-footer-item
                         {:title (i/i lang [:contact-by-email])
                          :href  (str "mailto:" e)}
                         (fa "fa-envelope")])
                (when h [:a.card-footer-item
                         {:title  (i/i lang [:go-to-website])
                          :target "new"
                          :href   h} (fa "fa-globe")])
                (when an [:a.card-footer-item
                          {:title  (i/i lang [:go-to-sig-website])
                           :target "new"
                           :href   (str annuaire-prefix an)}
                          (fa "fa-link")])]]])])))]))

(defn deps-dep-page [lang d]
  (let [rep-f @(re-frame/subscribe [:sort-repos-by?])
        repos @(re-frame/subscribe [:dep-repos?])
        q     (:q @(re-frame/subscribe [:filter?]))
        cnt   (count repos)
        title (str cnt (if (= cnt 1)
                         (i/i lang [:repo-depending-on])
                         (i/i lang [:repos-depending-on]))
                   (gstring/urlDecode d)
                   (when q (str (if (= cnt 1)
                                  (i/i lang [:matching])
                                  (i/i lang [:matching-s]))
                                "\"" q "\"")))]
    [:div
     [:h2 title]
     [:br]
     [:div.table-container
      [:table.table.is-hoverable.is-fullwidth
       [:thead
        [:tr
         [:th [:abbr
               [:a {:class    (when-not (= rep-f :favs) "has-text-grey")
                    :title    (i/i lang [:fav-sort])
                    :on-click #(re-frame/dispatch [:sort-repos-by! :favs])}
                (fa "fa-star")]]]
         [:th.has-text-left
          [:abbr
           [:a.button {:class    (when (= rep-f :name) "is-light")
                       :title    (i/i lang [:sort-repos-alpha])
                       :on-click #(re-frame/dispatch [:sort-repos-by! :name])}
            (i/i lang [:orga-repo])]]]
         [:th.has-text-centered
          [:abbr
           [:a.button.is-static {:title (i/i lang [:swh-link])}
            (i/i lang [:archive])]]]
         [:th.has-text-left
          [:abbr
           [:a.button {:class    (when (= rep-f :desc) "is-light")
                       :title    (i/i lang [:sort-description-length])
                       :on-click #(re-frame/dispatch [:sort-repos-by! :desc])}
            (i/i lang [:description])]]]
         [:th.has-text-right
          [:abbr
           [:a.button {:class    (when (= rep-f :date) "is-light")
                       :title    (i/i lang [:sort-update-date])
                       :on-click #(re-frame/dispatch [:sort-repos-by! :date])}
            (i/i lang [:update-short])]]]
         [:th.has-text-right
          [:abbr
           [:a.button {:class    (when (= rep-f :forks) "is-light")
                       :title    (i/i lang [:sort-forks])
                       :on-click #(re-frame/dispatch [:sort-repos-by! :forks])}
            (i/i lang [:forks])]]]
         [:th.has-text-right
          [:abbr
           [:a.button {:class    (when (= rep-f :stars) "is-light")
                       :title    (i/i lang [:sort-stars])
                       :on-click #(re-frame/dispatch [:sort-repos-by! :stars])}
            (i/i lang [:stars])]]]
         [:th.has-text-right
          [:abbr
           [:a.button {:class    (when (= rep-f :issues) "is-light")
                       :title    (i/i lang [:sort-issues])
                       :on-click #(re-frame/dispatch [:sort-repos-by! :issues])}
            (i/i lang [:issues])]]]
         [:th.has-text-right
          [:abbr
           [:a.button {:class    (when (= rep-f :reused) "is-light")
                       :title    (i/i lang [:sort-reused])
                       :on-click #(re-frame/dispatch [:sort-repos-by! :reused])}
            (i/i lang [:reused])]]]]]
       (into [:tbody]
             (for [dd repos]
               ^{:key dd}
               (let [{:keys [a? d f i li n o r s u dp g]}
                     dd
                     group (subs r 0 (- (count r) (inc (count n))))]
                 [:tr
                  ;; Favorite star
                  [:td [favorite lang n]]
                  ;; Repo < orga
                  [:td [:div
                        [:a {:href   r
                             :target "new"
                             :title  (str (i/i lang [:go-to-repo])
                                          (when li (str (i/i lang [:under-license]) li)))}
                         n]
                        " < "
                        [:a {:href  (rfe/href :repos {:lang lang} {:g group})
                             :title (i/i lang [:browse-repos-orga])}
                         o]]]
                  ;; SWH link
                  [:td.has-text-centered
                   [:a {:href   (str "https://archive.softwareheritage.org/browse/origin/" r)
                        :title  (i/i lang [:swh-link])
                        :target "new"}
                    [:img {:width "18px" :src "/images/swh-logo.png"}]]]
                  ;; Description
                  [:td {:class (when a? "has-text-grey")
                        :title (when a? (i/i lang [:repo-archived]))}
                   [:span
                    (when dp
                      [:span
                       [:a.has-text-grey
                        {:title (i/i lang [:Deps])
                         :href  (rfe/href
                                 :repo-deps
                                 {:lang lang
                                  :orga o
                                  :repo n})}
                        (fa "fa-cubes")]
                       " "]) d]]
                  ;; Update
                  [:td (or (to-locale-date u) "N/A")]
                  ;; Forks
                  [:td.has-text-right f]
                  ;; Stars
                  [:td.has-text-right s]
                  ;; Issues
                  [:td.has-text-right i]
                  ;; Reused
                  [:td.has-text-right g]])))]]]))

(defn deps-table [lang]
  (let [dep-f @(re-frame/subscribe [:sort-deps-by?])]
    [:div.table-container
     [:table.table.is-hoverable.is-fullwidth
      [:thead
       [:tr
        [:th
         [:abbr
          [:a.button
           {:class    (when (= dep-f :name) "is-light")
            :on-click #(re-frame/dispatch [:sort-deps-by! :name])}
           (i/i lang [:name])]]]
        [:th
         [:abbr
          [:a.button
           {:class    (when (= dep-f :type) "is-light")
            :on-click #(re-frame/dispatch [:sort-deps-by! :type])}
           (i/i lang [:type])]]]
        [:th.has-text-right
         [:abbr
          [:a.button
           {:class    (when (= dep-f :production) "is-light")
            :on-click #(re-frame/dispatch [:sort-deps-by! :production])}
           (i/i lang [:core-dep])]]]
        [:th.has-text-right
         [:abbr
          [:a.button
           {:class    (when (= dep-f :development) "is-light")
            :on-click #(re-frame/dispatch [:sort-deps-by! :development])}
           (i/i lang [:dev-dep])]]]]]
      (into [:tbody]
            (for [dd (take deps-per-page
                           (drop (* deps-per-page @(re-frame/subscribe [:deps-page?]))
                                 @(re-frame/subscribe [:deps?])))]
              ^{:key dd}
              (let [{:keys [t n c d]} dd]
                [:tr
                 [:td [:a {:href (rfe/href :deps {:lang lang} {:d (gstring/urlEncode n)})} n]]
                 [:td t]
                 [:td.has-text-right c]
                 [:td.has-text-right d]])))]]))

(defn deps-dep-page-class [lang d]
  (reagent/create-class
   {:display-name   "deps-dep-page-class"
    :component-did-mount
    (fn []
      (GET (str "/deps/" d)
           :handler
           #(re-frame/dispatch
             [:update-dep-repos! (map (comp bean clj->js) %)])))
    :reagent-render (fn [] (deps-dep-page lang d))}))

(defn deps-page [lang]
  (let [flt            @(re-frame/subscribe [:filter?])
        single-dep     (:d flt)
        deps           @(re-frame/subscribe [:deps?])
        deps-pages     @(re-frame/subscribe [:deps-page?])
        count-pages    (count (partition-all deps-per-page deps))
        first-disabled (zero? deps-pages)
        last-disabled  (= deps-pages (dec count-pages))
        dep-f          @(re-frame/subscribe [:sort-deps-by?])]
    [:div
     (when-not single-dep
       [:div.level-left
        [:a.button.level-item
         {:class    (str "is-" (if (= dep-f :name) "info is-light" "light"))
          :title    (i/i lang [:sort-name])
          :on-click #(re-frame/dispatch [:sort-deps-by! :name])} (i/i lang [:name])]
        [:a.button.level-item
         {:class    (str "is-" (if (= dep-f :type) "info is-light" "light"))
          :title    (i/i lang [:sort-type])
          :on-click #(re-frame/dispatch [:sort-deps-by! :type])} (i/i lang [:type])]
        [:a.button.level-item
         {:class    (str "is-" (if (= dep-f :production) "info is-light" "light"))
          :title    (i/i lang [:sort-production])
          :on-click #(re-frame/dispatch [:sort-deps-by! :production])} (i/i lang [:core-dep])]
        [:a.button.level-item
         {:class    (str "is-" (if (= dep-f :development) "info is-light" "light"))
          :title    (i/i lang [:sort-development])
          :on-click #(re-frame/dispatch [:sort-deps-by! :development])} (i/i lang [:dev-dep])]
        [:span.button.is-static.level-item
         (let [deps (count deps)]
           (if (< deps 2)
             (str deps (i/i lang [:dep]))
             (str deps (i/i lang [:deps]))))]
        [navigate-pagination :deps first-disabled last-disabled]])
     [:br]
     (if single-dep
       [deps-dep-page-class lang single-dep]
       [deps-table lang])
     [:br]]))

(defn repos-page-class [lang license language]
  (reagent/create-class
   {:display-name   "repos-page-class"
    :component-did-mount
    (fn []
      (GET "/repos"
           :handler
           #(re-frame/dispatch
             [:update-repos! (map (comp bean clj->js) %)])))
    :reagent-render (fn [] (repos-page lang license language))}))

(defn deps-page-class [lang]
  (reagent/create-class
   {:display-name   "deps-page-class"
    :component-did-mount
    (fn []
      (GET "/deps"
           :handler
           #(re-frame/dispatch
             [:update-deps! (map (comp bean clj->js) %)])))
    :reagent-render (fn [] (deps-page lang))}))

(defn figure [heading title]
  [:div.column
   [:div.has-text-centered
    [:div
     [:p.heading heading]
     [:p.title (str title)]]]])

(defn stats-card [heading data & [thead]]
  [:div.column
   [:div.card
    [:h1.card-header-title.subtitle heading]
    [:div.card-content
     [:table.table.is-fullwidth
      thead
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
         [:tr
          [:td t]
          [:td [:a {:title (i/i lang [:list-repos-depending-on-dep])
                    :href  (rfe/href :deps {:lang lang} {:d (gstring/urlEncode n)})} n]]
          [:td rs]])]]]]])

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
        (take 10 (top-clean-up (-> top_licenses
                                   (dissoc :Inconnue)
                                   (dissoc :Other))
                               lang "license"
                               (i/i lang [:list-repos-using-license])))]
    [:div
     [:div.columns
      (figure (i/i lang [:repos-of-source-code]) nb_repos)
      (figure (i/i lang [:orgas-or-groups]) nb_orgs)
      (figure (i/i lang [:mean-repos-by-orga]) avg_nb_repos)
      (figure (i/i lang [:median-repos-by-orga]) median_nb_repos)
      (figure (i/i lang [:deps-stats]) (:deps-total deps-total))]
     [:br]
     [:div.columns
      (stats-card [:span (i/i lang [:most-used-languages])]
                  top_languages_1
                  [:thead [:tr [:th (i/i lang [:language])] [:th "%"]]])
      (deps-card (i/i lang [:Deps]) deps lang)]
     [:div.columns
      (stats-card [:span
                   (i/i lang [:most-used-identified-licenses])
                   [:sup
                    [:a.has-text-grey.is-size-7
                     {:href  (str "/" lang "/glossary#license")
                      :title (i/i lang [:go-to-glossary])}
                     (fa "fa-question-circle")]]]
                  top_licenses_0
                  [:thead [:tr [:th (i/i lang [:license])] [:th "%"]]])
      [:div.column [:img {:src "/images/charts/top_licenses.svg"}]]]
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
      (stats-card [:span
                   (i/i lang [:orgas-with-more-stars])
                   [:sup
                    [:a.has-text-grey.is-size-7
                     {:href  (str "/" lang "/glossary#star")
                      :title (i/i lang [:go-to-glossary])}
                     (fa "fa-question-circle")]]]
                  top_orgs_by_stars)]
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
     [:br]]))

(defn stats-page-class [lang]
  (let [deps       (reagent/atom nil)
        stats      (reagent/atom nil)
        deps-total (reagent/atom nil)]
    (reagent/create-class
     {:display-name   "stats-page-class"
      :component-did-mount
      (fn []
        (GET "/deps-total"
             :handler #(reset! deps-total (walk/keywordize-keys %)))
        (GET "/deps-top"
             :handler #(reset! deps (take 10 (map (comp bean clj->js) %))))
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
                                   (i/i lang [:deps-of])) " ")
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
       (if (not-empty rdeps)
         [:div.table-container
          [:table.table.is-hoverable.is-fullwidth
           [:thead
            [:tr
             [:th
              [:a.button
               {:class    (when (= @sort-key :t) "is-light")
                :on-click #(do (when (= @sort-key :t)
                                 (reset! sort-rev? (not @sort-rev?)))
                               (reset! sort-key :t))}
               (i/i lang [:type])]]
             [:th
              [:a.button
               {:class    (when (= @sort-key :n) "is-light")
                :on-click #(do (when (= @sort-key :n)
                                 (reset! sort-rev? (not @sort-rev?)))
                               (reset! sort-key :n))}
               (i/i lang [:name])]]
             [:th.has-text-right
              [:a.button
               {:class    (when (= @sort-key :c) "is-light")
                :on-click #(do (when (= @sort-key :c)
                                 (reset! sort-rev? (not @sort-rev?)))
                               (reset! sort-key :c))}
               (i/i lang [:core-dep])]]
             [:th.has-text-right
              [:a.button
               {:class    (when (= @sort-key :d) "is-light")
                :on-click #(do (when (= @sort-key :d)
                                 (reset! sort-rev? (not @sort-rev?)))
                               (reset! sort-key :d))}
               (i/i lang [:dev-dep])]]]]
           (into [:tbody]
                 (for [{:keys [t n c d] :as r} rdeps]
                   ^{:key r}
                   [:tr
                    [:td t] [:td n]
                    [:td.has-text-right c] [:td.has-text-right d]]))]
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
        sort-key  (reagent/atom :c)
        sort-rev? (reagent/atom false)]
    (reagent/create-class
     {:display-name "repo-deps-page-class"
      :component-did-mount
      (fn []
        (GET (str "/deps/repos/" repo)
             :handler #(reset! deps (walk/keywordize-keys %))))
      :reagent-render
      (fn [] (repo-deps-page lang (:orga params) (:repo params) @deps sort-key sort-rev?))})))

(defn orga-deps-page
  "Table with group/organization dependencies."
  [lang orga deps sort-key sort-rev?]
  (let [cdeps (count deps)
        deps  (if (not= (deref sort-key) :repos)
                (sort-by @sort-key deps)
                (sort #(compare (count (:repos %1))
                                (count (:repos %2)))
                      deps))
        deps  (if @sort-rev? (reverse deps) deps)]
    [:div
     [:div [:h1 (str cdeps " " (if (< cdeps 2)
                                 (i/i lang [:dep-of])
                                 (i/i lang [:deps-of])) " ")
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
              :on-click #(do (reset! sort-rev? (not @sort-rev?))
                             (reset! sort-key :type))}
             (i/i lang [:type])]]
           [:th
            [:a.button
             {:class    (when (= @sort-key :name) "is-light")
              :on-click #(do (when (= @sort-key :name)
                               (reset! sort-rev? (not @sort-rev?)))
                             (reset! sort-key :name))}
             (i/i lang [:name])]]
           [:th.has-text-right
            [:a.button
             {:class    (when (= @sort-key :core) "is-light")
              :on-click #(do (when (= @sort-key :core)
                               (reset! sort-rev? (not @sort-rev?)))
                             (reset! sort-key :core))}
             (i/i lang [:core-dep])]]
           [:th.has-text-right
            [:a.button
             {:class    (when (= @sort-key :dev) "is-light")
              :on-click #(do (when (= @sort-key :dev)
                               (reset! sort-rev? (not @sort-rev?)))
                             (reset! sort-key :dev))}
             (i/i lang [:dev-dep])]]
           [:th
            [:a.button
             {:class (when (= @sort-key :repos) "is-light")
              :on-click
              #(do (when (= @sort-key :repos)
                     (reset! sort-rev? (not @sort-rev?)))
                   (reset! sort-key :repos))}
             (i/i lang [:Repos])]]]]
         (into [:tbody]
               (for [{:keys [type name core dev repos] :as d} deps]
                 ^{:key d}
                 [:tr
                  [:td type] [:td name]
                  [:td.has-text-right core] [:td.has-text-right dev]
                  [:td (for [{:keys [name full_name] :as r} repos]
                         ^{:key r}
                         [:span [:a {:href
                                     (str "https://github.com/"
                                          full_name)}
                                 name] " · "])]]))]
        [:br]]
       [:div
        [:h2 (i/i lang [:deps-not-found])]
        [:br]])]))

(defn orga-deps-page-class [lang]
  (let [deps      (reagent/atom nil)
        orga      (:orga @(re-frame/subscribe [:path-params?]))
        sort-key  (reagent/atom :core)
        sort-rev? (reagent/atom true)]
    (reagent/create-class
     {:display-name   "orga-deps-page-class"
      :component-did-mount
      (fn []
        (GET (str "/deps/orgas/" orga)
             :handler
             #(reset! deps (walk/keywordize-keys %))))
      :reagent-render (fn [] (orga-deps-page lang orga @deps sort-key sort-rev?))})))

(defn main-menu [q lang view]
  [:div.level
   [:div.level-left
    ;; FIXME: why :p here? Use level?
    ;; Orgas
    [:p.control.level-item
     [:a.button.is-danger
      {:title (i/i lang [:github-gitlab-etc])
       :href  (rfe/href :orgas {:lang lang})}
      (i/i lang [:orgas-or-groups])]]
    ;; Repos
    [:p.control.level-item
     [:a.button.is-success
      {:title (i/i lang [:all-public-sector-repos])
       :href  (rfe/href :repos {:lang lang})}
      (i/i lang [:repos-of-source-code])]]
    ;; Deps
    [:p.control.level-item
     [:a.button.is-warning
      {:title (i/i lang [:deps-expand])
       :href  (rfe/href :deps {:lang lang})}
      (i/i lang [:Deps])]]
    ;; Stats
    [:p.control.level-item
     [:a.button.is-info
      {:title (i/i lang [:stats-expand])
       :href  (rfe/href :stats {:lang lang})}
      (i/i lang [:stats])]]
    (when (or (= view :repos) (= view :orgas) (= view :deps))
      [:p.control.level-item
       [:input.input
        {:size        20
         :placeholder (i/i lang [:free-search])
         :value       (or @q (:q @(re-frame/subscribe [:display-filter?])))
         :on-change   (fn [e]
                        (let [ev (.-value (.-target e))]
                          (reset! q ev)
                          (async/go
                            (async/>! display-filter-chan {:q ev})
                            (async/<! (async/timeout timeout))
                            (async/>! filter-chan {:q ev}))))}]])
    (let [flt @(re-frame/subscribe [:filter?])]
      (when (seq (:g flt))
        [:p.control.level-item
         [:a.button.is-outlined.is-warning
          {:title (i/i lang [:remove-filter])
           :href  (rfe/href :repos {:lang lang})}
          [:span (:g flt)]
          (fa "fa-times")]]))]])

(defn live []
  (let [r (reagent/atom 10)]
    (fn []
      [:div
       [:input {:type      "range" :min "10" :max "50"
                :on-change #(let [v (.-value (.-target %))]
                              (println "Value:" v)
                              ;; (re-frame/dispatch [:range!] v)
                              (reset! r (js/parseInt v))
                              )}]
       ]))
  ;; [:ul
  ;;  (for [{:keys [u r n d o] :as e} @(re-frame/subscribe [:levent?])]
  ;;    ^{:key e}
  ;;    [:li
  ;;     [:p
  ;;      (gstring/format "%s (%s) pushed %s commits to %s at %s" u o n r d)]])]
  )

(defn main-page [q license language]
  (let [lang @(re-frame/subscribe [:lang?])
        view @(re-frame/subscribe [:view?])]
    [:div
     [main-menu q lang view]
     (condp = view
       :home-redirect
       (if dev?
         [:p "Testing."]
         (if (contains? i/supported-languages lang)
           (do (set! (.-location js/window) (str "/" lang "/groups")) "")
           (do (set! (.-location js/window) (str "/en/groups")) "")))
       ;; Table to display organizations
       :orgas     [organizations-page lang]
       ;; Table to display repositories
       :repos     [repos-page-class lang license language]
       ;; Table to display statistics
       :stats     [stats-page-class lang]
       ;; Table to display all dependencies
       :deps      [deps-page-class lang]
       ;; Table to display a repository dependencies
       :repo-deps [repo-deps-page-class lang]
       ;; Table to display a group dependencies
       :orga-deps [orga-deps-page-class lang]
       ;; Fall back on the organizations page
       :live      [live lang]
       ;; Fall back on the organizations page
       :else      (rfe/push-state :orgas {:lang lang}))]))

(defn main-class []
  (let [q        (reagent/atom nil)
        license  (reagent/atom nil)
        language (reagent/atom nil)]
    (reagent/create-class
     {:display-name   "main-class"
      :component-did-mount
      (fn []
        (GET "/orgas"
             :handler
             #(re-frame/dispatch
               [:update-orgas! (map (comp bean clj->js) %)])))
      :reagent-render (fn [] (main-page q license language))})))

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
    ["/live" :live]
    ["/stats" :stats]
    ["/deps"
     ["/:orga"
      ["/:repo" :repo-deps]
      ["" :orga-deps]]
     ["" :deps]]]])

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
  (sente/start-chsk-router! ch-chsk event-msg-handler))
