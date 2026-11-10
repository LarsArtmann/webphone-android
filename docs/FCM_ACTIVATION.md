# FCM activation checklist (the dormant lane's go-live steps)

The `firebase-messaging` dependency is in the build, but the lane is
DORMANT: no `google-services` plugin, no `app/google-services.json`,
so `FcmReceiver` never fires and the ntfy websocket is the zero-Google
default. This checklist is the exact drop-in order for the day the
lane goes live. Nothing here is a TODO — it is the prepared path, so
activation is minutes, not archaeology.

## 0. Why it is dormant (decision record)

- The ntfy lane needs no Google services and no Play Store; FCM adds
  a second transport for OEMs that kill dataSync sockets more
  aggressively (both transports run by owner decision 2026-10-23).
- firebase-messaging 26 deprecates `getToken()` AND `onNewToken`
  (the v1-registration world: `register()` + a callback). Both stay
  suppressed-on-purpose in `CallLayer.registerPushLanes` /
  `FcmReceiver` until this lane goes live: 26.0.1 ships no
  replacement callback, and a blind API flip on a lane that cannot
  fire is unverifiable. THE FLIP HAPPENS HERE, IN THE SAME SESSION
  AS ACTIVATION, so it can be tested on a device that actually
  receives tokens.

## 1. Google side (owner)

1. Create/select the Firebase project for the stack.
2. Add an Android app with package `software.larsartmann.webphone`
   (and, if distribution ever splits, the release applicationId must
   match — there is no suffix today).
3. Add the SHA-256 of the RELEASE signing key
   (`scripts/assetlinks.sh` prints it; `~/.config/webphone-android/`
   holds the keystore). Debug-key apps get tokens only from
   debug-signed installs.
4. Download `google-services.json` into `app/google-services.json`
   (never committed — confirm `.gitignore` covers it before the
   first commit).

## 2. Build side (code)

1. Root `build.gradle.kts`: add the Google services plugin to the
   classpath; `app/build.gradle.kts`: apply it. (Catalog alias first
   — dependency versions live exclusively in
   `gradle/libs.versions.toml`.)
2. Flip the suppressed deprecations, in the same session:
   - `CallLayer.registerPushLanes`: swap `Tasks.await(...token)` for
     the v1 `register()` flow (token delivered via callback — see
     firebase-messaging 26 docs), keep the `runCatching` best-effort
     shape and the `push-devices` upsert.
   - `FcmReceiver`: replace `onNewToken` with the v1 callback and
     re-register through `SessionApi.registerPushDevice` (the upsert
     by stable `device_id` already rotates the row server-side).
3. `proguard-rules.pro` already keeps `FcmReceiver` — re-check the
   keep after the API flip.

## 3. Server side (owner, cross-repo)

1. The stack's push config: add/enable the FCM transport
   (`push.fcm.*` in the webphone config — field names owned by the
   server repo's `internal/push`).
2. Deploy, then journal-grep the fanout startup.

## 4. Verify (device, the only proof)

1. Install a release-signed build with the plugin applied.
2. `adb shell dumpsys package software.larsartmann.webphone | grep
   POST_NOTIFICATIONS` — granted first.
3. Register: open the app, sign in; `adb logcat -s SessionApi` must
   show `push device registered (fcm)`.
4. Server: `GET /api/push-devices` shows the fcm row next to the
   ntfy row (same `device_id`).
5. Ring E2E with the ntfy lane socket CLOSED (force-stop the ntfy
   service) — the FCM message must ring while closed. That isolates
   the lane you just activated.
6. Update AGENTS.md: the "FCM lane is dormant by default" gotcha
   retires; the update-lane note records the new APK size delta.

## Pitfalls recorded here so they are not rediscovered

- `google-services.json` committed by accident breaks nothing at
  build time and leaks the project ids — keep it ignored.
- The `register()` token callback arrives BEFORE sign-in on a cold
  first launch: the registration upsert needs the session cookie —
  buffer the token (prefs) and flush it on the next
  `registerPushLanes` pass instead of dropping it.
- FCM data messages with `notification` payloads are shown BY THE
  SYSTEM when the app is backgrounded — the ring payload must stay
  data-only (it already is; keep it that way or you get double
  surfaces).
