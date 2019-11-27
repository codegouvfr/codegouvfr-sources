(ns codegouvfr.views
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.instant :as inst]
            [clj-rss.core :as rss]
            [clj-http.client :as http]
            [hiccup.page :as h]
            [ring.util.anti-forgery :as afu]
            [ring.util.response :as response]
            [markdown-to-hiccup.core :as md]
            [codegouvfr.i18n :as i]))

(defn md-to-string [s]
  (-> s (md/md->hiccup) (md/component)))

(defonce last-repositories-url "https://api-code.etalab.gouv.fr/api/stats/last_repositories")

(defn codegouvfr-latest-repositories []
  (let [reps (try (http/get last-repositories-url)
                  (catch Exception e nil))]
    (json/parse-string (:body reps) true)))

(defn rss-feed []
  (rss/channel-xml
   ;; FIXME: hardcode title/description if always english?
   {:title       (i/i "en" [:last-repos])
    :link        "https://code.etalab.gouv.fr/latest.xml"
    :description (i/i "en" [:last-repos])}
   (map (fn [item] {:title       (:nom item)
                    :link        (:repertoire_url item)
                    :description (:description item)
                    :author      (:organisation_nom item)
                    :pubDate     (inst/read-instant-date (:derniere_mise_a_jour item))})
        (codegouvfr-latest-repositories))))

(defn rss []
  (assoc
   (response/response (rss-feed))
   :headers {"Content-Type" "text/xml; charset=utf-8"}))

(defn default [lang]
  (assoc
   (response/response
    (io/input-stream
     (io/resource
      (str "public/index." lang ".html"))))
   :headers {"Content-Type" "text/html; charset=utf-8"}))

(defn template [lang title subtitle content]
  (h/html5
   {:lang lang}
   [:head
    [:title title]
    [:meta {:charset "utf-8"}]
    [:meta {:name "keywords" :content (i/i lang [:keywords])}]
    [:meta {:name "description" :content (i/i lang [:keywords])}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, shrink-to-fit=yes"}]
    (h/include-css "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.11.2/css/all.min.css")
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
       [:a {:href (str "/" lang "/contact") :title (i/i lang [:report-new-source-code]) :class "navbar-item"} (i/i lang [:contact])]
       [:a {:href (str "/" lang "/glossary") :title (i/i lang [:understand-tech-terms]) :class "navbar-item"} (i/i lang [:glossary])]
       [:a {:href (str "/" lang "/about") :title (i/i lang [:why-this-website?]) :class "navbar-item"} (i/i lang [:about])]
       [:a {:href  "https://www.etalab.gouv.fr"
            :title (i/i lang [:main-etalab-website])
            :class "navbar-item"} "Etalab"]
       [:a {:href  "/latest.xml" :target "new"
            :title (i/i lang [:subscribe-rss-flux])
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
        [:p (i/i lang [:website-developed-by]) [:a {:href "https://www.etalab.gouv.fr"} "Etalab"]
         (i/i lang [:source-code-available]) [:a {:href "https://github.com/etalab/codegouvfr"}
                                              (i/i lang [:here])]]]]]]]))

