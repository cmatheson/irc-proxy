(ns irc-proxy.core
  (:require [clojure.core.async :as async :refer :all]
            [irc-proxy.irc :as irc])
  (:import java.net.ServerSocket))

(defn server-chan
  "starts a server-loop, returns a channel that receives new client sockets"
  []
  (let [c (chan)
        srv (ServerSocket. 6667)]
    (thread
      (while true
        (let [client (.accept srv)]
          (go (>! c client)))))
    c))

(defn -main []
  (let [client-connection-chan (chan)]

    (loop [add-chan (server-chan)
           server (irc/server "irc.freenode.net" 6667)
           clients {}]

      (let [server-chan (:chan server)
            client-chans (keys clients)
            [msg c] (alts!! (into [server-chan add-chan] client-chans))]
        (cond
          (= c server-chan)
          (do
            (doseq [[_ {:keys [out]}] clients]
              (.println out msg))
            (recur add-chan (irc/handle-server-in msg server clients) clients))

          (clients c)
          (let [client (clients c)]
            (if msg
              (let [client (irc/handle-client-in msg client server)]
                (recur add-chan server (assoc clients c client)))
              ; client disconnect
              (do
                (println (format "%4d clients" (dec (count clients))))
                (recur add-chan
                       server
                       (dissoc clients c)))))

          (= c add-chan)
          (let [client (irc/client msg)]
            (println (format "%4d clients" (inc (count clients))))
            (recur add-chan server (assoc clients (client :chan) client))))))))
