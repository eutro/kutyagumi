(ns kutyagumi.misc.map-reader
  (:require [clojure.edn :as edn]
            [clojure.core.async :as async]
            [kutyagumi.misc.platform :as p]
            [kutyagumi.logic.board :as board]
            [kutyagumi.misc.util :as u]))

(def readers {'board/cell   board/map->LivingCell
              'board/wall   board/map->Wall
              'board/random rand-nth
              'board/seeded (let [r (rand)] #(nth % (-> % count (* r) long)))})

(defn read-board
  "Read a board from an EDN string.

  Readers:
  #board/cell: map->LivingCell
  #board/wall: map->Wall
  #board/random: rand-nth"
  [text]
  (-> {:readers readers} (edn/read-string text) u/transpose))

(defn read-file
  "Read a board from a file. See read-board"
  [fname]
  (async/go (-> (p/get-edn fname {:readers readers}) async/<! u/transpose)))

(defn pick-board []
  (async/go (-> "boards/boards.edn" p/get-edn async/<!
                rand-nth name (as-> $ (str "boards/" $ ".edn"))
                read-file async/<!)))
