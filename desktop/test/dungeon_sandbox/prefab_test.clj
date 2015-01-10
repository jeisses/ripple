(ns dungeon-sandbox.prefab-test
  (:use [clojure.test])
  (:require [dungeon-sandbox.prefab :as prefab]
            [brute.entity :as e]
            [dungeon-sandbox.asset-database :as asset-db]
            [dungeon-sandbox.components :as components]))

(deftest prefab

  (components/defcomponent TestComponent
    :create (fn [system params]
              {:some-field (* (:x params)
                              (:y params))
               :stuff (:stuff (asset-db/get-asset system params))}))

  (asset-db/defasset TestAsset
    :create (fn [system params] params))

  ;; TODO - would be nice if we could pass some callback to instantiate, or get the new instance UUID back
  ;; along with the system...

  (testing "prefab instantiation"

    ;; TODO - test prefabs that refer to other assets

    (let [system (asset-db/start {} "resources/test/test-prefab.yaml")]
      (let [system-with-instance (prefab/instantiate system "TestPrefab" {})
            entity (first (e/get-all-entities system-with-instance))
            component (e/get-component system-with-instance entity 'TestComponent)]

        (is (not= entity nil))
        (is (not= component nil))
        (is (= (:some-field component) 15))
        (is (= (:stuff component) "thing"))))))
