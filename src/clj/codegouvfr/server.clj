;; Copyright (c) 2019 DINSIC, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.server
  (:require [ring.util.response :as response]
            [clojure.java.io :as io]
            [codegouvfr.config :as config]
            [clojure.instant :as inst]
            [org.httpkit.server :as server]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.params :as params]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [markdown-to-hiccup.core :as md]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [cheshire.core :as json]
            [clj-rss.core :as rss]
            [clj-http.client :as http]
            [hiccup.page :as h]
            [hiccup.element :as he]
            [ring.util.anti-forgery :as afu]
            [postal.core :as postal]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]
            [taoensso.timbre.appenders (postal :as postal-appender)])
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

(defonce last-repositories-url "https://api-codes-sources-fr.antoine-augusti.fr/api/stats/last_repositories")

(defn md-to-string [s]
  (-> s (md/md->hiccup) (md/component)))

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
               :message-id #(postal.support/message-id "mail.etalab.studio")
               :to         config/admin-email
               :subject    (str name " / " organization)
               :body       message})]
      (when (= (:error res) :SUCCESS) (timbre/info log)))
    (catch Exception e
      (timbre/error (str "Can't send email: " (:cause (Throwable->map e)))))))

(defn default-page []
  (assoc
   (response/response
    (io/input-stream
     (io/resource
      "public/index.html")))
   :headers {"Content-Type" "text/html; charset=utf-8"}))

