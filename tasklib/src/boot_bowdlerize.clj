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
(def bower-repo "bower_components")
(def bower-edn "bower.edn")
(def local-edn "local.edn")
(def npm-edn "npm.edn")
(def polymer-edn "polymer.edn")
(def resources-edn "resources.edn")
(def webjars-edn "webjars.edn")
(def webjar-repo "webjars")

;; https://github.com/bower/spec/blob/master/config.md
;;TODO, maybe: accept clojure map for .bowerrc, bower.json

(defn- config-file?
  [f]
  #_(println "config-file? " (.getName f))
  #_(let [fns (load-file (.getName f))]
    )
  f)

(defn ->bowdlerized-fs
  "return bowdlerize-edn file with fs; create former if necessary,
  adding to (new) fs."
  [fileset verbose]
  (if verbose (util/info (str "->bowdlerized-fs\n")))
  (let [bowdlerize-edns (->> fileset
                           boot/input-files
                           (boot/by-name [bowdlerize-edn]))]
    (println "bowdlerize-edn files: " bowdlerize-edns)
    (condp = (count bowdlerize-edns)
      0 (let [tmp-dir (boot/tmp-dir!)
              f (io/file tmp-dir bowdlerize-edn)]
          (if verbose (util/info (str "Creating bowdlerize.edn\n")))
          (spit f {})
          [(-> fileset
               ;; (boot/add-resource tmp-dir)
               (boot/add-source tmp-dir)
               boot/commit!)
           f
           (-> f slurp read-string)])
      1 (do (println "FOUND " bowdlerize-edn)
            (let [bedn (boot/tmp-file (first bowdlerize-edns))]
              [fileset
               bedn
               (-> bedn slurp read-string)]))
      (throw (Exception. (str "only one " bowdlerize-edn " file allowed"))))))

;; (defn- ->bowdlerize-content
;;   "get or create bowdlerize.edn"
;;   [fileset verbose]
;;   (let [[bowdlerize-f newfs] (->bowdlerized-fs fileset verbose)]
;;     [newfs bowdlerize-f (-> bowdlerize-f slurp read-string)]))
;;     ;; [fileset bowdlerize-f (-> bowdlerize-f slurp read-string)]))

