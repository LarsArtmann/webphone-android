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
source set (138 tests: protocol unit tests + Robolectric smoke + the
deep-link/auth-detour surface + the wake-lane/session-watch verdicts,
ktlint-gated) and a CI workflow
(mirrored to GitHub 2026-10-25; the `push`/`pull_request`/
`workflow_dispatch` triggers DO create runs, but no job has ever
executed — the account's Actions billing needs an owner fix, see
FEATURES). First
emulator smoke (install + launch) passed 2026-10-24. Deep links
(tel/callto/sip/stack-https) and the in-shell OIDC auth detour SHIPPED
2026-10-27 and are emulator-proven (the 404-echo probe + the live
`begin`→Pocket-ID redirect); the live Pocket ID login itself still
awaits credentials (R17). **Update lane (2026-10-28; first releases
2026-11-11):** release APKs are signed with a dedicated key and
published to Forgejo releases (`scripts/release.sh`), which Obtainium
on the phone reads. The publish half is PROVEN: v0.2.1 (13:00) and
v0.2.2 are live, and v0.2.2 was verified from the OUTSIDE — anonymous
download byte-identical to the staged artifact, full battery on the
downloaded bytes (badging 202/0.2.2, 16 KB aligned, ABI gate green,
release-key signature = the v0.2.1 key). The device half stays
UNPROVEN: the phone runs a DEBUG-signed 0.2.1, so the first Obtainium
install needs a ONE-TIME UNINSTALL (release key ≠ debug key — the
0.2.2 release notes say so); after that, updates land in place. DEVICE DEPLOY
(2026-11-11): 0.2.1 (vc201) was adb-installed in place over the debug-signed
0.2.0 and verified (dumpsys + launch + liveness). REPO VISIBILITY (2026-11-11):
both remotes are PUBLIC now (GitHub via `gh repo edit`; Forgejo via API PATCH
`private=false`, anonymous GET verified on both). The release-lane token is
minted and stored: `~/.config/webphone-android/forgejo-token` holds a
`write:repository,read:user` token (CLI-minted on the forge host as user
`lars`; `--doctor` green: token valid, repo private=False). Superseded
`webphone-android-release` token (write:repository-only, no `read:user`,
never stored anywhere) is revocable in the Forgejo UI; the sops
`forgejo_token` in SystemNix is a literal `CHANGE_ME` placeholder, not a
real credential.

## Repository & mirrors

Forgejo is the primary (canonical) remote; GitHub is the off-site
mirror. Both are **public** (flipped 2026-11-11 from private; anonymous
read works, push credentials unchanged).

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
- **Mirror credential trap (RESOLVED 2026-11-11):** the sops
  `github_token` sync PAT used to lack `workflow` scope, so GitHub
  rejected mirror pushes containing workflow-file changes (the same
  trap froze `domains` at 114 commits behind as of 2026-10-25).
  EMPIRICALLY DEAD for this repo: commits touching
  `.github/workflows/ci.yml` (the ABI gate steps) mirrored to GitHub
  fine on 2026-11-11 (`scripts/mirror-lag.sh` → zero lag at a SHA that
  includes them; `git show github/main:.github/workflows/ci.yml`
  carries the ABI steps) — presumably the PAT was re-scoped owner-side.
  If a mirror push ever fails with "refusing to allow a Personal
  Access Token … without `workflow` scope" again, the fix is the old
  one: regenerate the PAT with repo+workflow scope, update the sops
  secret, redeploy.
- Mirror lag check (same probe as `domains`):
  `git fetch origin && git fetch github && git rev-parse origin/main github/main` —
  equal SHAs mean the mirror is keeping up.
- **NOTHING pushes ANY remote automatically** — the daemon commits locally
  only (verified 2026-10-25: both remotes lagged local while the mirror was
  unattached). UPDATE 2026-11-11: the push mirror IS ALIVE now (a plain
  `git push origin main` landed on `github` with no hand push — phase 5
  must have attached via a SystemNix deploy). Hand-push BOTH remotes
  anyway until `scripts/mirror-lag.sh` proves zero lag; the one exception
  is deliberate: `scripts/release.sh` pushes `main` and the release tag to
  `origin` as part of publishing.

## Build and verify

```console
nix develop                        # JDK 21 + Android SDK (platform 37, build-tools 36.0.0)
nix develop -c ./gradlew assembleDebug      # debug APK
nix develop -c ./gradlew assembleRelease    # minified + resource-shrunk (R8, proguard-rules.pro)
nix develop -c ./gradlew testDebugUnitTest  # JVM unit tests (protocol + Robolectric smoke)
nix develop -c ./gradlew ktlintCheck        # Kotlin style gate (official style, .editorconfig)
```

