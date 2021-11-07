;; Copyright (c) 2019-2021 DINUM, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.i18n
  (:require [taoensso.tempura :refer [tr]]))

(def supported-languages
  "A set of supported languages."
  #{"en" "fr" "it"})

(def localization
  ;; French translation
  {:fr
   {
    :all-forges                    "Toutes les forges"
    :Deps                          "Dépendances"
    :Repos                         "Dépôts"
    :Repos-deps-sim                "Dépôts aux dépendances similaires"
    :archive                       "SWH"
    :archive-on                    "Archive sur "
    :browse-repos-orga             "Voir la liste des dépôts de cette organisation ou de ce groupe"
    :contact                       "Contact"
    :created-at                    "Création"
    :dep                           " dépendance"
    :dep-of                        "dépendance identifiée pour"
    :deps                          " dépendances"
    :deps-expand                   "Naviguer dans les dépendances logicielles des dépôts"
    :deps-not-found                "Pas de dépendances identifiées."
    :deps-of                       "dépendances identifiées pour"
    :deps-stats                    "Dépendances identifiées"
    :description                   "Description"
    :distribution-by-platform      "Répartition par plateforme"
    :download                      "Télécharger"
    :forks                         "Forks"
    :free-search                   "Recherche libre"
    :glossary                      "Glossaire"
    :go-to-glossary                "Voir le glossaire"
    :go-to-orga                    "Visiter le compte d'organisation ou le groupe"
    :go-to-repo                    "Voir ce dépôt"
    :go-to-repos                   "Voir les dépôts"
    :go-to-website                 "Visiter le site web"
    :groups                        " groupes"
    :index-subtitle                "Ce site permet d'explorer les codes sources ouverts par des organismes publics."
    :index-title                   "Codes sources du secteur public"
    :issues                        "Tickets"
    :keywords                      "Accès aux codes sources du secteur public"
    :language                      "Langage"
    :languages                     "Langages"
    :last-repos                    "Derniers dépôts de codes sources publics"
    :license                       "Licence"
    :licenses                      "Licences"
    :list-repos-depending-on-dep   "Lister les dépôts qui dépendent de ce composant"
    :list-repos-using-license      "Lister les dépôts qui utilisent cette licence"
    :list-repos-with-language      "Lister les dépôts utilisant principalement ce langage"
    :matching                      " et contenant "
    :matching-s                    " et contenants "
    :mean-repos-by-orga            "Moyenne des dépôts par organisation"
    :median-repos-by-orga          "Médiane des dépôts par organisation"
    :more-info                     "Plus d'informations"
    :most-used-identified-licenses "Licences identifiées les plus utilisées"
    :most-used-languages           "Les 10 languages les plus utilisés"
    :most-used-licenses            "Licences les plus utilisées"
    :name                          "Nom"
    :no-archived-repos             "Ne pas inclure les dépôts archivés"
    :no-archives                   " Sauf archives"
    :no-dep-found                  "Pas de dépendance trouvée : une autre idée de requête ?"
    :no-orga-found                 "Pas d'organisation ou de groupe trouvé : une autre idée de requête ?"
    :no-repo-found                 "Pas de dépôt trouvé : une autre idée de requête ?"
    :number-of-repos               "Nombre de dépôts"
    :one-group                     " groupe"
    :no-forks                      "Pas de forks"
    :only-her                      "Que ESR"
    :only-with-license             "Avec licence"
    :orga-repo                     "Dépôt (groupe)"
    :orgas                         "Organisations"
    :orgas-or-groups               "Organisations ou groupes"
    :percent-of-repos-archived     "Proportion de dépôts archivés"
    :publication-policy            "Politique de publication des codes sources"
    :remove-filter                 "Supprimer le filtre"
    :repo                          " dépôt"
    :repo-archived                 "Ce dépôt est archivé"
    :repo-depending-on             " dépôt dépendant de "
    :report-new-source-code        "Signalez-nous vos ouvertures de codes sources"
    :repos                         " dépôts"
    :repos-number                  "Nombre de dépôts"
    :repos-of-source-code          "Dépôts de code source"
    :repos-on-swh                  "Dépôts dans Software Heritage"
    :reused                        "Réutil."
    :reuses                        "Réutilisations"
    :reuses-expand                 "Réutilisations dans d'autres dépôts ou paquetages"
    :sort                          "Trier"
    :sort-alpha                    "Par ordre alphabetique"
    :sort-creation                 "Par date de création"
    :sort-description-length       "Trier par longueur de description"
    :sort-development              "Trier par l'usage en développement"
    :sort-forks                    "Trier par nombre de fourches"
    :sort-issues                   "Trier par nombre de tickets"
    :sort-name                     "Trier par nom"
    :sort-orgas-alpha              "Trier par ordre alphabétique des noms d'organisations ou de groupes"
    :sort-orgas-creation           "Trier par date de création de l'organisation ou du groupe"
    :sort-production               "Trier par l'usage en production"
    :sort-repos                    "Par nombre de dépôts"
    :sort-repos-alpha              "Trier par ordre alphabétique des noms de dépôts"
    :sort-reused                   "Trier par nombre de dépôts et/ou paquetages réutilisateurs"
    :sort-stars                    "Trier par nombre d'étoiles"
    :sort-type                     "Trier par type"
    :sort-update-date              "Trier par date de mise à jour"
    :source-code                   "code source"
    :source-code-available         ", code source disponible "
    :Stars                         "Étoiles"
    :stars                         "étoiles"
    :stats                         "Chiffres"
    :stats-expand                  "Licences et langages les plus utilisées, etc."
    :submit                        "Envoyer"
    :subscribe-rss-flux            "S'abonner au flux RSS des derniers dépôts"
    :swh-link                      "Lien vers l'archive faite par Software Heritage"
    :type                          "Type"
    :under-license                 " sous licence "
    :understand-tech-terms         "Comprendre les termes techniques de ce site"
    :update                        "Mise à jour"
    :update-short                  "MàJ"
    :visit-on-github               "Visiter sur GitHub"
    :visit-on-gitlab               "Visiter sur l'instance GitLab"
    :with-description              " Avec description"
    :with-license                  " Avec une licence"
    :with-more-of                  " avec le plus de"
    :with-more-of*                 " avec le plus d'"
    }
   ;; English translation
   :en
   {
    :all-forges                    "All forges"
    :Deps                          "Dependencies"
    :Repos                         "Repositories"
    :Repos-deps-sim                "Repositories with similar dependencies"
    :archive                       "SWH"
    :archive-on                    "Archive on "
    :browse-repos-orga             "See the list of repositories from this organization or group"
    :contact                       "Contact"
    :created-at                    "Created"
    :dep                           " dependency"
    :dep-of                        "identified dependency for"
    :deps                          " dependencies"
    :deps-expand                   "Browse software dependencies of repositories"
    :deps-not-found                "No identified dependencies."
    :deps-of                       "identified dependencies for"
    :deps-stats                    "Identified dependencies"
    :description                   "Description"
    :distribution-by-platform      "Distribution per platform"
    :download                      "Download"
    :forks                         "Forks"
    :free-search                   "Free search"
    :glossary                      "Glossary"
    :go-to-glossary                "See the glossary"
    :go-to-orga                    "Visit the organization or group"
    :go-to-repo                    "See this repository"
    :go-to-repos                   "Go to the repositories"
    :go-to-website                 "Go to website"
    :groups                        " groups"
    :index-subtitle                "This website allows you to search through all french public sector published source code."
    :index-title                   "Browse french public sector source code"
    :issues                        "Issues"
    :keywords                      "List public sector source codes"
    :language                      "Language"
    :languages                     "Languages"
    :last-repos                    "Latest public source code repositories"
    :license                       "License"
    :licenses                      "Licenses"
    :list-repos-depending-on-dep   "List repositories depending on this"
    :list-repos-using-license      "List repositories published under this license"
    :list-repos-with-language      "List repositories mainly written in this language"
    :matching                      " and matching "
    :matching-s                    " and matching "
    :mean-repos-by-orga            "Mean number of repositories by organizations"
    :median-repos-by-orga          "Median number of repositories by orgnizations"
    :more-info                     "More information"
    :most-used-identified-licenses "Most used identified licenses"
    :most-used-languages           "Top 10 most used languages"
    :most-used-licenses            "Most used licenses"
    :name                          "Name"
    :no-archived-repos             "Do not include archived repositories"
    :no-archives                   " Hide archives"
    :no-dep-found                  "No dependency found: would you like to make a new request?"
    :no-orga-found                 "Organization or group not found: would you like to make a new request?"
    :no-repo-found                 "Repository not found: would you like to make a new request?"
    :number-of-repos               "Number of repositories"
    :one-group                     " group"
    :no-forks                      "No fork"
    :only-her                      "Only HER"
    :only-with-license             "Only repositories with a known license"
    :orga-repo                     "Repository (organization)"
    :orgas                         "Organizations"
    :orgas-or-groups               "Organizations or groups"
    :percent-of-repos-archived     "Percent of archived repositories"
    :publication-policy            "Source code publication policy"
    :remove-filter                 "Remove filter"
    :repo                          " repository"
    :repo-archived                 "This is an archived repository"
    :repo-depending-on             " repository depending on "
    :report-new-source-code        "Let us know when you open a source code"
    :repos                         " repositories"
    :repos-number                  "Number of repositories"
    :repos-of-source-code          "Source code repositories"
    :repos-on-swh                  "Repositories in Software Heritage"
    :reused                        "Reused"
    :reuses                        "Reuses"
    :reuses-expand                 "Reuses in other repositories or packages"
    :sort                          "Sort"
    :sort-alpha                    "By alphabetical order"
    :sort-creation                 "By creation date"
    :sort-description-length       "Sort by description length"
    :sort-forks                    "Sort by number of forks"
    :sort-issues                   "Sort by number of issues"
    :sort-orgas-alpha              "Sort by alphabetical order of organizations or groups"
    :sort-orgas-creation           "Sort by creation date of the organization or group"
    :sort-repos                    "By number of repositories"
    :sort-repos-alpha              "Sort repositories alphabetically"
    :sort-reused                   "Sort by number of reuses in other repositories and/or packages"
    :sort-stars                    "Sort by number of stars"
    :sort-update-date              "Sort by update date"
    :source-code                   "source code"
    :source-code-available         ", the source code is available "
    :Stars                         "Stars"
    :stars                         "stars"
    :stats                         "Figures"
    :stats-expand                  "Most used Licenses and languages, etc."
    :submit                        "Send"
    :subscribe-rss-flux            "Subscribe to our RSS feed to receive information about the latest repositories!"
    :swh-link                      "A link to the Software Heritage archive"
    :type                          "Type"
    :under-license                 " licensed "
    :understand-tech-terms         "A glossary to understand the technical terms used on this website"
    :update                        "Update"
    :update-short                  "Updated"
    :visit-on-github               "Browse on GitHub"
    :visit-on-gitlab               "Browse on the GitLab instance"
    :with-description              " With a description"
    :with-license                  " With known license"
    :with-more-of                  " with the most"
    :with-more-of*                 " with the most "
    }
   ;; Italian translation
   :it
   {
    :all-forges                    "All forges"                   ;; FIXME
    :Deps                          "Dipendenze"
    :Repos                         "Repositories"
    :Repos-deps-sim                "Repositories  con dipendenze simili"
    :archive                       "SWH"
    :archive-on                    "Archiviato su "
    :browse-repos-orga             "Vedere la lista di repository di questa organizazzione o di questo gruppo"
    :contact                       "Contatti"
    :created-at                    "Creato"
    :dep                           " dependency"                  ;; TODO
    :dep-of                        "identified dependency for"    ;; TODO
    :deps                          " dependencies"                ;; TODO
    :deps-expand                   "Sfoglia le dipendenze software dei repository"
    :deps-not-found                "No identified dependencies."  ;; TODO
    :deps-of                       "Identified dependencies for " ;; TODO
    :deps-stats                    "Identified dependencies"      ;; TODO
    :description                   "Descrizione"
    :distribution-by-platform      "Distribuzione per piattaforma"
    :download                      "Scaricare"
    :forks                         "Fork"
    :free-search                   "Ricerca libera"
    :glossary                      "Glossario"
    :go-to-glossary                "Vedi il glossario"
    :go-to-orga                    "Visita la pagina dell'organizzazione o del gruppo"
    :go-to-repo                    "Vedere questo repository"
    :go-to-repos                   "Vedi i reposutory"
    :go-to-website                 "Visita il sito web"
    :groups                        " grouppi"
    :index-subtitle                "Questo sito permette di navigare in alcuni codici sorgenti aperti degli enti pubblici."
    :index-title                   "Codici sorgenti del settore pubblico"
    :issues                        "Issue"
    :keywords                      "Accesso ai codici sorgenti del settore pubblico"
    :language                      "Lingua"
    :languages                     "Linguaggi"
    :last-repos                    "Ultimi repository di codice sorgente pubblico"
    :license                       "Licenza"
    :licenses                      "Licenze"
    :list-repos-depending-on-dep   "Elenca i repository in base a questo"
    :list-repos-using-license      "Elenca i repository pubblicati con questa licenza"
    :list-repos-with-language      "Elenca i repository scritti principalmente in questa lingua"
    :matching                      " e contenente "
    :matching-s                    " e contenente "
    :mean-repos-by-orga            "Numero medio di repository per organizzazione/gruppo"
    :median-repos-by-orga          "Numero mediano di repository per organizzazione/gruppo"
    :more-info                     "Maggiori informazioni"
    :most-used-identified-licenses "Licenze identificate più utilizzate"
    :most-used-languages           "Le 10 lingue più utilizzate"
    :most-used-licenses            "Licenze più utilizzate"
    :name                          "Name"                         ;; TODO
    :no-archived-repos             "Non includere i repository archiviati"
    :no-archives                   " Rimuovi archivi"
    :no-dep-found                  "Nessuna dipendenza trovata: volete fare una nuova richiesta?"
    :no-orga-found                 "Nessuna organizzazione o gruppo trovato: vuoi provare con un'altra richiesta?"
    :no-repo-found                 "Repository non trovato: vuoi provre a fare una nuova ricerca?"
    :number-of-repos               "Number of repositories"       ;; TODO
    :one-group                     " gruppo"
    :one-repo                      " repository"
    :no-forks                      "No fork"
    :only-her                      "Solo HER"
    :only-with-license             "Repository con una licenza identificata"
    :orga-repo                     "Repository (organizzazione)"
    :orgas                         "Organizzazioni"
    :orgas-or-groups               "Organizzazioni o gruppi"
    :percent-of-repos-archived     "Proporzione dei repository archiviati"
    :publication-policy            "Politica di pubblicazione del codice sorgente"
    :remove-filter                 "Rimuovi i filtri"
    :repo                          " repository"
    :repo-archived                 "Questo è un repository archiviato"
    :repo-depending-on             " repository dipendente da "
    :report-new-source-code        "Facci sapere quando hai rilascito del codice sorgente"
    :repos                         " repositories"
    :repos-number                  "Numero di repository"
    :repos-of-source-code          "Repository del codice sorgente"
    :repos-on-swh                  "Repository di Software Heritage"
    :reused                        "Utilizzi"
    :reuses                        "Riutilizzi"
    :reuses-expand                 "Riutilizzo in altri repository o pacchetti"
    :sort                          "Ordina"
    :sort-alpha                    "Ordine alfabetico"
    :sort-creation                 "Per data di creazione"
    :sort-description-length       "Ordina per lunghezza della descrizione"
    :sort-forks                    "Ordina per numero di fork"
    :sort-issues                   "Ordina per numero di issue"
    :sort-orgas-alpha              "Ordinare per ordine alfabetico del nome dell'organizzazione o del gruppo"
    :sort-orgas-creation           "Ordinare per data di creazione dell'organizzazione o del gruppo"
    :sort-repos                    "Per numero di repository"
    :sort-repos-alpha              "Ordina alfabeticamente i repository"
    :sort-reused                   "Ordina per numero di utilizzo in altri repository e / o pacchetti"
    :sort-stars                    "Ordina per numero di stelle"
    :sort-update-date              "Ordina per data di aggiornamento"
    :source-code                   "codide sorgente"
    :source-code-available         ", il codice sorgente è disponibile "
    :Stars                         "Stelle"
    :stars                         "stelle"
    :stats                         "Statistiche"
    :stats-expand                  "Licenze e lingue più utilizzate, ecc."
    :submit                        "Invia"
    :subscribe-rss-flux            "Sottoscrivi il nostro feed RSS per ricevere informazioni sugli ultimi repository pubblicati!"
    :swh-link                      "Link all'archivio fatto per il Software Heritage"
    :type                          "Type"                         ;; TODO
    :under-license                 " licenza "
    :understand-tech-terms         "Glossario per comprendere i termini tecnici usati in questo sito web"
    :update                        "Aggiorna"
    :update-short                  "Agg."
    :visit-on-github               "Visita su GitHub"
    :visit-on-gitlab               "Visite su GitLab"
    :with-description              " Con una descrizione"
    :with-license                  " Con una licenza"
    :with-more-of                  " con più di"
    :with-more-of*                 " con più di "
    }})

(def opts {:dict localization})

(defn i
  "Main i18n fonction."
  [lang input]
  (tr opts [lang] input))
