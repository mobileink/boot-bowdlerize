(ns filter-a
  (:import (javax.servlet Filter FilterChain FilterConfig
                          ServletRequest ServletResponse))
  (:require [ns-tracker.core :refer :all]))

(defn -init [^Filter this ^FilterConfig cfg]
  (println "filter-a init invoked"))

(defn -destroy [^Filter this]
  (println "filter-a destroy invoked"))

(def modified-namespaces (ns-tracker ["./"]))

(defn -doFilter
  [^Filter this
   ^ServletRequest rqst
   ^ServletResponse resp
   ^FilterChain chain]
  (println "inbound:  filter-a on: " (str (.getMethod rqst) " " (.getRequestURL rqst)))
  (.doFilter chain rqst resp)
  (println "outbound: filter-a on: " (str (.getMethod rqst) " " (.getRequestURL rqst)))
  )