The verification gate after every code change: `assembleDebug` green +
`zipalign -c -P 16 4` on the debug APK + `testDebugUnitTest` green +
`ktlintCheck` green (CI runs all four; `ktlintFormat` fixes
mechanically). The one-command form is
`bash scripts/verify-gate.sh` — it refuses to start while another
Gradle process is alive, then runs assemble + tests + ktlint + the
KGP-parity check + the 16 KB
zipalign + abi + contrast + `dprint check` in order, each Gradle step
time-bounded so a wedged build fails loudly (it is the gate, wrapped;
run its steps by hand only when debugging one of them). After any
toolchain or dependency jump, add the emulator
train (`nix develop .#emulator -c bash scripts/emulator-train.sh`) — it
exercises the wired install/launch path the hermetic JVM suite
deliberately no longer touches. The contrast gate is one command away:
`python3 scripts/contrast-check.py` (CI runs it; run it after ANY token
change).

The universal-APK contract is gated too: `python3 scripts/abi-check.py
<apk>` (run by verify-gate.sh, release.sh, and CI on both artifacts)
refuses any APK where `libjingle_peerconnection_so.so` (the WebRTC JNI
bridge — the load-bearing lib) is missing from `arm64-v8a`,
`armeabi-v7a`, `x86`, or `x86_64` — a dependency swap or stray
`abiFilters` shrinks `lib/` silently (androidx libs alone would keep
the dirs non-empty), and the loss would otherwise surface only as a
crash on someone else's device class. There are NO per-ABI builds, and
"amd64-v8a" is not a real ABI (amd64 IS x86_64, already covered).

The two devShells split the SDK deliberately: the BUILD shell carries
platform 37 + build-tools 36.0.0 (compileSdk 37), while the `.#emulator`
shell stays on API 36 + its system image (the AVD every train script
drives) — an API 37 image is a TODO row, not an accident.

Release lane (the update path Obtainium consumes):

```console
nix develop -c bash scripts/release.sh --init-key   # once: key + props in ~/.config/webphone-android/
nix develop -c bash scripts/release.sh bump patch   # version.txt arithmetic
nix develop -c bash scripts/release.sh --doctor     # preflight + forge/token probe
nix develop -c bash scripts/release.sh              # gate, sign, tag, push, publish
```

`scripts/release.sh` owns the whole gate (`assembleRelease`,
`testDebugUnitTest`, `ktlintCheck`, `zipalign -c -P 16 4` on the SIGNED
APK, `apksigner verify`, `scripts/contrast-check.py`) and
`scripts/forgejo-release.py` owns the forge API (stdlib python3; the
devShell has no `curl`/`tea`). The version lives in `version.txt` —
never retyped in Gradle. After a publish, run `bump patch` and commit:
a tree that still claims an already-published version can never produce
the next update (Obtainium compares versions, Android compares
versionCodes).

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
  `webrtc` dependency bump; requires build-tools 35+ — the `-P` flag
  does not exist in 34). NOTE: `zipalign` is NOT on the devShell PATH
  (and `find $ANDROID_HOME` misses it through symlinked dirs) — use
  the full build-tools 36 path:

  ```console
  nix develop -c bash -c '"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk'
  ```

- Formatting: `dprint.json` covers JSON/YAML/Markdown/Dockerfile only.
  Kotlin style is ktlint (official style via the root `.editorconfig`,
  plugin `org.jlleitschuh.gradle.ktlint` 14.2.0, CI-gated);
  `ktlintFormat` fixes mechanically. detekt is REJECTED (stalled at
  1.23.8 since 2025-02, KGP 2.4 compat risk — re-evaluate when 2.x
  stabilizes).
- Dependency versions live exclusively in `gradle/libs.versions.toml`;
  module build files use `libs.*` aliases. `settings.gradle.kts` sets
  `FAIL_ON_PROJECT_REPOS` — never add repositories to `app/build.gradle.kts`
  (the root `buildscript {}` block is the sanctioned exception — see
  the KGP pin gotcha below).
- Dependabot handles version bumps.

## Architecture

Single `:app` module, package-per-concern under
`software.larsartmann.webphone`:

| Package  | Contents                                                                                                                   | Role                                                                                                                                                                                                       |
| -------- | -------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `shell/` | `MainActivity`, `WebSurface`, `DeepLink`, `CallLayer`, `SessionApi`, `DeviceIdentity`, `SettingsActivity`                  | The WebView shell (hardened surface in `WebSurface`) + the deep-link intake + the idempotent call-layer wire + the origin's HTTP session seam + per-install identity + the persisted stack-origin settings |
| `sip/`   | `SipEngine`, `SipMessage`, `Digest`                                                                                        | SIP-over-WSS user agent: REGISTER (digest), INVITE/re-INVITE/CANCEL/BYE, DTMF INFO, REFER/NOTIFY                                                                                                           |
| `call/`  | `CallEngine`, `WebRtcEngine`, `CallService`, `CallConnectionService`, `TelecomOutgoing`, `CallActivity`, `DialpadActivity` | Call orchestration (incl. registration state), WebRTC audio, typed FGS, inbound Telecom integration, outbound addCall bridge (API 34+), in-call UI, dialer entry                                           |
| `push/`  | `RingEvent` (the router), `FcmReceiver`, `NtfySubscriptionService`                                                         | The two ring-while-closed transports, both converging on `RingEvent.route`                                                                                                                                 |
| `ui/`    | `Design` (`themeColor`, `iconButton`, `pillBackground`, `pulse`, …)                                                        | The design-token system behind every native surface: theme attrs resolved programmatically, one helper vocabulary                                                                                          |

