(ns cake.tasks.global
  (:use cake.core))

(require-tasks [cake.tasks help deps compile swank file version new eval bake])

(deftask default #{help})