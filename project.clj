(def ks-version "1.1.0")
(def jetty-version "9.2.10.v20150310")


(defn deploy-info
  [url]
  {:url url
   :username :env/nexus_jenkins_username
   :password :env/nexus_jenkins_password
   :sign-releases false})

(defproject puppetlabs/cthun-client "0.0.6-SNAPSHOT"
  :description "client library for cthun protocol"
  :url "https://github.com/puppetlabs/clj-cthun-client"
  :license {:name ""
            :url ""}

  :pedantic? :abort

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [prismatic/schema "0.4.3"]

                 ;; Transitive dependency for puppetlabs/ssl-utils and
                 ;; puppetlabs/cthun-message
                 [clj-time "0.9.0"]

                 [puppetlabs/ssl-utils "0.8.1"]
                 [puppetlabs/cthun-message "0.3.1"]
                 [puppetlabs/pcp-common "0.4.0"]

                 ;; Transitive dependencies on jetty for stylefuits/gniazdo
                 ;; to use the stable jetty release (gniazdo specifies 9.3.0M1)
                 [org.eclipse.jetty.websocket/websocket-client ~jetty-version]

                 [stylefruits/gniazdo "0.4.0" :exclusions [org.eclipse.jetty.websocket/websocket-client]]

                 ;; try+/throw+
                 [slingshot "0.12.2"]]

  :plugins [[lein-release "1.0.5" :exclusions [org.clojure/clojure]]]

  :repositories [["releases" "http://nexus.delivery.puppetlabs.net/content/repositories/releases/"]
                 ["snapshots"  "http://nexus.delivery.puppetlabs.net/content/repositories/snapshots/"]]

  :deploy-repositories [["releases" ~(deploy-info "http://nexus.delivery.puppetlabs.net/content/repositories/releases/")]
                        ["snapshots" ~(deploy-info "http://nexus.delivery.puppetlabs.net/content/repositories/snapshots/")]]

  :lein-release {:scm :git, :deploy-via :lein-deploy}

  :test-paths ["test" "test-resources"]

  :profiles {:dev {:dependencies [[puppetlabs/cthun "0.2.0-SNAPSHOT"]
                                  [puppetlabs/trapperkeeper "1.1.1" :classifier "test" :scope "test"]
                                  [puppetlabs/kitchensink ~ks-version :classifier "test" :scope "test"]]}})
