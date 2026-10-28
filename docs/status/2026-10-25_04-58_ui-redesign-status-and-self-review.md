# Status — the UI redesign session: design system, screenshots, self-review (2026-10-25 04:58)

> Covers this session only: "make the app a lot prettier and modern"
> executed end-to-end — design plan → token system → four redesigned
> surfaces → new launcher icon → emulator visual verification with
> screenshot iterations → docs sync. One prior standing defect closed
> (speaker feedback). Final gate green: `assembleDebug` +
> `assembleRelease` + 25/25 tests + 16 KB zipalign on both APKs +
> `dprint check`. Living docs synced; the daemon carried the code.

## Design direction (the thinking, in short)

A phone should read as a phone: the identity comes from phone
vernacular — a live "line dot" (the signature element) in fixed dark
glass floating over the web, an always-dark call screen (calls happen
in dim rooms), a DayNight dialer, brand teal carried over from the
PWA. Circular targets, weight-and-spacing typography, system fonts,
no templated look. Behavior was frozen before styling: every flow
(single-call guard, permission gates, registration gate, re-ring,
auto-finish, save-teardown) is untouched code path.

## a) FULLY DONE (verified, evidence-based)

| Work                                                                                                                                                                             | Evidence                                                                                                                                                  |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Design-token system: light/dark palettes as theme attrs, DayNight theme, helpers                                                                                                 | `values/attrs.xml`, `values{,-night}/colors.xml`, `values{,-night}/themes.xml`, `ui/Design.kt` (`themeColor`, `iconButton`, `pillBackground`, `pulse`, …) |
| The pulse respects reduced motion                                                                                                                                                | `animationsEnabled()` reads `ANIMATOR_DURATION_SCALE`; pulse skipped when 0                                                                               |
| Vector icon set (call, call_end, mic, mic_off, speaker, pause, backspace, settings, dialpad)                                                                                     | 9 tintable 24dp drawables; every icon control has a contentDescription                                                                                    |
| Launcher icon: handset glyph in the safe zone + monochrome layer                                                                                                                 | `ic_launcher_foreground.xml` (2.3× scaled path), `<monochrome>` added; SEEN in the emulator splash screenshot — teal disc, white handset                  |
| Shell redesign: glass line pill + breathing dot, circular teal FAB, glass gear                                                                                                   | `MainActivity` rewritten; screenshot-verified light AND dark over a live (error) page — chrome stays legible on both                                      |
| Dialer redesign: circular keys with letters, centered pad, bottom-anchored actions                                                                                               | `DialpadActivity` rewritten; screenshot-verified light + dark; long-press 0 → `+` shipped as a bonus                                                      |
| Settings redesign: stroked card field, full-width teal Save pill                                                                                                                 | `SettingsActivity` rewritten; screenshot-verified; the saved origin `https://pbx.artmann.tech` renders in the field                                       |
| Call screen redesign (code): always-dark gradient, initials avatar, circular answer/decline pads (pulsing answer), lit toggles, DTMF behind a Keypad toggle, danger hang-up pill | `CallActivity` rewritten + `Theme.WebPhone.Call` in the manifest; the cramped 13-button DTMF row is gone — build-verified, visual check needs a live call |
| Speaker toggle now re-renders → lit/unlit feedback                                                                                                                               | Closed fuck-up #8 from the 03:12 report; FEATURES row updated                                                                                             |
| Dead code removed: `ui/Fab.kt` (superseded by the token system)                                                                                                                  | trashed; zero remaining references                                                                                                                        |
| Behavior frozen: all call flows unchanged                                                                                                                                        | 25/25 tests green after every batch; Robolectric launch smoke passes on the new shell                                                                     |
| Emulator photo pipeline cracked: serial-pinned adb, `adb root`, boot-wait, frame waits                                                                                           | 5 screenshots captured cleanly after the iteration below; light + dark                                                                                    |
| Docs synced                                                                                                                                                                      | FEATURES (3 rows + speaker), CHANGELOG (4 new entries), AGENTS (4 new gotchas), TODO_LIST (icon row closed)                                               |
| Final gate                                                                                                                                                                       | `assembleDebug` + `assembleRelease` + `testDebugUnitTest` + 16 KB zipalign on BOTH APKs + `dprint check` — all green at 04:5x                             |

