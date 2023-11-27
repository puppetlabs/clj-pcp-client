(def jetty-version "10.0.18")

(defproject puppetlabs/pcp-client "2.0.0"
  :description "client library for PCP"
  :url "https://github.com/puppetlabs/clj-pcp-client"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :pedantic? :abort

  :min-lein-version "2.7.1"

  :parent-project {:coords [puppetlabs/clj-parent "7.2.7"]
                   :inherit [:managed-dependencies]}

  :dependencies [[puppetlabs/pcp-common "1.3.5" :exclusions [org.tukaani/xz]]
                 ;; We only care about org.eclipse.jetty.websocket/websocket-client
                 [com.puppetlabs/trapperkeeper-webserver-jetty10 "1.0.4"]
                 [org.eclipse.jetty.websocket/websocket-jetty-client ~jetty-version]
                 [org.eclipse.jetty.websocket/websocket-jetty-api ~jetty-version]

                 [org.clojure/clojure]
                 [org.clojure/tools.logging]
                 [puppetlabs/ssl-utils]
                 [puppetlabs/kitchensink]
                 [prismatic/schema]
                 [puppetlabs/trapperkeeper-status]
                 [puppetlabs/trapperkeeper-scheduler]
                 [puppetlabs/trapperkeeper-metrics "2.0.0"]
                 [slingshot]
                 [puppetlabs/i18n]]

  :plugins [[lein-release "1.0.5" :exclusions [org.clojure/clojure]]
            [lein-parent "0.3.4"]
            [puppetlabs/i18n "0.8.0"]]

  :lein-release {:scm :git
                 :deploy-via :lein-deploy}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]

  :test-paths ["test" "test-resources"]

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[puppetlabs/pcp-broker "2.0.0"]
                                  [org.clojure/tools.nrepl]
                                  [org.bouncycastle/bcpkix-jdk18on]
                                  [puppetlabs/trapperkeeper]
                                  [puppetlabs/trapperkeeper :classifier "test" :scope "test"]
                                  [puppetlabs/kitchensink :classifier "test" :scope "test"]]}
             :test-base [:dev
                         {:source-paths ["test-resources"]
                          :test-paths ^:replace ["test"]}]
             :test-schema-validation [:test-base
                                      {:injections [(do
                                                      (require 'schema.core)
                                                      (schema.core/set-fn-validation! true))]}]}

  :aliases {"test-all" ["with-profile" "test-base:test-schema-validation" "test"]})
