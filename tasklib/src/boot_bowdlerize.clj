(ns boot-bowdlerize
  "Example tasks showing various approaches."
  {:boot/export-tasks true}
  (:require [boot.core :as boot]
            [boot.pod :as pod]
            [boot.util :as util]
            [boot.aether :as aether]
            [stencil.core :as stencil]
            [cheshire.core :as json]
            [clojure.java.shell :refer [sh]]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def web-inf-dir "WEB-INF")
(def bowdlerize-edn "bowdlerize.edn")
(def bower-components "bower_components")
(def bower-edn "bower.edn")
(def local-edn "local.edn")
(def npm-edn "npm.edn")
(def polymer-edn "polymer.edn")
(def resources-edn "resources.edn")
(def webjars-edn "webjars.edn")

;; https://github.com/bower/spec/blob/master/config.md
;;TODO, maybe: accept clojure map for .bowerrc, bower.json

(defn- config-file?
  [f]
  #_(println "config-file? " (.getName f))
  #_(let [fns (load-file (.getName f))]
    )
  f)

(defn ->bowdlerize-fs
  "add bowdlerize-edn to fs if not already there"
  [fileset verbose]
  (let [tmp-dir (boot/tmp-dir!)
        bowdlerize-fs (->> fileset
                           boot/input-files
                           (boot/by-name [bowdlerize-edn]))
        f (condp = (count bowdlerize-fs)
                 0 (let [_ (if verbose (util/info (str "Creating bowdlerize.edn\n")))
                         f (io/file tmp-dir bowdlerize-edn)]
                     (spit f {})
                     f)
                 1 (boot/tmp-file (first bowdlerize-fs))
                 (throw (Exception. (str "only one " bowdlerize-edn " file allowed"))))]
    [f (-> fileset (boot/add-resource tmp-dir) boot/commit!)]))

(defn- ->bowdlerize-content
  [fileset verbose]
  (let [[bowdlerize-f newfs] (->bowdlerize-fs fileset verbose)]
    [fileset bowdlerize-f (-> bowdlerize-f slurp read-string)]))

(defn- cache-bowdlerize
  [fileset clean verbose]
  (let [cache-dir (boot/cache-dir! :bowdlerize/bowdlerize :global true)]
    (if clean (do (if verbose (util/info (str "Clearing bowdlerize cache\n")))
                  (boot/empty-dir! cache-dir)
                  (boot/commit! fileset)))
    (let [bowdlerize-f (io/file (.getPath cache-dir) bowdlerize-edn)]
      (if (.exists bowdlerize-f)
        (do #_(if verbose (util/info (str "Found cached " bowdlerize-edn "\n")))
            [(-> fileset (boot/add-source cache-dir) boot/commit!)
             bowdlerize-f (-> bowdlerize-f slurp read-string)])
        (let [[bowdlerize-f newfs] (->bowdlerize-fs fileset verbose)]
          [fileset bowdlerize-f (-> bowdlerize-f slurp read-string)])))))

(defn ->bowdlerize-pod
  [fileset]
  (let [edn-fs (->> fileset
                    boot/input-files
                    (boot/by-name [bowdlerize-edn]))
        _ (condp = (count edn-fs)
            0 (throw (Exception. (str bowdlerize-edn " file not found")))
            1 true
            (throw (Exception. (str "only one " bowdlerize-edn " file allowed"))))
        edn-f (boot/tmp-file (first edn-fs))
        edn-content (-> edn-f slurp read-string)
        coords (if-let [webjars (:webjars edn-content)]
                 (->> webjars vals (reduce merge) vals)
                 '())
        pod-env (update-in (boot/get-env) [:dependencies] #(identity %2)
                           (concat '[[boot/aether "2.5.5"]
                                     [boot/core "2.5.5"]
                                     [boot/pod "2.5.5"]]
                                   coords))
        pod (future (pod/make-pod pod-env))]
    [edn-content pod]))

