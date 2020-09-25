(ns kutyagumi.misc.network
  (:require [cljs.core.async :as async]
            [kutyagumi.misc.platform :as platform]
            [clojure.edn :as edn]))

;; to match the server version
(def ^:private VERSION "1.0.0")

(def ^:private ICE_SERVERS (async/promise-chan))
(def ^:private SERVER_URI (async/promise-chan))

(async/pipe (platform/get-edn "config/server.edn") SERVER_URI)
(async/pipe (platform/get-edn "config/ice_servers.edn") ICE_SERVERS)

(defn- send-to
  [connection packet]
  (.send connection (pr-str packet)))

(defn make-connection
  "host-or-join: :host or :join
  id: the game identifier

  Returns two async channels:
  [rtc-in rtc-out]"
  [host-or-join id]
  (async/go
    (let [server-message-chan (async/chan)
          ws (js/WebSocket. async/<! SERVER_URI)]
      (doto ws
        (.onopen (fn [] (println "Connected to server.")))
        (.onerror (fn [error] (println "Error:" error)))
        (.onmessage
          (fn [message]
            (async/put! server-message-chan (edn/read-string message)))))
      (let [{:keys [type version]} (async/<! server-message-chan)]
        (when-not (= type :connected)
          (throw (str "Unexpected " type " packet. Expected :connected")))
        (when-not (= version VERSION)
          (throw (str "Version mismatch. Server: "
                      version
                      ", client: "
                      VERSION
                      "."))))

      (send-to ws
        {:type host-or-join
         :id   id})

      (let [{:keys [type message]}
            (async/<! server-message-chan)]
        (case type
          (:user-error :error) (throw (str message))
          :success (println message)
          (throw (str "Unexpected packet type: " type))))

      (let [{:keys [type]}
            (async/<! server-message-chan)]
        (if (= :pairing type)
          (let [rtc-connection
                (js/webkitRTCPeerConnection.
                  {"iceServers" (async/<! ICE_SERVERS)})

                data-channel
                (.createDataChannel rtc-connection "data" {"reliable" true})

                rtc-in (async/chan)
                rtc-out (async/chan)]
            (.onicecandidate
              rtc-connection
              (fn [event]
                (when event
                  (send-to ws
                    {:type      :candidate
                     :candidate (.-candidate event)}))))
            (async/go-loop []
              (let [{:keys [type]
                     :as   packet}
                    (async/<! server-message-chan)]
                (case type
                  :candidate
                  (.addIceCandidate rtc-connection (js/RTCIceCandidate. (:candidate packet)))

                  :answer
                  (.setRemoteDescription rtc-connection (js/RTCSessionDescription. (:answer packet)))

                  :offer
                  (doto rtc-connection
                    (.setRemoteDescription (js/RTCSessionDescription. (:offer packet)))
                    (.createAnswer
                      (fn [answer]
                        (.setLocalDescription rtc-connection answer)
                        (send-to server-connection
                          {:type   :answer
                           :answer (.-sdp description)}))
                      (fn [error] (println "Error:" error))))

                  ;; TODO idk maybe handle this more gracefully
                  :disconnect
                  (throw "Disconnected.")

                  (throw (str "Unexpected packet type: " type))))
              (recur))
            (async/go-loop []
              (.send data-channel (async/<! rtc-out))
              (recur))
            (.onmessage
              data-channel
              (fn [event]
                (async/put! rtc-in (.-data event))))

            ;; make an initial offer if joining
            (if (= :join host-or-join)
              (.createOffer
                rtc-connection
                (fn [offer]
                  (send-to ws
                    {:type  :offer
                     :offer offer})
                  (.setLocalDescription rtc-connection offer))
                (fn [error] (println "Error:" error))))

            [rtc-in rtc-out])
          (throw (str "Unexpected packet type: " type)))))))
