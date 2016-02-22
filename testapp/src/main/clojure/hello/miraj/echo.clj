(ns hello.miraj.echo
  (:require [clojure.math.numeric-tower :as math]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.handler.dump :refer :all] ; ring-devel
            [ring.util.response :as rsp]
            [ring.util.servlet :as ring]
            [ring.middleware.params :refer [wrap-params]] ; in ring-core
            [ring.middleware.defaults :refer :all])
  (:import [java.io ByteArrayInputStream StringReader StringWriter]
           [javax.xml.stream XMLInputFactory
            XMLStreamReader
            XMLStreamConstants])) ; ring-defaults

;;(println "ring reloading echo")

(set! *warn-on-reflection* true)

(defroutes echo-routes
  (context "/echo" []
    (GET "/request/body" req
         (do (println "request")
             (-> (rsp/response (str "<p>" (dissoc req :body) "</p>"))
                 (rsp/content-type "text/html"))))
    (GET "/hello/:name" [name]
         (-> (rsp/response (str "Hi there, " name))
             (rsp/content-type "text/html")))
    (route/not-found "<h1>Echo API not found</h1>")))

(ring/defservice
   (-> (routes
        echo-routes)
       (wrap-defaults api-defaults)
       ))
