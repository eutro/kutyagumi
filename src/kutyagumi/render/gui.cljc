(ns kutyagumi.render.gui
  (:require [kutyagumi.logic.board #?@(:cljs [:refer [LivingCell Wall]])]
            [kutyagumi.misc.util :as u]
            [kutyagumi.misc.platform :as p]
            [clojure.core.async :as async]
            [play-cljc.gl.core :as c]
            [play-cljc.gl.entities-2d :as e]
            [play-cljc.transforms :as t]
            #?@(:clj  [[play-cljc.macros-java :refer [gl math]]]
                :cljs [[play-cljc.macros-js :refer-macros [gl math]]
                       [clojure.core.async :refer-macros [go]]]))
  #?(:clj (:import (kutyagumi.logic.board LivingCell Wall)
                   (org.lwjgl.glfw GLFW))))

(defn init
  "Yields a channel that returns a
  function to run on the main thread
  to retrieve the new game."
  [game]
  (async/go
    (let [functions
          ;; these are to reduce by successive application
          ;; to a map, but it has to run on the main thread
          ;; on the JVM.
          (loop [assets (-> "assets/assets.edn" p/get-edn async/<!)
                 ret nil]
            (if-not (seq assets)
              ret
              (recur
                (next assets)
                (conj ret
                      (let [k (first assets)]
                        (let [{:keys         [sprites shaders]
                               sprite-width  :width
                               sprite-height :height
                               :as           metadata}
                              (-> (str "assets/" (name k) ".edn") p/get-edn async/<!)

                              {:keys      [data]
                               tex-width  :width
                               tex-height :height}
                              (-> (str "assets/" (name k) ".png") p/get-image async/<!)

                              uncompiled (merge (e/->image-entity game data tex-width tex-height)
                                                shaders)]
                          (fn [resources]
                            (assoc resources
                              k
                              {:meta
                               metadata
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
                                                    sprite-width, sprite-height)]))))}))))))))]
      (fn []
        (gl game "enable"
            (gl game "BLEND"))
        (gl game "blendFunc"
            (gl game "SRC_ALPHA")
            (gl game "ONE_MINUS_SRC_ALPHA"))
        (gl game "enable"
            (gl game "DEPTH_TEST"))
        (assoc game
          ::assets (reduce #(%2 %1) {} functions))))))

(defprotocol GuiRenderable
  (draw [this board game
         pipeline
         x, y]
    "Render this element at (x, y)."))

(def color->background
  {:red   [(/ 224 255)
           (/ 161 255)
           (/ 161 255)
           1]
   :green [(/ 193 255)
           (/ 224 255)
           (/ 161 255)
           1]})

(defn seven-eighths [n]
  (/ (* n 7)
     8))

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
            pipeline
            (t/translate 0.125 0.125)
            (t/scale 0.75 0.75)
            (->> (c/render game)))))
    (draw previous board game pipeline x y))

  Wall
  (draw [{:keys [sides]}
         board
         {{{:keys [sprites]}
           :walls}
              ::assets,
          :as game}
         pipeline
         x, y]
    (doseq [side sides]
      (-> ((keyword "wall" (name side)) sprites)
          pipeline
          (->> (c/render game))))
    (draw nil board game pipeline x y)))

(defn padded [n]
  (* n 1.1))

(defn get-position-info
  "[cell-size [offset-x offset-y]]"
  [{{:keys [board]}
        :state,
    :as game}]
  (let [width (p/get-width game)
        height (p/get-height game)
        board-width (-> board count)
        board-height (-> board first count)
        shrunk (min (/ height (padded board-height))
                    (/ width (padded (+ board-width
                                        ;; for the score display
                                        4))))
        cell-size (long (* shrunk (/ 8 7)))]
    [cell-size
     [(/ (- width (* board-width shrunk)) 2)
      (/ (- height (* board-height shrunk)) 2)]]))

(def log2
  (let [ln2 (Math/log 2)]
    (fn [n]
      (/ (Math/log n)
         ln2))))

