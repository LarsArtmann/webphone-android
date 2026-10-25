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
- MMS attachment downloads work: same-origin `download` links ride the
  system DownloadManager WITH the session cookie (the attachment routes
  are session-gated); foreign downloads hand off to the browser — the
  session cookie never leaves the origin
- Main-frame HTTP errors (the proxy's 502, the origin's styled 404)
  render as pages BY POLICY; the native offline overlay stays reserved
  for transport failures ("stack unreachable")
- WCAG contrast guard as code: `scripts/contrast-check.py` +
  `scripts/contrast-pairs.json` (50 checks, light AND dark modes,
  alpha-composited chrome glass, worst-case bases) as a CI step — the
  2.44:1 answer-button class can never drift back silently; the
  reserved dark `wp_danger` token is now text-grade (6.1:1 on surface)
- Test suite grew to 72 JVM tests: WebView client delegation (the
  scope guard wired, the transport-vs-HTTP error split, the download
  policy incl. cookie confinement, Content-Disposition filename
  derivation), the MockWebServer REGISTER-ladder soak over a REAL
  websocket (challenge → credentialed retry → registered, the
  wrong-password bound, stale=true grace), the ntfy topic-rotation
  restart, the call service's styled/plain branches + shade hang-up,
  and the router's answered-call-spared + auth-rejected suppression
  pins
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
- CallStyle notifications (the platform calling look): incoming with
  the shade's Answer/Decline actions (`CallNotificationReceiver`) and
  an ongoing style with Hang up + the shade's own call clock (elapsed
  from the engine's establishment timestamp); plain-notification
  fallbacks wherever Telecom cannot back the style at post time
- Missed-call notes: a ring on a busy line and a `ring_end` that dies
  unanswered leave a visible note instead of a silent loss; a stale
  `ring_end` after a local answer spares the live call
- "Sign-in rejected" registration cause: the badge, the dialer guard,
  and the ring path distinguish a refused credential from a network
  outage — a ring push while auth-rejected surfaces the cause instead
  of promising a call that can never land
- In-call duration clock on the call screen (silent to TalkBack),
  dialpad + DTMF haptics, and recents re-read on resume
- Battery-optimization exemption ask: once per install, after a
  registration proves the session works (direct dialog + settings
  deep-link fallback)
- SIP hardening: the INVITE 401/407 digest rung (same dialog, same
  SDP, bounded), the bounded REGISTER retry ladder (one credentialed
  retry, two when stale), the silence watchdog (registered-but-quiet
  >6 min → rebuild), and the AttachGuard generation counter making
  shutdown invalidate in-flight call-layer wires
- Day/night theme recreation: `uiMode` removed from `configChanges`
  so the system recreates every surface on theme change (the frozen-
  palette root fix)
- WebView UX tail: image-only file inputs on API 33+ route through
  the system photo picker; verbose console bridging gated behind
  debuggable builds (errors always); the offline overlay re-resolves
  the origin at show time
- Self-serve train kit discipline: the emulator serial is resolved
  before any wait (a physical device on the same adb server no longer
  aborts the run) and the release smoke uninstalls mismatched-
  signature packages first; FIRST `--release` smoke ran green (R8
  output boots, 2026-10-25)
- `scripts/emulator-photos.sh` (light+dark capture in one run) and
  `scripts/mirror-lag.sh` (mirror-lag probe); MIT LICENSE
- CI push trigger (`push: branches: [main]`)

### Fixed

- System back on API 33+ never reached the WebView's tab history: the
  manifest's `enableOnBackInvokedCallback` silences `onBackPressed`,
  so a predictive-back callback bridges the history now (the override
  only serves API 31/32)
- The second call of the day was silently refused: the call phase
  never rewound after teardown (the ENDED line now accepts the next
  call — regression-pinned)
- The ntfy stop leak: a pending reconnect could resurrect the
  subscription after stop/destroy (rang forever with no owner)
- Call-engine confinement: every mutation sits under one lock,
  teardown disposes the media exactly once (double-dispose was a
  native-crash window), and offer/answer callbacks guard stood-down
  calls
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
- The `.kotlin/` directory (Kotlin 2.x session state) is local-only —
  gitignored, never committed
- `runCatching` swallows on the call path now log (the telecom-answer
  FGS start, the CallStyle post, the unroutable ntfy frame); Telecom
  `addCall` sessions carry a generation guard so superseded sessions
  disconnect instead of orphaning
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
