(ns kutyagumi.render.gui
  (:require [kutyagumi.render.core :refer [#?(:cljs BoardRenderer) render]]
            [kutyagumi.logic.board #?@(:cljs [:refer [LivingCell Wall]])]
            [kutyagumi.misc.util :as u]
            [kutyagumi.misc.platform :as p]
            [play-cljc.gl.core :as c]
            [play-cljc.gl.entities-2d :as e]
            [play-cljc.transforms :as t]
            #?(:clj  [play-cljc.macros-java :refer [gl math]]
               :cljs [play-cljc.macros-js :refer-macros [gl math]]))
  #?(:clj (:import (kutyagumi.render.core BoardRenderer)
                   (kutyagumi.logic.board LivingCell Wall))))

(def *assets (atom {}))

(defn init [game]
  (p/get-edn "assets/assets.edn"
    (fn [assets]
      (doseq [[k {:keys    [image]
                  metadata :meta}]
              assets]
        (p/get-edn (str "assets/" metadata)
          (fn [{:keys         [sprites]
                sprite-width  :width
                sprite-height :height
                :as           metadata}]
            (p/get-image (str "assets/" image)
              (fn [{:keys      [data]
                    tex-width  :width
                    tex-height :height}]
                (let [entity (->> (e/->image-entity game data tex-width tex-height)
                                  (c/compile game))]
                  (swap! *assets
                         assoc k
                         {:meta metadata
                          :sprites
                                (into {}
                                      (for [x (range (quot tex-width sprite-width))
                                            y (range (quot tex-height sprite-height))]
                                        (when-some [sprite-key (u/nd-nth-else sprites
                                                                              nil
                                                                              y, x)]
                                          [sprite-key
                                           (t/crop entity
                                                   (* x sprite-width), (* y sprite-height)
                                                   sprite-width, sprite-height)])))}))))))))))

(defprotocol GuiRenderable
  (draw [this board game
         pipeline
         x, y] "Render this element at (x, y)."))

(defn project-to [entity game]
  (t/project entity
             (max 1 (p/get-width game))
             (max 1 (p/get-height game))))

(extend-protocol GuiRenderable
  nil
  (draw [_ _board game
         pipeline
         _x, _y]
    (let [{{{blank-sprite :blank}
            :sprites}
           :terrain}
          @*assets]
      (when blank-sprite
        (-> blank-sprite
            (project-to game)
            pipeline
            (->> (c/render game))))))

  LivingCell
  (draw [{:keys [owner, previous]} board {:keys [total-time]
                                          :as   game}
         pipeline
         x, y]
    (let [{{:keys                 [sprites]
            {:keys [frames, fps]} :meta}
           :cells}
          @*assets]
      (when (and sprites frames fps)
        (let [frame-count (count frames)

              offset (* (+ x y)
                        (quot frame-count 8))

              sprite-sym (nth frames
                              (mod (+ (* total-time fps)
                                      offset)
                                   frame-count))

              sprite ((keyword (name owner)
                               (name sprite-sym))
                      sprites)]
          (when sprite
            (-> sprite
                (project-to game)
                pipeline
                (->> (c/render game)))))))
    (draw previous board game pipeline x y))

  Wall
  (draw [{:keys [sides]} board game
         pipeline
         x, y]
    (draw nil board game pipeline x y)
    (let [{{:keys [sprites]}
           :terrain}
          @*assets]
      (doseq [side sides]
        (when-some [sprite ((keyword "wall"
                                     (name side))
                            sprites)]
          (-> sprite
              (project-to game)
              pipeline
              (->> (c/render game))))))))

(defrecord GuiRenderer [game]
  BoardRenderer
  (render [_ board]
    (doseq [x (-> board count range)
            y (-> board first count range)]
      (draw (u/nd-nth board x y)
            board, game
            #(-> %
                 (t/scale 40 40)
                 (t/translate (* 7 x (/ 8)) (* 7 y (/ 8))))
            x, y))))
