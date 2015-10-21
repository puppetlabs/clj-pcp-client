(defproject puppetlabs/pcp-client "0.1.1-SNAPSHOT"
  :description "client library for PCP"
  :url "https://github.com/puppetlabs/clj-pcp-client"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :pedantic? :abort

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [prismatic/schema "0.4.3"]

                 ;; Transitive dependency for:
                 ;;  - puppetlabs/ssl-utils
                 ;;  - puppetlabs/pcp-common
                 ;;  - puppetlabs/trapperkeeper-authorization (via puppetlabs/pcp-broker)
                 [clj-time "0.10.0"]

                 [puppetlabs/ssl-utils "0.8.1"]
                 [puppetlabs/pcp-common "0.5.0"]

                 ;; Transitive dependencies on jetty for stylefuits/gniazdo
                 ;; to use a stable jetty release (gniazdo specifies 9.3.0M1)
                 [org.eclipse.jetty.websocket/websocket-client "9.2.10.v20150310"]

                 [stylefruits/gniazdo "0.4.0" :exclusions [org.eclipse.jetty.websocket/websocket-client]]

                 ;; try+/throw+
                 [slingshot "0.12.2"]]

  :plugins [[lein-release "1.0.5" :exclusions [org.clojure/clojure]]]

  :lein-release {:scm :git
                 :deploy-via :lein-deploy}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]

  :test-paths ["test" "test-resources"]

  :profiles {:dev {:dependencies [[puppetlabs/pcp-broker "0.5.0"]
                                  [puppetlabs/trapperkeeper "1.1.1" :classifier "test" :scope "test"]
                                  [puppetlabs/kitchensink "1.1.0" :classifier "test" :scope "test"]]}
             :cljfmt {:plugins [[lein-cljfmt "0.3.0"]
                                [lein-parent "0.2.1"]]
                      :parent-project {:path "../pl-clojure-style/project.clj"
                                       :inherit [:cljfmt]}}}

  :aliases {"cljfmt" ["with-profile" "+cljfmt" "cljfmt"]})
