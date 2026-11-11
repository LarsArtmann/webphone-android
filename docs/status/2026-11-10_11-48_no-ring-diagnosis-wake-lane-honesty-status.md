# Status: the no-ring diagnosis + wake-lane honesty (2026-11-10 11:48 CET)

**Scope line.** VERIFIED this session: the app-side ring-while-closed chain
(`push/*`, `shell/CallLayer.kt`, `shell/SessionApi.kt`, `shell/MainActivity.kt`)
read file-by-file; the server-side publishing conditions
(webphone `internal/push`, `internal/server/hooks/ring.go`, `internal/server/api/push_api.go`,
AGENTS + TODO_LIST 2026-11-08 legs entry) read file-by-file; the wake-lane
honesty + flap-safe probe implemented and pinned; `scripts/verify-gate.sh`
exit 0 (assembleDebug + testDebugUnitTest + ktlint + KGP parity + zipalign +
contrast + dprint); pins meta-proven (broken fixes → tests FAILED → restored →
green); commit `d820810` pushed to `origin` AND `github` (both at `d820810`).
NOT verified and why: the LIVE stack (`pbx.artmann.tech`) — no credentials, and
no unauthenticated request can distinguish seam-off from seam-on; any real
phone — no device attached this session, so the pill, the 1004 notification,
and FSI behavior are Robolectric-only; ntfy reachability at
`<origin>/<topic>/ws` from a phone network; the pre-existing answer-path race
(open question, untouched). This report is a point-in-time snapshot scoped to
THIS session; it does not re-audit the rest of the repo.

## Context: the question that started this

"Why did my phone with the app installed not ring, while the browser
(pbx.artmann.tech, open + logged in) picked up the call?" — Answer: the browser
needs NO push (open page → live sip.js websocket → INVITE lands directly); the
phone needs the 6-link push chain, and until today every app-side failure in
that chain was logcat-only, plus the server side is OFF by default
(`ringhook.enable` unset = dialplan never POSTs `/hooks/ring`; `push.ntfy.*`
unset = `/api/push-devices` answers 404). Ranked failure modes live in the
session's final chat answer and in AGENTS.md ("The wake lane has a UI verdict

- a flap-safe probe").

## a) FULLY DONE

