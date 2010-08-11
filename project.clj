(defproject cake "0.3.12-SNAPSHOT"
  :description "Save your fork, there's cake!"
  :dependencies [[clojure "1.2.0-RC2"]
                 [ordered-set "0.1.0"]
                 [org.apache.ant/ant "1.8.1"]
                 [org.apache.ant/ant-jsch "1.8.1"]
                 [org.clojars.ninjudd/maven-ant-tasks "2.1.0" :exclusions [ant/ant]]
                 [org.apache.maven.plugins/maven-shade-plugin "1.3.3"]]
  :dev-dependencies [[clojure-complete "0.1.2" :exclusions [clojure]]
                     [clojure-useful   "0.2.5" :exclusions [clojure]]])
