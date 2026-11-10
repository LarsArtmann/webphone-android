# webphone-android

The Android native-call shell for
[webphone](https://github.com/LarsArtmann/webphone) — the P1 verdict of
the platform decision (2026-10-23): **calls are the product**, and this
shell re-owns the island's call contract natively while the tab
surfaces (Messages, Fax, Voicemail, History, Contacts, Settings) stay
the stack's own web pages in a session-sharing WebView.

## What runs where

| Layer                                                                                                                                   | Home                                           | Why                                                                                                                                                                                          |
| --------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Call signaling (SIP over WSS: REGISTER, INVITE, hold re-INVITE, BYE, DTMF `application/dtmf-relay` equals-form, REFER + NOTIFY sipfrag) | `sip/SipEngine.kt`                             | The browser engine cannot ring a closed app; Kotlin owns the leg.                                                                                                                            |
| Call audio (WebRTC, libwebrtc AudioProcessing)                                                                                          | `call/WebRtcEngine.kt` (stream-webrtc-android) | The same AEC/NS desktop Chrome ships — best in class on Android.                                                                                                                             |
| OS call integration (self-managed Telecom ConnectionService, typed `microphone\|phoneCall` FGS, full-screen intent)                     | `call/*`                                       | Ring while CLOSED (the hard requirement), screen-off survival, BT/watch answer.                                                                                                              |
| Ring-while-closed push                                                                                                                  | `push/*`                                       | BOTH transports by owner decision: FCM (GMS devices) + ntfy websocket subscription (de-Googled). The server fan-out lives in webphone `internal/push` (`/hooks/ring` → `/api/push-devices`). |
| Every other surface                                                                                                                     | The WebView into the stack origin              | 100% reuse of the proven island + tab partials; the session cookie is the seam.                                                                                                              |

## Status: v0.1 scaffold, first device smoke done

The architecture is complete and the load-bearing parts are
implemented; the markers below are the honesty contract — what is
**untested against live infrastructure** until a device train against
a real stack:

- SIP REGISTER/INVITE/BYE/DTMF/hold: implemented, **not yet exercised
  against the stack's WSS gateway** (Caddy-fronted `/sip`). The
  protocol layer itself (message codec, digest credentials against the
  RFC 2617 §3.5 vector, CANCEL/BYE/487 wire sequences) is unit-tested.
- Incoming INVITE answering and REFER/NOTIFY transfer: implemented in
  the dispatch, **explicitly UNTESTED** against live FreeSWITCH.
- Ring-while-closed E2E (push → FSI → answer): implemented, untested
  against the live push fan-out.
- Install/launch/configure smoke: **VERIFIED on the API 36 emulator**
  (2026-10-24): debug and R8 release builds install, launch, render,
  persist the origin, and show the honest offline state. Find a real
  bug the shadows could not see — see `docs/status/`.
- stream-webrtc-android 16 KB page alignment: **VERIFIED at 1.3.10**
  on BOTH debug and release APKs (`zipalign -c -P 16 4`, build-tools
  35). Re-verify per release bump (the check needs build-tools ≥ 35 —
  the `-P` flag does not exist in 34).
- The `/api/session` + `/api/push-devices` contracts mirror the server
  seam that landed 2026-10-23 (webphone `internal/push`). Since
  2026-11-10 the drift is BUILD-TIME-pinned: the server renders
  byte-pinned fixtures from its own types, this repo vendors them
  (`scripts/sync-wire-fixtures.sh`) and `WireContractTest` decodes
  each through the real parsers — a renamed key reds the JVM suite,
  never the first device boot (the historical `sip_password` drift —
  REGISTER on empty credentials — was found and fixed by exactly this
  train).

## Build and verify

```console
nix develop                        # JDK 21 + Android SDK (platform 37, build-tools 36.0.0)
nix develop -c ./gradlew assembleDebug      # debug APK (debug-signed, installable)
nix develop -c ./gradlew assembleRelease    # minified + resource-shrunk (R8); signed when a release key exists
nix develop -c ./gradlew testDebugUnitTest  # protocol + Robolectric tests
nix develop -c ./gradlew ktlintCheck        # Kotlin style gate
nix develop .#emulator -c bash scripts/emulator-train.sh   # self-serve device smoke
```

Release signing reads `~/.config/webphone-android/release.properties`
(written once by `scripts/release.sh --init-key`; the keystore lives next
to it, never in the repo). Without that file `assembleRelease` still
builds — it just emits `app-release-unsigned.apk`, which no device will
install; that is what keeps CI and fresh checkouts green.

The FCM lane activates when `app/google-services.json` exists (drop
in + add the google-services plugin); the ntfy lane works with zero
Google dependencies.

Set the stack origin in the app's Settings screen (persisted; first
run prompts for it).

## Distribution and updates

Sideload first (the `USE_FULL_SCREEN_INTENT` default grant stays
enabled for sideloaded calling apps — the favorable lane; Play would
re-audit it). Play later, if ever.

Releases are published to the Forgejo forge and pulled by
[Obtainium](https://obtainium.imranr.dev/), so a phone update needs
neither USB nor wireless debugging.

### Install once, update forever (Obtainium)

1. In Forgejo create an access token (Settings → Applications,
   `read:repository`). In Obtainium add the app with the URL
   `https://forgejo.home.lan/lars/webphone-android`, tap **Override
   Source** and pick **Forgejo (Codeberg)** (that source only
   auto-detects `codeberg.org`), then paste the token — the repo is
   private.
2. Enable the per-app option **"Allow insecure HTTP requests"**. The
   forge serves a certificate from the internal `dnsblockd` CA, which is
   not in Android's system trust store; without the toggle every check
   fails on TLS.
3. Reachability: `forgejo.home.lan` resolves on the home LAN and, via
   NetBird, as `forgejo.larsartmann.cloud`. Off the VPN there is no
   route — Obtainium retries on its next check.
4. Install from Obtainium (it needs the "Install unknown apps" permission
   for itself). The first install since signing changed needs the old
   debug-signed build **uninstalled** first (Android refuses an APK whose
   signing key differs from the installed one).
5. Grant mic + notification permissions; set the stack origin if needed;
   log in on the island inside the WebView; verify the badge turns
   REGISTERED.
6. Kill the app and take an inbound call to prove the ntfy
   ring-while-closed lane — with the app set to battery "Unrestricted"
   (OEM battery killers are the #1 missed-ring cause).

### Cut a release

```console
scripts/release.sh --init-key     # once: signing key + properties in ~/.config/webphone-android/
scripts/release.sh bump patch     # version.txt arithmetic + the changelog reminder
#   ... cut CHANGELOG.md: move [Unreleased] under '## [X.Y.Z] - DATE', commit, push
scripts/release.sh                # gate → sign → tag vX.Y.Z → push → publish
```

`version.txt` is the single source of the version: versionName is its
content, versionCode is `major*10000 + minor*100 + patch` (so 0.2.0 is
200). The gate the script enforces is `assembleRelease` +
`testDebugUnitTest` + `ktlintCheck`, the 16 KB `zipalign -c -P 16 4`
check on the **signed** APK, `apksigner verify`, and the WCAG contrast
guard. The artifact lands on the release as
`webphone-android-<version>.apk`; `scripts/forgejo-release.py` owns the
forge API and is idempotent (a retry reuses the release and replaces the
asset).

Manual install still works without any updater: `adb install -r` the
debug APK over USB, or copy an APK to the phone and open it.
