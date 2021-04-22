(ns cattail.tools.nrepl
  (:require [cattail.log :as log]
            [nrepl.transport :as t]
            [nrepl.misc :refer [response-for]]
            [nrepl.server :refer [start-server stop-server default-handler]]
            [cider.nrepl :refer [wrap-apropos wrap-complete wrap-info wrap-inspect
                                 wrap-macroexpand wrap-ns wrap-resource wrap-stacktrace
                                 wrap-trace wrap-undef wrap-test]])
  (:import [android.content Context]
           [android.util Log]
           [java.io FileNotFoundException]
           [java.util.concurrent.atomic AtomicLong]
           [java.util.concurrent ThreadFactory]
           (javax.servlet.http HttpServletRequest)
           (org.eclipse.jetty.server Server)
           (org.eclipse.jetty.servlet ServletContextHandler ServletHolder)
           (org.eclipse.jetty.server.handler ContextHandler ContextHandlerCollection)
           (org.eclipse.jetty.websocket WebSocket WebSocketServlet
                                        WebSocket$OnTextMessage
                                        WebSocket$Connection)))


(defonce nrepl-version {:version-string "0.8.3", :qualifier "",
                        :incremental "0", :minor "8", :major "3"})

;; Applications that contains nREPL has to include cider.nrepl.middleware.*

(def cider-middleware
  "A vector containing all CIDER middleware."
  ['cider.nrepl/wrap-apropos
   'cider.nrepl/wrap-complete
   'cider.nrepl/wrap-info
   ;; 'cider.nrepl/wrap-inspect ;; NOT work
   'cider.nrepl/wrap-macroexpand
   'cider.nrepl/wrap-ns
   'cider.nrepl/wrap-resource
   ;; 'cider.nrepl/wrap-stacktrace
   ;; 'cider.nrepl/wrap-test
   'cider.nrepl/wrap-trace
   ;; 'cider.nrepl/wrap-undef
   ])

(defn- patch-unsupported-dependencies
  "Some non-critical CIDER and nREPL dependencies cannot be used on Android
  as-is, so they have to be tranquilized."
  []
  (let [curr-ns (ns-name *ns*)]
    (ns dynapath.util)
    (defn add-classpath! [& _])
    (defn addable-classpath [& _])
    (in-ns curr-ns)))

(defn start-repl*
  "Starts a remote nREPL server. Creates a `user` namespace because nREPL
  expects it to be there while initializing. References nrepl's `start-server`
  function on demand because the project can be compiled without nrepl
  dependency."
  
  ([repl-args]
   (start-repl* [] repl-args))
  ([middleware repl-args]
   (log/d "start-repl")
   (binding [*ns* (create-ns 'user)]
     (refer-clojure)
     (patch-unsupported-dependencies)
     (require '[nrepl.server :refer :all])
     ;; Hack nREPL version to avoid CIDER complaining about it.
     (require 'nrepl.version)
     (alter-var-root (resolve 'nrepl.version/version)
                     (constantly nrepl-version))
     (log/d (str nrepl.version/version))
     (apply start-server
            (->> (assoc repl-args
                        :bind "0.0.0.0"
                        :handler (or (:handler repl-args)
                                     (apply (resolve 'default-handler)
                                              (map (fn [sym]
                                                     (require (symbol (namespace sym)))
                                                     (resolve sym))
                                                   middleware))))
                 (reduce concat))))))

(defonce nrepl (atom {}))

(defn start-repl [type]
  (condp = type
    :cider (try
             (require 'cider.nrepl.version)
             (swap! nrepl assoc :cider (start-repl* cider-middleware {:cider? true :port 9999}))
             (catch FileNotFoundException e (log/e (.getMessage e))))
    :edn (swap! nrepl assoc :edn (start-repl* {:transport-fn t/edn :port (or 8999)}))
    :tty (swap! nrepl assoc :tty (start-repl* {:transport-fn t/tty :greeting-fn t/tty-greeting :port 10101}))))


(defn stop-repl [type]
  (when-let [some-repl (@nrepl type)] 
    (stop-server some-repl)
    (swap! nrepl dissoc type)))


;; WebSocket proxy of nrepl-edn

(defn send-message! [conn msg]
  (.sendMessage conn msg))

(defn create-websocket []
  (let [ws (atom nil)
        sock (java.net.Socket. "localhost" 8999)
        is (.getInputStream sock)
        os (.getOutputStream sock)]
    (proxy [WebSocket$OnTextMessage] []
      (onOpen [conn]
        (reset! ws conn)
        (future (try
                  (let [buffer (byte-array 10240)]
                    (loop [cnt 0
                           buf buffer]
                      (log/d "Thread loop: " cnt)
                      (let [size (.read is buf)]
                        (log/d "read:" size)
                        (send-message! @ws (subs (String. buf) 0 size)))
                      (recur (inc cnt) buf)))
                  (catch Exception e (log/i "Read thread exit"))))
        (log/i "Opened"))
      (onClose [close-code message]
        (.disconnect @ws)
        (.close sock)
        (log/i "Closed"))
      (onMessage [^String message]
        (log/i message)
        (try
          (doto os
            (.write (.getBytes message))
            (.flush))
          (log/d "write")
          (catch Exception e (log/e (.getMessage e))))))))

(defn create-websocket-servlet []
  (proxy [WebSocketServlet] []
    (doGet [req res]
      (log/d req))
    (doWebSocketConnect [^HttpServletRequest req ^String protocol]
      (create-websocket))))

(defn start-ws-repl-proxy [{:keys [port] :or {port 5999}}]
  (let [context (doto (ServletContextHandler. ServletContextHandler/SESSIONS)
                  (.addServlet (ServletHolder. (create-websocket-servlet)) "/repl"))
        srv (doto (Server. port)
              (.setHandler context))]
    (.start srv)
    (swap! nrepl assoc :ws-proxy srv)))

(defn stop-ws-repl-proxy []
  (when-let [prxy (:ws-proxy @nrepl)]
    (.stop prxy)
    (swap! nrepl dissoc :ws-proxy)))
