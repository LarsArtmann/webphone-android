<!-- TODO_LIST.md — LIVING document. Items verified against code at add time.
     DONE items are REMOVED, not kept. History lives in CHANGELOG.md. -->

# TODO List

> Short-term, actionable, bounded work items, verified against the actual code.
> For long-term vision and unrefined ideas, use ROADMAP.md.
> Items are ranked by impact. Status is verified, not assumed.

## Status legend

| Status           | Meaning                                                     |
| ---------------- | ----------------------------------------------------------- |
| 🔴 `TODO`        | Not started. Needs doing.                                   |
| 🟡 `IN_PROGRESS` | Actively being worked on.                                   |
| 🔵 `BLOCKED`     | Cannot proceed, external dependency or decision needed.     |
| 🟢 `DONE`        | Completed. Remove from this list and log in `CHANGELOG.md`. |

## High Impact

| Task                                                          | Status    | Impact | Effort | Evidence                                                                                                                                                                                                                                                                          |
| ------------------------------------------------------------- | --------- | ------ | ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Wire push-device registration after session resume            | 🔴 `TODO` | High   | 1-2h   | `shell/SessionApi.kt:76` (`registerPushDevice`) — zero callers; FCM comment in `push/FcmReceiver.kt:31` claims "kicked from MainActivity" but `MainActivity` never calls it. Without it the server never learns the ntfy topic / FCM token — ring-while-closed is dead end-to-end |
| Add outbound dial entry point                                 | 🔴 `TODO` | High   | 2-4h   | `call/CallEngine.kt:52` (`dial`) — zero callers; no UI anywhere starts a call                                                                                                                                                                                                     |
| First device train: exercise SIP against the live WSS gateway | 🔴 `TODO` | High   | 1 day  | REGISTER/INVITE/BYE/DTMF/hold untested; incoming INVITE + REFER/NOTIFY explicitly UNTESTED (`sip/SipEngine.kt:29-32`, README honesty contract)                                                                                                                                    |

## Medium Impact

| Task                                                | Status    | Impact | Effort | Evidence                                                                                                                    |
| --------------------------------------------------- | --------- | ------ | ------ | --------------------------------------------------------------------------------------------------------------------------- |
| Sync the WebView cookie into the OkHttp session jar | 🔴 `TODO` | Med    | 1-2h   | `shell/SessionApi.kt:32` — in-memory jar, separate from `CookieManager`; the session seam cannot work until they meet       |
| Implement FSI runtime check + heads-up degradation  | 🔴 `TODO` | Med    | 1h     | `push/RingRouter.kt:22` KDoc describes `canUseFullScreenIntent()` degrade; no code                                          |
| Add POST_NOTIFICATIONS runtime request              | 🔴 `TODO` | Med    | 30min  | Declared `AndroidManifest.xml:7`; no request code (API 33+)                                                                 |
| Hook logout → `deregisterPushDevice`                | 🔴 `TODO` | Med    | 1-2h   | `shell/SessionApi.kt:103` — zero callers; needs a WebView logout signal (JS bridge or cookie poll)                          |
| Settings surface for the stack origin               | 🔴 `TODO` | Med    | 2-4h   | `App.kt:45` — hardcoded `https://pbx.example.org`; README says this lands with the first device train                       |
| Add CI: build + 16 KB zipalign gate                 | 🔴 `TODO` | Med    | 1-2h   | `app/build.gradle.kts:11` claims "verified per release in CI" — no workflows exist (`.github/` holds only `dependabot.yml`) |

## Low Impact

| Task                                    | Status    | Impact | Effort | Evidence                                                                                      |
| --------------------------------------- | --------- | ------ | ------ | --------------------------------------------------------------------------------------------- |
| CallStyle-composed in-call notification | 🔴 `TODO` | Low    | 2-4h   | `call/CallActivity.kt:22` — "arrives with the first device train"; current UI is programmatic |
