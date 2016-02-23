(ns resources)

(def scripts
  {'hello.scripts/jquery {:uri "https://code.jquery.com/jquery-2.1.1.min.js"}})

(def statics
  {'hello.resources/statics {:uri "target"}})

(def styles
  {'hello.styles/app {:uri "styles/app.css"}
   'hello.scripts/app {:uri "scripts/app.js"}})
