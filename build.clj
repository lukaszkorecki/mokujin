(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(defn log [level fmt & args]
  (if (seq args)
    (printf "[%s] %s\n" level (format fmt args))
    (printf "[%s] %s\n" level fmt)))

(let [url [:url "https://github.com/lukaszkorecki/mokujin"]
      licenses [:licenses
                [:license
                 [:name "MIT"]
                 [:url "https://opensource.org/license/mit/"]]]
      scm [:scm
           [:url "https://github.com/lukaszkorecki/mokujin"]
           [:connection "scm:git:git://github.com/lukaszkorecki/mokujin.git"]
           [:developerConnection "scm:git:ssh://git@github.com/lukaszkorecki/mokujin.git"]]]

  (def pom-template
    {"mokujin" [[:description "A very thin wrapper around clojure.tools.logging which adds MDC support"]
                url
                licenses
                scm]

     "mokujin-logback" [[:description "Logback extensions for Mokujin - easy confiugration, pre-configured appenders etc"]
                        url
                        licenses
                        scm]}))

(def lib-name->sym
  {"mokujin" 'org.clojars.lukaszkorecki/mokujin
   "mokujin-logback" 'org.clojars.lukaszkorecki/mokujin-logback})

(def version-base "1.0.0")

(defn ^:private jar-opts
  [{:keys [snapshot? lib] :as opts}]
  (let [lib-sym (get lib-name->sym lib)
        version (str version-base
                     "."
                     (b/git-count-revs nil)
                     (when snapshot? "-SNAPSHOT"))
        pom (get pom-template lib)]
    (assoc opts
           :lib lib-sym
           :version version
           :jar-file (str "target/" (name lib-sym) "-" version ".jar")
           :basis (b/create-basis)
           :class-dir "target/classes"
           :target "target"
           :src-dirs ["src"]
           :pom-data pom)))

(defn clean [{:keys [lib]}]
  (b/with-project-root (name lib)
    (log :WARN "cleaning %s" lib)
    (b/delete {:path "target"})))

(defn jar
  [{:keys [lib snapshot] :as _args}]
  (b/with-project-root (name lib)
    (log :INFO "Building %s jar " lib)
    (let [{:keys [jar-file class-dir] :as opts} (jar-opts {:lib (name lib)
                                                           :snapshot? snapshot})]

      (log :WARN "Cleaning")
      (b/delete {:path "target"})
      (log :INFO "Writing pom.xml")
      (b/write-pom opts)

      (log :INFO "Compiling")
      (b/copy-dir {:src-dirs ["src" "resources"] :target-dir class-dir})
      (b/jar opts)
      (log :INFO "Finished: %s" jar-file))))

(defn install
  [{:keys [lib snapshot]}]
  (b/with-project-root (name lib)
    (let [{:keys [jar-file] :as opts} (jar-opts {:lib (name lib)
                                                 :snapshot? snapshot})]
      (log :INFO "installing %s" jar-file)
      (dd/deploy {:installer :local
                  :artifact (b/resolve-path jar-file)
                  :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))}))))

(defn publish
  [{:keys [lib snapshot]}]
  (b/with-project-root (name lib)
    (let [{:keys [jar-file] :as opts} (jar-opts {:lib (name lib)
                                                 :snapshot? snapshot})]
      (log :INFO "publishing %s" jar-file)
      (dd/deploy {:installer :remote
                  :artifact (b/resolve-path jar-file)
                  :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))}))))
