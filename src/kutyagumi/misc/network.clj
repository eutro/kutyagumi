(ns kutyagumi.misc.network
  (:require [kutyagumi.misc.platform :as platform]
            [clojure.core.async :as async]
            [clojure.edn :as edn])
  (:import (dev.onvoid.webrtc PeerConnectionFactory RTCConfiguration PeerConnectionObserver RTCIceServer RTCIceCandidate RTCSessionDescription RTCSdpType SetSessionDescriptionObserver RTCDataChannelInit RTCDataChannelObserver RTCDataChannelBuffer RTCAnswerOptions CreateSessionDescriptionObserver RTCOfferOptions)
           (org.java_websocket.client WebSocketClient)
           (java.net URI)
           (org.java_websocket.handshake ServerHandshake)
           (java.nio ByteBuffer)))

;; to match the server version
(def ^:private VERSION "1.0.0")

(def ^:private PCF (PeerConnectionFactory.))
(def ^:private CONFIG (RTCConfiguration.))
(def ^:private SERVER_URI* (promise))

(async/take! (platform/get-edn "config/server.edn")
  (fn [uri] (deliver SERVER_URI* (URI. uri))))

(async/take! (platform/get-edn "config/ice_servers.edn")
  (fn [servers]
    (doseq [{:keys [url]} servers]
      (.add (.-iceServers CONFIG)
            (doto (RTCIceServer.)
              (-> .-urls (.add url)))))))

(defn- send-to
  [^WebSocketClient connection packet]
  (.send connection (pr-str packet)))

(def ^:private default-ssdo
  (reify SetSessionDescriptionObserver
    (onSuccess [_])
    (onFailure [_ error]
      (.printStackTrace (RuntimeException. error)))))

(defn- throw-and-close [^WebSocketClient c ex]
  (.close c)
  (throw (RuntimeException. (str ex))))

(defn make-connection
  "host-or-join: :host or :join
  id: the game identifier

  Returns two async channels:
  [rtc-in rtc-out]"
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
          (let [rtc-connection
                (.createPeerConnection
                  PCF
                  CONFIG
                  (reify PeerConnectionObserver
                    (onIceCandidate [_ candidate]
                      (send-to server-connection
                        {:candidate (.-sdp candidate)}))))

                data-channel
                (.createDataChannel rtc-connection "data" (RTCDataChannelInit.))

                rtc-in (async/chan)
                rtc-out (async/chan)]
            (async/go-loop []
              (let [{:keys [type]
                     :as   packet}
                    (async/<! server-message-chan)]
                (case type
                  :candidate
                  (.addIceCandidate rtc-connection (RTCIceCandidate. nil nil (:candidate packet)))

                  :answer
                  (.setRemoteDescription rtc-connection
                                         (RTCSessionDescription. RTCSdpType/ANSWER (:answer packet))
                                         default-ssdo)

                  :offer
                  (doto rtc-connection
                    (.setRemoteDescription
                      (RTCSessionDescription. RTCSdpType/OFFER (:offer packet))
                      default-ssdo)
                    (.createAnswer
                      (RTCAnswerOptions.)
                      (reify CreateSessionDescriptionObserver
                        (onSuccess [_ description]
                          (.setLocalDescription rtc-connection description default-ssdo)
                          (send-to server-connection
                            {:type   :answer
                             :answer (.-sdp description)}))
                        (onFailure [_ error]
                          (.printStackTrace (RuntimeException. error))))))

                  ;; TODO idk maybe handle this more gracefully
                  :disconnect
                  (throw-and-close server-connection "Disconnected.")

                  (throw-and-close server-connection (str "Unexpected packet type: " type))))
              (recur))
            (async/go-loop []
              (.send data-channel
                     (RTCDataChannelBuffer.
                       (ByteBuffer/wrap (.getBytes (async/<! rtc-out) "UTF-8"))
                       false))
              (recur))
            (.registerObserver data-channel
                               (reify RTCDataChannelObserver
                                 (onBufferedAmountChange [_ _prev])
                                 (onStateChange [_])
                                 (onMessage [_ buffer]
                                   (async/>!! rtc-in (String. (.array (.-data buffer)) "UTF-8")))))

            ;; make an initial offer if joining
            (if (= :join host-or-join)
              (.createOffer
                rtc-connection
                (RTCOfferOptions.)
                (reify CreateSessionDescriptionObserver
                  (onSuccess [_ description]
                    (send-to server-connection
                      {:type  :offer
                       :offer (.-sdp description)})
                    (.setLocalDescription rtc-connection description default-ssdo))
                  (onFailure [_ error]
                    (.printStackTrace (RuntimeException. error))))))

            [rtc-in rtc-out])
          (throw-and-close server-connection (str "Unexpected packet type: " type)))))))
