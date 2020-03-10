;; Copyright (c) 2019-2020 DINUM, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.config)

(def smtp-host
  (or (System/getenv "SMTP_HOST")
      "localhost"))

(def smtp-login
  (System/getenv "SMTP_LOGIN"))

(def smtp-password
  (System/getenv "SMTP_PASSWORD"))

(def msgid-domain
  "The message-id domain to enhance email deliverability."
  (System/getenv "CODEGOUVFR_MSGID_DOMAIN"))

(def admin-email
  "The email address where to receive contact emails."
  (or (System/getenv "CODEGOUVFR_ADMIN_EMAIL")
      "bastien.guerry@data.gouv.fr"))

(def from
  (or (System/getenv "CODEGOUVFR_FROM")
      smtp-login))

(def github-user
  (System/getenv "CODEGOUVFR_GITHUB_USER"))

(def github-access-token
  (System/getenv "CODEGOUVFR_GITHUB_ACCESS_TOKEN"))

(def codegouvfr_port
  (or (read-string (System/getenv "CODEGOUVFR_PORT"))
      3000))

(def log-file
  "Where to store the application logs."
  "log.txt")
