(ns kutyagumi.main.nogui
  (:require [kutyagumi.logic.game.core :as game]
            [kutyagumi.logic.game.server :as server]
            [kutyagumi.render.text :as text]
            [kutyagumi.misc.map-reader :as mr]
            [kutyagumi.logic.player.reader-writer :as rw]
            [clojure.core.async :as async])
  (:gen-class))

(defn start [logic]
  (async/<!! (async/go-loop [logic logic]
               (-> logic game/get-state :board text/render)
               (-> logic game/update-game async/<! recur))))

(defn -main [& _this+args]
  (start
   (server/->ServerLogic
    (rw/make-player)
    (rw/make-player)
    {:board (-> "test.board.edn"
                mr/read-file
                async/<!!)
     :player :red})))
