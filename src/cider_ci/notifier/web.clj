; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.notifier.web
  (:require
    [cider-ci.auth.core :as auth]
    [cider-ci.auth.core]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.config :as config :refer [get-config]]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data :as data]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.ring :refer [wrap-handler-with-logging]]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json]
    ))


;##### status dispatch ########################################################

(defn status-handler [request]
  (let [stati {:rdbms (rdbms/check-connection)
               :messaging (messaging/check-connection)
               }]
    (if (every? identity (vals stati))
      {:status 200
       :body (json/write-str stati)
       :headers {"content-type" "application/json;charset=utf-8"} }
      {:status 511
       :body (json/write-str stati)
       :headers {"content-type" "application/json;charset=utf-8"} })))


;#### routing #################################################################

(defn build-routes [context]
  (cpj/routes

    (cpj/GET "/status" request #'status-handler)

    (cpj/GET "/" [] "OK")

    ))

(defn build-main-handler [context]
  ( -> (cpj.handler/api (build-routes context))
       (wrap-handler-with-logging 'cider-ci.notifier.web)
       routing/wrap-shutdown
       (wrap-handler-with-logging 'cider-ci.notifier.web)
       (ring.middleware.json/wrap-json-body {:keywords? true})
       (wrap-handler-with-logging 'cider-ci.notifier.web)
       (routing/wrap-prefix context)
       (wrap-handler-with-logging 'cider-ci.notifier.web)
       (auth/wrap-authenticate-and-authorize-service)
       (wrap-handler-with-logging 'cider-ci.notifier.web)
       (http-basic/wrap {:executor false :user false :service true})
       (wrap-handler-with-logging 'cider-ci.notifier.web)
       (routing/wrap-log-exception)))


;#### the server ##############################################################

(defn initialize []
  (let [conf (get-config)]
    (cider-ci.auth.core/initialize conf)
    (let [http-conf (-> conf :services :notifier :http)
          context (str (:context http-conf) (:sub_context http-conf))]
      (http-server/start http-conf (build-main-handler context)))))



;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
