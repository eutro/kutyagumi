(defproject kutyagumi-server "0.1.0"
  :description "A simple server for Kutyagumi."
  :url "https://github.com/eutropius225/kutyagumi"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.java-websocket/Java-WebSocket "1.5.1"]]
  :repl-options {:init-ns server.core}
  :main server.core
  :aot [server.core])
