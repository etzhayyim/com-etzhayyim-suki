(require '[clojure.test :as t])

(doseq [ns-sym '[suki.methods.test-charter-gates
                  suki.repository-contract-test]]
  (require ns-sym))

(let [result (apply t/run-tests
                    '[suki.methods.test-charter-gates
                      suki.repository-contract-test])]
  (System/exit (if (zero? (+ (:fail result) (:error result))) 0 1)))
