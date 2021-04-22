(ns cattail.interop
  (:require [cattail.log :as log]
            [cattail.utils :as u]
            [clojure.string :as str])
  (:import (android.app Application Activity)
           (clojure.lang IFn RT)
           (android.content Context)
           (android.util Log)))

(gen-class
 :name "cattail.interop.Bridge"
 :methods [^{:static true} [app [] android.app.Application]
           ^{:static true} [activity [] android.app.Activity]
           ^{:static true} [load [android.app.Activity] Object]
           ^{:static true} [invoke [String String] Object]           
           ^{:static true} [getIpAddr [] String]]
 ;; :exposes-methods {onCreate superOnCreate}
 ;;:extends android.app.Application
 )


(defonce app (atom nil))
(defonce activity (atom nil))

(defn invoke-fn [ns-name fn-name & opts]
  (let [loader (RT/var "clojure.core" "load")]
    (log/d "load clojure loader")    
    (.invoke loader (str "/" (-> ns-name
                                 (str/replace #"\." "/")
                                 (str/replace #"-" "_"))))
    (let [initiator (RT/var ns-name fn-name)]
      (try
        (log/d "invoke" (str ns-name "/" fn-name) opts)              
        (apply initiator opts)
        ;;(.invoke initiator)
        (catch Exception e (log/e (.getMessage e)))))))

(defn -init []
  [[] (ref {})])

(defn -app []
  @app)

(defn -activity []
  @activity)



(defmacro get-service
  "Gets a system service for the given type. Type is a keyword that names the
  service. Examples include :alarm for the alarm service and
  :layout-inflater for the layout inflater service."
  {:pre [(keyword? type)]}
  ([type]
   `(get-service (-app) ~type))
  ([context type]
   `(.getSystemService
     ^Context ~context
     ~(symbol (str (.getName Context) "/"
                   (u/keyword->static-field (name type)) "_SERVICE")))))


(defn -getIpAddr []
  (loop [x (bit-and 0xffffffff
                    (+ (inc 0xffffffff) (.-ipAddress (.getDhcpInfo (get-service :wifi)))))
         addr []]
    (if (= 0 x)
      (str/join "." addr)
      (recur (bit-shift-right x 8) (conj addr (bit-and 0xff x)))) ))


(defn -load [actvty]
  (log/i "cattail.load")
  (reset! app (.getApplication actvty))
  (reset! activity actvty)
  (try
    (let [art (Class/forName "clojure.lang.DalvikDynamicClassLoader")
          set-context (.getMethod art "setContext" (into-array java.lang.Class [android.content.Context]))]
      (log/d "invoke setContext")      
      (.invoke set-context nil (into-array Object [@app]))
      ;; (future (invoke-fn "cattail.tools.nrepl" "start-tty-repl" ))
      (future (invoke-fn "cattail.tools.nrepl" "start-repl" :cider)))
    (catch Exception e (log/e (.printStackTrace e)))))


(defn -invoke [ns-name fn-name & opts]
  (log/d "invoke" ns-name "/" fn-name)
  (apply invoke-fn ns-name fn-name opts))


