(ns kutyagumi.misc.platform
  (:require [clojure.edn :as edn]
            #?@(:clj  [[clojure.java.io :as io]]
                :cljs [[clojure.core.async :as a]]))
  #?(:clj (:import (java.nio ByteBuffer)
                   (org.lwjgl.glfw GLFW)
                   (org.lwjgl.system MemoryUtil)
                   (org.lwjgl.stb STBImage)
                   (java.io ByteArrayOutputStream)
                   (clojure.lang LineNumberingPushbackReader))))

(defn get-image [fname callback]
  #?(:clj  (let [^bytes barray (with-open [out (ByteArrayOutputStream.)]
                                 (with-open [is (-> (str "public/" fname)
                                                    io/resource
                                                    io/input-stream)]
                                   (io/copy is out))
                                 (.toByteArray out))
                 *width (MemoryUtil/memAllocInt 1)
                 *height (MemoryUtil/memAllocInt 1)
                 *components (MemoryUtil/memAllocInt 1)
                 direct-buffer (doto ^ByteBuffer
                                     (ByteBuffer/allocateDirect (alength barray))
                                 (.put barray)
                                 (.flip))
                 decoded-image (STBImage/stbi_load_from_memory
                                 direct-buffer *width *height *components
                                 STBImage/STBI_rgb_alpha)
                 image {:data   decoded-image
                        :width  (.get *width)
                        :height (.get *height)}]
             (MemoryUtil/memFree *width)
             (MemoryUtil/memFree *height)
             (MemoryUtil/memFree *components)
             (callback image))
     :cljs (let [image (js/Image.)]
             (doto image
               (-> .-src
                   (set! fname))
               (-> .-onload
                   (set! #(callback {:data   image
                                     :width  (.-width image)
                                     :height (.-height image)})))))))

(defn get-edn
  ([fname callback] (get-edn fname {} callback))
  ([fname opts callback]
   #?(:clj  (callback
              (with-open [rd (-> (str "public/" fname) io/resource io/input-stream
                                 io/reader LineNumberingPushbackReader.)]
                (edn/read opts rd)))
      :cljs (-> fname js/fetch
                (.then (fn [r]
                         (-> r .text
                             (.then (fn [r]
                                      (->> r (edn/read-string opts) callback))))))))))

(defn get-width [game]
  #?(:clj  (let [*width (MemoryUtil/memAllocInt 1)
                 _ (GLFW/glfwGetFramebufferSize ^long
                                                (:context game)
                                                *width
                                                nil)
                 n (.get *width)]
             (MemoryUtil/memFree *width)
             n)
     :cljs (-> game :context .-canvas .-clientWidth)))

(defn get-height [game]
  #?(:clj  (let [*height (MemoryUtil/memAllocInt 1)
                 _ (GLFW/glfwGetFramebufferSize ^long
                                                (:context game)
                                                nil
                                                *height)
                 n (.get *height)]
             (MemoryUtil/memFree *height)
             n)
     :cljs (-> game :context .-canvas .-clientHeight)))