(defn contact [lang]
  (template
   lang
   (i/i lang [:contact-form])
   (i/i lang [:contact-baseline])
   [:form
    {:action "/contact" :method "post"}
    (afu/anti-forgery-field)
    [:input {:name "lang" :type "hidden" :value lang}]
    [:div {:class "columns"}
     [:div {:class "field column is-6"}
      [:label {:class "label"} (i/i lang [:your-name])]
      [:div {:class "control"}
       [:input {:name        "name" :type  "text"
                :size        "30"   :class "input"
                :placeholder "Nom"}]]]
     [:div {:class "field column is-6"}
      [:label {:class "label"} (i/i lang [:your-email])]
      [:div {:class "control"}
       [:input {:name        "email"
                :type        "email"
                :size        "30"
                :class       "input"
                :placeholder (i/i lang [:email-placeholder]) :required true}]]]]
    [:div {:class "field"}
     [:label {:class "label"} (i/i lang [:your-affiliation])]
     [:div {:class "control"}
      [:input {:name        "organization" :type  "text"
               :size        "30"           :class "input"
               :placeholder (i/i lang [:affiliation-placeholder])}]]]
    [:div {:class "field"}
     [:label {:class "label"} (i/i lang [:your-message])]
     [:div {:class "control"}
      [:textarea {:class       "textarea"
                  :rows        "10"
                  :name        "message"             
                  :placeholder (i/i lang [:message-placeholder]) :required true}]]]
    [:div {:class "field is-pulled-right"}
     [:div {:class "control"}
      [:input {:type  "submit"
               :value (i/i lang [:submit])
               :class "button is-medium is-info"}]]]
    [:br]]))

(defn ok [lang] ;; FIXME: unused
  (template
   lang
   (i/i lang [:message-received])
   (i/i lang [:message-received-ok])
   [:div {:class "has-text-centered"}
    [:a {:class "button is-large is-primary"
         :href  (str "/" lang "/repos")}
     (i/i lang [:back-to-repos])]]))

(defn fr-about [lang]
  (template
   lang
   "About code.etalab.gouv.fr" "D'où viennent les données, à quoi peuvent-elles servir ?"
   [:div
    [:div {:class "container"}
     [:h1 {:class "title"} "D'où viennent les données ?"]
     (md-to-string "Dans le cadre de la <a target=\"new\" href=\"https://www.numerique.gouv.fr/publications/politique-logiciel-libre/\">Politique de contribution de l'État aux logiciels libres</a>, la DINSIC collecte la <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">liste des comptes d'organisation</a> où des organismes publics publient leurs codes sources. Nous nous servons de cette liste pour collecter des métadonnées sur tous les dépôts de code source.  Ces métadonnées sont publiées <a href=\"https://www.data.gouv.fr/fr/datasets/inventaire-des-depots-de-code-source-des-organismes-publics/\">sur data.gouv.fr</a> ou requêtables depuis <a href=\"https://api-code.etalab.gouv.fr/api/repertoires/all\">cette API</a>.")
     [:br]
     (md-to-string "La publication des codes sources est imposée par la <strong>Loi pour une République numérique</strong> : tout code source obtenu ou développé par un organisme remplissant une mission de service public est considéré comme un « document administratif » devant être publié en open data.  Pour connaître les conditions d'ouverture d'un logiciel du secteur public, vous pouvez consulter ce <a target=\"new\" href=\"https://guide-juridique-logiciel-libre.etalab.gouv.fr\">guide interactif</a>.")
     [:br]
     [:h1 {:class "title"} "Je ne vois pas mes codes sources !"]
     (md-to-string "C'est sûrement que votre forge, votre compte d'organisation ou votre groupe n'est pas <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">référencé ici</a>.")
     [:br]
     (md-to-string "<a href=\"/contact\">Écrivez-nous</a> et nous ajouterons votre forge ou votre compte d'organisation.")
     [:br]]
    [:div {:class "container"}
     [:h1 {:class "title"} "En quoi cette liste peut m'être utile ?"]
     [:p [:strong "Votre organisme accomplit une mission de service public ?"] " Avant de développer du code source par vous-même, vous pouvez chercher si du code déjà développé n'est pas disponible. Vous pouvez aussi repérer des projets qui vous intéressent pour vous rapprocher des organismes porteurs et leur demander comment contribuer."]
     [:br]
     [:p [:strong "Vous vous y connaissez en code ?"] " Vous pouvez trouver des projets qui vous intéressent et contribuer."]
     [:br]
     [:p [:strong "Je ne comprends pas ces mots !"] " Pas de panique : nous vous avons préparé un petit <a href=\"/fr/glossary\">glossaire</a> pour vous aider à tout comprendre."]
     [:br]]
    [:div {:class "container"}
     [:h1 {:class "title"} "Puis-je partager un lien vers une requête ?"]
     [:p "Oui !  Vous pouvez utiliser les paramètres \"s\", \"g\", \"language\" et \"license\" comme dans ces exemples :"]
     [:br]
     [:ul
      [:li "Trouver les dépôts contenant \"API\" : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?q=API"} "https://code.etalab.gouv.fr/fr/repos?q=API"]]
      [:li "Trouver les dépôts des groupes contenant \"beta\" : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/groups?g=beta"} "https://code.etalab.gouv.fr/fr/groups?g=beta"]]
      [:li "Trouver les dépôts en Python : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?language=python"} "https://code.etalab.gouv.fr/fr/repos?language=python"]]
      [:li "Trouver les dépôts sous Affero GPL : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?license=Affero"} "https://code.etalab.gouv.fr/fr/repos?license=Affero"]]
      [:li "Combiner les requêtes : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?license=Affero&g=beta"} "https://code.etalab.gouv.fr/fr/repos?license=Affero&g=beta"]]]
     [:br]]
    [:div {:class "container"}
     [:h1 {:class "title"} "Puis-je aider à faire évoluer ce site ?"]
     (md-to-string "<strong>Oui !</strong> La collecte des <a target=\"new\" href=\"https://github.com/etalab/data-codes-sources-fr\">métadonnées des dépôts</a> et l'<a target=\"new\" href=\"https://github.com/etalab/api-codes-sources-fr\">API</a> sont maintenus par Antoine Augusti (Etalab) ; le site que vous consultez est <a href=\"https://github.com/etalab/codegouvfr\">développé ici</a> par Bastien Guerry (Etalab).  N'hésitez pas à faire des suggestions sur ces dépôts, ils sont sous licence libre et toute contribution est la bienvenue.")
     [:br]
     (md-to-string "Pour suivre l'actualité des logiciels libres utilisés et produits par l'administration, inscrivez-vous à la <a target=\"new\" href=\"https://lists.eig-forever.org/subscribe/bluehats@mail.etalab.studio\">gazette #bluehats</a>.")
     [:br]
     (md-to-string "Et pour toute autre question, n'hésitez pas à [nous écrire](/fr/contact).")
     [:br]]]))

