(ns kutyagumi.main.core
  (:require [kutyagumi.logic.game.core :as game]
            [kutyagumi.render.gui :as gui]
            [kutyagumi.logic.game.server :as server]
            [kutyagumi.misc.map-reader :as mr]
            [kutyagumi.logic.player.reader-writer :as rw]
            [clojure.core.async :as async]))

(defn init [game]
  (gui/init game)
  (let [board-chan (async/chan 1)]
    (mr/read-file
      "test.board.edn"
      (partial async/>!! board-chan))
    (let [logic
          (server/->ServerLogic
            (rw/make-player)
            (rw/make-player)
            {:board (async/<!! board-chan)
             :player :red})]
      {:logic logic
       :chan (game/update-game logic)})))

(defn get-if-ready [chan]
  (first (async/alts!! [chan] :default nil)))

(defn main-loop
  [game {:keys [logic chan]
         :as   state}]
  (gui/render game (game/get-state logic))
  (if-some [new-logic (get-if-ready chan)]
    {:logic new-logic
     :chan (game/update-game new-logic)}
    state))