Unit tests live under `app/src/test/` mirroring the packages; Robolectric
tests MUST set `@Config(application = Application::class)` — the manifest
`App` initializes WebRTC's native factory, which throws
`UnsatisfiedLinkError` on the JVM.

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

Since 2026-11-10 that drift is BUILD-TIME-pinned: the server renders
byte-pinned consumer fixtures from its own types (webphone
`docs/contracts/wire/`, via `webphone contracts`), this repo vendors
them into `app/src/test/resources/wire/`, and `WireContractTest`
decodes each through the REAL parsers (`WireFacts`,
`SessionApi.parseSessionIdentity`, `SessionApi.registerPayload`) — a
renamed key on either side reds the JVM suite, never the first device
boot. The sync ritual: whenever the webphone contract tree changes,
run `scripts/sync-wire-fixtures.sh` (copy; `--check` is the drift
gate, exit 1) with `WEBPHONE_DIR` pointing at the server checkout
(default `../webphone`). The session answer carries the identity and
exactly ONE credential arm (`sip_credential` brokered / `password`
legacy); the SIP connection facts (`sipDomain`, `websocketPath`) come
from `/config.js` (`SessionApi.fetchIslandConfig`) — an answer without
config facts or a credential is NOT a registerable identity (the
honest-null pin; the empty-credential REGISTER that silently never
rang is what the 2026-11-10 drift fix ended).

The AUTH seam (webphone `internal/sso` + `internal/server/sso_api.go`):
the island's login card links `GET /api/auth/oauth2/begin` (same-origin
anchor), which 302s to the OIDC IdP (Pocket ID) and back to
`GET /api/auth/oauth2/callback`, which mints the session cookie and
303s to `/`. The AUTH DETOUR policy in `WebSurface` exists to serve
exactly this dance (see Gotchas). App-Link verification needs the
stack to serve `/.well-known/assetlinks.json` — cross-repo follow-up,
tracked in TODO_LIST.

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
- **`CAPABILITY_SELF_MANAGED` keep-decision (compileSdk 37, 2026-10-28)**:
  the CONSTANT is deprecated on the 37 stubs, but self-managed remains
  THE integration (inbound Telecom on every API) and 37 ships no
  replacement. `CallConnectionService.accountHandle` suppresses the
  one warning on purpose — do not "fix" it by dropping self-managed.
- **`onBackPressed` super-call keep-decision (2026-10-28)**: the API
  31/32 tail in `MainActivity` calls the deprecated
  `super.onBackPressed()` on purpose (predictive back owns 33+); the
  override carries `@Suppress("DEPRECATION")` — that warning stays
  suppressed, not fixed, until minSdk 34.
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
- **The wake lane has a UI verdict + a flap-safe probe (2026-11-10
  no-ring diagnosis)**: the line pill suffixed registration with
  `· wake on/off` (the `push/WakeLane` ledger; reasons ride the
  contentDescription) BECAUSE every wake-lane failure used to be
  logcat-only — a seam-off server, a refused registration, a dead
  session, and a dropped socket all looked identical from the couch:
  silence. The session watcher tears down ONLY on a definitive
  401/403 (`SessionApi.probeSession` tri-state; the old boolean probe
  treated transport failure as logout, so one tunnel flap silenced
  the phone FOREVER — the lane is torn down and nothing re-arms until
  reopen); a dead session also posts the "Ringing paused"
  notification (id 1004). Never reintroduce a boolean session probe.
  AND: the phone can still legitimately stay silent — the SERVER side
  is off by default (the FS dialplan's ring hook arms only with
  `ringhook.enable` on the consuming stack, and `push.ntfy.*` must be
  configured; webphone TODO_LIST 2026-11-08 says the E2E ring proof
  is STILL OPEN) — a `Connected · wake off` pill with register-404 in
  logcat means exactly that stack-side gap.
- **Mic FGS background-start ban**: Android 15+ forbids starting a
  `microphone` foreground service from the background. `CallService`
  is only ever started from user interaction (the answer flow through
  `CallActivity` is the legitimate launch path).
