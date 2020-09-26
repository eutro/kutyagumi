(ns kutyagumi.logic.game.server
  (:require [kutyagumi.logic.player.core :as player]
            [clojure.core.async :as async]
            [kutyagumi.misc.util :as util]
            [kutyagumi.logic.game.core #?@(:cljs [:refer [GameLogic]])]
            [kutyagumi.logic.board :as board])
  #?(:clj (:import (kutyagumi.logic.game.core GameLogic))))

(declare ->ServerLogic)

(defn filter-split [f v]
  [(filter f v) (filter (complement f) v)])

(defn xor
  "nil if (and a b) otherwise (or a b)"
  [a b]
  (if-not (and a b)
    (or a b)))

(defn flood-fill
  "Flood fill cells from the cell at (x, y)."
  [board player [x, y]]
  (let [old-cell (util/nd-nth board x y)]
    (reduce (fn [board [side-to [dx, dy]]]
              (let [pos [(+ x dx)
                         (+ y dy)]
                    [nx, ny] pos
                    cell (util/nd-nth-else board '__OUT_OF_BOUNDS nx, ny)]
                (if (= cell '__OUT_OF_BOUNDS)
                  board
                  (let [state {:board  board
                               :player player}]
                    (if (and (board/check-placement cell pos state)
                             (board/can-place-from old-cell [x, y] side-to state))
                      (flood-fill (:board (board/do-placement cell pos state))
                                  player
                                  pos)
                      board)))))
            board
            board/side->vec)))

(defn flood-fill-player [board player]
  (reduce (fn [board pos]
            (flood-fill board player pos))
          board
          (for [x (range (-> board count))
                y (range (-> board first count))
                :when (= (:owner (util/nd-nth board x y)) player)]
            [x, y])))

(defn check-victory
  "Flood fill around each green cell, and for each red cell,
  then compare if there are any that have been filled by both fills,
  in order to determine whether there is a winner.

  Returns:
  [winner, board]"
  [board]
  (let [green-fill (flood-fill-player board :green)
        red-fill (flood-fill-player board :red)
        f-split
        (filter-split
          first
          (for [x (range (-> board count))
                y (range (-> board first count))
                :let [r-acc (some-> (util/nd-nth red-fill x, y)
                                    (as-> $ (if (= (:owner $) :red) $))
                                    :owner)
                      g-acc (some-> (util/nd-nth green-fill x, y)
                                    (as-> $ (if (= (:owner $) :green) $))
                                    :owner)]
                :when (or r-acc g-acc)]
            [(xor r-acc g-acc)
             [x y]]))
        [captured, contested] f-split
        new-board
        (reduce (fn [board [owner [x y]]]
                  (let [cell (util/nd-nth board
                                          x, y)]
                    (if (:owner cell)
                      board                                 ;; filter out existing cells
                      (:board (board/do-placement cell
                                                  [x, y]
                                                  {:board  board
                                                   :player owner})))))
                board
                captured)]
    [(when-not (seq contested)
       (let [red-count (count (filter #(= (:owner %) :red) (flatten red-fill)))
             green-count (count (filter #(= (:owner %) :green) (flatten green-fill)))]
         (if (> red-count green-count)
           :red
           :green)))
     new-board]))

(defrecord ServerLogic
  [red green]
  GameLogic
  (update-game [this,
                {{:keys [board player]
                  :as   state}
                     :state,
                 :as game}]
    (async/go-loop []
      (println (player this))
      (let [[x y]
            (-> this
                player
                (player/next-move game)
                async/<!)
            cell (util/nd-nth-else board '__OUT_OF_BOUNDS
                                   x, y)]
        (if (and (not= '__OUT_OF_BOUNDS
                       cell)
                 (board/check-placement cell [x, y] state))
          (let [{:keys [board]
                 :as   new-state}
                (board/do-placement (util/nd-nth board
                                                 x, y)
                                    [x, y]
                                    state)

                [winner new-board] (check-victory board)
                new-state (assoc new-state :board new-board, :winner winner)]
            (assoc game
              :state new-state
              :logic (->ServerLogic
                       (async/<! (player/update-state red new-state))
                       (async/<! (player/update-state green new-state)))))
          (recur))))))
