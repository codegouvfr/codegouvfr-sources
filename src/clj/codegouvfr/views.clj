(ns codegouvfr.views
  (:require [cheshire.core :as json]
            [clojure.instant :as inst]
            [clj-rss.core :as rss]
            [clj-http.client :as http]
            [hiccup.page :as h]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.util.anti-forgery :as afu]
            [ring.util.response :as response]
            [markdown-to-hiccup.core :as md]
            [codegouvfr.i18n :as i]))

(defn md-to-hiccup
  "Convert a markdown `s` string to hiccup structure."
  [s]
  (-> s (md/md->hiccup) (md/component)))

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

(defn icons []
  [:svg {:aria-hidden "true", :focusable "false", :style "display:none"} [:defs  [:symbol {:viewbox "0 0 32 32", :fill-rule "evenodd", :clip-rule "evenodd", :stroke-linejoin "round", :stroke-miterlimit "1.414", :id "copy", :xmlns "http://www.w3.org/2000/svg"} [:path {:d "M19.75 8.5V1H6.625L1 6.625V23.5h11.25V31H31V8.5H19.75zM6.625 3.651v2.974H3.651l2.974-2.974zm-3.75 17.974V8.5H8.5V2.875h9.375V8.5l-5.625 5.625v7.5H2.875zm15-10.474v2.974h-2.974l2.974-2.974zm11.25 17.974h-15V16h5.625v-5.625h9.375v18.75z", :fill-rule "nonzero"}]] [:symbol {:viewbox "0 0 32 32", :fill-rule "evenodd", :clip-rule "evenodd", :stroke-linejoin "round", :stroke-miterlimit "1.414", :id "envelope", :xmlns "http://www.w3.org/2000/svg"} [:g {:fill-rule "nonzero"} [:path {:d "M30.1 5.419c-9.355-.002-18.733-.005-28.1-.005A1.06 1.06 0 0 0 .975 6.439v19.122A1.06 1.06 0 0 0 2 26.586h28a1.061 1.061 0 0 0 1.025-1.025V6.439a1.056 1.056 0 0 0-.925-1.02zM3.025 7.464h25.95v17.072H3.025V7.464z"}] [:path {:d "M30.06 9.513c.933.098 1.382 1.395.393 1.945L16.54 18.287c-.438.188-.479.178-.893 0L1.733 11.458c-1.743-.968-.065-2.254.894-1.842l13.466 6.61 13.562-6.651c.3-.094.312-.062.405-.062z"}]]] [:symbol {:viewbox "0 0 32 32", :fill-rule "evenodd", :clip-rule "evenodd", :stroke-linejoin "round", :stroke-miterlimit "1.414", :id "facebook", :xmlns "http://www.w3.org/2000/svg"} [:path {:d "M28.188 1H3.812A2.821 2.821 0 0 0 1 3.813v24.375A2.821 2.821 0 0 0 3.813 31H16V17.875h-3.75v-3.75H16V12.25a5.635 5.635 0 0 1 5.625-5.625h3.75v3.75h-3.75a1.88 1.88 0 0 0-1.875 1.875v1.875h5.625l-.937 3.75H19.75V31h8.438A2.821 2.821 0 0 0 31 28.187V3.812A2.821 2.821 0 0 0 28.188 1z"}]] [:symbol {:viewbox "0 0 32 32", :fill-rule "evenodd", :clip-rule "evenodd", :stroke-linejoin "round", :stroke-miterlimit "1.414", :id "github", :xmlns "http://www.w3.org/2000/svg"} [:path {:d "M16 1.371c-8.284 0-15 6.715-15 15 0 6.627 4.298 12.25 10.258 14.233.75.138 1.026-.326 1.026-.722 0-.357-.014-1.54-.021-2.793-4.174.907-5.054-1.77-5.054-1.77-.682-1.733-1.665-2.195-1.665-2.195-1.361-.931.103-.912.103-.912 1.506.106 2.299 1.546 2.299 1.546 1.338 2.293 3.509 1.63 4.365 1.247.134-.969.523-1.631.952-2.006-3.331-.379-6.834-1.666-6.834-7.413 0-1.638.586-2.976 1.546-4.027-.156-.378-.669-1.903.145-3.969 0 0 1.26-.403 4.126 1.537a14.453 14.453 0 0 1 3.755-.505c1.274.006 2.558.173 3.757.505 2.864-1.94 4.121-1.537 4.121-1.537.816 2.066.303 3.591.147 3.969.962 1.05 1.544 2.389 1.544 4.027 0 5.761-3.509 7.029-6.849 7.401.538.466 1.017 1.379 1.017 2.778 0 2.007-.018 3.623-.018 4.117 0 .399.27.867 1.03.72C26.707 28.616 31 22.996 31 16.371c0-8.285-6.716-15-15-15z", :fill-rule "nonzero"}]] [:symbol {:viewbox "0 0 32 32", :fill-rule "evenodd", :clip-rule "evenodd", :stroke-linejoin "round", :stroke-miterlimit "1.414", :id "googleplus", :xmlns "http://www.w3.org/2000/svg"} [:path {:d "M28.188 1H3.812A2.821 2.821 0 0 0 1 3.813v24.375A2.821 2.821 0 0 0 3.813 31h24.375A2.821 2.821 0 0 0 31 28.187V3.812A2.821 2.821 0 0 0 28.187 1zM12.251 23.5a7.493 7.493 0 0 1-7.5-7.5c0-4.148 3.352-7.5 7.5-7.5 2.027 0 3.721.738 5.028 1.963l-2.039 1.958c-.557-.534-1.529-1.154-2.989-1.154-2.56 0-4.653 2.121-4.653 4.734s2.092 4.734 4.653 4.734c2.971 0 4.084-2.133 4.254-3.234h-4.253v-2.573h7.084c.064.375.117.75.117 1.243 0 4.289-2.872 7.33-7.201 7.33l-.001-.001zm15-7.5h-1.875v1.875h-1.875V16h-1.875v-1.875h1.875V12.25h1.875v1.875h1.875V16z", :fill-rule "nonzero"}]] [:symbol {:viewbox "0 0 32 32", :fill-rule "evenodd", :clip-rule "evenodd", :stroke-linejoin "round", :stroke-miterlimit "1.414", :id "magnifier", :xmlns "http://www.w3.org/2000/svg"} [:path {:d "M30.07 26.529l-7.106-6.043c-.735-.662-1.521-.964-2.155-.936a11.194 11.194 0 0 0 2.691-7.299c0-6.214-5.036-11.25-11.25-11.25S1 6.037 1 12.251s5.036 11.25 11.25 11.25c2.786 0 5.334-1.012 7.299-2.691-.03.634.274 1.42.936 2.155l6.043 7.106c1.035 1.149 2.725 1.247 3.756.216 1.031-1.032.934-2.723-.216-3.756l.002-.002zm-17.82-6.78a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15z", :fill-rule "nonzero"}]] [:symbol {:viewbox "0 0 32 32", :fill-rule "evenodd", :clip-rule "evenodd", :stroke-linejoin "round", :stroke-miterlimit "1.414", :id "round-cross", :xmlns "http://www.w3.org/2000/svg"} [:path {:d "M16 .5C24.554.5 31.5 7.446 31.5 16S24.554 31.5 16 31.5.5 24.554.5 16 7.446.5 16 .5zm6.161 11.718a7.233 7.233 0 0 0-2.379-2.379L16 13.621l-3.782-3.782a7.233 7.233 0 0 0-2.379 2.379L13.621 16l-3.782 3.782a7.233 7.233 0 0 0 2.379 2.379L16 18.379l3.782 3.782a7.233 7.233 0 0 0 2.379-2.379L18.379 16l3.782-3.782z", :fill-rule "nonzero"}]] [:symbol {:viewbox "0 0 32 32", :fill-rule "evenodd", :clip-rule "evenodd", :stroke-linejoin "round", :stroke-miterlimit "1.414", :id "rss", :xmlns "http://www.w3.org/2000/svg"} [:path {:d "M28.188 1H3.812A2.821 2.821 0 0 0 1 3.813v24.375A2.821 2.821 0 0 0 3.813 31h24.375A2.821 2.821 0 0 0 31 28.187V3.812A2.821 2.821 0 0 0 28.187 1zM9.175 25.352a2.541 2.541 0 0 1-2.549-2.537 2.553 2.553 0 0 1 2.549-2.543 2.55 2.55 0 0 1 2.549 2.543 2.541 2.541 0 0 1-2.549 2.537zm6.399.023a8.913 8.913 0 0 0-2.62-6.339 8.882 8.882 0 0 0-6.328-2.625v-3.668c6.961 0 12.633 5.666 12.633 12.633h-3.685v-.001zm6.51 0c0-8.526-6.932-15.469-15.451-15.469V6.239c10.546 0 19.13 8.589 19.13 19.137h-3.68v-.001z", :fill-rule "nonzero"}]] [:symbol {:viewbox "0 0 32 32", :fill-rule "evenodd", :clip-rule "evenodd", :stroke-linejoin "round", :stroke-miterlimit "1.414", :id "twitter", :xmlns "http://www.w3.org/2000/svg"} [:path {:d "M31.003 6.695c-1.102.492-2.291.82-3.533.966a6.185 6.185 0 0 0 2.706-3.404 12.404 12.404 0 0 1-3.908 1.495 6.154 6.154 0 0 0-4.495-1.94 6.153 6.153 0 0 0-5.994 7.553A17.468 17.468 0 0 1 3.093 4.932a6.15 6.15 0 0 0-.831 3.094 6.147 6.147 0 0 0 2.736 5.122 6.16 6.16 0 0 1-2.789-.768v.076a6.154 6.154 0 0 0 4.94 6.034 6.149 6.149 0 0 1-2.783.106 6.177 6.177 0 0 0 5.748 4.277 12.347 12.347 0 0 1-9.117 2.549 17.4 17.4 0 0 0 9.44 2.766c11.32 0 17.513-9.381 17.513-17.514 0-.269-.005-.533-.017-.796a12.405 12.405 0 0 0 3.07-3.182v-.001z", :fill-rule "nonzero"}]]]])

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
   (h/include-css "/css/style.css")
   [:script {:type "text/javascript" :async true} "var _paq = window._paq || [];_paq.push(['trackPageView']);_paq.push(['enableLinkTracking']);(function(){var u=\"//stats.data.gouv.fr/\";_paq.push(['setTrackerUrl', u+'piwik.php']);_paq.push(['setSiteId', '95']);var d=document, g=d.createElement('script'), s=d.getElementsByTagName('script')[0];g.type='text/javascript'; g.async=true; g.defer=true; g.src=u+'matomo.js'; s.parentNode.insertBefore(g,s);})();"]
   [:noscript [:p [:img {:src "//stats.data.gouv.fr/piwik.php?idsite=95&rec=1" :style "border:0;" :alt ""}]]]])