## b) PARTIALLY DONE

| Work                            | Done                                                                     | Missing                                                                                                         |
| ------------------------------- | ------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------- |
| Call-screen VISUAL verification **→ TODO_LIST (ring train, R16/N044)**                         | Code + build + theme wiring verified                                     | Never SEEN: the ringing/active/ended screens need a live call (no stack available this session — see 502 below) |
| Pulse animation **→ TODO_LIST (ring train, R16)**                                              | Implemented + reduced-motion guard                                       | Static screenshots cannot show it; never observed running (emulator never reached REGISTERED)                   |
| Redesigned build on YOUR phone **→ TODO_LIST (First physical install, R14/N030-N032)**         | APK built and gate-green                                                 | Your phone still carries the PRE-redesign install — one `adb install -r` away                                   |
| ~~`scripts/emulator-photos.sh`~~ done — docs/status/2026-10-25_10-00 §a F057 (promoted + light/dark run 13-56 F058) | ~~The working photo script exists — in `/tmp`, session-local~~           | ~~Should be promoted into the repo (it encodes the serial-pinning + adb-root + wait lessons)~~                  |
| PWA icon parity **→ ROADMAP Open questions (icon parity)**                                     | Native icon deliberately diverged (handset vs the PWA's link glyph)      | Needs your decision — change both or neither (noted in AGENTS.md)                                               |
| ~~Day/night on the shell~~ done — the island pages ARE theme-aware; algorithmic darkening shipped (09-59) + visually proven (13-43 W101/W102) | ~~Chrome is fixed dark glass BY DESIGN; dark screenshot identical to light~~ | ~~The web island's own pages don't dark-mode (WebView darkening is a separate decision)~~                       |
| Live device trains **→ TODO_LIST (first device trains, R15/R16 — Q1)**                         | Everything self-serve is done                                            | Still blocked: your stack served **502 Bad Gateway** all session (nginx up, backend down)                       |

## c) NOT STARTED (standing list, unaffected by this session)

INVITE 401 re-auth ladder + silence watchdog (T25/F101-F103) ·
second-call busy UX (T23/F095-F096) · CallStyle notifications
(T21/F088-F090) · TLS-pinning memo (F107) · missed-call notification ·
call duration timer · dialpad haptics · battery-optimization
exemption prompt · CI first run (no git remote) · live contract
checks (F073-F075) · thread-confinement audit (F093) ·
shutdown-vs-ensureWired race (F094) · logout deregistration decision
(F063/F064) · F055 ntfy kill-cycle · TalkBack pass · contrast audit ·
emulator snapshots. None were promised this session.
**→ all resolved since:** 401 ladder + watchdog, busy UX, CallStyle, TLS memo (ROADMAP), missed-call, duration timer, haptics, battery prompt — shipped in the 10-00 pass; CI trigger added (10-00 F040, first green → TODO R13); live contracts → TODO R20; F093/F094 shipped (confinement + AttachGuard); logout decision → TODO R21; F055 proven (13-56); TalkBack → TODO R23; contrast gated (P10, CI); snapshots → ROADMAP Decisions (OFF).

## d) TOTALLY FUCKED UP (this session's mistakes, unvarnished)

1. **Same-class compile errors fixed instance-by-instance, not
   pattern-by-pattern.** The first build after the rewrites failed
   with 13 errors that were really TWO mistakes: `minWidth`/`minHeight`
   vs `minimumWidth`/`minimumHeight` (three files) and insets listeners
   returning the view instead of the insets (four files). The first
   error of each class should have triggered a repo-wide grep.
2. **I invented a string resource that didn't exist**
   (`enter_a_number_hint`) and left a nonsense leftover line
   (`themeColor(android.R.attr.colorForeground).let { … }`) in the
   first MainActivity draft. Both caught before commit, but a grep of
   strings.xml before referencing would have prevented the first, and
   a re-read before moving on the second.
3. **Predictable adb failure, not predicted.** YOUR phone was paired
   to this machine's adb server earlier TONIGHT, and my first emulator
   photo run still died on "more than one device/emulator". The script
   should have pinned `-s <serial>` from its first draft.
4. **sed surgery on the photo script instead of a rewrite** — the
   in-place edit left the script half-renamed (`A` calls with no
   `A()` helper), wasting an entire emulator boot (~2 min) before you
   told me to keep going and I rewrote it cleanly.
5. **Three emulator boots to get one clean photo pass**: multi-device
   (fixable day-one), then `SecurityException` (API 36 denies `am
   start` on non-exported activities from the shell uid — needed
   `adb root`, knowable up front), then a splash screenshot (8 s is
   not enough for a cold WebView start; needed 20 s). Each was
   diagnosed and fixed correctly, but the sequence cost ~6 minutes
   and three boots for what a more careful script would have done in
   one.
6. **Design.kt was written missing an import and then "fixed" via an
   edit whose old_string didn't match** — sloppy first draft plus a
   stale-read dance; two wasted tool calls.
7. **FEATURES.md stale-read rejections**: two multiedit attempts
   bounced on mtime tracking before I re-viewed and re-applied. The
   "re-view before edit" rule exists; I applied it to TODO_LIST but
   not FEATURES first.
8. **Standing record from earlier sessions** (rm usage, CHANGELOG
   wipe, stale-APK install, overclaimed todos, unfixed smoke script):
   not repeated tonight, not yet redeemed either — the emulator-smoke
   script still contains its adb flaw.

## e) WHAT WE SHOULD IMPROVE (process)

1. **Fix error classes, not instances** — first compiler error of a
   kind → grep the repo for every sibling before editing.
2. **Grep resources before referencing them** (strings, colors,
   drawables) — the invented string key was free to prevent.
3. **Write device-automation scripts defensive from line one**:
   serial-pinned adb, `adb root` for non-exported starts, generous
   boot/frame waits. Then leave sed alone and rewrite the file.
4. **Every surface claimed "restyled" must be SEEN** — the call screen
   stays honestly partial until a screenshot shows it ringing.
5. **Promote session scripts with reuse value into `scripts/`** —
   `/tmp` is where lessons go to die.
6. **Re-view before edit applies to every file, every time** (the
   FEATURES dance).

## f) Up to 50 next things (ranked: unblock-live first)

1. Reinstall the redesigned APK on your phone (built, gate-green) **→ TODO_LIST (First physical install, R14/N030-N032)**
2. ~~Fix/inspect the **502 Bad Gateway** on `pbx.artmann.tech` — it~~ done — resolved 2026-10-25 — the stack serves the island again (fetched live during the docs-health audit)
   ~~blocked every live check this session~~
3. Real device trains (outbound E2E, ring-while-closed E2E) — needs a **→ TODO_LIST (first device trains, R15/R16 — Q1)**
   test extension/account once the stack is up
4. Visually verify the redesigned call screen on a live call **→ TODO_LIST (ring train call-screen visuals, R16/N044)**
   (ringing, active, ended)
5. Measure the answer-path race on the live stack (F013); file the **→ TODO_LIST (Verify the answer-path race, R16/N041)**
   webphone re-INVITE issue if needed
6. ~~Promote `/tmp/wp-photos.sh` → `scripts/emulator-photos.sh`~~ done — docs/status/2026-10-25_10-00 §a F057 (script shipped)
   ~~(serial-pinned, rooted, waits baked in)~~
7. ~~Thread-confinement audit of `CallEngine` (F093)~~ done — docs/status/2026-10-25_10-00 §a T07 (single-lock confinement)
8. ~~Decide + fix `shutdown` vs in-flight `ensureWired` race (F094)~~ done — docs/status/2026-10-25_10-00 §a T08 (AttachGuard)
9. ~~INVITE 401 re-auth ladder + unit tests (F101, F103)~~ done — docs/status/2026-10-25_10-00 §a T09 (SipEngineAuthTest)
10. ~~Silence watchdog re-register + unit tests (F102, F103)~~ done — docs/status/2026-10-25_10-00 §a T13 (silence watchdog)
11. Logout deregistration: cookie-poll vs JS bridge (F063/F064) **→ TODO_LIST (Logout deregistration, R21/N054-N056)**
12. ~~Execute F055: `am kill` sticky-restart test for the ntfy lane~~ done — docs/status/2026-10-25_13-56 §a F035 (kill-cycle proven)
13. PWA icon parity decision (handset vs link glyph — both or neither) **→ routed to ROADMAP Open questions (icon parity)**
14. ~~CallStyle incoming notification (F088)~~ done — docs/status/2026-10-25_10-00 §a T21 (incoming CallStyle)
15. ~~CallStyle ongoing notification (F089)~~ done — docs/status/2026-10-25_10-00 §a T21 (ongoing CallStyle)
16. Live contract checks: `/api/session` field names (F073) **→ TODO_LIST (Live contract checks, R20/N052)**
17. Live contract checks: push payload shapes (F074) **→ TODO_LIST (Live contract checks, R20/N052)**
18. Live contract checks: CSRF dance (F075) **→ TODO_LIST (Live contract checks, R20/N053)**
19. ~~Busy heads-up + FSI suppression for a second call (F095-F096)~~ done — docs/status/2026-10-25_10-00 §a T20/T22 (missed-call policy)
20. Git remote → CI first green run (F040) → keep it green **→ TODO_LIST (GitHub billing → first green run, R13/N029)**
21. ~~TLS-pinning memo (F107)~~ done — ROADMAP Decisions (TLS NOT pinned; rationale recorded)
22. ~~LICENSE decision + file (F098)~~ done — docs/status/2026-10-25_10-00 §a F075 (MIT LICENSE)
23. ~~Fix `scripts/emulator-smoke.sh` adb robustness (or fold into the~~ done — docs/status/2026-10-25_10-00 §a T11 (serial pinning + fail-fast)
    ~~photos script and retire it)~~
24. ~~Debug-signed release smoke mode in the train script~~ done — docs/status/2026-10-25_10-00 §a F085 (--release mode; first green run 13-56 W180)
25. ~~Recents chips go stale after an in-place call (pre-existing flaw)~~ done — docs/status/2026-10-25_10-00 §a T23 (recents re-read on resume)
26. TalkBack pass over every programmatic surface (F079 remainder) **→ TODO_LIST (TalkBack walkthrough, R23/N060)**
27. ~~Contrast audit including the new token palette (F080 remainder)~~ done — docs/status/2026-10-25_10-00 §a T18 + contrast gate (P10, CI)
28. ~~Show WHY registration failed (network vs 401) on badge/dialer~~ done — docs/status/2026-10-25_10-00 §a T22 (badge + dialer cause)
29. ~~Missed-call notification when `ring_end` lands unanswered~~ done — docs/status/2026-10-25_10-00 §a T20/T22 (missed-call notes)
30. ~~In-call duration timer on the call screen~~ done — docs/status/2026-10-25_10-00 §a T23 (Active · m:ss clock)
31. ~~Haptic feedback on dialpad keys~~ done — docs/status/2026-10-25_10-00 §a T23 (haptics)
32. WebView dark-mode decision for the island's pages (algorithmic **→ routed to ROADMAP Open questions (WebView dark-mode)**
    darkening or leave-as-is)
