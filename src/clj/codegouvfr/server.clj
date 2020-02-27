;; Copyright (c) 2019 DINSIC, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.server
  (:require [ring.util.response :as response]
            [clojure.java.io :as io]
            [codegouvfr.config :as config]
            [codegouvfr.views :as views]
            [codegouvfr.i18n :as i]
            ;; [ring.middleware.reload :refer [wrap-reload]]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [postal.core :as postal]
            [postal.support]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]
            [taoensso.timbre.appenders (postal :as postal-appender)]
            [cheshire.core :as json]
            [clojure.string :as s])
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
         (catch Exception e (str "No file named " orga ".json"))))
   :headers {"Content-Type" "application/json; charset=utf-8"}))

(defn resource-repo-json
  "Expose the json resource corresponding to `repo`."
  [repo]
  (let [repos-deps (json/parse-string
                    (try (slurp "data/deps/repos-deps.json")
                         (catch Exception e
                           (timbre/error "Can't find repos-deps.json")))
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
                           (timbre/error "Can't find deps-repos.json")))
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
        (let [params (clojure.walk/keywordize-keys (:form-params req))]
          (send-email (conj params {:log (str "Sent message from " (:email params)
                                              " (" (:organization params) ")")}))
          (response/redirect (str "/" (:lang params) "/ok"))))
  
  (POST "/contact" req
        (let [params (clojure.walk/keywordize-keys (:form-params req))]
          (send-email (conj params {:log (str "Sent message from " (:email params)
                                              " (" (:organization params) ")")}))
          (response/redirect (str "/" (:lang params) "/ok"))))

  (GET "/:lang/:p1/:p2/:p3" [lang p1 p2 p3]
       (views/default
        (if (contains? i/supported-languages lang)
          lang
          "en")))
  (GET "/:lang/:p1/:p2" [lang p1 p2]
       (views/default
        (if (contains? i/supported-languages lang)
          lang
          "en")))
  (GET "/:lang/:p" [lang p]
       (views/default
        (if (contains? i/supported-languages lang)
          lang
          "en")))
  (GET "/:p" [p] (views/default "en"))
  (GET "/" [] (views/default "en"))
  
  (resources "/")
  (not-found "Not Found"))

(def app (-> #'routes
             (wrap-defaults site-defaults)
             ;; FIXME: Don't wrap reload in production
             ;; wrap-reload
             ))

(defn -main
  "Start tasks and the HTTP server."
  [& args]
  (jetty/run-jetty app {:port config/codegouvfr_port :join? false})
  (println (str "codegouvfr application started on locahost:" config/codegouvfr_port)))

;; (-main)