(defn en-about [lang]
  (template
   lang
   "About code.etalab.gouv.fr" "Where does the data come from, what can it be used for?"
   [:div
    [:div {:class "container"}
     [:h1 {:class "title"} "Where does the data come from?"]
     (md-to-string "As part of the <a target=\"new\" href=\"https://www.numerique.gouv.fr/publications/politique-logiciel-libre/\">government's free software contribution policy</a>, DINSIC collects the <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">list of organizational accounts</a> where public bodies share their source codes. We use this list to collect metadata about all source code repositories. These metadata are published on <a href=\"https://www.data.gouv.fr/fr/datasets/inventaire-des-depots-de-code-source-des-organismes-publics/\">data.gouv.fr</a> and queryable through <a href=\"https://api-code.etalab.gouv.fr/api/repertoires/all\">this API</a>.")
     [:br]
     (md-to-string "The publication of source codes is prescribed by the <strong>French Digital Republic Act (Loi pour une République numérique)</strong> : each source code obtained or developed by an organization fulfilling a public service mission is considered an administrative document, and, therefore, has to be published in open data. To understand the requirements to publish a public sector software, check out this <a target=\"new\" href=\"https://guide-juridique-logiciel-libre.etalab.gouv.fr\">(french) interactive guide</a>.")
     [:br]
     [:h1 {:class "title"} "I can't find my source codes!"]
     (md-to-string "It is likely that your software forge, your organization or group account is not <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">listed here</a>.")
     [:br]
     (md-to-string "<a href=\"/contact\">Contact us</a> and we will add your forge or your organization account.")
     [:br]]
    [:div {:class "container"}
     [:h1 {:class "title"} "How can this list help me?"]
     [:p [:strong "If your organization fulfills a public service mission:"] " Before you start programming, you can check for available source code. You can also look for projects you are interested in to get closer to lead organizations and ask them how to contribute."]
     [:br]
     [:p [:strong "If you know how to program:"] " You can find projects you are interested in and contribute to them."]
     [:br]
     [:p [:strong "I don't understand these words!"] " Don't worry: we made a short  <a href=\"/fr/glossary\">glossary</a> to help you better understand those words."]
     [:br]]
    [:div {:class "container"}
     [:h1 {:class "title"} "Can I share a link to a query?"]
     [:p "Yes, you can!  You can use the parameters \"s\", \"g\", \"language\" and \"license\" as in the following examples:"]
     [:br]
     [:ul
      [:li "Find repositories containing \"API\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?q=API"} "https://code.etalab.gouv.fr/fr/repos?q=API"]]
      [:li "Find group repositories containing \"beta\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/groups?g=beta"} "https://code.etalab.gouv.fr/fr/groups?g=beta"]]
      [:li "Find repositories in Python: " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?language=python"} "https://code.etalab.gouv.fr/fr/repos?language=python"]]
      [:li "Find repositories under the Affero GPL license: " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?license=Affero"} "https://code.etalab.gouv.fr/fr/repos?license=Affero"]]
      [:li "Combine queries : " [:a {:target "new" :href "https://code.etalab.gouv.fr/fr/repos?license=Affero&g=beta"} "https://code.etalab.gouv.fr/fr/repos?license=Affero&g=beta"]]]
     [:br]]
    [:div {:class "container"}
     [:h1 {:class "title"} "Can I help enhancing this website?"]
     (md-to-string "<strong>Yes!</strong> Harvesting <a target=\"new\" href=\"https://github.com/etalab/data-codes-sources-fr\">metadata of repositories</a> and exposing them via the <a target=\"new\" href=\"https://github.com/etalab/api-codes-sources-fr\">API</a> is done by Antoine Augusti (Etalab). This frontend is developed <a href=\"https://github.com/etalab/codegouvfr\">here</a> by Bastien Guerry (Etalab).  Don't hesitate to send suggestions on these repositories, they are published under a free software license and every contribution is welcome.")
     [:br]
     (md-to-string "To read news about free software used and developed by the French public sector, subscribe to the <a target=\"new\" href=\"https://lists.eig-forever.org/subscribe/bluehats@mail.etalab.studio\">#bluehats newsletter</a>.")
     [:br]
     (md-to-string "For any other question, please [drop a message](/en/contact).")
     [:br]]]))

