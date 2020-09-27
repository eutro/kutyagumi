(ns kutyagumi.render.text
  (:require [kutyagumi.logic.board #?@(:cljs [:refer [LivingCell Wall Boost]])]
            [kutyagumi.logic.board]
            [kutyagumi.misc.util :as u])
  #?(:clj (:import (kutyagumi.logic.board LivingCell Wall Boost))))

(defprotocol TextRenderable
  (render-as-text [this]
    "Render this as a 2x2 of characters."))

(def CELL_SIZE 2)

(defn char-or [a b]
  (if (= \space a) b a))

(defn string-or [a b]
  (apply str (map char-or
                  a, b)))

(defn grid-or [a b]
  (vec (map string-or
            a, b)))

(extend-protocol TextRenderable
  nil (render-as-text [_] ["  "
                           "  "])
  LivingCell
  (render-as-text [{:keys [owner previous]}]
    (grid-or
      (render-as-text previous)
      ({:red   ["RR"
                "RR"]
        :green ["GG"
                "GG"]}
       owner)))
  Wall
  (render-as-text [{has-side? :sides}]
    [(str (if (has-side? :up) "-" " ")
          (if (has-side? :right) "|" " "))
     (str (if (has-side? :left) "|" " ")
          (if (has-side? :down) "-" " "))])

  Boost
  (render-as-text [{:keys [other]}]
    (grid-or
      (render-as-text other)
      [["bb"]
       ["bb"]])))

(defn pad-left
  ([obj n] (pad-left obj n \space))
  ([obj n char]
   (str (apply str (repeat (->> obj str count (- n)) char))
        obj)))

(defn render [board]
  (doseq [printable-row
          (let [blank (apply str (repeat CELL_SIZE \space))]
            (concat (apply map (partial str blank " ")
                           (map #((if (even? %) identity reverse)
                                  [blank (pad-left % CELL_SIZE)])
                                (-> board count range)))
                    (map #(str (if (even? %1)
                                 (pad-left (quot %1 2) CELL_SIZE)
                                 blank)
                               " " %2)
                         (range)
                         (mapcat #(apply map str (map render-as-text %))
                                 (u/transpose board)))))]
    (println printable-row)))
