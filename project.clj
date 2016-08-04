(def tk-version "1.4.0")
(def ks-version "1.3.0")
(def i18n-version "0.4.1")

(defproject puppetlabs/pcp-client "0.3.3-SNAPSHOT"
  :description "client library for PCP"
  :url "https://github.com/puppetlabs/clj-pcp-client"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :pedantic? :abort

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]

                 ;; Transitive dependency for:
                 ;;  - puppetlabs/ssl-utils
                 ;;  - puppetlabs/pcp-common
                 ;;  - puppetlabs/trapperkeeper-authorization (via puppetlabs/pcp-broker)
                 [clj-time "0.10.0"]

                 [puppetlabs/ssl-utils "0.8.1"]
                 [puppetlabs/pcp-common "0.5.2" :exclusions [puppetlabs/kitchensink prismatic/schema]]
                 [puppetlabs/kitchensink "1.3.0"]
                 [prismatic/schema "1.0.4"]

                 ;; Transitive dependencies on jetty for stylefuits/gniazdo
                 ;; to use a stable jetty release (gniazdo specifies 9.3.0M1)
                 [org.eclipse.jetty.websocket/websocket-client "9.2.10.v20150310"]

                 [stylefruits/gniazdo "0.4.0" :exclusions [org.eclipse.jetty.websocket/websocket-client]]

                 ;; try+/throw+
                 [slingshot "0.12.2"]

                 [puppetlabs/i18n  ~i18n-version]]

  :plugins [[lein-release "1.0.5" :exclusions [org.clojure/clojure]]
            [puppetlabs/i18n  ~i18n-version]]

  :lein-release {:scm :git
                 :deploy-via :lein-deploy}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]

  :test-paths ["test" "test-resources"]

  :profiles {:dev {:dependencies [[puppetlabs/pcp-broker "0.7.1"]
                                  [puppetlabs/trapperkeeper ~tk-version]
                                  [puppetlabs/trapperkeeper ~tk-version :classifier "test" :scope "test"]
                                  [puppetlabs/kitchensink ~ks-version :classifier "test" :scope "test"]]}
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