- **FCM lane is dormant by default**: `firebase-messaging` is a plain
  dependency with no `google-services` plugin and no
  `app/google-services.json`. The ntfy lane (`NtfySubscriptionService`)
  is the zero-Google default; a GMS build drops in the JSON and enables
  the plugin. firebase-messaging 26 deprecates `getToken()` AND
  `onNewToken` (the v1-registration world: `register()` + callback) —
  both stay suppressed-on-purpose (`CallLayer.registerPushLanes`,
  `FcmReceiver`) until the lane goes live; 26.0.1 ships no replacement
  callback, and a blind API flip on a lane that cannot fire is
  unverifiable (decision 2026-10-28).
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
- **AGP 9 built-in Kotlin** (since the 2026-10-28 toolchain jump:
  Gradle 9.8.2 + AGP 9.4.1 + Kotlin 2.4.21): the
  `org.jetbrains.kotlin.android` plugin is GONE from both plugin
  blocks (incompatible with built-in Kotlin) and `android.kotlinOptions{}`
  no longer exists (jvmTarget follows `compileOptions.targetCompatibility`).
  "Latest Kotlin" rides the root `build.gradle.kts` classpath pin
  `kotlin-gradle-plugin:2.4.21` — its version MUST equal the catalog's
  `kotlin` (kotlin-test aligns to it); change BOTH or neither.
  `android.builtInKotlin=false` is a temporary escape hatch that dies
  in AGP 10 — never plan around it.
- **`kotlin("test")` is gone** (it needed the KGP plugin DSL): the
  catalog entry `kotlin-test` points at `kotlin-test-JUNIT` (not the
  bare `kotlin-test` artifact — an aggregator whose JVM redirect the
  built-in toolchain's compiler does not honor; `kotlin.test.Test`
  came up UNRESOLVED with the bare artifact).
- **Robolectric activity tests NEVER use `ActivityScenario`**: its
  `close()` parks in a wait-for-DESTROYED loop ON the Robolectric main
  thread, so a bare launch of the singleTask shell can never be
  destroyed — the executor sat at ~20 s CPU forever (the hours-long
  wedge, jstack-proven 2026-10-28). Use the synchronous
  `Robolectric.buildActivity(...).setup()` + `pause().stop().destroy()`
  (see `DeepLinkIntakeTest`).
- **Unit tests must never phone the live stack**: MainActivity's
  onCreate/resume calls `CallLayer.ensureWired`, which used to start a
  REAL session-HTTP + SIP-WSS + ntfy-websocket wire from every
  Robolectric launch. `CallLayer.wiringEnabled = false` (test seam,
  per-sandbox classloader so it cannot leak) gates it off in
  `DeepLinkIntakeTest`/`MainActivityLaunchTest`; the ntfy reconnect
  threads are DAEMON so a leaked sleep can never hold the executor
  JVM. The same hermeticity rule now covers the watcher teardown:
  `onSessionDead`'s server-row DELETE rides the injected
  `CallLayer.deregisterPushDevice` seam (a Robolectric pin asserts the
  CALL, never the HTTP). The wired path is exercised on the emulator
  train only.
- **A test run hangs silently for hours WITHOUT test logging**: the
  suite now prints per-test outcomes (`testOptions.unitTests.all {
  testLogging }`) — a stalled run is visible as a missing line.
  Pre-flight every build: `pgrep -f gradle` (the `crush-daily`
  scheduler dispatches agent sessions that run the gate
  `testDebugUnitTest assembleDebug` on this repo — NEVER run a
  concurrent build), and stream long builds to a log file instead of
  piping through `tail` (which buffers until exit).
- **An update IS a release tag plus an APK asset**: Obtainium's Forgejo
  source reads `GET /api/v1/repos/{owner}/{repo}/releases`
  (`per_page=100`), takes `tag_name` as the available version (falling
  back to the release name) and installs the `.apk` attachment. Nothing
  in this repo can make an update appear without both; a tag with no
  asset is invisible, and an APK with no release is unreachable. The
  file name is otherwise free — one universal APK (45 MB, all ABIs)
  means no ABI filter is needed (ABI completeness is GATED:
  `scripts/abi-check.py`), and `webphone-android-<version>.apk`
  keeps the version visible in the asset list.
- **version.txt is the version; versionCode is derived**: versionName is
  the file's content, versionCode is `major*10000 + minor*100 + patch`
  (0.2.0 → 200). Android only accepts an APK whose versionCode is
  strictly higher than the installed one, and Obtainium compares the
  release tag against the installed versionName — the two must move
  together, which is why both come from one line. Never reintroduce a
  literal `versionCode`/`versionName` in `app/build.gradle.kts`.
- **Release key lives in `~/.config/webphone-android/`** (`release.keystore`
  - `release.properties`, chmod 600, written by
    `scripts/release.sh --init-key`). Losing it is not a build problem: every
    installed device must UNINSTALL before it can take the next update
    (Android refuses a signing-key change). Back both files up. No key →
    `assembleRelease` still emits `app-release-unsigned.apk` and CI stays
    green, so the absence is silent until someone tries to install.
- **The debug build and the release build are different packages to
  Android**: the currently sideloaded APK is debug-signed (per-machine
  key), so switching to the release key needs one manual uninstall. That
  is a one-time cost, not a bug to "fix" by signing releases with the
  debug key (a debug key is guessable, so anyone could then ship a
  same-package update; the App-Link `assetlinks.json` fingerprint is also
  per-machine and would break).
