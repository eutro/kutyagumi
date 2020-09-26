(defproject kutyagumi-server "0.1.0"
  :min-lein-version "2.0.0"
  :description "A simple server for Kutyagumi."
  :url "https://github.com/eutropius225/kutyagumi"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.immutant/web "2.0.0-beta2"]
                 [ring/ring-core "1.3.0"]]
  :uberjar-name "kutyagumi-server-standalone.jar"
  :repl-options {:init-ns server.core}
  :main server.core
  :aot [server.core])
