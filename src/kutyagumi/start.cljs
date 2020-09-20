(ns kutyagumi.start
  (:require [kutyagumi.core :as c]
            [play-cljc.gl.core :as pc]
            [goog.events :as events]))

(defn msec->sec [n]
  (* 0.001 n))

(defn game-loop [game]
  (let [game (c/tick game)]
    (js/requestAnimationFrame
      (fn [ts]
        (let [ts (msec->sec ts)]
          (game-loop (assoc game
                       :delta-time (- ts (:total-time game))
                       :total-time ts)))))))

(defn listen-for-mouse [canvas]
  (events/listen
    js/window "mousemove"
    (fn [event]
      (swap! c/*state
             (fn [state]
               (let [bounds (.getBoundingClientRect canvas)
                     x (- (.-clientX event) (.-left bounds))
                     y (- (.-clientY event) (.-top bounds))]
                 (assoc state :mouse-x x :mouse-y y)))))))

(defn resize [context]
  (let [display-width context.canvas.clientWidth
        display-height context.canvas.clientHeight]
    (set! context.canvas.width display-width)
    (set! context.canvas.height display-height)))

(defn listen-for-resize [context]
  (events/listen
    js/window "resize"
    (fn [event]
      (resize context))))

(defn listen-for-click [canvas]
  (events/listen
    js/window "click"
    (fn [event]
      (swap! c/*state assoc :mouse-button :left))))

;; start the game

(defonce context
  (let [canvas (js/document.querySelector "canvas")
        context (.getContext canvas "webgl2")
        initial-game (assoc (pc/->game context)
                       :delta-time 0
                       :total-time (msec->sec (js/performance.now)))]
    (c/init initial-game)
    (listen-for-mouse canvas)
    (listen-for-click canvas)
    (resize context)
    (listen-for-resize context)
    (game-loop initial-game)
    context))

