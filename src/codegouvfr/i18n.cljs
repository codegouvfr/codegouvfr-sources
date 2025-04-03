;; Copyright (c) 2019-2025 DINUM, Bastien Guerry <bastien.guerry@code.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.i18n
  (:require [taoensso.tempura :refer [tr]]))

(def languages
  "A set of supported languages."
  #{"en" "fr"})

(def localization
  ;; French translation
  {:fr
   {
    :About                   "À propos"
    :Archived                "Archivé"
    :Awesome                 "Awesome code.gouv.fr"
    :Awesome-callout         "Logiciels libres remarquables activement développés et financés par des organismes publics "
    :Awesome-title           "Les projets open source remarquables de l'administration"
    :Criterium               "Critère"
    :Followers               "Abonnés"
    :Followers-total         "Nombre d'abonnés"
    :Following               "Abonnements"
    :Funders                 "Financements"
    :No                      "Non"
    :Open-issues             "Tickets ouverts"
    :Orga                    "Organisation"
    :Orgas                   "Organisations"
    :Releasename             "Nom de la version"
    :Releases                "Versions"
    :Repo                    "Dépôt"
    :Repos                   "Dépôts"
    :Score                   "Score"
    :Stars                   "Étoiles"
    :Stars-total             "Nombre d'étoiles"
    :Stats                   "Chiffres"
    :Subscribers             "Abonnés"
    :Template                "Modèle"
    :Users                   "Utilisateurs"
    :Value                   "Valeur"
    :Yes                     "Oui"
    :accessibility           "Accessibilité : partiellement conforme"
    :additional-info         "Informations supplémentaires"
    :all-forges              "Toutes les forges"
    :all-ministries          "Toutes les institutions"
    :bluehats                "Gazette BlueHats"
    :bluehats-desc           "Votre lettre d'information sur les logiciels libres par et pour les administrations."
    :browse-repos-orga       "Voir la liste des dépôts de cette organisation"
    :close                   "Fermer"
    :codegouvfr              "code.gouv.fr"
    :contact                 "Contact"
    :contact-title           "Vous pouvez nous contacter en utilisant l'adresse électronique floss@numerique.gouv.fr"
    :created-at              "Création"
    :description             "Description"
    :download                "Télécharger en .csv"
    :find-us                 "Retrouvez-nous"
    :floss                   "FLOSS"
    :floss-policy            "Politique logiciels libres"
    :footer-desc             "Ce site est géré par "
    :footer-desc-link        "la mission logiciels libres de la DINUM."
    :forge                   "Forge"
    :fork                    "Fork"
    :forks                   "Forks"
    :free-search             "Recherche libre"
    :go-to-data              "Voir les données détaillées"
    :go-to-orga              "Visiter le compte d'organisation"
    :go-to-repo              "Voir ce dépôt"
    :go-to-repos             "Voir les dépôts de cette organisation"
    :go-to-source            "Voir le code source"
    :go-to-website           "Visiter le site web"
    :home                    "Accueil"
    :home-orgas-desc         "Quelles sont les organisations qui publient des codes sources ?"
    :home-repos-desc         "Parcourez la liste des dépôts de code source publiés par des organismes publics pour les réutiliser ou y contribuer."
    :home-stats-desc         "Quels sont les langages de programmation et les licences les plus utilisés ?"
    :index-title             "Les codes sources du secteur public"
    :language                "Langage"
    :legal                   "Mentions légales"
    :license                 "Licence"
    :mastodon-follow         "Suivez-nous sur Mastodon"
    :ministry                "Ministère"
    :modal-close             "Fermer"
    :modal-select-theme      "Choisissez un thème pour personnaliser l’apparence du site."
    :modal-theme-dark        "Thème sombre"
    :modal-theme-light       "Thème clair"
    :modal-theme-system      "Système"
    :modal-title             "Paramètres d’affichage"
    :more-info               "Plus d'informations"
    :most-starred-orgas      "Comptes d'organisation avec le plus d'étoiles"
    :most-used-languages     "Langages de programmation"
    :most-used-licenses      "Licences"
    :name                    "Nom"
    :new-modal               "S'ouvre dans une fenêtre modale"
    :new-tab                 "Ouvre un nouvel onglet"
    :no-orga-found           "Pas d'organisation trouvée : une autre idée de requête ?"
    :no-repo-found           "Pas de dépôt trouvé : une autre idée de requête ?"
    :number-of-repos         "Nombre de dépôts"
    :only-contrib            "Contrib."
    :only-contrib-title      "Que les dépôts avec CONTRIBUTING.md"
    :only-fork               "Forks"
    :only-fork-title         "N'afficher que les dépôts qui sont des forks d'autres dépôts"
    :only-publiccode         "PublicCode"
    :only-publiccode-title   "Que les dépôts avec publiccode.yml"
    :only-template           "Modèle"
    :only-template-title     "Ne voir que les dépôts modèles"
    :only-with-license       "FLOSS"
    :only-with-license-title "Dépôts avec une licence libre identifiée"
    :orga                    " organisation"
    :orga-homepage           "Site web associé à cette organisation"
    :orgas                   " organisations"
    :personal-data           "Données personnelles et cookies"
    :primary-language        "Primary language"
    :release                 " version"
    :release-check-latest    "voir les dernières versions"
    :releases                " versions"
    :remove-filter           "Supprimer le filtre"
    :repo                    " dépôt"
    :repo-archived           "Ce dépôt est archivé"
    :repos                   " dépôts"
    :repos-number            "Nombre de dépôts"
    :repos-of-source-code    "Dépôts de code source"
    :repos-on-swh            "Dépôts dans Software Heritage"
    :repos-vs-followers      "Dépôts / Abonnés"
    :repos-vs-score          "Distribution des dépôts par score"
    :repos-vs-stars          "Dépôts / Étoiles"
    :rss-feed                "Flux RSS"
    :sitemap                 "Pages du site"
    :software                "Logiciel"
    :sorry-no-data-available "Désolé, les données ne sont pas disponibles."
    :sort                    "Trier"
    :sort-forks              "Trier par nombre de fourches"
    :sort-name               "Trier par nom"
    :sort-orgas-floss-policy "Trier en fonction de la présence d'une politique logiciels libres"
    :sort-repos              "Par nombre de dépôts"
    :sort-score              "Trier par score codegouvfr"
    :sort-subscribers        "Trier par nombre d'abonnés"
    :sort-update-date        "Trier par date de mise à jour"
    :sourcehut-link          "Retrouvez nos codes sources sur SourceHut"
    :stats-expand            "Quelques statistiques sur les langages, les licences, etc."
    :subscribe               "Abonnez-vous"
    :subscribe-rss-flux      "S'abonner au flux RSS des derniers dépôts"
    :support                 "Présent dans le marché de support interministériel logiciels libres"
    :swh-link                "Lien vers l'archive faite par Software Heritage"
    :switch-lang             "Switch to english"
    :technical-details       "Technical details"
    :title-default           "Codes sources du secteur public"
    :title-prefix            "code.gouv.fr - "
    :total-commits           "Total commits"
    :total-contributors      "Total contributors"
    :under-license           " sous licence "
    :update-short            "MàJ"
    :updated                 "Mise à jour"
    :website                 "Site web"
    :with-more-of            " avec le plus de"
    }
   ;; English translation
   :en
   {
    :About                   "About"
    :Archived                "Archived"
    :Awesome                 "Awesome code.gouv.fr"
    :Awesome-callout         "Awesome open source projects actively maintained and funded by public sector organizations"
    :Awesome-title           "Awesome open source projects from the French public sector"
    :Criterium               "Criterium"
    :Followers               "Followers"
    :Followers-total         "Total Followers"
    :Following               "Following"
    :Funders                 "Funding organisations"
    :No                      "No"
    :Open-issues             "Open issues"
    :Orga                    "Organization"
    :Orgas                   "Organizations"
    :Releasename             "Release name"
    :Releases                "Releases"
    :Repo                    "Repository"
    :Repos                   "Repositories"
    :Score                   "Score"
    :Stars                   "Stars"
    :Stars-total             "Total Stars"
    :Stats                   "Stats"
    :Subscribers             "Subscribers"
    :Template                "Template"
    :Users                   "Users"
    :Value                   "Value"
    :Yes                     "Yes"
    :accessibility           "Accessibility: partially conform"
    :additional-info         "Additional Infos"
    :all-forges              "All forges"
    :all-ministries          "All ministries"
    :bluehats                "BlueHats 🧢 newsletter"
    :bluehats-desc           "Newsletter in French about free software by and for the public sector."
    :browse-repos-orga       "See the list of repositories from this organization"
    :close                   "Close"
    :codegouvfr              "code.gouv.fr"
    :contact                 "Contact"
    :contact-title           "You can reach us by email at floss@numerique.gouv.fr"
    :created-at              "Created"
    :description             "Description"
    :download                "Download as .csv"
    :find-us                 "Follow us"
    :floss                   "FLOSS"
    :floss-policy            "Free Software policy"
    :footer-desc             "This website is maintained by "
    :footer-desc-link        "the free software unit at DINUM."
    :forge                   "Forge"
    :fork                    "Fork"
    :forks                   "Forks"
    :free-search             "Free-form search"
    :go-to-data              "Explore detailed data"
    :go-to-orga              "Visit the organization"
    :go-to-repo              "See this repository"
    :go-to-repos             "Go to the repositories"
    :go-to-source            "Browse the source code"
    :go-to-website           "Go to website"
    :home                    "Home"
    :home-orgas-desc         "What organizations are publishing source code"
    :home-repos-desc         "Browse the list of code source repositories opened by public agencies in order to reuse them or to contribute to them."
    :home-stats-desc         "What programming languages and what licenses are most frequently used?"
    :index-title             "Browse French public sector source code"
    :language                "Language"
    :legal                   "Legal mentions"
    :license                 "License"
    :mastodon-follow         "Follow us on Mastodon"
    :ministry                "Ministry"
    :modal-close             "Close"
    :modal-select-theme      "Select a theme to customize the website appearance."
    :modal-theme-dark        "Dark theme"
    :modal-theme-light       "Light theme"
    :modal-theme-system      "System"
    :modal-title             "Display parameters"
    :more-info               "More information"
    :most-starred-orgas      "Most starred organizations"
    :most-used-languages     "Most used programming languages"
    :most-used-licenses      "Most used licenses"
    :name                    "Name"
    :new-modal               "Open as a modal window"
    :new-tab                 "Open a new tab"
    :no-dep-found            "No dependency found: would you like to make a new request?"
    :no-orga-found           "Organization not found: would you like to make a new request?"
    :no-repo-found           "Repository not found: would you like to make a new request?"
    :number-of-repos         "Number of repositories"
    :only-contrib            "Contrib."
    :only-contrib-title      "Only repositories with CONTRIBUTING.md"
    :only-fork               "Forks"
    :only-fork-title         "Only display repositories if they are forks from other repositories"
    :only-publiccode         "PublicCode"
    :only-publiccode-title   "Only repositories with publiccode.yml"
    :only-template           "Templates"
    :only-template-title     "Only repositories for templates"
    :only-with-license       "FLOSS"
    :only-with-license-title "Repositories with a Free Software license"
    :orga                    " organization"
    :orga-homepage           "Website for this organization"
    :orgas                   " organizations"
    :personal-data           "Personal data and cookies"
    :primary-language        "Langage principal"
    :release                 " release"
    :release-check-latest    "check the latest releases"
    :releases                " releases"
    :remove-filter           "Remove filter"
    :repo                    " repository"
    :repo-archived           "This is an archived repository"
    :repos                   " repositories"
    :repos-number            "Number of repositories"
    :repos-of-source-code    "Source code repositories"
    :repos-on-swh            "Repositories in Software Heritage"
    :repos-vs-followers      "Repositories vs Followers"
    :repos-vs-score          "Repositories distribution by score"
    :repos-vs-stars          "Repositories vs Stars"
    :rss-feed                "RSS feed"
    :sitemap                 "Sitemap"
    :software                "Software"
    :sorry-no-data-available "Sorry, no data available."
    :sort                    "Sort"
    :sort-forks              "Sort by number of forks"
    :sort-name               "Sort by name"
    :sort-orgas-floss-policy "Sort by whether the organization has a Free Software policy"
    :sort-repos              "By number of repositories"
    :sort-score              "Sort by codegouvfr score"
    :sort-subscribers        "Sort by number of subscribers"
    :sort-update-date        "Sort by update date"
    :sourcehut-link          "Find our source code on SourceHut"
    :stats-expand            "Some stats on languages, licences, etc."
    :subscribe               "Subscribe"
    :subscribe-rss-flux      "Subscribe to our RSS feed to receive information about the latest repositories!"
    :swh-link                "A link to the Software Heritage archive"
    :switch-lang             "Ce site en français"
    :technical-details       "Détails techniques"
    :title-default           "French Public Sector Source Code"
    :title-prefix            "code.gouv.fr - "
    :total-commits           "Nombre de commits"
    :total-contributors      "Nombre de contributeurs"
    :under-license           " licensed "
    :understand-tech-terms   "A glossary to understand the technical terms used on this website"
    :update-short            "Updated"
    :updated                 "Updated"
    :website                 "Website"
    :with-more-of            " with the most"
    }})

(def opts {:dict localization})

(defn i
  "Main i18n fonction."
  [lang input]
  (tr opts [lang] [input]))
