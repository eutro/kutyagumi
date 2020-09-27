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

(def opts
  {:readers
   {'logic/board.LivingCell board/map->LivingCell
    'logic/board.Wall       board/map->Wall
    'logic/board.Boost      board/map->Boost
    'logic/game.core.State  game/map->State}})

(defn read-packet
  [payload-string]
  (edn/read-string
    opts
    (string/replace payload-string
                    #"#kutyagumi\.logic\."
                    "#logic/")))

(defrecord
  ^{:doc
    "Represents a game as seen by a client."}
  ClientLogic
  [player in out]
  GameLogic
  (update-game [_ game]
    (async/go-loop []
      (let [{:keys [type]
             :as   packet}
            (read-packet (async/<! in))]
        (case type
          :move (do (async/>! out (async/<! (player/next-move player game)))
                    (recur))
          :sync (assoc game :state (:state packet)))))))

(defn ->client-logic [player id uri]
  (async/go
    (let [[in out] (async/<! (nw/make-connection uri :join id))]
      (assert (and in out) "Failed to make connection!")
      (->ClientLogic player in out))))
