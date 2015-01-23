(ns ripple.physics
  (:require [play-clj.core :refer :all]
            [play-clj.g2d :refer :all]
            [play-clj.utils :as u]
            [ripple.subsystem :as s]
            [ripple.components :as c]
            [ripple.rendering :as r]
            [ripple.assets :as asset-db]
            [brute.entity :as e])
  (:import [com.badlogic.gdx.physics.box2d
            World
            BodyDef
            BodyDef$BodyType
            PolygonShape
            CircleShape
            FixtureDef
            Box2DDebugRenderer]
           [com.badlogic.gdx.math Vector2]
           [com.badlogic.gdx Gdx]))

(defmulti get-shape-def (fn [{:keys [shape]}] shape))

(defmethod get-shape-def "circle"
  [{:keys [radius]}]
  (doto (CircleShape.)
    (.setRadius radius)))

(defmethod get-shape-def "box"
  [{:keys [width height]}]
  (doto (PolygonShape.)
    (.setAsBox (/ width 2) (/ height 2))))

(defn- get-fixture-def
  [{:keys [shape density friction] :as params}]
  (let [shape-def (get-shape-def params)]
    (doto (FixtureDef.)
      (-> .shape (set! shape-def))
      (-> .density (set! density))
      (-> .friction (set! friction)))))

(c/defcomponent PhysicsBody
  :init
  (fn [component system {:keys [x y fixtures body-type fixed-rotation velocity-x velocity-y]}]
    (let [world (get-in system [:physics :world])
          body-type (case body-type
                      "dynamic" BodyDef$BodyType/DynamicBody
                      "kinematic" BodyDef$BodyType/KinematicBody
                      "static" BodyDef$BodyType/StaticBody)
          fixed-rotation (Boolean/valueOf fixed-rotation)
          body-def (doto (BodyDef.)
                     (-> .type (set! body-type))
                     (-> .position (.set x y))
                     (-> .fixedRotation (set! fixed-rotation)))
          body (doto (.createBody world body-def)
                 (.setLinearVelocity velocity-x velocity-y))]
      (assoc component :body (reduce #(doto %1 (.createFixture (get-fixture-def %2)))
                                     body fixtures)))))

(defn- create-world []
  (let [gravity (Vector2. 0 -9.8)
        do-sleep true]
    (World. gravity do-sleep)))

(defn- update-physics-body
  "Updates the Transform component on the entity with the current position and rotation of the Box2D body"
  [system entity]
  (let [body (-> (e/get-component system entity 'PhysicsBody)
                 (:body))
        body-position (.getPosition body)
        x (.x body-position)
        y (.y body-position)
        rotation (-> (.getAngle body)
                     (Math/toDegrees))]
    (e/update-component system entity 'Transform #(assoc % :position [x y] :rotation rotation))))

(defn- update-physics-bodies
  [system]
  (let [entities (e/get-all-entities-with-component system 'PhysicsBody)]
    (reduce update-physics-body
            system entities)))

(def debug-render? true)

(defn- debug-render*
  [system]
  (when debug-render?
    (let [debug-renderer (get-in system [:physics :debug-renderer])
          world (get-in system [:physics :world])
          camera (get-in system [:renderer :camera])
          projection-matrix (.combined camera)]
      (.render debug-renderer world projection-matrix)))
  system)

(defn- debug-render [system] (debug-render* system) system)

(s/defsubsystem physics

  :on-show
  (fn [system]
    (-> system
        (assoc-in [:physics :world] (create-world))
        (assoc-in [:physics :debug-renderer] (Box2DDebugRenderer.))
        (r/register-render-callback debug-render 2)))

  :on-pre-render
  (fn [system]
    (let [world (get-in system [:physics :world])]
      (.step world (.getDeltaTime Gdx/graphics) 6 2)) ;; TODO - want fixed physics update
    (-> system
        (update-physics-bodies))))

;; TODO - how much should we be cleaning up? (.dispose world) etc..
