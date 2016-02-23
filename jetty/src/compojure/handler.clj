(ns compojure.handler
  (:require [hiccup.core :refer :all]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [hello.resources :as resources]
            [hello.scripts :as scripts]
            [hello.styles :as styles]))

(defroutes app-routes
  (GET "/hi" [] "HELLO World")

  ;; favicon:
  ;; <link rel="icon"
  ;;     type="image/png"
  ;;     href="http://example.com/myicon.png">

  (GET "/" []
       (html [:head
              [:link {:href "http://fonts.googleapis.com/icon?family=Material+Icons"
                      :rel "stylesheet"}]
              ;; Import materialize.css
              [:link {:href (str (:uri styles/materialize))
                      :rel "stylesheet"
                      :type "css"}]
              [:link {:href (str (:uri styles/app))
                      :rel "stylesheet"
                      :type "css"}]

              ;; <!--Let browser know website is optimized for mobile-->
              [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]]
             [:body
              [:p "Hello"]
              ;; Import jQuery before materialize.js
              [:script {:type "text/javascript"
                        :src (:uri scripts/jquery)}]
              [:script {:type "text/javascript"
                        :src (:uri scripts/materialize)}]
              [:script {:type "text/javascript"
                        :src (:uri scripts/app)}]]))

  (route/files "/" [:root (:uri resources/statics)])

  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