- **Obtainium cannot validate the forge's certificate**: Forgejo is
  fronted by Caddy with a leaf minted from the internal `dnsblockd` CA
  (`CN=home.lan`, SANs `home.lan`/`larsartmann.cloud`), and Android 7+
  apps do not trust user-installed CAs. The working configuration is the
  per-app option **"Allow insecure HTTP requests"** ON (Obtainium maps it
  to `badCertificateCallback => true`), so the token and the APK travel
  over an unverified TLS session — acceptable for a LAN/VPN forge, NOT a
  pattern to copy for the public stack origin.
- **The forge is LAN/VPN-only**: `forgejo.home.lan` resolves on the home
  network, `forgejo.larsartmann.cloud` via NetBird (never public DNS).
  Obtainium checks therefore only succeed while the phone is home or on
  the VPN; there is no public update path until the forge gets a
  publicly-trusted endpoint (the internal CA exists precisely because
  the zone is private).
- **Forgejo facts that bite the upload** (probed 2026-10-28, Forgejo
  15.0.10): `GET /api/v1/settings/attachment` reports `max_size` 2048 MiB
  and `max_files` 5 — a 45 MB APK is fine, and the `attachment.MAX_SIZE`
  setting in the SystemNix forgejo module is the knob if that ever
  shrinks. The same endpoint's `allowed_types` list has no `.apk`; release
  assets are uploaded through `POST /releases/{id}/assets?name=…`
  (multipart field `attachment`) and have not been rejected on type, but
  if a future Forgejo starts enforcing the list, add `.apk` there. Tokens
  need `write:repository,read:user` to publish (the release script's
  preflight calls `GET /api/v1/user` — write:repository alone 403s there,
  proven 2026-11-11), and `read:repository` for the phone (OPTIONAL since
  the repo went public: anonymous release reads work).
- **Forge "No known key found for this signature in database" on signed
  commits (2026-11-11)**: every commit here is SSH-signed
  (`gpg.format=ssh` in `~/.config/git/config`), and the badge stays
  UNVERIFIED until two independent gates pass — either one failing alone
  renders the same string: (1) the commit's committer email must be an
  ACTIVATED email on the forge account (`GetUserByEmail` gates the whole
  key loop; the account carried only `lars@local` while commits use
  `git@lars.software`), and (2) the signing key needs the
  challenge-verified flag — `parseCommitWithSSHSignature` skips keys with
  `verified=false`, and that flag flips ONLY via the web flow
  (Settings → SSH/GPG Keys → Verify: sign the page's token with
  `ssh-keygen -Y sign -f <keyfile> -n gitea`, paste the armored block;
  the signature must cover the server-derived time-windowed token, so it
  cannot be pre-generated). The v1 API cannot do either leg:
  `POST /user/gpg_key_verify` is GPG-only, and adding emails needs
  `write:user` (the release token is `write:repository,read:user` — 403
  proven 2026-11-11).

## Conventions

- Prose-heavy KDoc explaining WHY (decisions, tradeoffs, cross-repo
  context), trailing commas, explicit `Unit` in no-op overrides,
  `runCatching` with explicit defaults at IO seams, `StateFlow` for
  observable state. Match this style.
- Engines and seams are `object` singletons (`CallEngine`,
  `WebRtcEngine`, `SessionApi`, `RingEvent`); per-call/per-connection
  state is nested classes (`CallMedia`, `Leg`).
- Notification IDs: 1001 = incoming ring, 1002 = call FGS, 1003 = ntfy
  FGS, 1004 = dead-session notice (`CallLayer.onSessionDead`). Channels
  `calls` (high, bypass-DND), `push` (min), and `account` (default — a
  "Ringing paused" notice wants sound-but-not-alarm weight, and the
  tap-through into the shell needs a real content intent) are created
  in `App.onCreate`.
- UI is programmatic in v0.1 (`CallActivity` builds views in code);
  viewBinding is enabled. `minSdk = 31`, `targetSdk = 36`,
  `compileSdk = 37` (the 2026-10-28 toolchain jump moved compileSdk;
  targetSdk deliberately stays 36 until a device train validates
  Android 17 behavior changes).
- Intents carry the ring fact under `RingEvent.EXTRA_FROM` /
  `EXTRA_CALL_ID` action-namespaced extras.
- Dialer honesty: the Call button is disabled unless
  `CallEngine.registration` is `REGISTERED` (the dialer shows the
  same state the shell badge does); dialing unregistered would no-op
  silently.
