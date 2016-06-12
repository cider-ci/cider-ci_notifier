(ns cider-ci.notifier.github-statuses.branch-updates
  (:require

    [cider-ci.notifier.github-statuses.shared
     :refer [base-query post-updates]]

    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.row-events :as row-events]

    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))


;### Branch update ############################################################

(defn get-repos-and-jobs-by-branch-update [branch-id]
  (let [query (-> base-query
                  (sql/merge-join :branches [:= :branches.repository_id :repositories.id])
                  (sql/merge-join :commits [:= :commits.id :branches.current_commit_id])
                  (sql/merge-join :jobs [:= :jobs.tree_id :commits.tree_id])
                  (sql/merge-where [:= :branches.id branch-id])
                  sql/format)]
    (jdbc/query
      (rdbms/get-ds)
      query)))

(defn evaluate-branch-update [branch-id]
  (post-updates
    (fn [] (get-repos-and-jobs-by-branch-update branch-id))))


;### Listen to branch updates #################################################

(def last-processed-branch-update (atom nil))

(defdaemon "process-branch-updates" 1
  (row-events/process "branch_update_events" last-processed-branch-update
                      (fn [row] (evaluate-branch-update (:branch_id row)))))

;### Initialize ###############################################################


(defn initialize []
  (start-process-branch-updates))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
