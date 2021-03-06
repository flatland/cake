(ns cake.tasks.search
  (:use cake
        cake.core
        [bake.core :only [log]]
        [clojure.string :only [join]]
        [clojure.java.io :only [as-url file copy reader]])
  (:require [sherlock.core :as sherlock])
  (:import java.net.HttpURLConnection))

(defn select-repos [repos]
  (let [repositories (:repositories *project*)]
    (if (seq repos)
      (select-keys repositories repos)
      repositories)))

(defn hash-url [url]
  (as-url (str (sherlock/remote-index-url url) ".md5")))

(defn hash-location [url]
  (file (str (sherlock/index-location url) ".md5")))

(defn new-hash [url]
  (copy (reader (hash-url url)) (hash-location url)))

(defn published? [url]
  (try
    (= HttpURLConnection/HTTP_OK
       (.getResponseCode
        (doto (.openConnection (hash-url url))
          (.setRequestMethod "HEAD"))))
    (catch Exception _ false)))

(defn download-index [url]
  (sherlock/update-index url)
  (new-hash url))

(defn init-index [id url]
  (when (sherlock/download-needed? url)
    (log (str "Downloading index for " id ". This might take a bit."))
    (download-index url)))

(defn update-message [id url]
  (let [hash-location (hash-location url)]
    (when (and (.exists hash-location)
               (not= (slurp hash-location)
                     (slurp (hash-url url))))
      (println (format "The index for %s is out of date. Pass --update to update it." id)))))

(defn update-repo [id url]
  (log (str "Updating index for " id ". This might take a bit."))
  (download-index url))

(defn identifier [{:keys [group-id artifact-id]}]
  (if group-id
    (str group-id "/" artifact-id)
    artifact-id))

(defn print-results [id page results]
  (when (seq results)
    (println " == Results from" id "-" "Showing page" page "/"
             (-> results meta :_total-hits (/ sherlock/*page-size*) Math/ceil int) "total")
    (doseq [{:keys [version description] :as artifact} results]
      (println (str "[" (identifier artifact) " \"" version "\"]") description))
    (prn)))

(defn batch-update [id url update]
  (init-index id url)
  (when update (update-repo id url))
  (update-message id url))

(deftask search
  "Search maven repos for artifacts."
  "Search takes a query that is treated as a lucene search query,
   allowing for simple string matches as well as advanced lucene queries.
   The first time you run this task will take a bit as it has to fetch the
   lucene indexes for your maven repositories.
   Options:
     --page   -- The page number to fetch (each page is 10 results long).
     --repos  -- ids of repositories to search delimited by commas.
     --update -- Updates indices for repos you specify."
  {[page] :page repos :repos search :search update :update}
  (let [page (if page (Integer. page) 1)]
    (binding [sherlock/*page-size* 10]
      (doseq [[id url] (select-repos repos) :when (published? url)]
        (batch-update id url update)
        (print-results
         id page
         (sherlock/get-page page (sherlock/search url (join " " search) page)))))))

(deftask latest
  "Find the latest version of an artifact."
  "Pass the name of a dependency and cake will search the lucene indexes for it
   and return the latest version. You can pass --repos which is expected to be a
   comma delimited list of repository names. If it is not passed, all repositories
   will be searched."
  {[term] :latest, repos :repos update :update}
  (let [repos (filter (comp published? second) (select-repos repos))
        [group-id artifact-id] (.split term "/")
        query (str "g:" group-id "* AND a:" (or artifact-id group-id))]
    (doseq [[id url] repos]
      (batch-update id url update))
    (let [result (first (sort-by :version (comp unchecked-negate compare)
                                 (filter (comp #{term} identifier)
                                         (mapcat #(sherlock/search % query 10 Integer/MAX_VALUE)
                                                 (map last repos)))))]
      (println
       (if result
         (str "[" (identifier result) " " (:version result) "]")
         (println "Nothing was found."))))))