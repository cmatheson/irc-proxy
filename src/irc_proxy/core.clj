(ns irc-proxy.core
  (:require [clojure.core.async :as async :refer :all]
            [clojure.java.io :as io])
  (:import java.net.ServerSocket))

(defn client-chan
  "returns a new channel to deal with client events"
  [client-socket]
  (let [in (io/reader client-socket)
        c (chan)]
    (thread
      (loop []
        (if-let [line (.readLine in)]
          (do
            (>!! c line)
            (recur))
          (>!! c :disconnected))))
    c))

(defn start-server
  "starts a server-loop, sends new client sockets to the given channel"
  [c]
  (let [srv (ServerSocket. 6667)]
    (while true
      (let [client (.accept srv)]
        (go (>! c client))))
    c))

(defn -main []
  (let [server-chan (chan)
        quit (chan)]

    (thread
      (loop [chans [server-chan]]
        (println (format "%4d clients" (- (count chans) 1)))
        (let [[v channel] (alts!! chans)]
          (cond
            (= channel server-chan)
            (recur (conj chans (client-chan v)))

            (= v :disconnected)
            (recur (remove #(= channel %) chans))

            :else (do
                    (println v)
                    (recur chans))))))

    (start-server server-chan)

    (<!! quit)))
