<!-- TODO_LIST.md — LIVING document. Items verified against code at add time.
     DONE items are REMOVED, not kept. History lives in CHANGELOG.md. -->

# TODO List

> Short-term, actionable, bounded work items, verified against the actual code.
> For long-term vision and unrefined ideas, use ROADMAP.md.
> Items are ranked by impact. Status is verified, not assumed.
> Evidence cites a code path plus the source status report
> (`docs/status/<date>_<name>.md` §section) for every harvested row.

## Status legend

| Status           | Meaning                                                     |
| ---------------- | ----------------------------------------------------------- |
| 🔴 `TODO`        | Not started. Needs doing.                                   |
| 🟡 `IN_PROGRESS` | Actively being worked on.                                   |
| 🔵 `BLOCKED`     | Cannot proceed, external dependency or decision needed.     |
| 🟢 `DONE`        | Completed. Remove from this list and log in `CHANGELOG.md`. |

## High Impact

| Task                                                                      | Status       | Impact | Effort | Evidence                                                                                                                                                      |
| ------------------------------------------------------------------------- | ------------ | ------ | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Owner: run the SystemNix deploy — phase 5 attaches the push mirror        | 🔵 `BLOCKED` | High   | 10min  | Owner-only (`nix run .#deploy`, sudo); mirror is deploy-gated. Source: 05-48 §b1/f1                                                                           |
| Owner: regenerate the sync PAT with `repo`+`workflow` scope (sops secret) | 🔵 `BLOCKED` | High   | 15min  | Unfreezes domains (114 commits behind) and this repo's `ci.yml` mirror pushes. Source: 05-48 §b2/f2                                                           |
| First device train: exercise the app end-to-end against the live stack    | 🔵 `BLOCKED` | High   | 1 day  | Needs operator: stack origin + test extension (Q1). The self-serve part is done: emulator kit + smoke passed 2026-10-24                                       |
| First physical install + first-run walk of the redesigned build           | 🔵 `BLOCKED` | High   | 30min  | The phone still carries the pre-redesign install; the steps live in README "Distribution". Source: 03-12 §f1, 04-58 §f1                                       |
| Verify the answer-path race (push → FSI vs INVITE)                        | 🔵 `BLOCKED` | High   | 2h     | Needs the live stack (Q1); does the server re-invite long enough? Plan task F013 measures it                                                                  |
| Thread-confinement audit: `_state`/`pendingInvite` writes across threads  | 🔴 `TODO`    | High   | 2h     | `CallEngine` is written from WebRTC callbacks, OkHttp WS thread, and the UI thread; `MutableStateFlow` is atomic but `pendingInvite`/`media` are plain fields |
| Stale theme colors after a day/night switch                               | 🔴 `TODO`    | High   | 1h     | `AndroidManifest.xml` pins `uiMode` in `configChanges` on all three activities → no recreation → token colors resolve once; re-resolve on `onConfigurationChanged` or drop `uiMode`. Source: 04-58 §f33 |

## Medium Impact

