(ns ^:clj-reload/no-reload ol.dev.portal
  (:require
   [clojure.string :as str]

   [ol.dev.portal-helpers :as portal-repl]))

(defn tap-routing
  "a two-arity function that takes the original value and the transformed value, it should return the tap list by default a single tap list is used for all values."
  [noisy-taps my-taps v v']
  (let [m (try (meta v) (catch Exception _))
        m' (try (meta v') (catch Exception _))]
    (if (or (:portal.nrepl/eval m)
            (:portal.nrepl/eval m')
            (:dev.repl/logging m)
            (:dev.repl/logging m'))
      (when-not (str/starts-with? (str (:code v)) "(tap>")
        noisy-taps)
      my-taps)))

(defonce ^{:doc "My Portal taplist state"}
  portal-state
  (atom nil))

(defn- tap-atom [tap-key]
  (or (get-in @portal-state [:taps tap-key])
      (throw (ex-info "Portal taps are not initialized. Call `open-portals` first."
                      {:tap-key tap-key}))))

(defn my-taps
  "Returns the atom containing regular `tap>` values."
  []
  (tap-atom :my-taps))

(defn noisy-taps
  "Returns the atom containing logging, middleware, and nREPL `tap>` values."
  []
  (tap-atom :noisy-taps))

(defn logs
  "Queries the regular tap log.

  Usage:

  ```clojure
  (logs)
  (logs 5)
  (logs :label)
  (logs :label 3)
  ```"
  ([] @(my-taps))
  ([n-or-label]
   (if (number? n-or-label)
     (vec (take-last n-or-label @(my-taps)))
     (vec (filter #(= n-or-label (first %)) @(my-taps)))))
  ([label n]
   (vec (take-last n (filter #(= label (first %)) @(my-taps))))))

(defn log-values
  "Returns only the `:value` entries from [[logs]]."
  ([] (mapv :value @(my-taps)))
  ([n-or-label] (mapv :value (logs n-or-label)))
  ([label n] (mapv :value (logs label n))))

(defn clear-logs!
  "Clears the regular tap log."
  []
  (reset! (my-taps) []))

(defn last-log
  "Returns the most recent regular tap entry, or its `:value` with any argument."
  ([] (last @(my-taps)))
  ([_] (:value (last @(my-taps)))))

(defn close-portals [m]
  (->> m vals (filter some?) (map (requiring-resolve 'portal.api/close)) doall))

(defn open-portals
  "Open the portals and set up the tap handlers.

  Will close any existing portals before opening new ones."
  []
  (let [portal-open (requiring-resolve 'portal.api/open)
        my-taps (atom (with-meta (list)
                        {:portal.viewer/default
                         :portal.viewer/inspector}))
        noisy-taps (atom (with-meta (list)
                           {:portal.viewer/default
                            :portal.viewer/inspector}))
        submit (portal-repl/make-submit :transforms portal-repl/recommended-transforms
                                        :tap-routing (partial tap-routing noisy-taps my-taps))]
    (close-portals (:portals @portal-state))
    (reset! portal-state
            {:taps {:my-taps my-taps
                    :noisy-taps noisy-taps}
             :portals {:normal (portal-open
                                {:portal.colors/theme          :portal.colors/zerodark
                                 :portal.launcher/window-title (str "[portal] " (System/getProperty "user.dir"))
                                 :value                        my-taps})
                       :noisy (portal-open
                               {:portal.colors/theme          :portal.colors/gruvbox
                                :portal.launcher/window-title "[portal] logging"
                                :value                        noisy-taps})}})

    (add-tap submit))

  (try
    (portal-repl/add-telemere-tap-handler!)
    (catch Throwable _))

  (try
    (portal-repl/add-tufte-tap-handler! {})
    (catch Throwable _))
  :done)

(comment

  (open-portals)

  (close-portals (:portals @portal-state))

  ;; goes to my-taps
  (tap> :test)

  ;; goes to the noisy-taps portal
  (tap> (with-meta
          [:noisy-logging]
          {:dev.repl/logging true}))
  ;;
  )
