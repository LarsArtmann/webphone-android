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
source set (26 tests: protocol unit tests + Robolectric smoke) and a CI
workflow (never run — no git remote configured). First emulator smoke
(install + launch) passed 2026-10-24.

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

| Package  | Contents                                                                                                | Role                                                                                                       |
| -------- | ------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `shell/` | `MainActivity`, `CallLayer`, `SessionApi`, `DeviceIdentity`, `SettingsActivity`                          | The WebView shell + the idempotent call-layer wire + the origin's HTTP session seam + per-install identity + the persisted stack-origin settings |
| `sip/`   | `SipEngine`, `SipMessage`, `Digest`                                                                     | SIP-over-WSS user agent: REGISTER (digest), INVITE/re-INVITE/CANCEL/BYE, DTMF INFO, REFER/NOTIFY           |
| `call/`  | `CallEngine`, `WebRtcEngine`, `CallService`, `CallConnectionService`, `TelecomOutgoing`, `CallActivity`, `DialpadActivity` | Call orchestration (incl. registration state), WebRTC audio, typed FGS, inbound Telecom integration, outbound addCall bridge (API 34+), in-call UI, dialer entry |
| `push/`  | `RingEvent` (the router), `FcmReceiver`, `NtfySubscriptionService`                                      | The two ring-while-closed transports, both converging on `RingEvent.route`                                 |
| `ui/`    | `Fab` (styleAsFab)                                                                                      | The one floating-action look shared by the shell dial button and the dialer call button                    |

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
- **Stack origin**: persisted in Settings (`webphone-settings` prefs;
  `SettingsActivity` validates `https://` and tears the call layer
  down on save). The shipped default `https://pbx.example.org` is a
  NON-place; first run prompts for the real origin. Cleartext is
  disabled (`usesCleartextTraffic="false"`), so the origin must be HTTPS.
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
  viewBinding is enabled but the CallStyle-composed notification UI is
  planned, not present. `minSdk = 31`, `targetSdk/compileSdk = 36`.
- Intents carry the ring fact under `RingEvent.EXTRA_FROM` /
  `EXTRA_CALL_ID` action-namespaced extras.
- Dialer honesty: the Call button is disabled unless
  `CallEngine.registration` is `REGISTERED` (the dialer shows the
  same state the shell badge does); dialing unregistered would no-op
  silently.
- dprint formats Markdown tables BETWEEN edits — re-view any `.md`
  file before `edit`/`multiedit` or exact matches fail. Run
  `nix run nixpkgs#dprint -- fmt` on touched `.md`/`.yml` files before
  finishing (CI runs `dprint check`).