| #  | Item                                                                                                                                                                                                                                                               | Evidence                                                                                                                                                                          |
| -- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1  | Root-cause diagnosis of the no-ring report (6-link chain, ranked failure modes)                                                                                                                                                                                    | Read: `RingEvent.kt`, `NtfySubscriptionService.kt`, `CallLayer.kt`, `SessionApi.kt`, `DeviceIdentity.kt`, server `push.go`/`ntfy.go`/`fcm.go`/`ring.go`/`push_api.go`/`config.go` |
| 2  | WakeLane ledger + line-pill verdict `Connected · wake on/off`                                                                                                                                                                                                      | `push/WakeLane.kt` (new), `MainActivity.kt` `linePillText` (file-level, internal), two `repeatOnLifecycle` collectors                                                             |
| 3  | Off-verdict reasons as the pill's contentDescription (refused / session dead / disconnected)                                                                                                                                                                       | `strings.xml` `wake_reason_*`, `linePillText` pairing                                                                                                                             |
| 4  | Flap-safe session probe: only definitive 401/403 tears the lane down; transport failure = UNREACHABLE = keep watching                                                                                                                                              | `SessionApi.probeFromCode` (pure) + `probeSession`; `SessionApiProbeTest` (4 pins)                                                                                                |
| 5  | Dead-session teardown tells the user: "Ringing paused" notification, id 1004                                                                                                                                                                                       | `CallLayer.onSessionDead`; `CallLayerSessionDeadTest` (2 pins: notify + channel + STOP intent + ledger; idempotency)                                                              |
| 6  | ntfy service reports ARMED (socket open) / DISCONNECTED (lost, `!stopped`-guarded)                                                                                                                                                                                 | `NtfySubscriptionService.onOpen/onFailure/onClosed`                                                                                                                               |
| 7  | Registration refusal surfaces as REFUSED (was: logcat-only)                                                                                                                                                                                                        | `CallLayer.ensureWired` consumes `registerPushLanes` result                                                                                                                       |
| 8  | Meta-proof of the two load-bearing pins (AGENTS vacuous-pin rule)                                                                                                                                                                                                  | broke notify + REFUSED branch → 2 pins FAILED → restored → gate green                                                                                                             |
| 9  | FEATURES.md honesty (watcher row → FULLY_FUNCTIONAL w/ tri-state; new wake-verdict row) + AGENTS.md memory (notification id 1004; wake-lane gotcha w/ stack-side OFF-by-default note)                                                                              | `FEATURES.md` §Ring-while-closed, `AGENTS.md` Conventions + Gotchas                                                                                                               |
| 10 | Pre-existing dprint drift on main's TODO_LIST.md fixed on sight (the table-width reflow the last session's blob left behind)                                                                                                                                       | `nix run nixpkgs#dprint -- fmt` → gate's step 6 green                                                                                                                             |
| 11 | Daemon-blob repair: 7 `chore: auto-commit` blobs folded into one honest commit WITHOUT folding foreign work (soft reset to pre-session `25227cc`, NOT origin/main; every blob's paths verified session-only; lag counts printed before reset: origin 11, github 8) | `git log` → single `d820810 fix: make wake-lane failures visible and flap-safe on the shell`                                                                                      |
| 12 | Push both remotes (standing order; no workflow files touched, so the mirror PAT carries it)                                                                                                                                                                        | origin + github both at `d820810`                                                                                                                                                 |

## b) PARTIALLY DONE

| # | Item                                     | What exists                                     | What is missing                                                                                                                                                             |
| - | ---------------------------------------- | ----------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | THE no-ring fix itself                   | App side armable + honest; gate green           | The user's phone is still unproven: server switches (`ringhook.enable`, `push.ntfy.url`) unconfirmed, no device train run — from the user's chair NOTHING is fixed yet      |
| 2 | Wake-lane visibility                     | Pill verdict + reasons                          | `registerPushDevice` 204 but socket never opens → UNKNOWN (no suffix) until the first onFailure (~10 s connect timeout); a starting/dead-socket state would close that mask |
| 3 | Dead-session notice latency              | Fires on the watcher tick                       | Up to 10 minutes after the actual logout/expiry                                                                                                                             |
| 4 | Notice visibility                        | Posted on `push` channel (min priority, silent) | A silent notice for a silent lane death repeats the original sin — it may deserve the calls channel or a re-post                                                            |
| 5 | DISCONNECTED during transient reconnects | Honest, `!stopped`-guarded                      | Foregrounding mid-reconnect can flicker `wake off` briefly; a grace period or "retrying" state would read better                                                            |
| 6 | Cross-repo follow-through                | AGENTS.md carries the stack-side note           | THIS repo's TODO_LIST has no standing "ring E2E proof" row (it lives only in webphone TODO_LIST 2026-11-08)                                                                 |

## c) NOT STARTED

| # | Item                                                                                                                                                                                                                                          |
| - | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | Device train (emulator or the real phone) exercising: pill verdict rendering, the 1004 notification, FSI ring, POST_NOTIFICATIONS + battery-exemption asks                                                                                    |
| 2 | Server-side enablement + the E2E ring proof (webphone legs item: `ringhook.enable` + `push.ntfy.*` + a registered device + a real call)                                                                                                       |
| 3 | ntfy access-control decision: the app's websocket subscribe sends NO Authorization header; if the self-hosted ntfy denies anonymous read, the lane loops on onFailure forever (silent) — needs the owner's ntfy config answer or an auth seam |
| 4 | `scripts/ring-diagnose.sh`: one command that greps the right logcat filters (`SessionApi`, `NtfySubscription`, `RingEvent`), prints the pill state and the battery/notification grant state — the next "why no ring" becomes one command      |
| 5 | HARVEST of section (f) below into TODO_LIST.md / ROADMAP.md (docs-health)                                                                                                                                                                     |
| 6 | Tap-the-pill → wake-detail surface (the contentDescription reason is invisible to most users on a locked phone)                                                                                                                               |

## d) TOTALLY FUCKED UP

| # | Item                                                                                                                                                                                                                                       | State                                                                                                                                                                      |
| - | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | The inherited product defect class: a shipped ring lane whose every failure was invisible AND that one network flap could permanently silence (boolean session probe)                                                                      | FIXED IN CODE this session (`d820810`) — but the LIVE phone remains unproven, so the user-visible situation is unchanged until the server switches + a device train happen |
| 2 | GitHub Actions billing (pre-existing, owner-side): every push since 2026-11-08 fails in seconds, jobs never start — CI red is BILLING, not code                                                                                            | Unchanged; local `verify-gate.sh` remains the substitute                                                                                                                   |
| 3 | Forgejo→GitHub mirror credential trap (pre-existing): sync PAT lacks `workflow` scope                                                                                                                                                      | Unchanged; this session pushed by hand, no workflow files involved                                                                                                         |
| 4 | Process, mine: the auto-commit daemon swept my mid-flight work into 7 `chore: auto-commit` blobs because I batched the commit until after the gate instead of hand-committing finished units immediately (AGENTS remedy says exactly that) | Repaired: soft-reset fold into `d820810` with the prescribed triple-check, zero loss — but the history noise was avoidable                                                 |
| 5 | Nothing on disk is broken: `main` = `d820810`, gate green, both remotes equal                                                                                                                                                              | —                                                                                                                                                                          |

## e) WHAT WE SHOULD IMPROVE

1. Hand-commit finished units IMMEDIATELY (the daemon beat me 7× this session).
2. Prove pins by breaking the fix FIRST, not after the first green (cheaper meta-proof loop).
3. Surface live-stack facts early — end the session with the 2-3 owner questions instead of "needs credentials" (done in the chat answer, should be the norm).
4. Wire socket START/FAIL into WakeLane so UNKNOWN can never mask a dead lane.
5. Promote the dead-session notice out of the min-priority channel (or make it sticky).
6. Share the request-building between `SessionApi.call` and `probeSession` (small duplication today).
7. Add a standing cross-repo ring-E2E row in THIS repo's TODO_LIST (not only webphone's).
8. The next "why no ring" should be ONE script (`scripts/ring-diagnose.sh`), not an archaeology session.

