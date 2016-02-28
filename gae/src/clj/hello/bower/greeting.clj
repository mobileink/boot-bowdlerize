(ns hello.bower.greeting
  (:require [hello.bower.page.hello :as h]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.handler.dump :refer :all] ; ring-devel
            [ring.util.response :as rsp]
            [ring.util.servlet :as ring]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.defaults :refer :all]))

(defroutes app-routes

  (GET "/hello/:you" [you :as rqst]
       (do (println "handler hello.bower.greeting on " (str (.getRequestURL (:servlet-request rqst))))
           (h/homepage you)))

  (route/not-found "NOT FOUND"))

(ring/defservice
   (-> (routes
        app-routes)
       (wrap-defaults api-defaults)
       ))
