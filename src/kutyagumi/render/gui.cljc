(ns kutyagumi.render.gui
  (:require [kutyagumi.logic.board #?@(:cljs [:refer [LivingCell Wall]])]
            [kutyagumi.misc.util :as u]
            [kutyagumi.misc.platform :as p]
            [clojure.core.async :as async]
            [play-cljc.gl.core :as c]
            [play-cljc.gl.entities-2d :as e]
            [play-cljc.transforms :as t]
            #?(:clj  [play-cljc.macros-java :refer [gl math]]
               :cljs [play-cljc.macros-js :refer-macros [gl math]]))
  #?(:clj (:import (kutyagumi.logic.board LivingCell Wall))))

(defn init [game]
  (async/go
    (gl game "enable"
        (gl game "BLEND"))
    (gl game "blendFunc"
        (gl game "SRC_ALPHA")
        (gl game "ONE_MINUS_SRC_ALPHA"))

    (assoc game
      ::assets
      (reduce
        #(%2 %1) {}
        (loop [assets (-> "assets/assets.edn" p/get-edn async/<!)
               ret nil]
          (if-not (seq assets)
            ret
            (recur (next assets)
                   (conj ret
                         (let [[k {:keys   [image]
                                   metaloc :meta}]
                               (first assets)]
                           (let [{:keys         [sprites]
                                  sprite-width  :width
                                  sprite-height :height
                                  :as           metadata}
                                 (-> (str "assets/" metaloc) p/get-edn async/<!)

                                 {:keys      [data]
                                  tex-width  :width
                                  tex-height :height}
                                 (-> (str "assets/" image) p/get-image async/<!)

                                 uncompiled (e/->image-entity game data tex-width tex-height)]
                             (fn [resources]
                               (assoc resources
                                 k
                                 {:meta metadata
                                  :sprites
                                        (into {}
                                              (let [entity (c/compile game uncompiled)]
                                                (for [x (range (quot tex-width sprite-width))
                                                      y (range (quot tex-height sprite-height))]
                                                  (when-some [sprite-key
                                                              (u/nd-nth-else sprites nil
                                                                             y, x)]
                                                    [sprite-key
                                                     (t/crop entity
                                                             (* x sprite-width), (* y sprite-height)
                                                             sprite-width, sprite-height)]))))})))))))))
      )))

(defprotocol GuiRenderable
  (draw [this board game
         pipeline
         x, y]
    "Render this element at (x, y)."))

(defn project-to [entity game]
  (t/project entity
             (max 1 (p/get-width game))
             (max 1 (p/get-height game))))

(extend-protocol GuiRenderable
  nil
  (draw [_ _board
         {{{{blank-sprite :blank}
            :sprites}
           :terrain}
              ::assets,
          :as game}
         pipeline
         _x, _y]
    (-> blank-sprite
        (project-to game)
        pipeline
        (->> (c/render game))))

  LivingCell
  (draw [{:keys [owner, previous]}
         board
         {:keys [total-time]
          {{:keys [sprites]
            {:keys [frames, fps]}
                  :meta}
           :cells}
                ::assets,
          :as   game}
         pipeline
         x, y]
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
            (->> (c/render game)))))
    (draw previous board game pipeline x y))

  Wall
  (draw [{:keys [sides]}
         board
         {{{:keys [sprites]}
           :terrain}
              ::assets,
          :as game}
         pipeline
         x, y]
    (draw nil board game pipeline x y)
    (doseq [side sides]
      (-> ((keyword "wall" (name side))
           sprites)
          (project-to game)
          pipeline
          (->> (c/render game))))))

(def color->background
  {:red   [(/ 224 255)
           (/ 161 255)
           (/ 161 255)
           1]
   :green [(/ 193 255)
           (/ 224 255)
           (/ 161 255)
           1]})

(def CELL_SIZE 40)

(defn seven-eighths [n]
  (/ (* n 7)
     8))

(defn render [{{:keys [board player]}
                   :state,
               :as game}]
  (c/render game
            {:viewport {:x      0
                        :y      0
                        :width  (p/get-width game)
                        :height (p/get-height game)}
             :clear    {:color (color->background player)
                        :depth 1}})

  (doseq [x (-> board count range)
          y (-> board first count range)]
    (draw (u/nd-nth board x y)
          board, game
          #(-> %
               (t/scale CELL_SIZE CELL_SIZE)
               (t/translate (seven-eighths x)
                            (seven-eighths y)))
          x, y)))
