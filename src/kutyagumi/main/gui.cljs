(ns kutyagumi.main.gui
  (:require [kutyagumi.main.core :as core]
            [play-cljc.gl.core :as pc]
            [cljs.core.async :as async]
            [goog.events :as events]
            [clojure.string :as string]))

(defn msec->sec [n]
  (* 0.001 n))

(defn resize [context]
  (let [canvas (.-canvas context)]
    (set! (.-width canvas) (.-clientWidth canvas))
    (set! (.-height canvas) (.-clientHeight canvas))))

(defn game-loop [game]
  (let [game (core/main-loop game)]
    (js/requestAnimationFrame
      (fn [ts]
        (let [ts (msec->sec ts)]
          (game-loop (assoc game
                       :delta-time (- ts (:total-time game))
                       :total-time ts)))))))

(defn -main [args]
  (let [canvas (js/document.querySelector "canvas")
        context (.getContext canvas "webgl2")
        click-chan (async/chan (async/dropping-buffer 1))
        game (assoc (pc/->game context)
               :delta-time 0
               :total-time (msec->sec (js/performance.now))
               :clicks click-chan)]
    (resize context)
    (events/listen
      js/window "resize"
      (fn [_] (resize context)))
    (events/listen
      js/window "click"
      (fn [event] (async/put! click-chan [(.-clientX event)
                                          (.-clientY event)])))
    (async/take! (core/init game args) game-loop)))

(-main (string/split (-> js/window .-location .-search (.substring 1))
                     #"[&=]"))
