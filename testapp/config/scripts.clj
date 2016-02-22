(ns scripts)

(alter-meta! *ns*
             (fn [m] (assoc m :resource-type :js)))

(def app {:uri "scripts/app.js"})

(def main {:uri "scripts/main.js"})

(def page {:uri "bower_components/page/page.js"})

(def routing {:uri "scripts/routing.js"})

(def polyfill {:uri "bower_components/webcomponentsjs/webcomponents.js"})
(def polyfill-min {:uri "bower_components/webcomponentsjs/webcomponents.min.js"})

(def polyfill-lite {:uri "bower_components/webcomponentsjs/webcomponents-lite.js"})
(def polyfill-lite-min {:uri "bower_components/webcomponentsjs/webcomponents-lite.min.js"})
