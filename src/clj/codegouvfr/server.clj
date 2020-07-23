;; Copyright (c) 2019-2020 DINUM, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.server
  (:require [ring.util.response :as response]
            [clojure.java.io :as io]
            [java-time :as t]
            [clojure.walk :as walk]
            [codegouvfr.config :as config]
            [codegouvfr.views :as views]
            [codegouvfr.i18n :as i]
            ;; [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [postal.core :as postal]
            [postal.support]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]
            [taoensso.timbre.appenders (postal :as postal-appender)]
            [cheshire.core :as json]
            [org.httpkit.server :as server]
            ;; [taoensso.sente :as sente]
            ;; [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
            [clojure.core.async :as async]
            [clj-http.client :as http]
            [tea-time.core :as tt]
            [clojure.set]
            [clojure.java.shell :as sh]
            [clojure.data.json :as datajson])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup logging

(timbre/set-config!
 {:level     :debug
  :output-fn (partial timbre/default-output-fn {:stacktrace-fonts {}})
  :appenders
  {:println (timbre/println-appender {:stream :auto})
   :spit    (appenders/spit-appender {:fname config/log-file})
   :postal  (merge (postal-appender/postal-appender ;; :min-level :warn
                    ^{:host config/smtp-host
                      :user config/smtp-login
                      :pass config/smtp-password}
                    {:from config/from
                     :to   config/admin-email})
                   {:min-level :error})}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente setup

;; (let [{:keys [ch-recv send-fn connected-uids
;;               ajax-post-fn ajax-get-or-ws-handshake-fn]}
;;       (sente/make-channel-socket! (get-sch-adapter) {})]

;;   (def ring-ajax-post                ajax-post-fn)
;;   (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
;;   (def ch-chsk                       ch-recv)
;;   (def chsk-send!                    send-fn)
;;   (def connected-uids                connected-uids))

;; (defn event-msg-handler [{:keys [id ?data event]}] ; FIXME: unused id ?data
;;   (doseq [uid (:any @connected-uids)]
;;     (chsk-send! uid [:event/PushEvent event])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Profiles for interactive development

(def profile (atom nil))
(defn profiles [type]
  (if (= type :production)
    {:send_channel_sleep              1000
     :repeat_in_connection_pool       240
     :repeat_in_connection_pool_sleep 15000
     :latest_updated_orgas            20}
    {:send_channel_sleep              1000
     :repeat_in_connection_pool       2
     :repeat_in_connection_pool_sleep 1000
     :latest_updated_orgas            2}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initial setup for retrieving events constantly

(def gh-org-events "https://api.github.com/orgs/%s/events")
(def orgas-url "https://api-code.etalab.gouv.fr/api/repertoires/all")
(def stats-url "https://api-code.etalab.gouv.fr/api/stats/general")
(def http-get-params {:cookie-policy :standard})
(def last-orgs-events (agent '()))
(def events-channel (async/chan))
(def http-get-gh-params
  (merge http-get-params
         {:basic-auth
          (str config/github-user ":" config/github-access-token)}))

(defn seqs-difference [seq1 seq2]
  (seq (clojure.set/difference (into #{} seq2) (into #{} seq1))))

;; (defn start-events-channel! []
;;   (async/go
;;     (loop [e (async/<! events-channel)]
;;       (when-let [{:keys [u]} e] ; FIXME: Is a user defined?
;;         (event-msg-handler {:event e}))
;;       (recur (async/<! events-channel)))))

(defn sort-events-by-date [diff]
  (map (fn [d] (update d :d #(t/format "MM/dd/YYYY HH:mm" %)))
       (sort-by :d (map #(update % :d t/zoned-date-time)
                        diff))))

(add-watch last-orgs-events
           :watcher
           (fn [_ _ old new]
             (when-let [diff (seqs-difference old new)]
               (async/thread
                 (doseq [e (sort-events-by-date diff)]
                   (timbre/info (pr-str e))
                   (Thread/sleep (:send_channel_sleep @profile))
                   (async/>!! events-channel e))))))

(def latest-updated-orgas-filter
  (comp
   (filter #(= (:plateforme %) "GitHub"))
   (map #(select-keys % [:organisation_nom :derniere_mise_a_jour]))
   (map #(update % :derniere_mise_a_jour t/zoned-date-time))))

(defn latest-updated-orgas [n]
  (let [repos (json/parse-string
               (:body (try (http/get orgas-url http-get-params)
                           (catch Exception _ nil))) ;; FIXME: add error?
               true)]
    (take
     n
     (distinct
      (->> (reverse
            (sort-by :derniere_mise_a_jour
                     (sequence latest-updated-orgas-filter repos)))
           (map :organisation_nom))))))

(defn filter-old-events [events]
  (let [yesterday (t/minus (t/instant) (t/days 1))]
    (filter (fn [{:keys [date]}]
              (not (t/before? (t/instant date) yesterday)))
            events)))

;; Authenticated rate limit is 5000 per hour.  Every hour, take 20
;; GitHub orgas with recently updated repos and get the last events
;; 240 times for these orgas every 15 seconds.
;; (defn latest-orgas-events! [orgas]
;;   (http/with-connection-pool
;;     {:timeout 5 :threads 4 :insecure? false :default-per-route 10}
;;     (dotimes [_ (:repeat_in_connection_pool profile)]
;;       (let [new-events (atom nil)]
;;         ;; Fet new-events for all recently updated orgas
;;         (doseq [org orgas]
;;           (when-let [events (json/parse-string
;;                              (:body
;;                               (try (http/get (format gh-org-events org)
;;                                              http-get-gh-params)
;;                                    (catch Exception e
;;                                      (timbre/error "Can't get events for" org e))))
;;                              true)]
;;             (doseq [{:keys [id actor repo payload created_at org]} ;; FIXME: use id
;;                     ;; Only take PushEvents so far
;;                     (filter #(= (:type %) "PushEvent") events)
;;                     :let
;;                     [user (:login actor)
;;                      repo-name (:name repo)
;;                      nb (:distinct_size payload)
;;                      date created_at
;;                      org-name (:login org)]]
;;               (swap! new-events conj {:u user :r repo-name :n nb
;;                                       :d date :o org-name}))))
;;         ;; Only update the main events list now, trigger UI updates
;;         (send last-orgs-events
;;               #(filter-old-events (apply merge %1 %2)) @new-events)
;;         ;; Then wait for 15 seconds
;;         (Thread/sleep (:repeat_in_connection_pool_sleep @profile))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Export licenses data as vega images

(def licenses-spdx
  {"Other"                                                      "Other"
   "MIT License"                                                "MIT"
   "GNU Affero General Public License v3.0"                     "AGPL-3.0"
   "GNU General Public License v3.0"                            "GPL-3.0"
   "GNU Lesser General Public License v2.1"                     "LGPL-2.1"
   "Apache License 2.0"                                         "Apache-2.0"
   "GNU General Public License v2.0"                            "GPL-2.0"
   "GNU Lesser General Public License v3.0"                     "LGPL-3.0"
   "Mozilla Public License 2.0"                                 "MPL-2.0"
   "Eclipse Public License 2.0"                                 "EPL-2.0"
   "Eclipse Public License 1.0"                                 "EPL-1.0"
   "BSD 3-Clause \"New\" or \"Revised\" License"                "BSD-3-Clause"
   "European Union Public License 1.2"                          "EUPL-1.2"
   "Creative Commons Attribution Share Alike 4.0 International" "CC-BY-SA-4.0"
   "BSD 2-Clause \"Simplified\" License"                        "BSD-2-Clause"
   "The Unlicense"                                              "Unlicense"
   "Do What The Fuck You Want To Public License"                "WTFPL"
   "Creative Commons Attribution 4.0 International"             "CC-BY-4.0"})

(defn set-licenses-vega-data [lang]
  (let [l0       (:top_licenses
                  (json/parse-string
                   (:body (try (http/get stats-url http-get-params)
                               (catch Exception e
                                 (timbre/error
                                  (str "Cannot get stats URL\n"
                                       (.getMessage e))))))
                   true))
        l1       (map #(zipmap [:License :Number] %)
                      (walk/stringify-keys
                       (dissoc l0 :Inconnue)))
        licenses (map #(assoc % :License (get licenses-spdx (:License %))) l1)]
    {:title    (i/i lang [:most-used-licenses])
     :data     {:values licenses}
     :encoding {:x     {:field "Number" :type "quantitative"
                        :axis  {:title (i/i lang [:repos-number])}}
                :y     {:field "License" :type "ordinal" :sort "-x"
                        :axis  {:title         false
                                :labelLimit    200
                                :offset        10
                                :maxExtent     100
                                :labelFontSize 15
                                :labelAlign    "right"}}
                :color {:field  "License"
                        :legend false
                        :type   "nominal"
                        :title  (i/i lang [:licenses])
                        :scale  {:scheme "tableau20"}}}
     :width    600
     :height   600
     :mark     {:type "bar" :tooltip {:content "data"}}}))

(defn temp-json-file
  "Convert `clj-vega-spec` to json and store it as tmp file."
  [clj-vega-spec]
  (let [tmp-file (java.io.File/createTempFile "vega." ".json")]
    (.deleteOnExit tmp-file)
    (with-open [file (io/writer tmp-file)]
      (datajson/write clj-vega-spec file))
    (.getAbsolutePath tmp-file)))

(defn vega-licenses-chart! []
  (sh/sh "vl2svg"
         (temp-json-file (set-licenses-vega-data "en"))
         "./resources/public/images/charts/top_licenses.svg"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Expose json resources

(defn resource-json
  "Expose a json resource."
  [f]
  (assoc
   (response/response
    (io/input-stream f))
   :headers {"Content-Type" "application/json; charset=utf-8"}))

(defonce deps-orgas
  (-> (try (slurp "data/deps-orgas.json")
           (catch Exception e (timbre/error
                               (str "No file named data/deps-orgas.json"
                                    (.getMessage e)))))
      json/parse-string))

(defonce deps-repos
  (-> (try (slurp "data/deps-repos.json")
           (catch Exception e (timbre/error
                               (str "No file named data/deps-repos.json"
                                    (.getMessage e)))))
      json/parse-string))

(defn resource-orga-json
  "Expose [orga].json as a json resource."
  [platform-orga]
  (when-let [platform (last (re-find #"^(GitHub|GitLab)::*" platform-orga))]
    (let [orga (last (re-find #"^(?:GitHub|GitLab)::(.+)$" platform-orga))]
      (assoc
       (response/response
        (json/generate-string (get deps-orgas (str [orga platform]))))
       :headers {"Content-Type" "application/json; charset=utf-8"}))))

(defn resource-repo-json
  "Expose the json resource corresponding to `repo`."
  [orga repo]
  (assoc
   (response/response
    (json/generate-string (get deps-repos (str [repo orga]))))
   :headers {"Content-Type" "application/json; charset=utf-8"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup email sending

(defn send-email
  "Send a templated email."
  [{:keys [email name organization message log]}]
  (try
    (when-let
        [res (postal/send-message
              {:host config/smtp-host
               :port 587
               :user config/smtp-login
               :pass config/smtp-password}
              {:from       config/from
               :message-id #(postal.support/message-id config/msgid-domain)
               :reply-to   email
               :to         config/admin-email
               :subject    (str name " / " organization)
               :body       message})]
      (when (= (:error res) :SUCCESS) (timbre/info log)))
    (catch Exception e
      (timbre/error (str "Can't send email: " (:cause (Throwable->map e)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup routes

(defroutes routes
  (GET "/latest.xml" [] (views/rss))
  ;; (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  ;; (POST "/chsk" req (ring-ajax-post                req))
  (GET "/orgas" [] (resource-json "data/orgas.json"))
  (GET "/repos" [] (resource-json "data/repos.json"))
  ;; The next two are for API usage only:
  (GET "/deps/:orga" [orga] (resource-orga-json orga))
  (GET "/deps/:orga/:repo" [orga repo] (resource-repo-json orga repo))
  (GET "/deps-total" [] (resource-json "data/deps-total.json"))
  (GET "/deps-top" [] (resource-json "data/deps-top.json"))
  (GET "/deps" [] (resource-json "data/deps.json"))

  (GET "/en/about" [] (views/en-about "en"))
  (GET "/en/contact" [] (views/contact "en"))
  (GET "/en/glossary" [] (views/en-glossary "en"))
  (GET "/en/ok" [] (views/ok "en"))
  (GET "/it/about" [] (views/it-about "it"))
  (GET "/it/contact" [] (views/contact "it"))
  (GET "/it/glossary" [] (views/it-glossary "it"))
  (GET "/it/ok" [] (views/ok "it"))
  (GET "/fr/about" [] (views/fr-about "fr"))
  (GET "/fr/contact" [] (views/contact "fr"))
  (GET "/fr/glossary" [] (views/fr-glossary "fr"))
  (GET "/fr/ok" [] (views/ok "fr"))

  ;; Backward compatibility
  (GET "/glossaire" [] (response/redirect "/fr/glossary"))
  (GET "/contact" [] (response/redirect "/fr/contact"))
  (GET "/apropos" [] (response/redirect "/fr/about"))

  (POST "/contact" req
        (let [params (walk/keywordize-keys (:form-params req))]
          (send-email (conj params {:log (str "Sent message from " (:email params)
                                              " (" (:organization params) ")")}))
          (response/redirect (str "/" (:lang params) "/ok"))))

  (POST "/contact" req
        (let [params (walk/keywordize-keys (:form-params req))]
          (send-email (conj params {:log (str "Sent message from " (:email params)
                                              " (" (:organization params) ")")}))
          (response/redirect (str "/" (:lang params) "/ok"))))
  ;; FIXME: unused bindings?
  (GET "/:lang/:p1/:p2/:p3" [lang]
       (views/default (if (contains? i/supported-languages lang) lang "fr")))
  (GET "/:lang/:p1/:p2" [lang]
       (views/default (if (contains? i/supported-languages lang) lang "fr")))
  (GET "/:lang/:p" [lang]
       (views/default
        (if (contains? i/supported-languages lang)
          lang
          "fr")))
  (GET "/:p" [] (views/default "fr"))
  (GET "/" [] (views/default "fr"))
  (resources "/")
  (not-found "Not Found"))

(def app (-> #'routes
             (wrap-defaults site-defaults)
             ;; ring.middleware.keyword-params/wrap-keyword-params
             ;; ring.middleware.params/wrap-params
             ;; FIXME: Don't wrap reload in production
             ;; wrap-reload
             ))

(defn start-tasks! []
  (tt/start!)
  (tt/every!
   14400 (fn []
           (timbre/info "Generating license chart")
           (vega-licenses-chart!)))
  ;; (tt/every! ;; FIXME: why the error when done?
  ;;  36000 (fn []
  ;;          (timbre/info "Start streaming GitHub events")
  ;;          (latest-orgas-events!
  ;;           (latest-updated-orgas (:latest_updated_orgas @profile)))))
  )

(defn -main
  "Start tasks and the HTTP server."
  []
  (reset! profile (profiles :production))
  (server/run-server app {:port config/codegouvfr_port :join? false})
  ;; (sente/start-chsk-router! ch-chsk event-msg-handler)
  ;; (start-events-channel!)
  (start-tasks!)
  (timbre/info (str "codegouvfr application started on locahost:" config/codegouvfr_port)))
