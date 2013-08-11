(ns irc-proxy.irc-test
  (:require [clojure.test :refer :all]
            [irc-proxy.irc :refer :all]))

(deftest parse-test
  (testing "simple command"
    (is (= {:prefix nil :command "QUIT" :params []}
           (parse "QUIT  "))))

  (testing "prefixed command"
    (is (= {:prefix "bob" :command "QUIT" :params []}
           (parse ":bob QUIT"))))

  (testing "commands with single arguments"
    (is (= {:prefix "bob" :command "QUIT" :params ["gotta run"]}
           (parse ":bob QUIT :gotta run")))
    (is (= {:prefix nil :command "JOIN" :params ["#foo"]}
           (parse "JOIN #foo"))))

  (testing "commands with multiple arguments"
    (is (= {:prefix nil :command "USER" :params ["bob" "0" "*" "Bob Loblaw"]}
           (parse "USER bob 0 * :Bob Loblaw")))
    (is (= {:prefix "bob" :command "JOIN" :params ["#foo" "#bar"]}
           (parse ":bob JOIN #foo #bar")))
    (is (= {:prefix nil :command "PRIVMSG" :params ["#foo" ""]}
           (parse "PRIVMSG #foo :")))))
