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
        out (PrintWriter. (.getOutputStream socket) true)
        nick "ircproxy"
        chatnet "freenode"]
    (thread
      (.println out (str "NICK " nick))
      (.println out "USER ircproxy 0 * :IRC Proxy")
      (doseq [l (line-seq in)]
        (>!! c l))
      (close! c)) ; TODO: reconnect instead
    {:chan c :out out :socket socket :nick nick :chatnet chatnet}))

(defn handle-server-in
  [msg server clients]
  (println msg)
  (let [{:keys [prefix command params]} (parse msg)
        out (server :out)]
    ; FIXME: this stuff should get logged but not passed on to
    ; clients
    (case command
      "PING"
      (do
        (.println out (str "PONG " (first params)))
        server)

      ("001" "002" "003" "004")
      (let [server (assoc server :prefix prefix)
            [_ & params] params ; first param might be the wrong nick for client
            reg-msg {:params params :command command}]
        (update-in server [:registration-messages]
                   #(conj (or % []) reg-msg)))

      "NICK"
      (assoc server :nick (first params))

      "433"
      (do
        (.println out "NICK ircproxy_") ; FIXME
        server)

      ; else
      server)))

(defn handle-client-in
  [msg client server]
  (let [{:keys [command params]} (parse msg)
        out (client :out)
        pass (fn []
               (.println (:out server) msg)
               client)]
    (println (str "> " msg))
    (case command
      "NICK"
      (if (:registered client)
        (pass)
        (assoc client :nick (first params)))

      "USER"
      (if (:registered client)
        (pass)
        (let [prefix (server :prefix)]
          (doseq [{:keys [command params]} (server :registration-messages)]
            (.println out (format ":%s %s %s %s"
                                  prefix
                                  command
                                  (client :nick)
                                  (string/join " " params))))
          (.println out (format ":%s NICK %s" (client :nick) (server :nick)))
          (-> client
              (dissoc :nick)
              (assoc :registered true))))

      "PING"
      (let [prefix (server :chatnet)
            target (first params)]
        (.println out (format ":%s PONG %s" prefix target))
        client)

      "QUIT"
      (do
        ; TODO: set away if all clients are disconnected
        (.close (client :socket))
        client)

      ; else
      (pass))))
