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

| Task                                                                     | Status       | Impact | Effort | Evidence                                                                                                                                                      |
| ------------------------------------------------------------------------ | ------------ | ------ | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| First device train: exercise the app end-to-end against the live stack   | 🔵 `BLOCKED` | High   | 1 day  | Needs operator: stack origin + test extension (Q1). The self-serve part is done: emulator kit + smoke passed 2026-10-24                                       |
| Verify the answer-path race (push → FSI vs INVITE)                       | 🔵 `BLOCKED` | High   | 2h     | Needs the live stack (Q1); does the server re-invite long enough? Plan task F013 measures it                                                                  |
| Thread-confinement audit: `_state`/`pendingInvite` writes across threads | 🔴 `TODO`    | High   | 2h     | `CallEngine` is written from WebRTC callbacks, OkHttp WS thread, and the UI thread; `MutableStateFlow` is atomic but `pendingInvite`/`media` are plain fields |

## Medium Impact

| Task                                                            | Status       | Impact | Effort | Evidence                                                                                                                                           |
| --------------------------------------------------------------- | ------------ | ------ | ------ | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Logout deregistration: cookie-poll (shipped) vs JS bridge       | 🔵 `BLOCKED` | Med    | 1h     | Operator picks (plan F063). Cookie-poll watcher shipped (`CallLayer.startSessionWatch`) but cannot deregister after the cookie is dead             |
| CallStyle-composed incoming + ongoing notifications             | 🔴 `TODO`    | Med    | 2-4h   | Plan T21; needs a run device train first (T01/T02) to verify against both ring paths                                                               |
| INVITE 401 re-auth ladder + silence watchdog re-register        | 🔴 `TODO`    | Med    | 2h     | Only REGISTER is challenge-handled (`SipEngine.receiveResponse`); PBXs that challenge INVITE break outbound calls. `SipEngineTest` seams are ready |
| Second-incoming-call UX: suppress FSI when busy + heads-up      | 🔴 `TODO`    | Med    | 1h     | `CallEngine.onIncomingCall` 486s silently; a "line busy" heads-up is plan F095-F096                                                                |
| `CallLayer.shutdown` vs in-flight `ensureWired` race decision   | 🔴 `TODO`    | Med    | 30min  | `attachOnce` serializes attach, but a wire completing AFTER shutdown leaves an orphan engine (`engine` re-set by the launch block)                 |
| Live contract checks: `/api/session` fields, push payload, CSRF | 🔵 `BLOCKED` | Med    | 1h     | Needs the live stack (Q1); field-drift class of bugs (plan T16)                                                                                    |

## Low Impact

| Task                                          | Status       | Impact | Effort | Evidence                                                                                           |
| --------------------------------------------- | ------------ | ------ | ------ | -------------------------------------------------------------------------------------------------- |
| Real adaptive icon artwork + LICENSE decision | 🔴 `TODO`    | Low    | 1h     | Plan T24 remainder (icon is the placeholder teal; no LICENSE file; config cache + dependabot done) |
| Cookie-expiry mid-call behavior check         | 🔵 `BLOCKED` | Low    | 30min  | Needs the live stack (plan F106)                                                                   |
| TLS-pinning consideration memo                | 🔴 `TODO`    | Low    | 30min  | Decide pin-or-not, record why (plan F107)                                                          |
| Retire honesty markers post-train             | 🔵 `BLOCKED` | Low    | 1h     | Needs both device trains green (plan T26)                                                          |
