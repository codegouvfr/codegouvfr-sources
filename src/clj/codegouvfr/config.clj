;; Copyright (c) 2019 DINSIC, Bastien Guerry <bastien.guerry@data.gouv.fr>
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

(def admin-email
  (or (System/getenv "CODEGOUVFR_ADMIN_EMAIL")
      "bastien.guerry@data.gouv.fr"))

(def from
  (or (System/getenv "CODEGOUVFR_FROM")
      smtp-login))

(def codegouvfr_port
  (or (System/getenv "CODEGOUVFR_PORT")
      3000))

(def log-file "log.txt")
