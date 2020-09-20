(ns kutyagumi.core
  (:require [kutyagumi.misc.platform :as p]
            [play-cljc.gl.core :as c]
            [kutyagumi.logic.game.core :as game]
            [kutyagumi.render.gui :as gui]
            [kutyagumi.render.core :as render]
            [kutyagumi.misc.map-reader :as mr]
            [clojure.core.async :as async]
            #?(:clj  [play-cljc.macros-java :refer [gl math]]
               :cljs [play-cljc.macros-js :refer-macros [gl math]])))

(defonce *state (atom {}))

(defn init [game]
  (gl game enable
      (gl game BLEND))
  (gl game blendFunc
      (gl game SRC_ALPHA)
      (gl game ONE_MINUS_SRC_ALPHA))

  (gui/init game)
  (reset! render/*renderer (gui/->GuiRenderer game)))

(def test-board
  (atom nil))

(mr/read-file "test.board.edn" (partial reset! test-board))

(def logic (atom nil))

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
    (when-some [l @logic]
      (swap! render/*renderer assoc :game game)
      (render/render @render/*renderer (-> l game/get-state :board))))
  game)
