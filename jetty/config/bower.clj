(ns bower)

(def config-map
  {"moment" 'hello.scripts/moment
   "materialize" [{:ns 'hello.styles :name 'materialize
                   :uri "bower_components/Materialize/bin/materialize.css"}
                  {:ns 'hello.scripts :name 'materialize
                   :uri "bower_components/Materialize/bin/materialize.js"}]})
