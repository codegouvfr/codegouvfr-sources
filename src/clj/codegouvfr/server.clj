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
            [clj-http.client :as http]
            [cheshire.core :as json]
            [tea-time.core :as tt]
            [clojure.data.csv :as data-csv]
            [semantic-csv.core :as semantic-csv]
            [hickory.core :as h]
            [hickory.zip :as hz]
            [hickory.select :as hs]
            [clojure.string :as s]
            [clojure.set :as clset])
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
;; Define variables later needed

(defonce
  ^{:doc "The URL from where to fetch repository data."}
  repos-url
  "https://api-code.etalab.gouv.fr/api/repertoires/all")

(defonce
  ^{:doc "The URL from where to fetch groups/organizations data."}
  orgas-url
  "https://api-code.etalab.gouv.fr/api/organisations/all")

(defonce
  ^{:doc "The URL from where to fetch information about organizations as
  provided by https://lannuaire.service-public.fr."}
  annuaire-url
  "https://www.data.gouv.fr/fr/datasets/r/ac26b864-6a3a-496b-8832-8cde436f5230")

(defonce
  ^{:doc "The URL to get emoji char/name pairs."}
  emoji-json-url
  "https://raw.githubusercontent.com/amio/emoji.json/master/emoji.json")

(defonce
  ^{:doc "A map of emoji with {:char \"\" :name \"\"."}
  emoji-json
  (->> (json/parse-string (:body (http/get emoji-json-url)) true)
       (map #(select-keys % [:char :name]))
       (map #(update % :name (fn [n] (str ":" (s/replace n " " "_") ":"))))))

(defonce
  ^{:doc "A list of keywords to ignore when generating data/orgas/[orga].json."}
  deps-rm-kws
  [:private :default_branch :language :id :checked :owner :full_name])

(defonce http-get-params {:cookie-policy :standard})

;; Ignore these keywords
;; :software_heritage_url :software_heritage_exists :derniere_modification
;; :page_accueil :date_creation :plateforme
(def repos-mapping
  "Mapping from repositories keywords to local short versions."
  {:nom                    :n
   :description            :d
   :organisation_nom       :o
   :langage                :l
   :licence                :li
   :repertoire_url         :r
   :topics                 :t
   :derniere_mise_a_jour   :u
   :nombre_forks           :f
   :nombre_issues_ouvertes :i
   :nombre_stars           :s
   :est_archive            :a?
   :est_fork               :f?})

;; Ignore these keywords
;; :private :default_branch :language :id :checked :owner :full_name
(def orgas-mapping
  "Mapping from groups/organizations keywords to local short versions."
  {:description        :d
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

(defonce
  ^{:doc "Mapping from GitHub license strings to the their license+SDPX short
  identifier version."}
  licenses-mapping
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

(defonce
  ^{:doc "A list of repositories dependencies, updated by the fonction
  `update-orgas-repos-deps` and stored for further retrieval in
  `update-deps`."}
  repos-deps
  (atom nil))

(defonce
  ^{:doc "The parsed output of retrieving orgas-url, updated by the fonction
  `update-orgas-json` and stored for further retrieval in
  `update-orgas` and `update-orgas-repos-deps`."}
  orgas-json
  (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Download repos, orgas and annuaire locally

(def cleanup-repos
  (comp
   (distinct)
   (map #(clset/rename-keys (select-keys % (keys repos-mapping)) repos-mapping))
   (map (fn [r] (assoc
                 r
                 :li (get licenses-mapping (:li r))
                 :dp (not (empty? (first (filter #(= (:n %) (:n r))
                                                 @repos-deps)))))))
   (map (fn [r] (update
                 r
                 :d
                 (fn [d]
                   (let [desc (atom d)]
                     (doseq [e emoji-json]
                       (swap! desc (fn [x]
                                     (when (string? x)
                                       (s/replace x (:name e) (:char e))))))
                     @desc)))))))

(defn update-repos
  "Generate data/repos.json from `repos-url`."
  []
  (when-let [repos-json
             (try (json/parse-string
                   (:body (http/get repos-url)) true)
                  (catch Exception e
                    (timbre/error "Can't reach repos-url")))]
    (spit "data/repos.json"
          (json/generate-string
           (sequence cleanup-repos repos-json)))
    (timbre/info "updated repos.json")))

(defn update-orgas-json
  "Reset `orgas-json` from `orgas-url`."
  []
  (let [old-orgas-json @orgas-json
        result
        (try (:body (http/get orgas-url))
             (catch Exception e
               (do (timbre/error (str "Can't get groups: "
                                      (:cause (Throwable->map e))))
                   old-orgas-json)))]
    (reset! orgas-json (distinct (json/parse-string result true))))
  (timbre/info (str "updated @orgas-json (" (count @orgas-json) " organisations)")))

(defn update-orgas
  "Generate data/orgas.json from `orgas-json` and `annuaire-url`."
  []
  (when-let [annuaire (apply merge
                             (map #(let [{:keys [github lannuaire]} %]
                                     {(keyword github) lannuaire})
                                  (-> (try (:body (http/get annuaire-url))
                                           (catch Exception e
                                             (timbre/error
                                              "Can't reach annuaire-url")))
                                      data-csv/read-csv
                                      semantic-csv/mappify)))]
    (spit "data/orgas.json"
          (json/generate-string
           (map #(assoc %
                        :an ((keyword (:l %)) annuaire)
                        :dp (let [f (str "data/deps/orgas/" (:l %) ".json")]
                              (if (.exists (io/file f))
                                (not (empty? (json/parse-string (slurp f)))))))
                (map #(clset/rename-keys % orgas-mapping)
                     @orgas-json))))
    (timbre/info (str "updated orgas.json"))))

(defn get-deps
  "Scrap backyourstack to get dependencies of an organization."
  [orga]
  (if-let [deps (try (http/get
                      (str "https://backyourstack.com/" orga "/dependencies")
                      http-get-params)
                     (catch Exception e
                       (timbre/error (str "Can't get dependencies: "
                                          (:cause (Throwable->map e))))))]
    (-> deps
        :body
        h/parse
        h/as-hickory
        (as-> dps (hs/select (hs/child (hs/id :__NEXT_DATA__)) dps))
        first
        :content
        first
        (json/parse-string true)
        :props
        :pageProps)))

(defn extract-deps-repos
  [orga]
  (let [s-deps #(select-keys
                 (clset/rename-keys % {:type :t :name :n})
                 [:t :n :core :dev])]
    (comp
     (filter #(not (empty? (:dependencies %))))
     (map #(select-keys % [:name :dependencies]))
     (map #(clset/rename-keys % {:name :n :dependencies :d}))
     (map #(assoc % :d (map (fn [r] (s-deps r)) (:d %))))
     (map #(assoc % :g orga)))))

(defonce extract-orga-deps
  (comp
   (map #(apply dissoc % [:project :peer :engines]))
   (map (fn [r]
          (let [rs (:repos r)]
            (assoc r :repos (map #(dissoc % :id) rs)))))))

(defn update-orgas-repos-deps
  "Generate data/deps/orgas/* and data/deps/repos-deps.json.
  Also reset the `repos-deps` atom."
  []
  (reset! repos-deps nil)
  (let [gh-orgas (map :login (filter #(= (:plateforme %) "GitHub") @orgas-json))]
    (doseq [orga gh-orgas]
      (if-let [data (get-deps orga)]
        (let [orga-deps  (sequence extract-orga-deps (:dependencies data))
              orga-repos (sequence (extract-deps-repos orga) (:repos data))]
          (spit (str "data/deps/orgas/" (s/lower-case orga) ".json")
                (json/generate-string orga-deps))
          (swap! repos-deps (partial apply conj) orga-repos))))
    (spit (str "data/deps/repos-deps.json")
          (json/generate-string @repos-deps))
    (timbre/info (str "updated orgas dependencies and "
                      (count @repos-deps) " repos dependencies"))))

(defn merge-colls [a b]
  (if (and (coll? a) (coll? b)) (into a b) b))

(defonce reduce-deps
  (comp
   (map #(apply (partial merge-with merge-colls) %))
   (map #(assoc % :rs (count (:rs %))))))

(defn update-deps
  "Generate data/deps/deps*.json."
  []
  (let [deps (atom nil)]
    (doseq [repo @repos-deps :let [r-deps (:d repo)]]
      (doseq [d0   r-deps
              :let [d (apply dissoc d0 [:core :dev :peer :engines])]]
        (swap! deps conj (assoc d :rs (vector (dissoc repo :d))))))
    (reset! deps
            (reverse (sort-by :rs (sequence reduce-deps
                                            (vals (group-by :n @deps))))))
    (spit "data/deps/deps-total.json"
          (json/generate-string {:deps-total (count @deps)}))
    (spit "data/deps/deps-top.json"
          (json/generate-string (take 100 @deps)))
    (timbre/info (str "updated deps-top and deps-total ("
                      (count @deps) ")"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Define tasks

(defn start-tasks []
  (tt/start!)
  (tt/every! 10800 update-orgas-json) ; set @orgas-json
  (tt/every! 10800 10 update-orgas-repos-deps) ; set @repos-deps
  (tt/every! 10800 240 update-deps)
  (tt/every! 10800 260 update-repos) ; use @repos-deps
  (tt/every! 10800 280 update-orgas) ; use @orgas-json
  (timbre/info "Tasks started!"))
;; (tt/cancel! update-*!)

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
  (let [deps (first (filter #(= (s/lower-case (:n %)) (s/lower-case repo))
                            @repos-deps))]
    (assoc
     (response/response
      (json/generate-string {:g (:g deps) :d (:d deps)}))
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
  (GET "/deps-total" [] (resource-json "data/deps/deps-total.json"))
  (GET "/deps" [] (resource-json "data/deps/deps-top.json"))

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
  (start-tasks)
  (jetty/run-jetty app {:port config/codegouvfr_port})
  (println (str "codegouvfr application started on locahost:" config/codegouvfr_port)))

;; (-main)
