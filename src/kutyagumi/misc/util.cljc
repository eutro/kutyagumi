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
  (if idcs
    (let [ret (nth coll (first idcs) nd-nth-sentinel)]
      (if (identical? ret nd-nth-sentinel)
        else
        (recur ret else (next idcs))))
    coll))

(defn nd-update
  "Similar to update, but multidimensional."
  [coll & args]
  (if (ifn? (first args))
    (apply (first args)
           coll
           (next args))
    (apply update
           coll
           (first args)
           nd-update
           (next args))))
