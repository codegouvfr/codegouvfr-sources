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

(def from "opensource"
  (or (System/getenv "CODEGOUVFR_FROM")
      smtp-login))

(def log-file "log.txt")
