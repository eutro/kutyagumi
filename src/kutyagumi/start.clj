(ns kutyagumi.start
  (:require [kutyagumi.core :as c]
            [play-cljc.gl.core :as pc])
  (:import (org.lwjgl.glfw GLFW Callbacks
                           GLFWCursorPosCallbackI
                           GLFWMouseButtonCallbackI
                           GLFWWindowCloseCallbackI)
           (org.lwjgl.opengl GL GL33)
           (org.lwjgl.system MemoryUtil)
           (java.nio IntBuffer))
  (:gen-class))

(defn mousecode->keyword [mousecode]
  (condp = mousecode
    GLFW/GLFW_MOUSE_BUTTON_LEFT :left
    GLFW/GLFW_MOUSE_BUTTON_RIGHT :right
    nil))

(defn on-mouse-move! [window xpos ypos]
  (let [*fb-width (MemoryUtil/memAllocInt 1)
        *fb-height (MemoryUtil/memAllocInt 1)
        *window-width (MemoryUtil/memAllocInt 1)
        *window-height (MemoryUtil/memAllocInt 1)
        _ (GLFW/glfwGetFramebufferSize ^long window ^IntBuffer *fb-width ^IntBuffer *fb-height)
        _ (GLFW/glfwGetWindowSize ^long window ^IntBuffer *window-width ^IntBuffer *window-height)
        fb-width (.get *fb-width)
        fb-height (.get *fb-height)
        window-width (.get *window-width)
        window-height (.get *window-height)
        width-ratio (/ fb-width window-width)
        height-ratio (/ fb-height window-height)
        x (* xpos width-ratio)
        y (* ypos height-ratio)]
    (MemoryUtil/memFree *fb-width)
    (MemoryUtil/memFree *fb-height)
    (MemoryUtil/memFree *window-width)
    (MemoryUtil/memFree *window-height)
    (swap! c/*state assoc :mouse-x x :mouse-y y)))

(defn on-mouse-click! [window button action mods]
  (swap! c/*state assoc
         :mouse-button
         (when (= action GLFW/GLFW_PRESS)
           (mousecode->keyword button))))

(defprotocol Events
  (on-mouse-move [this xpos ypos])
  (on-mouse-click [this button action mods])
  (on-tick [this game]))

(defrecord Window [handle])

(extend-type Window
  Events
  (on-mouse-move [{:keys [handle]} xpos ypos]
    (on-mouse-move! handle xpos ypos))
  (on-mouse-click [{:keys [handle]} button action mods]
    (on-mouse-click! handle button action mods))
  (on-tick [_ game]
    (c/tick game)))

(defn listen-for-events [{:keys [handle] :as window}]
  (doto handle
    (GLFW/glfwSetCursorPosCallback
      (reify GLFWCursorPosCallbackI
        (invoke [_ _ xpos ypos]
          (on-mouse-move window xpos ypos))))
    (GLFW/glfwSetMouseButtonCallback
      (reify GLFWMouseButtonCallbackI
        (invoke [_ _ button action mods]
          (on-mouse-click window button action mods))))
    (GLFW/glfwSetWindowCloseCallback
      (reify GLFWWindowCloseCallbackI
        (invoke [_ _]
          (System/exit 0))))))

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

(defn start [game window]
  (let [handle (:handle window)
        game (assoc game :delta-time 0, :total-time (GLFW/glfwGetTime))]
    (GLFW/glfwShowWindow handle)
    (c/init game)
    (listen-for-events window)
    (loop [game game]
      (when-not (GLFW/glfwWindowShouldClose handle)
        (let [ts (GLFW/glfwGetTime)
              game (assoc game
                     :delta-time (- ts (:total-time game))
                     :total-time ts)
              game (on-tick window game)]
          (GLFW/glfwSwapBuffers handle)
          (GLFW/glfwPollEvents)
          (recur game))))
    (Callbacks/glfwFreeCallbacks handle)
    (GLFW/glfwDestroyWindow handle)
    (GLFW/glfwTerminate)))

(defn -main [& _]
  (let [window (->window)]
    (start (pc/->game (:handle window)) window)))

