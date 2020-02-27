(ns codegouvfr.i18n
  (:require [taoensso.tempura :refer [tr]]))

(def supported-languages
  "A set of supported languages."
  #{"en" "fr" "it"})

(def localization
  ;; French translation
  {:fr
   {
    :Repo                        "Dépôt"
    :Repos                       "Dépôts"
    :repo-depending-on           " dépôt dépendant de "
    :repos-depending-on          " dépôts dépendants de "
    :matching                    " et contenant "
    :matching-s                  " et contenants "
    :about                       "À propos"
    :affiliation-placeholder     "Par ex. DGFiP"
    :archive                     "Archive"
    :archive-on                  "Archive sur "
    :back-to-repos               "Retour à la liste des dépôts de code source"
    :browse-repos-orga           "Voir la liste des dépôts de cette organisation ou de ce groupe"
    :contact                     "Contact"
    :contact-baseline            "Un compte d'organisation à signaler ? Un dépôt de code à ouvrir ? Sollicitez-nous !"
    :contact-by-email            "Contacter par email"
    :contact-form                "Formulaire de contact"
    :core-dep                    "Production"
    :created-at                  "Créé le "
    :deps                        "Dépendances"
    :dep-of                      "dépendance identifiée pour"
    :deps-of                     "dépendances identifiées pour"
    :deps-stats                  "Nombre de dépendances identifiées"
    :deps-not-found              "Pas de dépendances identifiées."
    :description                 "Description"
    :dev-dep                     "Développement"
    :distribution-by-platform    "Répartition par plateforme"
    :download                    "Télécharger"
    :email-placeholder           "Par ex. toto@modernisation.gouv.fr"
    :fav-add                     "Ajouter aux favoris"
    :fav-sort                    "Trier par favoris"                        
    :forks                       "Fourches"
    :free-search                 "Recherche libre"
    :github-gitlab-etc           "Sur GitHub ou sur des instances GitLab"
    :glossary                    "Glossaire"
    :go-to-glossary              "Voir le glossaire"
    :go-to-orga                  "Visiter le compte d'organisation ou le groupe"
    :go-to-repo                  "Voir ce dépôt"
    :go-to-repos                 "Voir les dépôts"
    :go-to-sig-website           "Visiter le site sur l'annuaire du service public"
    :go-to-website               "Visiter le site web"
    :groups                      " groupes"
    :group-repo-not-found        "Groupe et dépôt non trouvés."
    :here                        "ici"
    :issues                      "Tickets"
    :keywords                    "Accès aux codes sources du secteur public"
    :language                    "Langage"
    :languages                   "Langages"
    :last-repos                  "Derniers dépôts de codes sources publics"
    :license                     "Licence"
    :licenses                    "Licences"
    :main-etalab-website         "Site principal d'Etalab"
    :mean-repos-by-orga          "Nombre moyen de dépôts par organisation/groupe"
    :median-repos-by-orga        "Nombre médian de dépôts par organisation/groupe"
    :message-placeholder         "Votre message"
    :message-received            "Message reçu !"
    :message-received-ok         "Nous nous efforçons de répondre au plus vite."
    :most-used-languages         "Répartition en % des 9 langages les plus utilisés"
    :most-used-licenses          "Licences les plus utilisées (%)"
    :name                        "Nom"
    :no-archived-repos           "Ne pas inclure les dépôts archivés"
    :no-archives                 " Sauf archives"
    :no-orga-found               "Pas d'organisation ou de groupe trouvé : une autre idée de requête ?"
    :no-repo-found               "Pas de dépôt trouvé : une autre idée de requête ?"
    :number-of-repos             "Nombre de dépôts"
    :one-group                   " groupe"
    :only-forked-repos           "Que les dépôts fourchés d'autres dépôts"
    :only-forks                  " Fourches seules"
    :only-orga-with-code         "Que les organisations ayant publié du code"
    :only-with-description-repos "Que les dépôts ayant une description"
    :only-with-license           "Que les dépôts ayant une licence identifiée"
    :orga-repo                   "Dépôt < groupe"
    :orgas-or-groups             "Organisations ou groupes"
    :orgas-with-more-stars       "Organisations/groupes les plus étoilés"
    :percent-of-repos-archived   "Proportion de dépôts archivés"
    :remove-filter               "Supprimer le filtre : voir toutes les organisations ou groupes"
    :repo                        " dépôt"
    :repo-archived               "Ce dépôt est archivé"
    :report-new-source-code      "Signalez-nous vos ouvertures de codes sources"
    :repos                       " dépôts"
    :repos-number                "Nombre de dépôts"
    :repos-of-source-code        "Dépôts de code source"
    :repos-on-swh                "Dépôts dans Software Heritage"
    :reuses                      "Réutilisations"
    :reuses-expand               "Réutilisations dans d'autres dépôts ou paquetages"
    :reused                      "Réutil."
    :sort                        "Trier"
    :sort-production             "Trier par l'usage en production"
    :sort-development            "Trier par l'usage en développement"
    :sort-name                   "Trier par nom"
    :sort-type                   "Trier par type"
    :sort-alpha                  "Par ordre alphabetique"
    :sort-creation               "Par date de création"
    :sort-description-length     "Trier par longueur de description"
    :sort-forks                  "Trier par nombre de fourches"
    :sort-issues                 "Trier par nombre de tickets"
    :sort-orgas-alpha            "Trier par ordre alphabétique des noms d'organisations ou de groupes"
    :sort-orgas-creation         "Trier par date de création de l'organisation ou du groupe"
    :sort-repos                  "Par nombre de dépôts"
    :sort-repos-alpha            "Trier par ordre alphabétique des noms de dépôts"
    :sort-stars                  "Trier par nombre d'étoiles"
    :sort-reused                 "Trier par nombre de dépôts et/ou paquetages réutilisateurs"
    :sort-update-date            "Trier par date de mise à jour"
    :source-code                 "code source"
    :source-code-available       ", code source disponible "
    :stars                       "Étoiles"
    :stats                       "Chiffres"
    :submit                      "Envoyer"
    :subscribe-rss-flux          "S'abonner au flux RSS des derniers dépôts"
    :swh-link                    "Lien vers l'archive faite par Software Heritage"
    :type                        "Type"
    :under-license               " sous licence "
    :understand-tech-terms       "Comprendre les termes techniques de ce site"
    :update                      "Mise à jour"
    :update-short                "MàJ"
    :visit-on-github             "Visiter sur GitHub"
    :visit-on-gitlab             "Visiter sur l'instance GitLab"
    :website-developed-by        "Site développé par la mission "
    :why-this-website?           "Pourquoi ce site ?"
    :with-code                   " Avec du code publié"
    :with-description            " Avec description"
    :with-license                " Avec licence identifiée"
    :with-more-of                " avec le plus de"
    :your-affiliation            "Votre organisme de rattachement"
    :your-email                  "Votre adresse de courriel"
    :your-message                "Message"
    :your-name                   "Votre nom"
    }
   ;; English translation
   :en
   {
    :Repo                        "Repository"
    :Repos                       "Repositories"
    :repo-depending-on           " repository depending on "
    :repos-depending-on          " repositories depending on "
    :matching                    " and matching "
    :matching-s                  " and matching "
    :about                       "About"
    :affiliation-placeholder     "E.g. DGFiP"
    :archive                     "Archive"
    :archive-on                  "Archive on "
    :back-to-repos               "Go back to the source code repository list"
    :browse-repos-orga           "See the list of repositories from this organization or group"
    :contact                     "Contact"
    :contact-baseline            "Want to share an organization? A repository? Let us know!"
    :contact-by-email            "Contact by email"
    :contact-form                "Contact form"
    :core-dep                    "Core"
    :created-at                  "Created on "
    :deps                        "Dependencies"
    :dep-of                      "identified dependency for"
    :deps-of                     "identified dependencies for"
    :deps-stats                  "Number of identified dependencies"
    :deps-not-found              "No identified dependencies."
    :description                 "Description"
    :dev-dep                     "Development"
    :distribution-by-platform    "Distribution per platform"
    :download                    "Download"
    :email-placeholder           "E.g. toto@modernisation.gouv.fr"
    :fav-add                     "Add to favorites"
    :fav-sort                    "Sort by favorites"                        
    :forks                       "Forks"
    :free-search                 "Free search"
    :github-gitlab-etc           "On GitHub or on GitLab instances"
    :glossary                    "Glossary"
    :go-to-glossary              "See the glossary"
    :go-to-orga                  "Visit the organization or group"
    :go-to-repo                  "See this repository"
    :go-to-repos                 "Go to the repositories"
    :go-to-sig-website           "Go to website on lannuaire.service-public.fr"
    :go-to-website               "Go to website"
    :groups                      " groups"
    :group-repo-not-found        "Group/repository not found."
    :here                        "here"
    :issues                      "Issues"
    :keywords                    "List public sector source codes"
    :language                    "Language"
    :languages                   "Languages"
    :last-repos                  "Latest public source code repositories"
    :license                     "License"
    :licenses                    "Licenses"
    :main-etalab-website         "Etalab's main website"
    :mean-repos-by-orga          "Mean number of repositories by organizations/groups"
    :median-repos-by-orga        "Median number of repositories by orgnizations/groups"
    :message-placeholder         "Your message"
    :message-received            "Message received!"
    :message-received-ok         "We will do our best to reply as soon as possible."
    :most-used-languages         "Distribution in % of the 9 most used languages"
    :most-used-licenses          "Most used licenses (%)"
    :name                        "Name"
    :no-archived-repos           "Do not include archived repositories"
    :no-archives                 " Hide archives"
    :no-orga-found               "Organization or group not found: would you like to make a new request?"
    :no-repo-found               "Repository not found : would you like to make a new request?"
    :number-of-repos             "Number of repositories"
    :one-group                   " group"
    :only-forked-repos           "Forked repositories only"
    :only-forks                  " Forks only"
    :only-orga-with-code         "Only organizations with published code"
    :only-with-description-repos "Repositories with a description only"
    :only-with-license           "Only repositories with a known license"
    :orga-repo                   "Repository < group"
    :orgas-or-groups             "Organizations or groups"
    :orgas-with-more-stars       "Organizations/groups with the most stars"
    :percent-of-repos-archived   "Percent of archived repositories"
    :remove-filter               "Remove filter: display all the organizations or groups"
    :repo                        " repository"
    :repo-archived               "This is an archived repository"
    :report-new-source-code      "Let us know when you open a source code"
    :repos                       " repositories"
    :repos-number                "Number of repositories"
    :repos-of-source-code        "Source code repositories"
    :repos-on-swh                "Repositories in Software Heritage"
    :reuses                      "Reuses"
    :reuses-expand               "Reuses in other repositories or packages"
    :reused                      "Reused"
    :sort                        "Sort"
    :sort-alpha                  "By alphabetical order"
    :sort-creation               "By creation date"
    :sort-description-length     "Sort by description length"
    :sort-forks                  "Sort by number of forks"
    :sort-issues                 "Sort by number of issues"
    :sort-orgas-alpha            "Sort by alphabetical order of organizations or groups"
    :sort-orgas-creation         "Sort by creation date of the organization or group"
    :sort-repos                  "By number of repositories"
    :sort-repos-alpha            "Sort repositories alphabetically"
    :sort-stars                  "Sort by number of stars"
    :sort-reused                 "Sort by number of reuses in other repositories and/or packages"
    :sort-update-date            "Sort by update date"
    :source-code                 "source code"
    :source-code-available       ", the source code is available "
    :stars                       "Stars"
    :stats                       "Figures"
    :submit                      "Send"
    :subscribe-rss-flux          "Subscribe to our RSS feed to receive information about the latest repositories!"
    :swh-link                    "A link to the Software Heritage archive"
    :type                        "Type"
    :under-license               " licensed "
    :understand-tech-terms       "A glossary to understand the technical terms used on this website"
    :update                      "Update"
    :update-short                "Updated"
    :visit-on-github             "Browse on GitHub"
    :visit-on-gitlab             "Browse on the GitLab instance"
    :website-developed-by        "This website is powered by "
    :why-this-website?           "Why this website?"
    :with-code                   " With published code"
    :with-description            " With a description"
    :with-license                " With known license"
    :with-more-of                " with the most"
    :your-affiliation            "Your affiliation"
    :your-email                  "Your email address"
    :your-message                "Message"
    :your-name                   "Your name"
    }
   ;; Italian translation
   :it
   {
    :Repo                        "Repository"
    :Repos                       "Repositories"
    :repo-depending-on           " repository dipendente da "
    :repos-depending-on          " repositories dipendente da "
    :matching                    " e contenente "
    :matching-s                  " e contenente "
    :about                       "About"
    :affiliation-placeholder     "Es DGFiP"
    :archive                     "Archivia"
    :archive-on                  "Archiviato su "
    :back-to-repos               "Ritorna alla lista dei repository di codice sorgente"
    :browse-repos-orga           "Vedere la lista di repository di questa organizazzione o di questo gruppo"
    :contact                     "Contatti"
    :contact-baseline            "Hai un account organizzativo da segnalare? Un repository di codice da aprire? Scrivici!"
    :contact-by-email            "Contatta per email"
    :contact-form                "Contatti da"
    :core-dep                    "Core"                              ;; TODO
    :created-at                  "Creato il "
    :deps                        "Dependencies"                      ;; TODO
    :dep-of                      "identified dependency for"         ;; TODO
    :deps-of                     "Identified dependencies for "      ;; TODO
    :deps-stats                  "Number of identified dependencies" ;; TODO
    :deps-not-found              "No identified dependencies."       ;; TODO
    :description                 "Descrizione"
    :dev-dep                     "Development"                       ;; TODO
    :distribution-by-platform    "Distribuzione per piattaforma"
    :download                    "Scaricare"
    :email-placeholder           "Es toto@modernisation.gouv.fr"
    :fav-add                     "Add to favorites"                  ;; TODO
    :fav-sort                    "Sort by favorites"                 ;; TODO                 
    :forks                       "Fork"
    :free-search                 "Ricerca libera"
    :github-gitlab-etc           "Su GitHub o su istanze di GitLab"
    :glossary                    "Glossario"
    :go-to-glossary              "Vedi il glossario"
    :go-to-orga                  "Visita la pagina dell'organizzazione o del gruppo"
    :go-to-repo                  "Vedere questo repository"
    :go-to-repos                 "Vedi i reposutory"
    :go-to-sig-website           "Visita il sito sull'annuario di servizio pubblico"
    :go-to-website               "Visita il sito web"
    :groups                      " grouppi"
    :group-repo-not-found        "Group/repository not found."       ;; TODO
    :here                        "qui"
    :issues                      "Issue"
    :keywords                    "Accesso ai codici sorgenti del settore pubblico"
    :language                    "Lingua"
    :languages                   "Linguaggi"
    :last-repos                  "Ultimi repository di codice sorgente pubblico"
    :license                     "Licenza"
    :licenses                    "Licenze"
    :main-etalab-website         "Sito principale di Etalab"
    :mean-repos-by-orga          "Numero medio di repository per organizzazione/gruppo"
    :median-repos-by-orga        "Numero mediano di repository per organizzazione/gruppo"
    :message-placeholder         "Il tuo messaggio"
    :message-received            "Messaggio ricevuto!"
    :message-received-ok         "Faremo del nostro meglio per rispondere il prima possibile."
    :most-used-languages         "Distribuzione in % delle 9 linguaggi più utilizzate"
    :most-used-licenses          "Licenze più utilizzate (%)"
    :name                        "Name"                              ;; TODO
    :no-archived-repos           "Non includere i repository archiviati"
    :no-archives                 " Rimuovi archivi"
    :no-orga-found               "Nessuna organizzazione o gruppo trovato: vuoi provare con un'altra richiesta?"
    :no-repo-found               "Repository non trovato: vuoi provre a fare una nuova ricerca?"
    :number-of-repos             "Number of repositories"            ;; TODO
    :one-group                   " gruppo"
    :one-repo                    " repository"
    :only-forked-repos           "Solo repository forcati"
    :only-forks                  " Solo fork"
    :only-orga-with-code         "Organizzazione che hanno pubblicato del codice"
    :only-with-description-repos "Repository con solo una descrizione"
    :only-with-license           "Repository con una licenza identificata"
    :orga-repo                   "Repository < gruppo"
    :orgas-or-groups             "Organizzazioni o gruppi"
    :orgas-with-more-stars       "Organizzaioni/gruppi con più stelle"
    :percent-of-repos-archived   "Proporzione dei repository archiviati"
    :remove-filter               "Rimuovi i filtri: mostra tutte le organizzazioni o gruppi"
    :repo                        " repository"
    :repo-archived               "Questo è un repository archiviato"
    :report-new-source-code      "Facci sapere quando hai rilascito del codice sorgente"
    :repos                       " repositories"
    :repos-number                "Numero di repository"
    :repos-of-source-code        "Repository del codice sorgente"
    :repos-on-swh                "Repository di Software Heritage"
    :reuses                      "Riutilizzi"
    :reuses-expand               "Riutilizzo in altri repository o pacchetti"
    :reused                      "Utilizzi"
    :sort                        "Ordina"
    :sort-alpha                  "Ordine alfabetico"
    :sort-creation               "Per data di creazione"
    :sort-description-length     "Ordina per lunghezza della descrizione"
    :sort-forks                  "Ordina per numero di fork"
    :sort-issues                 "Ordina per numero di issue"
    :sort-orgas-alpha            "Ordinare per ordine alfabetico del nome dell'organizzazione o del gruppo"
    :sort-orgas-creation         "Ordinare per data di creazione dell'organizzazione o del gruppo"
    :sort-repos                  "Per numero di repository"
    :sort-repos-alpha            "Ordina alfabeticamente i repository"
    :sort-stars                  "Ordina per numero di stelle"
    :sort-reused                 "Ordina per numero di utilizzo in altri repository e / o pacchetti"
    :sort-update-date            "Ordina per data di aggiornamento"
    :source-code                 "codide sorgente"
    :source-code-available       ", il codice sorgente è disponibile "
    :stars                       "Stelle"
    :stats                       "Statistiche"
    :submit                      "Invia"
    :subscribe-rss-flux          "Sottoscrivi il nostro feed RSS per ricevere informazioni sugli ultimi repository pubblicati!"
    :swh-link                    "Link all'archivio fatto per il Software Heritage"
    :type                        "Type"                              ;; TODO
    :under-license               " licenza "
    :understand-tech-terms       "Glossario per comprendere i termini tecnici usati in questo sito web"
    :update                      "Aggiorna"
    :update-short                "Agg."
    :visit-on-github             "Visita su GitHub"
    :visit-on-gitlab             "Visite su GitLab"
    :website-developed-by        "Questo sito è supprotato da "
    :why-this-website?           "Perché questo sito?"
    :with-code                   " Con del codice pubblicato"
    :with-description            " Con una descrizione"
    :with-license                " Con una licenza identificata"
    :with-more-of                " con più di"
    :your-affiliation            "La tua affiliazione"
    :your-email                  "Il tuo indirizzo email"
    :your-message                "Messaggio"
    :your-name                   "Il tuo nome"
    }})

(def opts {:dict localization})

(defn i
  "Main i18n fonction."
  [lang input]
  (tr opts [lang] input))
