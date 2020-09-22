(ns kutyagumi.main.gui
  (:require [kutyagumi.main.core :as core]
            [clojure.core.async :as async]
            [play-cljc.gl.core :as pc])
  (:import (org.lwjgl.glfw GLFW
                           Callbacks
                           GLFWWindowCloseCallbackI
                           GLFWMouseButtonCallbackI
                           GLFWCursorPosCallbackI)
           (org.lwjgl.opengl GL GL33))
  (:gen-class))

(defrecord Window [handle])

(defn ->window []
  (when-not (GLFW/glfwInit)
    (throw (Exception. "Unable to initialize GLFW")))
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
  (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_FORWARD_COMPAT GL33/GL_TRUE)
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_PROFILE GLFW/GLFW_OPENGL_CORE_PROFILE)
  (if-let [window (GLFW/glfwCreateWindow 1024 768 "Kutyagumi" 0 0)]
    (do (GLFW/glfwMakeContextCurrent window)
        (GLFW/glfwSwapInterval 1)
        (GL/createCapabilities)
        (->Window window))
    (throw (Exception. "Failed to create window"))))

(defn start [game {:keys [handle]}]
  (let [mouse-pos (atom [0, 0])
        click-chan (async/chan (async/dropping-buffer 1))
        game (assoc game
                    :delta-time 0
                    :total-time (GLFW/glfwGetTime)
                    :clicks click-chan)]
    (doto handle
      (GLFW/glfwShowWindow)
      (GLFW/glfwSetWindowCloseCallback
        (reify GLFWWindowCloseCallbackI
          (invoke [_ _]
            (System/exit 0))))
      (GLFW/glfwSetMouseButtonCallback
        (reify GLFWMouseButtonCallbackI
          (invoke [_ _ button action _mods]
            (when (and (= button GLFW/GLFW_MOUSE_BUTTON_LEFT)
                       (zero? action))
              (async/>!! click-chan @mouse-pos)))))
      (GLFW/glfwSetCursorPosCallback
        (reify GLFWCursorPosCallbackI
          (invoke [_ _ x y]
            (reset! mouse-pos [x y])))))
    (loop [{last-time :total-time
            :as game} (core/init game)]
      (when-not (GLFW/glfwWindowShouldClose handle)
        (let [ts (GLFW/glfwGetTime)
              game (assoc game
                     :delta-time (- ts last-time)
                     :total-time ts)
              game (core/main-loop game)]
          (GLFW/glfwSwapBuffers handle)
          (GLFW/glfwPollEvents)
          (recur game))))
    (Callbacks/glfwFreeCallbacks handle)
    (GLFW/glfwDestroyWindow handle)
    (GLFW/glfwTerminate)))

(defn -main [& _this+args]
  (let [{:keys [handle] :as window} (->window)]
    (start (pc/->game handle) window)))

