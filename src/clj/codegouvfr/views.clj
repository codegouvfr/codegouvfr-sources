;; Copyright (c) 2019-2021 DINUM, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.views
  (:require [cheshire.core :as json]
            [clojure.instant :as inst]
            [clj-rss.core :as rss]
            [clj-http.client :as http]
            [hiccup.page :as h]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.util.anti-forgery :as afu]
            [ring.util.response :as response]
            [codegouvfr.i18n :as i]
            [codegouvfr.config :as config]
            [codegouvfr.md :as md]))

(defonce ^{:doc "The URL for the latest repositories."}
  latest-repositories-url
  "https://api-code.etalab.gouv.fr/api/stats/last_repositories")

(defn latest-repositories []
  (let [reps (try (http/get latest-repositories-url)
                  (catch Exception e
                    (println (.getMessage e))))]
    (json/parse-string (:body reps) true)))

(defn rss-feed
  "Generate a RSS feed from `lastest-repositories`."
  []
  (rss/channel-xml
   {:title       (i/i "en" [:last-repos])
    :link        "https://code.etalab.gouv.fr/latest.xml"
    :description (i/i "en" [:last-repos])}
   (map (fn [item] {:title       (:nom item)
                    :link        (:repertoire_url item)
                    :description (:description item)
                    :author      (:organisation_nom item)
                    :pubDate     (inst/read-instant-date (:derniere_mise_a_jour item))})
        (latest-repositories))))

(defn rss
  "Expose the RSS feed."
  []
  (assoc
   (response/response (rss-feed))
   :headers {"Content-Type" "text/xml; charset=utf-8"}))

