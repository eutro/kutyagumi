(ns kutyagumi.render.text
  (:require [kutyagumi.render.core :refer [#?(:cljs BoardRenderer) render]]
            [kutyagumi.logic.board #?@(:cljs [:refer [LivingCell Wall]])]
            [kutyagumi.logic.board]
            [kutyagumi.misc.util :as u])
  #?(:clj (:import (kutyagumi.render.core BoardRenderer)
                   (kutyagumi.logic.board LivingCell Wall))))

(defprotocol TextRenderable
  (render-as-text [this] "Render this as a 2x2 of characters."))

(extend-protocol TextRenderable
  nil (render-as-text [_] ["  "
                           "  "])
  LivingCell
  (render-as-text [this]
    ({:red   ["RR"
              "RR"]
      :green ["GG"
              "GG"]}
     (:owner this)))
  Wall
  (render-as-text [this]
    (let [f (fn [ud-side lr-side if-both]
              (let [has-ud (contains? (:sides this) ud-side)
                    has-lr (contains? (:sides this) lr-side)]
                (cond (and has-ud has-lr) if-both
                      has-ud \-
                      has-lr \|
                      :else \space)))]
      [(str (f :up :left \#)
            (f :up :right \#))
       (str (f :down :left \#)
            (f :down :right \#))])))

(deftype TextRenderer []
  BoardRenderer
  (render [_ board]
    (doseq [printable-row
            (mapcat #(apply map str
                            (map render-as-text %))
                    (u/transpose board))]
      (println printable-row))))
