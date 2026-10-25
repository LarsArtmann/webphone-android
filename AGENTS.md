# webphone-android

The Android native-call shell for [webphone](https://github.com/LarsArtmann/webphone)
(the server + web island live in that repo — this app is only the call
contract). Kotlin natively owns: SIP-over-WSS signaling, WebRTC call
audio, Telecom self-managed integration, and ring-while-closed push.
Every other surface (Messages, Fax, Voicemail, History, Contacts,
Settings) is the stack origin's own web pages in a session-sharing
WebView. Single-call by product decision: a second call waits, it never
silently replaces.

**Status: v0.1 scaffold, first runtime smoke done.** Load-bearing code is
implemented but untested against LIVE infrastructure (the README
"honesty contract" section lists the markers). There IS now: a JVM test
source set (25 tests: protocol unit tests + Robolectric smoke) and a CI
workflow (mirrored to GitHub 2026-10-25, but its triggers are
`pull_request`/`workflow_dispatch` only — it has never run). First
emulator smoke (install + launch) passed 2026-10-24.

## Repository & mirrors

Forgejo is the primary (canonical) remote; GitHub is the off-site
mirror. Both are **private**.

- Forgejo: `forgejo@evo-x2.home.lan:lars/webphone-android.git`
  (`origin`), created native via SSH push-to-create (Forgejo default is
  private) on 2026-10-25.
- GitHub: `git@github.com:LarsArtmann/webphone-android.git` (`github`
  remote, push fallback), created private 2026-10-25 and seed-pushed by
  hand over SSH — the seed had to carry `.github/workflows/ci.yml`,
  which the sync PAT cannot push (below).
- The Forgejo→GitHub push mirror (8h + `sync_on_commit`) is attached by
  phase 5 of `forgejo-github-sync`, driven by `forgejo.canonicalRepos`
  in `~/projects/SystemNix` (`platforms/nixos/system/configuration.nix`;
  entry added 2026-10-25). It attaches on the next SystemNix deploy
  (`nix run .#deploy`) or a root-shell hand-run of the phase-5 script
  `forgejo-push-mirror` with `FORGEJO_CANONICAL_REPOS=webphone-android`.
- **Mirror credential trap (LIVE for this repo):** the sops
  `github_token` sync PAT lacks `workflow` scope and this repo HAS
  `.github/workflows/ci.yml` — GitHub rejects mirror pushes containing
  workflow-file changes ("refusing to allow a Personal Access Token …
  without `workflow` scope"; the same trap froze `domains` at 114
  commits behind as of 2026-10-25). Fix: regenerate the PAT with
  repo+workflow scope, update the sops secret, redeploy. Commits that
  do not touch `.github/workflows/` mirror fine.
- Mirror lag check (same probe as `domains`):
  `git fetch origin && git fetch github && git rev-parse origin/main github/main` —
  equal SHAs mean the mirror is keeping up.
- **NOTHING pushes ANY remote automatically** — the daemon commits locally
  only (verified 2026-10-25: both remotes lagged local while the mirror was
  unattached). Until the push mirror attaches (next SystemNix deploy):
  after every session `git push origin main && git push github main` by
  hand, or both the canonical repo and the twin silently drift.

## Build and verify

```console
nix develop                        # JDK 21 + Android SDK (platform 36, build-tools 34+35)
nix develop -c ./gradlew assembleDebug      # debug APK
nix develop -c ./gradlew assembleRelease    # minified + resource-shrunk (R8, proguard-rules.pro)
nix develop -c ./gradlew testDebugUnitTest  # JVM unit tests (protocol + Robolectric smoke)
```

The verification gate after every code change: `assembleDebug` green +
`zipalign -c -P 16 4` on the debug APK + `testDebugUnitTest` green.

The self-serve device train (headless API 36 emulator, requires
`/dev/kvm`; the AVD lives in `.avd/`):

```console
nix develop .#emulator -c bash scripts/emulator-train.sh   # boot+install+launch+screenshot, exits
nix develop .#emulator -c bash scripts/emulator-smoke.sh   # keeps the emulator up for iterating
```

- The dev environment comes only from `flake.nix` (unfree license
  scoped to the Android SDK). Never add a Makefile or justfile —
  `flake.nix` is the task surface in LarsArtmann projects.
- 16 KB page-alignment check (mandatory per release and per
  `webrtc` dependency bump; requires build-tools 35 — the `-P` flag
  does not exist in 34):

  ```console
  zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk
  ```

- Formatting: `dprint.json` covers JSON/YAML/Markdown/Dockerfile only.
  Kotlin style is enforced by review, not tooling.
- Dependency versions live exclusively in `gradle/libs.versions.toml`;
  module build files use `libs.*` aliases. `settings.gradle.kts` sets
  `FAIL_ON_PROJECT_REPOS` — never add repositories to `app/build.gradle.kts`.
- Dependabot handles version bumps.

## Architecture

Single `:app` module, package-per-concern under
`software.larsartmann.webphone`:

| Package  | Contents                                                                                                                   | Role                                                                                                                                                                                |
| -------- | -------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `shell/` | `MainActivity`, `WebSurface`, `CallLayer`, `SessionApi`, `DeviceIdentity`, `SettingsActivity`                              | The WebView shell (hardened surface in `WebSurface`) + the idempotent call-layer wire + the origin's HTTP session seam + per-install identity + the persisted stack-origin settings |
| `sip/`   | `SipEngine`, `SipMessage`, `Digest`                                                                                        | SIP-over-WSS user agent: REGISTER (digest), INVITE/re-INVITE/CANCEL/BYE, DTMF INFO, REFER/NOTIFY                                                                                    |
| `call/`  | `CallEngine`, `WebRtcEngine`, `CallService`, `CallConnectionService`, `TelecomOutgoing`, `CallActivity`, `DialpadActivity` | Call orchestration (incl. registration state), WebRTC audio, typed FGS, inbound Telecom integration, outbound addCall bridge (API 34+), in-call UI, dialer entry                    |
| `push/`  | `RingEvent` (the router), `FcmReceiver`, `NtfySubscriptionService`                                                         | The two ring-while-closed transports, both converging on `RingEvent.route`                                                                                                          |
| `ui/`    | `Design` (`themeColor`, `iconButton`, `pillBackground`, `pulse`, …)                                                        | The design-token system behind every native surface: theme attrs resolved programmatically, one helper vocabulary                                                                   |

Unit tests live under `app/src/test/` mirroring the packages; Robolectric
tests MUST set `@Config(application = Application::class)` — the manifest
`App` initializes WebRTC's native factory, which
`UnsatisfiedLinkError`s on the JVM.

### Control flow

1. **Session**: `MainActivity` loads `App.origin` (persisted in
   Settings — `SettingsActivity` validates `https://`, saves to prefs,
   and tears the call layer down so the next resume re-wires) in the
   WebView; the island's login mints the session cookie.
   `CallLayer.ensureWired` (idempotent, callable from any wake path)
   resumes the session (`SessionApi.resumeSession()`, GET
   `/api/session`), creates the `SipEngine`, attaches it to the
   `CallEngine` singleton, registers the push lanes, starts the ntfy
   subscription, and starts a 10-minute session watcher (dead session
   → best-effort deregister + lane stop + engine teardown). A ring
   push while closed calls the same entry point — the SIP leg must
   come up for the INVITE to land (the bootstrap races it).
2. **Outbound call**: `DialpadActivity` →
   `CallConnectionService.placeOutgoingCall` → on API 34+
   `TelecomOutgoing.place` (`TelecomManager.addCall`, the modern VoIP
   session — a `CallControl` mirrors engine ACTIVE/HELD/ENDED) →
   `CallEngine.dial` → WebRTC offer → `SipEngine.invite` →
   180/200/ACK ladder → `onCallEstablished` applies the remote
   answer. Pre-34 devices (and Telecom rejections) fall back to a
   direct `CallEngine.dial`.
3. **Inbound call (ring while closed)**: push arrives (FCM data message
   or ntfy websocket JSON, same `{event, from, call_id}` payload) →
   `RingEvent.route` → `telecom.addNewIncomingCall` (self-managed
   connection) + full-screen-intent notification → `CallActivity` →
   explicit Answer tap → `CallEngine.answer`. The FSI opening NEVER
   answers by itself; Telecom/BT `onAnswer` is the other explicit path.
4. `CallEngine` is the single orchestrator: it implements
   `SipEngine.Listener`, exposes `StateFlow<CallState>`, and is the only
   writer of call state. Telecom callbacks
   (`CallConnectionService.SelfManagedConnection`) and UI both funnel
   into it.

### The server seam (cross-repo contract)

The HTTP/WS contracts mirror the webphone server (`internal/push`): GET `/api/session`, GET `/api/csrf` +
`X-CSRF-Token` header dance, POST/DELETE `/api/push-devices` (upsert by
stable `device_id`; token rotation replaces the row), WSS `/sip`
(Caddy-fronted), and the ntfy topic websocket
(`<origin>/<topic>/ws`). Field names are owned by the server repo —
drift surfaces at first device boot, not at compile time. Push payload
events: `ring` and `ring_end`.

## Gotchas

- **DTMF**: always the EQUALS form of `application/dtmf-relay`
  (`Signal=5`). Never the colon form — FreeSWITCH 200-OKs it and
  silently drops the digit.
- **Telecom outbound on API 36 (compileSdk)**: the classic self-managed
  outgoing entry `TelecomManager.placeCall(handle, uri, extras)` is
  GONE from the API 36 stubs — `onCreateOutgoingConnection` cannot fire
  and was deleted. Outbound rides `TelecomManager.addCall` (API 34+,
  `CallAttributes`/`CallControl`); API 31-33 dials directly (no OS
  surface). NEVER reintroduce `placeCall` — it does not compile against
  platform 36. Inbound stays classic (`addNewIncomingCall`, all APIs).
- **No release signing config**: `assembleRelease` emits
  `app-release-unsigned.apk` — NOT installable on any device. The only
  sideload/MVP artifact is the debug APK (auto debug-signed). Adding a
  signing config is a deliberate future step, not an accident to fix.
- **Stack origin**: persisted in Settings (`webphone-settings` prefs;
  `SettingsActivity` validates `https://` and tears the call layer
  down on save). The shipped default is the operator's live stack
  (`https://pbx.artmann.tech`) — no first-run prompt; anyone pointing
  elsewhere uses Settings. A saved origin wins over the default.
  Cleartext is disabled (`usesCleartextTraffic="false"`), so the
  origin must be HTTPS.
- **One cookie store**: `SessionApi`'s OkHttp client bridges through the
  WebView's `CookieManager` (read AND write). The island login mints
  there; API calls read the same jar. Never add a second cookie store
  — the v0.1 split-brain gap is closed, keep it that way.
- **Answer-path race (open question until the first device train)**: a
  ring push while closed rings via Telecom immediately, but the SIP
  INVITE only lands once `CallLayer.ensureWired` brings the leg up —
  whether the server re-invites long enough for that race to be won
  is unknown.
- **Mic FGS background-start ban**: Android 15+ forbids starting a
  `microphone` foreground service from the background. `CallService`
  is only ever started from user interaction (the answer flow through
  `CallActivity` is the legitimate launch path).
- **FCM lane is dormant by default**: `firebase-messaging` is a plain
  dependency with no `google-services` plugin and no
  `app/google-services.json`. The ntfy lane (`NtfySubscriptionService`)
  is the zero-Google default; a GMS build drops in the JSON and enables
  the plugin.
- **`hostToken()` in `SipEngine`** deliberately generates
  `<uuid12>.invalid` for Via/Contact. It looks like a bug; it is not —
  it is the WSS NAT convention. Don't "fix" it to a real hostname.
- **WebRTC init order**: `WebRtcEngine.initialize` runs once in
  `App.onCreate` (expensive stateful native factory); any `CallMedia`
  before it throws. Keep it that way.
- **`stream-webrtc-android` ≥ 1.3.10** is the first release shipping
  16 KB-aligned libwebrtc. Never downgrade; re-run the zipalign check
  on every bump.
- **R8 keeps**: `proguard-rules.pro` keeps `org.webrtc.**` (JNI
  reflection) and `FcmReceiver`. Extend it when adding anything
  reflection- or JNI-touched.
- **SIP liveness discipline**: the socket layer never re-registers on
  its own. Registration refreshes at expires/2 (300 s of 600 s); a
  dead socket is rebuilt by the watchdog after a 3 s backoff — the UI
  state must always follow `onRegistrationState`, never the socket
  callback (the web island's sip.js hang lesson, ported).
- **REGISTER digest**: answer with whatever algorithm the challenge
  names (MD5 or SHA-256), like the island's sip.js does. A multi-qop
  challenge (`"auth,auth-int"`) is hashed with the CHOSEN qop
  (`auth`), never the raw list — the RFC 2617 §3.5 vector is a unit
  test (`DigestTest`).
- **Hang-up discipline**: CANCEL while the ring is out (the INVITE's
  own Via branch + CSeq, RFC 3261 §9.1), BYE only on a confirmed
  dialog, 603 for an inbound ring. `SipEngineTest` pins the message
  sequences; a CANCEL after 200 is a protocol crime the guard refuses.
- `POST_NOTIFICATIONS` is requested in context — once registration
  proves the session works — not on a cold launch (API 33+).

## Conventions

- Prose-heavy KDoc explaining WHY (decisions, tradeoffs, cross-repo
  context), trailing commas, explicit `Unit` in no-op overrides,
  `runCatching` with explicit defaults at IO seams, `StateFlow` for
  observable state. Match this style.
- Engines and seams are `object` singletons (`CallEngine`,
  `WebRtcEngine`, `SessionApi`, `RingEvent`); per-call/per-connection
  state is nested classes (`CallMedia`, `Leg`).
- Notification IDs: 1001 = incoming ring, 1002 = call FGS, 1003 = ntfy
  FGS. Channels `calls` (high, bypass-DND) and `push` (min) are created
  in `App.onCreate`.
- UI is programmatic in v0.1 (`CallActivity` builds views in code);
  viewBinding is enabled. `minSdk = 31`, `targetSdk/compileSdk = 36`.
- Intents carry the ring fact under `RingEvent.EXTRA_FROM` /
  `EXTRA_CALL_ID` action-namespaced extras.
- Dialer honesty: the Call button is disabled unless
  `CallEngine.registration` is `REGISTERED` (the dialer shows the
  same state the shell badge does); dialing unregistered would no-op
  silently.
- **Two adb trees, one server**: the default devShell and the
  `.#emulator` devShell ship different `adb` versions sharing port
  5037 — an adb server started by one is killed by the other, and a
  mid-flight script then hangs in `adb wait-for-device`. Drive the
  emulator from ONE adb per session (the emulator shell's own).
- **WebView cookie gate**: `CookieManager.setAcceptThirdPartyCookies`
  CRASHES (`NullPointerException` inside the Chromium glue) when
  passed a null WebView — always pass the real `webView` (found by
  the first emulator smoke, 2026-10-24; Robolectric's shadow does not
  reproduce it, so this class of bug only surfaces on a real stack).
- **WebView hardening home**: `shell/WebSurface.kt` owns the WebView,
  its clients and the offline overlay. Navigation scope is the pure
  `navigationVerdict` — same-origin ONLY, everything else (other
  hosts, `tel:`, `mailto:`) hands off to the system; never render
  foreign web in the session-cookie-bearing WebView. Renderer death
  is consumed (`onRenderProcessGone` → fresh WebView + reload — the
  default kills the app). Web `getUserMedia` asks are DENIED by
  design: call audio is native (platform decision), and the deny is
  explicit so the page never hangs on a pending permission. Dark
  mode (API 33+): `isAlgorithmicDarkeningAllowed` + the pinned
  `android:isLightTheme` — the web app declares `color-scheme: dark
  light`, so it renders its OWN dark theme (no recoloring) and its
  manual light/dark toggle still overrides. The composer's MMS
  attach NEEDS `WebChromeClient.onShowFileChooser` — without a
  ChromeClient `<input type=file>` is a silent dead button; never
  ship the WebView without one. Downloads NEED a `DownloadListener`
  (`<a download>` is a silent no-op otherwise): same-origin downloads
  ride the system DownloadManager WITH the session cookie (attachment
  routes are session-gated) — foreign downloads hand off, the cookie
  never leaves the origin. Main-frame HTTP errors (proxy 502, the
  styled 404) RENDER as pages BY POLICY (`onReceivedHttpError` is a
  documented no-op) — the offline overlay is transport-failure only
  (`onReceivedError` → `onMainFrameTransportFailure` seam).
- **Contrast is a GATE, not a review**: `scripts/contrast-check.py`
  reads the real tokens from `values{,-night}/colors.xml` and checks
  `scripts/contrast-pairs.json` (WCAG: 4.5 text, 3.0 glyphs/dots) in
  BOTH modes, alpha-compositing the chrome glass over its bases
  (worst case). CI runs it; run it locally after ANY token change and
  add the pair when you add a colored surface. `wp_on_danger` is
  unlisted until a filled danger surface exists — mint passing values
  then, not before.
- **Predictive back owns API 33+ back nav**: the manifest sets
  `enableOnBackInvokedCallback="true"`, which SILENCES `onBackPressed`
  — `MainActivity` registers an `OnBackInvokedCallback` that bridges
  the WebView's tab history (falls through to `finish()`). Editing the
  override alone is dead code on 33+; it only serves API 31/32.
- **uiMode stays OUT of `configChanges`**: the theme-staleness fix
  (2026-10-25) removes it from all three activities so the system
  RECREATES on day/night and every surface re-resolves its tokens
  (the relaunch is logcat-provable). Re-adding it reintroduces the
  frozen-palette bug; Robolectric does not catch it — emulator find.
- **SIP liveness cancel-discipline** (the no-double-REGISTER-storm
  review verdict, 2026-10-25): every relaunch path (`scheduleRefresh`,
  `startSilenceWatchdog`, `startReconnectBackoff`, `disconnect`)
  cancels its predecessor Job BEFORE re-arming, and each REGISTER 200
  re-anchors both jobs. Keep that shape.
- **`SipEngine.lastActivity` lives in `receive()`**: every inbound
  frame (not just REGISTER) counts as wire activity — the silence
  watchdog (>6 min quiet, 30 s poll) judges the LINK, not one method.
- **AttachGuard semantics**: `CallLayer` wires carry a generation;
  `shutdown()` invalidates FIRST, so an in-flight `ensureWired`
  attaches to a dead generation and refuses. Never wire around the
  guard. `TelecomOutgoing` uses the same orphan shape: a superseded
  `addCall` session (late callback / stale watcher) disconnects
  itself instead of touching the current session's bookkeeping.
- **CallStyle falls back, never crashes**: shade CallStyle demands a
  Telecom call AT POST TIME — the API 31-33 direct-dial path has
  none, so `CallService` posts plain and `RingEvent` keeps the plain
  heads-up path. The shadowed tests pin both branches BUILDING; the
  platform's post-time rejection itself is device-train territory.
- **javap-first for platform/shadow APIs**: verify the real signature
  (android.jar or the Robolectric shadow class) before writing a test
  against it — `ShadowService.lastForegroundNotification` and friends
  are shadow API, not framework API (found building the CallService
  test, 2026-10-25).
- **Design tokens, not hardcoded colors**: the programmatic UI
  resolves `wp*` theme attrs (`values/attrs.xml`, light values in
  `values/colors.xml`, dark in `values-night/colors.xml`) through the
  `ui/Design.kt` helpers (`themeColor`, `iconButton`, `pillBackground`,
  `pulse`, …). Never hardcode a color in new UI — add a token instead.
  The call screen is deliberately ALWAYS dark (`Theme.WebPhone.Call` +
  fixed `call_*` colors); the shell chrome is deliberately fixed dark
  glass (it floats over arbitrary web pages). Edge-to-edge is on:
  every surface pads its own content from `WindowInsets` (and the
  insets listener must RETURN the insets, not the view).
- **UI automation needs `adb root` on API 36**: `am start` on a
  non-exported activity is denied for the shell uid (SecurityException)
  — run `adb root` first (works on the google_apis image), and pin
  every adb call with `-s <emulator-serial>` (the operator's phone may
  be attached to the same adb server).
- **TLS is NOT pinned (decision, 2026-10-25)**: the stack is
  Caddy-fronted with Let's Encrypt certs that ROTATE — pinning SPKI
  would brick every call at the next renewal. Trust is the platform
  TLS store + HTTPS-only (`usesCleartextTraffic="false"`); revisit
  only behind a long-lived internal CA.
- **API 31-33 outbound stays DIRECT-DIAL (decision, 2026-10-25)**: no
  Telecom surface outbound below 34 (`placeCall` is gone from the 36
  stubs, `addCall` needs 34+) — PERMANENT until minSdk 34, not a gap
  to fix. Inbound is fully Telecom-integrated on every API.
- **A ring push while AUTH-REJECTED must not ring** (W170): with no
  registration no INVITE can ever land, so `RingEvent` surfaces
  "Sign-in rejected" as a plain heads-up on the ring notification id
  (the server's `ring_end` still clears it). Transient states
  (OFF/CONNECTING/OFFLINE) still ring — the bootstrap race needs the
  benefit of the doubt.
- **The island page draws under the status bar in the shell** (found
  by the W01 emulator pass, 2026-10-25): the web header renders under
  the transparent status bar (the clock overlaps it) and its top
  controls are untappable in-shell (browser is unaffected). Cross-repo
  fix: the island needs `env(safe-area-inset-top)` handling, or the
  shell pads the WebView container. Untouched pending the webphone
  repo's call.
- **Launcher icon**: the adaptive icon leads with the handset glyph
  plus a monochrome layer (Android 13 themed icons). It deliberately
  DIVERGED from the PWA's link glyph — if the webphone repo ever wants
  icon parity, change BOTH or neither.
- dprint formats Markdown tables BETWEEN edits — re-view any `.md`
  file before `edit`/`multiedit` or exact matches fail. Run
  `nix run nixpkgs#dprint -- fmt` on touched `.md`/`.yml` files before
  finishing (CI runs `dprint check`).
