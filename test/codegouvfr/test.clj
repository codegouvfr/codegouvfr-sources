;; Copyright (c) 2019 DINSIC, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns codegouvfr.test
  (:require [clojure.test :refer :all]
            [codegouvfr.config :as config]
            [codegouvfr.server :as server]
            [clj-http.client :as http]))

(deftest environment-variables-exist
  (testing "Checking if all environment variables contain strings."
    (is (string? (System/getenv "SMTP_HOST")))
    (is (string? (System/getenv "SMTP_LOGIN")))
    (is (string? (System/getenv "SMTP_PASSWORD")))
    (is (string? (System/getenv "CODEGOUVFR_ADMIN_EMAIL")))
    (is (string? (System/getenv "CODEGOUVFR_FROM")))
    (is (string? (System/getenv "CODEGOUVFR_PORT")))))

(defn test-url [url]
  (is (= 200 (try (:status (http/get url))
                  (catch Exception e nil)))))

(deftest urls-are-reachable
  (testing "Checking if URLs return the data we need."
    (test-url server/repos-url)
    (test-url server/orgas-url)
    (test-url server/emoji-json-url)
    (test-url server/annuaire-url)))
