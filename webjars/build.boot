;;(def +project+ 'tmp.boot-bowdlwerize/jetty)
(def +version+ "0.1.0-SNAPSHOT")

(set-env!
;; :asset-paths #{"target"}
 :resource-paths #{"resources/public" "target/classes"}
 :source-paths #{"src" "config" "target/classes"}
 :repositories {"clojars" "https://clojars.org/repo"
                "maven-central" "http://mvnrepository.com"
                "central" "http://repo1.maven.org/maven2/"
                "webjars" "http://www.webjars.org/"}
 :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                   [hiccup "1.0.5"]
                   [pandeiro/boot-http          "0.7.1-SNAPSHOT" :scope "test"]
                   [boot/core "2.5.5" :scope "provided"]
                   [boot/pod "2.5.5" :scope "provided"]
                   [mobileink/boot-bowdlerize "0.1.0-SNAPSHOT" :scope "test"]

                   ;; [org.webjars.bower/github-com-PolymerElements-paper-button "1.0.11"]
                   ;; [org.webjars.bower/github-com-PolymerElements-paper-card "1.0.8"]
                   ;; [org.webjars.bower/github-com-polymerelements-iron-icon "1.0.7"]
                   ;; [org.webjars.bower/github-com-polymerelements-iron-icons "1.0.5"]

                   [compojure/compojure "1.4.0"]
                   [ring/ring-core "1.4.0"]
                   [ring/ring-devel "1.4.0"]
                   [ring/ring-servlet "1.4.0"]
                   [ring/ring-defaults "0.1.5"]
                   [ns-tracker/ns-tracker "0.3.0"]
                   ])

(require '[boot-bowdlerize :as b]
         '[boot.task.built-in :as builtin]
         '[boot.pod :as pod]
         '[pandeiro.boot-http    :refer [serve]])

(def configs #{
               'polymer/webjar-config
               ;; 'resources/scripts
               ;; 'resources/statics
               ;; 'resources/styles
               ;; 'polymer/config-map
               ;; 'bower/config-map
               })

(task-options!
 serve {:handler 'hello.greeting/app}
 b/config {:config-syms configs}
 b/config-rm {:config-syms configs}
 b/install {:config-syms configs}
 b/show {:config-syms configs}
 b/webjars {:config-syms configs}
 pom  {;;:project     +project+
       :version     +version+
       :description "boot-bowdlerize compojure example"
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build
  "build compojure sample app."
  []
  (comp
   (b/install) (b/config-rm) (b/config) (target)))

(deftask rebuild
  "build compojure sample app.  run b/install once first"
  []
  (comp
   (b/config-rm) (b/config) (target :no-clean true)))