(defn clamp [mn n mx]
  (max mn (min n mx)))

(defn render-number
  "Actually only numbers but hey."
  [{{{c->entity    :sprites
      {sw :width
       sh :height} :meta}
     :digits} ::assets
    :as       game}
   game-width, game-height
   number
   x, y
   char-width]
  (let [char-height (* char-width (/ sh sw))]
    (doseq [[c i] (u/zip (str number) (range))]
      (when-some [entity (c->entity c)]
        (-> entity
            (t/project game-width game-height)
            (t/translate (+ x (* char-width i)) y)
            (t/scale char-width char-height)
            (->> (c/render game)))))))

(defn render [{{:keys [board player over?]}
                   :state,
               :as game}]
  (let [width (max 1 (p/get-width game))
        height (max 1 (p/get-height game))

        red-count (u/count-cells board :red)
        green-count (u/count-cells board :green)

        [cell-size [offset-x offset-y]]
        (get-position-info game)]
    (c/render game
              {:viewport {:x      0
                          :y      0
                          :width  width
                          :height height}
               :clear    {:color (color->background player)
                          :depth 1}})

    (doseq [x (-> board count range)
            y (-> board first count range)]
      (draw (u/nd-nth board x y)
            board, game
            #(-> %
                 (t/project width height)
                 (t/translate offset-x offset-y)
                 (t/scale cell-size cell-size)
                 (t/translate (seven-eighths x)
                              (seven-eighths y)))
            x, y))

    (gl game "disable"
        (gl game "DEPTH_TEST"))
    (let [{{:keys [sprites]} :guicells}
          (::assets game)
          get-sprite
          (fn [this other offset]
            (sprites
              (+ (let [n (Math/round (clamp -2 (log2 (/ this other)) 2))]
                   (if (zero? n) 1 n))
                 offset)))

          green-rep (get-sprite green-count red-count -3)
          red-rep (get-sprite red-count green-count 3)]
      (if over?
        (let [{{:keys [sprites]} :popup}
              (::assets game)
              popup-portion 2.5
              popup-width (/ width popup-portion)
              popup-height (/ popup-width 2)
              popup-y (/ (- height popup-height) 2)
              popup-x (/ (- width popup-width) 2)
              winner (if (> red-count green-count)
                       :red
                       ;; green came second, so wins in a tie
                       :green)

              rep-portion 8
              rep-size (/ width rep-portion)
              rep-y (/ (- height rep-size) 2)

              font-size (/ rep-size 8)
              padding (/ rep-size 16)]

          (-> (winner sprites)
              (t/project width height)
              (t/translate popup-x popup-y)
              (t/scale popup-width popup-height)
              (->> (c/render game)))

          (doseq [[entity rep-x total]
                  [[green-rep (- (/ width 2) (* rep-size 1.5)) green-count]
                   [red-rep (+ (/ width 2) (* rep-size 0.5)) red-count]]]
            (-> entity
                (t/project width height)
                (t/translate rep-x rep-y)
                (t/scale rep-size rep-size)
                (->> (c/render game)))
            (render-number game
                           width height
                           total
                           (+ rep-x
                              (/ (- rep-size
                                    (* font-size
                                       (count (str total))))
                                 2))
                           (+ rep-y rep-size padding)
                           font-size)))
        (let [rep-size (* 2 cell-size)
              font-size (/ rep-size 8)
              padding (/ rep-size 16)]
          (doseq [[rep, total
                   rep-x, rep-y]
                  [[green-rep green-count padding padding]
                   [red-rep red-count (- width rep-size padding) padding]]]
            (-> rep
                (t/project width height)
                (t/translate rep-x rep-y)
                (t/scale rep-size rep-size)
                (->> (c/render game)))
            (render-number game
                           width height
                           total
                           (+ rep-x
                              (/ (- rep-size
                                    (* font-size
                                       (count (str total))))
                                 2))
                           (+ rep-y rep-size padding)
                           font-size)))))
    (gl game "enable"
        (gl game "DEPTH_TEST"))))
