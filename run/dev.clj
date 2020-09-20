(defmulti task first)

(defmethod task :default
  [[task-name]]
  (println "Unknown task:" task-name)
  (System/exit 1))

(require '[figwheel.main :as figwheel])

(defmethod task nil
  [_]
  (figwheel/-main "--build" "run/dev"))

(require '[kutyagumi.start-dev])

(defmethod task "native"
  [_]
  (kutyagumi.start-dev/start))

(defmethod task "repl"
  [_]
  (clojure.main/repl :init #(doto 'kutyagumi.start-dev require in-ns)))

(task *command-line-args*)
