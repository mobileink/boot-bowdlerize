(ns resources)

(def scripts [{:runtime 'hello.scripts/jquery :uri "https://code.jquery.com/jquery-2.1.1.min.js"}
              {:runtime 'hello.scripts/main :uri "/scripts/app.js"}])

;; we would need the following config var for jetty, so we could put
;;  (route/files "/" [:root (:uri resources/statics)])
;; in our routes.  but for gae, static files are handled
;; by the servlet container by default, so we don't need it
;; (def statics [{:runtime 'hello.resources/statics :uri "target"}])

(def styles [{:runtime 'hello.styles/app :uri "/styles/app.css"}])

