; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.notifier.main
  (:gen-class)
  (:require
    [cider-ci.self]
    [cider-ci.utils.app]
    [cider-ci.notifier.web :as web]
    [cider-ci.notifier.github-statuses]

    [logbug.catcher :as catcher]
    [logbug.thrown]
    [clojure.tools.logging :as logging]
    ))

(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn #(System/exit -1)}
    (cider-ci.utils.app/init web/build-main-handler)
    (cider-ci.notifier.github-statuses/initialize)
    ))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)


