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

;; (defn- bower->uri
;;   [base bower-meta]
;;   (let [mains (-> bower-meta :latest :main)]
;;       [(str/join "/" [base (-> bower-meta :latest :name) mains])])))

(defn- bower->uris
  [base bower-meta]
  (let [mains (-> bower-meta :latest :main)]
    (if (vector? mains)
      (into [] (for [main mains]
                 (str/join "/" [base (-> bower-meta :latest :name) main])))
      [(str/join "/" [base (-> bower-meta :latest :name) mains])])))

(defn- get-cfgs
  [nx cfgs]
  ;; (println "CFGS: " nx cfgs)
  (let [res (filter #(= (:ns %) nx) cfgs)]
    ;; (println "FILTERED: " res)
    res))

(defn- normalize-configs
  [base configs]
  ;; (println "normalize-configs: " configs)
  (let [resources (filter #(symbol? (first %)) configs)
        ;; _ (println "RESOURCES: " resources)
        normresources (into [] (for [resource resources]
                                 (do (println "RESOURCE: " resource)
                                 (merge (last resource)
                                        (if (str/ends-with? (:uri (last resource)) ".js")
                                          {:js true})
                                        (if (str/ends-with? (:uri (last resource)) ".css")
                                          {:css true})
                                        {:bower-pkg "foo"
                                         :ns (namespace (first resource))
                                         :name (name (first resource))}))))
        ;; _ (println "NORMRESOURCES: " normresources)
        simples (filter #(and (not (symbol? (first %)))
                              (or (symbol? (last %)) (map? (last %)))) configs)
        normsimples (for [[k v] simples]
               (merge {:bower-pkg k}
                      (condp apply [v] symbol? {:ns (symbol (namespace v)) :name (symbol (name v))}
                             map?    v)))
        ;; _ (println "NORMSIMPLES: " normsimples)
        compounds (filter #(vector? (last %)) configs)
        ;; _ (println "COMPOUNDS: " compounds)
        normcomp (flatten (into [] (for [[pkg cfgs] compounds]
                                     (into [] (for [cfg cfgs]
                                                (do ;;(println "CFG: " cfg)
                                                (merge
                                                 (if (str/ends-with? (:uri cfg) ".js")
                                                   {:js true})
                                                 (if (str/ends-with? (:uri cfg) ".css")
                                                   {:css true})
                                                 {:bower-pkg pkg} cfg)))))))
        normed (concat normcomp normsimples normresources)
        ]
    normed))

(defn- get-config-maps
  "convert config map to data map suitable for stencil"
  [bower-base configs]
  ;; (println "get-config-maps")
  (let [nss (set (flatten (for [[k v] configs]
                            (if (string? k)
                              (cond
                                (symbol? v) (let [ns (namespace v)]
                                              (if ns (symbol ns)
                                                  (throw (Exception. "var must be namespaced"))))
                                (map? v) (:ns v)
                                (vector? v)
                                (into [] (for [item v] (:ns item)))
                                ;;FIXME: better error msg
                                :else (throw (Exception. "config val must be map or vector of maps")))
                              (if (symbol? k)
                                (namespace k)
                                (throw (Exception. "config entries must be {sym {:uri str}} or {str config}")
                              ))))))
        ;; _ (println "nss: " nss)
        normal-configs (normalize-configs bower-base configs)
        ;; _ (println "NORMAL-CONFIGS:")
        ;; _ (pp/pprint normal-configs)
        ;;now add missing uris
        config-map (into [] (for [ns-config normal-configs]
                              (if (-> ns-config :uri)
                                ns-config
                                (let [m (bower-meta (:bower-pkg ns-config))
                                      kw (keyword (-> m :latest :name))
                                      ;; _ (println "kw: " kw)
                                      poly (str/starts-with? (:bower-pkg ns-config) "Polymer")
                                      ;;_ (println "poly: " poly)
                                      uris (bower->uris bower-base m)]
                                  (merge ns-config (if (> (count uris) 1)
                                                     (throw (Exception.
                                                             (str "too many uris for bower pkg '"
                                                             (:bower-pkg ns-config)
                                                             "'; run bower info -j on the package and create a config for each latest.main entry")))
                                                     (merge
                                                      (if (str/ends-with? (first uris) ".js")
                                                        {:js true})
                                                      (if (str/ends-with? (first uris) ".css")
                                                        {:css true})
                                                      (if poly {:polymer {:kw kw}})
                                                      {:uri (first uris)})))))))]
    config-map))

(defn- ns->filestr
  [ns-str]
  (str/replace ns-str #"\.|-" {"." "/" "-" "_"}))

(defn prep-for-stencil
  [& configs]
  ;; (println "prep-for-stencil: " configs)
  (let [nss (set (map #(-> % :ns) configs))
        ;; _ (println "nss: " nss)
        res (into [] (for [ns- nss]
                       (let [ns-cfgs (filter #(= ns- (-> % :ns)) configs)]
                         ;; (println "XXXXXXXXXXXXXXXX config-ns: " ns-)
                         {:config-ns ns-
                          :config
                          (into [] (for [ns-cfg ns-cfgs]
                                     ns-cfg))})))]
    ;; (println "FOR STENCIL:")
    ;; (pp/pprint res)
    res))

(boot/deftask config
  [c nss NSS #{sym} "config namespaced sym"
   b base PATH str "bower components base path, default: bower_components"
   o outdir PATH str "install dir, default: WEB-INF/classes"]
  (let [nss   (if nss nss #_(if (namespace nss)
                              nss (throw (Exception. (str "config symbol must be namespaced"))))
                  #{'bower/config})
        base (if base base "bower_components")
        outdir   (if (nil? outdir) "WEB-INF/classes" outdir)
        tgt     (boot/tmp-dir!)
        pod-env (update-in (boot/get-env) [:dependencies] conj '[cheshire "5.5.0"])
        config-pod    (future (pod/make-pod pod-env))]
    (boot/with-pre-wrap [fileset]
      (boot/empty-dir! tgt)
      (doseq [config-sym nss]
        (let [config-ns (symbol (namespace config-sym))]
          ;; (println "CONFIG-SYM: " config-sym)
          ;; (println "CONFIG-NS: " config-ns)
          (require config-ns)
          (if (not (find-ns config-ns)) (throw (Exception. (str "can't find config ns"))))
          (let [config-var (if-let [v (resolve config-sym)]
                             v (throw (Exception. (str "can't find config var for: " config-sym))))
                configs (deref config-var)
                config-maps (get-config-maps base configs)
                ;; _ (println "config-maps: " (count config-maps))
                ;; _ (pp/pprint config-maps)
                config-maps (apply prep-for-stencil config-maps)]
            (doseq [config-map config-maps]
              (do (println "config-map for stencil: " config-map)
                (let [config-file-name (str (ns->filestr (-> config-map :config-ns)) ".clj")
                      config-file (stencil/render-file "boot_bowdlerize/bower.mustache"
                                                       config-map)
                      out-file (doto (io/file tgt (str outdir "/" config-file-name))
                                 io/make-parents)]
                  (spit out-file config-file)))))))
        #_(util/warn (format "not a bower config ns: %s\n" n))
        (-> fileset (boot/add-resource tgt) boot/commit!))))

;;FIXME: make rm an option to config?
(boot/deftask config-rm
  "remove bower config files from target"
  [n nss NSS #{sym} "config namespace"]
  ;; (println "config-rm: " nss)
  (let [nss   (if (empty? nss) #{'bower} nss)
        tgt     (boot/tmp-dir!)
        pod-env (update-in (boot/get-env) [:dependencies] conj '[cheshire "5.5.0"])
        config-pod    (future (pod/make-pod pod-env))
        ]
    (boot/with-pre-wrap [fileset]
      (let [newfs (loop [nsx nss fs fileset]
                    (if (empty? nsx)
                          fs
                          (let [;;_ (println "foo" nsx)
                                config-sym (first nsx)
                                ;; _ (println "config-sym: " config-sym)
                                config-ns (symbol (namespace config-sym))
                                ;; _ (println "config-ns: " config-ns)
                                ]
                            (require config-ns)
                            (if (not (find-ns config-ns))
                              (throw (Exception. (str "can't find config ns"))))
                            (let [bower-file-path (str (ns->filestr config-ns) ".clj")
                                  bower-file (boot/tmp-get fs bower-file-path)]
                              (recur (rest nsx)
                                     (boot/rm fs [bower-file]))))))]
        (boot/commit! newfs)))))

(boot/deftask install
  [n nss NSS #{sym} "config namespace"
   o outdir PATH str "install dir, default: WEB-INF/classes"]
  ;;(let [nss   (if (empty? nss) #{'bower} nss)
  (let [nss   (if nss nss #_(if (namespace nss)
                              nss (throw (Exception. (str "config symbol must be namespaced"))))
                  #{'bower/config})
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
      (doseq [config-sym nss]
        (let [config-ns (symbol (namespace config-sym))]
          ;; (println "CONFIG-SYM: " config-sym)
          ;; (println "CONFIG-NS: " config-ns)
          (require config-ns)
          (if (not (find-ns config-ns)) (throw (Exception. (str "can't find config ns"))))
          ;;(if (:bower (meta (find-ns config-ns)))
          (let [config-var (if-let [v (resolve config-sym)]
                             v (throw (Exception. (str "can't find config var for: " config-sym))))
                configs (deref config-var)
                bower-pkgs (filter #(string? %) (keys configs))
                ;; _ (println "bower-pkgs: " bower-pkgs)
                ;; bower-pkgs (keys (deref (resolve bower-sym)))
                ]
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
