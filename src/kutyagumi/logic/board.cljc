(ns kutyagumi.logic.board
  (:require [kutyagumi.misc.util :as u]))

(defprotocol BoardPart
  (check-placement [this [x y] state]
    "Check if the player can place their piece
    on the board at the given position, where this
    part currently is.")
  (do-placement [this [x y] state]
    "Do the placement of a player's piece on the
    board, returning the new state."))

(defrecord LivingCell [owner]
  BoardPart
  (check-placement [_ _ _] false))

(def side->vec
  {:up    [0 -1]
   :down  [0 1]
   :left  [-1 0]
   :right [1 0]})

(defn owned-by?
  "Check if the cell at (x, y) on the board is owned by
  the given player."
  [board x y player]
  (= (:owner (u/nd-nth board x y))
     player))

(defrecord Wall [sides]
  BoardPart
  (check-placement [_ [x y] {:keys [player board]}]
    (some (fn [dx dy]
            (owned-by? board
                       (+ x dx)
                       (+ y dy)
                       player))
          (-> (apply dissoc side->vec sides)
              seq
              second))))

(def player->next
  {:red   :green
   :green :red})

(defn default-place
  [this x y {:keys [player] :as state}]
  (-> state
      (u/nd-update state
                 :board x y
                 (assoc (->LivingCell player)
                   :previous this))
      (update
        :player
        (player->next player))))

(extend-protocol BoardPart
  nil
  (check-placement [_ [x y] {:keys [player board]}]
    (some (fn [dx dy]
            (owned-by? board
                       (+ x dx)
                       (+ y dy)
                       player))
          [[0 1]
           [0 -1]
           [1 0]
           [-1 0]]))
  (do-placement [this [x y] state]
    (default-place this x y state))

  #?(:clj  Object
     :cljs js/Object)
  (do-placement [this [x y] state]
    (default-place this x y state)))
