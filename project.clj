;; Copyright (c) 2019 DINSIC, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(defproject codegouvfr "0.9.8"
  :description "Frontend to display public sector source code repositories"
  :url "https://github.com/etalab/codegouvfr"
  :license {:name "Eclipse Public License - v 2.0"
            :url  "http://www.eclipse.org/legal/epl-v20.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [compojure "1.6.1"]
                 [clj-http "3.10.0"]
                 [clj-rss "0.2.5"]
                 [ring/ring-core "1.8.0"]
                 [ring/ring-jetty-adapter "1.8.0"]
                 [ring/ring-devel "1.8.0"]
                 [ring/ring-defaults "0.3.2"]
                 [cheshire "5.10.0"]
                 [com.draines/postal "2.0.3"]
                 [com.taoensso/timbre "4.10.0"]
                 [markdown-to-hiccup "0.6.2"]
                 ;; FIXME: this explicit require should not be needed:
                 [org.clojure/tools.reader "1.3.2"]
                 ;; FIXME: https://github.com/ptaoussanis/tempura/issues/29#issuecomment-557178091
                 [com.taoensso/tempura "1.2.1"]]
  :source-paths ["src/clj" "src/cljc" "src/cljs"]
  :main codegouvfr.server
  :uberjar-name "codegouvfr-standalone.jar"
  :auto-clean false
  :clean-targets ^{:protect false} ["target" "resources/public/js/dev/"
                                    "resources/public/js/codegouvfr.js"]
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]}
  :profiles {:uberjar {:aot :all}
             :dev     {:source-paths ["src/cljc" "src/cljs"]
                       :dependencies [[cljs-ajax "0.8.0"]
                                      [cljs-bean "1.5.0"]
                                      [com.bhauman/figwheel-main "0.2.3" :exclusions [joda-time]]
                                      [com.bhauman/rebel-readline-cljs "0.1.4"]
                                      [markdown-to-hiccup "0.6.2"]
                                      [org.clojure/clojurescript "1.10.597"]
                                      [org.clojure/core.async "1.0.567"]
                                      [re-frame "0.11.0"]
                                      [reagent "0.9.1"]
                                      [reagent-utils "0.3.3"]
                                      [metosin/reitit-frontend "0.4.2"]]}})
