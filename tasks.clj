(ns user
  (:use cake cake.core cake.ant
        [useful :only [abort]]
        [cake.tasks.jar :only [uberjarfile]]
        [cake.tasks.release :only [upload-to-clojars]])
  (:import [org.apache.tools.ant.taskdefs Jar Copy Move ExecTask]
           [java.io File]))

(defn bakejar []
  (file (format "bake-%s.jar" (:version *project*))))

(defn add-dev-jars [task]
  (doseq [jar (fileset-seq {:dir "lib/dev" :includes "*.jar"})]
    (add-zipfileset task {:src jar :includes "**/*.clj" :excludes "META-INF/**/project.clj"})))

(deftask uberjar
  (let [jarfile (uberjarfile)
        bakejar (bakejar)]
    (ant Jar {:dest-file bakejar}
         (add-fileset {:dir "bake"})
         (add-dev-jars))
    (ant Jar {:dest-file jarfile :update true}
         (add-fileset {:file bakejar})
         (add-dev-jars))))

(defn snapshot? [version]
  (.endsWith version "SNAPSHOT"))

(deftask gem
  "Build standalone gem package."
  (let [version (:version *project*)]
    (if (snapshot? version)
      (println "refusing to make gem since this is a snapshot version:" version)
      (do (run-task 'uberjar)
          (ant Copy {:file (uberjarfile) :tofile (file "gem/lib/cake.jar")})
          (ant Copy {:file (bakejar)     :tofile (file "gem/lib/bake.jar")})
          (ant Copy {:file (file "bin/cake") :tofile (file "gem/bin/cake")})
          (ant ExecTask {:executable "gem" :dir (file "gem")}
               (env {"CAKE_VERSION" version})
               (args ["build" "cake.gemspec"]))))))

(defn ftime [string time]
  (format (apply str (map #(str "%1$t" %) string)) time))

(defn snapshot-timestamp [version]
  (if (snapshot? version)
    (let [t (java.util.Calendar/getInstance)]
      (.replaceAll version "SNAPSHOT" (str (ftime "Ymd" t) "." (ftime "HMS" t))))
    version))

(undeftask release)
(deftask release #{uberjar gem}
  "Release project jar to github and gem package to rubygems."
  (let [version   (:version *project*)
        snapshot? (snapshot? version)
        version   (if snapshot? (snapshot-timestamp version) version)
        jar       (format "jars/cake-%s.jar" version)]
    (ant Copy {:file (uberjarfile) :tofile  (file "releases" jar)})
    (spit (file "releases/current") version)    
    (when-not snapshot? (spit (file "releases/stable") version))
    (binding [*root* "releases"]
      (git "add" jar "current" "stable")
      (git "commit" "-m" (format "'release cake %s'" (:version *project*)))
      (comment git "push"))
    (when-not snapshot?
      (let [gem (str "cake-" version ".gem")]
        (log "Releasing gem:" gem)
        (ant ExecTask {:executable "gem" :dir (file "gem")}
             (args ["push" gem]))))))
