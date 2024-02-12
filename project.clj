(defproject puppetlabs/pcp-client "2.1.0-SNAPSHOT"
  :description "client library for PCP"
  :url "https://github.com/puppetlabs/clj-pcp-client"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :pedantic? :abort

  :min-lein-version "2.7.1"

  :parent-project {:coords [puppetlabs/clj-parent "7.3.7"]
                   :inherit [:managed-dependencies]}

  :dependencies [[puppetlabs/pcp-common "1.3.5" :exclusions [org.tukaani/xz]]
                 ;; the client, api, and utility namespaces are used from jetty 10, use the tk-ws-j10 project to manage the
                 ;; versions centrally.
                 [com.puppetlabs/trapperkeeper-webserver-jetty10]

                 [org.clojure/clojure]
                 [org.clojure/tools.logging]
                 [puppetlabs/ssl-utils]
                 [puppetlabs/kitchensink]
                 [prismatic/schema]
                 [puppetlabs/trapperkeeper-status]
                 [puppetlabs/trapperkeeper-scheduler]
                 [puppetlabs/trapperkeeper-metrics]
                 [slingshot]
                 [puppetlabs/i18n]]

  :plugins [[lein-release "1.0.5" :exclusions [org.clojure/clojure]]
            [lein-parent "0.3.9"]
            [puppetlabs/i18n "0.9.2"]]

  :lein-release {:scm :git
                 :deploy-via :lein-deploy}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]

  :test-paths ["test" "test-resources"]

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[puppetlabs/pcp-broker "2.0.2"]
                                  [org.clojure/tools.nrepl]
                                  [org.bouncycastle/bcpkix-jdk18on]
                                  [puppetlabs/trapperkeeper]
                                  [puppetlabs/trapperkeeper :classifier "test" :scope "test"]
                                  [puppetlabs/kitchensink :classifier "test" :scope "test"]]}
             :schema-validation {:injections [(do
                                                (require 'schema.core)
                                                (schema.core/set-fn-validation! true))]}
             :test-base {:source-paths ["test-resources"]
                         :test-paths ^:replace ["test"]}
             :test-schema-validation [:dev :test-base :schema-validation]}


  :aliases {"test-all" ["with-profile" "test-base:test-schema-validation" "test"]})
