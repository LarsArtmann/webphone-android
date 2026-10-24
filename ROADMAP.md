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

### 2. Operator configuration

The shell is currently single-origin, hardcoded. Ideas around making it
operator-configurable without growing a second settings brain.

Raw ideas:

- Settings surface for the stack origin (first device train deliverable)
- Whether origin should be provisioned (QR/enrollment) instead of typed
- Multi-account / multi-line: does the single-call policy survive a second
  extension?

### 3. Distribution

Sideload is the lane (the favorable `USE_FULL_SCREEN_INTENT` default grant).

Raw ideas:

- Play Store, later, if ever — Play would re-audit the full-screen-intent
  grant; the sideload lane keeps it by default
- Update distribution for sideloaded installs (APK self-update? nothing?)

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
