(ns kutyagumi.logic.game.core)

(defprotocol GameLogic
  "Generic protocol for the game logic.

  On a server, or a local game, this is responsible
  for dispatching movement requests.

  On a client, this would be responsible for receiving
  changes to the board."
  (update-game [this game]
    "Update the game state, awaiting the next move, etc.

    Expected to return a channel as such:

    - new-game
    - <end>"))

(defrecord
  ^{:doc
    "The game instance, holding the game state and
    current game logic, and some context-specific
    things (IO channels, window handle, etc.)"}
  Game [state logic])

(defrecord
  ^{:doc
    "The game state, holding the board and the player
    whose turn it is."}
  State [board player])
