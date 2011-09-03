(ns cake.file-test
  (:use clojure.test cake cake.file))

(defmacro with-project [bindings & body]
  `(binding [*project* {:name "project-stub", :version "0.0.0"}
             *root*    "/home/project"]
     (let ~bindings
       ~@body)))

(deftest file-fn
  (testing "single string"
    (with-project [s "foo/bar/baz"]
      (is (= (str *root* "/" s)
             (.toString (file s))))))

  (testing "multiple strings"
    (with-project [a "foo", b "bar"]
      (is (= (str *root* "/" a "/" b)
             (.toString (file a b))))))

  (testing "single file"
    (with-project [s "foo", f (java.io.File. s)]
      (is (= s (.toString f)))))

  (testing "file and string"
    (with-project [foo "foo", f (file foo), s "bar"]
      (is (= (str *root* "/" foo "/" s)
             (.toString (file f s))))))

  (testing "tilde expansion"
    (with-project [p "/foo/bar", tp "~/foo/bar"]
      (is (= (str (System/getProperty "user.home") p)
             (.toString (file tp))))))

  (testing "no arguments"
    (with-project [p "/foo/bar"]
      (is (= (str *root*)
             (.toString (file)))))))
