(ns cake.tasks.file
  (:use cake
        [cake.core :only [deftask]]
        [cake.task :only [run-task]]
        [uncle.core :only [ant add-fileset]]
        [cake.project :only [reload!]]
        [cake.file :only [file]]
        [bake.core :only [log]])
  (:import (org.apache.tools.ant.taskdefs Delete Mkdir)))

(defn clean-dir [dir]
  (when (seq (rest (file-seq dir)))
    (log "Deleting" (.getPath dir))
    (ant Delete {:dir dir})
    (.mkdirs dir)))

(deftask clean
  "Remove cake build artifacts."
  (ant Delete {:verbose true}
    (add-fileset {:dir (file) :includes "*.jar"})
    (add-fileset {:dir (file) :includes "*.war"})
    (add-fileset {:dir (file ".cake" "run") :includes "*"}))
  (doseq [dir ["classes" "build" "test/classes"]]
    (clean-dir (file dir)))
  (when (= ["deps"] (:clean *opts*))
    (clean-dir (file "lib"))
    (ant Delete {:verbose true}
         (add-fileset {:dir (file) :includes "pom.xml"})))
  (reload!))

(deftask file
  "Invoke a file task."
  (run-task (first (:file *opts*))))
