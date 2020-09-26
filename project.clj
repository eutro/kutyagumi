(defproject kutyagumi "0.1.0-SNAPSHOT"
  :repositories [["clojars" {:url "https://clojars.org/repo"
                             :sign-releases false}]]
  :clean-targets ^{:protect false} ["target"]
  :license {:name "MIT"}
  :main kutyagumi.main.gui
  :aot [kutyagumi.main.gui])
