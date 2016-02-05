; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.notifier.web
  (:require
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
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
    [cider-ci.utils.status :as status]


    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :refer [wrap-handler-with-logging]]

    [clj-logging-config.log4j :as logging-config]
    ))


;#### routing #################################################################

(defn build-routes [context]
  (cpj/routes
    ))

(defn build-main-handler [context]
  (I> wrap-handler-with-logging
      (cpj.handler/api (build-routes context))
      routing/wrap-shutdown
      (ring.middleware.json/wrap-json-body {:keywords? true})
      status/wrap
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
