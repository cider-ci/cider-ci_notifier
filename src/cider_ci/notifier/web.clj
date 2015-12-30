; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.notifier.web
  (:require
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.runtime :as runtime]

    [clojure.data :as data]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json]

    [logbug.debug :as debug :refer [÷> ÷>>]]
    [logbug.ring :refer [wrap-handler-with-logging]]

    [clj-logging-config.log4j :as logging-config]
    ))


;##### status dispatch ########################################################

(defn status-handler [request]
  (let [rdbms-status (rdbms/check-connection)
        messaging-status (rdbms/check-connection)
        memory-status (runtime/check-memory-usage)
        body (json/write-str {:rdbms rdbms-status
                              :messaging messaging-status
                              :memory memory-status})]
    {:status  (if (and rdbms-status messaging-status (:OK? memory-status))
                200 499 )
     :body body
     :headers {"content-type" "application/json;charset=utf-8"} }))


;#### routing #################################################################

(defn build-routes [context]
  (cpj/routes

    (cpj/GET "/status" request #'status-handler)

    (cpj/GET "/" [] "OK")

    ))

(defn build-main-handler [context]
  (÷> wrap-handler-with-logging
      (cpj.handler/api (build-routes context))
      routing/wrap-shutdown
      (ring.middleware.json/wrap-json-body {:keywords? true})
      (routing/wrap-prefix context)
      (authorize/wrap-require! {:service true})
      (http-basic/wrap {:executor false :user false :service true})
      (routing/wrap-log-exception)))


;#### the server ##############################################################

(defn initialize []
  (let [conf (get-config)]
    (let [http-conf (-> conf :services :notifier :http)
          context (str (:context http-conf) (:sub_context http-conf))]
      (http-server/start http-conf (build-main-handler context)))))



;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
