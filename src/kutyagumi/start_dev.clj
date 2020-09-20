(ns kutyagumi.start-dev
  (:require [kutyagumi.start :as start]
            [kutyagumi.core :as c]
            [orchestra.spec.test :as st]
            [expound.alpha :as expound]
            [clojure.spec.alpha :as s]
            [play-cljc.gl.core :as pc]
            [paravim.start]
            [paravim.core]))

(defn start []
  (st/instrument)
  (set! s/*explain-out* expound/printer)
  (let [window (start/->window)
        game (pc/->game (:handle window))]
    (start/start game window)))

(defn -main []
  (start))

