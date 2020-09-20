(ns kutyagumi.render.core)

(defprotocol BoardRenderer
  (render [this board game] "Render the board."))

(def *renderer (atom nil))

(extend-type nil
  BoardRenderer
  (render [_ _ _]
    (throw (ex-info "Attempting to render with null @kutyagumi.render.core/*renderer" {}))))
