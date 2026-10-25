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
  seam that landed 2026-10-23 (webphone `internal/push`); field-name
  drift is caught at the first device boot, not before.

## Build and verify

```console
nix develop                        # JDK 21 + Android SDK (platform 36, build-tools 34+35)
nix develop -c ./gradlew assembleDebug      # debug APK (THE installable artifact)
nix develop -c ./gradlew assembleRelease    # minified + resource-shrunk (R8); emits an UNSIGNED apk — not installable
nix develop -c ./gradlew testDebugUnitTest  # protocol + Robolectric tests
nix develop .#emulator -c bash scripts/emulator-train.sh   # self-serve device smoke
```

There is no release signing config yet — the debug APK is the only
artifact you can sideload. Signing is a deliberate future step, not an
accident to fix.

The FCM lane activates when `app/google-services.json` exists (drop
in + add the google-services plugin); the ntfy lane works with zero
Google dependencies.

Set the stack origin in the app's Settings screen (persisted; first
run prompts for it).

## Distribution

Sideload first (the `USE_FULL_SCREEN_INTENT` default grant stays
enabled for sideloaded calling apps — the favorable lane; Play would
re-audit it). Play later, if ever.

Install on a phone (Android 12+) in six steps:

1. Rebuild fresh — `nix develop -c ./gradlew assembleDebug` — never
   trust a stale APK.
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk` over USB
   (or copy the APK over and open it).
3. Grant mic + notification permissions; the real stack origin is the
   shipped default — change it in the app's Settings if needed.
4. Log in on the island inside the WebView; that session cookie drives
   everything.
5. Verify the badge turns REGISTERED, then dial via the FAB → dialpad.
6. Kill the app and take an inbound call to prove the ntfy
   ring-while-closed lane — with the app set to battery "Unrestricted"
   (OEM battery killers are the #1 missed-ring cause).
