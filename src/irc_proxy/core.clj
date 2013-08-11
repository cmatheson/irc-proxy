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
           clients #{}]

      (let [server-chan (:chan server)
            client-chans (set (map :chan clients))
            [msg c] (alts!! (into [server-chan add-chan] client-chans))]
        (cond
          (= c server-chan)
          (do
            (doseq [{:keys [out]} clients]
              (.println out msg))
            (println msg)
            (recur add-chan server clients))

          (client-chans c)
          (if msg
            (do
              (println (str "> " msg))
              (.println (:out server) msg)
              (recur add-chan server clients))
            ; client disconnect
            (do
              (println (format "%4d clients" (dec (count clients))))
              (recur add-chan
                     server
                     ; FIXME: make this efficient
                     (disj clients (first (filter
                                            #(= c (:chan %))
                                            clients))))))

          (= c add-chan)
          (do
            (println (format "%4d clients" (inc (count clients))))
            (recur add-chan server (conj clients (irc/client msg)))))))))