(defn bower-meta
  [pkg]
  (let [local-bower  (io/as-file "./node_modules/bower/bin/bower")
        global-bower (io/as-file "/usr/local/bin/bower")
        bcmd        (cond (.exists local-bower) (.getPath local-bower)
                           (.exists global-bower) (.getPath global-bower)
                           :else "bower")
        tgt     (boot/tmp-dir!)
        pod-env (update-in (boot/get-env) [:dependencies] conj '[cheshire "5.5.0"])
        bower-pod    (future (pod/make-pod pod-env))
        info (sh bcmd "info" "-j" pkg)
        j (json/parse-string (:out info) true)
        ]
    j))

(defn- bower->uris
  [repo bower-meta]
  (let [mains (-> bower-meta :latest :main)]
    (if (vector? mains)
      (into [] (for [main mains]
                 (str/join "/" [repo (-> bower-meta :latest :name) main])))
      [(str/join "/" [repo (-> bower-meta :latest :name) mains])])))

(defn- get-bower-pkgs
  [specs]
  (let [bower (-> specs :bower vals)
        npm (-> specs :npm vals)
        poly (-> specs :polymer vals)
        pf (fn [p] (reduce-kv (fn [m k v]
                                (let [pkg (if (symbol? k)
                                            (cond
                                              (vector? v)
                                              ;; {icon [:iron-icon "PolymerElements/iron-icon"]}
                                              [(subs (str (first v)) 1) (last v)]

                                              (string? v)
                                              ;; {moment "moment"}
                                              [v v]

                                              (map? v)
                                              ;; {materialize
                                              ;; {:bower "materialize"
                                              ;;  :url "bower_components/Materialize/bin/materialize.css"}}
                                              [(:bower v) (:bower v)])
                                            (if (vector? k) k))]
                                  (merge m pkg)))
                              []
                              p))
        bowers (set (reduce concat (map pf bower)))
        npms (set (reduce concat (map pf npm)))
        polys (set (reduce concat (map pf poly)))
        pkgs (concat bowers npms polys)
        ]
    pkgs))

(defn get-coords
  [type edn-content]
  (condp = type
    :bower '()
    :polymer '()
    :webjars (->> edn-content vals (reduce merge) vals)))

(defn- get-cfgs
  [nx cfgs]
  (let [res (filter #(= (:ns %) nx) cfgs)]
    res))

(declare normalize-configs)

(defn- get-config-maps
  "convert config map to data map suitable for stencil"
  [bower-base configs]
  (let [normal-configs (normalize-configs bower-base configs)
        ;;now add missing uris
        config-map (into [] (for [ns-config normal-configs]
                              (do ;; (println "ns-config: " ns-config)
                              (if (-> ns-config :uri)
                                ns-config
                                (let [m (bower-meta (or (:bower ns-config) (:polymer ns-config)))
                                      kw (keyword (-> m :latest :name))
                                      ;; _ (println "kw: " kw)
                                      ;; poly (str/starts-with? (:bower ns-config) "Polymer")
                                      ;; _ (println "poly: " poly)
                                      uris (bower->uris bower-base m)]
                                  (merge ns-config (if (> (count uris) 1)
                                                     (throw (Exception.
                                                             (str "too many uris for bower pkg '"
                                                             (:bower ns-config)
                                                             "'; run bower info -j on the package and create a config for each latest.main entry")))
                                                     (merge
                                                      (if (:polymer ns-config) {:polymer {:kw kw}})
                                                      {:uri (first uris)}))))))))]
    config-map))

(defn- merge-config-maps
  [ms]
  (let [nss (set (map :ns ms)) ;;  #(symbol (:config-ns %)) ms))
        merged-maps (into []
                          (for [k (seq nss)]
                            (let [specs (filter #(= k (:ns %)) ms)
                                  bodies (flatten (into [] (map #(dissoc % :ns) specs)))]
                              {:config-ns k
                               :config (vec bodies)})))]
    merged-maps))

(defn- normalize-configs
  [configs]
  (println "NORMALIZE-CONFIGS: " configs)
  (let [foo
        (reduce-kv (fn [seed k v]
                     (assoc seed k
                            (reduce-kv (fn [sdsd kk vv]
                                         (assoc sdsd kk
                                                (assoc vv :pkg-mgr k)))
                                       {} v)))
                   {}
                   configs)
        _ (println "FOO:")
        _ (pp/pprint foo)

        ;; bowers (vals configs) ;; (concat (:bower configs) (:polymer configs))
        ;; _ (println "BOWERS: " bowers)
        ;; nss (set (flatten (map #(keys %) bowers)))
        ;; _ (println "NSS: " nss)
        ;; merged-configs (reduce (fn [seed nsx]
        ;;               (let [m (reduce (fn [sd b]
        ;;                                 (merge sd (get b nsx)))
        ;;                               {}
        ;;                               bowers)]
        ;;                 (assoc seed nsx m)))
        ;;             {}
        ;;             (sort (seq nss)))
        ;; _ (println "MERGED:")
        ;; _ (pp/pprint merged-configs)

        ;; normbowers (flatten
        ;;             (merge (for [config merged-configs]
        ;;                      (do ;;(println "CONFIG: " config)
        ;;                          {:config-ns (first config)
        ;;                           :vars
        ;;                           (into [] (for [cfg (last config)]
        ;;                                      (do ;; (println "CFG: " cfg)
        ;;                                      {:name (first cfg)
        ;;                                       :rest (rest cfg)})))}))))
        ]
    ;; (println "NORMED:")
    ;; (pp/pprint normbowers)
    ;; normbowers))
foo))
                                ;; #_(cond
                            ;;   (:uri bower)
                            ;;   (into [] (for [[sym uri] (:runtime bower)]
                            ;;                (do ;; (println "FOO: "bower)
                            ;;                  (merge bower
                            ;;                         {:runtime sym
                            ;;                          :uri uri
                            ;;                          :ns (namespace sym)
                            ;;                          :name (name sym)}))))

                            ;;   (vector? (:bundles bower))
                            ;;   (for [bundle (:bundles bower)]
                            ;;     (merge {:bower (or (:bower bower) (:polymer bower))
                            ;;             :ns (namespace (:runtime bundle))
                            ;;             :name (name (:runtime bundle))}
                            ;;            bundle))

                            ;;   (symbol? (:runtime bower))
                            ;;   (let [m (bower-meta (or (:bower bower) (:polymer bower)))
                            ;;           kw (keyword (-> m :latest :name))
                            ;;           uris (bower->uris repo m)]
                            ;;       (merge bower (if (> (count uris) 1)
                            ;;                      (throw (Exception.
                            ;;                              (str "bower pkg '"
                            ;;                                   (or (:bower bower) (:polymer bower))
                            ;;                                   "' bundles multiple components; run bower info -j on the package and create a config for each latest.main entry"))))
                            ;;              {:ns (namespace (:runtime bower))
                            ;;               :name (name (:runtime bower))}
                            ;;              (if (:polymer bower) {:kw kw})
                            ;;              {:uri (first uris)}))

                            ;;   :else (throw (Exception. (str ":bower config map must have [:runtime sym] or [:bundles vec] entry: " bower)
                            ;; )))))))
        ;; resources (:resources configs)
        ;; normresources (into [] (for [resource resources]
        ;;                          (merge resource
        ;;                                     {:ns (namespace (:runtime resource))
        ;;                                      :name (name (:runtime resource))})))
        ;; simples (filter #(and (not (symbol? (first %)))
        ;;                       (or (symbol? (last %)) (map? (last %)))) configs)
        ;; normsimples (for [[k v] simples]
        ;;        (merge {:bower k}
        ;;               (condp apply [v] symbol? {:ns (symbol (namespace v)) :name (symbol (name v))}
        ;;                      map?    v)))
        ;; compounds (filter #(vector? (:runtime %)) configs)
        ;; normcomp (flatten (into [] (for [[pkg cfgs] compounds]
        ;;                              (into [] (for [cfg cfgs]
        ;;                                         (do ;;(println "CFG: " cfg)
        ;;                                         (merge
        ;;                                          ;; (if (str/ends-with? (:uri cfg) ".js")
        ;;                                          ;;   {:js true})
        ;;                                          ;; (if (str/ends-with? (:uri cfg) ".css")
        ;;                                          ;;   {:css true})
        ;;                                          {:bower pkg} cfg)))))))
        ;; normed (concat normcomp normsimples normresources normbowers)
    ;;     ]
    ;; bowers))

(defn- ns->filestr
  [ns-str]
  (str/replace ns-str #"\.|-" {"." "/" "-" "_"}))

(defn ->pkg-mgr-pod
  [pkg-mgr fileset]
  (let [edn
        (condp = pkg-mgr
              :bower bower-edn
              :local local-edn
              :npm   npm-edn
              :polymer polymer-edn
              :webjars webjars-edn)
        edn-fs (->> fileset boot/input-files (boot/by-name [edn]))
        _ (condp = (count edn-fs)
            0 (throw (Exception. (str edn " file not found")))
            1 true
            (throw (Exception. (str "only one " edn " file allowed"))))
        edn-f (boot/tmp-file (first edn-fs))
        edn-content (-> edn-f slurp read-string)
        coords (if (= pkg-mgr :webjars)
                  (->> edn-content vals (reduce merge) vals)
                  '())
        pod-env (update-in (boot/get-env) [:dependencies] #(identity %2)
                           (concat '[[boot/aether "2.5.5"]
                                     [boot/core "2.5.5"]
                                     [boot/pod "2.5.5"]]
                                   coords))
        pod (future (pod/make-pod pod-env))]
    [edn-content pod]))

(defn prep-for-stencil
  [& configs]
  ;; (println "prep-for-stencil: " configs)
  (let [nss (set (map #(-> % :ns) configs))
        ;; _ (println "nss: " nss)
        res (into [] (for [ns- nss]
                       (do ;; (println "ns- " ns-)
                       (let [ns-cfgs (filter #(= ns- (-> % :ns)) configs)]
                         {:config-ns ns-
                          :config
                          (into [] (for [ns-cfg ns-cfgs]
                                     ns-cfg))}))))]
    ;; (println "FOR STENCIL:")
    ;; (pp/pprint res)
    res))

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

(boot/deftask cache
  "cache bowerdlerize.edn"
  [c clean bool "clean config - clear cache first"
   r retrieve bool "retrieve cached master edn config file and sync to fileset"
   s save bool "save master edn config file from fileset to cache"
   v verbose bool "verbose"]
  ;; default to retrieve
  (if (and retrieve save)
    (util/exit-error
     (util/fail "boot-bowdlerize/cache: only one of :retrieve and :save may be specified\n")))
  (let [retrieve (or retrieve (not save))
        save (or (not retrieve) save)]
    (boot/with-pre-wrap fileset
      (let [cache-dir (boot/cache-dir! :bowdlerize/bowdlerize :global true)]
        (if clean
          (do (if verbose (util/info (str "Clearing bowdlerize cache\n")))
              (boot/empty-dir! cache-dir)
              (boot/commit! fileset))
          (if retrieve
            (do ;;(println "RETRIEVING")
                (let [bowdlerize-f (io/file (.getPath cache-dir) bowdlerize-edn)]
                  (if (.exists bowdlerize-f)
                    (do (if verbose (util/info (str "Retrieving cached bowdlerize.edn\n")))
                        (-> fileset (boot/add-source cache-dir) boot/commit!))
                    fileset)))
            (if save
              (do ;;(println "SAVING")
                  (let [[bowdlerize-content pod] (->bowdlerize-pod fileset)]
                    (let [_ (if verbose (util/info (str "Caching bowdlerize.edn\n")))
                          f (io/file (.getPath cache-dir) bowdlerize-edn)]
                      (spit f (with-out-str (pp/pprint bowdlerize-content)))))
                    ;; commit?
                    fileset))))))))

(boot/deftask config
  [c config-syms CONFIG-SYMS #{sym} "config namespaced sym"
   b repo PATH str "bower components repo path, default: bower_components"
   o outdir PATH str "install dir, default: classes"
   v verbose bool "print trace msgs"]
  (boot/with-pre-wrap fileset
  (let [tmp-dir (boot/tmp-dir!)
        prev-pre (atom nil)
        repo (if repo repo bower-components)

        bowdlerize-fs (->> (boot/fileset-diff @prev-pre fileset)
                             boot/input-files
                             (boot/by-name [bowdlerize-edn]))]
      (condp = (count bowdlerize-fs)
        0 (do
            (util/warn (str "Config: " bowdlerize-edn " not found\n"))
            fileset)
        1 (let [bowdlerize-f (first bowdlerize-fs)
                ;; _ (println bowdlerize-edn ": " bowdlerize-f)
                bowdlerize-map (->  (boot/tmp-file bowdlerize-f) slurp read-string)
                ;; _ (println "bowdlerize-map: " bowdlerize-map)
                path     (boot/tmp-path bowdlerize-f)
                in-file  (boot/tmp-file bowdlerize-f)
                out-file (io/file tmp-dir path)]
            ;;(doseq [k (keys bowdlerize-map)]
              (let [edn-forms (vals bowdlerize-map)
                    ;; _ (println "edn forms: " edn-forms)
                    config-maps (get-config-maps repo bowdlerize-map)
                     ;; _ (println "CONFIG-MAPS: " config-maps)
                    ;; _ (pp/pprint config-maps)
                    ;; config-specs (apply prep-for-stencil config-specs)
                    ;; _ (println "CONFIG-SPECS: " config-specs)
                    config-maps (merge-config-maps config-maps)
                    config-maps (typify config-maps)]
                ;; (println "CONFIG-MAPS TYPIFIED: " config-maps)
                (doseq [config-map config-maps]
                  ;; (println "PROCESSING: " config-map)
                  (let [config-file-name (str outdir (ns->filestr (-> config-map :config-ns)) ".clj")
                        ;; _ (println "writing: " config-file-name)
                        config-file (stencil/render-file "boot_bowdlerize/bower.mustache"
                                                         config-map)
                        ;; _ (println "config-file: " config-file)
                        out-file (doto (io/file tmp-dir config-file-name)
                                   io/make-parents)]
                    (spit out-file config-file)))))
        (throw (Exception. (str "only one " bowdlerize-edn " file allowed"))))
      (-> fileset (boot/add-resource tmp-dir) boot/commit!))))

(boot/deftask info
  [p package PACKAGE str "bower package"
   n cname NAME str "clojure name for package"
   d directory DIR str "bower components dir, default: bower_components"]
  (let [local-bower  (io/as-file "./node_modules/bower/bin/bower")
        global-bower (io/as-file "/usr/local/bin/bower")
        bcmd        (cond (.exists local-bower) (.getPath local-bower)
                           (.exists global-bower) (.getPath global-bower)
                           :else "bower")
        tgt     (boot/tmp-dir!)
        pod-env (update-in (boot/get-env) [:dependencies] conj '[cheshire "5.5.0"])
        bower-pod    (future (pod/make-pod pod-env))
        ]
    (boot/with-pre-wrap [fileset]
      (boot/empty-dir! tgt)
      (pod/with-eval-in @bower-pod
        (require '[clojure.java.shell :refer [sh]]
                 '[clojure.java.io :as io]
                 '[clojure.string :as str]
                 '[clojure.pprint :as pp]
                 '[cheshire.core :as json])
        (let [info (sh ~bcmd "info" "-j" ~package)
              j (json/parse-string (:out info) true)
              uri (str/join "/" [bower-components (-> j :latest :name) (-> j :latest :main)])
              ]
          (pp/pprint j)))
      fileset)))

(boot/deftask ^:private install-bower
  "Install bower packages as assets"
  [c clean-cache bool "clean reinstall (empty cache at start)"
   p pkg-type PGKMGR kw "one of :bower, :npm :polymer, or :webjars; default is all 4)"
   v verbose bool "verbose"]
  (let [tmp-dir     (boot/tmp-dir!)
        bower-cache (boot/cache-dir! :bowdlerize/bower :global true)
        _ (if clean-cache (do (if verbose (util/info (str "Cleaning bower cache\n")))
                              (boot/empty-dir! bower-cache)))
        local-bower  (io/as-file "./node_modules/bower/bin/bower")
        global-bower (io/as-file "/usr/local/bin/bower")
        bcmd        (cond (.exists local-bower) (.getPath local-bower)
                          (.exists global-bower) (.getPath global-bower)
                          :else "bower")]
    (boot/with-pre-wrap fileset
      (boot/empty-dir! tmp-dir)
      (let [[edn-content pod] (->bowdlerize-pod fileset)
            bower-specs {pkg-type (get edn-content pkg-type)}
            bower-pkgs (get-bower-pkgs bower-specs)]
        (pod/with-eval-in @pod
          (require '[boot.pod :as pod] '[boot.util :as util]
                   '[clojure.java.io :as io] '[clojure.string :as str]
                   '[clojure.java.shell :refer [sh]])
          (doseq [bower-pkg '~bower-pkgs]
            (let [path (str bower-components "/" (first bower-pkg))]
              (if (not (.exists (io/file ~(.getPath bower-cache) path)))
                (let [c [~bcmd "install" (fnext bower-pkg) :dir ~(.getPath bower-cache)]]
                  ;; (println "bower cmd: " c)
                  (if ~verbose (util/info (format "Installing bower pkg:   %s\n" bower-pkg)))
                  (apply sh c))
                (if ~verbose (util/info (format "Found cached bower pkg: %s\n" bower-pkg))))))))
      (-> fileset (boot/add-asset bower-cache) boot/commit!))))

(boot/deftask ^:private install-npm
  "Install npm packages as assets"
  [c clean-cache bool "clean reinstall (empty cache at start)"
   v verbose bool "verbose"]
  (let [tmp-dir     (boot/tmp-dir!)
        pkg-cache (boot/cache-dir! :bowdlerize/npm :global true)
        _ (if clean-cache (do (if verbose (util/info (str "Cleaning npm cache\n")))
                              (boot/empty-dir! pkg-cache)))
        local-npm  (io/as-file "./node_modules/npm/bin/npm")
        global-npm (io/as-file "/usr/local/bin/npm")
        bcmd        (cond (.exists local-npm) (.getPath local-npm)
                          (.exists global-npm) (.getPath global-npm)
                          :else "npm")]
    (boot/with-pre-wrap fileset
      (boot/empty-dir! tmp-dir)
      (let [[edn-content pod] (->bowdlerize-pod fileset)
            npm-specs {:npm (get edn-content :npm)}
            npm-pkgs (get-bower-pkgs npm-specs)]
        (pod/with-eval-in @pod
          (require '[boot.pod :as pod] '[boot.util :as util]
                   '[clojure.java.io :as io] '[clojure.string :as str]
                   '[clojure.java.shell :refer [sh]])
          (doseq [npm-pkg '~npm-pkgs]
            (let [path (str "node_modules/" (first npm-pkg))]
              (if (not (.exists (io/file ~(.getPath pkg-cache) path)))
                (let [c [~bcmd "install" (fnext npm-pkg) :dir ~(.getPath pkg-cache)]]
                  ;; (println "npm cmd: " c)
                  (if ~verbose (util/info (format "Installing npm pkg:   %s\n" npm-pkg)))
                  (apply sh c))
                (if ~verbose (util/info (format "Found cached npm pkg: %s\n" npm-pkg))))))))
      (-> fileset (boot/add-asset pkg-cache) boot/commit!))))

(boot/deftask ^:private install-webjars
  "Unpack webjars as assets"
  [c clean-cache bool "clean reinstall (empty cache at start)"
   v verbose bool "verbose"]
  (let [tmp-dir     (boot/tmp-dir!)
        webjars-cache (boot/cache-dir! :bowdlerize/webjars :global true)
        _ (if clean-cache (do (if verbose (util/info (str "Cleaning webjars cache\n")))
                              (boot/empty-dir! webjars-cache)))
        webjar-dir "webjars"
        destdir  (str (.getPath webjars-cache) "/" webjar-dir)]
    (boot/with-pre-wrap [fileset]
      (boot/empty-dir! tmp-dir)
      (let [[edn-content pod] (->bowdlerize-pod fileset)]
        (pod/with-eval-in @pod
          (require '[boot.pod :as pod] '[boot.util :as util]
                   '[clojure.java.io :as io] '[clojure.string :as str])
              (let [stripped-env (update-in pod/env [:dependencies]
                                            (fn [old] (let [new (filter
                                                                 (fn [dep]
                                                                   (str/starts-with?
                                                                    (str (namespace (first dep)))
                                                                    "org.webjars"))
                                                                 old)]
                                                        new)))
                    deps (pod/resolve-dependencies stripped-env)]
                (doseq [dep deps]
                  (let [coord (:dep dep)
                        maven (str (first coord))
                        groupid (subs maven 0 (str/index-of maven "/"))
                        artifactid (subs maven (+ 1 (str/index-of maven "/")))
                        version (str (last coord))
                        path (str/join "/" [~webjar-dir
                                            "META-INF/resources/webjars"
                                            artifactid
                                            version])]
                    (if (not (.exists (io/file ~(.getPath webjars-cache) path)))
                      (do (if ~verbose (util/info (format "Installing webjar:   %s\n" coord)))
                          (pod/unpack-jar (:jar dep) ~destdir))
                      (if ~verbose (util/info (format "Found cached webjar: %s\n" coord)))))))))
      (-> fileset (boot/add-asset webjars-cache) boot/commit!))))

(boot/deftask install
  "install bower/webjar packages as assets"
  [c clean bool "clean reinstall (empty cache at start)"
   o pkg-mgrs PGKMGRS #{kw} "only these pkg mgrs (:bower, :npm :polymer, or :webjars; default is all 4)"
   v verbose bool "verbose"]
  (let [clean (or clean false)
        verbose (or verbose false)
        pkg-mgrs (or pkg-mgrs #{:bower :npm :polymer :webjars})
        pipeline (map (fn [pkg-mgr]
                         (condp = pkg-mgr
                           :bower (install-bower :pkg-type :bower :clean-cache clean :verbose verbose)
                           :local identity
                           :npm (install-npm :clean-cache clean :verbose verbose)
                           :polymer (install-bower :pkg-type :polymer :clean-cache clean :verbose verbose)
                           :webjars (install-webjars :clean-cache clean :verbose verbose)
                           (util/fail (str pkg-mgr " installation not yet supported"))))
                      pkg-mgrs)]
    (apply comp pipeline)))

(boot/deftask ^:private metaconf-impl
  "elaborate bowdlerize config edn with pkg mgr config edn"
  [c clean bool "clean config - clear cache first"
   d dir DIR str "output dir"
   p pkg-mgr PKGMGR kw "package mgr keyword, :bower, :npm :polymer, :webjars"
   v verbose bool "Print trace messages."]
  (let [tmp-dir (boot/tmp-dir!)]
    (boot/with-pre-wrap fileset
      (let [[edn-content pod] (->pkg-mgr-pod pkg-mgr fileset)
            [newfs bowdlerize-f bowdlerize-content] (->bowdlerize-content fileset verbose)]
        ;; (println "FOO")
        ;; (pod/with-eval-in @pod
        ;;   (require '[boot.util :as util]
        ;;            '[clojure.pprint :as pp]
        ;;            '[clojure.java.io :as io])
          ;; (println "BAR")
          (if (get bowdlerize-content pkg-mgr)
            (if verbose (util/info (str bowdlerize-edn " already elaborated with " pkg-mgr "\n")))
            (do (if verbose (util/info
                              (str "Elaborating " bowdlerize-edn " with " pkg-mgr " stanza\n")))
                (let [path     (.getName bowdlerize-f)
                      out-file (io/file (.getPath tmp-dir) path)]
                  (spit out-file (with-out-str
                                   (pp/pprint
                                    (assoc bowdlerize-content
                                           pkg-mgr edn-content)))))))
      (-> newfs (boot/add-source tmp-dir) boot/commit!)))))

(boot/deftask metaconf
  "Construct transient master .edn config file (bowdlerize.edn)"
  [c clean bool "clean config - clear cache first.  default: true"
   C no-clean bool "do not clear cache.  default: false"
   o pkg-mgrs PGKMGRS #{kw} "only these pkg mgrs (:bower, :npm :polymer, or :webjars; default is all 4)"
   v verbose bool "verbose"]
  (if (and clean no-clean) (util/exit-error (util/fail "boot-bowdlerize/metaconf: only one of :clean and :no-clean allowed")))
  (let [pkg-mgrs (if pkg-mgrs pkg-mgrs #{:bower :npm :polymer :local :webjars})
        pipeline (map #(metaconf-impl :clean clean :pkg-mgr % :verbose verbose) pkg-mgrs)
        clean (or clean (not no-clean))]
    (comp (cache :retrieve true :verbose verbose :clean clean)
          (apply comp pipeline)
          (cache :save true :verbose verbose))))

(boot/deftask normalize
  [v verbose bool "verbose"]
  ;; (println "NORMALIZE")
  (boot/with-pre-wrap fileset
    (let [[edn-content pod] (->bowdlerize-pod fileset)]
      (pod/with-eval-in @pod
        (require '[boot.util :as util]
                 '[clojure.pprint :as pp])
        (println "edn-content")
        (pp/pprint '~edn-content)
        '~(normalize-configs edn-content)))
    fileset))

;; (boot/deftask resources
;;   "process resources.edn"
;;   [c config-syms SYMS #{sym} "namespaced symbols bound to meta-config data"
;;    b repo PATH str "bower components repo path, default: bower_components"
;;    d dir DIR str "output dir"
;;    k keep bool "keep intermediate .clj files"
;;    v verbose bool "Print trace messages."]
;;   (let [tmp-dir (boot/tmp-dir!)
;;         prev-pre (atom nil)
;;         repo (if repo repo "resources")
;;         ;; dir (if dir dir web-inf-dir)
;;         ]
;;     (boot/with-pre-wrap fileset
;;       (let [resources-fs (->> (boot/fileset-diff @prev-pre fileset)
;;                        boot/input-files
;;                        (boot/by-name [resources-edn]))
;;             _ (condp = (count resources-fs)
;;                 0 (throw (Exception. (str resources-edn " file not found")))
;;                 1 true
;;                 (throw (Exception. (str "only one " resources-edn " file allowed"))))

;;             resources-f (boot/tmp-file (first resources-fs))
;;             ;; _ (println "resources-f: " resources-f)
;;             resources-content (-> resources-f slurp read-string)
;;             ;; _ (println "resources-content: " resources-content)
;;             ]
;;         ;; elaborate bowdlerize.edn
;;         (let [bowdlerize-fs (->> (boot/fileset-diff @prev-pre fileset)
;;                                  boot/input-files
;;                                  (boot/by-name [bowdlerize-edn]))]
;;           (condp = (count bowdlerize-fs)
;;             0 (let [_ (util/info (str "Creating bowdlerize.edn\n"))
;;                     f (io/file tmp-dir bowdlerize-edn)]
;;                 (spit f (with-out-str (pp/pprint {:resources resources-content})))
;;                 fileset)
;;             1 (let [bowdlerize-f (first bowdlerize-fs)
;;                     ;; _ (println bowdlerize-edn ": " bowdlerize-f)
;;                     bowdlerize-content (->  (boot/tmp-file bowdlerize-f) slurp read-string)
;;                     ;; _ (println "bowdlerize-content: " bowdlerize-content)
;;                     path     (boot/tmp-path bowdlerize-f)
;;                     in-file  (boot/tmp-file bowdlerize-f)
;;                     out-file (io/file tmp-dir path)]
;;                 (if (:resources bowdlerize-content)
;;                   (do (util/info (str bowdlerize-edn " Already elaborated with :resources\n"))
;;                       fileset)
;;                   (do (util/info (str "Elaborating " bowdlerize-edn " with :resources stanza\n"))
;;                       (spit out-file (with-out-str
;;                                        (pp/pprint
;;                                         (assoc bowdlerize-content :resources resources-content)))))))
;;             (throw (Exception. (str "only one " bowdlerize-edn " file allowed"))))))
;;       (-> fileset (boot/add-source tmp-dir) boot/commit!))))

(boot/deftask show
  "show build-time dependencies"
  [n config-syms      CONFIG-SYMS #{sym} "config namespace"
   b bower            bool  "Print project bower component (build-time) dependency graph."
   w webjars          bool  "Print project webjars (build-time) dependency graph."]
  ;; (doseq [jar (sort (pod/jars-in-dep-order (boot/get-env)))]
  ;;   (println "JAR: " (.getName jar))))

  (doseq [config-sym config-syms]
    (let [config-ns (symbol (namespace config-sym))]
      (if (nil? config-ns) (throw (Exception. (str "config symbols must be namespaced"))))
      (require config-ns)
      ;; (println "Install CONFIG-NS: " config-ns)
      (if (not (find-ns config-ns)) (throw (Exception. (str "can't find config ns"))))
      ;; (doseq [[isym ivar] (ns-interns config-ns)] (println "ISYM2: " isym ivar))

      (let [config-var (if-let [v (resolve config-sym)]
                         v (throw (Exception. (str "can't find config var for: " config-sym))))
            ;; _ (println "config-var: " config-var)

            configs (deref config-var)
            ;; _ (println "configs:")
            ;; _ (pp/pprint configs)

            webjar-coords (map #(:webjar %) (filter #(:webjar %) configs))
            ;; _ (println "webjar-coords:")
            ;; _ (pp/pprint webjar-coords)

            pod-env (update-in (boot/get-env) [:dependencies]
                               #(identity %2)
                               (concat webjar-coords
                                       '[[boot/aether "2.5.5"]
                                         [boot/pod "2.5.5"]
                                         [boot/core "2.5.5"]]))
            ;; _ (println "POD-ENV deps:")
            ;; _ (println (:dependencies pod-env))
            pod    (future (pod/make-pod pod-env))]
        (pod/with-eval-in @pod
          (require '[boot.aether :as aether])
          (require '[boot.core :as boot])
          (require '[boot.pod :as pod])
          (require '[clojure.string :as str])
          (require '[clojure.pprint :as pp])

          (let [stripped-env (update-in pod/env [:dependencies]
                                        (fn [old] (filter
                                                   (fn [dep]
                                                     ;; (and
                                                     ;;  (not= "boot" (namespace (first dep)))
                                                     ;;  (not= "cheshire" (namespace (first dep)))
                                                     ;;  (not= "org.clojure" (namespace (first dep)))))
                                                     (str/starts-with?
                                                      (str (namespace (first dep)))
                                                      "org.webjars"))
                                                   old)))]
            ;; (println "stripped env:")
            ;; (pp/pprint stripped-env)
            (if ~webjars
              (println (aether/dep-tree stripped-env)))))))))

;; #_(boot/deftask webjars
;;   "process webjars.edn"
;;   [d dir DIR str "output dir"
;;    k keep bool "keep intermediate .clj files"
;;    v verbose bool "Print trace messages."]
;;   (let [tmp-dir (boot/tmp-dir!)]
;;     (boot/with-pre-wrap fileset
;;       (let [[edn-content pod] (->pkg-mgr-pod :webjars fileset)
;;             bowdlerize-content (->bowdlerize-content fileset)]
;;         (pod/with-eval-in @pod
;;           (require '[boot.util :as util]
;;                    '[clojure.pprint :as pp]
;;                    '[clojure.java.io :as io])
;;           (if (:webjars '~bowdlerize-content)
;;             (util/info (str ~bowdlerize-edn " already elaborated with :webjars\n"))
;;             (do (util/info (str "Elaborating " ~bowdlerize-edn " with :webjars stanza\n"))
;;               (let [path     ~(.getName bowdlerize-f)
;;                     out-file (io/file ~(.getPath tmp-dir) path)]
;;                 (spit out-file (with-out-str
;;                                  (pp/pprint
;;                                   (assoc '~bowdlerize-content
;;                                          :webjars '~edn-content)))))))))
;;       (-> fileset (boot/add-resource tmp-dir) boot/commit!))))

(boot/deftask tester
  "test"
  [t type KW kw "type"]
  (boot/with-pre-wrap [fileset]
    (let [[b-content pod] (->bowdlerize-pod fileset true)]
      (println "B-CONTENT: " b-content)
;;      (println "POD: " pod)
      fileset))

    #_(let [[edn-content pod] (->pkg-mgr-pod type fileset)]
      (println "EDN-CONTENT: " edn-content)
      (println "POD: " pod)
      fileset)
  #_(let [prev-pre (atom nil)]
    #_(let [deps (pod/resolve-dependencies (boot/get-env))
            wjs (filter #(#{"org.webjars" "org.webjars.bower"} (namespace (first (:dep %)))) deps)
            res (map :jar wjs)]
        (println "WJS: " res))
    (boot/with-pre-wrap [fileset]
      (let [webjars-fs (->> (boot/fileset-diff @prev-pre fileset)
                            boot/input-files
                            (boot/by-name [webjars-edn]))
            _ (condp = (count webjars-fs)
                0 (throw (Exception. (str webjars-edn " file not found")))
                1 true
                (throw (Exception. (str "only one " webjars-edn " file allowed"))))
            webjars-f (boot/tmp-file (first webjars-fs))
            webjars-content (-> webjars-f slurp read-string)
            _ (println "WEBJARS-CONTENT: " webjars-content)
            webjar-coords (->> webjars-content vals (reduce merge) vals)
            _ (println "WEBJAR-COORDS: " webjar-coords)
            ;;_ (doseq [c webjar-coords] (println "WEBJAR-COORD: " c))
            pod (webjars-podder webjar-coords)]
        fileset
        ))))
