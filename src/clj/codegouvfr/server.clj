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
            [tea-time.core :as tt]
            [clojure.data.csv :as data-csv]
            [semantic-csv.core :as semantic-csv]
            [hickory.core :as h]
            [hickory.zip :as hz]
            [hickory.select :as hs])
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
(defonce annuaire-url "https://www.data.gouv.fr/fr/datasets/r/ac26b864-6a3a-496b-8832-8cde436f5230")

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

(def licenses-mapping
  {"MIT License"                                                "MIT License (MIT)"
   "GNU Affero General Public License v3.0"                     "GNU Affero General Public License v3.0 (AGPL-3.0)"
   "GNU General Public License v3.0"                            "GNU General Public License v3.0 (GPL-3.0)"
   "GNU Lesser General Public License v2.1"                     "GNU Lesser General Public License v2.1 (LGPL-2.1)"
   "Apache License 2.0"                                         "Apache License 2.0 (Apache-2.0)"
   "GNU General Public License v2.0"                            "GNU General Public License v2.0 (GPL-2.0)"
   "GNU Lesser General Public License v3.0"                     "GNU Lesser General Public License v3.0 (LGPL-3.0)"
   "Mozilla Public License 2.0"                                 "Mozilla Public License 2.0 (MPL-2.0)"
   "Eclipse Public License 2.0"                                 "Eclipse Public License 2.0 (EPL-2.0)"
   "Eclipse Public License 1.0"                                 "Eclipse Public License 1.0 (EPL-1.0)"
   "BSD 3-Clause \"New\" or \"Revised\" License"                "BSD 3-Clause \"New\" or \"Revised\" License (BSD-3-Clause)"
   "European Union Public License 1.2"                          "European Union Public License 1.2 (EUPL-1.2)"
   "Creative Commons Attribution Share Alike 4.0 International" "Creative Commons Attribution Share Alike 4.0 International (CC-BY-NC-SA-4.0)"
   "BSD 2-Clause \"Simplified\" License"                        "BSD 2-Clause \"Simplified\" License (BSD-2-Clause)"
   "The Unlicense"                                              "The Unlicense (Unlicense)"
   "Do What The Fuck You Want To Public License"                "Do What The Fuck You Want To Public License (WTFPL)"
   "Creative Commons Attribution 4.0 International"             "Creative Commons Attribution 4.0 International (CC-BY-4.0)"})

(defonce repos-rm-ks
  [:software_heritage_url :software_heritage_exists :derniere_modification
   :page_accueil :date_creation :topics :plateforme])

(defn update-repos []
  (let [repos-json (json/parse-string (:body (http/get repos-url)) true)]
    (spit "data/repos.json"
          (json/generate-string
           (map
            (fn [r] (assoc r :li (get licenses-mapping (:li r))))
            (map #(clojure.set/rename-keys
                   (apply dissoc % repos-rm-ks) repos-mapping)
                 repos-json)))))
  (timbre/info (str "updated repos.json")))

(defonce orgas-json (atom nil))

(defn update-orgas-json []
  (let [old-orgas-json @orgas-json
        result
        (try (:body (http/get orgas-url))
             (catch Exception e
               (do (timbre/error (str "Can't get groups: "
                                      (:cause (Throwable->map e))))
                   old-orgas-json)))]
    (reset! orgas-json (json/parse-string result true))))

(defn update-orgas []
  (let [annuaire
        (apply merge
               (map #(let [{:keys [github lannuaire]} %]
                       {(keyword github) lannuaire})
                    (semantic-csv/mappify
                     (data-csv/read-csv (:body (http/get annuaire-url))))))]
    (spit "data/orgas.json"
          (json/generate-string
           (map #(assoc % :an ((keyword (:l %)) annuaire))
                (map #(clojure.set/rename-keys % orgas-mapping)
                     @orgas-json))))
    (timbre/info (str "updated orgas.json"))))

;; FIXME: Use the stats URL directly?
(defn update-stats []
  (spit "data/stats.json" (:body (http/get stats-url)))
  (timbre/info (str "updated stats.json")))

(defn get-deps [orga]
  ;; FIXME: Wrap http/get into a try clause
  (-> (http/get (str "https://backyourstack.com/" orga "/dependencies"))
      :body
      h/parse
      h/as-hickory
      (as-> dps (hs/select (hs/child (hs/id :__NEXT_DATA__)) dps))
      first
      :content
      first
      (json/parse-string true)
      :props
      :pageProps))