## f) Up to 50 things to get done next (brainstorm, impact-sorted in spirit — HARVEST must route, not commit)

**Ring chain closure (P0):**

1. Owner: set `push.ntfy.url` (+ token?) in the webphone config on the live host.
2. Owner: set `ringhook.enable` on the consuming stack (FS dialplan arms → POST `/hooks/ring`).
3. E2E ring proof: real call → phone rings while closed (webphone TODO legs item).
4. Answer-path race verdict: does the INVITE land after the push bootstrap (device train)?
5. ntfy read-auth decision: anonymous read allowed, or add an auth header seam to the subscribe?
6. Verify `<origin>/<topic>/ws` is actually routed by Caddy on the live host (subscribe 200 vs 404).
7. Device train: pill shows `wake on` after a real registration.
8. Force-stop / swipe-away test: does START_STICKY resurrect on the user's OEM?
9. Doze/battery test with the exemption NOT granted (does the socket survive?).
10. `ring_end` clears the ring + no ghost missed-call when answered on the browser.
11. Missed-call note when the ring died unanswered HERE (E2E, device).
12. `ringhook` ring-group path ruling (webphone §g-1, cross-repo).

**Wake-lane UX (P1):**
13. UNKNOWN masking fix: STARTING state from the service until the first open/fail.
14. Tap-the-pill → wake detail sheet (reason + last probe time + retry button).
15. Promote the 1004 notice to a visible priority (or re-post on unlock).
16. Grace period / "retrying" flavor for transient DISCONNECTED.
17. Watcher latency: first probe right after wiring (today the first tick is +10 min).
18. `wake off` + register-404 → pill should SAY "push disabled by server" (differentiate 404 from 422/csrf).
19. Session-expired notice should deep-link into the shell login (tap → MainActivity).
20. Localize the new strings if/when the app grows locales (single-locale today — fine).

