(ns codegouvfr.i18n
  (:require [taoensso.tempura :refer [tr]]))

(def supported-languages #{"en" "fr"})

(def localization
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
    :repos-from                  "Dépôts de "
    :source-code                 "code source"
    :repos-of-source-code        "Dépôts de code source"
    :orgas-or-groups             "Organisations ou groupes"
    :mean-repos-by-orga          "Nombre moyen de dépôts par organisation/groupe"
    :median-repos-by-orga        "Nombre médian de dépôts par organisation/groupe"
    :with-more-of                " avec le plus de "
    :orgas-with-more-stars       "Organisations/groupes les plus étoilés"
    :language                    "Langage"
    :license                     "Licence"
    :licenses                    "Licences"
    :more-used                   " les plus utilisées"
    :distribution-by-platform    "Répartition par plateformes"
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
    :your-affiliation            "Votre organisme public de rattachement"
    :affiliation-placeholder     "Par ex. DGFIP"
    :your-message                "Message"
    :message-placeholder         "Votre message"
    :submit                      "Envoyer"
    :message-received            "Message reçu !"
    :message-received-ok         "Nous nous efforçons de répondre au plus vite."
    :back-to-repos               "Retour à la liste des dépôts de code source"
    :glossary                    "Glossaire"
    :about                       "À propos"}
   :en
   {:no-repo-found               "Repository not found : would you like to make a new request?"
    :orga-repo                   "Repository / group"
    :sort-repos-alpha            "Trier par ordre alphabétique des noms de dépôts"
    :archive                     "Archive"
    :swh-link                    "Lien vers l'archive faite par Software Heritage"
    :description                 "Description"
    :sort-description-length     "Trier par longueur de description"
    :update                      "Update"
    :sort-update-date            "Trier par date de mise à jour"
    :update-short                "MàJ"
    :forks                       "Forks"
    :sort-forks                  "Trier par nombre de fourches"
    :stars                       "Stars"
    :sort-stars                  "Trier par nombre d'étoiles"
    :issues                      "Issues"
    :sort-issues                 "Trier par nombre de tickets"
    :browse-repos-orga           "Voir la liste des dépôts de cette organisation ou de ce groupe"
    :go-to-repo                  "Voir ce dépôt"
    :under-license               " licensed "
    :repo-archivedo              "Ce dépôt est archivé"
    :no-orga-found               "Pas d'organisation ou de groupe trouvé : une autre idée de requête ?"
    :go-to-orga                  "Visiter le compte d'organisation ou le groupe"
    :created-at                  "Créé le "
    :repos-number                "Nombre de dépôts"
    :go-to-repos                 "Voir les dépôts"
    :repo                        " repository"
    :repos                       " repositories"
    :visit-on-github             "Visiter sur GitHub"
    :visit-on-gitlab             "Visiter sur l'instance GitLab"
    :contact-by-email            "Contacter par email"
    :go-to-website               "Visiter le site web"
    :go-to-sig-website           "Visiter le site sur l'annuaire du service public"
    :go-to-glossary              "Voir le glossaire"
    :repos-from                  "Dépôts de "
    :source-code                 "source code"
    :repos-of-source-code        "Source code repositories"
    :orgas-or-groups             "Organizations or groups"
    :mean-repos-by-orga          "Nombre moyen de dépôts par organisation/groupe"
    :median-repos-by-orga        "Nombre médian de dépôts par organisation/groupe"
    :with-more-of                " with the most "
    :orgas-with-more-stars       "Organisations/groupes les plus étoilés"
    :language                    "Language"
    :license                     "License"
    :licenses                    "Licenses"
    :more-used                   " les plus utilisées"
    :distribution-by-platform    "Répartition par plateformes"
    :archive-on                  "Archive sur "
    :repos-on-swh                "Dépôts dans Software Heritage"
    :percent-of-repos-archived   "Proportion de dépôts archivés"
    :github-gitlab-etc           "Sur GitHub ou sur des instances GitLab"
    :stats                       "Chiffres"
    :free-search                 "Recherche libre"
    :remove-filter               "Remove filter : display all the organizations or groups"
    :only-forks                  " Forks only"
    :only-forked-repos           "Forked repositories only"
    :no-archives                 " Remove archives"
    :no-archived-repos           "Do not include archived repositories"
    :only-with-description-repos "Repositories with a description only"
    :with-description            " With a description"
    :only-with-license           "Que les dépôts ayant une licence identifiée"
    :with-license                " Avec licence identifiée"
    :one-repo                    "1 repository"
    :only-orga-with-code         "Que les organisations ayant publié du code"
    :with-code                   " Avec du code publié"
    :sort-orgas-alpha            "Trier par ordre alphabétique des noms d'organisations ou de groupes"
    :sort-alpha                  "Alphabetical order"
    :sort-repos                  "Trier par nombre de dépôts"
    :sort-orgas-creation         "Trier par date de création de l'organisation ou du groupe"
    :sort-creation               "Par date de création"
    :one-group                   "1 group"
    :groups                      " groups"
    :last-repos                  "Derniers dépôts de codes sources publics"
    :keywords                    "Accès aux codes sources du secteur public"
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
    :contact-baseline            "Un compte d'organisation à signaler ? Un dépôt de code à ouvrir ? Sollicitez-nous !"
    :your-name                   "Your name"
    :your-email                  "Your email address"
    :email-placeholder           "E.g. toto@modernisation.gouv.fr"
    :your-affiliation            "Votre organisme public de rattachement"
    :affiliation-placeholder     "E.g. DGFIP"
    :your-message                "Message"
    :message-placeholder         "Your message"
    :submit                      "Send"
    :message-received            "Message received!"
    :message-received-ok         "We will do our best to reply as soon as possible."
    :back-to-repos               "Retour à la liste des dépôts de code source"
    :glossary                    "Glossary"
    :about                       "À propos"}})

(def opts {:dict localization})

(defn i [lang input] (tr opts [lang] input))
