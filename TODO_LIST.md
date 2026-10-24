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

| Task                                                              | Status    | Impact | Effort | Evidence                                                                                                                                                                                                        |
| ----------------------------------------------------------------- | --------- | ------ | ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| First device train: exercise the app end-to-end against the stack | 🔴 `TODO` | High   | 1 day  | Dial (numpad → INVITE → audio), answer (push → FSI → 200), DTMF, hold, ring/ring_end; incoming INVITE + REFER/NOTIFY explicitly UNTESTED (`sip/SipEngine.kt:29-32`)                                             |
| Verify the answer-path race                                       | 🔴 `TODO` | High   | 2h     | A ring push while closed rings via Telecom immediately; the SIP INVITE needs `CallLayer.ensureWired` to win the race (`push/RingRouter.kt` ring, `shell/CallLayer.kt`) — does the server re-invite long enough? |
| Wire outbound calls through Telecom                               | 🔴 `TODO` | High   | 2-4h   | `call/CallConnectionService.kt` `onCreateOutgoingConnection` — zero callers; dialing bypasses Telecom, so BT/watches never see outbound calls. Integrate (`placeCall`) or delete the ghost                      |
| Fix FSI auto-answer (explicit Answer tap)                         | 🔴 `TODO` | High   | 1h     | `call/CallActivity.kt` `startCall()` answers RINGING_IN the moment the activity opens with mic granted — no user tap; product decision pending (status report 2026-10-24_17-44, Q3)                             |

## Medium Impact

| Task                                              | Status    | Impact | Effort | Evidence                                                                                                                        |
| ------------------------------------------------- | --------- | ------ | ------ | ------------------------------------------------------------------------------------------------------------------------------- |
| Hook logout → `deregisterPushDevice`              | 🔴 `TODO` | Med    | 1-2h   | `shell/SessionApi.kt:103` — zero callers; needs a WebView logout signal (JS bridge or cookie poll)                              |
| Settings surface for the stack origin             | 🔴 `TODO` | Med    | 2-4h   | `App.kt:45` — hardcoded `https://pbx.example.org`; README says this lands with the first device train                           |
| Add CI: build + 16 KB zipalign gate               | 🔴 `TODO` | Med    | 1-2h   | `app/build.gradle.kts:11` claims "verified per release in CI" — no workflows exist (`.github/` holds only `dependabot.yml`)     |
| Expose SIP registration state in the UI           | 🔴 `TODO` | Med    | 1-2h   | `call/CallEngine.kt` `onRegistrationState` = `Unit` — the user never sees whether the extension is registered                   |
| Persist ntfy server/topic; survive sticky restart | 🔴 `TODO` | Med    | 1h     | `push/NtfySubscriptionService.kt` — START_STICKY restart delivers null intent → stopSelf → lane silently dead until next resume |
| Add CANCEL for hang-up during outgoing ring-out   | 🔴 `TODO` | Med    | 1h     | `sip/SipEngine.kt` `bye()` is sent before the 200 OK; SIP prescribes CANCEL pre-answer                                          |
| Dialpad: preserve typed number across rotation    | 🔴 `TODO` | Med    | 30min  | `call/DialpadActivity.kt` — programmatic EditText without `android:id`, no `configChanges`                                      |
| Run `assembleRelease` once; verify R8 keeps       | 🔴 `TODO` | Med    | 30min  | `app/proguard-rules.pro` keeps only `org.webrtc.**` + `FcmReceiver`; release never built                                        |

## Low Impact

| Task                                         | Status    | Impact | Effort | Evidence                                                                                                           |
| -------------------------------------------- | --------- | ------ | ------ | ------------------------------------------------------------------------------------------------------------------ |
| CallStyle-composed in-call notification      | 🔴 `TODO` | Low    | 2-4h   | `call/CallActivity.kt:22` — "arrives with the first device train"; current UI is programmatic                      |
| Refresh file:line citations in FEATURES/TODO | 🔴 `TODO` | Low    | 30min  | `SessionApi.kt`/`CallEngine.kt` citations shifted by this session's rewrites (status report 2026-10-24_17-44, d-2) |
