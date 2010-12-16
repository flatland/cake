(ns cake.main
  (:use cake
        [cake.task :only [run-task run?]]
        [cake.file :only [file global-file]]
        [uncle.core :only [in-project]]
        [clojure.contrib.condition :only [handler-case *condition*]]
        [cake.utils :only [*readline-marker*]]        
        [bake.core :only [debug?]]
        [bake.io :only [init-multi-out]]
        [bake.reload :only [reload-project-files]]
        [cake.tasks.swank :only [start-swank]]
        [cake.utils.useful :only [on-shutdown]])
  (:require [cake.tasks default global]
            [cake.project :as project]
            [cake.server :as server])
  (:import (java.lang ClassLoader)
           (java.io File)))

(defn process-command [[task readline-marker]]
  (binding [*readline-marker* readline-marker]
    (in-project {:outs *outs* :verbose (debug?) :root *root*}
      (doseq [dir ["lib" "classes" "build"]]
        (.mkdirs (file dir)))
      (handler-case :type
        (run-task (symbol (name task)))
        (handle :abort-task
          (println (name task) "aborted:" (:message *condition*)))))))

(defn start-server [port]
  (reload-project-files)
  (eval (:startup *project*))
  (on-shutdown #(eval (:shutdown *project*)))
  (init-multi-out ".cake/cake.log")
  (in-project {:outs *outs* :verbose (debug?) :root *root*}
    (when-let [autostart (get *config* "swank.autostart")]
      (start-swank autostart))
    (server/create port process-command)))