(defn footer [lang]
  [:footer.footer
   [:div.content
    [:div.columns
     [:div.column.is-offset-2.is-4
      [:img {:src "/images/etalab.svg", :width "240px"}]
      [:ul.footer__social
       [:li [:a {:href "https://twitter.com/etalab", :title "Twitter"}
             [:svg.icon.icon-twitter
              [:use {:xlink:href "#twitter"}]]]]
       [:li [:a {:href "https://github.com/etalab", :title "Github"}
             [:svg.icon.icon-github
              [:use {:xlink:href "#github"}]]]]
       [:li [:a {:href "https://www.facebook.com/etalab", :title "Facebook"}
             [:svg.icon.icon-fb [:use {:xlink:href "#facebook"}]]]]
       [:li [:a {:href "mailto:info@data.gouv.fr", :title "Nous écrire un mail"}
             [:svg.icon.icon-mail [:use {:xlink:href "#envelope"}]]]]]]
     [:div.column.is-offset-1.is-4
      [:h1 "code.etalab.gouv.fr"]
      [:p (i/i lang [:website-developed-by])
       [:a {:href "https://www.etalab.gouv.fr"} "Etalab"]
       [:a {:href "https://github.com/etalab/code.etalab.gouv.fr"}
        (str (i/i lang [:source-code-available]) " " (i/i lang [:here]) ".")]]]]]])

