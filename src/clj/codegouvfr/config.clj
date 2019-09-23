(ns codegouvfr.config)

(def smtp-host
  (or (System/getenv "MAILGUN_HOST")
      "smtp.mailgun.org"))

(def smtp-login
  (System/getenv "MAILGUN_LOGIN"))

(def smtp-password
  (System/getenv "MAILGUN_PASSWORD"))

(def admin-email
  (or (System/getenv "CODEGOUVFR_ADMIN_EMAIL")
      "bastien.guerry@data.gouv.fr"))

(def from "opensource"
  (or (System/getenv "CODEGOUVFR_FROM")
      "postmaster@mail.etalab.studio"))

(def log-file "log.txt")
