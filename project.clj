(defproject puppetlabs/pcp-client "1.1.6-SNAPSHOT"
  :description "client library for PCP"
  :url "https://github.com/puppetlabs/clj-pcp-client"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :pedantic? :abort

  :min-lein-version "2.7.1"

  :parent-project {:coords [puppetlabs/clj-parent "0.6.1"]
                   :inherit [:managed-dependencies]}

  :dependencies [[puppetlabs/pcp-common "1.1.4"]

                 ;; Transitive dependencies on jetty for stylefuits/gniazdo
                 ;; to use a stable jetty release (gniazdo specifies 9.3.0M1)
                 [org.eclipse.jetty.websocket/websocket-client "9.2.10.v20150310"]
                 [stylefruits/gniazdo "0.4.0" :exclusions [org.eclipse.jetty.websocket/websocket-client]]

                 [org.clojure/clojure]
                 [org.clojure/tools.logging]
                 [puppetlabs/ssl-utils]
                 [puppetlabs/kitchensink]
                 [prismatic/schema]
                 [puppetlabs/trapperkeeper-status]
                 [puppetlabs/trapperkeeper-scheduler]
                 [slingshot]
                 [puppetlabs/i18n]]

  :plugins [[lein-release "1.0.5" :exclusions [org.clojure/clojure]]
            [lein-parent "0.3.1"]
            [puppetlabs/i18n "0.8.0"]]

  :lein-release {:scm :git
                 :deploy-via :lein-deploy}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]

  :test-paths ["test" "test-resources"]

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[puppetlabs/pcp-broker "1.2.0"]
                                  [puppetlabs/trapperkeeper]
                                  [puppetlabs/trapperkeeper :classifier "test" :scope "test"]
                                  [puppetlabs/kitchensink :classifier "test" :scope "test"]
                                  [puppetlabs/trapperkeeper-webserver-jetty9]]}
             :cljfmt {:plugins [[lein-cljfmt "0.3.0"]
                                [lein-parent "0.2.1"]]
                      :parent-project {:path "../pl-clojure-style/project.clj"
                                       :inherit [:cljfmt]}}
             :test-base [:dev
                         {:source-paths ["test-resources"]
                          :test-paths ^:replace ["test"]}]
             :test-schema-validation [:test-base
                                      {:injections [(do
                                                      (require 'schema.core)
                                                      (schema.core/set-fn-validation! true))]}]}

  :aliases {"cljfmt" ["with-profile" "+cljfmt" "cljfmt"]
            "test-all" ["with-profile" "test-base:test-schema-validation" "test"]})