- Cross-repo filings (issues/PRs to OTHER repos) are drafted in
  `docs/drafts/` inside THIS repo (never /tmp), filed from the real
  file (`gh ... --body-file`), and the draft is kept after filing —
  the draft is the in-repo record of what was filed and why.
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
- **Deep-link intake**: `MainActivity` is the ONLY exported VIEW
  surface (singleTask — cold links in `onCreate`, warm ones in
  `onNewIntent`, both one-line delegates into `routeDeepLink`).
  `tel:`/`callto:`/`sip:` open `DialpadActivity` PRE-FILLED (the Call
  button still gates on registration — dialer honesty holds on every
  entry path); same-origin https loads in the shell via
  `WebSurface.loadUri` (verdict re-checked = defense in depth);
  foreign links open the app home, NEVER the WebView and never a
  re-hand-off. The manifest https filter's host is PAIRED with
  `App.DEFAULT_ORIGIN` (`pbx.artmann.tech`) — change both or neither;
  `autoVerify` needs `assetlinks.json` from the server repo
  (TODO_LIST). Never use a wildcard host (chooser spam for every
  https link on the device).
- **The auth detour (in-shell OIDC)** — CORRECTED MODEL (R03,
  emulator-proven 2026-10-27; the session-1 premise was WRONG):
  modern Chromium DOES consult `shouldOverrideUrlLoading` for SERVER
  redirects — the island's `/api/auth/oauth2/begin` 302 lands in the
  callback WITH the IdP URL (`request.isRedirect == true`). Only
  cross-origin form POSTs arrive unjudged. So the detour has TWO
  bless seams, one trust argument: (1) `navigateOut`'s redirect seam
  — a MAIN-FRAME server redirect while no detour is live is, by
  construction, the origin's own chain → bless that authority and
  keep it in-shell (clicks, `isRedirect=false`, still hand off — W105
  stays true); (2) the commit-side hooks (`doUpdateVisitedHistory`
  - the `onPageStarted` belt, idempotent) catch the unjudged POST
    arrivals. While the detour is live, `navigationVerdict` lets that
    ONE authority navigate in-shell so the Pocket ID dance completes
    (without it, the IdP's own links eject the login to Chrome and the
    session mints in the WRONG cookie jar). One authority at a time
    (replace, not union); an origin commit/start ends the detour;
    `retry()` clears it explicitly (an offline retry never commits, so
    the hooks alone would leave a stale blessing). Downloads NEVER ride
    the detour (no cookie to a foreign host), and the session cookie
    never leaves the origin (per-authority cookie scoping, third-party
    cookies off). On-device proof: deep-link the begin URL → logcat
    `WebSurface: auth detour blessed via redirect: auth.larsartmann.cloud`
  - the Pocket ID pages render in the shell, no Chrome task.
    Multi-host IdPs would need a different allowance (operator question
    stands). Keep this shape — it is what makes "same-origin only" and
    "OIDC works" true at the same time.
- **The IdP is passkey-ONLY and the WebView must opt into WebAuthn
  (2026-11-11, the "OAuth broken / fingerprint re-login" diagnosis)**:
  Pocket ID supports ONLY passwordless sign-in — the "Authenticate"
  button IS the login (fingerprint/PIN unlock), there is no password
  door (only a one-time admin-issued login code). Android WebView ships
  with WebAuthn support DISABLED by default; the provider has supported
  it since Chromium M131 but ONLY after the app opts in:
  `WebSurface.optInToPasskeys` sets
  `WEB_AUTHENTICATION_SUPPORT_FOR_APP` (androidx.webkit 1.18) when
  `WebViewFeature.WEB_AUTHENTICATION` is supported, guarded +
  `runCatching` (Robolectric has no provider; a too-old provider
  degrades to the dead button instead of crashing). The verdict is
  LOGGED (`webauthn in webview: …`) — a device train reads it from
  logcat, never from a silent button. EMULATOR-PROVEN 2026-11-11: the
  API 36 AVD's WebView provider (com.google.android.webview
  133.0.6943.137, ≥ M131) answers `FOR_APP enabled (provider
  readback=1)` and the live island still loads — no crash, no
  regression. FOR_APP (not FOR_BROWSER — this
  shell is not a browser) also means Credential Manager serves the
  ceremony only for RP hosts that prove the app via Digital Asset
  Links: `auth.larsartmann.cloud` (Pocket ID's RP ID) must serve
  `/.well-known/assetlinks.json` with the `get_login_creds` relation +
  the RELEASE cert fingerprint — 404 there as of 2026-11-11, so the
  stack half is still open (TODO_LIST row; the draft is
  `docs/drafts/2026-11-11_stack-serve-assetlinks-on-idp-host-for-webview-passkeys.md`,
  `scripts/assetlinks.sh` emits both relations). Until that lands, the
  in-shell login STILL dead-ends at the RP check — the app half alone
  is necessary, not sufficient. "Stay logged in" was never broken:
  the server's session cookie is persistent (7-day sliding idle,
  30-day absolute cap) and the app flushes the cookie jar on
  page-finish/pause — what LOOKED like logout problems were forced
  logins (app-data wipes, the 30-day cap) with no possible re-login.
