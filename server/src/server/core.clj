(ns server.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import (java.net ServerSocket SocketException Socket)
           (clojure.lang LineNumberingPushbackReader)
           (java.io IOException Writer)))

(def PORT 4892)

(defmacro thread [& body]
  `(.start
     (Thread.
       ^Runnable
       (fn []
         ~@body))))

(defn send-packet [^Writer wr packet]
  (.write wr (pr-str packet))
  (.flush wr))

(def hosts (atom {}))

(defn open-session [^Socket connection]
  (thread
    (println "Client connected.")
    (try (with-open [rd (-> connection .getInputStream io/reader LineNumberingPushbackReader.)
                     wr (-> connection .getOutputStream io/writer)]
           (while (not (.isClosed connection))
             (try (let [{:keys [type]
                         :as   packet} (edn/read rd)]
                    (if (keyword? type)
                      (case type
                        :host (println "Hosting:" (pr-str packet))
                        :join (println "Joining:" (pr-str packet))
                        (send-packet wr
                          {:type    :error
                           :message "Malformed packet, unrecognized :type!"}))
                      (send-packet wr
                        {:type    :error
                         :message "Malformed packet, no :type given!"})))
                  (catch RuntimeException e
                    (send-packet wr
                      {:type    :error
                       :class   (.getName (.getClass e))
                       :message (.getMessage e)})))
             (recur))
           (println "Connection closed."))
         (catch IOException e
           (.printStackTrace e))
         (finally (.close connection)))))

(defn -main [& _args]
  (let [server (ServerSocket. PORT)]
    (.addShutdownHook
      (Runtime/getRuntime)
      (Thread. ^Runnable (fn [] (.close server))))
    (while (not (.isClosed server))
      (try (open-session (.accept server))
           (catch SocketException e
             (.printStackTrace e))))))
