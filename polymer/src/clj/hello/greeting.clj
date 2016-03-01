(ns hello.greeting
  (:require [hello.page.hello :as h]
            ;; uncomment if running on jetty
            ;; [hello.resources :as resources]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.handler.dump :refer :all] ; ring-devel
            [ring.util.response :as rsp]
            [ring.util.servlet :as ring]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.defaults :refer :all]))

(defroutes app-routes

  (GET "/" []
       h/homepage)

  ;; uncomment if running on jetty
  ;; (route/files "/" [:root (:uri resources/statics)])

  (route/not-found "NOT FOUND"))

;; for jetty:
;; (def app
;;   (wrap-defaults app-routes site-defaults))

;; for gae:
(ring/defservice
   (-> (routes
        app-routes)
       (wrap-defaults api-defaults)
       ))
