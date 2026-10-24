# webphone-android

The Android native-call shell for [webphone](https://github.com/LarsArtmann/webphone)
(the server + web island live in that repo — this app is only the call
contract). Kotlin natively owns: SIP-over-WSS signaling, WebRTC call
audio, Telecom self-managed integration, and ring-while-closed push.
Every other surface (Messages, Fax, Voicemail, History, Contacts,
Settings) is the stack origin's own web pages in a session-sharing
WebView. Single-call by product decision: a second call waits, it never
silently replaces.

**Status: v0.1 scaffold.** Load-bearing code is implemented but
untested against live infrastructure (the README "honesty contract"
section lists the markers). There is no test source set, no CI, and no
Kotlin formatter/linter configured — don't invent test commands; there
are none.

## Build

```console
nix develop                        # JDK 21 + Android SDK (platform 36, build-tools 34+35)
./gradlew assembleDebug            # debug APK
./gradlew assembleRelease          # minified + resource-shrunk (R8, proguard-rules.pro)
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

| Package  | Contents                                                                             | Role                                                                                      |
| -------- | ------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------- |
| `shell/` | `MainActivity`, `SessionApi`, `DeviceIdentity`                                       | The WebView shell + the origin's HTTP session seam + per-install identity                 |
| `sip/`   | `SipEngine`, `SipMessage`, `Digest`                                                  | SIP-over-WSS user agent: REGISTER (digest), INVITE/re-INVITE/BYE, DTMF INFO, REFER/NOTIFY |
| `call/`  | `CallEngine`, `WebRtcEngine`, `CallService`, `CallConnectionService`, `CallActivity` | Call orchestration, WebRTC audio, typed FGS, Telecom integration, in-call UI              |
| `push/`  | `RingEvent` (the router), `FcmReceiver`, `NtfySubscriptionService`                   | The two ring-while-closed transports, both converging on `RingEvent.route`                |

### Control flow

1. **Session**: `MainActivity` loads `App.origin` in the WebView; the
   island's login mints the session cookie. `SessionApi.resumeSession()`
   (GET `/api/session`) trades the cookie for SIP credentials and wires
   `SipEngine` into the `CallEngine` singleton. Retried on every
   `onResume` (login may have just happened).
2. **Outbound call**: `CallEngine.dial` → WebRTC offer →
   `SipEngine.invite` → 180/200/ACK ladder → `onCallEstablished` applies
   the remote answer.
3. **Inbound call (ring while closed)**: push arrives (FCM data message
   or ntfy websocket JSON, same `{event, from, call_id}` payload) →
   `RingEvent.route` → `telecom.addNewIncomingCall` (self-managed
   connection) + full-screen-intent notification → `CallActivity` →
   `CallEngine.answer`.
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
- **`App.origin`** is a hardcoded `@Volatile var` in `App.kt`
  (`https://pbx.example.org`) — the settings UI doesn't exist yet; set
  it before any real device run. Cleartext is disabled
  (`usesCleartextTraffic="false"`), so the origin must be HTTPS.
- **Two cookie stores, no sync (v0.1 gap)**: the WebView uses Android's
  `CookieManager`; `SessionApi` uses its own in-memory OkHttp
  `CookieJar`. Nothing copies between them yet — the session seam is
  acknowledged-untested scaffold.
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
  names (MD5 or SHA-256), like the island's sip.js does.
- `POST_NOTIFICATIONS` is declared in the manifest but no runtime
  request code exists yet — expected to matter on the first device
  train (API 33+).

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
