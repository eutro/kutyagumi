(ns kutyagumi.main.core
  (:require [kutyagumi.logic.game.core :as game]
            [kutyagumi.render.gui :as gui]
            [kutyagumi.logic.game.server :as server]
            [kutyagumi.logic.game.client :as client]
            [kutyagumi.misc.map-reader :as mr]
            [kutyagumi.logic.player.gui :as gp]
            [kutyagumi.logic.player.remote :as rp]
            [kutyagumi.misc.platform :as platform]
            [clojure.core.async :as async]))

(defn init
  "Yields a channel that returns a
  function to run on the main thread
  to retrieve the new game."
  [old-game {:as opts}]
  (async/go
    (let [state (game/->State
                  (async/<! (mr/pick-board))
                  :red)
          new-game
          (game/->Game
            state
            (cond
              (get opts "host")
              (server/->ServerLogic
                (gp/->GuiPlayer)
                (async/<! (rp/->remote-player
                            (get opts "host")
                            state
                            (or (get opts "server")
                                (async/<! (platform/get-edn "config/server.edn"))))))

              (get opts "join")
              (async/<! (client/->client-logic
                          (gp/->GuiPlayer)
                          (get opts "join")
                          (or (get opts "server")
                              (async/<! (platform/get-edn "config/server.edn")))))

              :else
              (server/->ServerLogic
                (gp/->GuiPlayer)
                (gp/->GuiPlayer))))
          gui-thunk (async/<! (gui/init (merge new-game old-game)))]
      (fn []
        (let [{:keys [logic]
               :as   game}
              (gui-thunk)]
          (assoc game
            ::chan (game/update-game logic game)))))))

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
