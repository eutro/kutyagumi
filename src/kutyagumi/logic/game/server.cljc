(ns kutyagumi.logic.game.server
  (:require [kutyagumi.logic.player.core :as player]
            [clojure.core.async :as async]
            [kutyagumi.misc.util :as util]
            [kutyagumi.logic.game.core #?@(:cljs [:refer [GameLogic]])]
            [kutyagumi.logic.board :as board])
  #?(:clj (:import (kutyagumi.logic.game.core GameLogic))))

(defrecord ServerLogic
  [red green state]
  GameLogic
  (update-game [{:keys [player]
                 {:keys [board]}
                       :state
                 :as   this}]
    (async/go
      (let [[x y]
            (-> this
                player
                player/next-move
                async/<!)
            cell (util/nd-nth-else board '__OUT_OF_BOUNDS
                                   x, y)]
        (if (and (not= '__OUT_OF_BOUNDS
                       cell)
                 (board/check-placement cell [x, y] board))
          (let [new-board
                (util/nd-update
                  board, x, y
                  board/do-placement
                  [x y] state)

                new-state
                (assoc state :board new-board)

                chan
                (async/merge [(player/update-state red new-state)
                              (player/update-state green new-state)])]
            (->ServerLogic (async/<! chan)
                           (async/<! chan)
                           new-state))
          this))))
  (get-state [_] state))
