(ns kutyagumi.misc.map-reader
  (:require [clojure.edn :as edn]
            [clojure.core.async :as async]
            [kutyagumi.misc.platform :as p]
            [kutyagumi.logic.board :as board]
            [kutyagumi.misc.util :as u]))

(def readers {'board/cell  board/map->LivingCell
              'board/wall  board/map->Wall
              'board/boost board/map->Boost
              'rand/nth    rand-nth})

(defn read-file
  "Read a board from a file."
  [fname]
  (async/go (-> (p/get-edn fname {:readers readers}) async/<!
                u/transpose)))

(defn pick-board []
  (async/go (-> "boards/boards.edn" p/get-edn async/<!
                rand-nth name (as-> $ (str "boards/" $ ".edn"))
                read-file async/<!)))
