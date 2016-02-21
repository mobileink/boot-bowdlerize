(ns boot-bowdlerize
  "Example tasks showing various approaches."
  {:boot/export-tasks true}
  (:require [boot.core :as boot]
            [boot.pod :as pod]
            [boot.util :as util]
            [stencil.core :as stencil]
            [cheshire.core :as json]
            [clojure.java.shell :refer [sh]]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; https://github.com/bower/spec/blob/master/config.md
;;TODO, maybe: accept clojure map for .bowerrc, bower.json

(defn- config-file?
  [f]
  #_(println "config-file? " (.getName f))
  #_(let [fns (load-file (.getName f))]
    )
  f)

(defn bower-meta
  [pkg]
  (let [local-bower  (io/as-file "./node_modules/bower/bin/bower")
        global-bower (io/as-file "/usr/local/bin/bower")
        shcmd        (cond (.exists local-bower) (.getPath local-bower)
                           (.exists global-bower) (.getPath global-bower)
                           :else "bower")
        bcmd "install"
        tgt     (boot/tmp-dir!)
        pod-env (update-in (boot/get-env) [:dependencies] conj '[cheshire "5.5.0"])
        bower-pod    (future (pod/make-pod pod-env))
        ;;c [shcmd bcmd "-j" pkg  :dir (.getPath tgt)]
        ;; (println "sh cmd: " c)
        ;; (pod/with-eval-in @bower-pod
        ;;   (require '[clojure.java.shell :refer [sh]]
        ;;            '[clojure.java.io :as io]
        ;;            '[clojure.string :as str]
        ;;            '[cheshire.core :as json])
        info (sh shcmd "info" "-j" pkg)
        j (json/parse-string (:out info) true)
        ]
    j))

(defn- bower->uri
  [bower-meta]
  (str
       (str/join "/" ["bower_components"
                      (-> bower-meta :latest :name)
                      (-> bower-meta :latest :main)])))

(defn- get-config-maps
  "convert config map to data map suitable for stencil"
  [configs]
  (let [nss (set (for [[k v] configs] (:ns v)))
        ns-configs (into [] (for [ns- nss]
                              (let [ns-config (filter #(= (:ns (val %)) ns-) configs)]
                                [ns- (into {} ns-config)])))
        config-map (into [] (for [ns-config ns-configs]
                              (let [k (first ns-config)
                                    syms (last ns-config)]
                                {:ns k
                                 :config (into [] (for [[bower-pkg config] syms]
                                                    (let [m (bower-meta bower-pkg)
                                                          uri (bower->uri m)]
                                                       (merge {:bower-pkg bower-pkg}
                                                              (assoc config :uri uri)))))})))]
    config-map))

(defn- ns->filestr
  [ns-str]
  (str/replace ns-str #"\.|-" {"." "/" "-" "_"}))

(boot/deftask config
  [n nss NSS #{sym} "config namespace"
   o outdir PATH str "install dir, default: WEB-INF/classes"]
;;   d directory DIR str "bower components dir, default: bower_components"]
  (let [nss   (if (empty? nss) #{'bower} nss)
        outdir   (if (nil? outdir) "WEB-INF/classes" outdir)
        tgt     (boot/tmp-dir!)
        pod-env (update-in (boot/get-env) [:dependencies] conj '[cheshire "5.5.0"])
        config-pod    (future (pod/make-pod pod-env))]
    (boot/with-pre-wrap [fileset]
      (boot/empty-dir! tgt)
      (doseq [n nss]
        (require n)
        (if (:bower (meta (find-ns n)))
          (let [bower-sym (symbol (str (ns-name n)) "bower")
                configs (deref (resolve bower-sym))
                config-maps (get-config-maps configs)]
            (doseq [config-map config-maps]
              (let [config-file-name (str (ns->filestr (:ns config-map)) ".clj")
                    config-file (stencil/render-file "boot_bowdlerize/bower.mustache"
                                                     config-map)
                    out-file (doto (io/file tgt (str outdir "/" config-file-name))
                               io/make-parents)]
                (spit out-file config-file))))
          (util/warn (format "not a bower config ns: %s\n" n))))
      (-> fileset (boot/add-resource tgt) boot/commit!))))

;;FIXME: make rm an option to config?
(boot/deftask config-rm
  "remove bower config files from target"
  [n nss NSS #{sym} "config namespace"]
  (let [nss   (if (empty? nss) #{'bower} nss)
        tgt     (boot/tmp-dir!)
        pod-env (update-in (boot/get-env) [:dependencies] conj '[cheshire "5.5.0"])
        config-pod    (future (pod/make-pod pod-env))
        ]
    (boot/with-pre-wrap [fileset]
      (let [newfs (loop [nsx nss fs fileset]
                    (if (empty? nsx)
                          fs
                          (let [firstns (first nsx)]
                            (require firstns)
                            (let [nx (find-ns firstns)]
                              (if (:bower (meta nx))
                                (let [bower-file-path (str (ns->filestr (first nsx)) ".clj")
                                      bower-file (boot/tmp-get fs bower-file-path)]
                                  (recur (rest nsx) (boot/rm fs [bower-file])))
                                (recur (rest nsx) fs))))))]
                    (boot/commit! newfs)))))

(boot/deftask install
  [n nss NSS #{sym} "config namespace"
   o outdir PATH str "install dir, default: WEB-INF/classes"]
  (let [nss   (if (empty? nss) #{'bower} nss)
        outdir   (if (nil? outdir) "WEB-INF/classes" outdir)
        local-bower  (io/as-file "./node_modules/bower/bin/bower")
        global-bower (io/as-file "/usr/local/bin/bower")
        shcmd        (cond (.exists local-bower) (.getPath local-bower)
                           (.exists global-bower) (.getPath global-bower)
                           :else "bower")
        tgt     (boot/tmp-dir!)
        pod-env (update-in (boot/get-env) [:dependencies] conj '[cheshire "5.5.0"])
        bower-pod    (future (pod/make-pod pod-env))]
    (boot/with-pre-wrap [fileset]
      (boot/empty-dir! tgt)
      (doseq [n nss]
        (require n)
        (if (:bower (meta (find-ns n)))
          (let [bower-sym (symbol (str (ns-name n)) "bower")
                bower-pkgs (keys (deref (resolve bower-sym)))]
            (doseq [pkg bower-pkgs]
              (let [c [shcmd "install" "-j" pkg :dir (.getPath tgt)]]
                (pod/with-eval-in @bower-pod
                  (require '[clojure.java.shell :refer [sh]]
                           '[clojure.java.io :as io]
                           '[clojure.string :as str]
                           '[cheshire.core :as json])
                  (sh ~@c)))))))
      (-> fileset (boot/add-resource tgt) boot/commit!))))

(boot/deftask info
  [p package PACKAGE str "bower package"
   n cname NAME str "clojure name for package"
   d directory DIR str "bower components dir, default: bower_components"]
  (let [local-bower  (io/as-file "./node_modules/bower/bin/bower")
        global-bower (io/as-file "/usr/local/bin/bower")
        shcmd        (cond (.exists local-bower) (.getPath local-bower)
                           (.exists global-bower) (.getPath global-bower)
                           :else "bower")
        bcmd "install"
        tgt     (boot/tmp-dir!)
        pod-env (update-in (boot/get-env) [:dependencies] conj '[cheshire "5.5.0"])
        bower-pod    (future (pod/make-pod pod-env))
        ]
;; (println "sh cmd: " shcmd)
;; (println "bower cmd: " bcmd)
    ;; (println "pkg: " package)
    (boot/with-pre-wrap [fileset]
      (boot/empty-dir! tgt)
      (let [c [shcmd bcmd "-j" package  :dir (.getPath tgt)]]
        ;; (println "sh cmd: " c)
        (pod/with-eval-in @bower-pod
          (require '[clojure.java.shell :refer [sh]]
                   '[clojure.java.io :as io]
                   '[clojure.string :as str]
                   '[clojure.pprint :as pp]
                   '[cheshire.core :as json])
          (let [info (sh ~shcmd "info" "-j" ~package)
                j (json/parse-string (:out info) true)
                uri (str/join "/" ["bower_components" (-> j :latest :name) (-> j :latest :main)])
                ]
            (pp/pprint j))))
      fileset)))

(boot/deftask post
  "I'm a post-wrap task."
  []
  ;; merge environment etc
  (println "Post-wrap task setup.")
  (boot/with-post-wrap fs
    (println "Post-wrap: Next task will run. Then we will run functions on its result (fs).")))

(boot/deftask pass-thru
  "I'm a pass-thru task."
  []
  ;; merge environment etc
  (println "Pass-thru task setup.")
  (boot/with-pass-thru fs
    (println "Pass-thru: Run functions on filesystem (fs). Next task will run with the same fs.")))
