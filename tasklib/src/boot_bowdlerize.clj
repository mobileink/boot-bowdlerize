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
        ;; _ (println "bower-meta: " pkg)
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
  (let [bowers (filter #(:bower %) configs)
        ;; _ (println "BOWERS: " bowers)
        normbowers (flatten (into [] (for [bower bowers]
                              (if (map? (:runtime bower))
                                (into [] (for [[sym uri] (:runtime bower)]
                                           (do ;; (println "FOO: "bower)
                                             (merge bower
                                                    {:runtime sym
                                                     :uri uri
                                                     :ns (namespace sym)
                                                     :name (name sym)}))))
                                (do ;; (println "BAR: " bower)

                                (let [m (bower-meta (:bower bower))
                                      kw (keyword (-> m :latest :name))
                                      ;; _ (println "kw: " kw)
                                      poly (str/starts-with? (:bower bower) "Polymer")
                                      ;; _ (println "poly: " poly)
                                      uris (bower->uris base m)]
                                  (merge bower (if (> (count uris) 1)
                                                 (throw (Exception.
                                                         (str "too many uris for bower pkg '"
                                                              (:bower bower)
                                                              "'; run bower info -j on the package and create a config for each latest.main entry"))))
                                         {:ns (namespace (:runtime bower))
                                          :name (name (:runtime bower))}
                                         ;; (if (str/ends-with? (first uris) ".js")
                                         ;;   {:js true})
                                         ;; (if (str/ends-with? (first uris) ".css")
                                         ;;   {:css true})
                                         (if poly {:polymer {:kw kw}})
                                         {:uri (first uris)})))))))

                                    ;; #_(merge bower
                                    ;;        {:ns (namespace (:runtime bower))
                                    ;;         :name (name (:runtime bower))})
        ;; _ (println "NORMBOWERS: " normbowers)

        resources (filter #(not (:bower %)) configs)
        ;; _ (println "RESOURCES: " resources)

        normresources (into [] (for [resource resources]
                                 (do ;; (println "RESOURCE: " resource)
                                     (merge resource
                                            ;; (if (str/ends-with? (:uri resource) ".js")
                                            ;;   {:js true})
                                            ;; (if (str/ends-with? (:uri resource) ".css")
                                            ;;   {:css true})
                                            {:ns (namespace (:runtime resource))
                                             :name (name (:runtime resource))}))))
        ;; _ (println "NORMRESOURCES: " normresources)

        simples (filter #(and (not (symbol? (first %)))
                              (or (symbol? (last %)) (map? (last %)))) configs)
        normsimples (for [[k v] simples]
               (merge {:bower k}
                      (condp apply [v] symbol? {:ns (symbol (namespace v)) :name (symbol (name v))}
                             map?    v)))
        ;; _ (println "NORMSIMPLES: " normsimples)

        compounds (filter #(vector? (:runtime %)) configs)
        ;; _ (println "COMPOUNDS: " compounds)
        normcomp (flatten (into [] (for [[pkg cfgs] compounds]
                                     (into [] (for [cfg cfgs]
                                                (do ;;(println "CFG: " cfg)
                                                (merge
                                                 ;; (if (str/ends-with? (:uri cfg) ".js")
                                                 ;;   {:js true})
                                                 ;; (if (str/ends-with? (:uri cfg) ".css")
                                                 ;;   {:css true})
                                                 {:bower pkg} cfg)))))))
        ;; _ (println "NORMCOMPOUNDS: " normcomp)

        normed (concat normcomp normsimples normresources normbowers)
        ;; _ (println "NORMED: " normed)
        ]
    normed))

