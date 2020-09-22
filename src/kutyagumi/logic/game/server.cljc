(ns kutyagumi.logic.game.server
  (:require [kutyagumi.logic.player.core :as player]
            [clojure.core.async :as async]
            [kutyagumi.misc.util :as util]
            [kutyagumi.logic.game.core #?@(:cljs [:refer [GameLogic]])]
            [kutyagumi.logic.board :as board])
  #?(:clj (:import (kutyagumi.logic.game.core GameLogic))))

(defrecord ServerLogic
  [red green]
  GameLogic
  (update-game [this,
                {{:keys [board player]
                  :as   state}
                     :state,
                 :as game}]
    (async/go
      (let [[x y]
            (-> this
                player
                (player/next-move game)
                async/<!)
            cell (util/nd-nth-else board '__OUT_OF_BOUNDS
                                   x, y)]
        (if (and (not= '__OUT_OF_BOUNDS
                       cell)
                 (board/check-placement cell [x, y] board))
          (let [new-state
                (board/do-placement (util/nd-nth board
                                                 x, y)
                                    [x, y]
                                    state)

                chan
                (async/merge [(player/update-state red new-state)
                              (player/update-state green new-state)])]
            (assoc game
              :state new-state
              :logic (->ServerLogic
                       (async/<! chan)
                       (async/<! chan))))
          game)))))
