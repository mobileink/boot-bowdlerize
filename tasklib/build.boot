(def project 'mobileink/boot-bowdlerize)
(def version "0.1.0-SNAPSHOT")

(set-env! :resource-paths #{"src"}
          ;; :source-paths   #{"test"}
          :dependencies   '[[org.clojure/clojure "RELEASE"]
                            [cheshire "5.5.0"]
                            [stencil "0.5.0"]
                            [boot "RELEASE" :scope "provided"]
                            [boot/aether "RELEASE"]
                            [adzerk/boot-test "RELEASE" :scope "test"]])

(task-options!
 pom {:project     project
      :version     version
      :description "FIXME: write description"
      :url         "http://example/FIXME"
      :scm         {:url "https://github.com/yourname/boot-bowdlerize"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build
  "Build and install the project locally."
  []
  (comp (pom) (jar) (install)))

(require '[adzerk.boot-test :refer [test]]
         '[boot-bowdlerize :as b])
