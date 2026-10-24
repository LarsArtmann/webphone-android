<!-- CHANGELOG.md — CHRONOLOGICAL record, append-only. Keep a Changelog format.
     No planning notes, no feature status, no internal context. -->

# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Dependabot dependency updates (`.github/dependabot.yml`)
- Dialer entry: numpad activity with mic-permission gate and single-call
  guard, plus a dial button on the shell (`call/DialpadActivity.kt`)
- Idempotent call-layer wire (`shell/CallLayer.kt`) — one SIP engine per
  process, shared by the resume loop AND ring pushes while closed
- Push-device registration: ntfy topic always, FCM token best-effort,
  on every session resume and token rotation
- `ring_end` stands the ring down: Telecom connection disconnected,
  ringing state cancelled
- Full-screen-intent runtime check with heads-up degradation
  (`canUseFullScreenIntent()`)
- `POST_NOTIFICATIONS` runtime request on first launch (API 33+)
- Hang-up control during outgoing ring-out; BT/watch answer path starts
  the call foreground service

### Fixed

- Session seam: OkHttp now bridges through the WebView's `CookieManager`
  — the login cookie actually reaches `/api/session` (was a private
  in-memory jar that never saw it)
- SipEngine leak: `CallEngine.detach()` now disconnects the socket and
  cancels refresh/watchdog jobs
- Engine churn: repeated resumes no longer stack WSS connections
- ntfy subscription: no duplicate websockets per resume; reconnect loop
  no longer double-fires or deadlocks on a stale socket reference

### Changed

- Formatting baseline via dprint (JSON/YAML/Markdown/Dockerfile; `dprint.json`)

## [0.1.0] - 2026-10-23

### Added

- Native-call shell scaffold: WebView tab surfaces over the stack origin
  (`shell/MainActivity.kt`)
- Session seam: cookie → SIP credentials resume, CSRF dance, push-device
  registry client (`shell/SessionApi.kt`)
- SIP-over-WSS user agent: REGISTER (digest), outbound INVITE, hold
  re-INVITE, BYE, DTMF (equals form), REFER/NOTIFY (`sip/SipEngine.kt`,
  `sip/SipMessage.kt`, `sip/Digest.kt`)
- WebRTC call audio via stream-webrtc-android (`call/WebRtcEngine.kt`)
- Single-call orchestrator with StateFlow state (`call/CallEngine.kt`)
- Telecom self-managed integration + typed `microphone|phoneCall` foreground
  service (`call/CallConnectionService.kt`, `call/CallService.kt`)
- Ring-while-closed push: FCM lane (dormant without `google-services.json`)
  and ntfy websocket lane, both routing through the full-screen-intent
  ringer (`push/*`)
- 16 KB page-aligned packaging verified on the assembled debug APK
  (`zipalign -c -P 16 4`, build-tools 35)
