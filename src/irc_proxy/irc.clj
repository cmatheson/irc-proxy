(ns irc-proxy.irc
  (:require [clojure.core.async :as async :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import (java.net Socket)
           (java.io PrintWriter)))

; FIXME: there has to be a better way to do this
(defn parse-params
  "breaks irc params string into a list of params"
  [params]
  (let [[front trailing] (string/split params #" :" 2)
        prune-empty #(remove empty? %)
        params (-> front
                   (string/split #" +")
                   prune-empty
                   vec)]
    (if trailing
      (conj params trailing)
      params)))

(defn parse
  "parses an irc message into prefix, command, and params list"
  [msg]
  (let [[_ prefix cmd params] (re-matches #"^(?::([^\s]+) )?([a-zA-Z]+|\d{1,3})(.*)"
                                          msg)]
    {:prefix prefix :command cmd :params (parse-params params)}))

(defn client
  "same as server but for clients"
  [socket]
  (let [c (chan)
        in (io/reader socket)
        out (PrintWriter. (.getOutputStream socket) true)]
    (thread
      (doseq [l (line-seq in)]
        (>!! c l))
      (close! c))
    {:chan c :out out :socket socket}))

(defn server
  "connects to the given server and returns a map of socket/out/chan
   the channel receives any input read from the socket"
  [host port]
  (let [c (chan)
        socket (Socket. host port)
        in (io/reader socket)
        out (PrintWriter. (.getOutputStream socket) true)]
    (thread
      (.println out "NICK ircproxy")
      (.println out "USER ircproxy 0 * :IRC Proxy")
      (doseq [l (line-seq in)]
        (let [{:keys [command prefix params]} (parse l)]
          ; FIXME: this stuff should get logged but not passed on to
          ; clients
          (case command
            "PING" (.println out (str "PONG " (first params)))
            "433"  (.println out "NICK ircproxy_") ; FIXME
            (>!! c l))))
      (close! c)) ; TODO: reconnect instead
    {:chan c :out out :socket socket}))
