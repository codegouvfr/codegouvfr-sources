(ns codegouvfr.i18n
  (:require [taoensso.tempura :refer [tr]]))

(def supported-languages #{"en" "fr" "it"})

(def localization
  ;; French translation
  {:fr
   {:no-repo-found               "Pas de dépôt trouvé : une autre idée de requête ?"
    :orga-repo                   "Dépôt / groupe"
    :sort-repos-alpha            "Trier par ordre alphabétique des noms de dépôts"
    :archive                     "Archive"
    :swh-link                    "Lien vers l'archive faite par Software Heritage"
    :description                 "Description"
    :sort-description-length     "Trier par longueur de description"
    :update                      "Mise à jour"
    :sort-update-date            "Trier par date de mise à jour"
    :update-short                "MàJ"
    :forks                       "Fourches"
    :sort-forks                  "Trier par nombre de fourches"
    :stars                       "Étoiles"
    :sort-stars                  "Trier par nombre d'étoiles"
    :issues                      "Tickets"
    :sort-issues                 "Trier par nombre de tickets"
    :browse-repos-orga           "Voir la liste des dépôts de cette organisation ou de ce groupe"
    :go-to-repo                  "Voir ce dépôt"
    :under-license               " sous licence "
    :repo-archivedo              "Ce dépôt est archivé"
    :no-orga-found               "Pas d'organisation ou de groupe trouvé : une autre idée de requête ?"
    :go-to-orga                  "Visiter le compte d'organisation ou le groupe"
    :created-at                  "Créé le "
    :repos-number                "Nombre de dépôts"
    :go-to-repos                 "Voir les dépôts"
    :repo                        " dépôt"
    :repos                       " dépôts"
    :visit-on-github             "Visiter sur GitHub"
    :visit-on-gitlab             "Visiter sur l'instance GitLab"
    :contact-by-email            "Contacter par email"
    :go-to-website               "Visiter le site web"
    :go-to-sig-website           "Visiter le site sur l'annuaire du service public"
    :go-to-glossary              "Voir le glossaire"
    :source-code                 "code source"
    :repos-of-source-code        "Dépôts de code source"
    :orgas-or-groups             "Organisations ou groupes"
    :mean-repos-by-orga          "Nombre moyen de dépôts par organisation/groupe"
    :median-repos-by-orga        "Nombre médian de dépôts par organisation/groupe"
    :with-more-of                " avec le plus de "
    :orgas-with-more-stars       "Organisations/groupes les plus étoilés"
    :language                    "Langage"
    :languages                   "Langages"
    :license                     "Licence"
    :licenses                    "Licences"
    :more-used                   " les plus utilisées"
    :distribution-by-platform    "Répartition par plateforme"
    :archive-on                  "Archive sur "
    :repos-on-swh                "Dépôts dans Software Heritage"
    :percent-of-repos-archived   "Proportion de dépôts archivés"
    :github-gitlab-etc           "Sur GitHub ou sur des instances GitLab"
    :stats                       "Chiffres"
    :free-search                 "Recherche libre"
    :remove-filter               "Supprimer le filtre : voir toutes les organisations ou groupes"
    :only-forks                  " Fourches seules"
    :only-forked-repos           "Que les dépôts fourchés d'autres dépôts"
    :no-archives                 " Sauf archives"
    :no-archived-repos           "Ne pas inclure les dépôts archivés"
    :only-with-description-repos "Que les dépôts ayant une description"
    :with-description            " Avec description"
    :only-with-license           "Que les dépôts ayant une licence identifiée"
    :with-license                " Avec licence identifiée"
    :one-repo                    "1 dépôt"
    :only-orga-with-code         "Que les organisations ayant publié du code"
    :with-code                   " Avec du code publié"
    :sort-orgas-alpha            "Trier par ordre alphabétique des noms d'organisations ou de groupes"
    :sort-alpha                  "Par ordre alphabetique"
    :sort-repos                  "Trier par nombre de dépôts"
    :sort-orgas-creation         "Trier par date de création de l'organisation ou du groupe"
    :sort-creation               "Par date de création"
    :one-group                   "1 groupe"
    :groups                      " groupes"
    :last-repos                  "Derniers dépôts de codes sources publics"
    :keywords                    "Accès aux codes sources du secteur public"
    :report-new-source-code      "Signalez-nous vos ouvertures de codes sources"
    :understand-tech-terms       "Comprendre les termes techniques de ce site"
    :why-this-website?           "Pourquoi ce site ?"
    :main-etalab-website         "Site principal d'Etalab"
    :subscribe-rss-flux          "S'abonner au flux RSS des derniers dépôts"
    :website-developed-by        "Site développé par la mission "
    :source-code-available       ", code source disponible "
    :here                        "ici"
    :contact                     "Contact"
    :contact-form                "Formulaire de contact"
    :contact-baseline            "Un compte d'organisation à signaler ? Un dépôt de code à ouvrir ? Sollicitez-nous !"
    :your-name                   "Votre nom"
    :your-email                  "Votre adresse de courriel"
    :email-placeholder           "Par ex. toto@modernisation.gouv.fr"
    :your-affiliation            "Votre organisme de rattachement"
    :affiliation-placeholder     "Par ex. DGFiP"
    :your-message                "Message"
    :message-placeholder         "Votre message"
    :submit                      "Envoyer"
    :message-received            "Message reçu !"
    :message-received-ok         "Nous nous efforçons de répondre au plus vite."
    :back-to-repos               "Retour à la liste des dépôts de code source"
    :glossary                    "Glossaire"
    :about                       "À propos"}
   ;; English translation
   :en
   {:no-repo-found               "Repository not found : would you like to make a new request?"
    :orga-repo                   "Repository / group"
    :sort-repos-alpha            "Sort repositories alphabetically"
    :archive                     "Archive"
    :swh-link                    "A link to the Software Heritage archive"
    :description                 "Description"
    :sort-description-length     "Sort by description length"
    :update                      "Update"
    :sort-update-date            "Sort by update date"
    :update-short                "Updated"
    :forks                       "Forks"
    :sort-forks                  "Sort by number of forks"
    :stars                       "Stars"
    :sort-stars                  "Sort by number of stars"
    :issues                      "Issues"
    :sort-issues                 "Sort by number of issues"
    :browse-repos-orga           "See the list of repositories from this organization or group"
    :go-to-repo                  "See this repository"
    :under-license               " licensed "
    :repo-archivedo              "This is an archived repository"
    :no-orga-found               "Organization or group not found: would you like to make a new request?"
    :go-to-orga                  "Visit the organization or group"
    :created-at                  "Created on "
    :repos-number                "Number of repositories"
    :go-to-repos                 "Go to the repositories"
    :repo                        " repository"
    :repos                       " repositories"
    :visit-on-github             "Browse on GitHub"
    :visit-on-gitlab             "Browse on the GitLab instance"
    :contact-by-email            "Contact by email"
    :go-to-website               "Go to website"
    :go-to-sig-website           "Go to website on lannuaire.service-public.fr"
    :go-to-glossary              "See the glossary"
    :source-code                 "source code"
    :repos-of-source-code        "Source code repositories"
    :orgas-or-groups             "Organizations or groups"
    :mean-repos-by-orga          "Mean number of repositories by organizations/groups"
    :median-repos-by-orga        "Median number of repositories by orgnizations/groups"
    :with-more-of                " with the most "
    :orgas-with-more-stars       "Organizations/groups with the most stars"
    :language                    "Language"
    :languages                   "Languages"
    :license                     "License"
    :licenses                    "Licenses"
    :more-used                   " the most used"
    :distribution-by-platform    "Distribution per platform"
    :archive-on                  "Archive on "
    :repos-on-swh                "Repositories in Software Heritage"
    :percent-of-repos-archived   "Percent of archived repositories"
    :github-gitlab-etc           "On GitHub or on GitLab instances"
    :stats                       "Figures"
    :free-search                 "Free search"
    :remove-filter               "Remove filter: display all the organizations or groups"
    :only-forks                  " Forks only"
    :only-forked-repos           "Forked repositories only"
    :no-archives                 " Hide archives"
    :no-archived-repos           "Do not include archived repositories"
    :only-with-description-repos "Repositories with a description only"
    :with-description            " With a description"
    :only-with-license           "Only repositories with a known license"
    :with-license                " With known license"
    :one-repo                    "1 repository"
    :only-orga-with-code         "Only organizations with published code"
    :with-code                   " With published code"
    :sort-orgas-alpha            "Sort by alphabetical order of organizations or groups"
    :sort-alpha                  "Alphabetical order"
    :sort-repos                  "Sort by the number of repositories"
    :sort-orgas-creation         "Sort by creation date of the organization or group"
    :sort-creation               "Sort by creation date"
    :one-group                   "1 group"
    :groups                      " groups"
    :last-repos                  "Latest public source code repositories"
    :keywords                    "List public sector source codes"
    :report-new-source-code      "Let us know when you open a source code"
    :understand-tech-terms       "A glossary to understand the technical terms used on this website"
    :why-this-website?           "Why this website?"
    :main-etalab-website         "Etalab's main website"
    :subscribe-rss-flux          "Subscribe to our RSS feed to receive information about the latest repositories!"
    :website-developed-by        "This website is powered by "
    :source-code-available       ", the source code is available "
    :here                        "here"
    :contact                     "Contact"
    :contact-form                "Contact form"
    :contact-baseline            "Want to share an organization? A repository? Let us know!"
    :your-name                   "Your name"
    :your-email                  "Your email address"
    :email-placeholder           "E.g. toto@modernisation.gouv.fr"
    :your-affiliation            "Your affiliation"
    :affiliation-placeholder     "E.g. DGFiP"
    :your-message                "Message"
    :message-placeholder         "Your message"
    :submit                      "Send"
    :message-received            "Message received!"
    :message-received-ok         "We will do our best to reply as soon as possible."
    :back-to-repos               "Go back to the source code repository list"
    :glossary                    "Glossary"
    :about                       "About"}
   ;; Italian translation
   :it
   {:no-repo-found               "Repository non trovato: vuoi provre a fare una nuova ricerca?"
    :orga-repo                   "Repository / gruppo"
    :sort-repos-alpha            "Ordina alfabeticamente i repository"
    :archive                     "Archivia"
    :swh-link                    "Link all'archivio fatto per il Software Heritage"
    :description                 "Descrizione"
    :sort-description-length     "Ordina per lunghezza della descrizione"
    :update                      "Aggiorna"
    :sort-update-date            "Ordina per data di aggiornamento"
    :update-short                "Agg."
    :forks                       "Fork"
    :sort-forks                  "Ordina per numero di fork"
    :stars                       "Stelle"
    :sort-stars                  "Ordina per numero di stelle"
    :issues                      "Issue"
    :sort-issues                 "Ordina per numero di issue"
    :browse-repos-orga           "Vedere la lista di repository di questa organizazzione o di questo gruppo"
    :go-to-repo                  "Vedere questo repository"
    :under-license               " licenza "
    :repo-archivedo              "Questo è un repository archiviato"
    :no-orga-found               "Nessuna organizzazione o gruppo trovato: vuoi provare con un'altra richiesta?"
    :go-to-orga                  "Visita la pagina dell'organizzazione o del gruppo"
    :created-at                  "Creato il "
    :repos-number                "Numero di repository"
    :go-to-repos                 "Vedi i reposutory"
    :repo                        " repository"
    :repos                       " repository"
    :visit-on-github             "Visita su GitHub"
    :visit-on-gitlab             "Visite su GitLab"
    :contact-by-email            "Contatta per email"
    :go-to-website               "Visita il sito web"
    :go-to-sig-website           "Visita il sito sull'annuario di servizio pubblico"
    :go-to-glossary              "Vedi il glossario"
    :source-code                 "codide sorgente"
    :repos-of-source-code        "Repository del codice sorgente"
    :orgas-or-groups             "Organizzazioni o gruppi"
    :mean-repos-by-orga          "Numero medio di repository per organizzazione/gruppo"
    :median-repos-by-orga        "Numero mediano di repository per organizzazione/gruppo"
    :with-more-of                " con più di "
    :orgas-with-more-stars       "Organizzaioni/gruppi con più stelle"
    :language                    "Lingua"
    :languages                   "Linguaggi"
    :license                     "Licenza"
    :licenses                    "Licenze"
    :more-used                   " più utilizzate"
    :distribution-by-platform    "Distribuzione per piattaforma"
    :archive-on                  "Archiviato su "
    :repos-on-swh                "Repository di Software Heritage"
    :percent-of-repos-archived   "Proporzione dei repository archiviati"
    :github-gitlab-etc           "Su GitHub o su istanze di GitLab"
    :stats                       "Statistiche"
    :free-search                 "Ricerca libera"
    :remove-filter               "Rimuovi i filtri: mostra tutte le organizzazioni o gruppi"
    :only-forks                  " Solo fork"
    :only-forked-repos           "Solo repository forcati"
    :no-archives                 " Rimuovi archivi"
    :no-archived-repos           "Non includere i repository archiviati"
    :only-with-description-repos "Repository con solo una descrizione"
    :with-description            " Con una descrizione"
    :only-with-license           "Repository con una licenza identificata"
    :with-license                " Con una licenza identificata"
    :one-repo                    "1 repository"
    :only-orga-with-code         "Organizzazione che hanno pubblicato del codice"
    :with-code                   " Con del codice pubblicato"
    :sort-orgas-alpha            "Ordinare per ordine alfabetico del nome dell'organizzazione o del gruppo"
    :sort-alpha                  "Ordine alfabetico"
    :sort-repos                  "Ordinare per numero di repository"
    :sort-orgas-creation         "Ordinare per data di creazione dell'organizzazione o del gruppo"
    :sort-creation               "Ordine per data di creazione"
    :one-group                   "1 gruppo"
    :groups                      " grouppi"
    :last-repos                  "Ultimi repository di codice sorgente pubblico"
    :keywords                    "Accesso ai codici sorgenti del settore pubblico"
    :report-new-source-code      "Facci sapere quando hai rilascito del codice sorgente"
    :understand-tech-terms       "Glossario per comprendere i termini tecnici usati in questo sito web"
    :why-this-website?           "Perché questo sito?"
    :main-etalab-website         "Sito principale di Etalab"
    :subscribe-rss-flux          "Sottoscrivi il nostro feed RSS per ricevere informazioni sugli ultimi repository pubblicati!"
    :website-developed-by        "Questo sito è supprotato da "
    :source-code-available       ", il codice sorgente è disponibile "
    :here                        "qui"
    :contact                     "Contatti"
    :contact-form                "Contatti da"
    :contact-baseline            "Hai un account organizzativo da segnalare? Un repository di codice da aprire? Scrivici!"
    :your-name                   "Il tuo nome"
    :your-email                  "Il tuo indirizzo email"
    :email-placeholder           "Es toto@modernisation.gouv.fr"
    :your-affiliation            "La tua affiliazione"
    :affiliation-placeholder     "Es DGFiP"
    :your-message                "Messaggio"
    :message-placeholder         "Il tuo messaggio"
    :submit                      "Invia"
    :message-received            "Messaggio ricevuto!"
    :message-received-ok         "Faremo del nostro meglio per rispondere il prima possibile."
    :back-to-repos               "Ritorna alla lista dei repository di codice sorgente"
    :glossary                    "Glossario"
    :about                       "About"}})

(def opts {:dict localization})

(defn i [lang input] (tr opts [lang] input))
