(ns kutyagumi.main.core
  (:require [kutyagumi.logic.game.core :as game]
            [kutyagumi.render.gui :as gui]
            [kutyagumi.logic.game.server :as server]
            [kutyagumi.misc.map-reader :as mr]
            [kutyagumi.logic.player.gui :as gp]
            [clojure.core.async :as async]))

(defn init [game]
  (#?(:cljs async/go
      :clj  do)
    (let [{:keys [logic]
           :as   game}
          (#?(:cljs async/<!
              :clj do)
            (gui/init
              (merge
                (game/->Game
                  (game/->State
                    (-> "test.board.edn" mr/read-file #?(:cljs async/<!
                                                         :clj async/<!!))
                    :red)
                  (server/->ServerLogic
                    (gp/->GuiPlayer)
                    (gp/->GuiPlayer)))
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
