# SUPERB-Pass Execution — Verification, Hardening, Docs Truth (2026-10-25 13:56)

**Session scope:** execute the agent-executable tasks of
`docs/planning/2026-10-25_10-17_SUPERB-post-hardening-verification-and-daily-driver-plan.md`
(P02, P06-P10, P13-P17, P19, P21, P23, P25, P26, P11a). The pass ran
alongside at least one other live agent session (contrast gate, WebView
policy tests, `28ba2be`); one daemon sweep race and one split-brain were
caught and resolved (§d).

**Gate at report time:** 72 JVM tests green (fresh `--rerun` execution,
17 result files rewritten 13:41), `zipalign -c -P 16 4` green, contrast
gate 50/50, dprint clean. Local `main` is **5 commits ahead** of both
remotes (last verified equal tip: `1c6940e`) — push is the immediate
next action (§f1).

## a) FULLY DONE

| Item                                                                                                                                                                                                                                                                                                                                                                                     | Evidence                                                                                                      |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| P02 push origin+github, tips verified equal (`1c6940e`)                                                                                                                                                                                                                                                                                                                                  | `git ls-remote` both = HEAD at 10:34                                                                          |
| P08 CI diagnosis: push trigger WORKS (runs fire), jobs billing-blocked — **owner fix, no workflow defect**                                                                                                                                                                                                                                                                               | `gh run view 40181133177`: "recent account payments have failed or your spending limit needs to be increased" |
| W101 WebView dark-mode seam — FIRST visual proof (island renders its own dark theme under uimode night)                                                                                                                                                                                                                                                                                  | `photos/shell-dark.png` vs `shell-light.png`                                                                  |
| W103 offline overlay: native "Can't reach the phone system" + Retry + recovery end-to-end on airplane mode                                                                                                                                                                                                                                                                               | `photos/w103-offline.png`, `photos/w103-r.png`                                                                |
| W106 back nav with empty history backgrounds the app, zero crashes                                                                                                                                                                                                                                                                                                                       | logcat, no FATAL/AndroidRuntime                                                                               |
| W107 renderer kill → crash detected → new sandboxed process → WebView rebuilt + origin reloaded, app survived                                                                                                                                                                                                                                                                            | logcat `Renderer process (5426) crash detected` → proc 5628                                                   |
| F024 uimode recreation (T06 root fix PROVEN): `finishDrawing of relaunch`, new window token, no crash                                                                                                                                                                                                                                                                                    | logcat 10:38:08                                                                                               |
| F038 smoke-script dry run: install + launch + render clean on the live emulator                                                                                                                                                                                                                                                                                                          | `Displayed +2s12ms`, native lib loaded                                                                        |
| F058 photos run: light + dark in one pass                                                                                                                                                                                                                                                                                                                                                | `photos/shell-{light,dark}.png`                                                                               |
| P07 F035/F036 ntfy kill-cycle PROVEN: `am kill` correctly refuses the FGS; hard kill → "Scheduling restart … for start-requested" → new pid → service re-foregrounded → subscription loop resumed. Nothing to fix                                                                                                                                                                        | logcat 10:42-10:43                                                                                            |
| P09 W113/W114: `navigateOut` policy seam + client delegation pinned; main-frame-vs-subframe overlay split pinned (2 tests)                                                                                                                                                                                                                                                               | `WebSurface.kt`, `WebSurfaceTest.kt`                                                                          |
| P14 W130 image-only file inputs → system photo picker on API 33+                                                                                                                                                                                                                                                                                                                         | `WebSurface.chooseIntent`                                                                                     |
| P14 W131 **predictive-back bug fix**: `enableOnBackInvokedCallback="true"` had SILENCED `onBackPressed` — in-shell back nav was dead on API 33+; an `OnBackInvokedCallback` bridges WebView history now                                                                                                                                                                                  | `MainActivity` + AGENTS gotcha                                                                                |
| P14 W132 console bridge gated behind debuggable (errors always)                                                                                                                                                                                                                                                                                                                          | `ShellWebChromeClient.onConsoleMessage`                                                                       |
| P14 W133 offline overlay re-resolves the origin at SHOW time (stale-origin fix)                                                                                                                                                                                                                                                                                                          | `WebSurface.showOffline`                                                                                      |
| P15 W140 CallServiceTest: styled + plain branches build, shade hang-up drives the engine (3 tests; shadow-permitting honesty recorded)                                                                                                                                                                                                                                                   | `call/CallServiceTest.kt`                                                                                     |
| P16 W150 pin: stale `ring_end` after a local answer spares the live call, posts nothing                                                                                                                                                                                                                                                                                                  | `RingEventRouteTest`                                                                                          |
| P16 W151 ntfy rotation pinned: restart aims at the NEW topic; `subscriptionUrl` seam                                                                                                                                                                                                                                                                                                     | `NtfySubscriptionServiceTest`                                                                                 |
| P16 W152 review verdict: NO double-REGISTER storm — every relaunch path cancels its predecessor Job first; discipline recorded as an AGENTS gotcha                                                                                                                                                                                                                                       | `SipEngine` lines 145/172/185/645                                                                             |
| P17 W160 swallow sweep: telecom-answer FGS start, CallStyle post, unroutable ntfy frame now LOG                                                                                                                                                                                                                                                                                          | `CallConnectionService`, `RingEvent`, `NtfySubscriptionService`                                               |
| P17 W161 TelecomOutgoing generation guard: superseded `addCall` sessions disconnect instead of orphaning                                                                                                                                                                                                                                                                                 | `TelecomOutgoing.kt`                                                                                          |
| P19 W170 ring while AUTH-REJECTED surfaces "Sign-in rejected" on the ring notification id (server's ring_end still clears it) instead of a false Telecom ring                                                                                                                                                                                                                            | `RingEvent.ring` + test                                                                                       |
| P19 W171 shade call clock: `CallState.startedAtMs` (re-INVITE-safe) + `CallService.setWhen`                                                                                                                                                                                                                                                                                              | `CallEngine`, `CallService`                                                                                   |
| P13 W120/W122 MockWebServer REGISTER soak over a REAL websocket: challenge→credentialed→REGISTERED, wrong-password ladder ENDS at 2, stale=true buys exactly 3. W121 honestly scoped out (needs WebRTC native; covered seam-level by `SipEngineAuthTest`)                                                                                                                                | `sip/SipEngineOverTheWireTest.kt` + `mockwebserver` dep                                                       |
| P21/W180 FIRST `--release` smoke GREEN after two script fixes (§d3/§d4): R8 output boots, `Displayed +1s180ms`                                                                                                                                                                                                                                                                           | `webphone-release-smoke.png` (committed `589f886`, now untracked)                                             |
| Train-script hardening: serial resolved BEFORE any wait (physical devices on the server no longer abort), `emu kill` trap fallback, release-mode uninstall                                                                                                                                                                                                                               | `scripts/emulator-train.sh`                                                                                   |
| P23 docs truth: TODO_LIST rebuilt (13 shipped rows struck), FEATURES flipped (CallStyle 🟡, missed-call 🟢, contrast CI gate, 72 tests, CI billing note, hardened-surface row), AGENTS gotchas (predictive back, uiMode contract, watchdog cancel-discipline, lastActivity-in-receive, AttachGuard semantics, CallStyle fallback, javap-first, TLS, API 31-33, W170, status-bar finding) | the three docs commits                                                                                        |
| P25 decisions sweep written: ROADMAP "Decisions" section (TLS not pinned, API 31-33 permanent, CallEndpoint keep, WorkManager rejected, crash reporting none, snapshots off, WebView stance, Kotlin formatter none-for-v0.1, values-de not now); stale shipped raw ideas pruned; CI open question resolved                                                                               | `ROADMAP.md`                                                                                                  |
| W204 strings audit: both interpolated strings already positional — PASS, no change                                                                                                                                                                                                                                                                                                       | `strings.xml`                                                                                                 |
| W210 screenshots untracked + gitignored (`photos/`, `webphone-release-smoke.png`); concurrent session's `work/` untouched                                                                                                                                                                                                                                                                | `.gitignore`, `git rm --cached`                                                                               |
| CHANGELOG: `.kotlin/` note, 10:00-pass entries, this pass's entries, 60→72 test count                                                                                                                                                                                                                                                                                                    | `CHANGELOG.md`                                                                                                |

## b) PARTIALLY DONE

| Item                                           | State                                                                                                                                                                            | Remainder                                                                                                 |
| ---------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| W102 in-page theme toggle override **→ TODO_LIST (cross-repo: island safe-area-inset-top)**     | Darkening seam proven (F024/W101); the island's own "Theme" pill sits under the status-bar cutout (top inset 128 px) and is UNTAPPABLE in-shell — my two taps hit the status bar | Cross-repo fix: island `env(safe-area-inset-top)` or shell pads the WebView container; recorded in AGENTS |
| ~~W193 annotate the 06:38 plan~~ done — docs/status/2026-10-25_14-33 §a (fa7f144: rows struck, check-rows complete)   | ~~`emit-keys` generated for 51 rows (dry-run-first discipline loaded from the docs-health skill); spec NOT yet applied, NOT committed~~                                              | ~~Apply `status-items` with `v`-kind verdicts, then `check-rows`, then commit~~                           |
| ~~P02 push (recurring)~~ done — 14-33 §a (pushed twice, tips equal f585c6d; mirror-lag green)                           | ~~5 commits ahead of both remotes; the plan pre-authorizes the push~~                                                                                                                | ~~Push both remotes, re-verify tips, run `scripts/mirror-lag.sh`~~                                        |
| P11 tag `v0.1.0` **→ TODO_LIST (R24/N062 — operator timing)**                                                          | CHANGELOG entries all in; `[0.1.0] - 2026-10-23` section exists but NO git tag exists yet                                                                                            | Operator timing call (F074b) — deliberately not taken                                                     |
| W104/W105 attach flow + external-link hand-off **→ TODO_LIST (R19/N049-N050)**                                         | Everything else in W01 verified; these two need a LOGGED-IN island session (Pocket ID passkey)                                                                                       | Q1-adjacent operator credential; row added to TODO_LIST                                                   |

## c) NOT STARTED (all owner/live-stack gated)

- P03 physical install + first-run walk (needs the operator's phone)
- P04/P05 device trains (Q1: test extension + account)
- P12 logout KEEP-vs-PURGE (F045 operator answer)
- P18 CallStyle device verification, P20 AEC/NS + R8-vs-real-call + CANCEL-vs-BYE (trains)
- P22 TalkBack on device + battery allow/deny (device + REGISTERED)
- P24 island-standdown seam (owner nod, cross-repo)
- P26 remainder: Forgejo description (token), icon parity (owner both-or-neither)
- P27 post-train docs sweep (honesty-marker retirement after the trains) **→ TODO_LIST (R26/N066)**
- F041 first green CI run (billing fix first) **→ TODO_LIST (R13/N029)**

**→ all routed:** P03 → R14; P04/P05 → R15/R16; P12 → R21; P18/P20/P22 → R23 + trains; P24 → R22; P26 remainder → R25.

## d) TOTALLY FUCKED UP (honest)

1. **Lost commit messages to the daemon AGAIN (3rd+ time).** The daemon
   swept the cluster work into `8bbeb91`, `3b3d723`, `e42e0dc`
   (heuristic messages) before each manual commit landed; only
   `f4c9ca4` (docs truth) carries a real message. The "commit
   IMMEDIATELY after each gate" rule was not fast enough — the daemon
   polls faster than a gate round-trip.
2. **Split-brain with the concurrent session: I wrote
   `scripts/contrast-pairs.txt` while their `contrast-check.py` +
   `contrast-pairs.json` already existed** (created 10:44:59, mid-my-
   emulator-pass). I had checked the dir at session start, not
   immediately before writing. Their implementation is better
   (AARRGGBB, per-pair compositing, dual-mode); mine was trashed
   uncommitted. Wasted effort + a near-duplicate-format incident.
3. **My own log filter hid the release-train failure**: the grep pattern
   (`^==|FATAL|AndroidRuntime|error|Error`) did not include "failed",
   so `INSTALL_FAILED_UPDATE_INCOMPATIBLE` was swallowed and the first
   rerun looked mysteriously truncated. Cost one extra 4-minute train
   run to diagnose.
4. **First `--release` train failed on a bare `adb wait-for-device`**
   (more than one device — the operator's phone was attached): the
   script resolved the serial AFTER waiting. Fixed, then the SECOND
   failure was the signature mismatch (AVD userdata held the
   gradle-debug-signed package). Two script bugs found exactly because
   the smoke finally ran — but both were knowable in advance.
5. **Wrong-file edit under fatigue**: fixing the `destroy()` compile
   errors I confused `CallServiceTest` with `NtfySubscriptionServiceTest`,
   deleted a good assertion, and had to rewrite the file whole. The
   `multiedit` whitespace-adaptation warning was the tell.
6. **Three gate iterations on test-writing** (missing `R` import;
   `ServiceController.destroy()` does not exist; CallStyle renders the
   PERSON as EXTRA_TITLE) and one design error (asserting
   `frames.count` AFTER my own polling had drained the queue). The
   javap-first/shadow-API lesson is real — I lived it, then wrote it
   into AGENTS.
7. **Stale-context hazard**: hours passed mid-session (background
   waits); three daemon commits and a concurrent session's commits
   landed under me. My mental model of "the tree" went stale twice and
   had to be re-derived from git (one daemon "16-line train.sh addition"
   turned out to be my OWN edit being swept).

## e) WHAT WE SHOULD IMPROVE

1. **Commit-before-daemon**: for gate-verified work, commit with `git
   commit` in the SAME command chain as the gate (already the rule —
   but the real fix is shorter gates or a daemon pause hook).
2. **Re-check for concurrent files in the 5 seconds before `write`** —
   `ls` the target dir, not the session-start snapshot.
3. **Never grep-filter train/script output on the first diagnostic
   run** — tail everything, filter on the second run.
4. **Shadow-API tests: read the shadow class (javap/strings) BEFORE
   writing the test** — it is in the gradle cache and takes 20 seconds.
5. **The 06:38-plan annotation should have been generated from
   emit-keys BEFORE the docs commit**, so the marker hashes and the
   work commits land together (next session: §b row 2).
6. **A tiny `scripts/verify-gate.sh`** wrapping build+tests+zipalign+ **→ TODO_LIST (verify-gate wrapper — one-command gate; harvested 2026-10-28)**
   contrast+dprint would make the gate one command (and one background
   job) instead of five.

## f) NEXT 50 (ranked; owner items marked)

1. ~~Push `main` to origin + github (5 commits waiting); verify tips equal.~~ done — docs/status/2026-10-25_14-33 §a (pushed twice, tips equal f585c6d)
2. ~~Run `scripts/mirror-lag.sh` after the push (expect zero lag until the~~ done — 14-33 §a (mirror-lag probe green after each push)
   ~~mirror attaches).~~
3. ~~Apply the W193 annotation spec to the 06:38 plan (dry-run → apply →~~ done — docs/status/2026-10-25_14-33 §a W193 (fa7f144: 51 rows struck, ledger exemption, check-rows complete)
   ~~`check-rows` → commit) — keys already generated.~~
4. **Owner**: fix GitHub Actions billing → the next push produces the **→ TODO_LIST (GitHub billing fix, R12c/N027)**
   FIRST green CI run (F041) — then watch it.
5. **Owner**: SystemNix deploy (attaches the Forgejo→GitHub push mirror). **→ TODO_LIST (Owner: SystemNix deploy, R12a/N025)**
6. **Owner**: regenerate the sync PAT with `repo`+`workflow` scope. **→ TODO_LIST (Owner: PAT repo+workflow, R12b/N026)**
7. **Owner/Q1**: name the test extension + account for `pbx.artmann.tech`. **→ TODO_LIST (Q1 test extension + account — the standing blocker)**
8. **Operator**: cut the `v0.1.0` annotated tag (CHANGELOG is ready). **→ TODO_LIST (v0.1.0 annotated tag, R24/N062)**
9. Cross-repo: island `safe-area-inset-top` fix for the status-bar **→ TODO_LIST (Cross-repo: island safe-area-inset-top — status-bar overlap in-shell)**
   overlap (finding of this pass).
10. Emulator: W104 attach flow with a logged-in session. **→ TODO_LIST (logged-in emulator: W104 attach, R19/N049)**
11. Emulator: W105 external-link hand-off with a logged-in session. **→ TODO_LIST (logged-in emulator: W105 hand-off vs the redirect seam, R19/N050)**
12. Emulator: W106 tab-nav back-stack WITH history (beyond the boundary test). **→ TODO_LIST (logged-in emulator: W106 back stack with history, R19/N051)**
13. Re-run `emulator-photos.sh` after the status-bar fix (before/after pair). **→ TODO_LIST (re-run photos after the island safe-area fix)**
14. Device train T04: outbound E2E (F008-F014). **→ TODO_LIST (outbound device train, R15/N033-N038)**
15. Device train T05: ring-while-closed + race measurement (F015-F021). **→ TODO_LIST (ring train + race, R16/N039-N044)**
16. T03: physical install + first-run walk. **→ TODO_LIST (First physical install, R14/N030-N032)**
17. F063 CallStyle device verification (both trains). **→ TODO_LIST (CallStyle device verification, R23/N059)**
18. F081 AEC/NS on hardware. **→ TODO_LIST (AEC/NS on hardware, R15/N037)**
19. F082 webrtc R8 keeps vs a REAL release call. **→ TODO_LIST (first device train — R8 keeps vs a real release call, R15/R16)**
20. F084 FreeSWITCH CANCEL-vs-BYE confirmation. **→ TODO_LIST (FreeSWITCH CANCEL-vs-BYE, R16/N038)**
21. F052 battery-prompt allow/deny on device (REGISTERED needed). **→ TODO_LIST (battery-prompt allow/deny, R23/N061)**
22. F053 TalkBack walkthrough (dialpad, in-call, settings, shell, overlay). **→ TODO_LIST (TalkBack walkthrough, R23/N060)**
23. F045/F046/F047 logout deregistration (KEEP-vs-PURGE → implement → verify). **→ TODO_LIST (Logout deregistration, R21/N054-N056)**
24. P24 island-standdown seam design note (owner nod) in the webphone repo. **→ TODO_LIST (island-standdown seam design note, R22/N057-N058)**
25. F077 Forgejo repo description (needs a token). **→ TODO_LIST (Forgejo repo description, R25/N064)**
26. F079 icon parity both-or-neither with the PWA. **→ TODO_LIST (icon parity both-or-neither, R25/N065)**
27. P27 honesty-marker retirement in README/FEATURES after the trains. **→ TODO_LIST (post-train honesty-marker retirement, R26/N066)**
28. P27 CHANGELOG cut for the version AFTER v0.1.0. **→ TODO_LIST (CHANGELOG cut for the version after v0.1.0, R26/N068)**
29. F088 ROADMAP revisit: multi-line, provisioning, post-train themes. **→ TODO_LIST (ROADMAP revisit multi-line/provisioning, R26/N067)**
30. F089 next-version section + tag (post-train). **→ TODO_LIST (next-version section + tag, R24/N063)**
31. ~~Auth-ladder INVITE soak over MockWebServer IF a JVM WebRTC offer~~ done — honestly scoped out (W121 — needs WebRTC native; seam-covered by SipEngineAuthTest, documented)
    ~~seam ever exists (else stays seam-covered — documented).~~
32. Consider `scripts/verify-gate.sh` (§e6). **→ TODO_LIST (verify-gate wrapper — one-command gate)**
33. ~~Consider a daemon-pause convention for message-critical commits.~~ **NOT-DO — the commit-immediately convention stands; no daemon-pause hook was built or needed since.**
34. ~~Dependabot PR: firebase-messaging 24.1.0→26.0.1 sits unmerged (CI was~~ done — firebase-messaging 26.0.1 landed in the 2026-10-28 dependency refresh (CHANGELOG)
    ~~billing-dead when it ran) — review + merge once CI is green.~~
35. Watch the ubuntu-latest → Ubuntu 26 runner migration annotation **→ TODO_LIST (watch runner migration at the first green CI run, R13)**
    (appeared in the CI run log).
36. ~~Investigate the `webphone-release-smoke.png` + `photos/` history~~ **NOT-DO — private repo, small blobs, documented (13-43 d5) — history rewrite not worth the risk.**
    ~~entries (now untracked; consider `git gc` hygiene someday — low).~~
37. ~~`work/` directory ownership: the concurrent session left~~ done — work/ gitignored (13-43 d5) + screenshots untracked (W210)
    ~~`w00-baseline.*` committed — ask whether `work/` should be~~
    ~~gitignored wholesale.~~
38. ~~Add `emulator-verify.sh` (concurrent session's script) to AGENTS~~ **NOT-DO — train/smoke scripts remain the canonical documented kit; emulator-verify is discoverable in scripts/.**
    ~~build/verify docs if it is meant to stay.~~
39. ~~Decide whether `webphone-train.png` class artifacts should move into~~ done — both artifact paths gitignored (13-56 §a W210)
    ~~`photos/` (one ignore rule instead of two).~~
40. RingEvent: consider surfacing `RegistrationFailure.OFFLINE` in the **→ TODO_LIST (re-validate OFFLINE surfacing after the trains, R16/N043)**
    incoming path too (W170 covers AUTH_REJECTED only — deliberate, but
    re-validate after the trains).
41. ~~CallActivity duration clock and the shade clock could share one~~ **NOT-DO — two small m:ss sites — a shared util would out-couple more than it saves.**
    ~~formatter util (duplicate m:ss math).~~
42. ~~`TelecomOutgoing.control` field is written but never read after~~ done — superseded — W161 generation guard shipped; `control` remains write-only bookkeeping (verified in code, 2026-10-28)
    ~~assignment — confirm it can be dropped or is needed for API edges.~~
43. ~~`CallConnectionService.placeOutgoingCall` + `TelecomOutgoing.place`~~ **NOT-DO — two deliberate guard sites — an engine-level dedup would blur Telecom ownership.**
    ~~both gate the single-call policy — one guard could live in the engine.~~
44. ~~Wire the W01 findings (renderer-kill rebuild, offline overlay) into~~ done — later reports (00-20 and on) carry the W01 findings in their evidence tables
    ~~the next status report's verification-evidence table.~~
45. After the billing fix: confirm the contrast gate runs green IN CI **→ TODO_LIST (confirm contrast green IN CI at the first green run, R13)**
    (python3 availability on the runner is assumed, not yet proven).
46. After the mirror attaches: unfreeze `domains` (114 behind — other repo). **→ TODO_LIST (domains unfreeze rides the PAT re-scope, R12b/N026 — other repo)**
47. ~~Consider tagging the report-evidence screenshots per pass~~ **NOT-DO — photos/ is flat and small; per-pass subdirs would be ceremony.**
    ~~(`photos/2026-10-25-w01/…`) so pairs stay attributable.~~
48. ~~Battery ask: add the settings deep-link result logging (currently~~ **NOT-DO — silent settings deep-link fallback is fail-safe; extra logging adds noise.**
    ~~silent fallback) — trivial, next code touch.~~
49. `NtfySubscriptionServiceTest`: add the ACTION_STOP → stopped-flag **→ TODO_LIST (pin the ntfy ACTION_STOP stopped-flag case in NtfySubscriptionServiceTest)**
    case (the stop-leak regression pin) — small gap I left.
50. ~~Sweep FEATURES.md for the two rows still claiming "rotation preserved~~ done — verified — configChanges retains rotation; only uiMode was removed (FEATURES wording is correct)
    ~~via configChanges" (dialer row) — uiMode left that list; rotation~~
    ~~itself is intact, wording should be exact.~~

## g) QUESTIONS FOR THE OPERATOR

1. **GitHub Actions billing** — can you fix the failed payment / raise
   the spending limit now? Everything else in CI is ready; the first
   green run (F041) and the Dependabot PR (#34) are hostage to it.
2. **Q1 live-stack access** — which test extension (+ account) may the
   app use against `pbx.artmann.tech`, and is the stack reachable from
   the emulator/phone? It gates both device trains, the attach/link
   verifications, and the battery-prompt paths.
3. **Tag timing** — cut `v0.1.0` NOW (CHANGELOG ready, honesty contract
   travels with the repo) or after the device trains? The `[0.1.0] -
   2026-10-23` CHANGELOG section exists; no git tag does.
