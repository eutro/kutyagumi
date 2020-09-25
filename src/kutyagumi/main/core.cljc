(ns kutyagumi.main.core
  (:require [kutyagumi.logic.game.core :as game]
            [kutyagumi.render.gui :as gui]
            [kutyagumi.logic.game.server :as server]
            [kutyagumi.logic.game.client :as client]
            [kutyagumi.misc.map-reader :as mr]
            [kutyagumi.logic.player.gui :as gp]
            [kutyagumi.logic.player.rtc :as rp]
            [clojure.core.async :as async]))

(defn init [game args]
  (#?(:cljs async/go
      :clj  do)
    (let [[host-or-join id & _] args

          _ (if (and host-or-join (not id))
              (throw (ex-info "No game ID supplied!" {:args args})))

          {:keys [logic]
           :as   game}
          (#?(:cljs async/<!
              :clj  do)
            (gui/init
              (merge
                (->
                  (game/->Game
                    (game/->State
                      (async/<! (mr/pick-board))
                      :red)
                    (case host-or-join
                      nil
                      (server/->ServerLogic
                        (gp/->GuiPlayer)
                        (gp/->GuiPlayer))

                      "host"
                      (server/->ServerLogic
                        (gp/->GuiPlayer)
                        (async/<! (rp/->rtc-player id)))

                      "join"
                      (async/<! (client/->client-game
                                  (gp/->GuiPlayer)
                                  id))

                      (throw (ex-info "Invalid first argument! Should be \"host\" or \"join\"" {:args args}))))
                  #?(:cljs async/<!
                     :clj  async/<!!))
                game)))]
      (assoc game
        ::chan (game/update-game logic game)))))

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
