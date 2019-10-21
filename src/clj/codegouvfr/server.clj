;; Copyright (c) 2019 DINSIC, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.server
  (:require [ring.util.response :as response]
            [clojure.java.io :as io]
            [codegouvfr.config :as config]
            [codegouvfr.views :as views]
            [codegouvfr.i18n :as i]
            [org.httpkit.server :as server]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.params :as params]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [postal.core :as postal]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]
            [taoensso.timbre.appenders (postal :as postal-appender)]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [tea-time.core :as tt])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup logging

(timbre/set-config!
 {:level     :debug
  :output-fn (partial timbre/default-output-fn {:stacktrace-fonts {}})
  :appenders
  {:println (timbre/println-appender {:stream :auto})
   :spit    (appenders/spit-appender {:fname config/log-file})
   :postal  (postal-appender/postal-appender ;; :min-level :warn
             ^{:host config/smtp-host
               :user config/smtp-login
               :pass config/smtp-password}
             {:from config/from
              :to   config/admin-email})}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Download repos, orgas and stats locally

(defonce repos-url "https://api-code.etalab.gouv.fr/api/repertoires/all")
(defonce orgas-url "https://api-code.etalab.gouv.fr/api/organisations/all")
(defonce stats-url "https://api-code.etalab.gouv.fr/api/stats/general")

(def repos-mapping {:nom                    :n
                    :description            :d
                    :page_accueil           :h
                    :organisation_nom       :o
                    :plateforme             :p
                    :langage                :l
                    :licence                :li
                    :repertoire_url         :r
                    :topics                 :t
                    :date_creation          :c
                    :derniere_mise_a_jour   :u
                    :derniere_modification  :m
                    :nombre_forks           :f
                    :nombre_issues_ouvertes :i
                    :nombre_stars           :s
                    :est_archive            :a?
                    :est_fork               :f?})

(def orgas-mapping {:description        :d
                    :adresse            :a
                    :email              :e
                    :nom                :n
                    :plateforme         :p
                    :site_web           :h
                    :est_verifiee       :v?
                    :login              :l
                    :date_creation      :c
                    :nombre_repertoires :r
                    :organisation_url   :o
                    :avatar_url         :au})

(defn local-json-file [file remap url ks]
  (spit file
        (json/generate-string
         (map #(clojure.set/rename-keys
                (apply dissoc % ks) remap)
              (json/parse-string
               (:body (http/get url)) true))))
  (timbre/info (str "updated " file)))

(defn update-repos []
  (local-json-file
   "repos.json"
   repos-mapping
   repos-url
   [:software_heritage_url :software_heritage_exists :derniere_modification]))

(defn update-orgas []
  (local-json-file "orgas.json" orgas-mapping orgas-url nil))

(defn update-stats []
  (spit "stats.json" (:body (http/get stats-url)))
  (timbre/info (str "updated stats.json")))

(defn start-tasks []
  (tt/start!)
  (def update-repos! (tt/every! 10800 update-repos))
  (def update-orgas! (tt/every! 10800 update-orgas))
  (def update-stats! (tt/every! 10800 update-stats))
  (timbre/info "Tasks started!"))
;; (tt/cancel! update-*!)

(defn json-resource [f]
  (assoc
   (response/response
    (io/input-stream f))
   :headers {"Content-Type" "application/json; charset=utf-8"}))

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
  (GET "/orgas" [] (json-resource "orgas.json"))
  (GET "/stats" [] (json-resource "stats.json"))
  (GET "/repos" [] (json-resource "repos.json"))
  
  (GET "/en/about" [] (views/en-about "en"))
  (GET "/en/contact" [] (views/contact "en"))
  (GET "/en/glossary" [] (views/en-glossary "en"))
  (GET "/en/ok" [] (views/ok "en"))
  (GET "/fr/about" [] (views/fr-about "fr"))
  (GET "/fr/contact" [] (views/contact "fr"))
  (GET "/fr/glossary" [] (views/fr-glossary "fr"))
  (GET "/fr/ok" [] (views/ok "fr"))

  ;; Backward compatibility
  (GET "/glossaire" [] (response/redirect "/fr/glossary"))
  (GET "/contact" [] (response/redirect "/fr/contact"))
  (GET "/apropos" [] (response/redirect "/fr/about"))

  (POST "/contact" req
        (let [params (clojure.walk/keywordize-keys (:form-params req))]
          (send-email (conj params {:log (str "Sent message from " (:email params)
                                              " (" (:organization params) ")")}))
          (response/redirect (str "/" (:lang params) "/ok"))))
  
  (GET "/:lang/:page" [lang page]
       (views/default
        (if (contains? i/supported-languages lang)
          lang
          "en")))
  (GET "/:page" [page] (views/default "en"))
  (GET "/" [] (views/default "en"))
  
  (resources "/")
  (not-found "Not Found"))

(def app (-> #'routes
             (wrap-defaults site-defaults)
             params/wrap-params
             wrap-reload))

(defn -main [& args]
  (start-tasks)
  (def server (server/run-server app {:port config/codegouvfr_port}))
  (println (str "codegouvfr application started on locahost:" config/codegouvfr_port)))

;; (-main)

