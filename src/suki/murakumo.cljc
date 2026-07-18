(ns suki.murakumo
  "Pure cljc actor boundary generated from manifest migration scaffold."
  (:require [clojure.string :as str]))

(def actor-did
  "did:web:etzhayyim.com:suki")

(def common-gates
  [:council-charter-attestation
   :no-platform-held-key-baseline
   :no-probing-baseline
   :murakumo-only-inference-baseline
   :did-primary-baseline
   :append-only-gate-baseline
   :kotoba-only-substrate-baseline])

(defn collection
  [name]
  (str "com.etzhayyim.suki." name))

(def cell-specs {
  :suki_chassis_fabrication {:legacy-cell "suki-chassis-fabrication"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "suki_chassis_fabrication")]
     :required-gates common-gates
     :trigger "manifest cell suki_chassis_fabrication"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
  :suki_powertrain_assembly {:legacy-cell "suki-powertrain-assembly"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "suki_powertrain_assembly")]
     :required-gates common-gates
     :trigger "manifest cell suki_powertrain_assembly"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
  :suki_cab_assembly {:legacy-cell "suki-cab-assembly"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "suki_cab_assembly")]
     :required-gates common-gates
     :trigger "manifest cell suki_cab_assembly"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
  :suki_hitch_pto_assembly {:legacy-cell "suki-hitch-pto-assembly"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "suki_hitch_pto_assembly")]
     :required-gates common-gates
     :trigger "manifest cell suki_hitch_pto_assembly"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
  :suki_paint_finishing {:legacy-cell "suki-paint-finishing"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "suki_paint_finishing")]
     :required-gates common-gates
     :trigger "manifest cell suki_paint_finishing"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
  :suki_electrical_ecu_load {:legacy-cell "suki-electrical-ecu-load"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "suki_electrical_ecu_load")]
     :required-gates common-gates
     :trigger "manifest cell suki_electrical_ecu_load"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
  :suki_quality_field_test {:legacy-cell "suki-quality-field-test"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "suki_quality_field_test")]
     :required-gates common-gates
     :trigger "manifest cell suki_quality_field_test"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
  :suki_emissions_audit {:legacy-cell "suki-emissions-audit"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "suki_emissions_audit")]
     :required-gates common-gates
     :trigger "manifest cell suki_emissions_audit"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
  :suki_vehicle_attestation_binder {:legacy-cell "suki-vehicle-attestation-binder"
     :phase :event
     :murakumo-node "reuben"
     :collections [(collection "suki_vehicle_attestation_binder")]
     :required-gates common-gates
     :trigger "manifest cell suki_vehicle_attestation_binder"
     :ceiling "Manifest-driven migration scaffold; explicit execution stays in runtime methods"}
})

(defn safe-rkey
  [s]
  (let [clean (-> (str s)
                  (str/replace #"^did:web:" "")
                  (str/replace #"[^A-Za-z0-9._~-]" "-"))]
    (if (str/blank? clean) "unknown" clean)))

(defn gate-value
  [attestations gate]
  (or (get attestations gate)
      (get attestations (name gate))
      (when (set? attestations) (attestations gate))
      (when (set? attestations) (attestations (name gate)))))

(defn missing-gates
  [spec attestations]
  (->> (:required-gates spec)
       (remove #(boolean (gate-value attestations %)))
       vec))

(defn put-record-effect
  [collection rkey record]
  {:op :mst/put-record
   :actor actor-did
   :collection collection
   :rkey rkey
   :record record})

(defn records-for
  [spec {:keys [records record computed-at request-id]
         :as input}]
  (let [input-records (cond
                        (map? records) records
                        (some? record) {0 record}
                        :else {})
        base {:actorDid actor-did
              :computedAt computed-at
              :legacyCell (:legacy-cell spec)
              :phase (:phase spec)
              :requestId request-id
              :actorBoundary "cljc-migration-scaffold"
              :scaffold true
              :constitutionalStatus "attested-plan"}]
    (map-indexed
     (fn [idx coll]
       (let [record* (merge {:$type coll}
                            base
                            (or (get input-records coll)
                                (get input-records idx)
                                {}))
             rkey (safe-rkey (or (:rkey record*)
                                 (get record* "rkey")
                                 (:tid record*)
                                 request-id
                                 (str (:legacy-cell spec) "-" idx)))]
         {:collection coll
          :record record*
          :rkey rkey}))
     (:collections spec))))

(defn cell-plan
  [cell-key {:keys [attestations] :as input}]
  (let [spec (get cell-specs cell-key)]
    (when-not spec
      (throw (ex-info "unknown cell" {:cell cell-key})))
    (let [missing (missing-gates spec attestations)]
      (merge
       {:cell cell-key
        :legacy-cell (:legacy-cell spec)
        :actor actor-did
        :phase (:phase spec)
        :murakumo-node (:murakumo-node spec)
        :trigger (:trigger spec)
        :ceiling (:ceiling spec)
        :required-gates (:required-gates spec)
        :missing-gates missing}
       (if (seq missing)
         {:status :blocked
          :effects []}
         (let [planned-records (records-for spec input)]
           {:status :ready
            :records (vec planned-records)
            :effects (mapv (fn [{:keys [collection record rkey]}]
                             (put-record-effect collection rkey record))
                           planned-records)}))))))

(defn all-cell-plans
  [input]
  (into {}
        (map (fn [cell-key] [cell-key (cell-plan cell-key input)]))
        (keys cell-specs)))
