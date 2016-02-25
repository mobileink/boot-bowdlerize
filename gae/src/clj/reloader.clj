(ns reloader
  (:import (javax.servlet Filter FilterChain FilterConfig
                          ServletRequest ServletResponse))
  (:require [ns-tracker.core :refer :all]))

(defn -init [^Filter this ^FilterConfig cfg])

(defn -destroy [^Filter this])

(def modified-namespaces (ns-tracker ["./"]))  ;; hello/miraj" "hello/miraj/page"]))

(defn -doFilter
  [^Filter this
   ^ServletRequest rqst
   ^ServletResponse resp
   ^FilterChain chain]
  (doseq [ns-sym (modified-namespaces)]
    (println "reloading " (str ns-sym))
    (require ns-sym :reload))
  (.doFilter chain rqst resp))
