(ns ripple.core
  (:require [play-clj.core :refer :all]
            [play-clj.g2d :refer :all]
            [play-clj.utils :as u]
            [ripple.player :as player]
            [ripple.rendering :as rendering]
            [ripple.sprites :as sprites]
            [ripple.physics :as physics]
            [ripple.audio :as audio]
            [ripple.components :as c]
            [ripple.assets :as a]
            [ripple.subsystem :as subsystem]
            [ripple.prefab :as prefab]
            [ripple.tiled-map :as tiled-map]
            [brute.entity :as e]
            [brute.system :as s]))

(def sys (atom 0))

(defn- start
  "Create all the initial entities with their components"
  [system]
  (let [tile-map (e/create-entity)]
    (-> system
        (prefab/instantiate "PlatformLevel" {}))))

(defn- init-system
  "Initialize the ES system and all subsystems"
  []
  (-> (e/create-system)

      (a/init-asset-manager) ;; clears any existing asset defs

      (subsystem/register-subsystem a/assets)
      (subsystem/register-subsystem rendering/rendering)
      (subsystem/register-subsystem physics/physics)
      (subsystem/register-subsystem prefab/prefabs)
      (subsystem/register-subsystem sprites/sprites)
      (subsystem/register-subsystem audio/audio)
      (subsystem/register-subsystem player/player)
      (subsystem/register-subsystem tiled-map/level)

      ;; Initialize component module (clear component defs) (unecessary if we keep them in system!)

      ;; Initialise subsystems
      (subsystem/on-system-event :on-show) ;; TODO rename

      (start)))

(defscreen main-screen
  :on-show
  (fn [screen entities]
    (reset! sys (init-system))
    (update! screen :renderer (stage) :camera (orthographic))
    nil)

  :on-touch-down
  (fn [screen entities]
    (reset! sys (-> @sys (subsystem/on-system-event :on-touch-down)))
    nil)

  :on-render
  (fn [screen entities]
    (reset! sys (-> @sys
                    (subsystem/on-system-event :on-pre-render)
                    (subsystem/on-system-event :on-render)))
    nil)

  :on-resize
  (fn [screen entities]
    (subsystem/on-system-event  @sys :on-resize)
    nil))

(defgame ripple
  :on-create
  (fn [this]
    (set-screen! this main-screen)))