(defn- get-config-maps
  "convert config map to data map suitable for stencil"
  [bower-base configs]
  ;; (println "get-config-maps: " configs)
  (let [
        ;; nss (set (flatten (for [[k v] configs]
        ;;                     (if (string? k)
        ;;                       (cond
        ;;                         (symbol? v) (let [ns (namespace v)]
        ;;                                       (if ns (symbol ns)
        ;;                                           (throw (Exception. "var must be namespaced"))))
        ;;                         (map? v) (:ns v)
        ;;                         (vector? v)
        ;;                         (into [] (for [item v] (:ns item)))
        ;;                         ;;FIXME: better error msg
        ;;                         :else (throw (Exception. "config val must be map or vector of maps")))
        ;;                       (if (symbol? k)
        ;;                         (namespace k)
        ;;                         (throw (Exception. "config entries must be {sym {:uri str}} or {str config}")
        ;;                       ))))))
        ;; _ (println "nss: " nss)
        normal-configs (normalize-configs bower-base configs)
        ;; _ (println "NORMAL-CONFIGS:")
        ;; _ (pp/pprint normal-configs)
        ;;now add missing uris
        config-map (into [] (for [ns-config normal-configs]
                              (do ;; (println "ns-config: " ns-config)
                              (if (-> ns-config :uri)
                                ns-config
                                (let [m (bower-meta (:bower ns-config))
                                      kw (keyword (-> m :latest :name))
                                      ;; _ (println "kw: " kw)
                                      poly (str/starts-with? (:bower ns-config) "Polymer")
                                      ;; _ (println "poly: " poly)
                                      uris (bower->uris bower-base m)]
                                  (merge ns-config (if (> (count uris) 1)
                                                     (throw (Exception.
                                                             (str "too many uris for bower pkg '"
                                                             (:bower ns-config)
                                                             "'; run bower info -j on the package and create a config for each latest.main entry")))
                                                     (merge
                                                      ;; (if (str/ends-with? (first uris) ".js")
                                                      ;;   {:js true})
                                                      ;; (if (str/ends-with? (first uris) ".css")
                                                      ;;   {:css true})
                                                      (if poly {:polymer {:kw kw}})
                                                      {:uri (first uris)}))))))))]
    ;; (println "xxxx config-map: " config-map)
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
                       (do ;; (println "ns- " ns-)
                       (let [ns-cfgs (filter #(= ns- (-> % :ns)) configs)]
                         ;; (println "XXXXXXXXXXXXXXXX config-ns: " ns-)
                         {:config-ns ns-
                          :config
                          (into [] (for [ns-cfg ns-cfgs]
                                     ns-cfg))}))))]
    ;; (println "FOR STENCIL:")
    ;; (pp/pprint res)
    res))

