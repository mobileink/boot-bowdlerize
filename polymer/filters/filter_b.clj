(ns filter-b
  (:import (javax.servlet Filter FilterChain FilterConfig
                          ServletRequest ServletResponse))
  (:require [ns-tracker.core :refer :all]))

(defn -init [^Filter this ^FilterConfig cfg]
  (println "filter-b init invoked"))

(defn -destroy [^Filter this]
  (println "filter-b destroy invoked"))

(def modified-namespaces (ns-tracker ["./"]))

#_(defn make-dofilter-method
  "Turns a handler into a function that takes the same arguments and has the
  same return value as the doFilter method in the servlet.Filter class."
  [handler]
  (fn [^Filter this
       ^HttpServletRequest request
       ^HttpServletResponse response
       ^FilterChain filter-chain]
    (let [request-map (-> request
                          (ring/build-request-map)
                          (ring/merge-servlet-keys servlet request response))]
      (if-let [response-map (handler request-map)]
        (.doFilter
         filter-chain
         request
         (update-servlet-response response response-map))
        (throw (NullPointerException. "Handler returned nil"))))))

(defn -doFilter
  [^Filter this
   ^ServletRequest rqst
   ^ServletResponse resp
   ^FilterChain chain]
  (println "inbound:  filter-b on: " (str (.getMethod rqst) " " (.getRequestURL rqst)))
  (.doFilter chain rqst resp)
  (println "outbound: filter-b on: " (str (.getMethod rqst) " " (.getRequestURL rqst))))
