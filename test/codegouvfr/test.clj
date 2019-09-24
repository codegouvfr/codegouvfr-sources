;; Copyright (c) 2019 DINSIC, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.test
  (:require [clojure.test :refer :all]
            [codegouvfr.config :as config]))

(deftest test-environment-variables
  (testing "Checking if all environment variables contain strings."
    (is (and (string? (System/getenv "SMTP_HOST"))
             (string? (System/getenv "SMTP_LOGIN"))
             (string? (System/getenv "SMTP_PASSWORD"))
             (string? (System/getenv "CODEGOUVFR_ADMIN_EMAIL"))
             (string? (System/getenv "CODEGOUVFR_FROM"))
             (string? (System/getenv "CODEGOUVFR_PORT"))))))

