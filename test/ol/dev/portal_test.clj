(ns ol.dev.portal-test
  (:require [clojure.test :refer [deftest is testing]]
            [ol.dev.portal :as portal]))

(defn- with-portal-state [state f]
  (let [original-state @portal/portal-state]
    (try
      (reset! portal/portal-state state)
      (f)
      (finally
        (reset! portal/portal-state original-state)))))

(deftest tap-accessors-return-portal-tap-atoms
  (let [my-taps    (atom [:my])
        noisy-taps (atom [:noisy])]
    (with-portal-state {:taps {:my-taps    my-taps
                               :noisy-taps noisy-taps}}
      (fn []
        (is (identical? my-taps (portal/my-taps)))
        (is (identical? noisy-taps (portal/noisy-taps)))))))

(deftest tap-accessors-require-open-portals
  (with-portal-state nil
    (fn []
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Portal taps are not initialized"
                            (portal/my-taps))))))

(deftest log-query-helpers-match-dev-namespace-semantics
  (let [entries [[:old {:value 1}]
                 [:fetch {:value 2}]
                 [:fetch {:value 3}]
                 {:value 4}]]
    (with-portal-state {:taps {:my-taps (atom entries)}}
      (fn []
        (testing "logs returns all, count-limited, and label-filtered entries"
          (is (= entries (portal/logs)))
          (is (= [[:fetch {:value 3}] {:value 4}]
                 (portal/logs 2)))
          (is (= [[:fetch {:value 2}] [:fetch {:value 3}]]
                 (portal/logs :fetch)))
          (is (= [[:fetch {:value 3}]]
                 (portal/logs :fetch 1))))
        (testing "log-values extracts :value from the selected entries"
          (is (= [nil nil nil 4]
                 (portal/log-values)))
          (is (= [nil nil]
                 (portal/log-values :fetch)))
          (is (= [nil]
                 (portal/log-values :fetch 1))))
        (testing "last-log mirrors the dev namespace helper"
          (is (= {:value 4} (portal/last-log)))
          (is (= 4 (portal/last-log :v))))))))

(deftest clear-logs-resets-normal-taps
  (let [taps (atom [[:fetch {:value 1}]])]
    (with-portal-state {:taps {:my-taps taps}}
      (fn []
        (is (= [] (portal/clear-logs!)))
        (is (= [] @taps))))))
