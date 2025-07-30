;; Copyright © 2025 Casey Link <unnamedrambler@gmail.com>
;; SPDX-License-Identifier: EUPL-1.2
;; Inspired by sean corfield's https://github.com/seancorfield/dot-clojure/blob/develop/src/org/corfield/dev/repl.clj
(ns ol.dev.repl
  "Invoke ol.dev.repl/-main to start a REPL based on
  what tooling you have available on your classpath."
  (:require [clojure.repl :refer [demunge]]
            [clojure.string :as str]))

(when-not (resolve 'requiring-resolve)
  (throw (ex-info ":dev/repl and repl.clj require at least Clojure 1.10"
                  *clojure-version*)))

(defn- tap>log [frame level throwable message]
  (let [class-name (symbol (demunge (.getClassName frame)))]
    ;; only called for enabled log levels:
    (tap>
     (with-meta
       {:form     '()
        :level    level
        :result   (or throwable message)
        :ns       (symbol (or (namespace class-name)
                                                                        ;; fully-qualified classname - strip class:
                              (str/replace (name class-name) #"\.[^\.]*$" "")))
        :file     (.getFileName frame)
        :line     (.getLineNumber frame)
        :column   0
        :time     (java.util.Date.)
        :runtime  :clj}
       {:dev.repl/logging true}))))

(defn- ctl-log*adapter [log-star]
  (let [log*-fn (deref log-star)]
    (fn [logger level throwable message]
      (try
        (let [^StackTraceElement frame (nth (.getStackTrace (Throwable. "")) 2)]
          (tap>log frame level throwable message))
        (catch Throwable _))
      (log*-fn logger level throwable message))))

(defn -main
  "If Jedi Time is on the classpath, require it (so that Java Time
  objects will support datafy/nav).

  If Datomic Dev Datafy is on the classpath, require it (so that
  Datomic objects will support datafy/nav).

  Start a Socket REPL server, if requested. The port is selected from:
  * SOCKET_REPL_PORT environment variable if present, else
  * socket-repl-port JVM property if present, else
  * .socket-repl-port file if present
  Writes the selected port back to .socket-repl-port for next time.

  Use a value of none to suppress the Socket Server startup.

  Then pick a REPL as follows:
  * if Figwheel Main is on the classpath then start that, else
  * if Rebel Readline is on the classpath then start that, else
  * start a plain ol' Clojure REPL."
  [& args]
  ;; jedi-time?
  (try
    (require 'jedi-time.core)
    (println "Java Time is Datafiable...")
    (catch Throwable _))

  ;; datomic/dev.datafy?
  (try
    ((requiring-resolve 'datomic.dev.datafy/datafy!))
    (println "Datomic Datafiers Enabled...")
    (catch Throwable _))
  ;; if Portal and clojure.tools.logging are both present,
  ;; cause all (successful) logging to also be tap>'d:
  (try
    ;; if we have Portal on the classpath...
    (require 'portal.console)
    ;; ...then install a tap> ahead of tools.logging:
    (let [log-star (requiring-resolve 'clojure.tools.logging/log*)]
      (alter-var-root
       log-star
       (constantly (ctl-log*adapter log-star))))
    (println "clojure.tools.logging will be tap>'d...")
    (catch Throwable _))

  ;; select and start a main REPL:
  (let [;; figure out what middleware we might want to supply to nREPL:
        middleware
        (into []
              (filter #(try (requiring-resolve (second %)) true (catch Throwable _)))
              [["Portal"   'portal.nrepl/wrap-portal]
               ["Notebook" 'portal.nrepl/wrap-notebook]
               ["CIDER"    'cider.nrepl/cider-middleware]
               ["refactor-nrepl" 'refactor-nrepl.middleware/wrap-refactor]])
        mw-args
        (when (seq middleware)
          ["--middleware" (str (mapv second middleware))])
        [repl-name repl-fn]
        (or
         (try ; nREPL?
           [(str "nREPL Server"
                 (when (seq middleware)
                   (str " with " (str/join ", " (map first middleware)))))
            (let [nrepl (requiring-resolve 'nrepl.cmdline/-main)]
              (fn []
                (apply nrepl (concat mw-args args))))]
           (catch Throwable _))
            ;; fallback to plain REPL:
         ["clojure.main" (resolve 'clojure.main/main)])]
    (println "Starting" repl-name "as the REPL...")
    (println (str/join " " (concat mw-args args)))
    (repl-fn)
    ;; ensure a smooth exit after the REPL is closed
    (System/exit 0)))
