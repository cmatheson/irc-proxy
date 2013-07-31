(defproject irc-proxy "0.1.0-SNAPSHOT"
  :description "IRC Proxy (like bip, but better)"
  :url "http://github.com/cmatheson/irc-proxy"
  :license {:name "GNU General Public License"
            :url "http://www.gnu.org/licenses/gpl.html"}
  :main irc-proxy.core
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.0-SNAPSHOT"]]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"})
