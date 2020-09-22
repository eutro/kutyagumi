(ns kutyagumi.main.nogui
  (:require [kutyagumi.logic.game.core :as game]
            [kutyagumi.logic.game.server :as server]
            [kutyagumi.render.text :as text]
            [kutyagumi.misc.map-reader :as mr]
            [kutyagumi.logic.player.reader-writer :as rw]
            [clojure.core.async :as async])
  (:gen-class))

(defn start [game]
  (async/<!! (async/go-loop [game game]
               (-> game :state :board text/render)
               (-> game :logic (game/update-game game) async/<! recur))))

(defn -main [& _args]
  (start
    (game/->Game
      (game/->State
        (-> "test.board.edn"
            mr/read-file
            async/<!!)
        :red)
      (server/->ServerLogic
        (rw/make-player)
        (rw/make-player)))))