| Task                                                                     | Status       | Impact | Effort | Evidence                                                                                                                                           |
| ------------------------------------------------------------------------ | ------------ | ------ | ------ | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Logout deregistration: cookie-poll (shipped) vs JS bridge                 | 🔵 `BLOCKED` | Med    | 1h     | Operator picks (plan F063). Cookie-poll watcher shipped (`CallLayer.startSessionWatch`) but cannot deregister after the cookie is dead             |
| Execute F055: ntfy sticky-restart kill-cycle (`adb shell am kill`)        | 🔴 `TODO`    | Med    | 30min  | Persistence code shipped (dac60c9); the actual device kill-cycle was never executed — executable on the emulator. Source: 19-14 §f8, 03-12 §f11    |
| Fix `scripts/emulator-smoke.sh` adb robustness (pin `-s`, fail-fast)      | 🔴 `TODO`    | Med    | 30min  | `scripts/emulator-smoke.sh:12` runs a bare `adb devices` — the dual-server/multi-device flaw is still in the script. Source: 19-14 §d5/f19/f47     |
| Battery-optimization exemption prompt for the ntfy lane (Doze)            | 🔴 `TODO`    | Med    | 1h     | No request code exists (only the accepted-trade KDoc in `NtfySubscriptionService`); OEM killers are the #1 missed-ring cause. Source: 03-12 §f4     |
| TalkBack pass + contrast audit over every programmatic surface            | 🔴 `TODO`    | Med    | 2h     | The a11y floor shipped (`ui/Design.kt` helpers); the full audit never ran. Source: 19-14 §f24/f25                                                  |
| Promote the photo pipeline into `scripts/emulator-photos.sh`              | 🔴 `TODO`    | Med    | 1h     | The working script (serial-pinned adb, `adb root`, boot/frame waits) died in `/tmp` with its session; the lessons live in AGENTS.md. Source: 04-58 §f6/e5 |
| CI execution home + a `push` trigger decision                             | 🔵 `BLOCKED` | Med    | 30min  | `ci.yml` triggers are `pull_request`/`workflow_dispatch` only — NOTHING builds on merge-to-main; where CI runs is the operator's call (ROADMAP "Open questions"). Source: 05-48 §f13 |
| CallStyle-composed incoming + ongoing notifications                       | 🔴 `TODO`    | Med    | 2-4h   | Plan T21; needs a run device train first (T01/T02) to verify against both ring paths                                                               |
| INVITE 401 re-auth ladder + silence watchdog re-register                  | 🔴 `TODO`    | Med    | 2h     | Only REGISTER is challenge-handled (`SipEngine.receiveResponse`); PBXs that challenge INVITE break outbound calls. `SipEngineTest` seams are ready |
| Second-incoming-call UX: suppress FSI when busy + heads-up                | 🔴 `TODO`    | Med    | 1h     | `CallEngine.onIncomingCall` 486s silently; a "line busy" heads-up is plan F095-F096                                                                |
| `CallLayer.shutdown` vs in-flight `ensureWired` race decision             | 🔴 `TODO`    | Med    | 30min  | `attachOnce` serializes attach, but a wire completing AFTER shutdown leaves an orphan engine (`engine` re-set by the launch block)                 |
| Live contract checks: `/api/session` fields, push payload, CSRF           | 🔵 `BLOCKED` | Med    | 1h     | Needs the live stack (Q1); field-drift class of bugs (plan T16)                                                                                    |

## Low Impact

| Task                                   | Status       | Impact | Effort | Evidence                                                                                             |
| -------------------------------------- | ------------ | ------ | ------ | ---------------------------------------------------------------------------------------------------- |
| Recents chips go stale after a call    | 🔴 `TODO`    | Low    | 30min  | The chips build once in `onCreate` + `configChanges` blocks recreation. Source: 19-14 §d8/f21        |
| Port `scripts/mirror-lag.sh` (domains) | 🔴 `TODO`    | Low    | 30min  | Only the inline two-liner probe exists (AGENTS.md "Repository & mirrors"). Source: 05-48 §f12        |
| Set the Forgejo repo description       | 🔵 `BLOCKED` | Low    | 10min  | The GitHub twin has one; Forgejo has none (needs web UI or an API token). Source: 05-48 §c/f10       |
| LICENSE decision + file                | 🔴 `TODO`    | Low    | 30min  | Adaptive icon artwork DONE 2026-10-25 (handset glyph + monochrome layer); LICENSE file still missing |
| Cookie-expiry mid-call behavior check  | 🔵 `BLOCKED` | Low    | 30min  | Needs the live stack (plan F106)                                                                     |
| TLS-pinning consideration memo         | 🔴 `TODO`    | Low    | 30min  | Decide pin-or-not, record why (plan F107)                                                            |
| Retire honesty markers post-train      | 🔵 `BLOCKED` | Low    | 1h     | Needs both device trains green (plan T26)                                                            |
