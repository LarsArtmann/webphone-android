<!-- ROADMAP.md — LONG-TERM direction and raw ideas. No bounded tasks (those go
     to TODO_LIST.md), no status indicators, no completed milestones. -->

# Roadmap

> Long-term direction and raw ideas. Items here are NOT actionable tasks.
> When an idea is refined into bounded work, it moves to TODO_LIST.md.

## Themes

### 1. Production calling polish

After the first device train validates the honesty-contract markers, harden
the call surfaces toward daily-driver quality.

Raw ideas:

- Bluetooth/watch answer-path edge cases through the Telecom integration
- Doze/battery-manager variance in the ntfy lane (accepted trade today;
  revisit if rings go missing in the field)
- Audio-focus and audio-routing polish on the WebRTC leg
- Baseline profiles for startup + the call path (deferred 2026-10-25:
  measure a real device first — see Decisions)

### 2. Operator configuration

The origin is user-settable since 2026-10-24 (persisted in Settings,
`https://`-validated, live re-wire on save); the shipped default is the
operator's live stack. Ideas around going beyond typed configuration without
growing a second settings brain.

Raw ideas:

- Whether origin should be provisioned (QR/enrollment) instead of typed
- Multi-account / multi-line: does the single-call policy survive a second
  extension?

### 3. Distribution

Sideload is the lane (the favorable `USE_FULL_SCREEN_INTENT` default grant).

Raw ideas:

- Play Store, later, if ever — Play would re-audit the full-screen-intent
  grant; the sideload lane keeps it by default
- Update distribution for sideloaded installs (APK self-update? nothing?)
- A release signing config (deliberate future step — today the only
  installable artifact is the debug APK; `assembleRelease` emits an
  unsigned APK)

## Decisions

The made-in-head calls, written down (2026-10-25 sweep). Each carries its
revisit trigger — a decision without one is a grudge.

- **TLS is NOT pinned.** The stack is Caddy-fronted with Let's Encrypt
  certificates that rotate; SPKI pinning would brick every call at the
  next renewal. Trust = platform TLS store + HTTPS-only
  (`usesCleartextTraffic="false"`). Revisit only behind a long-lived
  internal CA.
- **API 31-33 outbound stays direct-dial, permanently.** `placeCall`
  is gone from the API 36 stubs and `addCall` needs 34+; there is no
  Telecom surface outbound below 34 until minSdk rises to 34. Not a
  gap — the honest degradation. Inbound is Telecom-integrated
  everywhere.
- **CallEndpoint-based speaker routing: NOT for v0.1.**
  `setCommunicationDevice` works on the supported range today; the
  CallEndpoint shape aligns with `addCall` but only exists on 34+.
  Revisit when minSdk reaches 34.
- **WorkManager as ntfy reconnect fallback: REJECTED.** Its minimum
  cadence is 15 minutes — that defeats a push socket, which is the
  entire lane. The real fix shipped: sticky FGS + the battery
  exemption ask. Revisit only if OEM task-killers defeat the FGS in
  the field.
- **Crash reporting: NONE by policy.** A personal stack with one
  operator; logcat + `adb bugreport` + the train scripts are the
  pipeline. Revisit ACRA (self-hosted) if daily-driver failures become
  untraceable.
- **Emulator snapshots: OFF (`-no-snapshot`).** Determinism over boot
  time — a train must prove the cold path a real user gets. Revisit
  if train boot time becomes a real tax.
- **WebView stance:** Safe Browsing stays default-on (the island is
  the only content and it is trusted, but the cost of the default is
  zero); user-agent stays WebView-default (no spoofing — the island
  knows its own shell); pinch-zoom stays enabled (accessibility over
  app-look); API 31-32 keeps the web app's own theme toggle as the
  dark-mode fallback (algorithmic darkening needs 33+).
- **Kotlin formatter/linter: none for v0.1.** Style is enforced by
  review (Ktlint/detekt would fight the programmatic-UI idiom for
  marginal gain at this size). Revisit when a second heavy Kotlin
  contributor exists.
- **values-de (German strings): not now.** The UI is English-only;
  strings are already extractor-safe (positional args, no string
  concatenation in layouts). Revisit when there is a second user who
  wants it.

## Open questions

Unresolved questions route here until an answer turns them into bounded work
(TODO_LIST) or non-goals.

- **Live-stack access (the device-train blocker):** which test extension
  (+ account, if login needs one) may the app use against the operator's
  stack, and is the stack reachable? Everything self-serve is done.
- **Logout semantics (drives the deregistration design):** when a user logs
  out on the island, does the server KEEP the `/api/push-devices` rows (the
  native side must deregister BEFORE the cookie dies → JS bridge needed), or
  PURGE them (the shipped cookie-poll watcher suffices)?
- **Icon parity:** the native launcher leads with the handset glyph; the PWA
  uses a link glyph. Keep them diverged, or adopt the handset in both (change
  both or neither)?
- **Visibility end-state:** both remotes are PRIVATE today; the sibling
  `webphone` repo is public and this README reads public-facing. Keep private
  until the honesty-contract markers clear, then flip the GitHub twin public
  — or stay private?

## Non-goals

- **Multi-call:** single-call by product decision — a personal extension
  line; a second call waits, it never silently replaces.
- **Web-only call UI regression:** the platform decision moved the call
  contract into Kotlin because the browser cannot ring a closed app; do not
  move signaling/audio back into the WebView.
- **A second login surface:** the island login is the ONE login surface; the
  shell never grows its own login form.
- **Hard Google dependency:** the ntfy lane must keep working with zero
  Google dependencies; FCM is an additive lane, never a requirement.
