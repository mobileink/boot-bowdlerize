(ns resources)

#_(def scripts
  [{:runtime 'hello.scripts/jquery
   :uri "https://code.jquery.com/jquery-2.1.1.min.js"}])

#_(def statics
  [{:runtime 'hello.resources/statics
    :uri "target"}])

(def styles
  [{:runtime 'hello.styles/app :uri "styles/app.css"}
   {:runtime 'hello.scripts/app :uri "scripts/app.js"}
   {:bower "moment"
    :runtime 'hello.scripts/moment}])
