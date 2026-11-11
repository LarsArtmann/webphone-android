# Security Policy

## Supported Versions

| Version                                          | Supported |
| ------------------------------------------------ | --------- |
| latest release (`version.txt`, Forgejo releases) | yes       |
| anything older, debug-signed builds              | no        |

This is a single-operator project: only the newest published release
receives attention, and there is no backporting.

## Reporting a vulnerability

Report privately via GitHub's security advisory form:
<https://github.com/LarsArtmann/webphone-android/security/advisories/new>
(a GitHub account is required; the repository is a public mirror of the
canonical Forgejo repo). Alternatively open a Forgejo issue on
`lars/webphone-android` if the report is not sensitive. Include the
version from `version.txt` and, for on-device behavior, the Android
version. Response-time commitment: best-effort acknowledgment within
7 days (single operator; no contractual SLA).

## Security Practices

- **Scope**: this repository only — the Android shell (WebView
  hardening, SIP-over-WSS signaling, WebRTC call audio, Telecom
  integration, push lanes, persisted settings, the session-cookie
  bridge). Server/web-island/PBX issues belong to the
  [webphone](https://github.com/LarsArtmann/webphone) repository.
- **Honesty contract**: the README's honesty-contract section is part
  of this policy — the load-bearing call path is implemented but not
  yet proven against live infrastructure, and CI has never executed a
  job. Treat the app as a personal calling client for the operator's
  own stack, not an audited product.
- **Distribution**: debug builds are debug-signed and not for
  distribution; only the release-keyed APKs on Forgejo releases are
  update-path artifacts. The forge endpoint is reachable on the home
  LAN/VPN only (internal CA).
- **Transport**: cleartext is disabled app-wide; TLS is platform-trust
  (not pinned — rotating Let's Encrypt certs, a documented decision in
  ROADMAP).
