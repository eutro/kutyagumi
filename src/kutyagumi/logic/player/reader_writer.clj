(ns kutyagumi.logic.player.reader-writer
  (:require kutyagumi.logic.player.core
            [clojure.core.async :as async])
  (:import (kutyagumi.logic.player.core Player)
           (java.io Reader Writer Closeable)))

(defn parse-line [^String line]
  (when-some [[_all
               x, y]
              (->> line (re-matches #"(\d+)[ :/](\d+)"))]
    [(Integer/parseInt x)
     (Integer/parseInt y)]))

(defrecord
  ^{:doc
    "A player that takes input from a reader (e.g. stdin).

    This is not responsible for closing the streams itself."}
  ReaderWriterPlayer
  [^Reader in, ^Writer out]
  Player
  (next_move [_ _game]
    (async/thread
      (binding [*in* in]
        (loop [line (read-line)]
          (if-some [ret (parse-line line)]
            ret
            (do (binding [*out* out]
                  (println "Invalid input."))
                (recur (read-line))))))))
  (update_state [this _new-state]
    ;; local player, nothing to sync
    (async/to-chan! [this]))
  Closeable
  (close [_] (with-open [_in in, _out out])))

(defn make-player
  ([] (make-player *in*))
  ([in] (make-player in *out*))
  ([in out] (->ReaderWriterPlayer in out)))
