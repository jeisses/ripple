(ns ripple.asset-database
  (:import [com.badlogic.gdx.graphics.g2d TextureRegion Sprite Animation]
           [com.badlogic.gdx.graphics Texture]
           [com.badlogic.gdx.maps MapLayer]
           [com.badlogic.gdx.maps.tiled TmxMapLoader]
           [ripple.components Position SpriteRenderer TiledMapRendererComponent Player])
  (:require [play-clj.core :refer :all]
            [play-clj.g2d :refer :all]
            [play-clj.utils :as u]
            [ripple.components :as c]
            [clj-yaml.core :as yaml]
            [brute.entity :as e]
            [brute.system :as s]))

(def asset-defs (atom {}))

(defn get-asset-def [asset-symbol]
  (get @asset-defs asset-symbol))

(defn remove-asset-def [asset-symbol]
  (swap! asset-defs dissoc asset-symbol))

(defn register-asset-def [asset-symbol create-fn]
  (swap! asset-defs assoc asset-symbol create-fn))

(defn get-asset [system asset-name]
  "Get an asset by name and instantiate it"
  (let [asset-db (:asset-db system)
        asset (get asset-db asset-name)
        create-fn (-> (symbol (:asset asset))
                      (get-asset-def)
                      (:create))]
    (create-fn system asset)))

(defmacro defasset
  [n & options]
  `(let [options# ~(apply hash-map options)]
     (register-asset-def '~n options#)))

;; Core asset definitions

(defasset texture
  :create
  (fn [system params]
    (Texture. (:path params))))

(defasset texture-region
  :create
  (fn [system params]
    (let [texture (get-asset system (:texture params))
          x (first (:tile-indices params))
          y (second (:tile-indices params))
          width (first (:tile-size params))
          height (second (:tile-size params))]
      (TextureRegion. texture x y width height))))

(defasset animation
  :create
  (fn [system params]
    ;; create a LibGDX animation using the asset params
    (let [frame-duration (:frame-speed params)
          frame-width (:tile-width params)
          frame-height (:tile-height params)
          texture (get-asset system (:texture params))
          key-frames (map #(TextureRegion. texture
                                           (* frame-width (first %))
                                           (* frame-height (second %))
                                           frame-width
                                           frame-height)
                          (:frames params))]
      (Animation. (float frame-duration)
                  (u/gdx-array key-frames)))))


(defn create-component [type params]
  "Given a component type and some params, instantiate the component data"
  (let [record-constructor (-> (apply str ["c/->" type]) ;; ghettoooo
                               (symbol)
                               (resolve))]
    (apply record-constructor params)))

(defn create-entity-from-prefab [system prefab-name params]
  (let [prefab (-> (:asset-db system)
                      (get prefab-name))
           entity (e/create-entity system)
           components (:components prefab)
           system-with-entity (e/add-entity system entity)]
    (reduce (fn [system component] (e/add-component system component))
            system components)
    system))

(defn create-component [type params]
  "Given a component type and some params, instantiate the component data"
  (let [record-constructor (-> (apply str ["c/->" type]) ;; ghettoooo
                               (symbol)
                               (resolve))]
    (apply record-constructor params)))

(defn create-entity-from-prefab [system prefab-name params]
  (let [prefab (-> (:asset-db system)
                      (get prefab-name))
           entity (e/create-entity system)
           components (:components prefab)
           system-with-entity (e/add-entity system entity)]
    (reduce (fn [system component] (e/add-component system component))
            system components)
    system))

(defn- load-asset-file [path]
  (yaml/parse-string
   (slurp path)))

(defn init-asset-db [] {})

(defn load-asset [asset-db asset]
  (assoc asset-db (:name asset) asset))

(defn start
  "Start this system"
  [system asset-files]
  (let [asset-db (init-asset-db)
        parsed-assets (flatten (map load-asset-file asset-files))]
    (assoc system :asset-db (reduce load-asset asset-db parsed-assets))))