(defn- merge-config-maps
  [ms]
  ;; (println "merge-config-maps: " ms)
  (let [ks (set (map #(symbol (:config-ns %)) ms))
        merged-maps (into []
                          (for [k (seq ks)]
                            (let [specs (filter #(= k (symbol (:config-ns %))) ms)
                                  bodies (flatten (into [] (map #(:config %) specs)))]
                              {:config-ns k
                               :config (vec bodies)})))]
    ;; (println "MERGED MAPS:")
    ;; (pp/pprint merged-maps)
    merged-maps))

(defn- typify
  [config-maps]
  (for [cfg config-maps]
    (do ;;(println "TYPIFY: " cfg)
        (merge cfg
               {:config (for [config (:config cfg)]
                          (merge config
                                 (if (str/ends-with? (:uri config) ".js")
                                   {:js true})
                                 (if (str/ends-with? (:uri config) ".css")
                                   {:css true})))}))))

(boot/deftask config
  [c config-syms CONFIG-SYMS #{sym} "config namespaced sym"
   b base PATH str "bower components base path, default: bower_components"
   o outdir PATH str "install dir, default: classes"]
  (let [config-syms   (if config-syms config-syms #_(if (namespace config-syms)
                              config-syms (throw (Exception. (str "config symbol must be namespaced"))))
                  #{'bower/config})
        base (if base base "bower_components")
        outdir   (if (nil? outdir) "classes" outdir)
        tgt     (boot/tmp-dir!)
        pod-env (update-in (boot/get-env) [:dependencies] conj '[cheshire "5.5.0"])
        config-pod    (future (pod/make-pod pod-env))]
    (boot/with-pre-wrap [fileset]
      (boot/empty-dir! tgt)
      (let [config-maps
            (into []
                  (flatten
                   (for [config-sym config-syms]
                        (let [config-ns (symbol (namespace config-sym))]
                          ;; (println "CONFIG-NS: " config-ns)
                          (require config-ns)
                          ;; (doseq [[ivar isym] (ns-interns config-ns)] (println "interned: " ivar isym))
                          (if (not (find-ns config-ns)) (throw (Exception. (str "can't find config ns"))))
                          (let [config-var (if-let [v (resolve config-sym)]
                                             v (throw
                                                (Exception.
                                                 (str "can't find config var for: " config-sym))))
                                configs (deref config-var)
                                config-specs (get-config-maps base configs)
                                ;; _ (pp/pprint config-specs)
                                config-specs (apply prep-for-stencil config-specs)
                                ;; _ (println (format "config-specs for ns '%s: %s"
                                ;;                    config-ns (count config-specs)))
                                ]
                            config-specs)))))
            ;; _ (println "CONFIG-MAPS: " config-maps)
            config-maps (merge-config-maps config-maps)
            config-maps (typify config-maps)]
        ;; (println "CONFIG-MAPS: " (count config-maps))
        ;; (pp/pprint config-maps)
        (doseq [config-map config-maps]
            (let [config-file-name (str outdir "/" (ns->filestr (-> config-map :config-ns)) ".clj")
                  ;; _ (println "writing: " config-file-name)
                  config-file (stencil/render-file "boot_bowdlerize/bower.mustache"
                                                   config-map)
                  out-file (doto (io/file tgt config-file-name)
                             io/make-parents)]
              (spit out-file config-file))))
      (-> fileset (boot/add-resource tgt) boot/commit!))))

;;FIXME: make rm an option to config?
(boot/deftask config-rm
  "remove bower config files from target"
  [c config-syms CONFIG-SYMS #{sym} "config namespaced sym"]
  ;; [n nss NSS #{sym} "config namespace"]
  ;; (println "config-rm: " nss)
  (let [config-syms   (if (empty? config-syms) #{'bower} config-syms)
        tgt     (boot/tmp-dir!)
        pod-env (update-in (boot/get-env) [:dependencies] conj '[cheshire "5.5.0"])
        config-pod    (future (pod/make-pod pod-env))
        ]
    (boot/with-pre-wrap [fileset]
      (let [newfs
            (loop [nsx (set (map #(symbol (namespace %)) config-syms)) fs fileset]
              (if (empty? nsx)
                fs
                (let [;;_ (println "foo" nsx)
                      config-sym (first nsx)
                      ;; _ (println "config-sym: " config-sym)
                      config-ns config-sym
                      ;; _ (println "config-ns: " config-ns (type config-ns))
                      ]
                  (require config-ns)
                  (if (not (find-ns config-ns))
                    (throw (Exception. (str "can't find config ns"))))
                  (let [bower-file-path (str (ns->filestr config-ns) ".clj")
                        bower-file (boot/tmp-get fs bower-file-path)]
                    ;; (println "removing: " (str config-sym))
                    (recur (rest nsx)
                           (boot/rm fs [bower-file]))))))]
        (boot/commit! newfs)))))

(boot/deftask install
  [n config-syms CONFIG-SYMS #{sym} "config namespace"
   o outdir PATH str "install dir, default: bower_components"]
  ;;(let [config-syms   (if (empty? config-syms) #{'bower} config-syms)
  (let [config-syms   (if config-syms config-syms #_(if (namespace config-syms)
                              config-syms (throw (Exception. (str "config symbol must be namespaced"))))
                  #{'bower/config})
        outdir   (if (nil? outdir) "bower_components" outdir)
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
      (doseq [config-sym config-syms]
        (let [config-ns (symbol (namespace config-sym))]
          (require config-ns)
          (if (not (find-ns config-ns)) (throw (Exception. (str "can't find config ns"))))
          (println "CONFIG-NS 2: " config-ns)
          ;; (doseq [[isym ivar] (ns-interns config-ns)] (println "ISYM2: " isym ivar))
          (let [config-var (if-let [v (resolve config-sym)]
                             v (throw (Exception. (str "can't find config var for: " config-sym))))
                _ (println "config-var: " config-var)
                configs (deref config-var)
                _ (println "configs: " configs)

                bower-pkgs (filter #(:bower %) configs)
                _ (println "bower-pkgs: " bower-pkgs)
                ;; bower-pkgs (keys (deref (resolve bower-sym)))
                ]
            (doseq [pkg bower-pkgs]
              (let [c [shcmd "install" "-j" (:bower pkg) :dir (.getPath tgt)]]
                (println "bower cmd: " c)
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