**Robustness (P1):**
21. Re-subscribe on origin change: Settings teardown stops the lane — verify the next resume re-arms with the NEW origin (test exists for prefs; live socket path unproven).
22. ntfy reconnect cap: after N failed hours, post a "lane down" notice instead of looping forever.
23. FCM lane activation checklist (google-services.json drop-in) remains owner-gated — keep dormant.
24. `DeviceIdentity` reinstall: new device_id + topic — confirm the old row gets pruned (ErrUnregistered only on ntfy-404 servers; likely lingers — harmless? decide).
~~25. Watch the 8-devices-per-extension cap (storeAtCapacity) — second install of the app on a tablet could hit it; surface the 409 as REFUSED-with-reason.~~ done — the cap surfaces as WakeLane REFUSED_INVALID (422, not 409): the pill text names the device-list cap (strings.xml + WakeLane.kt; AGENTS push-register-capacity gotcha)

**Diagnostics (P1):**
26. `scripts/ring-diagnose.sh` (logcat filters + grants + pill state dump).
27. Log the ntfy HTTP status code in `onFailure` (response?.code) — today only the throwable message.
28. Structured ring-chain breadcrumbs: one log tag that prints the chain stage on each ring attempt.

**CI/process (P2):**
29. Owner: fix GitHub Actions billing (every push red since 2026-11-08).
30. Owner: regenerate the sync PAT with `workflow` scope (mirror trap).
31. Post-billing: confirm CI green on `d820810`.
32. TODO_LIST harvest of this list (docs-health HARVEST, route P0/P1 vs ROADMAP).
33. Keep `docs/status/` harvest discipline: annotate THIS report when the E2E proof lands.

**Docs (P2):**
34. README honesty markers: flip "Ring-while-closed E2E: untested" once the proof lands (NOT before).
35. AGENTS.md: record the ntfy auth answer once given (subscribe header or anonymous).
36. FEATURES: bump the "Push device registration" row when live-registered once.

**Nice-to-have (P3, ROADMAP fuel):**
37. Pill tap-through to Dialpad when wake is on (fewer taps to dial).
38. CallStyle shade ring on the API 31-33 direct-dial path remains platform-train territory (keep the plain fallback).
39. Battery-exemption ask: verify it still fires post-registration on the user's OEM.
40. Consider a periodic keep-alive alarm as a belt-and-braces lane restart (weigh battery cost first).
41. Instrument a ring-arrival counter (private log only; no telemetry by decision).
42. Verify `probeSession` behavior against a proxy 502 (UNREACHABLE path) on the emulator train.
43. Fold `linePillText`'s wake suffix into the TalkBack announcement order test (a11y pass).
44. Sweep `docs/status/` old reports for stale ring-lane claims (docs-health ANNOTATE).
45. `AGENTS.md` is growing a ring-lane cluster — consider a dedicated section at the next docs pass.
46. Unit-test `CallLayer.shutdown` → UNKNOWN ledger transition (implicit today, un-pinned).
47. Pin the `!stopped` DISCONNECTED guard (stop-leak pin's sibling).
48. Rename `NtfySubscriptionServiceTest` stop-leak test names to match the new seam vocabulary (cosmetic).
49. Consider `WakeLane` history ring-buffer for the detail sheet (last 5 transitions).
50. Owner question follow-through: whichever of the 3 answers below arrives first, encode it in AGENTS.md the same day.

## g) Questions I cannot figure out myself

1. **Has the phone EVER rung while closed (any build), or was this the first live attempt?** If it once worked, something regressed (server config, cookie death, OEM update); if never, the chain was never armed and links #1/#2 are the whole story.
2. **May I treat the live pbx host as modifiable this week** (set `push.ntfy.url` + `ringhook.enable`), or should the E2E ring proof run on a VM train first? (Owner-scope decision; changes whether the next session is a config change or a NixOS VM exercise.)
3. **Which phone/OEM + Android version runs the app, and was it installed via Obtainium (release APK) or debug-sideloaded?** OEM battery aggression + notification-grant state decide links #5/#6, and the install channel decides which signing key the `assetlinks`/update lane must match.
