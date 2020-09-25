(ns kutyagumi.logic.player.rtc
  (:require [kutyagumi.logic.player.core #?@(:cljs [:refer [Player]])]
            [clojure.core.async :as async]
            [kutyagumi.misc.network :as nw])
  #?(:clj (:import (kutyagumi.logic.player.core Player))))

(defrecord RTCPlayer [in out]
  Player
  (next-move [_ _game]
    (async/go (async/<! in)))
  (update-state [this state]
    (async/go
      (async/go (async/>! out state))
      this)))

(defn ->rtc-player [id]
  (async/go
    (let [[in out] (async/<! (nw/make-connection :host id))]
      (->RTCPlayer in out))))
