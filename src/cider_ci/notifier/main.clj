(ns cider-ci.notifier.main
  (:gen-class)
  (:require
    [cider-ci.notifier.github-statuses]
    [cider-ci.notifier.web :as web]
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.thrown]
    [cider-ci.utils.nrepl :as nrepl]
    ))

(defn -main [& args]
  (logging/info "The notifier is initializing ...")
  (catcher/with-logging {}
    (config/initialize {})
    (let [conf (config/get-config)]
      (logbug.thrown/reset-ns-filter-regex #".*cider.ci.*")
      (rdbms/initialize (get-db-spec :notifier))
      (messaging/initialize (:messaging conf))
      (cider-ci.notifier.github-statuses/initialize)
      (nrepl/initialize (-> conf :services :notifier :nrepl))
      (web/initialize)))
  (logging/info "The notifier is listening ..."))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)


