; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(defproject cider-ci_notifier "3.0.0"
  :description "Cider-CI Notifier"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "3.0.0"]
                 [cider-ci/clj-utils "3.0.1"]
                 [clj-http "1.1.2"]
                 [honeysql "0.6.1"]
                 [org.apache.httpcomponents/httpclient "4.5"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 [ring/ring-codec "1.0.0"]
                 ]
  :source-paths ["src"]
  :profiles {:dev
             {:dependencies [[midje "1.7.0"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["../config" "./config" "./resources"]}
             :production
             {:resource-paths ["/etc/cider-ci" "../config" "./config" "./resources"]}}
  :aot [cider-ci.notifier.main]
  :main cider-ci.notifier.main
  :jvm-opts ["-Xmx256m"]
  :repl-options {:timeout  120000}
  )
