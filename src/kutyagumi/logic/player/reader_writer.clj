(ns kutyagumi.logic.player.reader-writer
  (:require kutyagumi.logic.player.core
            [clojure.core.async :as async])
  (:import (kutyagumi.logic.player.core Player)
           (java.io Reader Writer Closeable)
           (java.util.regex Pattern Matcher)))

(defn parse-line [^String line]
  (let [matcher (->> line (.matcher #"(\d+)[ :/](\d+)"))]
    (when (.matches matcher)
      [(Integer/parseInt (.group matcher 1))
       (Integer/parseInt (.group matcher 2))])))

(defrecord
  ^{:doc
    "A player that takes input from a reader (e.g. stdin).

    This is not responsible for closing the streams itself."}
  ReaderWriterPlayer
  [^Reader in, ^Writer out,
   state]
  Player
  (next_move [_]
    (async/thread
      (binding [*in* in]
        (loop [line (read-line)]
          (if-some [ret (parse-line line)]
            ret
            (do (binding [*out* out]
                  (println "Invalid input."))
                (recur (read-line))))))))
  (update_state [this new-state]
    (async/to-chan! [(assoc this :state new-state)]))
  Closeable
  (close [_] (with-open [_in in, _out out])))

(defn make-player
  ([] (make-player *in*))
  ([in] (make-player in *out*))
  ([in out] (->ReaderWriterPlayer in out nil)))