(defn template-page [title subtitle content]
  (h/html5
   {:lang "fr-FR"}
   [:head
    [:title title]
    [:meta {:charset "utf-8"}]
    [:meta {:name "keywords" :content "Accès aux codes sources du secteur public"}]
    [:meta {:name "description" :content "Accès aux codes sources du secteur public"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, shrink-to-fit=yes"}]
    (h/include-css "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.9.0/css/all.min.css")
    (h/include-css "/css/style.css")]
   [:body
    [:nav {:class "navbar" :role "navigation" :aria-label "main navigation"}
     [:div {:class "navbar-brand"}
      [:a {:class "navbar-item" :href "/"}
       [:img {:src    "/images/logo-marianne.svg"
              :alt    "Logo Marianne"
              :width  "120"
              :height "100"}
        "code.etalab.gouv.fr (alpha)"]]]
     [:div {:class "navbar-menu"}
      [:div {:class "navbar-end"}
       [:a {:href "/apropos" :title "Pourquoi ce site ?" :class "navbar-item"} "À propos"]
       [:a {:href "/contact" :title "Signalez-nous vos ouvertures de codes sources" :class "navbar-item"} "Contact"]
       [:a {:href  "https://www.etalab.gouv.fr"
            :title "Site principal d'Etalab"
            :class "navbar-item"} "Etalab"]
       [:a {:href  "latest.xml" :target "new"
            :title "S'abonner au flux RSS des derniers dépôts"
            :class "navbar-item button"} [:span {:class "icon"}
                                          [:i {:class "fas fa-rss"}]]]]]]
    [:section {:class "hero"}
     [:div {:class "hero-body"}
      [:div {:class "container"}
       [:h1 {:class "title has-text-centered"} title]
       [:h2 {:class "subtitle column is-8 is-offset-2 has-text-centered"} subtitle]]]]
    [:section {:class "section"}
     [:div {:class "column is-8 is-offset-2"}
      content]]
    [:footer {:class "footer"}
     [:div {:class "content"}
      [:div {:class "columns"}
       [:div {:class "column is-offset-2 is-4"}
        [:img {:src "/images/etalab.svg" :width "240px"}]]
       [:div {:class "column is-offset-1 is-4"}
        [:h1 "code.etalab.gouv.fr"]
        [:p "Site développé par la mission Etalab"]]]]]]))

(defn contact-page []
  (template-page
   "Formulaire de contact"
   "Un compte d'organisation à signaler ? Un dépôt de code à ouvrir ? N'hésitez pas à nous solliciter !"
   [:form
    {:action "/contact" :method "post"}
    (afu/anti-forgery-field)
    [:div {:class "columns"}
     [:div {:class "field column is-6"}
      [:label {:class "label"} "Votre nom"]
      [:div {:class "control"}
       [:input {:name        "name" :type  "text"
                :size        "30"   :class "input"
                :placeholder "Nom"}]]]
     [:div {:class "field column is-6"}
      [:label {:class "label"} "Votre adresse de courriel"]
      [:div {:class "control"}
       [:input {:name        "email"                              :type     "email"
                :size        "30"                                 :class    "input"
                :placeholder "Par ex. toto@modernisation.gouv.fr" :required true}]]]]
    [:div {:class "field"}
     [:label {:class "label"} "Votre organisme public de rattachement"]
     [:div {:class "control"}
      [:input {:name        "organization" :type  "text"
               :size        "30"           :class "input"
               :placeholder "Par ex. DGFIP"}]]]
    [:div {:class "field"}
     [:label {:class "label"} "Message"]
     [:div {:class "control"}
      [:textarea {:class       "textarea"      :rows     "10"
                  :name        "message"             
                  :placeholder "Votre message" :required true}]]]
    [:div {:class "field is-pulled-right"}
     [:div {:class "control"}
      [:input {:type  "submit"
               :value "Envoyer"
               :class "button is-medium is-info"}]]]
    [:br]]))

(defn thanks-page []
  (template-page "Message reçu !" "Nous nous efforçons de répondre au plus vite."
                 [:div {:class "has-text-centered"}
                  [:a {:class "button is-large is-primary"
                       :href  "/"}
                   "Retour à la liste des dépôts de code source"]]))

(defn about-page []
  (template-page
   "À propos de code.etalab.gouv.fr" "D'où viennent les données, à quoi peuvent-elles servir ?"
   [:div
    [:div {:class "container"}
     [:h1 {:class "title"} "D'où viennent les données ?"]
     (md-to-string "Dans le cadre de la <a target=\"new\" href=\"https://www.numerique.gouv.fr/publications/politique-logiciel-libre/\">Polique de contribution de l'État aux logiciels libres</a>, la DINSIC collecte la <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">liste des comptes d'organisation</a> où des organismes publics partagent leurs codes sources. Nous nous servons de cette liste pour collecter des métadonnées sur tous les dépôts de code source.  Ces métadonnées sont publiées <a href=\"https://www.data.gouv.fr/fr/datasets/inventaire-des-depots-de-code-source-des-organismes-publics/\">sur data.gouv.fr</a> ou interrogeables depuis <a href=\"https://api-codes-sources-fr.antoine-augusti.fr/api/repertoires/all\">cette API</a>.")
     [:br]
     (md-to-string "Le partage des codes sources est imposé par la <strong>Loi pour une République numérique</strong> : tout code source obtenu ou développé par un organisme public est considéré comme un « document administratif » devant par défaut être publié en open data.  Pour connaître les conditions d'ouverture d'un logiciel du secteur public, vous pouvez consulter ce <a target=\"new\" href=\"https://guide-juridique-logiciel-libre.etalab.gouv.fr\">guide interactif</a>.")
     [:br]
     [:h2 {:class "subtitle"} "« Je ne vois pas mes codes sources ! »"]
     (md-to-string "C'est sûrement que votre forge ou votre compte d'organisation n'est pas <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">référencé ici</a>.  <a href=\"/contact\">Écrivez-nous</a> et nous ajouterons votre forge ou votre compte d'organisation.")
     [:br]]
    [:div {:class "container"}
     [:h1 {:class "title"} "En quoi cette liste peut m'être utile ?"]
     [:p [:strong "Vous êtes un organisme public ?"] " Avant de développer du code source par vous-même, vous pouvez chercher si du code déjà développé n'est pas disponible. Vous pouvez aussi repérer des projets qui vous intéressent pour vous rapprocher des organismes porteurs et leur demander comment contribuer."]
     [:br]
     [:p [:strong "Vous vous y connaissez en code ?"] " Vous pouvez trouver des projets qui vous intéressent et contribuer."]
     [:br]]
    [:div {:class "container"}
     [:h1 {:class "title"} "Mini-glossaire"]
     [:p "<strong>Codes sources</strong> : le code source d'un programme informatique est ce qu'écrit une programmeuse ou un programmeur.  Il peut s'agir de programmes complexes ou de quelques lignes. Ce code source peut être partagé sous licence libre pour permettre aux autres programmeurs de l'étudier, de le modifier, de le diffuser et de partager leurs améliorations."]
     [:br]
     (md-to-string "<strong>Secteur public</strong> : les codes sources développés dans le cadre de missions de service public ont vocation à être publiés, dans certains conditions. Ce site propose de chercher dans l'ensemble des codes sources aujourd'hui identifiés comme provenant d'un organisme public. Il a été développé par [la mission Etalab](https://www.etalab.gouv.fr/).")
     [:br]
     (md-to-string "<strong>Fourche</strong> : un dépôt « fourché » (ou « forké » en franglais) est un dépôt de code source qui a été développé à partir d'un autre.")
     [:br]
     (md-to-string "<strong>Software Heritage (SWH)</strong> : <a target=\"new\" href=\"https://www.softwareheritage.org/\">Software Heritage</a> est un projet dont le but est d'archiver tous les codes sources disponibles.  Pour chaque dépôt référencé sur ce site, nous donnons le lien vers la version archivée sur Software Heritage.")
     [:br]]
    [:div {:class "container"}
     [:h1 {:class "title"} "Puis aider à faire évoluer ce site ?"]
     (md-to-string "<strong>Oui !</strong> La collecte des <a target=\"new\" href=\"https://github.com/etalab/data-codes-sources-fr\">métadonnées des dépôts</a> et l'<a target=\"new\" href=\"https://github.com/AntoineAugusti/api-codes-sources-fr\">API</a> sont maintenus par Antoine Augusti (Etalab) ; le site que vous consultez est <a href=\"https://github.com/etalab/codegouvfr\">développé ici</a> par Bastien Guerry (Etalab).  N'hésitez pas à faire des suggestions sur ces dépôts, ils sont sous licence libre et toute contribution est la bienvenue.")
     [:br]]
    [:div {:class "container"}
     [:h1 {:class "title"} "Une question ?"]
     (md-to-string "Pour suivre l'actualité des logiciels libres utilisés et produits par l'administration, inscrivez-vous à la <a target=\"new\" href=\"https://lists.eig-forever.org/subscribe/bluehats@mail.etalab.studio\">gazette #bluehats</a>.")
     [:br]
     (md-to-string "Et pour toute autre question, n'hésitez pas à [nous écrire](/contact).")]]))

(defn codegouvfr-latest-repositories []
  (let [reps (try (http/get last-repositories-url)
                  (catch Exception e nil))]
    (json/parse-string (:body reps) true)))

(defn rss-feed []
  (rss/channel-xml
   {:title       "Derniers dépôts de codes sources publics"
    :link        "https://code.eig-forever.org/latest.xml"
    :description "Derniers dépôts de codes sources publics"}
   (map (fn [item] {:title       (:nom item)
                    :link        (:repertoire_url item)
                    :description (:description item)
                    :author      (:organisation_nom item)
                    :pubDate     (inst/read-instant-date (:derniere_mise_a_jour item))})
        (codegouvfr-latest-repositories))))

(defn rss-page []
  (assoc
   (response/response (rss-feed))
   :headers {"Content-Type" "text/xml; charset=utf-8"}))

(defroutes routes
  (GET "/latest.xml" [] (rss-page))
  (GET "/" [] (default-page))
  (GET "/contact" [] (contact-page))
  (GET "/merci" [] (thanks-page))
  (POST "/contact" req
        (let [params (clojure.walk/keywordize-keys (:form-params req))]
          (send-email (conj params {:log (str "Sent message from " (:name params)
                                              " (" (:organization params) ")")}))
          (response/redirect "/merci")))
  (GET "/apropos" [] (about-page))
  (GET "/:page" [page] (default-page))
  (resources "/")
  (not-found "Not Found"))

(def app (-> #'routes
             (wrap-defaults site-defaults)
             params/wrap-params
             wrap-reload))

(defn -main [& args]
  (def server (server/run-server app {:port config/codegouvfr_port}))
  (println (str "Server started on locahost:" config/codegouvfr_port)))

;; (-main)

