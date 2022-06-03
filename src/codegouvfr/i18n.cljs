;; Copyright (c) 2019-2022 DINUM, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.i18n
  (:require [taoensso.tempura :refer [tr]]))

(def supported-languages
  "A set of supported languages."
  #{"en" "fr"})

(def localization
  ;; French translation
  {:fr
   {
    :About                         "À propos"
    :Deps                          "Dépendances"
    :Libraries                     "Bibliothèques"
    :Library                       "Bibliothèque"
    :Papillon                      "Services"
    :Repos                         "Dépôts"
    :Repos-deps-sim                "Dépôts aux dépendances similaires"
    :Sill                          "SILL"
    :Stars                         "Étoiles"
    :Stats                         "Chiffres"
    :accessibility                 "Accessibilité : totalement conforme"
    :added                         "Ajouté"
    :all-dep-types                 "Tous les types de dépendances"
    :all-forges                    "Toutes les forges"
    :all-lib-types                 "Tous les types de bibliothèques"
    :all-ministries                "Tous les ministères et institutions de rattachement"
    :archive                       "SWH"
    :archive-on                    "Archive sur "
    :bluehats                      "Gazette BlueHats 🧢"
    :bluehats-desc                 "Votre lettre d'information sur les logiciels libres par et pour les administrations."
    :browse-repos-orga             "Voir la liste des dépôts de cette organisation ou de ce groupe"
    :cdl-visit                     "Voir la fiche du logiciel %s dans le Comptoir du Libre (ADULLACT)"
    :close                         "Fermer"
    :contact                       "Contact"
    :contact-title                 "Vous pouvez nous contacter en utilisant l'adresse électronique logiciels-libres@data.gouv.fr"
    :created-at                    "Création"
    :dep                           " dépendance"
    :deps                          " dépendances"
    :deps-stats                    "Dépendances identifiées dans tous les dépôts"
    :description                   "Description"
    :details                       "Fiche"
    :download                      "Télécharger"
    :find-us                       "Retrouvez-nous"
    :floss-policy                  "Politique logiciels libres"
    :footer-desc                   "Ce site est géré par "
    :footer-desc-link              "le pôle logiciels libres d'Etalab."
    :forge                         "Forge"
    :forks                         "Forks"
    :free-search                   "Recherche libre"
    :glossary                      "Glossaire"
    :go-to-orga                    "Visiter le compte d'organisation ou le groupe"
    :go-to-repo                    "Voir ce dépôt"
    :go-to-repos                   "Voir les dépôts"
    :go-to-website                 "Visiter le site web"
    :groups                        " groupes"
    :home                          "Accueil"
    :home-about-desc               "Pourquoi ce site ?  Comment est-il fabriqué ?  Où télécharger les données ?  Comment contribuer ?"
    :home-deps-desc                "Quelles sont les bibliothèques de code les plus utilisées ?"
    :home-groups-desc              "Quelles sont les organisations et groupes qui publient des codes sources ?"
    :home-libs-desc                "Quelles sont les bibliothèques de code publiées à partir des organisations ou groupes référencés sur ce site ?"
    :home-papillon-desc            "Quels services en ligne du secteur public reposent sur un logiciel libre ?"
    :home-repos-desc               "Parcourez la liste des dépôts de code source publiés par des organismes publics pour les réutiliser ou y contribuer."
    :home-sill-desc                "Explorez la liste des logiciels libres en usage dans l'administration, recommandés à toutes, et pour lesquels vous pouvez solliciter de l'aide."
    :home-stats-desc               "Quels sont les langages de programmation et les licences les plus utilisés ?"
    :index-title                   "Les codes sources du secteur public"
    :issues                        "Tickets"
    :language                      "Langage"
    :legal                         "Mentions légales"
    :lib                           " bibliothèque"
    :lib-type                      "Type"
    :library                       "Bibliothèque"
    :libs                          " bibliothèques"
    :libs-stats                    "Bibliothèques publiées à partir des dépôts"
    :license                       "Licence"
    :list-repos-depending-on-dep   "Lister les dépôts qui dépendent de ce composant"
    :mastodon-follow               "Suivez-nous sur Mastodon"
    :mean-repos-by-orga            "Moyenne des dépôts par organisation"
    :median-repos-by-orga          "Médiane des dépôts par organisation"
    :ministry                      "Ministère"
    :modal-close                   "Fermer"
    :modal-select-theme            "Choisissez un thème pour personnaliser l’apparence du site."
    :modal-theme-dark              "Thème sombre"
    :modal-theme-light             "Thème clair"
    :modal-theme-system            "Système"
    :modal-title                   "Paramètres d’affichage"
    :more-info                     "Plus d'informations"
    :most-used-identified-licenses "Licences identifiées les plus utilisées"
    :most-used-languages           "Langages de programmation les plus utilisés"
    :most-used-licenses            "Licences les plus utilisées"
    :name                          "Nom"
    :new-modal                     "S'ouvre dans une fenêtre modale"
    :new-tab                       "Ouvre un nouvel onglet"
    :no-dep-found                  "Pas de dépendance trouvée : une autre idée de requête ?"
    :no-lib-found                  "Bibliothèque non trouvée : une autre requête ?"
    :no-orga-found                 "Pas d'organisation ou de groupe trouvé : une autre idée de requête ?"
    :no-repo-found                 "Pas de dépôt trouvé : une autre idée de requête ?"
    :number-of-repos               "Nombre de dépôts"
    :occurrences                   "Nombre"
    :one-group                     " groupe"
    :only-contrib                  "Contrib."
    :only-contrib-title            "Que les dépôts avec CONTRIBUTING.md"
    :only-fork                     "Forks"
    :only-fork-title               "N'afficher que les dépôts qui sont des forks d'autres dépôts"
    :only-her                      "ESR"
    :only-her-title                "Ne voir que les dépôts de l'enseignement supérieur et de la recherche"
    :only-lib                      "Bibli."
    :only-lib-title                "Ne voir que les dépôts de bibliothèques"
    :only-with-license             "FLOSS"
    :only-with-license-title       "Dépôts avec une licence libre identifiée"
    :orga-repo                     "Dépôt (groupe)"
    :orgas                         "Organisations"
    :orgas-or-groups               "Organisations ou groupes"
    :papillon                      " services en ligne instanciant un logiciel libre"
    :papillon-agency               "Opérateur"
    :papillon-stats                "Services en ligne instanciant un logiciel libre"
    :papillon-title                "Services en ligne instanciant un logiciel libre"
    :papillon0                     " service s'appuyant sur un logiciel libre"
    :personal-data                 "Données personnelles et cookies"
    :providers                     "Prestataires"
    :providers-adullact            "Voir les prestataires via l'ADULLACT"
    :remove-filter                 "Supprimer le filtre"
    :repo                          " dépôt"
    :repo-archived                 "Ce dépôt est archivé"
    :repos                         " dépôts"
    :repos-number                  "Nombre de dépôts"
    :repos-of-source-code          "Dépôts de code source"
    :repos-on-swh                  "Dépôts dans Software Heritage"
    :reused                        "Réutil."
    :reuses-expand                 "Réutilisations dans d'autres dépôts ou paquetages"
    :software                      "Logiciel"
    :rss-feed                      "Flux RSS"
    :service                       "Service"
    :service-visit                 "Aller à l'accueil du service"
    :sill                          " logiciels libres recommandés"
    :sill-stats                    "Logiciels libres recommandés"
    :sill-visit                    "Voir la fiche du logiciel %s dans le SILL"
    :sill0                         " logiciel libre recommandé"
    :sitemap                       "Pages du site"
    :sort                          "Trier"
    :sort-description-length       "Trier par longueur de description"
    :sort-forks                    "Trier par nombre de fourches"
    :sort-libs-alpha               "Trier par ordre alphabétique des noms de bibliothèques"
    :sort-name                     "Trier par nom"
    :sort-orgas-alpha              "Trier par ordre alphabétique des noms d'organisations ou de groupes"
    :sort-orgas-creation           "Trier par date de création de l'organisation ou du groupe"
    :sort-repos                    "Par nombre de dépôts"
    :sort-repos-alpha              "Trier par ordre alphabétique des noms de dépôts"
    :sort-reused                   "Trier par nombre de dépôts et/ou paquetages réutilisateurs"
    :sort-sill-alpha               "Trier par ordre alphabétique des noms de logiciels"
    :sort-sill-date                "Trier par date d'ajout dans le SILL"
    :sort-stars                    "Trier par nombre d'étoiles"
    :sort-type                     "Trier par type"
    :sort-update-date              "Trier par date de mise à jour"
    :sourcehut-link                "Retrouvez nos codes sources sur SourceHut"
    :stars                         "étoiles"
    :stats-expand                  "Quelques statistiques sur les langages, les licences, etc."
    :subscribe                     "Abonnez-vous"
    :subscribe-rss-flux            "S'abonner au flux RSS des derniers dépôts"
    :support                       "Présent dans le marché de support interministériel logiciels libres"
    :swh-link                      "Lien vers l'archive faite par Software Heritage"
    :switch-lang                   "Switch to english"
    :top-forges                    "Forges avec le plus de dépôts"
    :top-ministries                "Ministères avec le plus de dépôts"
    :topics                        "Mots-clefs les plus utilisés"
    :twitter-follow                "Suivez-nous sur Twitter"
    :type                          "Type"
    :under-license                 " sous licence "
    :understand-tech-terms         "Comprendre les termes techniques de ce site"
    :update-short                  "MàJ"
    :with-more-of                  " avec le plus de"
    :with-more-of*                 " avec le plus d'"
    :workshop                      "Atelier"
    }
   ;; English translation
   :en
   {
    :About                         "About"
    :Deps                          "Dependencies"
    :Libraries                     "Libraries"
    :Library                       "Library"
    :Papillon                      "Instances"
    :Repos                         "Repositories"
    :Repos-deps-sim                "Repositories with similar dependencies"
    :Sill                          "Recommended free software"
    :Stars                         "Stars"
    :Stats                         "Stats"
    :accessibility                 "Accessibility: conform"
    :added                         "Added"
    :all-dep-types                 "All dependency types"
    :all-forges                    "All forges"
    :all-lib-types                 "All library types"
    :all-ministries                "All ministries"
    :archive                       "SWH"
    :archive-on                    "Archive on "
    :bluehats                      "BlueHats 🧢 newsletter"
    :bluehats-desc                 "French news about free software by and for the public sector."
    :browse-repos-orga             "See the list of repositories from this organization or group"
    :cdl-visit                     "See %s in Comptoir du Libre (ADULLACT)"
    :close                         "Close"
    :contact                       "Contact"
    :contact-title                 "You can reach us by email at logiciels-libres@data.gouv.fr"
    :created-at                    "Created"
    :dep                           " dependency"
    :deps                          " dependencies"
    :deps-stats                    "Identified dependencies in all repositories"
    :description                   "Description"
    :details                       "Details"
    :download                      "Download"
    :find-us                       "Follow us"
    :floss-policy                  "Free Software policy"
    :footer-desc                   "This website is maintained by "
    :footer-desc-link              "the free software pole at Etalab."
    :forge                         "Forge"
    :forks                         "Forks"
    :free-search                   "Free-form search"
    :glossary                      "Glossary"
    :go-to-orga                    "Visit the organization or group"
    :go-to-repo                    "See this repository"
    :go-to-repos                   "Go to the repositories"
    :go-to-website                 "Go to website"
    :groups                        " groups"
    :home                          "Home"
    :home-about-desc               "Why this website?  How is it built?  Where are the data?  How to contribute?"
    :home-deps-desc                "What libraries are frequently used by all the repositories?"
    :home-groups-desc              "What organizations are publishing source code"
    :home-libs-desc                "What libraries are published from groups and organizations referenced on this website?"
    :home-papillon-desc            "What public sector online services are based on free software?"
    :home-repos-desc               "Browse the list of code source repositories opened by public agencies in order to reuse them or to contribute to them."
    :home-sill-desc                "Explore the list of recommended free software for the public sector."
    :home-stats-desc               "What programming languages and what licenses are most frequently used?"
    :index-title                   "Browse French public sector source code"
    :issues                        "Issues"
    :language                      "Language"
    :legal                         "Legal mentions"
    :lib                           " library"
    :lib-type                      "Type"
    :library                       "Library"
    :libs                          " libraries"
    :libs-stats                    "Libraries published from the repositories"
    :license                       "License"
    :list-repos-depending-on-dep   "List repositories depending on this"
    :mastodon-follow               "Follow us on Mastodon"
    :mean-repos-by-orga            "Mean number of repositories by organizations"
    :median-repos-by-orga          "Median number of repositories by orgnizations"
    :ministry                      "Ministry"
    :modal-close                   "Close"
    :modal-select-theme            "Select a theme to customize the website appearance."
    :modal-theme-dark              "Dark theme"
    :modal-theme-light             "Light theme"
    :modal-theme-system            "System"
    :modal-title                   "Display parameters"
    :more-info                     "More information"
    :most-used-identified-licenses "Most used identified licenses"
    :most-used-languages           "Top 10 most used languages"
    :most-used-licenses            "Most used licenses"
    :name                          "Name"
    :new-modal                     "Open as a modal window"
    :new-tab                       "Open a new tab"
    :no-dep-found                  "No dependency found: would you like to make a new request?"
    :no-lib-found                  "Library not found: would you like to make a new request?"
    :no-orga-found                 "Organization or group not found: would you like to make a new request?"
    :no-repo-found                 "Repository not found: would you like to make a new request?"
    :number-of-repos               "Number of repositories"
    :occurrences                   "Number"
    :one-group                     " group"
    :only-contrib                  "Contrib."
    :only-contrib-title            "Only repositories with CONTRIBUTING.md"
    :only-fork                     "Only forks"
    :only-fork-title               "Only display repositories if they are forks from other repositories"
    :only-her                      "Only HER"
    :only-her-title                "Only repositories from higher education and research"
    :only-lib                      "Libraries"
    :only-lib-title                "Only repositories for libraries"
    :only-with-license             "FLOSS"
    :only-with-license-title       "Repositories with a Free Software license"
    :orga-repo                     "Repository (organization)"
    :orgas                         "Organizations"
    :orgas-or-groups               "Organizations or groups"
    :papillon                      " services based on a free software"
    :papillon-agency               "Agency"
    :papillon-stats                "Online services running a free software"
    :papillon-title                "Online services based on free software"
    :papillon0                     " service based on a free software"
    :personal-data                 "Personal data and cookies"
    :providers                     "Providers"
    :providers-adullact            "List providers via ADULLACT"
    :remove-filter                 "Remove filter"
    :repo                          " repository"
    :repo-archived                 "This is an archived repository"
    :repos                         " repositories"
    :repos-number                  "Number of repositories"
    :repos-of-source-code          "Source code repositories"
    :repos-on-swh                  "Repositories in Software Heritage"
    :reused                        "Reused"
    :software                      "Software"
    :reuses-expand                 "Reuses in other repositories or packages"
    :rss-feed                      "RSS feed"
    :service                       "Service"
    :service-visit                 "Go to the service page"
    :sill                          " free software"
    :sill-stats                    "Recommended free software"
    :sill-visit                    "See %s in the SILL"
    :sill0                         " free software"
    :sitemap                       "Sitemap"
    :sort                          "Sort"
    :sort-description-length       "Sort by description length"
    :sort-forks                    "Sort by number of forks"
    :sort-libs-alpha               "Sort libraries alphabetically"
    :sort-name                     "Sort by name"
    :sort-orgas-alpha              "Sort by alphabetical order of organizations or groups"
    :sort-orgas-creation           "Sort by creation date of the organization or group"
    :sort-repos                    "By number of repositories"
    :sort-repos-alpha              "Sort repositories alphabetically"
    :sort-reused                   "Sort by number of reuses in other repositories and/or packages"
    :sort-sill-alpha               "Sort software alphabetically"
    :sort-sill-date                "Sort by when the software have been added"
    :sort-stars                    "Sort by number of stars"
    :sort-type                     "Sort by type"
    :sort-update-date              "Sort by update date"
    :sourcehut-link                "Find our source code on SourceHut"
    :stars                         "stars"
    :stats-expand                  "Some stats on languages, licences, etc."
    :subscribe                     "Subscribe"
    :subscribe-rss-flux            "Subscribe to our RSS feed to receive information about the latest repositories!"
    :support                       "Listed in the interministerial free software support public marked"
    :swh-link                      "A link to the Software Heritage archive"
    :switch-lang                   "Ce site en français"
    :top-forges                    "Top forges"
    :top-ministries                "Top ministries"
    :topics                        "Most frequent topics"
    :twitter-follow                "Follow us on Twitter"
    :type                          "Type"
    :under-license                 " licensed "
    :understand-tech-terms         "A glossary to understand the technical terms used on this website"
    :update-short                  "Updated"
    :with-more-of                  " with the most"
    :with-more-of*                 " with the most "
    :workshop                      "Workshop"
    }})

(def opts {:dict localization})

(defn i
  "Main i18n fonction."
  [lang input]
  (tr opts [lang] input))
