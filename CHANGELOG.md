<!-- CHANGELOG.md — CHRONOLOGICAL record, append-only. Keep a Changelog format.
     No planning notes, no feature status, no internal context. -->

# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Hardened WebView shell (`shell/WebSurface.kt`): renderer-crash
  recovery (a dead WebView renderer rebuilds the surface instead of
  killing the app), same-origin navigation scope (other hosts and
  `tel:`/`mailto:` links hand off to the system, never to the
  session-cookie-bearing WebView), a native offline overlay with Retry
  for main-frame load failures, a working file chooser (the composer's
  MMS attach button — a silent dead button without a WebChromeClient),
  explicit deny for web `getUserMedia` asks (call audio is native),
  console-to-logcat bridging, WebView pause/resume pairing (battery),
  and debug-only `chrome://inspect` support
- WebView dark mode (API 33+): the web surfaces follow the system
  theme via algorithmic darkening — the web app declares
  `color-scheme: dark light` and renders its own dark theme; its
  manual light/dark toggle still overrides
- Design system: light/dark theme tokens (`wp*` attrs, DayNight) with
  programmatic helpers in `ui/Design.kt`; every native surface
  redesigned — dark-glass shell chrome with a pulsing line dot, a
  circular dialer with letters under the keys and bottom-anchored
  actions, an always-dark call screen (gradient, initials avatar,
  circular answer/decline pads, toggles with lit states, the DTMF pad
  behind a Keypad toggle), and a card-based Settings field
- Edge-to-edge rendering on all native surfaces (insets-aware
  padding); the WebView runs full-bleed behind the system bars
- Adaptive launcher icon: handset glyph in the safe zone plus a
  monochrome layer (Android 13 themed icons)
- Dependabot dependency updates (`.github/dependabot.yml`)
- Dialer entry: numpad activity with mic-permission gate and single-call
  guard, plus a dial button on the shell
- Idempotent call-layer wire (`shell/CallLayer.kt`) — one SIP engine per
  process, shared by the resume loop AND ring pushes while closed
- Push-device registration: ntfy topic always, FCM token best-effort,
  on every session resume and token rotation
- `ring_end` stands the ring down: Telecom connection disconnected,
  ringing state cancelled
- Full-screen-intent runtime check with heads-up degradation
  (`canUseFullScreenIntent()`)
- Settings surface: the stack origin is user-configurable —
  persisted (`SettingsActivity`, `https://`-validated), loaded at
  process start; the shipped default origin is the live stack
  (`https://pbx.artmann.tech`)
- SIP registration state surfaced end-to-end: `CallEngine.registration`
  (OFF/CONNECTING/REGISTERED), a shell badge, and a dialer that
  disables the Call button while unregistered
- Outbound calls ride Telecom on API 34+ (`TelecomManager.addCall` +
  `CallControl` state mirroring — the classic `placeCall` entry is
  gone from the API 36 stubs); API 31-33 and Telecom rejections fall
  back to a direct dial
- CANCEL support: hang-up during ring-out sends a spec-correct CANCEL
  (the INVITE's own Via branch + CSeq + provisional To tag, RFC 3261
  §9.1); a confirmed dialog refuses CANCEL and ends via BYE; non-2xx
  INVITE finals are ACKed (487 teardown is clean, not an error)
- ntfy lane persistence: server/topic survive a sticky restart
  (null-intent restarts resubscribe from prefs)
- Session watcher: a dead session (island logout) stops the ntfy lane
  and tears the engine down, best-effort deregistering the device
- Test suite: 25 JVM tests — SIP codec round-trips and malformed-input
  rejection, digest credentials against the RFC 2617 §3.5 vector
  (independently re-computed), CANCEL/BYE/487/ACK wire sequences,
  device-identity topic shape, plus Robolectric smoke for the push
  router (Telecom + notification shadows) and the shell launch
- CI workflow (`.github/workflows/ci.yml`): debug build, unit tests,
  16 KB zipalign gate, dprint check (first run awaits a git remote)
- Self-serve device train: `.#emulator` devShell (API 36 emulator +
  system image) with `scripts/emulator-train.sh` / `emulator-smoke.sh`
- Speaker toggle (earpiece ⇄ speaker, communication-device API),
  dialpad recents (last 5 numbers), long-press backspace clear,
  floating-action styling, 48dp touch targets, extracted strings,
  ended-call auto-finish, re-ring intent refresh

### Fixed

- The session cookie minted by the island login is flushed to disk
  (page finish + pause) — a process kill can no longer eat the login
- Session seam: OkHttp now bridges through the WebView's `CookieManager`
  — the login cookie actually reaches `/api/session` (was a private
  in-memory jar that never saw it)
- SipEngine leak: `CallEngine.detach()` now disconnects the socket and
  cancels refresh/watchdog jobs
- Engine churn: repeated resumes no longer stack WSS connections
- ntfy subscription: no duplicate websockets per resume; reconnect loop
  no longer double-fires or deadlocks on a stale socket reference
- Startup crash on real WebView stacks: `setAcceptThirdPartyCookies`
  NPEs under the Chromium glue when passed a null WebView (found by
  the first emulator smoke, 2026-10-24)
- FSI auto-answer: opening the incoming-call screen answered the call
  without any user tap; answering now requires an explicit Answer tap
  (or the Telecom/BT answer path)
- The WebView follows an origin change immediately on resume (saving a
  new origin used to leave the old origin's page on screen)
- Digest challenges advertising a qop LIST (`"auth,auth-int"`) are now
  hashed and answered with the CHOSEN qop (`auth`), not the raw list;
  challenge parsing handles unquoted values (`algorithm=SHA-256` no
  longer swallows the rest of the header)
- SIP parser rejects non-`SIP/2.0` request lines and out-of-range
  status codes instead of parsing garbage into messages
- Telecom connections are destroyed when the remote side ends the call
  (previously a zombie connection survived a remote BYE)
- Notification permission is requested in context (post-registration)
  instead of on a cold launch

### Changed

- Formatting baseline via dprint (JSON/YAML/Markdown/Dockerfile; `dprint.json`)
- Call end attribution modeled explicitly (`CallState.endedBy`:
  local/remote/failed) and rendered as Telecom DisconnectCauses
- Gradle configuration cache enabled; Dependabot config de-duplicated
  (two identical `gradle /` entries — Dependabot rejects that file) and
  extended with the `github-actions` ecosystem

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
