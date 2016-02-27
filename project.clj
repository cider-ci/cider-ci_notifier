; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(import 'java.io.File)
(load-file (str "src" File/separator "cider_ci" File/separator "notifier.clj"))

(defproject cider-ci_notifier cider-ci.notifier/VERSION
  :description "Cider-CI Notifier"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-utils "8.3.0"]

                 [clj-http "2.1.0"]
                 [honeysql "0.6.3"]
                 [org.apache.httpcomponents/httpclient "4.5.2"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [ring/ring-codec "1.0.0"]
                 ]
  :source-paths ["src"]
  :profiles {:dev
             {:dependencies [[midje "1.8.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["../config" "./config" "./resources"]
              }
             :production
             {:resource-paths ["/etc/cider-ci" "../config" "./config" "./resources"]}}
  :aot [cider-ci.notifier.main]
  :main cider-ci.notifier.main
  :repl-options {:timeout  120000}
  )
