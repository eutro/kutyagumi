(ns kutyagumi.misc.network
  (:require [kutyagumi.misc.platform :as platform]
            [clojure.core.async :as async]
            [clojure.edn :as edn])
  (:import (org.java_websocket.client WebSocketClient)
           (java.net URI)
           (org.java_websocket.handshake ServerHandshake)))

;; to match the server version
(def ^:private VERSION "1.0.0")
(def ^:private SERVER_URI* (promise))

(async/take! (platform/get-edn "config/server.edn")
  (fn [uri] (deliver SERVER_URI* (URI. uri))))

(defn- send-to
  [^WebSocketClient connection packet]
  (.send connection (pr-str packet)))

(defn- throw-and-close [^WebSocketClient c ex]
  (.close c)
  (throw (RuntimeException. (str ex))))

(defn make-connection
  "host-or-join: :host or :join
  id: the game identifier

  Returns two async channels:
  [in-chan out-chan]"
  [host-or-join id]
  (let [server-message-chan (async/chan)
        server-connection
        (proxy [WebSocketClient] [@SERVER_URI*]
          (onClose [code reason remote]
            (println "Connection to server closed."
                     "Remote?" remote
                     "Code:" code
                     "Reason:" reason))
          (onError [ex]
            (.printStackTrace ex))
          (onMessage [message]
            (some->> (edn/read-string message)
                     (async/>!! server-message-chan)))
          (onOpen [^ServerHandshake handshake]
            (println "Connected to server:" (.getHttpStatusMessage handshake))))]
    (.connect server-connection)
    (async/go
      (let [{:keys [type version]} (async/<! server-message-chan)]
        (when-not (= type :connected)
          (throw-and-close server-connection
                           (str "Unexpected " type " packet. Expected :connected")))
        (when-not (= version VERSION)
          (throw-and-close server-connection
                           (format "Version mismatch. Server: %s, client: %s."
                                   version
                                   VERSION))))
      (send-to server-connection
        {:type host-or-join
         :id   id})

      (let [{:keys [type message]}
            (async/<! server-message-chan)]
        (case type
          (:user-error :error) (throw-and-close server-connection message)
          :success (println message)
          (throw-and-close server-connection
                           (str "Unexpected packet type: " type))))

      (let [{:keys [type]}
            (async/<! server-message-chan)]
        (if (= :pairing type)
          (let [in-chan (async/chan)
                out-chan (async/chan)]
            (async/go-loop []
              (let [{:keys [type]
                     :as   packet}
                    (async/<! server-message-chan)]
                (case type
                  :from-peer (async/>! in-chan (:payload packet))

                  (:error :user-error)
                  (throw-and-close server-connection (:message packet))

                  ;; TODO idk maybe handle this more gracefully
                  :disconnect
                  (throw-and-close server-connection "Disconnected.")

                  (throw-and-close server-connection (str "Unexpected packet type: " type))))
              (recur))
            (async/go-loop []
              (when-some [payload (async/<! out-chan)]
                (send-to server-connection
                  {:type    :to-peer
                   :payload (pr-str payload)})
                (recur)))

            [in-chan out-chan])
          (throw-and-close server-connection (str "Unexpected packet type: " type)))))))
