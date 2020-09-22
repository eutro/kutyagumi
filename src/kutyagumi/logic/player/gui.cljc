(ns kutyagumi.logic.player.gui
  (:require [kutyagumi.logic.player.core #?@(:cljs [:refer [Player]])]
            [clojure.core.async :as async]
            [kutyagumi.render.gui :as render])
  #?(:clj (:import (kutyagumi.logic.player.core Player))))

(deftype
  ^{:doc
    "A local player that clicks the GUI.

    Implies the GUI renderer."}
  GuiPlayer []
  Player
  (next-move [_ {:keys [clicks]}]
    (async/poll! clicks)
    (async/go
      (let [size (render/seven-eighths render/CELL_SIZE)
            [mx, my]
            (async/<! clicks)]
        [(quot (long mx) size)
         (quot (long my) size)])))
  (update-state [this _state] (async/to-chan! [this])))
