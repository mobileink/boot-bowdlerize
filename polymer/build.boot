;;(def +project+ 'tmp.boot-bowdlwerize/jetty)
(def +version+ "0.1.0-SNAPSHOT")

(set-env!
 :gae {:app-id "boot-polymer"
       :version "0-1-0-SNAPSHOT"}
 :asset-paths #{"resources/public"}
 :source-paths #{"src/clj" "config" "filters"}
 :repositories {"clojars" "https://clojars.org/repo"
                "maven-central" "http://mvnrepository.com"
                "central" "http://repo1.maven.org/maven2/"}
 :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                   [hiccup "1.0.5"]
                   [pandeiro/boot-http          "0.7.1-SNAPSHOT" :scope "test"]
                   [boot/core "RELEASE" :scope "provided"]
                   [boot/pod "RELEASE" :scope "provided"]
                   [mobileink/boot-bowdlerize "0.1.0-SNAPSHOT" :scope "test"]
                   [migae/boot-gae "0.1.0-SNAPSHOT" :scope "test"]
                   [com.google.appengine/appengine-java-sdk "1.9.32"
                    :scope "provided" :extension "zip"]
                   [com.google.appengine/appengine-tools-sdk "1.9.32"]
                   [compojure/compojure "1.4.0"]
                   [ring/ring-core "1.4.0"]
                   [ring/ring-devel "1.4.0"]
                   [ring/ring-servlet "1.4.0"]
                   [ring/ring-defaults "0.1.5"]
                   [ns-tracker/ns-tracker "0.3.0"]
                   ])

(require '[boot-bowdlerize :as b]
         '[migae.boot-gae :as gae]
         '[boot.task.built-in :as builtin]
         '[boot.pod :as pod]
         '[pandeiro.boot-http    :refer [serve]])

;; (def configs #{'resources/scripts
;;                'resources/statics
;;                'resources/styles
;;                'polymer/config-map
;;                'bower/config-map})

(task-options!
 serve {:handler 'hello.greeting/app}
 ;; b/config {:config-syms configs}
 ;; b/config-rm {:config-syms configs}
 ;; b/install {:config-syms configs}
 pom  {;;:project     +project+
       :version     +version+
       :description "boot-bowdlerize polymer example"
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

#_(deftask dev
  "build with boot-gae and boot-bowdlerize"
  [k keep bool  "keep meta-config source files (copy to target)"]
  (comp
   ;; (b/bower)
   (b/polymer)
   (b/resources)
   ;; (b/install)
   (b/config)
   (gae/dev :keep keep)
   (target)))

