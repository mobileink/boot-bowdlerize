(def +project+ 'mobileink/gae-bower)
(def +version+ "0.1.0-SNAPSHOT")

(set-env!
 :gae {:app-id "gae-bower"
       :version "0-1-0-SNAPSHOT"}
 :asset-paths #{"resources/public"}
 :source-paths #{"config" "src/clj" "filters"}
 :repositories {"clojars" "https://clojars.org/repo"
                "maven-central" "http://mvnrepository.com"
                "central" "http://repo1.maven.org/maven2/"}
 :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                   [boot/core "2.5.5" :scope "provided"]
                   [mobileink/boot-bowdlerize "0.1.0-SNAPSHOT" :scope "test"]
                   [migae/boot-gae "0.1.0-SNAPSHOT" :scope "test"]
                   [com.google.appengine/appengine-java-sdk "1.9.32"
                    :scope "provided" :extension "zip"]
                   ;; we need this so we can import KickStart:
                   [com.google.appengine/appengine-tools-sdk "1.9.32"]
                   [javax.servlet/servlet-api "2.5"]
                   [compojure/compojure "1.4.0"]
                   [ring/ring-core "1.4.0"]
                   [ring/ring-devel "1.4.0"]
                   [ring/ring-servlet "1.4.0"]
                   [ring/ring-defaults "0.1.5"]
                   [ns-tracker/ns-tracker "0.3.0"]
                   ])

(require '[migae.boot-gae :as gae]
         '[boot-bowdlerize :as b]
         '[boot.task.built-in])

(def configs #{'resources/scripts
               'resources/styles
               'bower/config-map})

(task-options!
 b/config {:config-syms configs}
 b/install {:config-syms configs}
 pom  {:project     +project+
       :version     +version+
       :description "Example gae app using boot-gae and boot-bowdlerize"
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build
  "build with boot-gae and boot-bowdlerize"
  [k keep bool  "keep meta-config source files (copy to target)"]
  (comp
   (b/install)
   ;; (b/bower)
   ;; (b/polymer)
   ;; (b/resources)
   (gae/build :keep keep)
   (target)))