(defn- cache-bowdlerize
  [fileset clean verbose]
  (let [cache-dir (boot/cache-dir! :bowdlerize/bowdlerize)]
    (if clean (do (if verbose (util/info (str "Clearing bowdlerize cache\n")))
                  (boot/empty-dir! cache-dir)
                  (boot/commit! fileset)))
    (let [bowdlerize-f (io/file (.getPath cache-dir) bowdlerize-edn)]
      (if (.exists bowdlerize-f)
        (do #_(if verbose (util/info (str "Found cached " bowdlerize-edn "\n")))
            ;; [(-> fileset (boot/add-source cache-dir) boot/commit!) ;;FIXME: if keep, ...
            [(-> fileset (boot/add-resource cache-dir) boot/commit!)
             bowdlerize-f (-> bowdlerize-f slurp read-string)])
        (let [[bowdlerize-f newfs] (->bowdlerized-fs fileset verbose)]
          [fileset bowdlerize-f (-> bowdlerize-f slurp read-string)])))))

(defn ->bowdlerize-pod
  "Given a fileset, return the boot TempFile for bowdlerize.edn,
  its content, and a pod with deps for webjars if any."
  [fileset]
  (let [edn-fs (->> fileset
                    boot/input-files
                    (boot/by-name [bowdlerize-edn]))
        _ (condp = (count edn-fs)
            0 (throw (Exception. (str bowdlerize-edn " file not found")))
            1 true
            (throw (Exception. (str "only one " bowdlerize-edn " file allowed"))))
        edn-f (first edn-fs)
        edn-content (-> (boot/tmp-file edn-f) slurp read-string)
        coords (if-let [webjars (:webjars edn-content)]
                 (reduce-kv (fn [sofar k v]
                              (conj sofar (reduce (fn [ssofar vv]
                                                    (conj ssofar (if (keyword? (first vv))
                                                                   (last vv) vv)))
                                                  {} (vals v))))
                              {}
                              webjars)
                 '())
        ;; _ (println "COORDS: " coords)
        pod-env (update-in (boot/get-env) [:dependencies] #(identity %2)
                           (concat '[[boot/aether "RELEASE"]
                                     [boot/core "RELEASE"]
                                     [boot/pod "RELEASE"]]
                                   coords))
        pod (future (pod/make-pod pod-env))]
    [edn-f edn-content pod]))

(defn bower-meta
  [pkg]
  (println "\tBOWER-META") ;; bower-meta)
  (defonce local-bower  (io/as-file "./node_modules/bower/bin/bower"))
  (defonce global-bower (io/as-file "/usr/local/bin/bower"))
  (defonce bcmd        (cond (.exists local-bower) (.getPath local-bower)
                           (.exists global-bower) (.getPath global-bower)
                           :else "bower"))
  (let [;; tgt     (boot/tmp-dir!)
        ;; pod-env (update-in (boot/get-env) [:dependencies] conj '[cheshire "5.5.0"])
        ;; bower-pod    (future (pod/make-pod pod-env))
        info (sh bcmd "info" "-j" pkg)
        j (json/parse-string (:out info) true)
        ]
    j))

(defn- bower->uris
  [repo bower-meta]
  (println "BOWER->URIS: " bower-meta)
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
                                ;; (println "GET-BOWER-PKG: " k (type k) v (type v))
                                (let [pkg (cond
                                            (symbol? k)
                                            (cond
                                              (vector? v)
                                              ;; {icon [:iron-icon "PolymerElements/iron-icon"]}
                                              [(first v) (last v)]
                                              ;; [(subs (str (first v)) 1) (last v)]

                                              (string? v)
                                              ;; {moment "moment"}
                                              [(keyword k) v]

                                              (map? v)
                                              ;; less
                                              ;; {:bower less, :file bower_components/less/dist/less.js}
                                              [(keyword k) (:bower v)]

                                              :else (throw (IllegalArgumentException.
                                                            (str "Val must be vec, string, or map: " v))))

                                            (string? k)
                                            [(keyword k) k]

                                            :else (throw (IllegalArgumentException.
                                                          (str "Key must be sym or string: " k))))]
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

(defn- stencilize-bower
  [props]
  ;; (println "STENCILIZE-BOWER: " props)
  (if (:file props)
    (do ;;(println "FILE: " (:file props))
        props)
    (let [pkg (:bower props)
          bm (bower-meta pkg)
          kw (keyword (-> bm :latest :name))
          ;; _ (println "kw: " kw)
          main (-> bm :latest :main)
          ;; _ (println "main: " main)

          ;; poly (str/starts-with? (:bower ns-config) "Polymer")
          ;; _ (println "poly: " poly)
          uris (bower->uris bower-repo bm)
          ;; _ (println "URIS: " uris)
          ]
      (assoc props :file (str (first uris)))
      )))

(defn- stencilize-webjar
  [fileset props]
  ;; (println "STENCILIZE-WEBJAR: " props)
  (let [f (str (:file props) "/bower.json" )
        ;; _ (println "F: " f)
        webjar-f (-> fileset (boot/tmp-get f))]
    ;; _ (println "WEBJAR F: " webjar-f)
    (if (nil? webjar-f)
      (util/fail (format "WEBJAR - not found: %s\n" f))
      (let [jf (boot/tmp-file webjar-f)
            ;; _ (println "WEBJAR JF: " jf)
            s (-> (boot/tmp-file webjar-f) slurp)
            j (json/parse-string s true)
            main (-> j :main)
            file (cond
                   (string? main) (str/join "/" [(:file props) main])
                   (vector? main)
                   (do (util/warn (format "Multiple main entries in bower.json; you must hand-tune your config spec: %s\n" f))
                       (format "PICK ONE (from bower.json main): %s\n" main))
                   :else (do (util/fail (format "Strange :main in bower.json, not string/vector: %s\n" f))
                             (str "WEBJAR PKG PROBABLY BROKEN!")))
            normed (assoc props :file file)
            ]
        ;; (println "S: " s)
        ;; (println "FILE: " file)
        ;; (println "NORMED: " normed)
        normed))))

(defn- stencilize-local
  [props]
  (println "STENCILIZE-LOCAL: " props)
  props)

(defn- stencilize-npm
  [props]
  (println "STENCILIZE-NPM: " props)
  props)

(declare typify)

(defn- stencilize-props
  [fileset props]
  ;; (println "STENCILIZE-PROPS: " (:config-kw props))
  (let [nprops (condp = (:config-kw props)
                 :bower (stencilize-bower props)
                 :polymer (stencilize-bower (assoc props :polymer true))
                 :webjars props  ;;  (stencilize-webjar fileset (assoc props :webjar true))
                 :local (stencilize-local props)
                 :npm (stencilize-npm props)
                 :else (throw (IllegalArgumentException. "Unrecognized config kw: " (:config-kw props))))
        ;; _ (println "NPROPS: " nprops)
        nprops (typify nprops)]
    nprops))

(defn- stencilize-features
  [fileset nsx configs]
  ;; (println "\nSTENCILIZE-FEATURES: " nsx)
  (let [m (reduce (fn [sd cfg]
                    (merge sd (get cfg nsx)))
                  {}
                  configs)
        mm (flatten (reduce-kv (fn [sd k v]
                                 (let [props (stencilize-props fileset v)]
                                   ;; (println "K: " k " " props)
                                   (merge sd (conj {:name k} props))))
                               []
                               m))
        ;; _(println "MM: " mm)
        ]
    mm))

(defn- stencilize-maps
  "convert config map to data map suitable for stencil"
  [fileset bower-base bowdlerize-edn]
  (let [configs (vals bowdlerize-edn)
        nss (sort (seq (set (reduce (fn [seed v] (if (map? v)
                                        (concat seed (keys v))
                                        seed))
                         #{} configs))))
        ;; _ (println "       NSS: " nss)

        merged-configs (reduce (fn [seed nsx]
                                   (conj seed {:app-ns nsx
                                               :names (into [] (stencilize-features
                                                                fileset nsx configs))}))
                               []
                               nss)

        ;; _ (println "MERGED NSS: " (sort (map #(:app-ns %) merged-configs)))
        ;; _ (println "MERGED:")
        ;; _ (pp/pprint merged-configs)
        ]
    merged-configs))

(defn- webjar->file
  [fileset coords]
  (let [maven (str (first coords))
        groupid (subs maven 0 (str/index-of maven "/"))
        artifactid (subs maven (+ 1 (str/index-of maven "/")))
        version (str (last coords))
        root (str/join "/" [webjar-repo
                            "META-INF/resources/webjars"
                            artifactid
                            version])
        bower-json (str root "/bower.json")
        webjar-json-f (-> fileset (boot/tmp-get bower-json))]
    (if (nil? webjar-json-f)
      (util/fail (format "WEBJAR - not found: %s\n" bower-json))
      (let [webjar-json-str (-> (boot/tmp-file webjar-json-f) slurp)
            webjar-json (json/parse-string webjar-json-str true)
            main (-> webjar-json :main)
            file (cond
                   (string? main) (str/join "/" [root (if (str/starts-with? main "./")
                                                        (subs main 2)
                                                        main)])
                   (vector? main)
                   (do (util/warn (format "Multiple main entries in bower.json; you must hand-tune your config spec: %s\n" bower-json))
                       nil)
                   :else (do (util/fail
                              (format "Strange :main in bower.json, not string/vector: %s\n" bower-json))
                             nil))
            ]
        (if (nil? file)
          nil
          (if (-> fileset (boot/tmp-get file))
            file
            (throw (IllegalArgumentException. (str "File not found: " file)))))))))

(defn- normalize-config
  "Normalize configuration form.
For example, from: :bower materialize {:bower materialize, :file bower_components/Materialize/bin/materialize.css}
to {materialize {:bower materialize, :file bower_components/Materialize/bin/materialize.css, :config-kw :bower}}"
  [fileset pkg-mgr-kw k v]
  (println "normalize-config: " pkg-mgr-kw k v)
  (let [prop-kw (condp = pkg-mgr-kw
                  :webjars :coords
                  :polymer :coords
                  :bower :bower
                  :npm   :pkg
                  :local :file)]
    (println "FIX K: " k (type k))
    (println "FIX V: " v (type v))
    (cond
      (symbol? k)
      (cond
        (string? v)  ;; :bower, :local
        [k {prop-kw v :config-kw pkg-mgr-kw}]

        (map? v) ;; bower
        {k (assoc v :config-kw pkg-mgr-kw)}

        (vector? v)
        (condp = pkg-mgr-kw
          :polymer {k {:kw (first v)
                       :file (bower->uris v)
                       :bower (last v) :config-kw pkg-mgr-kw}}
          :webjars (if (keyword? (first v))
                     (do ;;(println "FOO: " v)
                       {k {:kw (first v)
                           :file (webjar->file fileset (last v))
                           prop-kw (last v)
                           :config-kw pkg-mgr-kw}})
                     {k {:file (webjar->file fileset v) prop-kw v :config-kw pkg-mgr-kw}})
          :else (throw (IllegalArgumentException. (str ))))

        :else (throw (IllegalArgumentException. (str "Val must be string, map, or vector: " v))))

      (vector? k) ;; webjar coord vector
      nil

      (string? k) ;; bower pkg string
      (do
          ;; e.g. "PolymerElements/paper-input", "webcomponentsjs"
        (cond
          (string? v)
          (do
            [k {:bower v :config-kw pkg-mgr-kw}])

          (map? v)
          (do
            (let [x (reduce-kv (fn [seed kk vv]
                                 (cond
                                   (vector? vv)
                                   ;; e.g.  [:paper-textarea
                                   ;;        "bower_components/paper-input/paper-textarea.html"]
                                   (conj seed [kk {:kw (first vv)
                                                   :file (last vv)
                                                   :bower k
                                                   :config-kw pkg-mgr-kw}])

                                   (string? vv)
                                   (conj seed [kk {:bower k :file vv :config-kw pkg-mgr-kw}])))
                               {} v)]
              x))
          :else (throw (IllegalArgumentException. (str "Val must be string or map: " v)))))

      :else (throw (IllegalArgumentException. (str "Key must be symbol or string: " k))))))

;;FIXME: for webjars, read the bower.json to get path
(defn- normalize-configs
  [fileset configs]
  (println "NORMALIZE-CONFIGS: " configs)
  (let [norm
        (reduce-kv (fn [seed k v]
                     ;; (println "K: " k)
                     ;; (println "V: " v)
                     (let [newv (reduce-kv
                                 (fn [sdsd kk vv]
                                   ;; (println "KK: " kk)
                                   ;; (println "VV: " vv)
                                   (let [newvv (reduce-kv
                                                (fn [sdsdsd kkk vvv]
                                                  ;; (println "KKK: " kkk)
                                                  ;; (println "VVV: " vvv)
                                                  (let [m (normalize-config fileset k kkk vvv)]
                                                    (println "normalized config: " m)
                                                    (conj sdsdsd m)))
                                                {} vv)]
                                     (assoc sdsd kk newvv)))
                                 {} v)]
                       (assoc seed k newv)))
                   {} configs)]
    norm))

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
;;         ]
;;     ;; (println "NORMED:")
;;     ;; (pp/pprint normbowers)
;;     ;; normbowers))
;; foo))
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
  "read the edn file for the pkg mgr (e.g. bower.edn), return a pod(?)"
  [pkg-mgr fileset]
  ;; (println "->PKG-MGR-POD: " pkg-mgr)
  (let [edn
        (condp = pkg-mgr
              :bower bower-edn
              :local local-edn
              :npm   npm-edn
              :polymer polymer-edn
              :webjars webjars-edn)
        edn-fs (->> fileset boot/input-files (boot/by-name [edn]))]
    (condp = (count edn-fs)
      0 [nil nil]
      1 (let [edn-f (boot/tmp-file (first edn-fs))
              edn-content (-> edn-f slurp read-string)
              coords (if (= pkg-mgr :webjars)
                       (->> edn-content vals (reduce merge) vals)
                       '())
              pod-env (update-in (boot/get-env) [:dependencies] #(identity %2)
                                 (concat '[[boot/aether "RELEASE"]
                                           [boot/core "RELEASE"]
                                           [boot/pod "RELEASE"]]
                                         coords))
              pod (future (pod/make-pod pod-env))]
          [edn-content pod])
      (throw (Exception. (str "only one " edn " file allowed"))))))

;; (defn prep-for-stencil
;;   [& configs]
;;   ;; (println "prep-for-stencil: " configs)
;;   (let [nss (set (map #(-> % :ns) configs))
;;         ;; _ (println "nss: " nss)
;;         res (into [] (for [ns- nss]
;;                        (do ;; (println "ns- " ns-)
;;                        (let [ns-cfgs (filter #(= ns- (-> % :ns)) configs)]
;;                          {:config-ns ns-
;;                           :config
;;                           (into [] (for [ns-cfg ns-cfgs]
;;                                      ns-cfg))}))))]
;;     ;; (println "FOR STENCIL:")
;;     ;; (pp/pprint res)
;;     res))

(defn- typify
  [config]
  (if (:file config)
    (merge config
           (cond
             (str/ends-with? (:file config) ".js")
             {:js true}

             (str/ends-with? (:file config) ".css")
             {:css true}

             :else {}))
    config))


  ;; (for [cfg config-maps]
  ;;   (do (println "TYPIFY: " cfg)
  ;;       (merge cfg
  ;;              {:config (for [config (:config cfg)]
  ;;                         (merge config
  ;;                                (if (str/ends-with? (:uri config) ".js")
  ;;                                  {:js true})
  ;;                                (if (str/ends-with? (:uri config) ".css")
  ;;                                  {:css true})))}))))

(boot/deftask cache
  "control bowdlerize.edn cache: retrieve, save, clean"
  [c clean bool "clean config - clear cache first"
   t trace bool "dump cache to stdout if verbose=true"
   r retrieve bool "retrieve cached master edn config file and sync to fileset"
   s save bool "save master edn config file from fileset to cache"
   v verbose bool "verbose"]
  ;; default to retrieve
  (if verbose (util/info (str "TASK: cache " clean "\n")))
  (if (and retrieve save)
    (util/exit-error
     (util/fail "boot-bowdlerize/cache: only one of :retrieve and :save may be specified\n")))
  (let [retrieve (or retrieve (not save))
        save (or (not retrieve) save)]
    (boot/with-pre-wrap fileset
      (let [cache-dir (boot/cache-dir! :bowdlerize/bowdlerize)]
        (if clean
          (do (if verbose (util/info (str "Clearing bowdlerize cache\n")))
              (boot/empty-dir! cache-dir)
              (boot/commit! fileset))
          (if retrieve
            (do ;;(println "RETRIEVING")
                (let [bowdlerize-f (io/file (.getPath cache-dir) bowdlerize-edn)]
                  (if (.exists bowdlerize-f)
                    (do (if verbose
                          (let [bowdlerize-content (-> bowdlerize-f slurp read-string)]
                            (util/info (str "Retrieving cached bowdlerize.edn\n"))
                            (if trace (util/info (with-out-str (pp/pprint bowdlerize-content))))))
                    ;; if keep
                        (-> fileset (boot/add-resource cache-dir) boot/commit!))
                    ;; else
                        ;; (-> fileset (boot/add-source cache-dir) boot/commit!))
                    fileset)))
            (if save
              (do ;;(println "SAVING")
                  (let [[bowdlerize-f bowdlerize-content pod] (->bowdlerize-pod fileset)]
                    (if verbose (do (util/info (str "Caching bowdlerize.edn\n"))
                                    (if trace (util/info (with-out-str (pp/pprint bowdlerize-content))))))
                    (let [f (io/file (.getPath cache-dir) bowdlerize-edn)]
                      (spit f (with-out-str (pp/pprint bowdlerize-content)))))
                    ;; commit?
                    fileset))))))))

(boot/deftask config
  "generate clojure runtime config files.  these are .clj files that
  should be :required for access to config vars."
  [c config-syms CONFIG-SYMS #{sym} "config namespaced sym"
   b repo PATH str "bower components repo path, default: bower_components"
   o outdir PATH str "install dir, default: classes"
   v verbose bool "print trace msgs"]
  ;; (println "CONFIG")
  (boot/with-pre-wrap fileset
    (let [tmp-dir (boot/tmp-dir!)]
      (boot/empty-dir! tmp-dir)
      (let [[bowdlerize-f bowdlerize-content pod] (->bowdlerize-pod fileset)
            path     (boot/tmp-path bowdlerize-f)
            in-file  (boot/tmp-file bowdlerize-f)
            out-file (io/file tmp-dir path)]
        (let [edn-forms (vals bowdlerize-content)
              config-maps (stencilize-maps fileset repo bowdlerize-content)]
          (doseq [config-map config-maps]
            (do ;;(println "CONFIG-MAP")
                ;;(pp/pprint config-map)
                ;; (println "ENV: " (boot/get-env))
                (let [config-file-name (str outdir (ns->filestr (-> config-map :app-ns)) ".clj")
                      ;; _ (println "CFG FILE: " config-file-name)
                      config-file (stencil/render-file "boot_bowdlerize/bower.mustache"
                                                       config-map)
                      out-file (doto (io/file tmp-dir config-file-name) io/make-parents)]
                  (spit out-file config-file))))))
      (-> fileset (boot/add-resource tmp-dir) boot/commit!))))

(boot/deftask info
  "Print bower info for package (-p package-name-string)"
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
              uri (str/join "/" [~bower-repo (-> j :latest :name) (-> j :latest :main)])
              ]
          (pp/pprint j)))
      fileset)))

(boot/deftask install-bower
  "Cache bower packages"
  [c clean-cache bool "clean reinstall (empty cache at start)"
   p pkg-name PKG str "package name"
   t pkg-type PGKMGR kw "one of :bower, :npm :polymer, or :webjars; default is all 4)"
   v verbose bool "verbose"]
  ;; (println "install-bower: " pkg-name pkg-type)
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
      (let [[bowdlerize-f edn-content pod] (->bowdlerize-pod fileset)
            bower-specs {pkg-type (get edn-content pkg-type)}
            bower-pkgs (if pkg-name
                         (list pkg-name)
                         (get-bower-pkgs bower-specs))]
        (println "bower-specs: " bower-specs)
        (println "bower-pkgs: " bower-pkgs)
        (if (empty? bower-pkgs)
          fileset
          (do (if verbose (util/info (str "Installing " pkg-type " packages\n")))
              (pod/with-eval-in @pod
                (require '[boot.pod :as pod] '[boot.util :as util]
                         '[clojure.java.io :as io] '[clojure.string :as str]
                         '[clojure.java.shell :refer [sh]])
                (doseq [bower-pkg '~bower-pkgs]
                  (let [_ (println "PKG: " bower-pkg)
                        seg (subs (str (first bower-pkg)) 1)
                        path (str ~bower-repo "/" (last bower-pkg))
                        repo-file (io/file ~(.getPath bower-cache) seg)]
                    (println "REPO-FILE: " repo-file)
                    ;;(println "PATH: " path)
                    (if (.exists repo-file)
                      (if ~verbose (util/info (format "Found cached bower pkg: %s\n" bower-pkg)))
                      (let [c [~bcmd "install" (fnext bower-pkg) :dir ~(.getPath bower-cache)]]
                        (println "bower cmd: " c)
                        (if ~verbose (util/info (format "Installing bower pkg:   %s\n" bower-pkg)))
                        (apply sh c)))))))))
      (-> fileset (boot/add-asset bower-cache) boot/commit!))))

(boot/deftask ^:private install-npm
  "Install npm packages as assets"
  [c clean-cache bool "clean reinstall (empty cache at start)"
   v verbose bool "verbose"]
  (let [tmp-dir     (boot/tmp-dir!)
        pkg-cache (boot/cache-dir! :bowdlerize/npm)
        _ (if clean-cache (do (if verbose (util/info (str "Cleaning npm cache\n")))
                              (boot/empty-dir! pkg-cache)))
        local-npm  (io/as-file "./node_modules/npm/bin/npm")
        global-npm (io/as-file "/usr/local/bin/npm")
        bcmd        (cond (.exists local-npm) (.getPath local-npm)
                          (.exists global-npm) (.getPath global-npm)
                          :else "npm")]
    (boot/with-pre-wrap fileset
      (boot/empty-dir! tmp-dir)
      (let [[bowdlerize-f edn-content pod] (->bowdlerize-pod fileset)
            npm-specs {:npm (get edn-content :npm)}
            npm-pkgs (get-bower-pkgs npm-specs)]
        (if (empty? npm-pkgs)
          fileset
          (do (util/info (str "Installing :npm packages\n"))
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
                      (if ~verbose (util/info (format "Found cached npm pkg: %s\n" npm-pkg)))))))
              (-> fileset (boot/add-asset pkg-cache) boot/commit!)))))))

