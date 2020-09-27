(ns kutyagumi.misc.util)

(defn nd-nth
  "Index a multidimensional sequence."
  [coll & idcs]
  (if idcs
    (recur (nth coll (first idcs))
           (next idcs))
    coll))

(def ^:private nd-nth-sentinel
  'kutyagumi.misc.util/__SENTINEL)

(defn nd-nth-else
  "Index a multidimensional sequence,
  returning else if out of bounds."
  [coll else & idcs]
  (if (seq idcs)
    (let [ret (nth coll (first idcs) nd-nth-sentinel)]
      (if (identical? ret nd-nth-sentinel)
        else
        (recur ret else (next idcs))))
    coll))

(defn nd-update
  "Similar to update, but multidimensional.

  The last argument is to be a seq of arguments
  to pass the penultimate argument to update."
  [coll & args]
  (if (seq (nnext args))
    (apply update
           coll
           (first args)
           nd-update
           (next args))
    (apply (first args)
           coll
           (second args))))

(defn transpose [m]
  "Transpose a 2D matrix."
  (when (seq m) (apply mapv vector m)))

(defn zip
  "Similar to transpose."
  [& colls]
  (apply map vector colls))

(defn count-cells
  "Count the cells owned by this player."
  [board owner]
  (count (filter #(= (:owner %) owner)
                 (flatten board))))
