(ns kutyagumi.main.core
  (:require [kutyagumi.logic.game.core :as game]
            [kutyagumi.render.gui :as gui]
            [kutyagumi.logic.game.server :as server]
            [kutyagumi.misc.map-reader :as mr]
            [kutyagumi.logic.player.reader-writer :as rw]
            [clojure.core.async :as async]))

(defn init [game]
  (let [{:keys [logic]
         :as   game}
        (gui/init
          (merge
            (game/->Game
              (game/->State
                (-> "test.board.edn" mr/read-file async/<!!)
                :red)
              (server/->ServerLogic
                (rw/make-player)
                (rw/make-player)))
            game))]
    (assoc game
      ::chan (game/update-game logic game))))

(defn main-loop
  [{::keys [chan]
    :as    game}]
  (gui/render game)
  (if-some [{:keys [logic]
             :as   new-game}
            (async/poll! chan)]
    (assoc new-game
      ::chan (game/update-game logic new-game))
    game))
