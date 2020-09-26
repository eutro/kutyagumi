(ns kutyagumi.logic.game.client
  (:require [clojure.core.async :as async]
            [kutyagumi.misc.network :as nw]
            [kutyagumi.logic.player.core :as player]
            [kutyagumi.logic.game.core #?@(:cljs [:refer [GameLogic]])]
            [kutyagumi.logic.board :as board]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [kutyagumi.logic.game.core :as game])
  #?(:clj (:import (kutyagumi.logic.game.core GameLogic))))

(def readers
  {'logic/board.LivingCell board/map->LivingCell
   'logic/board.Wall       board/map->Wall
   'logic/game.core.State  game/map->State})

(defn parse-state
  [payload-string]
  (edn/read-string {:readers readers}
                   (string/replace payload-string
                                   #"#kutyagumi\.logic\."
                                   "#logic/")))

(defrecord
  ^{:doc
    "Represents a game as seen by a client.

    Note, that the client player is always green."}
  ClientGame
  [player in out]
  GameLogic
  (update-game [_ {{next-player :player}
                       :state
                   :as game}]
    (async/go
      (when (= :green next-player)
        (async/>! out (async/<! (player/next-move player game))) game)
      (assoc game :state (parse-state (async/<! in))))))

(defn ->client-game [player id]
  (async/go
    (let [[in out] (async/<! (nw/make-connection :join id))]
      (->ClientGame player in out))))
