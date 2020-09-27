(ns kutyagumi.logic.board
  (:require [kutyagumi.misc.util :as u]))

(defprotocol BoardPart
  (check-placement [this [x y] state]
    "Check if the player can place their piece
    on the board at the given position, where this
    part currently is.")
  (can-place-from [this [x y] side state]
    "Check if the player can place adjacent
    to this part.")
  (do-placement [this [x y] state]
    "Do the placement of a player's piece on the
    board, returning the new state."))

(defrecord LivingCell [owner]
  BoardPart
  (check-placement [_ _ _] false)
  (can-place-from [{{:keys [sides]} :previous}
                   _pos
                   side
                   {:keys [player]}]
    (and (= owner player)
         (not (contains? sides side)))))

(def side->vec
  {:up    [0 -1]
   :down  [0 1]
   :left  [-1 0]
   :right [1 0]})

(def side->opposite
  {:up :down
   :down :up
   :left :right
   :right :left})

(def player->next
  {:red   :green
   :green :red})

(defn default-place
  [this x y {:keys [player] :as state}]
  (-> state
      (u/nd-update :board x y
                   (constantly (-> player
                                   ->LivingCell
                                   (assoc :previous this)))
                   nil)
      (update :player player->next)))

(defrecord Wall [sides]
  BoardPart
  (check-placement [_ [x, y] {:keys [board]
                              :as state}]
    (some (fn [[side [dx dy]]]
            (let [cx (+ x dx)
                  cy (+ y dy)]
              (can-place-from (u/nd-nth-else board nil cx cy)
                              [cx cy]
                              (side->opposite side)
                              state)))
          (seq (apply dissoc side->vec sides))))
  (do-placement [this [x, y] state]
    (default-place this x y state))
  (can-place-from [_this _pos _side _state] false))

(defrecord Boost [other]
  BoardPart
  (check-placement [_ pos state]
    (check-placement other pos state))
  (do-placement [_ pos {:keys [player]
                        :as state}]
    (assoc (do-placement other pos state)
      :player player))
  (can-place-from [_this _pos _side _state] false))

(extend-protocol BoardPart
  nil
  (check-placement [_ [x y] {:keys [board]
                             :as state}]
    (some (fn [[side [dx dy]]]
            (let [cx (+ x dx)
                  cy (+ y dy)]
              (can-place-from (u/nd-nth-else board nil cx cy)
                              [cx cy]
                              (side->opposite side)
                              state)))
          side->vec))
  (do-placement [this [x y] state]
    (default-place this x y state))
  (can-place-from [_this _pos _side _state] false))
