(ns ol.dev.portal
  (:require
   [clojure.string :as str]

   [ol.dev.portal-helpers :as portal-repl]))

(try
  (portal-repl/add-telemere-tap-handler!)
  (catch Throwable _))

(try
  (portal-repl/add-tufte-tap-handler! {})
  (catch Throwable _))

;; We have two portal taps: one for noisy logging, and one for everything else (repl evals, etc)

(defonce ^{:doc "Regular tap>'d values"}
  my-taps (atom (with-meta (list)
                  {:portal.viewer/default
                   :portal.viewer/inspector})))

(defonce ^{:doc "Logging and middleware tap>'d values"}
  noisy-taps (atom (with-meta (list)
                     {:portal.viewer/default
                      :portal.viewer/inspector})))

(def transforms portal-repl/recommended-transforms)
(defn tap-routing
  "a two-arity function that takes the original value and the transformed value, it should return the tap list by default a single tap list is used for all values."
  [v v']
  (let [m (try (meta v) (catch Exception _))
        m' (try (meta v') (catch Exception _))]
    (if (or (:portal.nrepl/eval m)
            (:portal.nrepl/eval m')
            (:dev.repl/logging m)
            (:dev.repl/logging m'))
      (when-not (str/starts-with? (str (:code v)) "(tap>")
        noisy-taps)
      my-taps)))

(defonce ^{:doc "Fixed wrapper around submit*."}
  submit
  (portal-repl/make-submit :transforms #'transforms :tap-routing #'tap-routing))

(defonce ^{:doc "Atom for current regular value."}
  portal
  ((requiring-resolve 'portal.api/open)
   {:portal.colors/theme          :portal.colors/zerodark
    :portal.launcher/window-title (str "[portal] " (System/getProperty "user.dir"))
    :value                        my-taps}))

(defonce ^{:doc "Atom for noisy values"}
  portal-noisy
  ((requiring-resolve 'portal.api/open)
   {:portal.colors/theme          :portal.colors/gruvbox
    :portal.launcher/window-title "[portal] logging"
    :value                        noisy-taps}))

(add-tap submit)

(tap> :wtf1)
(tap> :wtf2)
(tap> :wtf3)
(tap> :wtf4)

(tap>
 (with-meta {:wtf :dude}
   {:dev.repl/logging true}))
