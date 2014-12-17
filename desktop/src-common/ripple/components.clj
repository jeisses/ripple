(ns ripple.components
  (:require
   [brute.entity :refer :all])
  (:import
   [clojure.lang PersistentArrayMap]))

;; TODO remove
(defrecord Player [])
(defrecord TiledMapRendererComponent [tiled-map-renderer])
(defrecord SpriteRenderer [texture])
(defrecord Position [x y])



(defmethod get-component-type PersistentArrayMap
  [component]
  (:type component))

(def component-defs (atom {}))

(defn get-component-def [component-symbol]
  (get @component-defs component-symbol))

(defn remove-component-def [component-symbol]
  (swap! component-defs dissoc component-symbol))

(defn register-component-def [component-symbol create-fn]
  (swap! component-defs assoc component-symbol create-fn))

(defmacro defcomponent
  [n & options]
  `(let [options# ~(apply hash-map options)
         create-fn# (:create options#)]
     (register-component-def '~n (assoc options#
                                        :create-component #(assoc (create-fn# %1 %2)
                                                                  :type '~n)))))

(defn create-component [system component-symbol params]
  (-> (get-component-def component-symbol)
      (get :create-component)
      (apply [system params])))

;; CORE COMPONENT DEFS - probably move elsewhere...

(defcomponent Player
  :create
  (fn [system params]))

(defcomponent TiledMapRendererComponent
  :create
  (fn [system params]))

(defcomponent SpriteRenderer
  :create
  (fn [system params]))

(defcomponent Position
  :create
  (fn [system params]
    {:x (:x params)
     :y (:y params)}))
