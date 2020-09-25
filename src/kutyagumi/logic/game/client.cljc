(ns kutyagumi.logic.game.client
  (:require [clojure.core.async :as async]
            [kutyagumi.misc.network :as nw]
            [kutyagumi.logic.player.core :as player]
            [kutyagumi.logic.game.core #?@(:cljs [:refer [GameLogic]])])
  #?(:clj (:import (kutyagumi.logic.game.core GameLogic))))

(defrecord
  ^{:doc
    "Represents a game as seen by a client.

    Note, that the client player is always green."}
  ClientGame
  [player in out]
  GameLogic
  (update-game [_ game]
    (async/go
      (when (= :green player)
        (async/>! out (async/<! (player/next-move player game))))
      (assoc game :state (async/<! in)))))

(defn ->client-game [player id]
  (async/go
    (let [[in out] (async/<! (nw/make-connection :join id))]
      (->ClientGame player in out))))
