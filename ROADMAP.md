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

- CallStyle-composed in-call notifications (the platform's native calling look)
- Bluetooth/watch answer-path edge cases through the Telecom integration
- Doze/battery-manager variance in the ntfy lane (accepted trade today;
  revisit if rings go missing in the field)
- Audio-focus and audio-routing polish on the WebRTC leg
- Missed-call notification when `ring_end` lands unanswered
- In-call duration timer; dialpad haptic feedback
- WorkManager as a fallback ntfy reconnect path
- `CallEndpoint`-based speaker routing on API 34+ (align with the addCall
  session instead of raw AudioManager)
- Crash reporting (self-hosted, e.g. ACRA) — or none by policy
- Baseline profiles for startup + the call path

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
- **Where should CI run?** GitHub Actions with a `push` trigger (private
  minutes; the account's Actions were billing-dead 2026-09-08), the native
  Forgejo runner via `.forgejo/workflows`, or manual device trains only?
  Today NOTHING builds on merge-to-main.
- **WebView dark-mode:** the island's own pages do not dark-mode; algorithmic
  darkening for the WebView is a separate, undecided surface.
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
