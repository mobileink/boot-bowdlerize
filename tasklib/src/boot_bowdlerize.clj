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
(def bower-edn "bower.edn")
(def npm-edn "npm.edn")
(def polymer-edn "polymer.edn")
(def resources-edn "resources.edn")
(def webjars-edn "webjars.edn")

(defn expand-home [s]
  (if (or (.startsWith s "~") (.startsWith s "$HOME"))
    (str/replace-first s "~" (System/getProperty "user.home"))
    s))

;; https://github.com/bower/spec/blob/master/config.md
;;TODO, maybe: accept clojure map for .bowerrc, bower.json

(defn- config-file?
  [f]
  #_(println "config-file? " (.getName f))
  #_(let [fns (load-file (.getName f))]
    )
  f)

(defn get-coords
  [type edn-content]
  (condp = type
    :bower '()
    :polymer '()
    :webjars (->> edn-content vals (reduce merge) vals)))

(defn ->bowdlerize-pod
  [fileset]
  (let [edn bowdlerize-edn
        edn-fs (->> fileset
                        boot/input-files
                        (boot/by-name [edn]))
        _ (condp = (count edn-fs)
            0 (throw (Exception. (str edn " file not found")))
            1 true
            (throw (Exception. (str "only one " edn " file allowed"))))
        edn-f (boot/tmp-file (first edn-fs))
        ;; _ (println "EDN-F: " edn-f)
        edn-content (-> edn-f slurp read-string)
        ;; _ (println "EDN-CONTENT: " (type edn-content) edn-content)
        coords (if-let [webjars (:webjars edn-content)]
                 (->> webjars vals (reduce merge) vals)
                  '())
        ;; _ (println "COORDS: " coords)
        pod-env (update-in (boot/get-env) [:dependencies] #(identity %2)
                           (concat '[[boot/aether "2.5.5"]
                                     [boot/core "2.5.5"]
                                     [boot/pod "2.5.5"]]
                                   coords))
        pod (future (pod/make-pod pod-env))]
    [edn-content pod]))

