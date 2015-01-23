(ns ripple.player
  (:require [play-clj.core :refer :all]
            [brute.entity :as e]
            [ripple.components :refer :all]
            [ripple.sprites :as sprites]
            [ripple.prefab :as prefab]
            [ripple.assets :as a]
            [ripple.subsystem :as s])
  (:import [com.badlogic.gdx.math Vector2]
           [com.badlogic.gdx Input$Keys]
           [com.badlogic.gdx Gdx]))

;; An example Player component that handles physics-based movement with arrow keys

(defcomponent Player
  :create
  (fn [system {:keys [move-force

                      bullet-prefab
                      bullet-speed
                      bullet-offset

                      walk-animation
                      idle-animation
                      idle-down-forward-animation
                      idle-down-animation
                      idle-up-animation
                      idle-up-forward-animation
                      walking-down-forward-animation
                      walking-down-animation
                      walking-up-animation
                      walking-up-forward-animation]
               :as params}]
    {:move-force move-force

     :bullet-prefab bullet-prefab
     :bullet-speed bullet-speed
     :bullet-offset bullet-offset

     :state [:walking :aim-forward]

     :walk-animation (a/get-asset system walk-animation)
     :idle-animation (a/get-asset system idle-animation)
     :idle-up-forward-animation (a/get-asset system idle-up-forward-animation)
     :idle-up-animation (a/get-asset system idle-up-animation)
     :idle-down-forward-animation (a/get-asset system idle-down-forward-animation)
     :idle-down-animation (a/get-asset system idle-down-animation)

     :walking-up-forward-animation (a/get-asset system walking-up-forward-animation)
     :walking-up-animation (a/get-asset system walking-up-animation)
     :walking-down-forward-animation (a/get-asset system walking-down-forward-animation)
     :walking-down-animation (a/get-asset system walking-down-animation)}))

;; TODO - this will need to be smarter with movable camera... cant assume bottom left is 0,0

(defn- screen-to-world
  [system screen-x screen-y]
  (let [pixels-per-unit (get-in system [:renderer :pixels-per-unit])
        camera (get-in system [:renderer :camera])
        screen-width (.getWidth Gdx/graphics)
        screen-height (.getHeight Gdx/graphics)
        screen-y (- screen-height screen-y) ;; relative to bottom-left
        world-x (/ screen-x pixels-per-unit)
        world-y (/ screen-y pixels-per-unit)]
    [(float world-x) (float world-y)]))

(def cardinal-directions-to-aim-states
  (map (fn [[dir anim]] [(.nor dir) anim])
       [[(Vector2. 0 1) :aim-up]
        [(Vector2. 1 1) :aim-up-forward]
        [(Vector2. 1 0) :aim-forward]
        [(Vector2. 1 -1) :aim-down-forward]
        [(Vector2. 0 -1) :aim-down]
        [(Vector2. -1 -1) :aim-down-forward]
        [(Vector2. -1 0) :aim-forward]
        [(Vector2. -1 1) :aim-up-forward]]))

(defn- get-aim-state-for-direction
  "Sorts cardinal directions by distance from direction, and
  returns the aim state for the closest direction"
  [direction]
  (let [sorted-directions-to-anims (->> cardinal-directions-to-aim-states
                                        (map (fn [[dir anim]]
                                               [(-> (.sub (.cpy dir) direction)
                                                    (.len2))
                                                anim]))
                                        (sort-by first))]
    (-> sorted-directions-to-anims
        (first)
        (second))))

