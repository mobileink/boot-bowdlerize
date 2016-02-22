(ns bower)

(def config-map
  {"moment" 'foo.js/moment
   "materialize" [{:ns 'foo.style :name 'materialize-css
                   :uri "bower_components/Materialize/bin/materialize.css"}
                  {:ns 'foo.style :name 'materialize-js
                   :uri "bower_components/Materialize/bin/materialize.js"}]})
