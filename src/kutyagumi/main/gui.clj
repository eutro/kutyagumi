(ns kutyagumi.main.gui
  (:require [kutyagumi.main.core :as core]
            [play-cljc.gl.core :as pc])
  (:import (org.lwjgl.glfw GLFW Callbacks
                           GLFWWindowCloseCallbackI)
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
  (let [game (assoc game :delta-time 0, :total-time (GLFW/glfwGetTime))]
    (GLFW/glfwShowWindow handle)
    (GLFW/glfwSetWindowCloseCallback
      handle
      (reify GLFWWindowCloseCallbackI
        (invoke [_ _]
          (System/exit 0))))
    (loop [{last-time :total-time
            :as game} game
           state (core/init game)]
      (when-not (GLFW/glfwWindowShouldClose handle)
        (let [ts (GLFW/glfwGetTime)
              game (assoc game
                     :delta-time (- ts last-time)
                     :total-time ts)
              state (core/main-loop game state)]
          (GLFW/glfwSwapBuffers handle)
          (GLFW/glfwPollEvents)
          (recur game state))))
    (Callbacks/glfwFreeCallbacks handle)
    (GLFW/glfwDestroyWindow handle)
    (GLFW/glfwTerminate)))

(defn -main [& _this+args]
  (let [{:keys [handle] :as window} (->window)]
    (start (pc/->game handle) window)))

