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
  (next-move [_ {:keys [clicks]
                 :as   game}]
    (async/poll! clicks)
    (async/go
      (let [[mx, my] (async/<! clicks)
            [cell-size [offset-x offset-y]] (render/get-position-info game)
            size (render/seven-eighths cell-size)]
        [(quot (long (- mx offset-x)) size)
         (quot (long (- my offset-y)) size)])))
  (update-state [this _state] (async/to-chan! [this])))
