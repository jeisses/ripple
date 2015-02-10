(ns ripple.tiled-map
  (:require [ripple.assets :as a]
            [ripple.components :as c]
            [ripple.subsystem :as s]
            [ripple.prefab :as prefab]
            [ripple.rendering :as r]
            [brute.entity :as e])
  (:import [com.badlogic.gdx.maps.tiled TmxMapLoader]
           [com.badlogic.gdx.maps MapLayer]
           [com.badlogic.gdx Gdx]
           [com.badlogic.gdx.maps.tiled TiledMapTileLayer]
           [com.badlogic.gdx.maps.tiled.renderers OrthogonalTiledMapRenderer]))

;;
;; Representation of a LibGDX TiledMap
;;
(a/defasset tiled-map
  :create
  (fn [system {:keys [path]}]
    (-> (TmxMapLoader.)
        (.load path))))

(c/defcomponent OrthogonalTiledMapRendererComponent
  :init
  (fn [component entity system {:keys [tiled-map pixels-per-unit]}]
    (let [tiled-map (a/get-asset system tiled-map)]
      (assoc component :renderer (OrthogonalTiledMapRenderer. tiled-map (float (/ 1 pixels-per-unit)))))))

(defn render-maps
  "Render any tiled map renderer components"
  [system]
  (doseq [entity (e/get-all-entities-with-component system 'OrthogonalTiledMapRendererComponent)]
    (let [tiled-map-component (e/get-component system entity 'OrthogonalTiledMapRendererComponent)
          tiled-map-renderer (:renderer tiled-map-component)
          camera (:camera (:renderer system))]
      (.setView tiled-map-renderer camera)
      (.render tiled-map-renderer)))
  system)

(c/defcomponent TiledMapSpawner
  :fields [:tiled-map {:asset true}
           :pixels-per-unit {:default 32}])

(defn- get-object-layers
  [tiled-map]
  (filter (fn [layer] (= (type layer) MapLayer))
   (.getLayers tiled-map)))

(defn- get-map-objects [tiled-map]
  (flatten (reduce #(conj % (-> (.getObjects %2)
                                (.iterator)
                                (iterator-seq)))
                   [] (get-object-layers tiled-map))))

(defn- get-tile-layers
  [tiled-map]
  (filter (fn [layer] (= (type layer) TiledMapTileLayer))
   (.getLayers tiled-map)))

(defn- get-tile-cells-for-layer
  "Lazy seq of all available tile cells for all tile layers for a given tile layer"
  [tile-layer]
  (let [height (.getHeight tile-layer)
        width (.getWidth tile-layer)]
    (for [x (range width)
          y (range height)
          :let [tile-cell (.getCell tile-layer x y)]
          :when tile-cell]
      {:tile-cell tile-cell
       :x (+ x 0.5)  ;; TODO - don't assume 1 tile == 1 world unit?
       :y (+ y 0.5)})))

(defn- get-tile-cells
  "Lazy seq of all available tile cells for all tile layers for a given tiled-map"
  [tiled-map]
  (flatten (map #(get-tile-cells-for-layer %)
                (get-tile-layers tiled-map))))

(defmulti create-entity-from-map-object (fn [_ map-object _] (class map-object)))

(defn- get-event-connections-from-map-object
  "TODO docs"
  [map-object]
  (let [properties (.getProperties map-object)
        event-keys (filter #(= \+ (first %))
                           (-> properties
                               (.getKeys)
                               (iterator-seq)))]
    (reduce (fn [outputs event-key]
              (let [output-event (subs event-key 1)
                    connection-string (.get properties event-key)
                    [_ connected-tag connected-event] (re-matches #"(.*):(.*)" connection-string)]
                (conj outputs [output-event connected-tag (keyword connected-event)])))
            [] event-keys)))

(defmethod create-entity-from-map-object com.badlogic.gdx.maps.objects.RectangleMapObject
  [system map-object pixels-per-unit]
  "For the given map object, create and add an entity with the required components
  (if appropriate) to the ES system"
  (let [rectangle (.getRectangle map-object)
        width (/ (.width rectangle) pixels-per-unit)
        height (/ (.height rectangle) pixels-per-unit)
        tag (.getName map-object)
        x (+ (/ (.x rectangle) pixels-per-unit)
             (/ width 2))
        y (+ (/ (.y rectangle) pixels-per-unit)
             (/ height 2))
        event-connections  (get-event-connections-from-map-object map-object)]
    (prefab/instantiate system
                        (-> map-object (.getProperties) (.get "type"))
                        ;; TODO - more extendable way of instantiation params
                        {:transform {:position [x y]}
                         :eventhub {:outputs event-connections :tag tag}
                         :areatrigger {:x x :y y :width width :height height}
                         :physicsbody {:x x :y y}})))

(defmethod create-entity-from-map-object com.badlogic.gdx.maps.objects.EllipseMapObject
  [system map-object pixels-per-unit]
  "For the given map object, create and add an entity with the required components
  (if appropriate) to the ES system"
  (let [ellipse (.getEllipse map-object)
        x (/ (.x ellipse) pixels-per-unit)
        y (/ (.y ellipse) pixels-per-unit)]
    (prefab/instantiate system
                        (-> map-object (.getProperties) (.get "type"))
                        {:transform {:position [x y]}
                         :physicsbody {:x x :y y}})))

(defn- create-entities-for-map-objects
  [system tiled-map pixels-per-unit]
  (let [map-objects (get-map-objects tiled-map)]
    (reduce (fn [system map-object]
              (if (.isVisible map-object)
                (create-entity-from-map-object system map-object pixels-per-unit)
                system))
            system map-objects)))

(defn- spawn-prefab-for-tile-cell [system tile-cell x y]
  (let [prefab-name (-> tile-cell (.getTile) (.getProperties) (.get "prefab"))]
    (if (not (nil? prefab-name))
      (prefab/instantiate system
                          prefab-name
                          {:transform {:position [x y]}
                           :physicsbody {:x x :y y}})
      ;; Else, nothing to instantiate
      system)))

(defn- create-entities-for-map-tiles
  [system tiled-map pixels-per-unit]
  (let [tile-cells (get-tile-cells tiled-map)]
    (reduce (fn [system {:keys [tile-cell x y]}]
              (spawn-prefab-for-tile-cell system tile-cell x y))
            system tile-cells)))

(defn init-map-spawner
  [system entity]
  (let [spawner (e/get-component system entity 'TiledMapSpawner)
        tiled-map (:tiled-map spawner)
        pixels-per-unit (:pixels-per-unit spawner)]
    (if (not (:initialized spawner))
      (-> system
          ;; TODO intantiate prefabs for tiles..
          (e/update-component entity 'TiledMapSpawner #(assoc % :initialized true))
          (create-entities-for-map-tiles tiled-map pixels-per-unit)
          (create-entities-for-map-objects tiled-map pixels-per-unit))
      system)))

(defn init-map-spawner-components
  [system]
  (reduce init-map-spawner
          system (e/get-all-entities-with-component system 'TiledMapSpawner)))

(s/defsubsystem level
  :asset-defs [:tiled-map]
  :component-defs ['OrthogonalTiledMapRendererComponent 'TiledMapSpawner]
  :on-pre-render init-map-spawner-components
  :on-show
  (fn [system]
    (-> system
        (r/register-render-callback render-maps 0))))
