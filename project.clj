; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci/notifier "0.0.0-PLACEHOLDER"
  :description "Cider-CI Notifier"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [clj-http "2.1.0"]
                 [honeysql "0.6.3"]
                 [org.apache.httpcomponents/httpclient "4.5.2"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [ring/ring-codec "1.0.0"]
                 ]
  :source-paths ["src"]
  :plugins [[cider-ci/lein_cider-ci_dev "0.2.1"]]
  :profiles {:dev
             {:dependencies [[midje "1.8.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["../config" "./config" "./resources"]
              }}
  :aot [:all]
  :main cider-ci.notifier.main
  :repl-options {:timeout  120000}
  )
