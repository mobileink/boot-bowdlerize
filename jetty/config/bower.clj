(ns bower)

(def config-map
  [{:bower "moment"
    :runtime 'hello.scripts/moment}
   {:bower "materialize"
    :runtime {'hello.styles/materialize
              "bower_components/Materialize/bin/materialize.css"
              'hello.scripts/materialize
              "bower_components/Materialize/bin/materialize.js"}}])