(defn ->pkg-mgr-pod
  [pkg-mgr fileset]
  ;; (println "->PKG-MGR-POD: " pkg-mgr)
  (let [edn
        (condp = pkg-mgr
              :bower bower-edn
              :npm   npm-edn
              :polymer polymer-edn
              :webjars webjars-edn)
        ;; _ (println "EDN: " edn)
        edn-fs (->> fileset
                        boot/input-files
                        (boot/by-name [edn]))
        _ (condp = (count edn-fs)
            0 (throw (Exception. (str edn " file not found")))
            1 true
            (throw (Exception. (str "only one " edn " file allowed"))))
        edn-f (boot/tmp-file (first edn-fs))
        ;; _ (println "EDN-F: " edn-f)
        edn-content (-> edn-f slurp read-string)
        ;; _ (println "EDN-CONTENT: " (type edn-content) edn-content)
        coords (if (= pkg-mgr :webjars)
                  (->> edn-content vals (reduce merge) vals)
                  '())
        ;; _ (println "COORDS: " coords)
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
        ;;c [bcmd "install" "-j" pkg  :dir (.getPath tgt)]
        ;; (println "sh cmd: " c)
        ;; (pod/with-eval-in @bower-pod
        ;;   (require '[clojure.java.shell :refer [sh]]
        ;;            '[clojure.java.io :as io]
        ;;            '[clojure.string :as str]
        ;;            '[cheshire.core :as json])
        ;; _ (println "bower-meta: " pkg)
        info (sh bcmd "info" "-j" pkg)
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
  ;; (println "NORMALIZE-CONFIGS: " configs)
  (let [bowers (concat (:bower configs) (:polymer configs))
        ;; (filter #(or (:bower %) (:polymer %)) configs)
        ;; _ (println "BOWERS: " bowers)
        normbowers (flatten
                    (into []
                          (for [bower bowers]
                            (do ;; (println "BOWER: " bower)
                            (cond

                              (:uri bower)
                              (into [] (for [[sym uri] (:runtime bower)]
                                           (do ;; (println "FOO: "bower)
                                             (merge bower
                                                    {:runtime sym
                                                     :uri uri
                                                     :ns (namespace sym)
                                                     :name (name sym)}))))

                              (vector? (:bundles bower))
                              (for [bundle (:bundles bower)]
                                (merge {:bower (or (:bower bower) (:polymer bower))
                                        :ns (namespace (:runtime bundle))
                                        :name (name (:runtime bundle))}
                                       bundle))

                              (symbol? (:runtime bower))
                              (let [m (bower-meta (or (:bower bower) (:polymer bower)))
                                      kw (keyword (-> m :latest :name))
                                      ;; _ (println "kw: " kw)
                                      ;; poly (str/starts-with? (:bower bower) "Polymer")
                                      ;; _ (println "poly: " poly)
                                      uris (bower->uris base m)]
                                  (merge bower (if (> (count uris) 1)
                                                 (throw (Exception.
                                                         (str "bower pkg '"
                                                              (or (:bower bower) (:polymer bower))
                                                              "' bundles multiple components; run bower info -j on the package and create a config for each latest.main entry"))))
                                         {:ns (namespace (:runtime bower))
                                          :name (name (:runtime bower))}
                                         (if (:polymer bower) {:kw kw})
                                         {:uri (first uris)}))

                              :else (throw (Exception. (str ":bower config map must have [:runtime sym] or [:bundles vec] entry: " bower)
                            )))))))

                                    ;; #_(merge bower
                                    ;;        {:ns (namespace (:runtime bower))
                                    ;;         :name (name (:runtime bower))})
        ;; _ (println "NORMBOWERS: " normbowers)

        ;; resources (filter #(not (or (:bower %)
        ;;                             (:polymer %)
        ;;                             (:npm %))) configs)
        resources (:resources configs)
        ;; _ (println "RESOURCES: " resources)

        normresources (into [] (for [resource resources]
                                 (do ;; (println "RESOURCE: " resource)
                                     (merge resource
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
  ;; (println "GET-CONFIG-MAPS: " configs)
  (let [normal-configs (normalize-configs bower-base configs)
        ;; _ (println "NORMAL-CONFIGS:") _ (pp/pprint normal-configs)
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
                                                      ;; (if (str/ends-with? (first uris) ".js")
                                                      ;;   {:js true})
                                                      ;; (if (str/ends-with? (first uris) ".css")
                                                      ;;   {:css true})
                                                      (if (:polymer ns-config) {:polymer {:kw kw}})
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
  ;; (println "MERGE-CONFIG-MAPS: " ms)
  (let [nss (set (map :ns ms)) ;;  #(symbol (:config-ns %)) ms))
        ;; _ (println "nss: " nss)
        merged-maps (into []
                          (for [k (seq nss)]
                            (let [specs (filter #(= k (:ns %)) ms)
                                  bodies (flatten (into [] (map #(dissoc % :ns) specs)))]
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

;; (boot/deftask bower
;;   "process bower.edn"
;;   [c config-syms SYMS #{sym} "namespaced symbols bound to meta-config data"
;;    b base PATH str "bower components base path, default: bower_components"
;;    d dir DIR str "output dir"
;;    k keep bool str "keep intermediate .clj files"
;;    v verbose bool "Print trace messages."]
;;   (let [tmp-dir (boot/tmp-dir!)
;;         prev-pre (atom nil)
;;         base (if base base "bower_components")
;;         ;; dir (if dir dir web-inf-dir)
;;         ]
;;     (boot/with-pre-wrap fileset
;;       (let [edn-fs (->> (boot/fileset-diff @prev-pre fileset)
;;                        boot/input-files
;;                        (boot/by-name ["bower.edn"]))]
;;         (if (> (count edn-fs) 1) (throw (Exception. "only one bower.edn file allowed")))
;;         (if (= (count edn-fs) 0) (throw (Exception. "bower.edn file not found")))

;;         (let [edn-f (first edn-fs)
;;               edn-forms (-> (boot/tmp-file edn-f) slurp read-string)
;;               ;; _ (println "edn-forms: " edn-forms)
;;               config-maps (get-config-maps base edn-forms)
;;               ;; _ (println "config-maps: " config-maps)
;;               config-specs (apply prep-for-stencil config-maps)
;;               _ (println "CONFIG-SPECS: " config-specs)
;;               config-maps (merge-config-maps config-maps)
;;               _ (println "CONFIG-MAPS MERGED: " config-maps)
;;               config-maps (typify config-maps)]
;;           (println "CONFIG-MAPS TYPIFIED: " config-maps)
;;             (doseq [config-map config-maps]
;;               (let [config-file-name (str (ns->filestr (-> config-map :config-ns)) ".clj")
;;                     _ (println "writing: " config-file-name)
;;                     config-file (stencil/render-file "boot_bowdlerize/bower.mustache"
;;                                                      config-map)
;;                     out-file (doto (io/file tmp-dir config-file-name)
;;                                io/make-parents)]
;;                 (spit out-file config-file)))))
;;         (-> fileset (boot/add-resource tmp-dir) boot/commit!))))

(boot/deftask bower
  "process bower.edn"
  [c config-syms SYMS #{sym} "namespaced symbols bound to meta-config data"
   b base PATH str "bower components base path, default: bower_components"
   d dir DIR str "output dir"
   k keep bool "keep intermediate .clj files"
   v verbose bool "Print trace messages."]
  (let [tmp-dir (boot/tmp-dir!)
        prev-pre (atom nil)
        base (if base base "bower")
        ;; dir (if dir dir web-inf-dir)
        ]
    (boot/with-pre-wrap fileset
      (let [bower-fs (->> (boot/fileset-diff @prev-pre fileset)
                       boot/input-files
                       (boot/by-name [bower-edn]))
            _ (condp = (count bower-fs)
                0 (throw (Exception. (str bower-edn " file not found")))
                1 true
                (throw (Exception. (str "only one " bower-edn " file allowed"))))

            bower-f (boot/tmp-file (first bower-fs))
            ;; _ (println "bower-f: " bower-f)
            bower-content (-> bower-f slurp read-string)
            ;; _ (println "bower-content: " bower-content)
            ]
        ;; elaborate bowdlerize.edn
        (let [bowdlerize-fs (->> (boot/fileset-diff @prev-pre fileset)
                                 boot/input-files
                                 (boot/by-name [bowdlerize-edn]))]
          (condp = (count bowdlerize-fs)
            0 (let [_ (util/info (str "Creating bowdlerize.edn\n"))
                    f (io/file tmp-dir bowdlerize-edn)]
                (spit f (with-out-str (pp/pprint {:bower bower-content})))
                fileset)
            1 (let [bowdlerize-f (first bowdlerize-fs)
                    ;; _ (println bowdlerize-edn ": " bowdlerize-f)
                    bowdlerize-content (->  (boot/tmp-file bowdlerize-f) slurp read-string)
                    ;; _ (println "bowdlerize-content: " bowdlerize-content)
                    path     (boot/tmp-path bowdlerize-f)
                    in-file  (boot/tmp-file bowdlerize-f)
                    out-file (io/file tmp-dir path)]
                (if (:bower bowdlerize-content)
                  (do (util/info (str bowdlerize-edn " Already elaborated with :bower\n"))
                      fileset)
                  (do (util/info (str "Elaborating " bowdlerize-edn " with :bower stanza\n"))
                      (spit out-file (with-out-str
                                       (pp/pprint (assoc bowdlerize-content :bower bower-content)))))))
            (throw (Exception. (str "only one " bowdlerize-edn " file allowed"))))))
      (-> fileset (boot/add-source tmp-dir) boot/commit!))))

(defn ->bowdlerize-fs
  "add bowdlerize-edn to fs if not already there"
  [fileset]
  ;; (println "->BOWDLERIZE-FS")
  (let [tmp-dir (boot/tmp-dir!)
        bowdlerize-fs (->> fileset
                           boot/input-files
                           (boot/by-name [bowdlerize-edn]))
        f (condp = (count bowdlerize-fs)
                 0 (let [_ (util/info (str "Creating bowdlerize.edn\n"))
                         f (io/file tmp-dir bowdlerize-edn)]
                     ;; (println "NEW BOWDLERIZE-EDN FILE: " f)
                     (spit f {})
                     f)
                 1 (boot/tmp-file (first bowdlerize-fs))
                 (throw (Exception. (str "only one " bowdlerize-edn " file allowed"))))]
    ;; (println "BOWDLERIZE-EDN FILE: " f)
    [f (-> fileset (boot/add-resource tmp-dir) boot/commit!)]))

(boot/deftask ^:private meta-config-impl
  "elaborate bowdlerize config edn with pkg mgr config edn"
  [d dir DIR str "output dir"
   k keep bool "keep intermediate .clj files"
   p pkg-mgr PKGMGR kw "package mgr keyword, :bower, :npm :polymer, :webjars"
   v verbose bool "Print trace messages."]
  (let [tmp-dir (boot/tmp-dir!)]
    (boot/with-pre-wrap fileset
      (let [[edn-content pod] (->pkg-mgr-pod pkg-mgr fileset)
            [bowdlerize-f newfs] (->bowdlerize-fs fileset)
            bowdlerize-content (-> bowdlerize-f slurp read-string)]
        (pod/with-eval-in @pod
          (require '[boot.util :as util]
                   '[clojure.pprint :as pp]
                   '[clojure.java.io :as io])
          (if (get '~bowdlerize-content ~pkg-mgr)
            (util/info (str ~bowdlerize-edn " already elaborated with " ~pkg-mgr "\n"))
            (do (util/info (str "Elaborating " ~bowdlerize-edn " with " ~pkg-mgr " stanza\n"))
                (let [path     ~(.getName bowdlerize-f)
                      out-file (io/file ~(.getPath tmp-dir) path)]
                  (spit out-file (with-out-str
                                   (pp/pprint
                                    (assoc '~bowdlerize-content
                                           ~pkg-mgr '~edn-content)))))))))
      (-> fileset (boot/add-source tmp-dir) boot/commit!))))

  ;; "elaborate bowdlerize config edn with pkg mgr config edn"
  ;; [d dir DIR str "output dir"
  ;;  k keep bool "keep intermediate .clj files"

(boot/deftask meta-config
  [o pkg-mgrs PGKMGRS #{kw} "only these pkg mgrs (:bower, :npm :polymer, or :webjars; default is all 4)"]
  (let [pkg-mgrs (if pkg-mgrs pkg-mgrs #{:bower :npm :polymer :webjars})]
    (apply comp (map #(meta-config-impl :pkg-mgr %) pkg-mgrs))))

#_(boot/deftask webjars
  "process webjars.edn"
  [d dir DIR str "output dir"
   k keep bool "keep intermediate .clj files"
   v verbose bool "Print trace messages."]
  (let [tmp-dir (boot/tmp-dir!)]
    (boot/with-pre-wrap fileset
      (let [[edn-content pod] (->pkg-mgr-pod :webjars fileset)
            [bowdlerize-f newfs] (->bowdlerize-fs fileset)
            bowdlerize-content (-> bowdlerize-f slurp read-string)]
        (pod/with-eval-in @pod
          (require '[boot.util :as util]
                   '[clojure.pprint :as pp]
                   '[clojure.java.io :as io])
          (if (:webjars '~bowdlerize-content)
            (util/info (str ~bowdlerize-edn " already elaborated with :webjars\n"))
            (do (util/info (str "Elaborating " ~bowdlerize-edn " with :webjars stanza\n"))
              (let [path     ~(.getName bowdlerize-f)
                    out-file (io/file ~(.getPath tmp-dir) path)]
                (spit out-file (with-out-str
                                 (pp/pprint
                                  (assoc '~bowdlerize-content
                                         :webjars '~edn-content)))))))))
      (-> fileset (boot/add-resource tmp-dir) boot/commit!))))

(boot/deftask resources
  "process resources.edn"
  [c config-syms SYMS #{sym} "namespaced symbols bound to meta-config data"
   b base PATH str "bower components base path, default: bower_components"
   d dir DIR str "output dir"
   k keep bool "keep intermediate .clj files"
   v verbose bool "Print trace messages."]
  (let [tmp-dir (boot/tmp-dir!)
        prev-pre (atom nil)
        base (if base base "resources")
        ;; dir (if dir dir web-inf-dir)
        ]
    (boot/with-pre-wrap fileset
      (let [resources-fs (->> (boot/fileset-diff @prev-pre fileset)
                       boot/input-files
                       (boot/by-name [resources-edn]))
            _ (condp = (count resources-fs)
                0 (throw (Exception. (str resources-edn " file not found")))
                1 true
                (throw (Exception. (str "only one " resources-edn " file allowed"))))

            resources-f (boot/tmp-file (first resources-fs))
            ;; _ (println "resources-f: " resources-f)
            resources-content (-> resources-f slurp read-string)
            ;; _ (println "resources-content: " resources-content)
            ]
        ;; elaborate bowdlerize.edn
        (let [bowdlerize-fs (->> (boot/fileset-diff @prev-pre fileset)
                                 boot/input-files
                                 (boot/by-name [bowdlerize-edn]))]
          (condp = (count bowdlerize-fs)
            0 (let [_ (util/info (str "Creating bowdlerize.edn\n"))
                    f (io/file tmp-dir bowdlerize-edn)]
                (spit f (with-out-str (pp/pprint {:resources resources-content})))
                fileset)
            1 (let [bowdlerize-f (first bowdlerize-fs)
                    ;; _ (println bowdlerize-edn ": " bowdlerize-f)
                    bowdlerize-content (->  (boot/tmp-file bowdlerize-f) slurp read-string)
                    ;; _ (println "bowdlerize-content: " bowdlerize-content)
                    path     (boot/tmp-path bowdlerize-f)
                    in-file  (boot/tmp-file bowdlerize-f)
                    out-file (io/file tmp-dir path)]
                (if (:resources bowdlerize-content)
                  (do (util/info (str bowdlerize-edn " Already elaborated with :resources\n"))
                      fileset)
                  (do (util/info (str "Elaborating " bowdlerize-edn " with :resources stanza\n"))
                      (spit out-file (with-out-str
                                       (pp/pprint
                                        (assoc bowdlerize-content :resources resources-content)))))))
            (throw (Exception. (str "only one " bowdlerize-edn " file allowed"))))))
      (-> fileset (boot/add-source tmp-dir) boot/commit!))))

(boot/deftask config
  [c config-syms CONFIG-SYMS #{sym} "config namespaced sym"
   b base PATH str "bower components base path, default: bower_components"
   o outdir PATH str "install dir, default: classes"]
  (boot/with-pre-wrap fileset
  (let [tmp-dir (boot/tmp-dir!)
        prev-pre (atom nil)
        base (if base base "bower_components")

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
                    config-maps (get-config-maps base bowdlerize-map)
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




  ;; (let [config-syms   (if config-syms config-syms #_(if (namespace config-syms)
  ;;                             config-syms (throw (Exception. (str "config symbol must be namespaced"))))
  ;;                 #{'bower/config})
  ;;       base (if base base "bower_components")
  ;;       outdir   (if (nil? outdir) "./" outdir)
  ;;       tgt     (boot/tmp-dir!)
  ;;       pod-env (update-in (boot/get-env) [:dependencies] conj '[cheshire "5.5.0"])
  ;;       config-pod    (future (pod/make-pod pod-env))]
  ;;   (boot/with-pre-wrap [fileset]
  ;;     (boot/empty-dir! tgt)
  ;;     (let [config-maps
  ;;           (into []
  ;;                 (flatten
  ;;                  (for [config-sym config-syms]
  ;;                       (let [config-ns (symbol (namespace config-sym))]
  ;;                         ;; (println "CONFIG-NS: " config-ns)
  ;;                         (require config-ns)
  ;;                         ;; (doseq [[ivar isym] (ns-interns config-ns)] (println "interned: " ivar isym))
  ;;                         (if (not (find-ns config-ns)) (throw (Exception. (str "can't find config ns"))))
  ;;                         (let [config-var (if-let [v (resolve config-sym)]
  ;;                                            v (throw
  ;;                                               (Exception.
  ;;                                                (str "can't find config var for: " config-sym))))
  ;;                               configs (deref config-var)
  ;;                               config-specs (get-config-maps base configs)
  ;;                               ;; _ (pp/pprint config-specs)
  ;;                               config-specs (apply prep-for-stencil config-specs)
  ;;                               ;; _ (println (format "config-specs for ns '%s: %s"
  ;;                               ;;                    config-ns (count config-specs)))
  ;;                               ]
  ;;                           config-specs)))))
  ;;           ;; _ (println "CONFIG-MAPS: " config-maps)
  ;;           config-maps (merge-config-maps config-maps)
  ;;           config-maps (typify config-maps)]
  ;;       ;; (println "CONFIG-MAPS: " (count config-maps))
  ;;       ;; (pp/pprint config-maps)
  ;;       (doseq [config-map config-maps]
  ;;           (let [config-file-name (str outdir "/" (ns->filestr (-> config-map :config-ns)) ".clj")
  ;;                 ;; _ (println "writing: " config-file-name)
  ;;                 config-file (stencil/render-file "boot_bowdlerize/bower.mustache"
  ;;                                                  config-map)
  ;;                 out-file (doto (io/file tgt config-file-name)
  ;;                            io/make-parents)]
  ;;             (spit out-file config-file))))
  ;;     (-> fileset (boot/add-resource tgt) boot/commit!))))

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
;; (println "sh cmd: " bcmd)
    ;; (println "pkg: " package)
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
              uri (str/join "/" ["bower_components" (-> j :latest :name) (-> j :latest :main)])
              ]
          (pp/pprint j)))
      fileset)))

(declare unpack-webjars)

(boot/deftask install
  "install bower components"
  [n config-syms CONFIG-SYMS #{sym} "config namespace"
   b bower            bool  "install bower pkgs only"
   j webjars          bool  "install webjars only"
   d bower-home       PATH str "where to put the bower repo (default: ~/.bower)"
   r bower-repo       PATH str "repo for bower pkgs (default: bower_components)"
   w webjar-home      PATH str "where to put the webjars repo (default: ~/.webjars)"
   x webjar-repo      PATH str "repo for webjars (default: webjars)"
   ]
  ;; (println "TASK: install")
  (let [prev-pre (atom nil)]
    (boot/with-pre-wrap fileset
      (let [tmp-dir    (boot/tmp-dir!)
            bower-wd   (boot/tmp-dir!)
            bower-home (if bower-home bower-home (expand-home "~/.bower"))
            bower-repo (if bower-repo bower-repo "bower_components")
            bower-rcf (io/file bower-wd ".bowerrc")
            bower-rcc (json/generate-string {:directory
                                             (expand-home (str bower-home "/" bower-repo))
                                            ;; :ignoredDependencies ignore
                                             } {:pretty true})
            bowdlerize-fs (->> (boot/fileset-diff @prev-pre fileset)
                               boot/input-files
                               (boot/by-name [bowdlerize-edn]))
            config-map (if (not= 1 (count bowdlerize-fs))
                         (util/warn
                          (str "Config: one " bowdlerize-edn "expected, found " (count bowdlerize-fs) "\n"))
                         (->  (boot/tmp-file (first bowdlerize-fs)) slurp read-string))
            ;; _ (println "config-map: " config-map)
            config-pkgs (concat (:bower config-map) (:polymer config-map))
            ;; _ (println "CONFIG-PKGS: " config-pkgs)

            ;; bower-components   (if (nil? bower-components) "bower_components" bower-components)
            local-bower  (io/as-file "./node_modules/bower/bin/bower")
            global-bower (io/as-file "/usr/local/bin/bower")
            bcmd        (cond (.exists local-bower) (.getPath local-bower)
                              (.exists global-bower) (.getPath global-bower)
                              :else "bower")
            pod-env (boot/get-env)
            bower-pod    (future (pod/make-pod pod-env))
            destdir (.getPath bower-wd)]
        (boot/empty-dir! bower-wd)
        (doto bower-rcf io/make-parents (spit bower-rcc))
        (doseq [config-pkg config-pkgs]
            (if-let [pkg (or (:bower config-pkg) (:polymer config-pkg))]
              (let [c [bcmd "install" pkg :dir destdir]]
                ;;(println "bower cmd: " c)
                (util/info (format "Installing %s\n" pkg))
                (pod/with-eval-in @bower-pod
                  (require '[clojure.java.shell :refer [sh]])
                  (sh ~@c)))))
        ;; fileset))))
        (-> fileset (boot/add-asset tmp-dir) boot/commit!)))))

          ;; (let [config-ns (symbol (namespace (or (:polymer config-pkg) (:bower config-pkg))))]
          ;;   (if (nil? config-ns) (throw (Exception. (str "config symbols must be namespaced"))))
            ;; (require config-ns)
            ;; ;; (println "Install CONFIG-NS: " config-ns)
            ;; (if (not (find-ns config-ns)) (throw (Exception. (str "can't find config ns"))))
            ;; ;; (doseq [[isym ivar] (ns-interns config-ns)] (println "ISYM2: " isym ivar))
            ;; (let [config-var (if-let [v (resolve config-sym)]
            ;;                    v (throw (Exception. (str "can't find config var for: " config-sym))))
            ;;       ;; _ (println "config-var: " config-var)
            ;;       configs (deref config-var)
            ;;       ;; _ (println "configs: " configs)

            ;;       bower-pkgs (filter #(or (:bower %) (:polymer %)) configs)
            ;;       ;; _ (println "bower-pkgs: " bower-pkgs)

            ;;       destdir (.getPath tmp-dir)
            ;;       ]
          ;; (cond
            ;;(not (empty? bower-pkgs))

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

(boot/deftask install-webjars
  "unpack webjars"
  [c clean-cache bool "clean reinstall (empty cache at start)"
   d webjar-dir PATH str "install dir, default: webjars"]
  (let [tmp-dir     (boot/tmp-dir!)
        webjars-cache (boot/cache-dir! :bowdlerize/webjars :global true)
        _ (if clean-cache (do (println "CLEANING CACHE") (boot/empty-dir! webjars-cache)))
        prev-pre (atom nil)
        webjar-dir (if webjar-dir webjar-dir "webjars")
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
                      (do (util/info (str "unpacking: " coord "\n"))
                          (pod/unpack-jar (:jar dep) ~destdir))))))))
      (-> fileset (boot/add-resource webjars-cache) boot/commit!))))

(boot/deftask tester
  "test"
  [t type KW kw "type"]
  (boot/with-pre-wrap [fileset]
    (let [[b-content pod] (->bowdlerize-pod fileset)]
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
