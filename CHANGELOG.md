## 1.1.5

This is a maintenance release.

* Add a retry? parameter to the client to allow connection without retries.

## 1.1.4

This is a maintenance release.

* Bumps clj-parent to 0.6.1 for i18n 0.8.0, and clj-pcp-common to 1.1.4.

## 1.1.3

This is a maintenance release.

* [PCP-731](https://tickets.puppetlabs.com/browse/PCP-731) Bump clj-parent to
0.4.3 to pickup i18n 0.7.1.

## 1.1.2

This is a maintenance release.

* [PCP-731](https://tickets.puppetlabs.com/browse/PCP-731) Bump clj-parent to
0.4.2 to pickup i18n 0.7.0 for a change in pot file name.

## 1.1.1

This is a maintenance release.

* [PCP-701](https://tickets.puppetlabs.com/browse/PCP-701) Add an on-connect
callback that's called whenever the client connects.

## 1.1.0

This is a minor maintenance and feature release.

* [PCP-713](https://tickets.puppetlabs.com/browse/PCP-713) Add an on-close
callback that's called whenever the client disconnects.
* Update to clj-parent 0.4.1 and pcp-common 1.1.1.

## 1.0.0

This is a major feature release to support PCP v2. It drops support for PCP v1.

* [PCP-700](https://tickets.puppetlabs.com/browse/PCP-700) Support usage in
pcp-broker. Specifically allow initializing from an SSLContext, handle the
heartbeat thread internally, and don't set sender so messages can be relayed.
* [PCP-652](https://tickets.puppetlabs.com/browse/PCP-652) Switch to using PCP
v2. Drops support for message expiration.

## 0.4.0

This is a feature release to enable compatibility with pcp-broker 1.0. It
retains compatibility with earlier pcp-broker implementations and uses PCP v1.

* [PCP-693](https://tickets.puppetlabs.com/browse/PCP-693) Updates the client
to interoperate with pcp-broker 1.0.0.

## 0.3.4

This is a maintenance release.

* Update (puppetlabs/i18n) to 0.4.3 and (puppetlabs/pcp-common) to 0.5.4 for a
performance-related bug fix in the i18n library.

## 0.3.3

This is a bug fix release.

* [PCP-544](https://tickets.puppetlabs.com/browse/PCP-544) Make client
operations more reliable by handling exceptions
* [PCP-535](https://tickets.puppetlabs.com/browse/PCP-535) Add
test-schema-validation profile and test-all task, don't test with schemas by
default
* [PCP-537](https://tickets.puppetlabs.com/browse/PCP-537) Report connecting
or disconnected immediately on connection close
* [PCP-530](https://tickets.puppetlabs.com/browse/PCP-530) Allow cert chains in
client cert

## 0.3.2

This is a bug fix release.

* [PCP-518](https://tickets.puppetlabs.com/browse/PCP-518) clj-pcp-client now
  includes a small delay between attempting to reconnect when disconnected
  while broker is starting up or shutting down.

## 0.3.1

This is a bug fix release.

* [PCP-370](https://tickets.puppetlabs.com/browse/PCP-370) Removed unused SSL
  certificates.
* [PCP-368](https://tickets.puppetlabs.com/browse/PCP-368) Add string
  externalization using puppetlabs/i18n library.
* [PCP-488](https://tickets.puppetlabs.com/browse/PCP-488) Disable schema
  validations by default.
* Update dependencies to (puppetlabs/pcp-common 0.5.1).
* [PCP-480](https://tickets.puppetlabs.com/browse/PCP-480) Make max binary
  message size configurable.

## 0.3.0

This is a bug fix release.  Changes to the Client interface have been made.

* Updated the examples.
* Made documentation improvements.
* [PCP-357](https://tickets.puppetlabs.com/browse/PCP-357) Don't start the
  WebSocket heartbeat thread before connecting; add a new function to the
  puppetlabs.pcp.client/Client interface for starting the WebSocket heartbeat
  synchronously.
* [PCP-346](https://tickets.puppetlabs.com/browse/PCP-346) Add an optional
  :user-data field to the puppetlabs.pcp.client/Client interface. 

## 0.2.2

This is a security release.  It is the same as 0.2.1, but public.

## 0.2.1

This is a security release - it was only available from internal mirrors

* [PCP-323](https://tickets.puppetlabs.com/browse/PCP-323) Verify the
  certificate of the broker connected to matches the hostname that
  was specified in the server uri.

## 0.2.0

This is a maintenance release

This is the first public release to clojars

* [PCP-47](https://tickets.puppetlabs.com/browse/PCP-47) Update dependencies to
  public released versions (puppetlabs/pcp-broker 0.5.0, puppetlabs/pcp-common
  0.5.0) and update to publish to clojars.

## 0.1.0

This is a feature and maintenance release

* Added standard cljfmt alias
* Added ASL 2.0 LICENSE
* Added CONTRIBUTING.md in preparation to be an open project
* Reworked ports in the examples to use the standard PCP port of 8142
* [PCP-24](https://tickets.puppetlabs.com/browse/PCP-24) Added reconnection
  logic.
* Updated pcp-broker dependency to 0.2.2 to get benefit of CTH-251 fixes.
* [PCP-43](https://tickets.puppetlabs.com/browse/PCP-43) Refactored flow of
  client so it can be used with `with-open`.
* [PCP-4](https://tickets.puppetlabs.com/browse/PCP-4) Now can wait for session
  association.  Adds `(client/associating? client)`, `(client/associated? client)`,
  and `(client/wait-for-assocation client timeout-ms)`.
* [PCP-59](https://tickets.puppetlabs.com/browse/PCP-59) Removed extra state
  checking.  Removes `(client/closing? client)` and `(client/closed? client)`.
  Renames `(client/open? client)` to `(client/connected? client)`.

## 0.0.7

This is a maintenance release

* [CTH-331](https://tickets.puppetlabs.com/browse/CTH-331) Uri scheme updated
  from `cth` to `pcp`

## 0.0.6

This is a feature and maintenance release

* [CTH-305](https://tickets.puppetlabs.com/browse/CTH-305) Websocket pings are
  now sent at a regular interval to heartbeat the connection.
* [CTH-311](https://tickets.puppetlabs.com/browse/CTH-311) distribution renamed
  from puppetlabs/cthun-client to puppetlabs/pcp-client

## 0.0.5

This is a feature release

* [CTH-337](https://tickets.puppetlabs.com/browse/CTH-337) Identity is now
  derived from client common-name and client type.

## 0.0.4

This is a maintenance release

* Updated puppetlabs/cthun-message depenency from 0.1.0 to 0.2.0
* Changed to a behaviour of fixing version incompatibilities via explicit
  version hoiting, rather than excludes proliferation.

## 0.0.3

This is a maintenance release

* [CTH-215](https://tickets.puppetlabs.com/browse/CTH-215) Added test that
  starts a broker to test real message sending.
* Fix namespace declarations caught by eastwood

## 0.0.2

This is a feature and maintenance release

* Update puppetlabs/cthun-message dependency from 0.0.1 to 0.1.0
* [CTH-212](https://tickets.puppetlabs.com/browse/CTH-212) Renames and rework
  for updates to protocol specificatons.

## 0.0.1

* Initial internal release
