(ns kutyagumi.misc.platform
  (:require [clojure.edn :as edn]
            [clojure.core.async :as async]
            #?(:clj  [clojure.java.io :as io]
               :cljs [cljs.core.async.interop :refer-macros [<p!]]))
  #?(:clj (:import (java.nio ByteBuffer)
                   (org.lwjgl.glfw GLFW)
                   (org.lwjgl.system MemoryUtil)
                   (org.lwjgl.stb STBImage)
                   (java.io ByteArrayOutputStream)
                   (clojure.lang LineNumberingPushbackReader))))

(defn get-image [fname]
  #?(:clj  (async/go
             (let [^bytes barray (with-open [out (ByteArrayOutputStream.)]
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
               image))
     :cljs (let [image (js/Image.)
                 chan (async/promise-chan)]
             (doto image
               (-> .-src
                   (set! fname))
               (-> .-onload
                   (set! #(async/put!
                            chan
                            {:data   image
                             :width  (.-width image)
                             :height (.-height image)}))))
             chan)))

(defn get-edn
  ([fname] (get-edn fname {}))
  ([fname opts]
   #?(:clj  (async/go
              (with-open [rd (-> (str "public/" fname)
                                 io/resource io/input-stream
                                 io/reader LineNumberingPushbackReader.)]
                (edn/read opts rd)))
      :cljs (async/go
              (-> fname js/fetch <p!
                  .text <p!
                  (->> (edn/read-string opts)))))))

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
