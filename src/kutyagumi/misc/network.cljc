(ns kutyagumi.misc.network
  (:require [clojure.core.async :as async]
            [clojure.edn :as edn])
  #?(:clj (:import (org.java_websocket.client WebSocketClient)
                   (java.net URI)
                   (org.java_websocket.handshake ServerHandshake))))

;; to match the server version
(def ^:private VERSION "1.1.0")

(defn- open-server-connection [uri]
  (let [in-chan (async/chan)
        out-chan (async/chan)

        open-promise (async/promise-chan)

        connection
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
                                   (async/put! in-chan)))
                        (onOpen [^ServerHandshake handshake]
                          (println "Connected to server:" (.getHttpStatusMessage handshake))
                          (async/put! open-promise true)))
                  (.connect))
           :cljs    (doto (js/WebSocket. uri)
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
                                       (async/put! in-chan)))))
                      (-> .-onopen
                          (set!
                            (fn [event]
                              (js/console.info "Connected to server:" event)
                              (async/put! open-promise true)))))
           :default nil)]
    (async/take! open-promise
      (fn []
        (async/go-loop []
          (if-some [p (async/<! out-chan)]
            (do (.send connection (pr-str p)) (recur))
            (.close connection)))))
    [in-chan out-chan]))

(defn- log-error [err]
  #?(:cljs (js/console.error err)
     :clj  (.printStackTrace err))
  nil)

(defn- fatal [err]
  (log-error err)
  #?(:cljs (js/alert (str "A fatal error has occurred."
                          err)))
  (throw err))

(defn make-connection
  "host-or-join: :host or :join
  id: the game identifier

  Returns two async channels:
  [in-chan out-chan]"
  [uri host-or-join id]
  (let [in-chan (async/chan)
        out-chan (async/chan)]
    (async/go-loop []
      (let [[server-in server-out] (open-server-connection uri)
            piped-out (async/chan)]
        (async/pipe out-chan piped-out)
        (-> (loop []
              (let [{:keys [type version]} (async/<! server-in)]
                (case type

                  :connected (if (= version VERSION)
                               true
                               (do (async/close! server-out)
                                   (async/close! piped-out)
                                   (fatal (ex-info "Version mismatch."
                                                   {:client VERSION
                                                    :server version}))
                                   ;; park forever
                                   (async/<! (async/chan))))

                  :pulse (do (async/>! server-out {:type :pulse})
                             (recur))

                  (do (async/close! server-out)
                      (async/close! piped-out)
                      (log-error (ex-info "Unexpected packet."
                                          {:type     type
                                           :expected [:connected :pulse]}))))))
            (when
              (async/>! server-out
                        {:type host-or-join
                         :id   id}))

            (when
              (loop []
                (let [{:keys [type message]}
                      (async/<! server-in)]
                  (case type
                    :user-error (do (async/close! server-out)
                                    (async/close! piped-out)
                                    (fatal (ex-info message {}))
                                    ;; park forever
                                    (async/<! (async/chan)))
                    :error (log-error (ex-info message {}))
                    :success (do (#?(:clj  println
                                     :cljs js/alert)
                                   message)
                                 true)

                    :pulse (do (async/>! server-out {:type :pulse})
                               (recur))

                    (do (async/close! server-out)
                        (async/close! piped-out)
                        (log-error (ex-info "Unexpected packet."
                                            {:type     type
                                             :expected [:user-error
                                                        :error
                                                        :success
                                                        :pulse]})))))))

            (when
              (loop []
                (let [{:keys [type]}
                      (async/<! server-in)]
                  (case type
                    :pairing
                    (do
                      (async/go-loop []
                        (when-some [payload (async/<! piped-out)]
                          (async/>! server-out
                                    {:type    :to-peer
                                     :payload (pr-str payload)})
                          (recur)))
                      (loop []
                        (let [{:keys [type]
                               :as   packet}
                              (async/<! server-in)]
                          (case type
                            :from-peer (async/>! in-chan (:payload packet))

                            :user-error
                            (do (async/close! server-out)
                                (async/close! piped-out)
                                (fatal (ex-info (:message packet) {}))
                                ;; park forever
                                (async/<! (async/chan)))

                            :error
                            (log-error (ex-info (:message packet) {}))

                            :disconnect
                            (let [m "Opponent disconnected."]
                              #?(:cljs (js/alert m))
                              (log-error (ex-info m {}))
                              (async/close! server-out)
                              (async/close! piped-out)
                              false)

                            :pulse (async/>! server-out {:type :pulse})

                            (do (async/close! server-out)
                                (async/close! piped-out)
                                (log-error (ex-info "Unexpected packet."
                                                    {:type     type
                                                     :expected [:from-peer
                                                                :user-error
                                                                :error
                                                                :disconnect
                                                                :pulse]})))))
                        (recur)))

                    :pulse (do (async/>! server-out {:type :pulse})
                               (recur))

                    (do (async/close! server-out)
                        (async/close! piped-out)
                        (log-error (ex-info "Unexpected packet."
                                            {:type     type
                                             :expected [:pairing
                                                        :pulse]})))))))))
      (println "Attempting to reconnect...")
      (recur))
    [in-chan out-chan]))
