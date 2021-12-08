;; Copyright (c) 2019-2021 DINUM, Bastien Guerry <bastien.guerry@data.gouv.fr>
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
    :Repos                         "Dépôts"
    :Repos-deps-sim                "Dépôts aux dépendances similaires"
    :Stars                         "Étoiles"
    :Stats                         "Chiffres"
    :accessibility                 "Accessibilité : partiellement conforme"
    :all-forges                    "Toutes les forges"
    :archive                       "SWH"
    :archive-on                    "Archive sur "
    :back-to-homepage              "Retour à l'accueil"
    :bluehats                      "Gazette BlueHats 🧢"
    :bluehats-desc                 "Votre lettre d'information sur les logiciels libres par et pour les administrations."
    :browse-repos-orga             "Voir la liste des dépôts de cette organisation ou de ce groupe"
    :close                         "Fermer"
    :contact                       "Contact"
    :contact-title                 "Vous pouvez nous contacter en utilisant l'adresse électronique :<br>[logiciels-libres@data.gouv.fr](mailto:logiciels-libres@data.gouv.fr \"Suivez ce lien pour nous envoyer un courriel\")"
    :created-at                    "Création"
    :dep                           " dépendance"
    :deps                          " dépendances"
    :deps-stats                    "Dépendances identifiées dans tous les dépôts"
    :description                   "Description"
    :distribution-by-platform      "Répartition par plateforme"
    :download                      "Télécharger"
    :find-us                       "Retrouvez-nous"
    :footer-desc                   "Ce site est géré par "
    :footer-desc-link              "le pôle logiciels libres d'Etalab."
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
    :home-repos-desc               "Parcourez la liste des dépôts de code source publiés par des organismes publics pour les réutiliser ou y contribuer."
    :home-stats-desc               "Quels sont les langages de programmation et les licences les plus utilisés ?"
    :index-subtitle                "Ce site permet d'explorer les codes sources publiés par des organismes publics."
    :index-title                   "Codes sources du secteur public"
    :issues                        "Tickets"
    :language                      "Langage"
    :legal                         "Mentions légales"
    :license                       "Licence"
    :list-repos-depending-on-dep   "Lister les dépôts qui dépendent de ce composant"
    :list-repos-using-license      "Lister les dépôts qui utilisent cette licence"
    :list-repos-with-language      "Lister les dépôts utilisant principalement ce langage"
    :mean-repos-by-orga            "Moyenne des dépôts par organisation"
    :median-repos-by-orga          "Médiane des dépôts par organisation"
    :modal-close                   "Fermer"
    :modal-title                   "Paramètres d’affichage"
    :modal-select-theme            "Choisissez un thème pour personnaliser l’apparence du site."
    :modal-theme-light             "Thème clair"
    :modal-theme-dark              "Thème sombre"
    :modal-theme-system            "Système"
    :more-info                     "Plus d'informations"
    :most-used-identified-licenses "Licences identifiées les plus utilisées"
    :most-used-languages           "Les 10 languages les plus utilisés"
    :most-used-licenses            "Licences les plus utilisées"
    :name                          "Nom"
    :new-modal                     "S'ouvre dans une fenêtre modale"
    :new-tab                       "Ouvre un nouvel onglet"
    :no-dep-found                  "Pas de dépendance trouvée : une autre idée de requête ?"
    :no-orga-found                 "Pas d'organisation ou de groupe trouvé : une autre idée de requête ?"
    :no-repo-found                 "Pas de dépôt trouvé : une autre idée de requête ?"
    :nothing-here                  "Il n'y a rien ici."
    :number-of-repos               "Nombre de dépôts"
    :one-group                     " groupe"
    :only-her                      "Que ESR"
    :only-her-title                "Ne voir que les dépôts de l'enseignement supérieur et de la recherche"
    :only-not-fork                 "Pas de fork"
    :only-not-fork-title           "Ne pas afficher les dépôts s'ils sont des forks d'autres dépôts"
    :only-with-license             "Avec licence"
    :orga-repo                     "Dépôt (groupe)"
    :orgas                         "Organisations"
    :orgas-or-groups               "Organisations ou groupes"
    :personal-data                 "Données personnelles et cookies"
    :remove-filter                 "Supprimer le filtre"
    :repo                          " dépôt"
    :repo-archived                 "Ce dépôt est archivé"
    :repos                         " dépôts"
    :repos-number                  "Nombre de dépôts"
    :repos-of-source-code          "Dépôts de code source"
    :repos-on-swh                  "Dépôts dans Software Heritage"
    :reused                        "Réutil."
    :reuses-expand                 "Réutilisations dans d'autres dépôts ou paquetages"
    :rss-feed                      "Flux RSS"
    :sitemap                       "Pages du site"
    :sourcehut-link                "Retrouvez nos codes sources sur SourceHut"
    :sorry                         "Désolé !"
    :sort                          "Trier"
    :sort-description-length       "Trier par longueur de description"
    :sort-forks                    "Trier par nombre de fourches"
    :sort-name                     "Trier par nom"
    :sort-orgas-alpha              "Trier par ordre alphabétique des noms d'organisations ou de groupes"
    :sort-orgas-creation           "Trier par date de création de l'organisation ou du groupe"
    :sort-repos                    "Par nombre de dépôts"
    :sort-repos-alpha              "Trier par ordre alphabétique des noms de dépôts"
    :sort-reused                   "Trier par nombre de dépôts et/ou paquetages réutilisateurs"
    :sort-stars                    "Trier par nombre d'étoiles"
    :sort-type                     "Trier par type"
    :sort-update-date              "Trier par date de mise à jour"
    :stars                         "étoiles"
    :stats-expand                  "Quelques statistiques sur les langages, les licences, etc."
    :subscribe                     "Abonnez-vous"
    :subscribe-rss-flux            "S'abonner au flux RSS des derniers dépôts"
    :swh-link                      "Lien vers l'archive faite par Software Heritage"
    :switch-lang                   "Switch to english"
    :twitter-follow                "Suivez-nous sur Twitter"
    :type                          "Type"
    :under-license                 " sous licence "
    :understand-tech-terms         "Comprendre les termes techniques de ce site"
    :update-short                  "MàJ"
    :with-more-of                  " avec le plus de"
    :with-more-of*                 " avec le plus d'"
    }
   ;; English translation
   :en
   {
    :About                         "About"
    :Deps                          "Dependencies"
    :Repos                         "Repositories"
    :Repos-deps-sim                "Repositories with similar dependencies"
    :Stars                         "Stars"
    :Stats                         "Overview"
    :accessibility                 "Accessibility: partially conform"
    :all-forges                    "All forges"
    :archive                       "SWH"
    :archive-on                    "Archive on "
    :back-to-homepage              "Back to homepage"
    :bluehats                      "BlueHats 🧢 newsletter"
    :bluehats-desc                 "French news about free software by and for the public sector."
    :browse-repos-orga             "See the list of repositories from this organization or group"
    :close                         "Close"
    :contact                       "Contact"
    :contact-title                 "You can reach us by email:<br>[logiciels-libres@data.gouv.fr](mailto:logiciels-libres@data.gouv.fr \"Follow this link to send us an email\")"
    :created-at                    "Created"
    :dep                           " dependency"
    :deps                          " dependencies"
    :deps-stats                    "Identified dependencies in all repositories"
    :description                   "Description"
    :distribution-by-platform      "Distribution per platform"
    :download                      "Download"
    :find-us                       "Follow us"
    :footer-desc                   "This website is maintained by "
    :footer-desc-link              "the free software pole at Etalab."
    :forks                         "Forks"
    :free-search                   "Free search"
    :glossary                      "Glossary"
    :go-to-orga                    "Visit the organization or group"
    :go-to-repo                    "See this repository"
    :go-to-repos                   "Go to the repositories"
    :go-to-website                 "Go to website"
    :groups                        " groups"
    :home                          "Home"
    :home-about-desc               "Why this website?  How is it built?  Where are the data?  How to contribute?"
    :home-deps-desc                "What libraries are frequently used by all the repositories?"
    :home-repos-desc               "Browse the list of code source repositories opened by public agencies in order to reuse them or to contribute to them."
    :home-stats-desc               "What programming languages and what licenses are most frequently used?"
    :index-subtitle                "This website allows you to search through all French public sector published source code."
    :index-title                   "Browse French public sector source code"
    :issues                        "Issues"
    :language                      "Language"
    :legal                         "Legal mentions"
    :license                       "License"
    :list-repos-depending-on-dep   "List repositories depending on this"
    :list-repos-using-license      "List repositories published under this license"
    :list-repos-with-language      "List repositories mainly written in this language"
    :mean-repos-by-orga            "Mean number of repositories by organizations"
    :median-repos-by-orga          "Median number of repositories by orgnizations"
    :modal-close                   "Close"
    :modal-title                   "Display parameters"
    :modal-select-theme            "Select a theme to customize the website appearance."
    :modal-theme-light             "Light theme"
    :modal-theme-dark              "Dark theme"
    :modal-theme-system            "System"
    :more-info                     "More information"
    :most-used-identified-licenses "Most used identified licenses"
    :most-used-languages           "Top 10 most used languages"
    :most-used-licenses            "Most used licenses"
    :name                          "Name"
    :new-modal                     "Open as a modal window"
    :new-tab                       "Open a new tab"
    :no-dep-found                  "No dependency found: would you like to make a new request?"
    :no-orga-found                 "Organization or group not found: would you like to make a new request?"
    :no-repo-found                 "Repository not found: would you like to make a new request?"
    :nothing-here                  "Nothing to see here"
    :number-of-repos               "Number of repositories"
    :one-group                     " group"
    :only-her                      "Only HER"
    :only-her-title                "Only repositories from higher education and research"
    :only-not-fork                 "No forks"
    :only-not-fork-title           "Do not display repositories if they are forks from other repositories"
    :only-with-license             "Only repositories with a known license"
    :orga-repo                     "Repository (organization)"
    :orgas                         "Organizations"
    :orgas-or-groups               "Organizations or groups"
    :personal-data                 "Personal data and cookies"
    :remove-filter                 "Remove filter"
    :repo                          " repository"
    :repo-archived                 "This is an archived repository"
    :repos                         " repositories"
    :repos-number                  "Number of repositories"
    :repos-of-source-code          "Source code repositories"
    :repos-on-swh                  "Repositories in Software Heritage"
    :reused                        "Reused"
    :reuses-expand                 "Reuses in other repositories or packages"
    :rss-feed                      "RSS feed"
    :sitemap                       "Sitemap"
    :sourcehut-link                "Find our source code on SourceHut"
    :sorry                         "Sorry!"
    :sort                          "Sort"
    :sort-description-length       "Sort by description length"
    :sort-forks                    "Sort by number of forks"
    :sort-name                     "Sort by name"
    :sort-orgas-alpha              "Sort by alphabetical order of organizations or groups"
    :sort-orgas-creation           "Sort by creation date of the organization or group"
    :sort-repos                    "By number of repositories"
    :sort-repos-alpha              "Sort repositories alphabetically"
    :sort-reused                   "Sort by number of reuses in other repositories and/or packages"
    :sort-stars                    "Sort by number of stars"
    :sort-type                     "Sort by type"
    :sort-update-date              "Sort by update date"
    :stars                         "stars"
    :stats-expand                  "Some stats on languages, licences, etc."
    :subscribe                     "Subscribe"
    :subscribe-rss-flux            "Subscribe to our RSS feed to receive information about the latest repositories!"
    :swh-link                      "A link to the Software Heritage archive"
    :switch-lang                   "Ce site en français"
    :twitter-follow                "Follow us on Twitter"
    :type                          "Type"
    :under-license                 " licensed "
    :understand-tech-terms         "A glossary to understand the technical terms used on this website"
    :update-short                  "Updated"
    :with-more-of                  " with the most"
    :with-more-of*                 " with the most "
    }})

(def opts {:dict localization})

(defn i
  "Main i18n fonction."
  [lang input]
  (tr opts [lang] input))