(defn head [title lang]
  [:head
   [:title title]
   [:meta {:charset "utf-8"}]
   [:meta {:name "keywords" :content (i/i lang [:keywords])}]
   [:meta {:name "description" :content (i/i lang [:keywords])}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1, shrink-to-fit=yes"}]
   [:meta {:property "og:locale" :content "fr_FR"}]
   [:meta {:property "og:type" :content "website"}]
   [:meta {:property "og:title" :content title}]
   [:meta {:property "og:url" :content "https://code.etalab.gouv.fr"}]
   [:meta {:property "og:site_name" :content title}]
   [:meta {:property "og:image" :content "https://www.etalab.gouv.fr/wp-content/uploads/2019/06/etalab-white.png"}]
   [:meta {:property "twitter:card" :content "summary_large_image"}]
   [:meta {:property "twitter:title" :content title}]
   [:meta {:property "twitter:site" :content "@Etalab"}]
   [:meta {:property "twitter:creator" :content "@Etalab"}]
   [:link {:rel "canonical" :href "https://code.etalab.gouv.fr"}]
   [:link {:rel   "alternate" :type "application/rss+xml"
           :title "RSS feed"  :href "https://code.etalab.gouv.fr/latest.xml"}]
   (h/include-css "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.11.2/css/all.min.css")
   (h/include-css (str config/codegouvfr_base_url "/css/style.css"))
   (h/include-css (str config/codegouvfr_base_url "/css/dsfr.min.css"))
   [:script {:src "https://tag.aticdn.net/619928/smarttag.js"}]
   [:script "var ATTag = new ATInternet.Tracker.Tag(); ATTag.page.send({name:'Page_Name'});"]
   [:script {:type "text/javascript" :async true} "var _paq = window._paq || [];_paq.push(['trackPageView']);_paq.push(['enableLinkTracking']);(function(){var u=\"//stats.data.gouv.fr/\";_paq.push(['setTrackerUrl', u+'piwik.php']);_paq.push(['setSiteId', '95']);var d=document, g=d.createElement('script'), s=d.getElementsByTagName('script')[0];g.type='text/javascript'; g.async=true; g.defer=true; g.src=u+'matomo.js'; s.parentNode.insertBefore(g,s);})();"]
   [:noscript [:p [:img {:src "//stats.data.gouv.fr/piwik.php?idsite=95&rec=1" :style "border:0;" :alt ""}]]]])

(defn footer [lang]
  [:footer.fr-footer {:role "contentinfo"}
   [:div.fr-container
    [:div.fr-footer__body
     [:div.fr_footer__brand.fr-enlarge-link
      [:a.fr-link {:href "/" :title "Retour à l'accueil"}
       [:p.fr-logo "République<br>Française"]]]
     [:div.fr-footer__content
      [:p.fr-footer__content-desc "Codes sources du secteur public"]
      [:ul.fr-footer__content-list
       [:li.fr-footer__content-item
        [:a.fr-footer__content-link
         {:href "https://data.gouv.fr"} "data.gouv.fr"]]
       [:li.fr-footer__content-item
        [:a.fr-footer__content-link
         {:href "https://etalab.gouv.fr"} "etalab.gouv.fr"]]
       [:li.fr-footer__content-item
        [:a.fr-footer__content-link
         {:href "https://numerique.gouv.fr"} "numerique.gouv.fr"]]
       [:li.fr-footer__content-item
        [:a.fr-footer__content-link
         {:href "https://gouvernement.fr"} "gouvernement.fr"]]]]]
    [:div.fr-footer__bottom
     [:ul.fr-footer__bottom-list
      [:li.fr-footer__bottom-item
       [:a {:href "mailto:logiciels-libres@data.gouv.fr"} "Contact"]]
      [:li.fr-footer__bottom-item
       [:p "Accessibilité: conforme à 75%"]]
      [:li.fr-footer__bottom-item
       [:p "Mentions légales"]]]]]])

;; (def navbar
;;   [:nav.navbar {:role "navigation" :aria-label "main navigation"}
;;    [:div.navbar-brand
;;     [:a.navbar-item {:href "https://code.etalab.gouv.fr"}
;;      [:img {:src    "/images/logo-marianne.svg"
;;             :alt    "Logo Marianne"
;;             :width  "120"
;;             :height "100"}
;;       "code.etalab.gouv.fr (beta)"]]]
;;    [:div.navbar-menu
;;     [:div.navbar-end
;;      [:a.navbar-item
;;       {:href (str "/" lang "/contact") :title (i/i lang [:report-new-source-code])}
;;       (i/i lang [:contact])]
;;      [:a.navbar-item
;;       {:href (str "/" lang "/glossary") :title (i/i lang [:understand-tech-terms])}
;;       (i/i lang [:glossary])]
;;      [:a.navbar-item {:href (str "/" lang "/about") :title (i/i lang [:why-this-website?])}
;;       (i/i lang [:about])]
;;      [:a.navbar-item {:href  "https://www.etalab.gouv.fr"
;;                       :title (i/i lang [:main-etalab-website])} "Etalab"]
;;      [:a.navbar-item.button
;;       {:href  "/latest.xml" :target "new"
;;        :title (i/i lang [:subscribe-rss-flux])}
;;       [:span.icon [:i.fas.fa-rss]]]]]])

(defn banner [lang title subtitle]
  [:header.fr-header {:role "banner"}
   ;; Header body
   [:div.fr-header__body
    [:div.fr-container
     [:div.fr-header__body-row
      [:div.fr-header__brand.fr-enlarge-link
       [:div.fr-header__brand-top
        [:div.fr-header__logo
         [:p.fr-logo "République<br>Française"]]
        [:div.fr-header__navbar
         [:button.fr-btn--menu.fr-btn
          {:data-fr-opened false
           :aria-controls  "header-navigation"
           :aria-haspopup  "menu"
           :title          "menu"}
          "Menu"]]]
       [:div.fr-header__service
        [:a {:href "/" :title title}
         [:p.fr-header__service-title title]]
        [:p.fr-header__service-tagline subtitle]]]
      [:div.fr-header__tools
       [:div.fr-header__tools-links
        [:ul.fr-links-group
         [:li
          [:a.fr-link.fr-fi-mail-line
           {:href "mailto:logiciels-libres@data.gouv.fr"}
           "Contact"]]]]]]]]
   ;; Header menu
   [:div#header-navigation.fr-header__menu.fr-modal
    {:aria-labelledby "button-menu"}
    [:div.fr-container
     [:button.fr-link--close.fr-link
      {:aria-controls "header-navigation"} "Fermer"]
     [:div.fr-header__menu-links]
     [:nav.fr-nav {:role "navigation" :aria-label "Menu principal"}
      [:ul.fr-nav__list
       [:li.fr-nav__item
        [:a.fr-nav__link {:href "groups" :target "_self"}
         "Organisation ou groupes"]]
       [:li.fr-nav__item
        [:a.fr-nav__link {:href "repos" :target "_self"}
         "Dépôts de code source"]]
       [:li.fr-nav__item
        ;; FIXME: Use fr-nav__item--active or :aria-current "/fr/deps"?
        [:a.fr-nav__link {:href "deps" :target "_self"}
         "Dépendances"]]
       ;; [:li.fr-nav__item.fr-nav__item--active
       ;;  [:a.fr-nav__link {:href         "stats"
       ;;                    :target       "_self"
       ;;                    :aria-current "stats"}
       ;;   "Chiffres"]]
       ]]]]
   ])

(defn default [lang & [title subtitle content]]
  (let [title    (or title (i/i lang [:index-title]))
        subtitle (or subtitle (i/i lang [:index-subtitle]))
        content0 (if content
                   [:div content]
                   [:div#app])]
    (h/html5
     {:lang lang}
     (head title lang)
     [:body
      (let [csrf-token (force anti-forgery/*anti-forgery-token*)]
        [:div#sente-csrf-token {:data-csrf-token csrf-token}])
      (banner lang title subtitle)
      [:div content0]
      (when-not content
        [:div
         [:script {:src (str config/codegouvfr_base_url "/js-dsfr/dsfr.nomodule.min.js")}]
         [:script {:src (str config/codegouvfr_base_url "/js/codegouvfr.js")}]
         [:script "codegouvfr.core.init();"]])
      (footer lang)])))

;; (defn contact
;;   "Contact template."
;;   [lang]
;;   (default
;;    lang
;;    (i/i lang [:contact-form])
;;    (i/i lang [:contact-baseline])
;;    [:form
;;     {:action "/contact" :method "post"}
;;     (afu/anti-forgery-field)
;;     [:input {:name "lang" :type "hidden" :value lang}]
;;     [:div.columns
;;      [:div.field.column.is-6
;;       [:label.label (i/i lang [:your-name])]
;;       [:div.control
;;        [:input.input {:name "name" :type        "text"
;;                       :size "30"   :placeholder "Nom"}]]]
;;      [:div.field.column.is-6
;;       [:label.label (i/i lang [:your-email])]
;;       [:div.control
;;        [:input.input
;;         {:name        "email"
;;          :type        "email"
;;          :size        "30"
;;          :placeholder (i/i lang [:email-placeholder]) :required true}]]]]
;;     [:div.field
;;      [:label.label (i/i lang [:your-affiliation])]
;;      [:div.control
;;       [:input.input {:name "organization" :type        "text"
;;                      :size "30"           :placeholder (i/i lang [:affiliation-placeholder])}]]]
;;     [:div.field
;;      [:label.label (i/i lang [:your-message])]
;;      [:div.control
;;       [:textarea.textarea
;;        {:rows        "10"
;;         :name        "message"
;;         :placeholder (i/i lang [:message-placeholder]) :required true}]]]
;;     [:div.field.is-pulled-right
;;      [:div.control
;;       [:input.button.is-medium.is-info {:type  "submit"
;;                                         :value (i/i lang [:submit])}]]]
;;     [:br]]))

;; (defn ok
;;   "Template for the contact form feedback."
;;   [lang] ;; FIXME: unused
;;   (default
;;    lang
;;    (i/i lang [:message-received])
;;    (i/i lang [:message-received-ok])
;;    [:div.has-text-centered
;;     [:a.fr-link {:href (str "/" lang "/repos")}
;;      (i/i lang [:back-to-repos])]]))

;; (defn fr-about [lang]
;;   (default
;;    lang
;;    "About code.etalab.gouv.fr" "D'où viennent les données, à quoi peuvent-elles servir ?"
;;    [:div
;;     [:div.container
;;      [:h1.title "D'où viennent les données ?"]
;;      (md/to-hiccup "Dans le cadre de la <a target=\"new\" href=\"https://www.numerique.gouv.fr/publications/politique-logiciel-libre/\">Politique de contribution de l'État aux logiciels libres</a>, la DINUM collecte la <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">liste des comptes d'organisation</a> où des organismes publics publient leurs codes sources. Nous nous servons de cette liste pour collecter des métadonnées sur tous les dépôts de code source.  Ces métadonnées sont publiées <a href=\"https://www.data.gouv.fr/fr/datasets/inventaire-des-depots-de-code-source-des-organismes-publics/\">sur data.gouv.fr</a> ou requêtables depuis <a href=\"https://api-code.etalab.gouv.fr/api/repertoires/all\">cette API</a>.")
;;      [:br]
;;      (md/to-hiccup "La publication des codes sources est imposée par la <strong>Loi pour une République numérique</strong> : tout code source obtenu ou développé par un organisme remplissant une mission de service public est considéré comme un « document administratif » devant être publié en open data.  Pour connaître les conditions d'ouverture d'un logiciel du secteur public, vous pouvez consulter ce <a target=\"new\" href=\"https://guide-juridique-logiciel-libre.etalab.gouv.fr\">guide interactif</a>.")
;;      [:br]
;;      [:h1.title "Je ne vois pas mes codes sources !"]
;;      (md/to-hiccup "C'est sûrement que votre forge, votre compte d'organisation ou votre groupe n'est pas <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">référencé ici</a>.")
;;      [:br]
;;      (md/to-hiccup "<a href=\"/contact\">Écrivez-nous</a> et nous ajouterons votre forge ou votre compte d'organisation.")
;;      [:br]]
;;     [:div.container
;;      [:h1.title "En quoi cette liste peut m'être utile ?"]
;;      [:p [:strong "Votre organisme accomplit une mission de service public ?"] " Avant de développer du code source par vous-même, vous pouvez chercher si du code déjà développé n'est pas disponible. Vous pouvez aussi repérer des projets qui vous intéressent pour vous rapprocher des organismes porteurs et leur demander comment contribuer."]
;;      [:br]
;;      [:p [:strong "Vous vous y connaissez en code ?"] " Vous pouvez trouver des projets qui vous intéressent et contribuer."]
;;      [:br]
;;      [:p [:strong "Je ne comprends pas ces mots !"] " Pas de panique : nous vous avons préparé un petit <a href=\"/fr/glossary\">glossaire</a> pour vous aider à tout comprendre."]
;;      [:br]]
;;     [:div.container
;;      [:h1.title "Puis-je partager un lien vers une requête ?"]
;;      [:p "Oui !  Vous pouvez utiliser les paramètres \"s\", \"g\", \"language\" et \"license\" comme dans ces exemples :"]
;;      [:br]
;;      [:ul
;;       [:li "Trouver les dépôts contenant \"API\" : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?q=API"} "https://code.etalab.gouv.fr/fr/repos?q=API"]]
;;       [:li "Trouver les dépôts des groupes contenant \"betagouv\" : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?g=betagouv"} "https://code.etalab.gouv.fr/fr/repos?g=betagouv"]]
;;       [:li "Trouver les groupes contenant \"medialab\" : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/groups?q=medialab"} "https://code.etalab.gouv.fr/fr/groups?q=medialab"]]
;;       [:li "Trouver les dépôts en Python : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?language=python"} "https://code.etalab.gouv.fr/fr/repos?language=python"]]
;;       [:li "Trouver les dépôts sous Affero GPL : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?license=Affero"} "https://code.etalab.gouv.fr/fr/repos?license=Affero"]]
;;       [:li "Combiner les requêtes : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?license=Affero&g=beta"} "https://code.etalab.gouv.fr/fr/repos?license=Affero&g=beta"]]]
;;      [:br]]
;;     [:div.container
;;      [:h1.title "Puis-je aider à faire évoluer ce site ?"]
;;      (md/to-hiccup "<strong>Oui !</strong> La collecte des <a target=\"new\" href=\"https://github.com/etalab/data-codes-sources-fr\">métadonnées des dépôts</a> et l'<a target=\"new\" href=\"https://github.com/etalab/api-codes-sources-fr\">API</a> sont maintenus par Antoine Augusti (Etalab) ; le site que vous consultez est <a href=\"https://github.com/etalab/code.etalab.gouv.fr\">développé ici</a> par Bastien Guerry (Etalab).  N'hésitez pas à faire des suggestions sur ces dépôts, ils sont sous licence libre et toute contribution est la bienvenue.")
;;      [:br]
;;      (md/to-hiccup "Pour suivre l'actualité des logiciels libres utilisés et produits par l'administration, inscrivez-vous à la <a target=\"new\" href=\"https://lists.eig-forever.org/subscribe/bluehats@mail.etalab.studio\">gazette #bluehats</a>.")
;;      [:br]
;;      (md/to-hiccup "Et pour toute autre question, n'hésitez pas à [nous écrire](/fr/contact).")
;;      [:br]]]))

;; (defn en-about [lang]
;;   (default
;;    lang
;;    "About code.etalab.gouv.fr" "Where does the data come from, what can it be used for?"
;;    [:div
;;     [:div.container
;;      [:h1.title "Where does the data come from?"]
;;      (md/to-hiccup "As part of the <a target=\"new\" href=\"https://www.numerique.gouv.fr/publications/politique-logiciel-libre/\">government's free software contribution policy</a>, DINUM collects the <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">list of organizational accounts</a> where public bodies share their source codes. We use this list to collect metadata about all source code repositories. These metadata are published on <a href=\"https://www.data.gouv.fr/fr/datasets/inventaire-des-depots-de-code-source-des-organismes-publics/\">data.gouv.fr</a> and queryable through <a href=\"https://api-code.etalab.gouv.fr/api/repertoires/all\">this API</a>.")
;;      [:br]
;;      (md/to-hiccup "The publication of source codes is prescribed by the <strong>French Digital Republic Act (Loi pour une République numérique)</strong> : each source code obtained or developed by an organization fulfilling a public service mission is considered an administrative document, and, therefore, has to be published in open data. To understand the requirements to publish a public sector software, check out this <a target=\"new\" href=\"https://guide-juridique-logiciel-libre.etalab.gouv.fr\">(french) interactive guide</a>.")
;;      [:br]
;;      [:h1.title "I can't find my source codes!"]
;;      (md/to-hiccup "It is likely that your software forge, your organization or group account is not <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">listed here</a>.")
;;      [:br]
;;      (md/to-hiccup "<a href=\"/contact\">Contact us</a> and we will add your forge or your organization account.")
;;      [:br]]
;;     [:div.container
;;      [:h1.title "How can this list help me?"]
;;      [:p [:strong "If your organization fulfills a public service mission:"] " Before you start programming, you can check for available source code. You can also look for projects you are interested in to get closer to lead organizations and ask them how to contribute."]
;;      [:br]
;;      [:p [:strong "If you know how to program:"] " You can find projects you are interested in and contribute to them."]
;;      [:br]
;;      [:p [:strong "I don't understand these words!"] " Don't worry: we made a short  <a href=\"/fr/glossary\">glossary</a> to help you better understand those words."]
;;      [:br]]
;;     [:div.container
;;      [:h1.title "Can I share a link to a query?"]
;;      [:p "Yes, you can!  You can use the parameters \"s\", \"g\", \"language\" and \"license\" as in the following examples:"]
;;      [:br]
;;      [:ul
;;       [:li "Find repositories containing \"API\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?q=API"} "https://code.etalab.gouv.fr/fr/repos?q=API"]]
;;       [:li "Find group repositories containing \"betagouv\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?g=betagouv"} "https://code.etalab.gouv.fr/fr/repos?g=betagouv"]]
;;       [:li "Find groups containing \"medialab\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/groups?q=medialab"} "https://code.etalab.gouv.fr/fr/groups?q=medialab"]]
;;       [:li "Find group repositories containing \"beta\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/groups?g=beta"} "https://code.etalab.gouv.fr/fr/groups?g=beta"]]
;;       [:li "Find repositories in Python: " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?language=python"} "https://code.etalab.gouv.fr/fr/repos?language=python"]]
;;       [:li "Find repositories under the Affero GPL license: " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?license=Affero"} "https://code.etalab.gouv.fr/fr/repos?license=Affero"]]
;;       [:li "Combine queries : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?license=Affero&g=beta"} "https://code.etalab.gouv.fr/fr/repos?license=Affero&g=beta"]]]
;;      [:br]]
;;     [:div.container
;;      [:h1.title "Can I help enhancing this website?"]
;;      (md/to-hiccup "<strong>Yes!</strong> Harvesting <a target=\"new\" href=\"https://github.com/etalab/data-codes-sources-fr\">metadata of repositories</a> and exposing them via the <a target=\"new\" href=\"https://github.com/etalab/api-codes-sources-fr\">API</a> is done by Antoine Augusti (Etalab). This frontend is developed <a href=\"https://github.com/etalab/code.etalab.gouv.fr\">here</a> by Bastien Guerry (Etalab).  Don't hesitate to send suggestions on these repositories, they are published under a free software license and every contribution is welcome.")
;;      [:br]
;;      (md/to-hiccup "To read news about free software used and developed by the French public sector, subscribe to the <a target=\"new\" href=\"https://lists.eig-forever.org/subscribe/bluehats@mail.etalab.studio\">#bluehats newsletter</a>.")
;;      [:br]
;;      (md/to-hiccup "For any other question, please [drop a message](/en/contact).")
;;      [:br]]]))

;; (defn it-about [lang]
;;   (default
;;    lang
;;    "A proposito di code.etalab.gouv.fr" "Da dove provengono i dati, per cosa possono essere utilizzati?"
;;    [:div
;;     [:div.container
;;      [:h1.title "Da dove provengono i dati"]
;;      (md/to-hiccup "Nel quadro della <a target=\"new\" href=\"https://www.numerique.gouv.fr/publications/politique-logiciel-libre/\">Politica di contribuzione dello Stato per il software libero</a>, il DINUM raccoglie la <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">lista dei repository delle organizzazioni</a> o degli organismi pubblici che condivido il loro codici sorgenti. Usiamo questo elenco per raccogliere metadati su tutti i repository di codice sorgente. Questi matadati sono pubblicati <a href=\"https://www.data.gouv.fr/fr/datasets/inventaire-des-depots-de-code-source-des-organismes-publics/\">su data.gouv.fr</a> o ricercabili da <a href=\"https://api-code.etalab.gouv.fr/api/repertoires/all\">questa API</a>.")
;;      [:br]
;;      (md/to-hiccup "La condivisione di codice sorgente è imposta dalla <strong>Legge per una Repubblica digitale</strong>: qualsiasi codice sorgente ottenuto o sviluppato da un'organizzazione che compie una missione di servizio pubblico è considerato un \"documento amministrativo\" da pubblicare come dato aperto. Per conoscere le condizioni di apertura di un software del settore pubblico, è possibile consultare questa <a target=\"new\" href=\"https://guide-juridique-logiciel-libre.etalab.gouv.fr\">guida interattiva</a>.")
;;      [:br]
;;      [:h1.title "Non vedo i miei sorgenti di codice!"]
;;      (md/to-hiccup "&Egrave; sicuramente perchè il vostro repository, il vostro account d'organizzazione o il vostro gruppo non è <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">registrato qui</a>.")
;;      [:br]
;;      (md/to-hiccup "<a href=\"/contact\">Scrivici</a> e noi aggiungeremo il vostor repository o il vostro accounti d'organizzazione.")
;;      [:br]]
;;     [:div.container
;;      [:h1.title "Come questa lista mi può essere utile?"]
;;      [:p [:strong "La tua organizzazione svolge una missione di servizio pubblico?"] "Prima di poter sviluppare del nuovo codice sorgente, puoi cercare che il codice che ti serve non sia già disponibile. Puoi anche trovare progetti che ti interessano ed avvicinarti alle organizzazioni che li hanno prodotti per chiedere loro come contribuire."]
;;      [:br]
;;      [:p [:strong "Conoscete un codice?"] " Potete trovare i progetti che ti interessano e contribuire."]
;;      [:br]
;;      [:p [:strong "Non capisco queste parole!"] " Niente panico: abbiamo preparato un piccolo <a href=\"/it/glossary\">glossario</a> per aiutarvi a comprendere."]
;;      [:br]]
;;     [:div.container
;;      [:h1.title "Posso condividere un collegamento a una richiesta?"]
;;      [:p "Sì! Potete usare i parametri \"s\", \"g\", \"language\" e \"license\" come in questo esempio:"]
;;      [:br]
;;      [:ul
;;       [:li "Travare i repository contenti \"API\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?q=API"} "https://code.etalab.gouv.fr/it/repos?q=API"]]
;;       [:li "Trovare i repository dei gruppi contenenti \"betagouv\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?g=betagouv"} "https://code.etalab.gouv.fr/it/repos?g=betagouv"]]
;;       [:li "Find groups containing \"medialab\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/groups?q=medialab"} "https://code.etalab.gouv.fr/fr/groups?q=medialab"]] ;; TODO
;;       [:li "Trovare i repository su Python: " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?language=python"} "https://code.etalab.gouv.fr/it/repos?language=python"]]
;;       [:li "Trovare i repository sotto licenza Affero GPL: " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?license=Affero"} "https://code.etalab.gouv.fr/it/repos?license=Affero"]]
;;       [:li "Combinare le richieste: " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?license=Affero&g=beta"} "https://code.etalab.gouv.fr/it/repos?license=Affero&g=beta"]]]
;;      [:br]]
;;     [:div.container
;;      [:h1.title "Posso aiutare a far evolvere questo sito?"]
;;      (md/to-hiccup "<strong>Sì!</strong> La raccoota di <a target=\"new\" href=\"https://github.com/etalab/data-codes-sources-fr\">metadati dei repository</a> e le <a target=\"new\" href=\"https://github.com/etalab/api-codes-sources-fr\">API</a> sono mantenute da Antoine Augusti (Etalab); il sito che state consultando è <a href=\"https://github.com/etalab/code.etalab.gouv.fr\">sviluppato</a> da Bastien Guerry (Etalab).  Hon esitate a dare suggerimenti su questi repository, sono sotto licenza libera e tutte le contribuzioni sono benvenute.")
;;      [:br]
;;      (md/to-hiccup "Per seguire le notizie del software gratuito utilizzato e prodotto dall'amministrazione, iscriviti alla <a target=\"new\" href=\"https://lists.eig-forever.org/subscribe/bluehats@mail.etalab.studio\">gazzetta #bluehats</a>.")
;;      [:br]
;;      (md/to-hiccup "E per qualsiasi altra domanda, non esitare a [scriverci](/contact).")
;;      [:br]]]))

;; (defn fr-glossary [lang]
;;   (default
;;    lang
;;    "Glossaire pour code.etalab.gouv.fr" "Qu'est-ce qu'un dépôt ? Une « organisation » ? Une licence ?"
;;    [:div
;;     [:div.container
;;      [:a {:name "source-code"} [:h2.subtitle "Codes sources"]]
;;      [:br]
;;      [:p "Le code source d'un programme informatique est ce qu'écrit une programmeuse ou un programmeur.  Il peut s'agir de programmes complexes ou de quelques lignes. Ce code source peut être partagé sous licence libre pour permettre aux autres programmeurs de l'étudier, de le modifier, de le diffuser et de partager leurs améliorations."]
;;      [:br]

;;      [:a {:name "repository"} [:h2.subtitle "Dépôt"]]
;;      [:br]
;;      (md/to-hiccup "Un « dépôt » est un espace dans lequel sont publiés les fichiers de code source. C'est ce que vous voyez lorsque vous visitez un lien vers un code source hébergé sur une forge. C'est aussi ce que vous pouvez copier sur votre machine pour l'explorer localement.")
;;      [:br]

;;      [:a {:name "dependencies"} [:h2.subtitle "Dépendances"]]
;;      [:br]
;;      (md/to-hiccup "Un logiciel intègre souvent des briques logicielles publiées sous licence libre.  Celles-ci sont appelées « dépendances ».  Ce site permet de parcourir la liste des dépendances de *mise en production*, non les dépendances de *développement* ; d'autre part, seules sont comprises les dépendances sollicitées par au moins deux dépôts.  Voir aussi [\"Réutilisations\"](#reuses).")
;;      [:br]

;;      [:a {:name "etoile"} [:h2.subtitle "Étoiles"]]
;;      [:br]
;;      (md/to-hiccup "Les « étoiles » (« stars » en anglais) sont un moyen pour les utilisateurs des plates-formes de mettre un dépôt en favori.  Pour l'instant, nous collectons cette information sur GitHub, GitLab et les instances de GitLab.  Ce n'est pas une mesure de la qualité du code source.")
;;      [:br]

;;      [:a {:name "fourche"} [:h2.subtitle "Fourche"]]
;;      [:br]
;;      (md/to-hiccup "Un dépôt « fourché » (ou « forké » en franglais) est un dépôt de code source qui a été développé à partir d'un autre.")
;;      [:br]

;;      [:a {:name "license"} [:h2.subtitle "Licence"]]
;;      [:br]
;;      (md/to-hiccup "Une licence logicielle est un contrat passé entre les auteurs d'un logiciel et ses réutilisateurs.  Les licences dites « libres » accordent aux utilisateurs le droit de réutiliser le code source d'un logiciel.")
;;      [:br]

;;      [:a {:name "organization-group"} [:h2.subtitle "Organisation et groupe"]]
;;      [:br]
;;      (md/to-hiccup "GitHub permet d'avoir des comptes personnels pour y héberger du code et des « comptes d'organisation ».  Un « groupe » est la notion plus ou moins équivalent sur les instance de GitLab.  Un organisme remplissant une mission de service public peut avoir un ou plusieurs organisations et/ou groupes sur une ou plusieurs forges.")
;;      [:br]

;;      [:a {:name "reuses"} [:h2.subtitle "Réutilisations"]]
;;      [:br]
;;      (md/to-hiccup "GitHub permet de connaître le nombre de dépôts qui en utilisent un autre: le nombre de ces dépôts est présenté ici dans la colonne \"Réutilisations\" de la liste des dépôts.  Voir aussi [\"Dépendances\"](#dependencies).")
;;      [:br]

;;      [:a {:name "secteur-public"} [:h2.subtitle "Secteur public"]]
;;      [:br]
;;      (md/to-hiccup "Les codes sources développés dans le cadre de missions de service public ont vocation à être publiés, dans certains conditions. Ce site propose de chercher dans l'ensemble des codes sources aujourd'hui identifiés comme provenant d'un organisme remplissant une mission de service public. Il a été développé par [la mission Etalab](https://www.etalab.gouv.fr/).")
;;      [:br]

;;      [:a {:name "software-heritage"} [:h2.subtitle "Software heritage"]]
;;      [:br]
;;      (md/to-hiccup "<a target=\"new\" href=\"https://www.softwareheritage.org/\">Software Heritage</a> est un projet dont le but est d'archiver tous les codes sources disponibles.  Pour chaque dépôt référencé sur ce site, nous donnons le lien vers la version archivée sur Software Heritage.")
;;      [:br]]]))

;; (defn en-glossary [lang]
;;   (default
;;    lang
;;    "Glossary for code.etalab.gouv.fr" "What is a repository? An organization? A license?"
;;    [:div
;;     [:div.container

;;      [:a {:name "dependencies"} [:h2.subtitle "Dependencies"]]
;;      [:br]
;;      (md/to-hiccup "A software often use open source libraries. These libraries are called \"dependencies\". This website allows you to browse dependencies.  Only *production* dependencies are considered, and only those who are required by two or more repositories.  See also [\"Reuses\"](#reuses).")
;;      [:br]

;;      [:a {:name "fourche"} [:h2.subtitle "Fork"]]
;;      [:br]
;;      (md/to-hiccup "A \"forked\" repository est is repository derived from another one.")
;;      [:br]

;;      [:a {:name "license"} [:h2.subtitle "Licence"]]
;;      [:br]
;;      (md/to-hiccup "A software licence is a contract between a software's authors and its end-users. So-called \"libre\" licences grant licensees permission to distribute, modify and share a software's source code.")
;;      [:br]

;;      [:a {:name "organization-group"} [:h2.subtitle "Organisation & Group"]]
;;      [:br]
;;      (md/to-hiccup "GitHub allows to have personal accounts or \"organizations accounts\" to store source code.  A \"group\" is the more or less equivalent notion used on GitLab instances.  A public sector organization may have one or more organization accounts and/or groups on one or several software forges.")
;;      [:br]

;;      [:a {:name "reuses"} [:h2.subtitle "Reuses"]]
;;      [:br]
;;      (md/to-hiccup "GitHub allows to get the number of repositories that depend on another repository: the number of \"dependants\" is listed here in the \"Reuse\" column of the list of repositories.  See also [\"Dependencies\"](#dependencies).")
;;      [:br]

;;      [:a {:name "secteur-public"} [:h2.subtitle "Public Sector"]]
;;      [:br]
;;      (md/to-hiccup "Source code developed by a public agency must be published, under certain conditions.  This website offers the possibility to search within source code repositories that are identified as coming from public sector organisms.  It has been developed by [Etalab](https://www.etalab.gouv.fr/).")
;;      [:br]

;;      [:a {:name "repository"} [:h2.subtitle "Repository"]]
;;      [:br]
;;      (md/to-hiccup "A \"repository\" is a place where source code files are stored.  This is what you see when you browse a link to a source code as hosted on a software forge.  This is also what you copy on your machine to explore it locally.")
;;      [:br]

;;      [:a {:name "software-heritage"} [:h2.subtitle "Software Heritage"]]
;;      [:br]
;;      (md/to-hiccup "<a target=\"new\" href=\"https://www.softwareheritage.org/\">Software Heritage</a> is a project whose ambition is to collect, preserve, and share all software that is publicly available in source code form. Each repository here referenced is linked to its corresponding Software Heritage archive version.")
;;      [:br]

;;      [:a {:name "source-code"} [:h2.subtitle "Source Code"]]
;;      [:br]
;;      [:p "A software's source code is what a developer writes.  Source code can be just a few lines or quite a few of them. Source code can be made available under a free licence for others to freely distribute, modify and share."]
;;      [:br]

;;      [:a {:name "etoile"} [:h2.subtitle "Stars"]]
;;      [:br]
;;      (md/to-hiccup "Stars allow users to mark a repository as favourite. At the moment, we collect repository favouriteness data from GitHub, Gitlab and instances thereof. Favouriteness is not a source code quality metric.")
;;      [:br]

;;      ]]))

;; (defn it-glossary [lang]
;;   (default
;;    lang
;;    "Glossario di code.etalab.gouv.fr" "Cosa è un repository? Un'organizzazione? Una licenza?"
;;    [:div
;;     [:div.container

;;      [:a {:name "source-code"} [:h2.subtitle "Codici sorgenti"]]
;;      [:br]
;;      [:p "Il codice sorgente di un programma informatico è quello che scrive una programmatrice o un programmatore. Questi possono essere sia programmi molto complessi che programmi di poche linee. Il codice sorgente può essere condiviso con una licenza aperta per consentire ad altri programmatori di studiare, modificare, distribuire e condividere i loro miglioramenti al codice."]
;;      [:br]

;;      ;; TODO: i18n
;;      [:a {:name "dependencies"} [:h2.subtitle "Dipendenze"]]
;;      [:br]
;;      (md/to-hiccup "Un software spesso utilizza librerie open source. Queste librerie sono chiamate \"dipendenze\". Questo sito web permette di navigare tra le dipendenze.  Sono considerate solo le dipendenze di *produzione* e solo quelle richieste da due o più repository.  Vedi anche [\"Riutilizzazioni\"](#reuses).")
;;      [:br]

;;      [:a {:name "fourche"} [:h2.subtitle "Fork"]]
;;      [:br]
;;      (md/to-hiccup "Una fork è un repository che è stato sviluppato partendo dal codice presente in un altro repository pubblico.")
;;      [:br]

;;      [:a {:name "license"} [:h2.subtitle "Licenze"]]
;;      [:br]
;;      (md/to-hiccup "Una licenza è un contratto sviluppato tra l'autore di un programma e i suoi utilizzatori. Le licenze dette \"libere\" concedono agli utilizzatori il diritto di riutilizzare il codice sorgente di un programma.")
;;      [:br]

;;      [:a {:name "organization-group"} [:h2.subtitle "Organizzazione e gruppi"]]
;;      [:br]
;;      (md/to-hiccup "GitHub è un sito di verisonamento del codice e permette di avere dei repository personali e dei \"repository di organizzazione\". Un gruppo è la nozione più o meno equivalente sulle istanze di GitLab. Un organismo che svolge una missione di servizio pubblico può avere una o più organizzazioni e/o gruppi su una o più siti di verisonamento del codice.")
;;      [:br]

;;      [:a {:name "repository"} [:h2.subtitle "Repository"]]
;;      [:br]
;;      (md/to-hiccup "Un repository è uno spazio dentro il quale vengono pubblicati i file di codice sorgente. Questo è ciò che si vede quando si visita un link al codice sorgente ospitato su un repository. &Egrave; anche quello che puoi copiare sul tuo computer per esplorarlo localmente.")
;;      [:br]

;;      [:a {:name "reuses"} [:h2.subtitle "Riutilizza"]]
;;      [:br]
;;      (md/to-hiccup "GitHub permette di ottenere il numero di repository che dipendono da un altro repository: il numero di \"dipendenti\" è elencato qui nella colonna \"Riutilizzo\" della lista dei repository.  Vedi anche [\"Dipendenze\"](#dependencies).")
;;      [:br]

;;      [:a {:name "secteur-public"} [:h2.subtitle "Settore pubblico"]]
;;      [:br]
;;      (md/to-hiccup "I codici sorgente sviluppati nell'ambito di servizi pubblici sono destinati a essere pubblicati in open source sotto determinate condizioni. Questo sito offre la possibilità di cercare nell'insieme di codici sorgenti oggi identificati come provenienti da un'organizzazione che svolge un compito di servizio pubblico. Il sito è stato sviluppato da [Etalab](https://www.etalab.gouv.fr/).")
;;      [:br]

;;      [:a {:name "software-heritage"} [:h2.subtitle "Software heritage"]]
;;      [:br]
;;      (md/to-hiccup "<a target=\"new\" href=\"https://www.softwareheritage.org/\">Software Heritage</a> è un progetto che ha lo scopo di archiviare tutti i codici sorgenti disponbili. Per ciascun repository referenziato in questo sito, viene fornito il link alla versione archiviata su Software Heritage.")
;;      [:br]

;;      [:a {:name "etoile"} [:h2.subtitle "Stelle"]]
;;      [:br]
;;      (md/to-hiccup "Le stelle («star» in inglese) sono un mezzo per permettere agi utilizzato delle piattaforme di versionamento del codice di mettere un repository tra i preferity. Al momento, noi memorizziamo questa informazion su GitHub, GitLab e le istanze private di GitLab. Questa non è una misura della qualità del codice sorgente.")
;;      [:br]

;;      ]]))
