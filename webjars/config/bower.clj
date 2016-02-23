(ns bower)

(def config-map
  [{:bower "moment"
    :runtime 'hello.scripts/moment}
   {:bower "materialize"
    :bundles [{:runtime 'hello.styles/materialize
               :uri "bower_components/Materialize/bin/materialize.css"}
              {:runtime 'hello.scripts/materialize
               :uri "bower_components/Materialize/bin/materialize.js"}]}])
