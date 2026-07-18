(ns suki.murakumo-test
  (:require [clojure.test :refer [deftest is testing]]
            [suki.murakumo :as suki]))

(def full-attestations
  (into {}
        (map (fn [gate] [gate (str "attested-" (name gate))]))
        (distinct (mapcat :required-gates (vals suki/cell-specs)))))

(deftest maps-all-legacy-suki-cells
  (is (= #{"suki_cab_assembly"
           "suki_chassis_fabrication"
           "suki_electrical_ecu_load"
           "suki_emissions_audit"
           "suki_hitch_pto_assembly"
           "suki_paint_finishing"
           "suki_powertrain_assembly"
           "suki_quality_field_test"
           "suki_vehicle_attestation_binder"}
         (set (map :legacy-cell (vals suki/cell-specs))))))

(deftest r0-gates-block-effects
  (let [plan (suki/cell-plan :chassis-fabrication
                             {:chassis-id "chassis-001"
                              :computed-at "2026-06-29T00:00:00Z"})]
    (is (= :blocked (:status plan)))
    (is (= [:council-charter-attestation
            :silen-suki-baseline-review
            :ag-engineering-sme-registry
            :ecu-engineer-sme-registry
            :ag-mechanic-sme-registry
            :r1-activation-adr
            :robot-witness-quorum-baseline
            :open-chassis-cad-baseline
            :steel-provenance-baseline
            :kasane-welding-baseline]
           (:missing-gates plan)))
    (is (empty? (:effects plan)))))

(deftest attested-ecu-emits-rtr-effect
  (let [plan (suki/cell-plan :electrical-ecu-load
                             {:attestations full-attestations
                              :vin "SUKI-0001"
                              :computed-at "2026-06-29T00:00:00Z"
                              :record {:tid "ecu-001"
                                       :bootloaderUnlockDefault true
                                       :canBusProtocol "ISOBUS"}})
        effect (first (:effects plan))]
    (is (= :ready (:status plan)))
    (is (= :mst/put-record (:op effect)))
    (is (= suki/actor-did (:actor effect)))
    (is (= "com.etzhayyim.suki.electricalEcuAttestation" (:collection effect)))
    (is (= "ecu-001" (:rkey effect)))
    (is (= true (get-in effect [:record :rightToRepair])))
    (is (= true (get-in effect [:record :bootloaderUnlockDefault])))))

(deftest special-gates-remain-cell-specific
  (testing "ECU keeps right-to-repair gate"
    (let [attestations (dissoc full-attestations :right-to-repair-no-drm-baseline)
          plan (suki/cell-plan :electrical-ecu-load {:attestations attestations})]
      (is (= [:right-to-repair-no-drm-baseline] (:missing-gates plan)))
      (is (empty? (:effects plan)))))
  (testing "field test keeps SAE level 3 driver-in-seat gate"
    (let [attestations (dissoc full-attestations :sae-level-3-driver-in-seat-baseline)
          plan (suki/cell-plan :quality-field-test {:attestations attestations})]
      (is (= [:sae-level-3-driver-in-seat-baseline] (:missing-gates plan)))
      (is (empty? (:effects plan))))))

(deftest all-cell-plans-ready-when-attested
  (let [plans (suki/all-cell-plans {:attestations full-attestations
                                    :vin "SUKI-0001"
                                    :chassis-id "chassis-001"
                                    :computed-at "2026-06-29T00:00:00Z"})]
    (is (= (set (keys suki/cell-specs)) (set (keys plans))))
    (is (every? #(= :ready (:status %)) (vals plans)))
    (is (= (count suki/cell-specs)
           (count (mapcat :effects (vals plans)))))))
