(ns kutyagumi.main.core
  (:require [kutyagumi.logic.game.core :as game]
            [kutyagumi.render.gui :as gui]
            [kutyagumi.logic.game.server :as server]
            [kutyagumi.misc.map-reader :as mr]
            [kutyagumi.logic.player.reader-writer :as rw]
            [clojure.core.async :as async]))

(defn init [game]
  (let [logic
        (server/->ServerLogic
         (rw/make-player)
         (rw/make-player)
         {:board (-> "test.board.edn" mr/read-file async/<!!)
          :player :red})]
    (assoc (gui/init game)
           :state {:logic logic
                   :chan (game/update-game logic)})))

(defn get-if-ready [chan]
  (first (async/alts!! [chan] :default nil)))

(defn main-loop
  [{{:keys [logic chan]
     :as   state}
    :state,
    :as game}]
  (gui/render game (game/get-state logic))
  (if-some [new-logic (get-if-ready chan)]
    (assoc game
           :state
           (assoc state
                  :logic new-logic
                  :chan (game/update-game new-logic)))
    game))
