(ns tasks
  (:use cake cake.core cake.file uncle.core
        [cake.utils :only [git]]
        [bake.core :only [log]]
        [cake.tasks.jar :only [build-uberjar jars uberjarfile]]
        [cake.tasks.release :only [upload-to-clojars]]
        [cake.tasks.version :only [snapshot? snapshot-timestamp]])
  (:import [org.apache.tools.ant.taskdefs Jar Copy Move ExecTask]
           [java.io File]))

(defn bakejar []
  (file "bake.jar"))

(defn clojure-jar []
  (str "clojure-" (.replace (clojure-version) "SNAPSHOT" "*") ".jar"))

(defn add-dev-jars [task]
  (doseq [jar (fileset-seq {:dir "lib/dev" :includes "*.jar"})]
    (add-zipfileset task {:src jar :includes "**/*.clj" :excludes "META-INF/**/project.clj"})))

(undeftask uberjar)
(deftask uberjar #{jar}
  "Create a standalone jar containing all project dependencies."
  (build-uberjar (jars :excludes [(clojure-jar)]))
  (let [jarfile (uberjarfile)
        bakejar (bakejar)]
    (ant Jar {:dest-file bakejar}
         (add-fileset {:dir "bake"})
         (add-dev-jars))
    (ant Jar {:dest-file jarfile :update true}
         (add-fileset {:file bakejar})
         (add-dev-jars))))

(deftask gem
  "Build standalone gem package."
  (let [version (:version *project*)]
    (if (snapshot? version)
      (println "refusing to make gem since this is a snapshot version:" version)
      (do (invoke uberjar)
          (ant Copy {:file (uberjarfile) :tofile (file "gem/lib/cake.jar")})
          (ant Copy {:file (bakejar)     :tofile (file "gem/lib/bake.jar")})
          (ant Copy {:tofile (file "gem/lib/clojure.jar")}
               (add-fileset {:dir "lib" :includes (clojure-jar)}))
          (ant Copy {:file (file "bin/cake") :tofile (file "gem/bin/cake")})
          (ant ExecTask {:executable "gem" :dir (file "gem")}
               (env {"CAKE_VERSION" version})
               (args ["build" "cake.gemspec"]))))))

(undeftask release)
(deftask release #{uberjar gem tag}
  "Release project jar to github and gem package to rubygems."
  (let [version   (:version *project*)
        snapshot? (snapshot? version)
        version   (if snapshot? (snapshot-timestamp version) version)
        jar       (format "jars/cake-%s.jar" version)]
    (ant Copy {:file (uberjarfile) :tofile (file "releases" jar)})
    (spit (file "releases/current") version)
    (when-not snapshot?
      (ant Copy {:file (file "bin" "cake") :tofile (file "releases" "cake")})
      (spit (file "releases/stable") version))
    (binding [*root* (file "releases")]
      (git "add" jar "cake" "current" "stable")
      (git "commit" "--allow-empty" "-m" (format "'release cake %s'" (:version *project*)))
      (git "push"))
    (when-not snapshot?
      (let [gem (str "cake-" version ".gem")]
        (log "Releasing gem:" gem)
        (ant ExecTask {:executable "gem" :dir (file "gem")}
             (args ["push" gem]))))))