(boot/deftask ^:private install-webjars
  "Unpack webjars as assets"
  [c clean-cache bool "clean reinstall (empty cache at start)"
   v verbose bool "verbose"]
  (let [tmp-dir     (boot/tmp-dir!)
        webjars-cache (boot/cache-dir! :bowdlerize/webjars)
        _ (if clean-cache (do (if verbose (util/info (str "Cleaning webjars cache\n")))
                              (boot/empty-dir! webjars-cache)))
        destdir  (str (.getPath webjars-cache) "/" webjar-repo)]
    (boot/with-pre-wrap [fileset]
      (util/info (str "Installing :webjars\n"))
      (boot/empty-dir! tmp-dir)
      (let [[bowdlerize-f edn-content pod] (->bowdlerize-pod fileset)]
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
                  (let [;; _ (println "DEP: " dep)
                        coord (:dep dep)
                        maven (str (first coord))
                        groupid (subs maven 0 (str/index-of maven "/"))
                        artifactid (subs maven (+ 1 (str/index-of maven "/")))
                        version (str (last coord))
                        path (str/join "/" [~webjar-repo
                                            "META-INF/resources/webjars"
                                            artifactid
                                            version])]
                    (if (not (.exists (io/file ~(.getPath webjars-cache) path)))
                      (do (if ~verbose (util/info (format "Installing webjar:   %s\n"
                                                          coord)))
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
    ;;(comp (cache :retrieve true :verbose verbose :clean clean)
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
            [newfs bowdlerize-f bowdlerize-content] (->bowdlerized-fs fileset verbose)]
        ;; (println "FOO")
        ;; (pod/with-eval-in @pod
        ;;   (require '[boot.util :as util]
        ;;            '[clojure.pprint :as pp]
        ;;            '[clojure.java.io :as io])
          ;; (println "BAR")
        (if (nil? edn-content)
          fileset
          (if (get bowdlerize-content pkg-mgr)
            (if verbose (util/info (str bowdlerize-edn " already elaborated with " pkg-mgr "\n")))
            (do (if verbose (util/info
                             (str "Elaborating " bowdlerize-edn " with " pkg-mgr " stanza\n")))
                (let [path     (.getName bowdlerize-f)
                      out-file (io/file (.getPath tmp-dir) path)]
                  (spit out-file (with-out-str
                                   (pp/pprint
                                    (assoc bowdlerize-content
                                           pkg-mgr edn-content)))))
                (-> newfs (boot/add-source tmp-dir) boot/commit!))))))))

(boot/deftask metaconf
  "Construct transient master .edn config file (bowdlerize.edn)"
  [c clean bool "clean config - clear cache first.  default: true"
   C no-clean bool "do not clear cache.  default: false"
   p pkg-mgrs PGKMGRS #{kw} "only these pkg mgrs (:bower, :npm :polymer, or :webjars; default is all 4)"
   v verbose bool "verbose"]
  (if (and clean no-clean) (util/exit-error (util/fail "boot-bowdlerize/metaconf: only one of :clean and :no-clean allowed")))
  ;; (println "pkg-mgrs: " pkg-mgrs)
  (let [pkg-mgrs (if pkg-mgrs (set pkg-mgrs) #{:bower :npm :polymer :local :webjars})
        pipeline (map #(metaconf-impl :clean clean :pkg-mgr % :verbose verbose) pkg-mgrs)
        clean (or clean (not no-clean))]
    (comp (cache :retrieve true :verbose verbose :clean clean)
          (apply comp pipeline)
          (cache :save true :verbose verbose))))

(boot/deftask normalize
  "Normalize"
  [v verbose bool "verbose"]
  (println "NORMALIZE")
;;  (comp (cache :retrieve true :verbose verbose :clean clean)
  (boot/with-pre-wrap fileset
    (let [tmp-dir (boot/tmp-dir!)
          [bowdlerize-f edn-content pod] (->bowdlerize-pod fileset)
          [newfs bowdlerize-f bowdlerize-content] (->bowdlerized-fs fileset verbose)]
      (println "bowdlerize-f: " bowdlerize-f)
      (let [norm (assoc (normalize-configs fileset edn-content)
                        :normalized true)
            out-file (io/file (.getPath tmp-dir) (.getName bowdlerize-f))]
        (spit out-file (with-out-str (pp/pprint norm))))
      ;; FIXME if keep ...
    (-> fileset (boot/add-resource tmp-dir) boot/commit!)))
    ;; (-> fileset (boot/add-source tmp-dir) boot/commit!)))
  ;;(cache :save true :verbose verbose)
  )

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
                                       '[[boot/aether "RELEASE"]
                                         [boot/pod "RELEASE"]
                                         [boot/core "RELEASE"]]))
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
    (let [[bowdlerize-f b-content pod] (->bowdlerize-pod fileset true)]
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
