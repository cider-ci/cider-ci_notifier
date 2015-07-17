(ns cider-ci.notifier.github-statuses
  (:import
    [org.apache.http.client.utils URIBuilder]
    )
  (:require
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.thrown]
    [honeysql.core :as hc]
    [honeysql.helpers :as hh]
    [pg-types.all]
    [ring.util.codec :refer [url-encode]]
    ))


;### helper ###################################################################

(defn get-default-github-token []
  (let [token (-> (get-config) :github_authtoken)]
    (if (-> token clojure.string/blank? not)
      token
      nil)))

(defn amend-with-url-properties [params]
  (let [url (-> params :git_url URIBuilder.)
        path (.getPath url)
        path-segments (->> (clojure.string/split path #"\/")
                           (filter #(not (clojure.string/blank? %))))]
    (merge params
           {:host (.getHost url)
            :org (first path-segments)
            :repository (-> path-segments
                            second
                            (.replaceAll ".git$" ""))})))


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
  (str "https://api.github.com/repos/"
       (-> params :org url-encode) "/" (-> params :repository url-encode)
       "/statuses/"
       (-> params :commit_id url-encode)))

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
  (catcher/wrap-with-suppress-and-log-warn
    (let [token (or (:repository_github_authtoken params)
                    (:default_github_authtoken params)
                    (throw (IllegalStateException. "Neither :repository_github_authtoken nor :default_github_authtoken given")))
          url (-> params build-url)
          body (-> params build-body json/write-str)]
      (logging/debug [token url body])
      (http-client/post url
                        {:oauth-token token
                         :body body
                         :content-type :json })
      [url body])))


;### shared ###################################################################

(defn base-query []
  (-> (hh/select :repositories.git_url
                 [:repositories.github_authtoken :repository_github_authtoken]
                 [:jobs.id  :job_id]
                 :jobs.name
                 :jobs.state
                 [(get-default-github-token) :default_github_authtoken]
                 [:commits.id :commit_id])
      (hh/modifiers :distinct)
      (hh/from :repositories)
      (hh/merge-where [:or
                       [:<> :repositories.github_authtoken nil]
                       [:and
                        [:= :repositories.use_default_github_authtoken true ]
                        (hc/raw (-> (get-default-github-token) boolean))]])))

(defn evaluate-update [get-repos-and-jobs]
  (future (catcher/wrap-with-suppress-and-log-error
            (->> (get-repos-and-jobs)
                 (map amend-with-url-properties)
                 (map post-status)
                 doall))))


;### Job update ###############################################################

(defn build-repos-and-jobs-query-by-job-id [job-id]
  (-> (base-query)
      (hh/merge-join :branches [:= :branches.repository_id :repositories.id])
      (hh/merge-join :branches_commits [:= :branches.id :branches_commits.branch_id])
      (hh/merge-join :commits [:= :commits.id :branches_commits.commit_id])
      (hh/merge-join :jobs [:= :jobs.tree_id :commits.tree_id])
      (hh/merge-where [:= :jobs.id job-id])
      hc/format))

(defn get-repos-and-jobs-by-job-update [msg]
  (jdbc/query
    (rdbms/get-ds)
    (build-repos-and-jobs-query-by-job-id (:id msg))))

(defn evaluate-job-update [msg]
  (evaluate-update
    (fn [] (get-repos-and-jobs-by-job-update msg))))

;(evaluate-job-update {:id "9fba4026-8968-4ad4-98ea-8f583c5955f5", :state "passed"})
(defn listen-to-job-updates-and-fire-evaluate-job-update []
  (messaging/listen "job.updated" evaluate-job-update))


;### Branch update ############################################################

(defn get-repos-and-jobs-by-branch-update [msg]
  (let [query (-> (base-query)
                  (hh/merge-join :branches [:= :branches.repository_id :repositories.id])
                  (hh/merge-join :commits [:= :commits.id :branches.current_commit_id])
                  (hh/merge-join :jobs [:= :jobs.tree_id :commits.tree_id])
                  (hh/merge-where [:= :repositories.id (:repository_id msg)])
                  (hh/merge-where [:= :branches.name (:name msg)])
                  hc/format)]
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
  (listen-to-job-updates-and-fire-evaluate-job-update)
  (listen-to-branch-updates-and-fire-evaluate-branch-update))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
