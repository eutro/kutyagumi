(defmulti task first)

(defmethod task :default
  [[task-name]]
  (println "Unknown task:" task-name)
  (System/exit 1))

(require '[figwheel.main :as figwheel]
         '[clojure.main :as main]
         '[kutyagumi.main.gui :as gui]
         '[kutyagumi.main.nogui :as nogui])

(defmethod task nil
  [_]
  (figwheel/-main "--build" "run/dev"))

(defmethod task "native"
  [_]
  (gui/-main (next *command-line-args*)))

(defmethod task "nogui"
  [_]
  (nogui/-main))

(defmethod task "repl"
  [_]
  (main/repl :init #(doto 'kutyagumi.start-dev require in-ns)))

(task *command-line-args*)
