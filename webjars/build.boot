;;(def +project+ 'tmp.boot-bowdlerize/jetty)
(def +version+ "0.1.0-SNAPSHOT")

(set-env!
 :asset-paths #{"resources/public"}
 :source-paths #{"src" "config"}
 :repositories {"clojars" "https://clojars.org/repo"
                "maven-central" "http://mvnrepository.com"
                "central" "http://repo1.maven.org/maven2/"
                "webjars" "http://www.webjars.org/"}
 :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                   [hiccup "1.0.5"]
                   [pandeiro/boot-http          "0.7.1-SNAPSHOT" :scope "test"]
                   [boot/core "RELEASE" :scope "provided"]
                   [boot/pod "RELEASE" :scope "provided"]
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
         '[boot.task.built-in]
         '[boot.pod :as pod]
         '[pandeiro.boot-http    :refer [serve]])

(task-options!
 serve {:handler 'hello.greeting/app}
 pom  {;;:project     +project+
       :version     +version+
       :description "boot-bowdlerize compojure example"
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build
  "build webjars sample app."
  [c clean bool "clean config - clear cache first.  default: false"
   p pkg-mgrs PKGMGRS #{kw} "only PKGMGR (:bower, :npm, :polymer, or :webjars)"
   t trace bool "trace"
   v verbose bool "verbose"]
  (comp
   (b/metaconf :pkg-mgrs pkg-mgrs :clean clean :verbose verbose)
   (sift :to-resource #{#"bowdlerize.edn$"})
   (b/install :clean clean :verbose verbose)
   (target)
   ;; (show :fileset true)
   (b/cache :verbose verbose :trace trace)
   #_(b/normalize); :verbose verbose)
   ;; (b/cache :save true :verbose verbose :trace trace)
   #_(b/config) ;; :verbose verbose)
   #_(sift :move {#"(.*\.clj$)" "WEB-INF/classes/$1"})))