(defn it-about [lang]
  (template
   lang
   "A proposito di code.etalab.gouv.fr" "Da dove provengono i dati, per cosa possono essere utilizzati?"
   [:div
    [:div {:class "container"}
     [:h1 {:class "title"} "Da dove provengono i dati"]
     (md-to-string "Nel quadro della <a target=\"new\" href=\"https://www.numerique.gouv.fr/publications/politique-logiciel-libre/\">Politica di contribuzione dello Stato per il software libero</a>, il DINSIC raccoglie la <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">lista dei repository delle organizzazioni</a> o degli organismi pubblici che condivido il loro codici sorgenti. Usiamo questo elenco per raccogliere metadati su tutti i repository di codice sorgente. Questi matadati sono pubblicati <a href=\"https://www.data.gouv.fr/fr/datasets/inventaire-des-depots-de-code-source-des-organismes-publics/\">su data.gouv.fr</a> o ricercabili da <a href=\"https://api-code.etalab.gouv.fr/api/repertoires/all\">questa API</a>.")
     [:br]
     (md-to-string "La condivisione di codice sorgente è imposta dalla <strong>Legge per una Repubblica digitale</strong>: qualsiasi codice sorgente ottenuto o sviluppato da un'organizzazione che compie una missione di servizio pubblico è considerato un \"documento amministrativo\" da pubblicare come dato aperto. Per conoscere le condizioni di apertura di un software del settore pubblico, è possibile consultare questa <a target=\"new\" href=\"https://guide-juridique-logiciel-libre.etalab.gouv.fr\">guida interattiva</a>.")
     [:br]
     [:h1 {:class "title"} "Non vedo i miei sorgenti di codice!"]
     (md-to-string "&Egrave; sicuramente perchè il vostro repository, il vostro account d'organizzazione o il vostro gruppo non è <a target=\"new\" href=\"https://github.com/DISIC/politique-de-contribution-open-source/blob/master/comptes-organismes-publics\">registrato qui</a>.")
     [:br]
     (md-to-string "<a href=\"/contact\">Scrivici</a> e noi aggiungeremo il vostor repository o il vostro acocunt d'organizzazione.")
     [:br]]
    [:div {:class "container"}
     [:h1 {:class "title"} "Come questa lista mi può essere utile?"]
     [:p [:strong "La tua organizzazione svolge una missione di servizio pubblico?"] "Prima di poter sviluppare del nuovo codice sorgente, puoi cercare che il codice che ti serve non sia già disponibile. Puoi anche trovare progetti che ti interessano ed avvicinarti alle organizzazioni che li hanno prodotti per chiedere loro come contribuire."]
     [:br]
     [:p [:strong "Conoscete un codice?"] " Potete trovare i progetti che ti interessano e contribuire."]
     [:br]
     [:p [:strong "Non capisco queste parole!"] " Niente panico: abbiamo preparato un piccolo <a href=\"/it/glossary\">glossario</a> per aiutarvi a comprendere."]
     [:br]]
    [:div {:class "container"}
     [:h1 {:class "title"} "Posso condividere un collegamento a una richiesta?"]
     [:p "Sì! Potete usare i parametri \"s\", \"g\", \"language\" e \"license\" come in questo esempio:"]
     [:br]
     [:ul
      [:li "Travare i repository contenti \"API\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?q=API"} "https://code.etalab.gouv.fr/it/repos?q=API"]]
      [:li "Trovare i repository dei gruppi contenenti \"beta\": " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/groups?g=beta"} "https://code.etalab.gouv.fr/it/groups?g=beta"]]
      [:li "Trovare i repository su Python: " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?language=python"} "https://code.etalab.gouv.fr/it/repos?language=python"]]
      [:li "Trovare i repository sotto licenza Affero GPL: " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?license=Affero"} "https://code.etalab.gouv.fr/it/repos?license=Affero"]]
      [:li "Combinare le richieste: " [:a {:target "new" :href "https://code.etalab.gouv.fr/it/repos?license=Affero&g=beta"} "https://code.etalab.gouv.fr/it/repos?license=Affero&g=beta"]]]
     [:br]]
    [:div {:class "container"}
     [:h1 {:class "title"} "Posso aiutare a far evolvere questo sito?"]
     (md-to-string "<strong>Sì!</strong> La raccoota di <a target=\"new\" href=\"https://github.com/etalab/data-codes-sources-fr\">metadati dei repository</a> e le <a target=\"new\" href=\"https://github.com/etalab/api-codes-sources-fr\">API</a> sono mantenute da Antoine Augusti (Etalab); il sito che state consultando è <a href=\"https://github.com/etalab/codegouvfr\">sviluppato</a> da Bastien Guerry (Etalab).  Hon esitate a dare suggerimenti su questi repository, sono sotto licenza libera e tutte le contribuzioni sono benvenute.")
     [:br]
     (md-to-string "Per seguire le notizie del software gratuito utilizzato e prodotto dall'amministrazione, iscriviti alla <a target=\"new\" href=\"https://lists.eig-forever.org/subscribe/bluehats@mail.etalab.studio\">gazzetta #bluehats</a>.")
     [:br]
     (md-to-string "E per qualsiasi altra domanda, non esitare a [scriverci](/contact).")
     [:br]]]))

(defn fr-glossary [lang]
  (template
   lang
   "Glossaire pour code.etalab.gouv.fr" "Qu'est-ce qu'un dépôt ? Une « organisation » ? Une licence ?"
   [:div
    [:div {:class "container"}
     [:a {:name "source-code"} [:h2 {:class "subtitle"} "Codes sources"]]
     [:br]
     [:p "Le code source d'un programme informatique est ce qu'écrit une programmeuse ou un programmeur.  Il peut s'agir de programmes complexes ou de quelques lignes. Ce code source peut être partagé sous licence libre pour permettre aux autres programmeurs de l'étudier, de le modifier, de le diffuser et de partager leurs améliorations."]
     [:br]

     [:a {:name "repository"} [:h2 {:class "subtitle"} "Dépôt"]]
     [:br]
     (md-to-string "Un « dépôt » est un espace dans lequel sont publiés les fichiers de code source. C'est ce que vous voyez lorsque vous visitez un lien vers un code source hébergé sur une forge. C'est aussi ce que vous pouvez copier sur votre machine pour l'explorer localement.")
     [:br]

     [:a {:name "dependencies"} [:h2 {:class "subtitle"} "Dépendances"]]
     [:br]
     (md-to-string "Un logiciel intègre souvent des briques logicielles publiées sous licence libre.  Celles-ci sont appelées « dépendances ».  Ce site permet d'en parcourir la liste.  Ces informations sont collectées depuis le site [backyourstack.com](https://backyourstack.com), qui détecte les dépendances de JavaScript (NPM), PHP (Composer), .NET (Nuget), Go (dep), Ruby (Gem) et Python (Requirement).")
     [:br]
     
     [:a {:name "etoile"} [:h2 {:class "subtitle"} "Étoiles"]]
     [:br]
     (md-to-string "Les « étoiles » (« stars » en anglais) sont un moyen pour les utilisateurs des plates-formes de mettre un dépôt en favori.  Pour l'instant, nous collectons cette information sur GitHub, GitLab et les instances de GitLab.  Ce n'est pas une mesure de la qualité du code source.")
     [:br]
     
     [:a {:name "fourche"} [:h2 {:class "subtitle"} "Fourche"]]
     [:br]
     (md-to-string "Un dépôt « fourché » (ou « forké » en franglais) est un dépôt de code source qui a été développé à partir d'un autre.")
     [:br]
     
     [:a {:name "license"} [:h2 {:class "subtitle"} "Licence"]]
     [:br]
     (md-to-string "Une licence logicielle est un contrat passé entre les auteurs d'un logiciel et ses réutilisateurs.  Les licences dites « libres » accordent aux utilisateurs le droit de réutiliser le code source d'un logiciel.")
     [:br]

     [:a {:name "organization-group"} [:h2 {:class "subtitle"} "Organisation et groupe"]]
     [:br]
     (md-to-string "GitHub permet d'avoir des comptes personnels pour y héberger du code et des « comptes d'organisation ».  Un « groupe » est la notion plus ou moins équivalent sur les instance de GitLab.  Un organisme remplissant une mission de service public peut avoir un ou plusieurs organisations et/ou groupes sur une ou plusieurs forges.")
     [:br]

     [:a {:name "secteur-public"} [:h2 {:class "subtitle"} "Secteur public"]]
     [:br]
     (md-to-string "Les codes sources développés dans le cadre de missions de service public ont vocation à être publiés, dans certains conditions. Ce site propose de chercher dans l'ensemble des codes sources aujourd'hui identifiés comme provenant d'un organisme remplissant une mission de service public. Il a été développé par [la mission Etalab](https://www.etalab.gouv.fr/).")
     [:br]
     
     [:a {:name "software-heritage"} [:h2 {:class "subtitle"} "Software heritage"]]
     [:br]
     (md-to-string "<a target=\"new\" href=\"https://www.softwareheritage.org/\">Software Heritage</a> est un projet dont le but est d'archiver tous les codes sources disponibles.  Pour chaque dépôt référencé sur ce site, nous donnons le lien vers la version archivée sur Software Heritage.")
     [:br]]]))

(defn en-glossary [lang]
  (template
   lang
   "Glossary for code.etalab.gouv.fr" "What is a repository? An organization? A license?"
   [:div
    [:div {:class "container"}

     [:a {:name "dependencies"} [:h2 {:class "subtitle"} "Dependencies"]]
     [:br]
     (md-to-string "Softwares often open source libraries. These libraries are the \"dependencies\" of a software. This website allows you to browse a repository or a group dependencies.  They are collected from [backyourstack.com](https://backyourstack.com), which detects dependencies from JavaScript (NPM), PHP (Composer), .NET (Nuget), Go (dep), Ruby (Gem) and Python (Requirement).")
     [:br]

     [:a {:name "fourche"} [:h2 {:class "subtitle"} "Fork"]]
     [:br]
     (md-to-string "A \"forked\" repository est is repository derived from another one.")
     [:br]

     [:a {:name "license"} [:h2 {:class "subtitle"} "Licence"]]
     [:br]
     (md-to-string "A software licence is a contract between a software's authors and its end-users. So-called \"libre\" licences grant licensees permission to distribute, modify and share a software's source code.")
     [:br]

     [:a {:name "organization-group"} [:h2 {:class "subtitle"} "Organisation & Group"]]
     [:br]
     (md-to-string "GitHub allows to have personal accounts or \"organizations accounts\" to store source code.  A \"group\" is the more or less equivalent notion used on GitLab instances.  A public sector organization may have one or more organization accounts and/or groups on one or several software forges.")
     [:br]

     [:a {:name "secteur-public"} [:h2 {:class "subtitle"} "Public Sector"]]
     [:br]
     (md-to-string "Source code developed by a public agency must be published, under certain conditions.  This website offers the possibility to search within source code repositories that are identified as coming from public sector organisms.  It has been developed by [Etalab](https://www.etalab.gouv.fr/).")
     [:br]

     [:a {:name "repository"} [:h2 {:class "subtitle"} "Repository"]]
     [:br]
     (md-to-string "A \"repository\" is a place where source code files are stored.  This is what you see when you browse a link to a source code as hosted on a software forge.  This is also what you copy on your machine to explore it locally.")
     [:br]

     [:a {:name "software-heritage"} [:h2 {:class "subtitle"} "Software Heritage"]]
     [:br]
     (md-to-string "<a target=\"new\" href=\"https://www.softwareheritage.org/\">Software Heritage</a> is a project whose ambition is to collect, preserve, and share all software that is publicly available in source code form. Each repository here referenced is linked to its corresponding Software Heritage archive version.")
     [:br]

     [:a {:name "source-code"} [:h2 {:class "subtitle"} "Source Code"]]
     [:br]
     [:p "A software's source code is what a developer writes.  Source code can be just a few lines or quite a few of them. Source code can be made available under a free licence for others to freely distribute, modify and share."]
     [:br]

     [:a {:name "etoile"} [:h2 {:class "subtitle"} "Stars"]]
     [:br]
     (md-to-string "Stars allow users to mark a repository as favourite. At the moment, we collect repository favouriteness data from GitHub, Gitlab and instances thereof. Favouriteness is not a source code quality metric.")
     [:br]

     ]]))

(defn it-glossary [lang]
  (template
   lang
   "Glossario di code.etalab.gouv.fr" "Cosa è un repository? Un'organizzazione? Una licenza?"
   [:div
    [:div {:class "container"}
     
     [:a {:name "source-code"} [:h2 {:class "subtitle"} "Codici sorgenti"]]
     [:br]
     [:p "Il codice sorgente di un programma informatico è quello che scrive una programmatrice o un programmatore. Questi possono essere sia programmi molto complessi che programmi di poche linee. Il codice sorgente può essere condiviso con una licenza aperta per consentire ad altri programmatori di studiare, modificare, distribuire e condividere i loro miglioramenti al codice."]
     [:br]

     ;; TODO: i18n
     [:a {:name "dependencies"} [:h2 {:class "subtitle"} "Dependencies"]]
     [:br]
     (md-to-string "Softwares often open source libraries. These libraries are the \"dependencies\" of a software. This website allows you to browse a repository or a group dependencies.  They are collected from [backyourstack.com](https://backyourstack.com), which detects dependencies from JavaScript (NPM), PHP (Composer), .NET (Nuget), Go (dep), Ruby (Gem) and Python (Requirement).")
     [:br]

     [:a {:name "fourche"} [:h2 {:class "subtitle"} "Fork"]]
     [:br]
     (md-to-string "Una fork è un repository che è stato sviluppato partendo dal codice presente in un altro repository pubblico.")
     [:br]

     [:a {:name "license"} [:h2 {:class "subtitle"} "Licenze"]]
     [:br]
     (md-to-string "Una licenza è un contratto sviluppato tra l'autore di un programma e i suoi utilizzatori. Le licenze dette \"libere\" concedono agli utilizzatori il diritto di riutilizzare il codice sorgente di un programma.")
     [:br]

     [:a {:name "organization-group"} [:h2 {:class "subtitle"} "Organizzazione e gruppi"]]
     [:br]
     (md-to-string "GitHub è un sito di verisonamento del codice e permette di avere dei repository personali e dei \"repository di organizzazione\". Un gruppo è la nozione più o meno equivalente sulle istanze di GitLab. Un organismo che svolge una missione di servizio pubblico può avere una o più organizzazioni e/o gruppi su una o più siti di verisonamento del codice.")
     [:br]

     [:a {:name "repository"} [:h2 {:class "subtitle"} "Repository"]]
     [:br]
     (md-to-string "Un repository è uno spazio dentro il quale vengono pubblicati i file di codice sorgente. Questo è ciò che si vede quando si visita un link al codice sorgente ospitato su un repository. &Egrave; anche quello che puoi copiare sul tuo computer per esplorarlo localmente.")
     [:br]

     [:a {:name "secteur-public"} [:h2 {:class "subtitle"} "Settore pubblico"]]
     [:br]
     (md-to-string "I codici sorgente sviluppati nell'ambito di servizi pubblici sono destinati a essere pubblicati in open source sotto determinate condizioni. Questo sito offre la possibilità di cercare nell'insieme di codici sorgenti oggi identificati come provenienti da un'organizzazione che svolge un compito di servizio pubblico. Il sito è stato sviluppato da [Etalab](https://www.etalab.gouv.fr/).")
     [:br]

     [:a {:name "software-heritage"} [:h2 {:class "subtitle"} "Software heritage"]]
     [:br]
     (md-to-string "<a target=\"new\" href=\"https://www.softwareheritage.org/\">Software Heritage</a> è un progetto che ha lo scopo di archiviare tutti i codici sorgenti disponbili. Per ciascun repository referenziato in questo sito, viene fornito il link alla versione archiviata su Software Heritage.")
     [:br]

     [:a {:name "etoile"} [:h2 {:class "subtitle"} "Stelle"]]
     [:br]
     (md-to-string "Le stelle («star» in inglese) sono un mezzo per permettere agi utilizzato delle piattaforme di versionamento del codice di mettere un repository tra i preferity. Al momento, noi memorizziamo questa informazion su GitHub, GitLab e le istanze private di GitLab. Questa non è una misura della qualità del codice sorgente.")
     [:br]

     ]]))
