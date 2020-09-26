(ns kutyagumi.misc.network
  (:require [kutyagumi.misc.platform :as platform]
            [clojure.core.async :as async]
            [clojure.edn :as edn])
  #?(:clj (:import (org.java_websocket.client WebSocketClient)
                   (java.net URI)
                   (org.java_websocket.handshake ServerHandshake))))

;; to match the server version
(def ^:private VERSION "1.0.0")

(defn- send-to
  #?(:clj  ([^WebSocketClient connection packet]
            (.send connection (pr-str packet)))
     :cljs ([connection packet]
            (.send connection (pr-str packet)))))

(defn- throw-and-close
  #?(:clj  ([^WebSocketClient c ex]
            (.close c)
            (throw (RuntimeException. (str ex))))
     :cljs ([c ex]
            (.close c)
            (throw (str ex)))))

(defn make-connection
  "host-or-join: :host or :join
  id: the game identifier

  Returns two async channels:
  [in-chan out-chan]"
  [uri host-or-join id]
  (async/go
    (let [server-message-chan (async/chan)
          server-connection
          #?(:clj (doto (proxy [WebSocketClient] [(URI. uri)]
                          (onClose [code reason remote]
                            (println "Connection to server closed."
                                     "Remote?" remote
                                     "Code:" code
                                     "Reason:" reason))
                          (onError [ex]
                            (.printStackTrace ex))
                          (onMessage [message]
                            (some->> (edn/read-string message)
                                     (async/put! server-message-chan)))
                          (onOpen [^ServerHandshake handshake]
                            (println "Connected to server:" (.getHttpStatusMessage handshake))))
                    (.connect))
             :cljs (doto (js/WebSocket. uri)
                     (-> .-onclose
                         (set!
                           (fn [event]
                             (println "Connection to server closed."
                                      "Code:" (.-code event)
                                      "Reason:" (.-reason event)))))
                     (-> .-onerror
                         (set!
                           (fn [event]
                             (js/console.error "WebSocket Error:" event))))
                     (-> .-onmessage
                         (set!
                           (fn [event]
                             (some->> (edn/read-string (.-data event))
                                      (async/put! server-message-chan)))))
                     (-> .-onopen
                         (set!
                           (fn [event]
                             (js/console.info "Connected to server:" event))))))]
      (let [{:keys [type version]} (async/<! server-message-chan)]
        (when-not (= type :connected)
          (throw-and-close server-connection
                           (str "Unexpected " type " packet. Expected :connected")))
        (when-not (= version VERSION)
          (throw-and-close server-connection
                           (str "Version mismatch. "
                                "Server: "
                                version
                                ", "
                                "client: "
                                VERSION
                                \.))))
      (send-to server-connection
        {:type host-or-join
         :id   id})

      (let [{:keys [type message]}
            (async/<! server-message-chan)]
        (case type
          :user-error (-> message
                          #?(:cljs js/alert
                             :clj  throw-and-close))
          :error (throw-and-close server-connection message)
          :success (#?(:clj println
                       :cljs js/alert)
                     message)
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

                  :user-error
                  (-> (:message packet)
                      #?(:cljs js/alert
                         :clj  throw-and-close))

                  :error
                  (throw-and-close server-connection (:message packet))

                  ;; TODO idk maybe handle this more gracefully
                  :disconnect
                  (let [m "Opponent disconnected."]
                    #?(:cljs (js/alert m))
                    (throw-and-close server-connection m))

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
