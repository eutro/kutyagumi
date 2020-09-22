(ns kutyagumi.logic.player.core)

(defprotocol Player
  "Generic protocol for players.

  This may be a local player, bot, remote player etc."
  (next-move [this game]
    "A promise of the next move this player will make,
    in the form of a clojure.core.async channel returning
    data in this format:

    - [x y]
    - <end>")
  (update-state [this state]
    "Sync the game state to this player.
    Will be called each time the state changes.

    Expected to return a channel supplying the
    new player.

    - player
    - <end>"))
