(ns kutyagumi.misc.map-reader
  (:require [clojure.edn :as edn]
            [kutyagumi.misc.platform :as p]
            [kutyagumi.logic.board :as board]
            [kutyagumi.misc.util :as u]))

(def readers {'board/cell board/map->LivingCell
              'board/wall board/map->Wall})

(defn read-board
  "Read a board from an EDN string.

  Readers:
  #board/cell: map->LivingCell
  #board/wall: map->Wall"
  [text]
  (-> {:readers readers}
      (edn/read-string text)
      u/transpose))

(defn read-file
  "Read a board from a file. See read-board"
  [fname callback]
  (p/get-edn fname {:readers readers} #(-> % u/transpose callback)))
