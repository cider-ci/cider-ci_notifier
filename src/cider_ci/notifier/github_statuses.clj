; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.notifier.github-statuses
  (:require
    [cider-ci.notifier.github-statuses.branch-updates]
    [cider-ci.notifier.github-statuses.job-updates]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))



;### Initialize ###############################################################

(defn initialize []
  (cider-ci.notifier.github-statuses.job-updates/initialize)
  (cider-ci.notifier.github-statuses.branch-updates/initialize))



;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
