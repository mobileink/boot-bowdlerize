(def +project+ 'tmp.boot-bowdlwerize/compojure)
(def +version+ "0.1.0-SNAPSHOT")

(set-env!
 :asset-paths #{"resources/public" "target"}
 :resource-paths #{"resources/public" "config"}
 :source-paths #{"src" "config" "target"}
;; :target-path "target"
 :repositories {"clojars" "https://clojars.org/repo"
                "maven-central" "http://mvnrepository.com"
                "central" "http://repo1.maven.org/maven2/"}
 :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                   [hiccup "1.0.5"]
                   [pandeiro/boot-http          "0.7.1-SNAPSHOT" :scope "test"]
                   [boot/core "2.5.2" :scope "provided"]
                   [mobileink/boot-bowdlerize "0.1.0-SNAPSHOT" :scope "test"]
                   [compojure/compojure "1.4.0"]
                   [ring/ring-core "1.4.0"]
                   [ring/ring-devel "1.4.0"]
                   [ring/ring-servlet "1.4.0"]
                   [ring/ring-defaults "0.1.5"]
                   [ns-tracker/ns-tracker "0.3.0"]
                   ])

(require '[boot-bowdlerize :as b]
         '[boot.task.built-in :as builtin]
         '[pandeiro.boot-http    :refer [serve]])

(def configs #{'resources/styles 'bower/config-map})

(task-options!
 serve {:handler 'compojure.handler/app}
 b/config {:nss configs :outdir "./"}
 b/config-rm {:nss configs}
 b/install {:nss configs}
 pom  {:project     +project+
       :version     +version+
       :description "boot-bowdlerize compojure example"
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build
  "build compojure sample app"
  []
  (comp (b/install) (b/config) (b/config-rm) (target)))
