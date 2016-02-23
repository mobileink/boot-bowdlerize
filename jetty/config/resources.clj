(ns resources)

(def scripts
  [{:runtime 'hello.scripts/jquery :uri "https://code.jquery.com/jquery-2.1.1.min.js"}
   {:runtime 'hello.scripts/app :uri "scripts/app.js"}])

(def statics
  [{:runtime 'hello.resources/statics
    :uri "target"}])

(def styles
  [{:runtime 'hello.styles/app :uri "styles/app.css"}])
