; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.notifier.github-statuses
  (:import
    [org.apache.http.client.utils URIBuilder]
    )
  (:require

    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]

    [clj-http.client :as http-client]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]
    [logbug.catcher :as catcher]
    [pg-types.all]
    [ring.util.codec :refer [url-encode]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))


;### POST Status ##############################################################

(defn build-target-url [params]
  (let [config (get-config)
        ui-http-config (-> config :services :ui :http)]
    (str (:server_base_url config)
         (:context ui-http-config)
         (:sub_context ui-http-config)
         "/workspace/jobs/"
         (:job_id params))))

(defn build-url [params]
  (->> [ "repos"
        (:api_owner params)
        (:api_repo params)
        "statuses"
        (:commit_id params)]
       (map url-encode)
       (concat [(:api_endpoint params)])
       (clojure.string/join "/")))

(defn map-state [state]
  (case state
    ("pending" "dispatching" "executing") "pending"
    ("passed") "success"
    ("failed") "failure"
    "error"))

(defn build-body [params]
  {"state"  (map-state (:state params))
   "target_url"  (build-target-url params)
   "description" (str (:state params) " - " (:name params))
   "context"  (str "Cider-CI@" (:hostname (get-config)) " - " (:name params) )})

(defn post-status [params]
  (catcher/snatch
    {}
    (let [token (:api_authtoken params)
          url (-> params build-url)
          body (-> params build-body json/write-str)]
      (logging/debug [token url body])
      (http-client/post url
                        {:oauth-token token
                         :body body
                         :content-type :json })
      [url body])))


;### shared ###################################################################

(def base-query
  (-> (sql/select :repositories.git_url
                  [:repositories.foreign_api_endpoint :api_endpoint]
                  [:repositories.foreign_api_authtoken :api_authtoken]
                  [:repositories.foreign_api_owner :api_owner]
                  [:repositories.foreign_api_repo :api_repo]
                  [:jobs.id  :job_id]
                  :jobs.name
                  :jobs.state
                  [:commits.id :commit_id])
      (sql/modifiers :distinct)
      (sql/from :repositories)
      (sql/merge-where (sql/raw (str "repositories.foreign_api_endpoint !~ '^\\s*$'")))
      (sql/merge-where (sql/raw (str "repositories.foreign_api_authtoken !~ '^\\s*$'")))
      (sql/merge-where (sql/raw (str "repositories.foreign_api_owner !~ '^\\s*$'")))
      (sql/merge-where (sql/raw (str "repositories.foreign_api_repo !~ '^\\s*$'")))))

(defn evaluate-update [get-repos-and-jobs]
  (future (catcher/snatch {} (->> (get-repos-and-jobs)
                                  (map post-status)
                                  doall))))


;### Job update ###############################################################

(defn build-repos-and-jobs-query-by-job-id [job-id]
  (-> base-query
      (sql/merge-join :branches [:= :branches.repository_id :repositories.id])
      (sql/merge-join :branches_commits [:= :branches.id :branches_commits.branch_id])
      (sql/merge-join :commits [:= :commits.id :branches_commits.commit_id])
      (sql/merge-join :jobs [:= :jobs.tree_id :commits.tree_id])
      (sql/merge-where [:= :jobs.id job-id])
      sql/format))

(defn get-repos-and-jobs-by-job-update [job-id]
  (jdbc/query
    (rdbms/get-ds)
    (build-repos-and-jobs-query-by-job-id job-id)))

(defn evaluate-job-update [job-id]
  (evaluate-update
    (fn [] (get-repos-and-jobs-by-job-update job-id))))



;### Listen to job updates ####################################################

(def last-processed-job-update (atom nil))

(defn process-job-updates [proc]
  (when-let [after-row (or @last-processed-job-update
                           (I>> identity-with-logging
                                [(str "SELECT * FROM job_state_update_events"
                                      " ORDER BY created_at DESC, id LIMIT 1")]
                                (jdbc/query (rdbms/get-ds))
                                first))]
    (if-let [lst (I>> identity-with-logging
                      [(str "SELECT * FROM job_state_update_events"
                            " WHERE created_at >= ? AND id != ?"
                            " ORDER BY created_at ASC , id LIMIT 100")
                       (:created_at after-row) (:id after-row)]
                      (jdbc/query (rdbms/get-ds))
                      (map (fn [row] (proc (:job_id row)) row))
                      last)]
      (reset! last-processed-job-update lst)
      (reset! last-processed-job-update after-row))))

(defdaemon "process-job-updates"
  1 (process-job-updates evaluate-job-update))


;### Branch update ############################################################

(defn get-repos-and-jobs-by-branch-update [msg]
  (let [query (-> base-query
                  (sql/merge-join :branches [:= :branches.repository_id :repositories.id])
                  (sql/merge-join :commits [:= :commits.id :branches.current_commit_id])
                  (sql/merge-join :jobs [:= :jobs.tree_id :commits.tree_id])
                  (sql/merge-where [:= :repositories.id (:repository_id msg)])
                  (sql/merge-where [:= :branches.name (:name msg)])
                  sql/format)]
    (jdbc/query
      (rdbms/get-ds)
      query)))

(defn evaluate-branch-update [msg]
  (evaluate-update
    (fn [] (get-repos-and-jobs-by-branch-update msg))))

(defn listen-to-branch-updates-and-fire-evaluate-branch-update []
  (messaging/listen "branch.updated" evaluate-branch-update))


;### Initialize ###############################################################

(defn initialize []
  (start-process-job-updates)
  ; TODO replace the following
  (listen-to-branch-updates-and-fire-evaluate-branch-update))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
