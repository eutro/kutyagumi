(ns kutyagumi.logic.game.core)

(defprotocol GameLogic
  "Generic protocol for the game logic.

  On a server, or a local game, this is responsible
  for dispatching movement requests.

  On a client, this would be responsible for receiving
  changes to the board."
  (update-game [this]
    "Update the game state. Await the next move, etc.

    Expected to return a channel as such:

    - new-logic
    - <end>")
  (get-state [this] "Get the current game state."))
