(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'org.clojars.lukaszkorecki/mokujin)
(def version-stable (format "1.0.0.%s" (b/git-count-revs nil)))
(def version-snapshot (format "%s-SNAPSHOT" version-stable))

(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version-stable))
(def target "target")
;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path target}))

(defn ^:private jar-opts
  [{:keys [version] :as opts}]
  (assoc opts
         :lib lib
         :version version
         :jar-file jar-file
         :basis (b/create-basis)
         :class-dir class-dir
         :target target
         :src-dirs ["src"]
         #_#_:pom-data (pom-template version)))

(defn jar
  "This task will create the JAR in the `target` directory."
  [{:keys [snapshot] :as opts}]
  (let [{:keys [jar-file] :as opts'} (jar-opts {:version (if snapshot
                                                           version-snapshot
                                                           version-stable)})]
    (println (format "Cleaning '%s'..." target))
    (b/delete {:path "target"})
    (println (format "Writing 'pom.xml'..."))
    (b/write-pom opts')
    (println (format "Copying source files to '%s'..." class-dir))
    (b/copy-dir {:src-dirs ["src" "resources"] :target-dir class-dir})
    (println (format "Building JAR to '%s'..." jar-file))
    (b/jar opts')
    (println "Finished.")))

(defn install
  [{:keys [snapshot]}]
  (let [{:keys [jar-file] :as opts'} (jar-opts {:version (if snapshot
                                                           version-snapshot
                                                           version-stable)})]
    (dd/deploy {:installer :local
                :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts' [:lib :class-dir]))})))
