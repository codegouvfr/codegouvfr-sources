;; Copyright (c) 2019 DINSIC, Bastien Guerry <bastien.guerry@data.gouv.fr>
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
            [clojure.string :as s]
            [org.httpkit.server :as server]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
            [clojure.core.async :as async]
            [clj-http.client :as http]
            [tea-time.core :as tt]
            [clojure.set])
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

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {})]

  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv)
  (def chsk-send!                    send-fn)
  (def connected-uids                connected-uids))

(defn event-msg-handler [{:keys [id ?data event]}] ; FIXME: unused id ?data
  (doseq [uid (:any @connected-uids)]
    (chsk-send! uid [:event/PushEvent event])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initial setup for retrieving events constantly

(def gh-org-events "https://api.github.com/orgs/%s/events")

(def orgas-url "https://api-code.etalab.gouv.fr/api/repertoires/all")

(def http-get-params {:cookie-policy :standard})

(def http-get-gh-params
  (merge http-get-params
         {:basic-auth
          (str config/github-user ":" config/github-access-token)}))

(def last-orgs-events (agent '()))

(def events-channel (async/chan 100))

(defn seqs-difference [seq1 seq2]
  (seq (clojure.set/difference (into #{} seq2) (into #{} seq1))))

(defn start-events-channel! []
  (async/go
    (loop [e (async/<! events-channel)]
      (when-let [{:keys [u]} e] ; FIXME: Is a user defined?
        (event-msg-handler {:event e}))
      (recur (async/<! events-channel)))))

;; (event-msg-handler {:event {:u "BLAIREAU"}})
;; (conj ["dog" "lion" "tiger"] "ours")

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
                   (Thread/sleep 1000)
                   (async/>!! events-channel e))))))

(def latest-updated-orgas-filter
  (comp
   (filter #(= (:plateforme %) "GitHub"))
   (map #(select-keys % [:organisation_nom :derniere_mise_a_jour]))
   (map #(update % :derniere_mise_a_jour t/zoned-date-time))))

(defn latest-updated-orgas [n]
  (let [repos (json/parse-string
               (:body (try (http/get orgas-url http-get-params)))
               true)]
    (take
     n
     (distinct
      (->> (reverse
            (sort-by :derniere_mise_a_jour
                     (sequence latest-updated-orgas-filter repos)))
           (map :organisation_nom))))))

;; Authenticated rate limit is 5000 per hour.  Every hour, take 20
;; GitHub orgas with recently updated repos and get the last events
;; 240 times for these orgas every 15 seconds.
(defn latest-orgas-events [orgas]
  (http/with-connection-pool
    {:timeout 5 :threads 4 :insecure? false :default-per-route 10}
    (dotimes [_ 240]
      (let [new-events (atom nil)]
        ;; Fet new-events for all recently updated orgas
        (doseq [org orgas]
          (when-let [events (json/parse-string
                             (:body
                              (try (http/get (format gh-org-events org)
                                             http-get-gh-params)
                                   (catch Exception e
                                     (timbre/error "Can't get events for" org e))))
                             true)]
            (doseq [{:keys [id actor repo payload created_at org]} ;; FIXME: use id
                    ;; Only take PushEvents so far
                    (filter #(= (:type %) "PushEvent") events)
                    :let
                    [user (:login actor)
                     repo-name (:name repo)
                     nb (:distinct_size payload)
                     date created_at
                     org-name (:login org)]]
              (swap! new-events conj {:u user :r repo-name :n nb
                                      :d date :o org-name}))))
        ;; Only update the main events list now, trigger UI updates
        (send last-orgs-events #(take 100 (apply merge %1 %2)) @new-events)
        ;; Then wait for 15 seconds
        (Thread/sleep 15000)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Expose json resources

(defn resource-json
  "Expose a json resource."
  [f]
  (assoc
   (response/response
    (io/input-stream f))
   :headers {"Content-Type" "application/json; charset=utf-8"}))

(defn resource-orga-json
  "Expose [orga].json as a json resource."
  [orga]
  (assoc
   (response/response
    (try (slurp (str "data/deps/orgas/" (s/lower-case orga) ".json"))
         (catch Exception e (timbre/error
                             (str "No file named " orga ".json\n"
                                  (.getMessage e))))))
   :headers {"Content-Type" "application/json; charset=utf-8"}))

(defn resource-repo-json
  "Expose the json resource corresponding to `repo`."
  [repo]
  (let [repos-deps (json/parse-string
                    (try (slurp "data/deps/repos-deps.json")
                         (catch Exception e
                           (timbre/error (str "Can't find repos-deps.json\n"
                                              (.getMessage e)))))
                    true)
        deps       (first (filter
                           #(= (s/lower-case (:n %)) (s/lower-case repo))
                           repos-deps))]
    (assoc
     (response/response
      (json/generate-string {:g (:g deps) :d (:d deps)}))
     :headers {"Content-Type" "application/json; charset=utf-8"})))

(defn resource-dep-json
  "Expose the json resource corresponding to `dep`."
  [dep]
  (let [deps-repos (json/parse-string
                    (try (slurp "data/deps/deps-repos.json")
                         (catch Exception e
                           (timbre/error (str "Can't find deps-repos.json\n"
                                              (.getMessage e)))))
                    true)
        dep        (first (filter
                           #(= (s/lower-case (:n %)) (s/lower-case dep))
                           deps-repos))]
    (assoc
     (response/response
      (json/generate-string (:rs dep)))
     :headers {"Content-Type" "application/json; charset=utf-8"})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup email sending

(defn send-email
  "Send a templated email."
  [{:keys [email name organization message log]}]
  (try
    (if-let
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
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req))
  (GET "/orgas" [] (resource-json "data/orgas.json"))
  (GET "/repos" [] (resource-json "data/repos.json"))
  (GET "/deps/orgas/:orga" [orga] (resource-orga-json orga))
  (GET "/deps/repos/:repo" [repo] (resource-repo-json repo))
  (GET "/deps/:dep" [dep] (resource-dep-json dep))
  (GET "/deps-total" [] (resource-json "data/deps/deps-total.json"))
  (GET "/deps-top" [] (resource-json "data/deps/deps-top.json"))
  (GET "/deps" [] (resource-json "data/deps/deps.json"))

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
  (tt/every! ;; FIXME: why the error when done?
   36000
   (fn []
     ((timbre/info "Start streaming GitHub events")
      (latest-orgas-events (latest-updated-orgas 20))))))

(defn -main
  "Start tasks and the HTTP server."
  []
  (server/run-server app {:port config/codegouvfr_port :join? false})
  (sente/start-chsk-router! ch-chsk event-msg-handler)
  (start-events-channel!)
  (start-tasks!)
  (timbre/info (str "codegouvfr application started on locahost:" config/codegouvfr_port)))

;; (-main)
