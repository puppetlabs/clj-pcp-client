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
* [CTH-212](https://tickets.puppetlabs.com/browse/CTH-212) Renames and rework for updates to protocol specificatons.

## 0.0.1

* Initial internal release
