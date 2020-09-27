(ns server.core
  (:gen-class)
  (:require [clojure.edn :as edn])
  (:import (org.java_websocket.server WebSocketServer)
           (java.net InetSocketAddress)
           (org.java_websocket WebSocket WebSocketImpl)))

;; to match the game's protocol version
(def VERSION "1.1.0")
(def LOG_PACKETS false)

(defn send-to [^WebSocket socket packet]
  (when LOG_PACKETS (print "> ") (prn packet))
  (.send socket (pr-str packet)))

;; I sure do hope I don't get memory leaks
(def id->host* (atom {}))
(def host->id* (atom {}))
(def pairs (atom {}))

(defmulti handle-packet
  (fn [_connection {:keys [type]}] type))

(defmethod handle-packet :default
  [connection {:keys [type]}]
  (send-to connection
    {:type    :error
     :message (str "Unknown type: " type)}))

(defmethod handle-packet :host
  [connection {:keys [id]}]
  (cond (not id)
        (send-to connection
          {:type    :error
           :message "No host ID given."})

        (@id->host* id)
        (send-to connection
          {:type    :user-error
           :message (str "Someone is already hosting the game: " \" id \")})

        (@host->id* connection)
        (send-to connection
          {:type    :error
           :message (str "You are already hosting a game!")})

        (@pairs connection)
        (send-to connection
          {:type    :error
           :message "You are already starting a game!"})

        :else
        (do (swap! id->host* assoc id connection)
            (swap! host->id* assoc connection id)
            (send-to connection
              {:type    :success
               :message (str "Hosting game: " \" id \")}))))

(defmethod handle-packet :join
  [connection {:keys [id]}]
  (if id
    (if-let [host-connection (@id->host* id)]
      (locking connection
        (cond (= host-connection connection)
              (send-to connection
                {:type    :user-error
                 :message "Cannot join your own game!"})

              (@pairs connection)
              (send-to connection
                {:type    :error
                 :message "You are already starting a game!"})

              (@pairs host-connection)
              (send-to connection
                {:type    :error
                 :message "The host is already starting a game!"})

              :else
              (do (swap! id->host* dissoc id)
                  (swap! host->id* dissoc host-connection)
                  (swap! pairs assoc
                         connection, host-connection
                         host-connection, connection)
                  (send-to connection
                    {:type    :success
                     :message (str "Joining game: " \" id \")})

                  (doseq [c [connection host-connection]]
                    (send-to c {:type :pairing})))))
      (send-to connection
        {:type    :user-error
         :message (str "Nobody is hosting the game: " \" id \")}))
    (send-to connection
      {:type    :error
       :message "No host ID given."})))

(defmethod handle-packet :to-peer
  [connection {:keys [payload]}]
  (if-let [pair (@pairs connection)]
    (send-to pair
      {:type    :from-peer
       :payload payload})
    (send-to connection
      {:type    :error
       :message "Not paired with anyone."})))

(defmethod handle-packet :pulse
  [_connection _packet]
  ;; NOOP
  )

(defn on-message [connection message]
  (when-some [packet
              (try (edn/read-string message)
                   (catch RuntimeException e
                     (send-to connection
                       {:type    :error
                        :message (str "Malformed EDN: " (.getMessage e))})))]
    (when LOG_PACKETS (print "< ") (prn packet))
    (handle-packet connection packet)))

(defn on-close [connection]
  (when-let [id (@host->id* connection)]
    (swap! id->host* dissoc id)
    (swap! host->id* dissoc connection))

  (when-let [pair (@pairs connection)]
    (swap! pairs dissoc connection pair)
    (send-to pair
      {:type :disconnect})))

(defn -main [& [port]]
  (let [port (try (Integer/parseInt port)
                  (catch NumberFormatException _ WebSocketImpl/DEFAULT_PORT))
        server
        (proxy [WebSocketServer] [(InetSocketAddress. port)]
          (onClose [connection _code _reason _remote]
            (on-close connection))
          (onError [_connection exception]
            (.printStackTrace exception))
          (onMessage [connection message]
            (on-message connection message))
          (onOpen [connection _handshake]
            (send-to connection {:type    :connected
                                 :version VERSION}))
          (onStart []))]
    (.start server)
    (println "Started server on port:" port)
    (while true
      ;; send a pulse every 10 seconds so the connection doesn't get marked as idle
      ;; clients are expected to pulse back
      (Thread/sleep 10000)
      (.broadcast server (pr-str {:type :pulse})))))