- **The debug keystore lives at `~/.config/.android/debug.keystore`
  (found 2026-10-27)**: the nix JDK resolves AGP's debug keystore via
  the XDG config home, so the CLASSIC `~/.android/debug.keystore` can
  exist as a DECOY with a different cert (here it does). Never read
  fingerprints for assetlinks by hand — `scripts/assetlinks.sh`
  discovers the real keystore and REFUSES to emit when its cert
  doesn't match the built APK's (apksigner cross-check).
- **App-link resolution reality (R02 train, 2026-10-27)**: until the
  server serves `assetlinks.json` and verification flips, an IMPLICIT
  `https://pbx.artmann.tech/…` VIEW intent resolves to CHROME
  (unverified `autoVerify` loses to the browser — resolver evidence:
  `cmd package query-activities` ranks the app #0, but `am start`
  still picks Chrome). On an emulator, simulate the post-R18 state:
  `adb shell pm set-app-links-user-selection --user 0 --package
  software.larsartmann.webphone true pbx.artmann.tech` — then
  implicit stack links land in the app. `sip:`/`callto:` resolve to
  us directly (sole handler); `tel:` shows the system resolver until
  the user sets a default (the Google Dialer also claims it).
- **rg `-r` is NOT recursive (three strikes: 2026-10-27 and prior)**:
  `-r` is the replace-display flag. The recursive search is `-n`
  (lines) / `-l` (files) — e.g. `rg -n pattern` or plain `rg pattern`.
  If a search returns nothing and you passed `-r`, you searched
  NOTHING — the flag silently changed the command's meaning.
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
  be attached to the same adb server). For TAP coordinates, never
  scale from screenshots — the estimate drifted >100 px on the
  resolver sheet (R02 train, 2026-10-27): `adb shell uiautomator dump`
  and read the exact `bounds` of the target node instead.
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
- **Wake-lane refusal vocabulary (2026-11-11, meta-proven pins)**:
  `WakeLane` is a 9-state ledger — UNKNOWN, STARTING, ARMED, four
  NAMED refusals (`REFUSED_SEAM_OFF`/`REFUSED_INVALID`/`REFUSED_AUTH`/
  `REFUSED_OTHER`), SESSION_DEAD, DISCONNECTED. Two rules keep it
  honest: (1) a refusal STICKS until the next wire attempt —
  `reportSocketState` skips ARMED/DISCONNECTED/STARTING while a
  REFUSED_* verdict stands (the baseline let a socket-open overwrite
  REFUSED with ARMED on a seam-off server: a lie); (2) a signed-OUT
  shell stays UNKNOWN by design — `ensureWired` bails at
  `resumeSession()` BEFORE the lane machinery starts, so "Not
  connected" (not a refusal) is the honest no-session state. The
  refusal→HTTP mapping: 404 seam-off, 422 invalid-or-device-list-full,
  403 session/CSRF, transport → DISCONNECTED (never a refusal — a
  tunnel flap is a link problem).
- **Push-register capacity is 422, NOT 409** (verified in webphone
  `internal/server/api`, 2026-11-11): the plan-era "cap-409" premise
  was wrong — `storeAtCapacity` answers `StatusUnprocessableEntity`,
  the SAME code as an invalid body/transport/token. The app cannot
  distinguish "invalid" from "list full" by status; the pill text
  carries both ("invalid… or its device list is full"). `/api/csrf`
  needs NO session (apidocs) — a csrf-null verdict means LINK, not
  auth.
- **Offline overlay dismissal is SUCCESS-GATED (2026-11-11,
  emulator-proven)**: the overlay hides ONLY on `onPageFinished` for
  a load whose `mainFrameLoadFailed` flag is false. The old rule
  (hide at `onPageStarted`) flashed Chromium's raw error page
  ("Webpage not available", `net::ERR_INTERNET_DISCONNECTED`) for the
  whole attempt window on every offline retry — airplane mode on the
  emulator showed the overlay up AND the raw page under it, ready to
  surf through on Retry. Never reintroduce a load-start dismissal;
  `retry()`/`loadUri()`/`loadOriginIfChanged()` only re-resolve the
  overlay body text now. Pins: `WebSurfaceTest` (meta-proven).
