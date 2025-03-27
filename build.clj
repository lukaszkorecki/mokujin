(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def mokujin-lib 'org.clojars.lukaszkorecki/mokujin)
(def mokujin-logback-lib 'org.clojars.lukaszkorecki/mokujin-logback)
(def version-stable (format "1.0.0.%s" (b/git-count-revs nil)))
(defn version-snapshot [suffix] (format "%s-SNAPSHOT-%s" version-stable suffix))

(def class-dir "target/classes")
(def mokujin-jar-file (fn [version] (format "target/%s-%s.jar" (name mokujin-lib) version)))
(def mokujin-logback-jar-file (fn [version] (format "target/%s-%s.jar" (name mokujin-logback-lib) version)))
(def target "target")

(def ^:private mokujin-pom-template
  [[:description "A very thin wrapper around clojure.tools.logging which adds MDC support"]
   [:url "https://github.com/lukaszkorecki/mokujin"]
   [:licenses
    [:license
     [:name "MIT"]
     [:url "https://opensource.org/license/mit/"]]]
   [:scm
    [:url "https://github.com/lukaszkorecki/mokujin"]
    [:connection "scm:git:git://github.com/lukaszkorecki/mokujin.git"]
    [:developerConnection "scm:git:ssh://git@github.com/lukaszkorecki/mokujin.git"]]])

(def ^:private mokujin-logback-pom-template
  [[:description "Logback specific utilities for Mokujin logger"]
   [:url "https://github.com/lukaszkorecki/mokujin"]
   [:licenses
    [:license
     [:name "MIT"]
     [:url "https://opensource.org/license/mit/"]]]
   [:scm
    [:url "https://github.com/lukaszkorecki/mokujin"]
    [:connection "scm:git:git://github.com/lukaszkorecki/mokujin.git"]
    [:developerConnection "scm:git:ssh://git@github.com/lukaszkorecki/mokujin.git"]]])

(defn clean [_]
  (println (format "Cleaning '%s'..." target))
  (b/delete {:path target})
  (b/delete {:path "mokujin/target"})
  (b/delete {:path "mokujin-logback/target"}))

;; Tasks for mokujin core library
(defn mokujin-jar
  [{:keys [snapshot] :as _args}]
  (let [version (if snapshot
                  (version-snapshot snapshot)
                  version-stable)
        jar-file (mokujin-jar-file version)
        opts {:lib mokujin-lib
              :version version
              :jar-file jar-file
              :basis (b/create-basis {:project "mokujin/deps.edn"})
              :class-dir class-dir
              :target target
              :src-dirs ["mokujin/src"]
              :pom-data mokujin-pom-template}]
    (println (format "Building mokujin library"))
    (println (format "Writing 'pom.xml'..."))
    (b/write-pom opts)
    (println (format "Copying source files to '%s'..." class-dir))
    (b/copy-dir {:src-dirs ["mokujin/src"] :target-dir class-dir})
    (println (format "Building JAR to '%s'..." jar-file))
    (b/jar opts)
    (println "Finished mokujin library build.")))

(defn mokujin-install
  [{:keys [snapshot]}]
  (let [version (if snapshot
                  (version-snapshot snapshot)
                  version-stable)
        jar-file (mokujin-jar-file version)
        opts {:lib mokujin-lib
              :version version
              :jar-file jar-file
              :basis (b/create-basis {:project "mokujin/deps.edn"})
              :class-dir class-dir}]
    (dd/deploy {:installer :local
                :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))})))

(defn mokujin-publish
  [{:keys [snapshot]}]
  (let [version (if snapshot
                  (version-snapshot snapshot)
                  version-stable)
        jar-file (mokujin-jar-file version)
        opts {:lib mokujin-lib
              :version version
              :jar-file jar-file
              :basis (b/create-basis {:project "mokujin/deps.edn"})
              :class-dir class-dir}]
    (dd/deploy {:installer :remote
                :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))})))

;; Tasks for mokujin-logback library
(defn mokujin-logback-jar
  [{:keys [snapshot] :as _args}]
  (let [version (if snapshot
                  (version-snapshot snapshot)
                  version-stable)
        jar-file (mokujin-logback-jar-file version)
        opts {:lib mokujin-logback-lib
              :version version
              :jar-file jar-file
              :basis (b/create-basis {:project "mokujin-logback/deps.edn"})
              :class-dir class-dir
              :target target
              :src-dirs ["mokujin-logback/src"]
              :pom-data mokujin-logback-pom-template}]
    (println (format "Building mokujin-logback library"))
    (println (format "Writing 'pom.xml'..."))
    (b/write-pom opts)
    (println (format "Copying source files to '%s'..." class-dir))
    (b/copy-dir {:src-dirs ["mokujin-logback/src"] :target-dir class-dir})
    (println (format "Building JAR to '%s'..." jar-file))
    (b/jar opts)
    (println "Finished mokujin-logback library build.")))

(defn mokujin-logback-install
  [{:keys [snapshot]}]
  (let [version (if snapshot
                  (version-snapshot snapshot)
                  version-stable)
        jar-file (mokujin-logback-jar-file version)
        opts {:lib mokujin-logback-lib
              :version version
              :jar-file jar-file
              :basis (b/create-basis {:project "mokujin-logback/deps.edn"})
              :class-dir class-dir}]
    (dd/deploy {:installer :local
                :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))})))

(defn mokujin-logback-publish
  [{:keys [snapshot]}]
  (let [version (if snapshot
                  (version-snapshot snapshot)
                  version-stable)
        jar-file (mokujin-logback-jar-file version)
        opts {:lib mokujin-logback-lib
              :version version
              :jar-file jar-file
              :basis (b/create-basis {:project "mokujin-logback/deps.edn"})
              :class-dir class-dir}]
    (dd/deploy {:installer :remote
                :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))})))

;; Combined tasks
(defn jar
  [args]
  (clean nil)
  (mokujin-jar args)
  (mokujin-logback-jar args))

(defn install
  [args]
  (jar args)
  (mokujin-install args)
  (mokujin-logback-install args))

(defn publish
  [args]
  (jar args)
  (mokujin-publish args)
  (mokujin-logback-publish args))