(defn- get-player-aim-state
  [system entity]
  (let [[mouse-x mouse-y] (screen-to-world system (.getX Gdx/input) (.getY Gdx/input))
        [player-x player-y] (:position (e/get-component system entity 'Transform))
        player-to-mouse (Vector2. (- mouse-x player-x)
                                  (- mouse-y player-y))]
    (get-aim-state-for-direction player-to-mouse)))

(defn- get-player-aim-direction
  [system entity]
  (let [[mouse-x mouse-y] (screen-to-world system (.getX Gdx/input) (.getY Gdx/input))
        [player-x player-y] (:position (e/get-component system entity 'Transform))
        player-to-mouse (Vector2. (- mouse-x player-x)
                                  (- mouse-y player-y))]
    (.nor player-to-mouse)))

(defn- get-player-walk-state
  [system entity]
  (let [player (e/get-component system entity 'Player)
        v2 (-> (e/get-component system entity 'PhysicsBody)
               (:body)
               (.getLinearVelocity)
               (.len2))]        
    (if (> v2 0.1) :walking :standing)))

(def state-to-anim
  {[:standing :aim-forward] :idle-animation
   [:standing :aim-up-forward] :idle-up-forward-animation
   [:standing :aim-up] :idle-up-animation
   [:standing :aim-down-forward] :idle-down-forward-animation
   [:standing :aim-down] :idle-down-animation
   [:walking :aim-forward] :walk-animation
   [:walking :aim-up-forward] :walking-up-forward-animation
   [:walking :aim-up] :walking-up-animation
   [:walking :aim-down-forward] :walking-down-forward-animation
   [:walking :aim-down] :walking-down-animation}) 

(defn enter-state 
  [system entity state]
  (let [anim-ref (get state-to-anim state)
        anim (get (e/get-component system entity 'Player) anim-ref)]
    (sprites/play-animation system entity anim)))

(defn- get-player-state [system entity]
  (let [walk-state (get-player-walk-state system entity)
        aim-state (get-player-aim-state system entity)]
    [walk-state aim-state]))

(defn- update-state [system entity]
  (let [player (e/get-component system entity 'Player)
        old-state (:state player)
        new-state (get-player-state system entity)]
    (if (not (= old-state new-state))
      (-> system
          (enter-state entity new-state)
          (e/update-component entity 'Player #(-> % (assoc :state new-state))))
      system)))

(defn- update-player-aim
  "Flip x scale appropriately based on mouse direction from player"
  [system entity]
  (let [[mouse-x mouse-y] (screen-to-world system (.getX Gdx/input) (.getY Gdx/input))
        [player-x player-y] (:position (e/get-component system entity 'Transform))
        x-scale (if (< (- mouse-x player-x) 0)
                  -1 1)]
    (-> system
        (e/update-component entity 'Transform #(assoc % :scale [x-scale 1])))))

;; Player Movement

(defn- get-move-direction []
  "Return normalized movement direction for whatever movement keys are currently depressed"
  (let [keys-to-direction {Input$Keys/DPAD_UP (Vector2. 0 1)
                           Input$Keys/DPAD_DOWN (Vector2. 0 -1)
                           Input$Keys/DPAD_LEFT (Vector2. -1 0)
                           Input$Keys/DPAD_RIGHT (Vector2. 1 0)
                           Input$Keys/W (Vector2. 0 1)
                           Input$Keys/S (Vector2. 0 -1)
                           Input$Keys/A (Vector2. -1 0)
                           Input$Keys/D (Vector2. 1 0)}]
    (-> (reduce (fn [move-direction [keycode direction]]
                  (if (.isKeyPressed Gdx/input keycode)
                    (.add move-direction direction)
                    move-direction))
                (Vector2. 0 0) keys-to-direction)
        (.nor))))

(defn- apply-movement-force
  [system entity direction]
  (let [body (-> (e/get-component system entity 'PhysicsBody)
                 (:body))
        force (-> (e/get-component system entity 'Player)
                  (:move-force))
        force (.scl direction (float force))]
    (.applyForceToCenter body force true)))


(defn- update-player-movement
  [system entity]
  (let [direction (get-move-direction)]
    (when (> (.len2 direction) 0)
      (apply-movement-force system entity direction))
    system))

(defn- player-fire
  [system entity]
  (let [player (e/get-component system entity 'Player)
        bullet-prefab (:bullet-prefab player)
        aim-direction (get-player-aim-direction system entity)
        bullet-offset (float (:bullet-offset player))
        bullet-speed (float (:bullet-speed player))
        [x y] (:position (e/get-component system entity 'Transform))
        bullet-origin (.add (Vector2. x y)
                            (.scl (.cpy aim-direction) bullet-offset))
        bullet-velocity (.scl aim-direction bullet-speed)]
    (prefab/instantiate system bullet-prefab {:physicsbody {:x (.x bullet-origin)
                                                            :y (.y bullet-origin)
                                                            :velocity-x (.x bullet-velocity)
                                                            :velocity-y (.y bullet-velocity)}})))

;; Player Update
(defn- handle-mouse-input
  [system]
  (let [entity (-> (e/get-all-entities-with-component system 'Player)
                   (first))]
    (player-fire system entity)))

(defn- update-player
  [system entity]
  (let [player (e/get-component system entity 'Player)]
    (-> system
        (update-state entity)
        (update-player-aim entity)
        (update-player-movement entity))))

(defn- update-player-components
  [system]
  (let [player-entities (e/get-all-entities-with-component system 'Player)]
    (reduce update-player system player-entities)))

(s/defsubsystem player
  :on-render update-player-components
  :on-touch-down handle-mouse-input)