- **The wake-off detail sheet's Retry is a REWIRE, not an
  ensureWired** (2026-11-11): a refused lane is wired-but-attached
  (the guard holds), so plain `ensureWired` would no-op — Retry must
  tear down (`CallLayer.rewire` = shutdown + ensureWired) and let the
  ledger return to UNKNOWN while re-deciding. The sheet's body and
  its "Last checked" stamp never share a sentence (UNKNOWN has its
  own body string; `wake_never_checked` is the stamp's only). The
  probe stamp lands at the register verdict AND every watcher tick
  (`WakeLane.reportProbe` — the seam `nowMs` is frozen in pins).
- **The phone's WiFi DNS reality (found 2026-11-11)**: the Edge 60's
  home WiFi (`UPC504705697`) DHCP-hands the ISP resolvers
  (193.47.158.254/255, domain `play.pl`) — `*.home.lan` names do NOT
  resolve on this segment, so Obtainium forge checks (and anything
  else aiming at the LAN zone) only work on the dnsblockd LAN or
  NetBird. Diagnose "forge unreachable on WiFi" here first. Same
  recon: the phone's POST_NOTIFICATIONS was UNGRANTED and the battery
  whitelist EMPTY until adb-granted 2026-11-11 (`pm grant` +
  `dumpsys deviceidle whitelist +`) — re-check both on the next
  "didn't ring" report (`scripts/ring-diagnose.sh` covers them).
- **Deploy-day phone recipe (proven 2026-11-11)**: (1) cross-check the
  SIGNING CERT before any in-place update — pull the phone's `base.apk`,
  `apksigner verify --print-certs`, compare against
  `~/.config/.android/debug.keystore`; a mismatch means uninstall-first,
  not `install -r`. (2) The phone is fingerprint-locked: `wm
  dismiss-keyguard` is a no-op on a secure lock and that is correct —
  NEVER try to bypass it. Visual UI verification needs an unlock window;
  without one, prove liveness with `pidof` + a screencap of the lock
  screen (the app's notification icons are visible there). (3) Wake the
  screen (`input keyevent KEYCODE_WAKEUP`) BEFORE launching via monkey —
  launching onto an off screen wastes the first screenshot. (4) A
  sideload deploy SPENDS a version number: Obtainium compares the forge
  release tag against the installed versionName, so publishing a version
  equal to an already-sideloaded build reads as up-to-date on the phone
  (0.2.1 is now spent this way — first-release numbering is a §g
  question, report 2026-11-11_11-43).
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
  finishing (CI runs `dprint check`). CAVEAT (found 2026-11-11): the
  EXPLICIT-FILE form (`fmt CHANGELOG.md`) no-ops with "No files found
  to format with the specified plugins" while the bare `fmt`/`check`
  forms work — format with the bare form and verify with `-- check`,
  never trust the explicit-file form's silence.
- **BuildFlow's nix-fmt roundtrip needs a `formatter` output (2026-11-11,
  the first green run)**: buildflow copies the flake to a `/tmp` tree and
  runs `nix fmt` there, which invokes the formatter BARE. Plain `nixfmt`
  refuses bare invocation (empty-stdin parse error), its `.` directory
  mode is deprecated, and `pkgs.nixfmt-tree` pins its tree root to the
  STORE path it lives in (dies with "path . not inside the tree root
  /nix/store" inside the roundtrip copy). The working shape is the
  flake's own `writeShellScriptBin` that walks `*.nix` files via
  `find` when handed no arguments. Keep flake.nix nixfmt-clean (the
  formatter is wired into the gate now).
- **Fleet policy gates went live via buildflow (2026-11-11)**: `cue-vet`
  requires a top-level `permissions:` block in ci.yml (cue-policy
  #CIWorkflow — CI never publishes, `contents: read` is the floor), and
  `securitymd` requires a `SECURITY.md` with Supported Versions +
  Security Practices sections plus a reporting link carrying a
  response-time line. Both fail a repo from nothing; they are fleet
  policy, not this repo's invention.
- **Daemon-blob remedy (2026-10-28)**: when the auto-commit daemon
  sweeps a finished pre-push tree into a `chore: auto-commit` blob,
  amend it in place — but ONLY after verifying (a) `git show --name-only
  HEAD` holds solely this session's paths, (b) both remotes lag HEAD
  (`git rev-parse origin/main github main HEAD`), (c) the fold-in diff
  is understood. Before ANY reset (soft included), print
  `git rev-list --count origin/main..HEAD` — resetting to a LAGGING
  origin folds every unpushed commit into the squash (the 2026-10-28
  over-broad-squash incident: five real commits folded, repaired via
  `git commit-tree` with zero loss because nothing had been pushed).
  Never amend after a push; better, hand-commit finished units
  immediately so the daemon never gets the chance.
- **A new test that pins a fix is VACUOUS until meta-proven (2026-10-28,
  the ntfy stop-leak pin)**: break the fix, watch the pin FAIL, restore,
  watch it pass — the pin's first design (relying on websocket
  close-handshake latency under Robolectric) passed with both stopped
  belts deliberately broken, because close-handshake latency is
  seconds-scale and non-deterministic under the looper. Drive test seams
  directly; never wait on network events under Robolectric.
- **Root build-script tasks under the configuration cache**: capture
  `Files` BY VALUE inside a `run { }` scope — `Project` capture and
  top-level script properties both fail the cache (`kgpParityCheck`'s
  two hard-won lessons, 2026-10-28).
- **Coverage claims need a mechanical cross-check**: any plan/doc line
  like "ALL todos covered / nothing dropped" must be verified by a
  row-by-row TODO↔plan diff before delivery — the 02:56 SUPERB plan
  shipped with 5 TODO rows missing despite the claim and needed a
  post-hoc repair (`7574c79`). Row counts are not coverage proof.
