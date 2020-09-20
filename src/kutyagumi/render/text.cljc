(ns kutyagumi.render.text
  (:require [kutyagumi.render.core :refer [#?(:cljs BoardRenderer) render]]
            [kutyagumi.logic.board #?@(:cljs [:refer [LivingCell Wall]])]
            [kutyagumi.logic.board])
  #?(:clj (:import (kutyagumi.render.core BoardRenderer)
                   (kutyagumi.logic.board LivingCell Wall))))

(defprotocol TextRenderable
  (render-as-char [this] "Render this as a 2x2 of characters."))

(extend-protocol TextRenderable
  nil (render-as-char [_] ["  "
                           "  "])
  LivingCell
  (render-as-char [this]
    ({:red   ["RR"
              "RR"]
      :green ["GG"
              "GG"]}
     (:owner this)))
  Wall
  (render-as-char [this]
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
  (render [_ board _game]
    (doseq [printable-row
            (flatten
              (for [row board]
                (apply map str (map render-as-char row))))]
      (println printable-row))))
