(ns styles.shared)

(alter-meta! *ns*
             (fn [m] (assoc m
                            :co-ns true
                            :resource-type :polymer-style-module)))

(def pfx "styles")

(def uri "shared.html")

;; dom-modules in styles/shared.html

(def psk "psk-style")

(def psk "shared-styles")
