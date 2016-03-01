(ns hello.page.hello
  (:require [hiccup.core :refer :all]
            [hello.scripts :as scripts]
            [hello.styles :as styles]
            [polymer.polyfill.min :as polyfill]
            [polymer.paper :as paper :refer [button card]]
            [polymer.iron :as iron :refer [icon input]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(def home-meta
  {:title "boot-bowdlerize polymer demo"
   :description "this page demonstrates usage of boot-bowdlerize with polymer components"
   :platform {:apple {:mobile-web-app {:capable true
                                       :status-bar-style :black
                                       :title :hello-str} ;; "Hello"
                      :touch {:icon {:uri "/images/touch/chrome-touch-icon-192x192.png"}}}
              :ms {:navbutton-color "#FFCCAA"
                   :tile-color "#3372DF"
                   :tile-image "images/ms-touch-icon-144x144-precomposed.png"}
              :mobile {:agent {:format :html5
                               :url "http://example.org/"}
                       :web-app-capable true}}})

(def homepage
  (html
   [:script {:src (:uri polyfill/lite)}]
   [:link {:href (str (:uri paper/button)) :rel "import"}]
   [:link {:href (str (:uri paper/card)) :rel "import"}]
   ;; iron-icon won't work unless we also import iron-icons
   [:link {:href (str (:uri iron/icons)) :rel "import"}]
   [:link {:href (str (:uri iron/icon)) :rel "import"}]
   [:link {:href (str (:uri iron/input)) :rel "import"}]
   [:link {:href (str (:uri styles/hello)) :rel "stylesheet" :type "css"}]
   [:link {:href (str (:uri styles/cards)) :rel "stylesheet" :type "css"}]

   [:body
    [:h1 "HELLO BOWDLERIZED POLYMER!"]
    [:div [:iron-icon {:icon "menu"}]]
    [:div {:id "cards"}
     [:paper-card {:heading "Hello, you ol' Card!"}
      [:div {:class "card-content"} "Some content"]
      [:div {:class "card-actions"}
       [:paper-button {:id "btn" :raised true} "Click me! Do it!  Do it now!"]]]]
    [:script {:src (:uri scripts/app)}]
    ]))
