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
                   ;; [miraj/boot-miraj "0.1.0-SNAPSHOT" :scope "test"]
                   ;; [miraj/html "5.1.0-SNAPSHOT"]
                   ;; [miraj/markup "0.1.0-SNAPSHOT"]
                   ;; [miraj/iron "1.2.3-SNAPSHOT"]
                   ;; [components/greetings "0.1.0-SNAPSHOT"]
                   ;; [boot/core "2.5.2" :scope "provided"]
                   ;; [adzerk/boot-test "1.0.7" :scope "test"]
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
   (b/config)
   (gae/dev :keep keep)
   ;; (gae/install-sdk)
   ;; (gae/libs)
   ;; (gae/logging)
   ;; (gae/config)
   ;; (gae/servlets)
   ;; this step marks all .clj files as +INPUT,+OUTPUT if keep is true
   ;; (if keep
   ;;   (sift :to-resource #{#"\.clj$"})
   ;;   identity)
   ;; ;; the following sift :move will move all .clj files, including
   ;; ;; the meta-config files in config/
   ;; ;; but only those marked +OUTPUT will be written to
   ;; ;; the target dir by the target task
   ;; ;; the config/ dir is in :source-paths, which marks
   ;; ;; its files -OUTPUT, so (unless 'keep' is true, see above)
   ;; ;; they will be moved but not written
   ;; (sift :move {#"(.*\.clj$)" "WEB-INF/classes/$1"})
   (target)))