33. ~~Stale theme colors after a day/night switch (`configChanges`~~ done — now a TODO_LIST row (added 2026-10-25, docs-health audit)
    ~~includes `uiMode`, so activities skip recreation — colors resolve~~
    ~~once). Re-resolve on `onConfigurationChanged` or drop uiMode.~~
34. ~~Robolectric test for the SettingsActivity save flow~~ done — docs/status/2026-10-25_10-00 §a F070 (SettingsActivityTest)
35. ~~Engine-level unit test for `CallEngine.hangUp` phase dispatch~~ done — docs/status/2026-10-25_10-00 §a F071 (CallEngineTest)
36. ~~Suppress or fix the AGP-vs-compileSdk-36 warning~~ done — property added 10-00; MOOT — removed by the AGP 9 jump (2026-10-28)
37. ~~Clean up the stale `local.properties` sdk.dir~~ done — docs/status/2026-10-25_10-00 §a F073 (trashed, build green)
38. ~~Battery-optimization exemption prompt for the ntfy lane (Doze)~~ done — docs/status/2026-10-25_10-00 §a T16 (ask shipped; allow/deny verify → TODO R23)
39. WorkManager as a fallback ntfy reconnect path **→ routed to ROADMAP theme 1 (WorkManager fallback reconnect)**
40. Verify webrtc consumer R8 rules against a REAL release call **→ TODO_LIST (first device train, R15/R16 — R8 keeps vs a real call)**
41. FCM lane enablement once `google-services.json` exists (operator) **→ FEATURES (FCM lane DISABLED — awaits google-services.json)**
42. Cookie-expiry mid-call behavior check (F106) **→ TODO_LIST (Live contract checks, R20)**
43. Retire honesty markers post-train (F104) + ROADMAP revisit (F105) **→ TODO_LIST (Post-train docs sweep, R26/N066)**
44. ~~Emulator snapshot reuse for faster boots~~ done — ROADMAP Decisions (emulator snapshots OFF — determinism)
45. ~~API 31-33 outbound Telecom: reflection or document the fallback~~ done — ROADMAP Decisions (API 31-33 direct-dial permanent until minSdk 34)
46. Crash reporting decision (self-hosted ACRA — or none by policy) **→ routed to ROADMAP theme 1 (crash reporting decision)**
47. Call audio: verify AEC/NS engages on device (audio-quality train) **→ TODO_LIST (first device train, R15/N037 — AEC/NS check)**
48. `CallEndpoint`-based speaker routing on API 34+ (align addCall) **→ routed to ROADMAP theme 1 (CallEndpoint speaker routing)**
49. Baseline profiles (startup + call path) **→ routed to ROADMAP theme 1 (baseline profiles)**
50. FreeSWITCH second-call behavior: CANCEL vs BYE when caller gives up **→ TODO_LIST (ring train, R16/N038 — FreeSWITCH CANCEL-vs-BYE)**

## g) Questions only you can answer

1. **The live stack**: `pbx.artmann.tech` served 502 all session — is
   that known maintenance, or will you go poke the backend? And once
   it's up: which test extension (+ account, if login needs one) may I
   use for the device trains and the call-screen verification? **→ the 502 cleared — the stack serves again (verified 2026-10-25); the test extension is still owed (ROADMAP Open questions)**
2. **Logout semantics (drives F063/F064)**: when a user logs out on
   the island, does the server KEEP the `/api/push-devices` rows (the
   native side must deregister BEFORE the cookie dies → JS bridge
   needed), or PURGE them (the shipped cookie-poll watcher suffices)? **→ still open — ROADMAP Open questions**
3. **Icon parity**: the native launcher now leads with the handset
   glyph; the PWA still uses the link glyph. Keep them diverged, or
   should the webphone repo adopt the handset too (both or neither)? **→ routed to ROADMAP Open questions (icon parity)**