(defonce deps-rm-kws [:private :default_branch :language :id :checked :owner :full_name])

(defonce repos-deps (atom nil))

(defn update-orgas-repos-deps []
  (reset! repos-deps nil)
  (let [gh-orgas (map :login (filter #(= (:plateforme %) "GitHub") (take 8 @orgas-json)))]
    (doall
     (for [orga gh-orgas
           :let [data (get-deps orga)
                 orga-deps (:dependencies data)
                 orga-repos0
                 (filter #(not (empty? (:dependencies %)))
                         (map (fn [r] (assoc r :g orga))
                              (map #(apply dissoc % deps-rm-kws) (:repos data))))
                 orga-repos1
                 (map #(clojure.set/rename-keys
                        % {:name :n :dependencies :d}) orga-repos0)
                 orga-repos (map #(assoc % :d
                                         (map (fn [r] (clojure.set/rename-keys
                                                       r {:type :t :name :n}))
                                              (:d %)))
                                 orga-repos1)]]
       (do (spit (str "data/deps/orgas/" orga ".json")
                 (json/generate-string (map #(dissoc % :project) orga-deps)))
           (swap! repos-deps (partial apply conj) orga-repos))))
    (spit (str "data/deps/repos-deps.json")
          (json/generate-string @repos-deps))
    (timbre/info (str "updated orgas and repos dependencies"))))

(defn merge-with-colls [a b]
  (if (and (coll? a) (coll? b)) (into a b) b))

(defn update-deps []
  (let [deps (atom nil)]
    (doall
     (for [rep @repos-deps :let [r-deps (:d rep)]]
       (doall
        (for [d0   r-deps
              :let [d (apply dissoc d0 [:core :dev :peer :engines])]]
          (swap! deps conj
                 (assoc d :rs (vector (dissoc rep :d))))))))
    (reset! deps (reverse (sort #(compare (count (:rs %1))
                                          (count (:rs %2)))
                                (map #(apply (partial merge-with merge-with-colls) %)
                                     (vals (group-by :n @deps))))))
    (spit "data/deps/deps-repos.json" (json/generate-string @deps))
    (reset! deps (map #(assoc % :rs (count (:rs %))) @deps))
    (spit "data/deps/deps-count.json" (json/generate-string @deps)))
  (timbre/info (str "updated data/deps/*")))

;; (first @repos-deps)
;; (update-orgas-json)
;; (update-orgas-repos-deps)
;; (update-deps)

(defn start-tasks []
  (tt/start!)
  (def update-orgas! (tt/every! 10800 update-orgas-json))
  (def update-orgas! (tt/every! 10800 update-orgas))
  (def update-repos! (tt/every! 10800 update-repos))
  (def update-stats! (tt/every! 10800 update-stats))
  (def update-stats! (tt/every! 86400 10 update-orgas-repos-deps))
  (def update-stats! (tt/every! 86400 20 update-deps))
  ;; (def update-stats! (tt/every! 86400 10800 update-deps))
  (timbre/info "Tasks started!"))
;; (tt/cancel! update-*!)

(defn resource-json [f]
  (assoc
   (response/response
    (io/input-stream f))
   :headers {"Content-Type" "application/json; charset=utf-8"}))

(defn resource-orga-json [orga]
  (assoc
   (response/response
    (try (slurp (str "data/deps/orgas/" orga ".json"))
         (catch Exception e (str "No file named " orga ".json"))))
   :headers {"Content-Type" "application/json; charset=utf-8"}))

(defn resource-repo-json [repo]
  (assoc
   (response/response
    (json/generate-string (:d (first (filter #(= (:n %) repo) @repos-deps)))))
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
  (GET "/orgas" [] (resource-json "data/orgas.json"))
  (GET "/stats" [] (resource-json "data/stats.json"))
  (GET "/repos" [] (resource-json "data/repos.json"))
  (GET "/deps/orgas/:orga" [orga] (resource-orga-json orga))
  (GET "/deps/repos/:repo" [repo] (resource-repo-json repo))
  (GET "/deps" [] (resource-json "data/deps/deps-count.json"))
  (GET "/deps-repos" [] (resource-json "data/deps/deps-repos.json"))

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
             ;; wrap-reload
             ))

(defn -main [& args]
  (start-tasks)
  (def server (server/run-server app {:port config/codegouvfr_port}))
  (println (str "codegouvfr application started on locahost:" config/codegouvfr_port)))

;; (-main)

