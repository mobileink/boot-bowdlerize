(ns compojure.handler
  (:require [hiccup.core :refer :all]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [hello.scripts :as scripts]
            [hello.styles :as styles]))

(defroutes app-routes
  (GET "/" [] "HELLO World")

  ;; favicon:
  ;; <link rel="icon"
  ;;     type="image/png"
  ;;     href="http://example.com/myicon.png">

  (GET "/foo" []
       (html [:head
              [:link {:href "http://fonts.googleapis.com/icon?family=Material+Icons"
                      :rel "stylesheet"}]
              ;; Import materialize.css
              [:link {:type "text/css" :rel="stylesheet"
                      :href (:uri styles/materialize)
                      #_"css/materialize.min.css"
                      :media "screen,projection"}]

              ;; <!--Let browser know website is optimized for mobile-->
              [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]]
             [:body
              ;; Import jQuery before materialize.js
              [:script {:type "text/javascript"
                        :src "https://code.jquery.com/jquery-2.1.1.min.js"}]
              [:script {:type "text/javascript"
                        :src (:uri scripts/materialize)
                        #_"js/materialize.min.js"}]]))

  (route/resources "/" :root "./")

  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
