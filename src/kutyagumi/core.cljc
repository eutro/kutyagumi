(ns kutyagumi.core
  (:require [kutyagumi.misc.platform :as p]
            [play-cljc.gl.core :as c]
            [kutyagumi.render.gui :as gui]
            [kutyagumi.render.core :as render]
            [kutyagumi.test.map-reader :as mr]
            #?(:clj  [play-cljc.macros-java :refer [gl math]]
               :cljs [play-cljc.macros-js :refer-macros [gl math]])))

(def grid-width 9)
(def grid-height 16)

(defonce board (vec (for [_ (range grid-width)]
                      (vec (for [_ (range grid-height)]
                             (atom nil))))))

(defonce *state (atom {}))

(defn init [game]
  (gl game enable
      (gl game BLEND))
  (gl game blendFunc
      (gl game SRC_ALPHA)
      (gl game ONE_MINUS_SRC_ALPHA))

  (gui/init game)
  (reset! render/*renderer (gui/->GuiRenderer)))

(def test-board
  (atom nil))

(mr/read-file "test.board.edn" (partial reset! test-board))

(defn tick [game]
  (let [game-width (p/get-width game)
        game-height (p/get-height game)]
    (c/render game
              {:viewport {:x      0
                          :y      0
                          :width  game-width
                          :height game-height}
               :clear    {:color [(/ 173 255)
                                  (/ 216 255)
                                  (/ 230 255)
                                  1]
                          :depth 1}})
    (render/render @render/*renderer @test-board game))
  game)
