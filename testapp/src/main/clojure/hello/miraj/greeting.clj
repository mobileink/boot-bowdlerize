(ns hello.miraj.greeting
  (:require [hello.miraj.page.hello :as h]
            [miraj.markup :as miraj]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.handler.dump :refer :all] ; ring-devel
            [ring.util.response :as rsp]
            [ring.util.servlet :as ring]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.defaults :refer :all]))

(defroutes app-routes

  (GET "/miraj" []
       (h/homepage))

  (route/not-found "NOT FOUND"))

(ring/defservice
   (-> (routes
        app-routes)
       (wrap-defaults api-defaults)
       ))
