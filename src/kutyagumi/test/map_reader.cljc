(ns kutyagumi.test.map-reader
  (:require [clojure.edn :as edn]
            [kutyagumi.misc.platform :as p]
            [kutyagumi.logic.board :as board]))

(def readers {'board/cell board/map->LivingCell
              'board/wall board/map->Wall})

(defn read-board
  "Read a board from an EDN string.

  Readers:
  #board/cell: map->LivingCell
  #board/wall: map->Wall"
  [text]
  (edn/read-string {:readers readers} text))

(defn read-file
  [fname callback]
  (p/get-edn fname {:readers readers} callback))
