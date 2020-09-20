(ns kutyagumi.logic.player.gui
  (:require [kutyagumi.logic.player.core #?@(:cljs [:refer [Player]])]
            [clojure.core.async :as async])
  #?(:clj (:import (kutyagumi.logic.player.core Player))))

(def listening? (atom false))
(def click-channel (async/chan 1
                               (fn [add]
                                 (fn [& args]
                                   (when @listening?
                                     (apply add args)
                                     (reset! listening? false))))))

(defn next-click []
  (reset! listening? true)
  click-channel)

(deftype
  ^{:doc
    "A local player that clicks the GUI.

    Implies the GUI renderer."}
  GuiPlayer []
  Player
  (next-move [_]
    (async/go
      (reset! listening? true)))
  (update-state [this _state] (async/to-chan! [this])))