(defn default [lang & [title subtitle content]]
  (let [title    (or title (i/i lang [:index-title]))
        subtitle (or subtitle (i/i lang [:index-subtitle]))
        content0 (if content
                   [:div.column.is-8.is-offset-2 content]
                   [:div.column.is-10.is-offset-1
                    [:div.container {:id "app"}]])]
    (h/html5
     {:lang lang}
     (head title lang)
     [:body
      (let [csrf-token (force anti-forgery/*anti-forgery-token*)]
        [:div#sente-csrf-token {:data-csrf-token csrf-token}])
      (icons)
      [:nav.navbar {:role "navigation" :aria-label "main navigation"}
       [:div.navbar-brand
        [:a.navbar-item {:href "https://code.etalab.gouv.fr"}
         [:img {:src    "/images/logo-marianne.svg"
                :alt    "Logo Marianne"
                :width  "120"
                :height "100"}
          "code.etalab.gouv.fr (alpha)"]]]
       [:div.navbar-menu
        [:div.navbar-end
         [:a.navbar-item
          {:href (str "/" lang "/contact") :title (i/i lang [:report-new-source-code])}
          (i/i lang [:contact])]
         [:a.navbar-item
          {:href (str "/" lang "/glossary") :title (i/i lang [:understand-tech-terms])}
          (i/i lang [:glossary])]
         [:a.navbar-item {:href (str "/" lang "/about") :title (i/i lang [:why-this-website?])}
          (i/i lang [:about])]
         [:a.navbar-item {:href  "https://www.etalab.gouv.fr"
              :title (i/i lang [:main-etalab-website])} "Etalab"]
         [:a.navbar-item.button
          {:href  "/latest.xml" :target "new"
           :title (i/i lang [:subscribe-rss-flux])}
          [:span.icon [:i.fas.fa-rss]]]]]]
      [:section.hero
       [:div.hero-body
        [:div.container
         [:h1.title.has-text-centered title]
         [:h2.subtitle.column.is-8.is-offset-2.has-text-centered subtitle]]]]
      [:section.section content0]
      (when-not content
        [:div
         [:script {:src "/js/codegouvfr.js"}]
         [:script "codegouvfr.core.init();"]])
      (footer lang)])))

(defn contact
  "Contact template."
  [lang]
  (default
   lang
   (i/i lang [:contact-form])
   (i/i lang [:contact-baseline])
   [:form
    {:action "/contact" :method "post"}
    (afu/anti-forgery-field)
    [:input {:name "lang" :type "hidden" :value lang}]
    [:div.columns
     [:div.field.column.is-6
      [:label.label (i/i lang [:your-name])]
      [:div.control
       [:input.input {:name "name" :type        "text"
                      :size "30"   :placeholder "Nom"}]]]
     [:div.field.column.is-6
      [:label.label (i/i lang [:your-email])]
      [:div.control
       [:input.input
        {:name        "email"
         :type        "email"
         :size        "30"
         :placeholder (i/i lang [:email-placeholder]) :required true}]]]]
    [:div.field
     [:label.label (i/i lang [:your-affiliation])]
     [:div.control
      [:input.input {:name "organization" :type        "text"
                     :size "30"           :placeholder (i/i lang [:affiliation-placeholder])}]]]
    [:div.field
     [:label.label (i/i lang [:your-message])]
     [:div.control
      [:textarea.textarea
       {:rows        "10"
        :name        "message"
        :placeholder (i/i lang [:message-placeholder]) :required true}]]]
    [:div.field.is-pulled-right
     [:div.control
      [:input.button.is-medium.is-info {:type  "submit"
                                        :value (i/i lang [:submit])}]]]
    [:br]]))

(defn ok
  "Template for the contact form feedback."
  [lang] ;; FIXME: unused
  (default
   lang
   (i/i lang [:message-received])
   (i/i lang [:message-received-ok])
   [:div.has-text-centered
    [:a.button.is-large.is-primary {:href  (str "/" lang "/repos")}
     (i/i lang [:back-to-repos])]]))

(defn fr-about [lang]
  (default
   lang
   "About code.etalab.gouv.fr" "D'où viennent les données, à quoi peuvent-elles servir ?"
   [:div
    [:div.container
     [:h1.title "D'où viennent les données ?"]
     (md-to-hiccup "Dans le cadre de la <a target=\"new\" href=\"https://www.numerique.gouv.fr/publications/politique-logiciel-libre/\">Politique de contribution de l'État aux logiciels libres</a>, la DINSIC collecte la <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">liste des comptes d'organisation</a> où des organismes publics publient leurs codes sources. Nous nous servons de cette liste pour collecter des métadonnées sur tous les dépôts de code source.  Ces métadonnées sont publiées <a href=\"https://www.data.gouv.fr/fr/datasets/inventaire-des-depots-de-code-source-des-organismes-publics/\">sur data.gouv.fr</a> ou requêtables depuis <a href=\"https://api-code.etalab.gouv.fr/api/repertoires/all\">cette API</a>.")
     [:br]
     (md-to-hiccup "La publication des codes sources est imposée par la <strong>Loi pour une République numérique</strong> : tout code source obtenu ou développé par un organisme remplissant une mission de service public est considéré comme un « document administratif » devant être publié en open data.  Pour connaître les conditions d'ouverture d'un logiciel du secteur public, vous pouvez consulter ce <a target=\"new\" href=\"https://guide-juridique-logiciel-libre.etalab.gouv.fr\">guide interactif</a>.")
     [:br]
     [:h1.title "Je ne vois pas mes codes sources !"]
     (md-to-hiccup "C'est sûrement que votre forge, votre compte d'organisation ou votre groupe n'est pas <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">référencé ici</a>.")
     [:br]
     (md-to-hiccup "<a href=\"/contact\">Écrivez-nous</a> et nous ajouterons votre forge ou votre compte d'organisation.")
     [:br]]
    [:div.container
     [:h1.title "En quoi cette liste peut m'être utile ?"]
     [:p [:strong "Votre organisme accomplit une mission de service public ?"] " Avant de développer du code source par vous-même, vous pouvez chercher si du code déjà développé n'est pas disponible. Vous pouvez aussi repérer des projets qui vous intéressent pour vous rapprocher des organismes porteurs et leur demander comment contribuer."]
     [:br]
     [:p [:strong "Vous vous y connaissez en code ?"] " Vous pouvez trouver des projets qui vous intéressent et contribuer."]
     [:br]
     [:p [:strong "Je ne comprends pas ces mots !"] " Pas de panique : nous vous avons préparé un petit <a href=\"/fr/glossary\">glossaire</a> pour vous aider à tout comprendre."]
     [:br]]
    [:div.container
     [:h1.title "Puis-je partager un lien vers une requête ?"]
     [:p "Oui !  Vous pouvez utiliser les paramètres \"s\", \"g\", \"language\" et \"license\" comme dans ces exemples :"]
     [:br]
     [:ul
      [:li "Trouver les dépôts contenant \"API\" : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?q=API"} "https://code.etalab.gouv.fr/fr/repos?q=API"]]
      [:li "Trouver les dépôts des groupes contenant \"betagouv\" : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?g=betagouv"} "https://code.etalab.gouv.fr/fr/repos?g=betagouv"]]
      [:li "Trouver les groupes contenant \"medialab\" : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/groups?q=medialab"} "https://code.etalab.gouv.fr/fr/groups?q=medialab"]]
      [:li "Trouver les dépôts en Python : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?language=python"} "https://code.etalab.gouv.fr/fr/repos?language=python"]]
      [:li "Trouver les dépôts sous Affero GPL : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?license=Affero"} "https://code.etalab.gouv.fr/fr/repos?license=Affero"]]
      [:li "Combiner les requêtes : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?license=Affero&g=beta"} "https://code.etalab.gouv.fr/fr/repos?license=Affero&g=beta"]]]
     [:br]]
    [:div.container
     [:h1.title "Puis-je aider à faire évoluer ce site ?"]
     (md-to-hiccup "<strong>Oui !</strong> La collecte des <a target=\"new\" href=\"https://github.com/etalab/data-codes-sources-fr\">métadonnées des dépôts</a> et l'<a target=\"new\" href=\"https://github.com/etalab/api-codes-sources-fr\">API</a> sont maintenus par Antoine Augusti (Etalab) ; le site que vous consultez est <a href=\"https://github.com/etalab/code.etalab.gouv.fr\">développé ici</a> par Bastien Guerry (Etalab).  N'hésitez pas à faire des suggestions sur ces dépôts, ils sont sous licence libre et toute contribution est la bienvenue.")
     [:br]
     (md-to-hiccup "Pour suivre l'actualité des logiciels libres utilisés et produits par l'administration, inscrivez-vous à la <a target=\"new\" href=\"https://lists.eig-forever.org/subscribe/bluehats@mail.etalab.studio\">gazette #bluehats</a>.")
     [:br]
     (md-to-hiccup "Et pour toute autre question, n'hésitez pas à [nous écrire](/fr/contact).")
     [:br]]]))

(defn en-about [lang]
  (default
   lang
   "About code.etalab.gouv.fr" "Where does the data come from, what can it be used for?"
   [:div
    [:div.container
     [:h1.title "Where does the data come from?"]
     (md-to-hiccup "As part of the <a target=\"new\" href=\"https://www.numerique.gouv.fr/publications/politique-logiciel-libre/\">government's free software contribution policy</a>, DINSIC collects the <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">list of organizational accounts</a> where public bodies share their source codes. We use this list to collect metadata about all source code repositories. These metadata are published on <a href=\"https://www.data.gouv.fr/fr/datasets/inventaire-des-depots-de-code-source-des-organismes-publics/\">data.gouv.fr</a> and queryable through <a href=\"https://api-code.etalab.gouv.fr/api/repertoires/all\">this API</a>.")
     [:br]
     (md-to-hiccup "The publication of source codes is prescribed by the <strong>French Digital Republic Act (Loi pour une République numérique)</strong> : each source code obtained or developed by an organization fulfilling a public service mission is considered an administrative document, and, therefore, has to be published in open data. To understand the requirements to publish a public sector software, check out this <a target=\"new\" href=\"https://guide-juridique-logiciel-libre.etalab.gouv.fr\">(french) interactive guide</a>.")
     [:br]
     [:h1.title "I can't find my source codes!"]
     (md-to-hiccup "It is likely that your software forge, your organization or group account is not <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">listed here</a>.")
     [:br]
     (md-to-hiccup "<a href=\"/contact\">Contact us</a> and we will add your forge or your organization account.")
     [:br]]
    [:div.container
     [:h1.title "How can this list help me?"]
     [:p [:strong "If your organization fulfills a public service mission:"] " Before you start programming, you can check for available source code. You can also look for projects you are interested in to get closer to lead organizations and ask them how to contribute."]
     [:br]
     [:p [:strong "If you know how to program:"] " You can find projects you are interested in and contribute to them."]
     [:br]
     [:p [:strong "I don't understand these words!"] " Don't worry: we made a short  <a href=\"/fr/glossary\">glossary</a> to help you better understand those words."]
     [:br]]
    [:div.container
     [:h1.title "Can I share a link to a query?"]
     [:p "Yes, you can!  You can use the parameters \"s\", \"g\", \"language\" and \"license\" as in the following examples:"]
     [:br]
     [:ul
      [:li "Find repositories containing \"API\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?q=API"} "https://code.etalab.gouv.fr/fr/repos?q=API"]]
      [:li "Find group repositories containing \"betagouv\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?g=betagouv"} "https://code.etalab.gouv.fr/fr/repos?g=betagouv"]]
      [:li "Find groups containing \"medialab\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/groups?q=medialab"} "https://code.etalab.gouv.fr/fr/groups?q=medialab"]]
      [:li "Find group repositories containing \"beta\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/groups?g=beta"} "https://code.etalab.gouv.fr/fr/groups?g=beta"]]
      [:li "Find repositories in Python: " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?language=python"} "https://code.etalab.gouv.fr/fr/repos?language=python"]]
      [:li "Find repositories under the Affero GPL license: " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?license=Affero"} "https://code.etalab.gouv.fr/fr/repos?license=Affero"]]
      [:li "Combine queries : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?license=Affero&g=beta"} "https://code.etalab.gouv.fr/fr/repos?license=Affero&g=beta"]]]
     [:br]]
    [:div.container
     [:h1.title "Can I help enhancing this website?"]
     (md-to-hiccup "<strong>Yes!</strong> Harvesting <a target=\"new\" href=\"https://github.com/etalab/data-codes-sources-fr\">metadata of repositories</a> and exposing them via the <a target=\"new\" href=\"https://github.com/etalab/api-codes-sources-fr\">API</a> is done by Antoine Augusti (Etalab). This frontend is developed <a href=\"https://github.com/etalab/code.etalab.gouv.fr\">here</a> by Bastien Guerry (Etalab).  Don't hesitate to send suggestions on these repositories, they are published under a free software license and every contribution is welcome.")
     [:br]
     (md-to-hiccup "To read news about free software used and developed by the French public sector, subscribe to the <a target=\"new\" href=\"https://lists.eig-forever.org/subscribe/bluehats@mail.etalab.studio\">#bluehats newsletter</a>.")
     [:br]
     (md-to-hiccup "For any other question, please [drop a message](/en/contact).")
     [:br]]]))

(defn it-about [lang]
  (default
   lang
   "A proposito di code.etalab.gouv.fr" "Da dove provengono i dati, per cosa possono essere utilizzati?"
   [:div
    [:div.container
     [:h1.title "Da dove provengono i dati"]
     (md-to-hiccup "Nel quadro della <a target=\"new\" href=\"https://www.numerique.gouv.fr/publications/politique-logiciel-libre/\">Politica di contribuzione dello Stato per il software libero</a>, il DINSIC raccoglie la <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">lista dei repository delle organizzazioni</a> o degli organismi pubblici che condivido il loro codici sorgenti. Usiamo questo elenco per raccogliere metadati su tutti i repository di codice sorgente. Questi matadati sono pubblicati <a href=\"https://www.data.gouv.fr/fr/datasets/inventaire-des-depots-de-code-source-des-organismes-publics/\">su data.gouv.fr</a> o ricercabili da <a href=\"https://api-code.etalab.gouv.fr/api/repertoires/all\">questa API</a>.")
     [:br]
     (md-to-hiccup "La condivisione di codice sorgente è imposta dalla <strong>Legge per una Repubblica digitale</strong>: qualsiasi codice sorgente ottenuto o sviluppato da un'organizzazione che compie una missione di servizio pubblico è considerato un \"documento amministrativo\" da pubblicare come dato aperto. Per conoscere le condizioni di apertura di un software del settore pubblico, è possibile consultare questa <a target=\"new\" href=\"https://guide-juridique-logiciel-libre.etalab.gouv.fr\">guida interattiva</a>.")
     [:br]
     [:h1.title "Non vedo i miei sorgenti di codice!"]
     (md-to-hiccup "&Egrave; sicuramente perchè il vostro repository, il vostro account d'organizzazione o il vostro gruppo non è <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">registrato qui</a>.")
     [:br]
     (md-to-hiccup "<a href=\"/contact\">Scrivici</a> e noi aggiungeremo il vostor repository o il vostro acocunt d'organizzazione.")
     [:br]]
    [:div.container
     [:h1.title "Come questa lista mi può essere utile?"]
     [:p [:strong "La tua organizzazione svolge una missione di servizio pubblico?"] "Prima di poter sviluppare del nuovo codice sorgente, puoi cercare che il codice che ti serve non sia già disponibile. Puoi anche trovare progetti che ti interessano ed avvicinarti alle organizzazioni che li hanno prodotti per chiedere loro come contribuire."]
     [:br]
     [:p [:strong "Conoscete un codice?"] " Potete trovare i progetti che ti interessano e contribuire."]
     [:br]
     [:p [:strong "Non capisco queste parole!"] " Niente panico: abbiamo preparato un piccolo <a href=\"/it/glossary\">glossario</a> per aiutarvi a comprendere."]
     [:br]]
    [:div.container
     [:h1.title "Posso condividere un collegamento a una richiesta?"]
     [:p "Sì! Potete usare i parametri \"s\", \"g\", \"language\" e \"license\" come in questo esempio:"]
     [:br]
     [:ul
      [:li "Travare i repository contenti \"API\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?q=API"} "https://code.etalab.gouv.fr/it/repos?q=API"]]
      [:li "Trovare i repository dei gruppi contenenti \"betagouv\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?g=betagouv"} "https://code.etalab.gouv.fr/it/repos?g=betagouv"]]
      [:li "Find groups containing \"medialab\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/groups?q=medialab"} "https://code.etalab.gouv.fr/fr/groups?q=medialab"]] ;; TODO
      [:li "Trovare i repository su Python: " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?language=python"} "https://code.etalab.gouv.fr/it/repos?language=python"]]
      [:li "Trovare i repository sotto licenza Affero GPL: " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?license=Affero"} "https://code.etalab.gouv.fr/it/repos?license=Affero"]]
      [:li "Combinare le richieste: " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?license=Affero&g=beta"} "https://code.etalab.gouv.fr/it/repos?license=Affero&g=beta"]]]
     [:br]]
    [:div.container
     [:h1.title "Posso aiutare a far evolvere questo sito?"]
     (md-to-hiccup "<strong>Sì!</strong> La raccoota di <a target=\"new\" href=\"https://github.com/etalab/data-codes-sources-fr\">metadati dei repository</a> e le <a target=\"new\" href=\"https://github.com/etalab/api-codes-sources-fr\">API</a> sono mantenute da Antoine Augusti (Etalab); il sito che state consultando è <a href=\"https://github.com/etalab/code.etalab.gouv.fr\">sviluppato</a> da Bastien Guerry (Etalab).  Hon esitate a dare suggerimenti su questi repository, sono sotto licenza libera e tutte le contribuzioni sono benvenute.")
     [:br]
     (md-to-hiccup "Per seguire le notizie del software gratuito utilizzato e prodotto dall'amministrazione, iscriviti alla <a target=\"new\" href=\"https://lists.eig-forever.org/subscribe/bluehats@mail.etalab.studio\">gazzetta #bluehats</a>.")
     [:br]
     (md-to-hiccup "E per qualsiasi altra domanda, non esitare a [scriverci](/contact).")
     [:br]]]))

(defn fr-glossary [lang]
  (default
   lang
   "Glossaire pour code.etalab.gouv.fr" "Qu'est-ce qu'un dépôt ? Une « organisation » ? Une licence ?"
   [:div
    [:div.container
     [:a {:name "source-code"} [:h2.subtitle "Codes sources"]]
     [:br]
     [:p "Le code source d'un programme informatique est ce qu'écrit une programmeuse ou un programmeur.  Il peut s'agir de programmes complexes ou de quelques lignes. Ce code source peut être partagé sous licence libre pour permettre aux autres programmeurs de l'étudier, de le modifier, de le diffuser et de partager leurs améliorations."]
     [:br]

     [:a {:name "repository"} [:h2.subtitle "Dépôt"]]
     [:br]
     (md-to-hiccup "Un « dépôt » est un espace dans lequel sont publiés les fichiers de code source. C'est ce que vous voyez lorsque vous visitez un lien vers un code source hébergé sur une forge. C'est aussi ce que vous pouvez copier sur votre machine pour l'explorer localement.")
     [:br]

     [:a {:name "dependencies"} [:h2.subtitle "Dépendances"]]
     [:br]
     (md-to-hiccup "Un logiciel intègre souvent des briques logicielles publiées sous licence libre.  Celles-ci sont appelées « dépendances ».  Ce site permet d'en parcourir la liste.  Ces informations sont collectées depuis le site [backyourstack.com](https://backyourstack.com), qui détecte les dépendances de JavaScript (NPM), PHP (Composer), .NET (Nuget), Go (dep), Ruby (Gem) et Python (Requirement).  Les dépendances affichées sont celles nécessaires pour la mise en *production* ou pour le *développement*.")
     [:br]

     [:a {:name "etoile"} [:h2.subtitle "Étoiles"]]
     [:br]
     (md-to-hiccup "Les « étoiles » (« stars » en anglais) sont un moyen pour les utilisateurs des plates-formes de mettre un dépôt en favori.  Pour l'instant, nous collectons cette information sur GitHub, GitLab et les instances de GitLab.  Ce n'est pas une mesure de la qualité du code source.")
     [:br]

     [:a {:name "fourche"} [:h2.subtitle "Fourche"]]
     [:br]
     (md-to-hiccup "Un dépôt « fourché » (ou « forké » en franglais) est un dépôt de code source qui a été développé à partir d'un autre.")
     [:br]

     [:a {:name "license"} [:h2.subtitle "Licence"]]
     [:br]
     (md-to-hiccup "Une licence logicielle est un contrat passé entre les auteurs d'un logiciel et ses réutilisateurs.  Les licences dites « libres » accordent aux utilisateurs le droit de réutiliser le code source d'un logiciel.")
     [:br]

     [:a {:name "organization-group"} [:h2.subtitle "Organisation et groupe"]]
     [:br]
     (md-to-hiccup "GitHub permet d'avoir des comptes personnels pour y héberger du code et des « comptes d'organisation ».  Un « groupe » est la notion plus ou moins équivalent sur les instance de GitLab.  Un organisme remplissant une mission de service public peut avoir un ou plusieurs organisations et/ou groupes sur une ou plusieurs forges.")
     [:br]

     [:a {:name "secteur-public"} [:h2.subtitle "Secteur public"]]
     [:br]
     (md-to-hiccup "Les codes sources développés dans le cadre de missions de service public ont vocation à être publiés, dans certains conditions. Ce site propose de chercher dans l'ensemble des codes sources aujourd'hui identifiés comme provenant d'un organisme remplissant une mission de service public. Il a été développé par [la mission Etalab](https://www.etalab.gouv.fr/).")
     [:br]

     [:a {:name "software-heritage"} [:h2.subtitle "Software heritage"]]
     [:br]
     (md-to-hiccup "<a target=\"new\" href=\"https://www.softwareheritage.org/\">Software Heritage</a> est un projet dont le but est d'archiver tous les codes sources disponibles.  Pour chaque dépôt référencé sur ce site, nous donnons le lien vers la version archivée sur Software Heritage.")
     [:br]]]))

(defn en-glossary [lang]
  (default
   lang
   "Glossary for code.etalab.gouv.fr" "What is a repository? An organization? A license?"
   [:div
    [:div.container

     [:a {:name "dependencies"} [:h2.subtitle "Dependencies"]]
     [:br]
     (md-to-hiccup "Softwares often open source libraries. These libraries are the \"dependencies\" of a software. This website allows you to browse a repository or a group dependencies.  They are collected from [backyourstack.com](https://backyourstack.com), which detects dependencies from JavaScript (NPM), PHP (Composer), .NET (Nuget), Go (dep), Ruby (Gem) and Python (Requirement).  We display both *production* and *development* dependencies.")
     [:br]

     [:a {:name "fourche"} [:h2.subtitle "Fork"]]
     [:br]
     (md-to-hiccup "A \"forked\" repository est is repository derived from another one.")
     [:br]

     [:a {:name "license"} [:h2.subtitle "Licence"]]
     [:br]
     (md-to-hiccup "A software licence is a contract between a software's authors and its end-users. So-called \"libre\" licences grant licensees permission to distribute, modify and share a software's source code.")
     [:br]

     [:a {:name "organization-group"} [:h2.subtitle "Organisation & Group"]]
     [:br]
     (md-to-hiccup "GitHub allows to have personal accounts or \"organizations accounts\" to store source code.  A \"group\" is the more or less equivalent notion used on GitLab instances.  A public sector organization may have one or more organization accounts and/or groups on one or several software forges.")
     [:br]

     [:a {:name "secteur-public"} [:h2.subtitle "Public Sector"]]
     [:br]
     (md-to-hiccup "Source code developed by a public agency must be published, under certain conditions.  This website offers the possibility to search within source code repositories that are identified as coming from public sector organisms.  It has been developed by [Etalab](https://www.etalab.gouv.fr/).")
     [:br]

     [:a {:name "repository"} [:h2.subtitle "Repository"]]
     [:br]
     (md-to-hiccup "A \"repository\" is a place where source code files are stored.  This is what you see when you browse a link to a source code as hosted on a software forge.  This is also what you copy on your machine to explore it locally.")
     [:br]

     [:a {:name "software-heritage"} [:h2.subtitle "Software Heritage"]]
     [:br]
     (md-to-hiccup "<a target=\"new\" href=\"https://www.softwareheritage.org/\">Software Heritage</a> is a project whose ambition is to collect, preserve, and share all software that is publicly available in source code form. Each repository here referenced is linked to its corresponding Software Heritage archive version.")
     [:br]

     [:a {:name "source-code"} [:h2.subtitle "Source Code"]]
     [:br]
     [:p "A software's source code is what a developer writes.  Source code can be just a few lines or quite a few of them. Source code can be made available under a free licence for others to freely distribute, modify and share."]
     [:br]

     [:a {:name "etoile"} [:h2.subtitle "Stars"]]
     [:br]
     (md-to-hiccup "Stars allow users to mark a repository as favourite. At the moment, we collect repository favouriteness data from GitHub, Gitlab and instances thereof. Favouriteness is not a source code quality metric.")
     [:br]

     ]]))

(defn it-glossary [lang]
  (default
   lang
   "Glossario di code.etalab.gouv.fr" "Cosa è un repository? Un'organizzazione? Una licenza?"
   [:div
    [:div.container

     [:a {:name "source-code"} [:h2.subtitle "Codici sorgenti"]]
     [:br]
     [:p "Il codice sorgente di un programma informatico è quello che scrive una programmatrice o un programmatore. Questi possono essere sia programmi molto complessi che programmi di poche linee. Il codice sorgente può essere condiviso con una licenza aperta per consentire ad altri programmatori di studiare, modificare, distribuire e condividere i loro miglioramenti al codice."]
     [:br]

     ;; TODO: i18n
     [:a {:name "dependencies"} [:h2.subtitle "Dependencies"]]
     [:br]
     (md-to-hiccup "Softwares often open source libraries. These libraries are the \"dependencies\" of a software. This website allows you to browse a repository or a group dependencies.  They are collected from [backyourstack.com](https://backyourstack.com), which detects dependencies from JavaScript (NPM), PHP (Composer), .NET (Nuget), Go (dep), Ruby (Gem) and Python (Requirement).  We display both *production* and *development* dependencies.")
     [:br]

     [:a {:name "fourche"} [:h2.subtitle "Fork"]]
     [:br]
     (md-to-hiccup "Una fork è un repository che è stato sviluppato partendo dal codice presente in un altro repository pubblico.")
     [:br]

     [:a {:name "license"} [:h2.subtitle "Licenze"]]
     [:br]
     (md-to-hiccup "Una licenza è un contratto sviluppato tra l'autore di un programma e i suoi utilizzatori. Le licenze dette \"libere\" concedono agli utilizzatori il diritto di riutilizzare il codice sorgente di un programma.")
     [:br]

     [:a {:name "organization-group"} [:h2.subtitle "Organizzazione e gruppi"]]
     [:br]
     (md-to-hiccup "GitHub è un sito di verisonamento del codice e permette di avere dei repository personali e dei \"repository di organizzazione\". Un gruppo è la nozione più o meno equivalente sulle istanze di GitLab. Un organismo che svolge una missione di servizio pubblico può avere una o più organizzazioni e/o gruppi su una o più siti di verisonamento del codice.")
     [:br]

     [:a {:name "repository"} [:h2.subtitle "Repository"]]
     [:br]
     (md-to-hiccup "Un repository è uno spazio dentro il quale vengono pubblicati i file di codice sorgente. Questo è ciò che si vede quando si visita un link al codice sorgente ospitato su un repository. &Egrave; anche quello che puoi copiare sul tuo computer per esplorarlo localmente.")
     [:br]

     [:a {:name "secteur-public"} [:h2.subtitle "Settore pubblico"]]
     [:br]
     (md-to-hiccup "I codici sorgente sviluppati nell'ambito di servizi pubblici sono destinati a essere pubblicati in open source sotto determinate condizioni. Questo sito offre la possibilità di cercare nell'insieme di codici sorgenti oggi identificati come provenienti da un'organizzazione che svolge un compito di servizio pubblico. Il sito è stato sviluppato da [Etalab](https://www.etalab.gouv.fr/).")
     [:br]

     [:a {:name "software-heritage"} [:h2.subtitle "Software heritage"]]
     [:br]
     (md-to-hiccup "<a target=\"new\" href=\"https://www.softwareheritage.org/\">Software Heritage</a> è un progetto che ha lo scopo di archiviare tutti i codici sorgenti disponbili. Per ciascun repository referenziato in questo sito, viene fornito il link alla versione archiviata su Software Heritage.")
     [:br]

     [:a {:name "etoile"} [:h2.subtitle "Stelle"]]
     [:br]
     (md-to-hiccup "Le stelle («star» in inglese) sono un mezzo per permettere agi utilizzato delle piattaforme di versionamento del codice di mettere un repository tra i preferity. Al momento, noi memorizziamo questa informazion su GitHub, GitLab e le istanze private di GitLab. Questa non è una misura della qualità del codice sorgente.")
     [:br]

     ]]))
