# Status — Pareto execution of the unblocked tiers + first device smoke (2026-10-24 18:55)

> Executes `docs/planning/2026-10-24_17-50_pareto-device-train-readiness.md`
> for everything NOT blocked on operator input (Q1: live stack origin +
> test account). Every code task passed the verification gate
> (`assembleDebug` + `zipalign -c -P 16 4` + `testDebugUnitTest`).

## a) What was executed (plan task → state)

| Plan task | State | Evidence |
| --------- | ----- | -------- |
| T03 FSI auto-answer fix (F019-F020) | DONE | `CallActivity.answerCall` is the only surface answer path; the FSI open never answers |
| T04 outbound Telecom de-ghost (F022-F026) | DONE, redesigned by discovery | The plan's `placeCall` approach was IMPOSSIBLE: the 3-arg `TelecomManager.placeCall` is absent from the API 36 stubs (javap-verified). Shipped `TelecomOutgoing` (`addCall`, API 34+) + direct-dial fallback (31-33); `onCreateOutgoingConnection` deleted (nothing can invoke it) |
| T05 settings surface (F028-F032) | DONE | `SettingsActivity`, persisted origin, first-run prompt, save → live teardown/re-wire |
| T06 registration state (F033-F036) | DONE | `CallEngine.registration` OFF/CONNECTING/REGISTERED; shell badge; dialer gates the Call button |
| T07 CI gate (F037-F040) | DONE (dormant) | `.github/workflows/ci.yml`; no git remote configured, first run awaits a push |
| T08 protocol unit tests (F041-F048) | DONE | 26 tests green: SipMessage (8), Digest (6), SipEngine sequences (6), DeviceIdentity (3), Robolectric (6: router 4 + launch 2) |
| T09 CANCEL (F049-F052) | DONE | Spec-correct CANCEL (INVITE's own Via, CSeq method swap, provisional To tag); confirmed-dialog guard; 487 → ACK → clean teardown — all pinned by tests |
| T10 ntfy persistence (F053-F054) | DONE | server/topic persisted; null-intent sticky restart resubscribes. F055 device kill-test still pending (needs a kill cycle on real hardware) |
| T11 release/R8 (F056-F058) | DONE | assembleRelease + R8 green; debug-signed release installed + launched on the emulator, zero fatals, UI identical |
| T12 emulator kit (F059-F061) | DONE | `.#emulator` devShell (API 36 image) + train/smoke scripts; documented in AGENTS.md |
| T13 logout deregister (F062) | DONE (honest scope) | Session watcher polls every 10 min; dead session → lane stop + teardown + BEST-EFFORT deregister (cookie usually already dead — the JS-bridge alternative still needs the operator's pick, F063/F064) |
| T14 dialpad UX (F065-F069) | DONE | configChanges rotation fix, recents (last 5), long-press clear, FAB styling (shared `ui/Fab`) |
| T15 Robolectric smoke (F070-F072) | DONE | Router tests (Telecom incoming + notification + ring_end + unknown-event) and launch tests (chrome + first-run dialog) |
| T17 strings/audio (F076-F078) | DONE | Strings extracted; REAL speaker toggle (communication-device API); audio focus deliberately NOT requested — Telecom owns it for self-managed calls (documented in KDoc) |
| T18 accessibility (F079-F080) | DONE (floor) | 48dp targets everywhere, backspace contentDescription; full TalkBack/contrast audit remains open |
| T19 notification UX (F081-F082) | DONE | POST_NOTIFICATIONS moved to post-registration; unregistered dialer state = disabled Call + hint |
| T20 docs sync (F083-F087) | DONE | Citations rewritten as symbols; DOMAIN_LANGUAGE +9 terms; CHANGELOG merged; status index created; AGENTS.md truth-restored |
| T22 call-screen robustness (F091-F092) | DONE | `onNewIntent` re-ring refresh; ended-screen auto-finish (3 s) |

## b) The headline: the first device smoke ran and CAUGHT A REAL CRASH

The plan's 1% (device trains) is blocked on Q1 — but T12 converted part
of it into a self-serve loop. The first emulator smoke:

1. **Found a fatal**: `setAcceptThirdPartyCookies(null, …)` NPEs inside
   the Chromium WebView glue on a real stack → app crashed on launch.
   Robolectric could never catch it (shadow tolerates null). Fixed.
2. **Found a UX gap**: saving a new origin left the OLD origin's page
   on screen (WebView only loaded in `onCreate`). Fixed: resume reloads
   when the origin changed. Walked the flow on-device (save → reload).
3. **Verified the chrome**: first-run dialog, teal FAB, SETTINGS,
   "Not connected" badge, honest disabled dialer — all render and behave.

The R8-minified release build was then installed and launched the same
way: zero fatals, identical UI. The release lane is proven, not assumed.

## c) Discovered-this-session facts worth remembering (now in AGENTS.md)

- API 36 removed `TelecomManager.placeCall(handle, uri, extras)` from
  the stubs — the plan's T04 approach would not have compiled. `addCall`
  is the only sanctioned outbound self-managed path on 34+.
- Robolectric tests MUST use `@Config(application = Application::class)`
  or the manifest `App` loads WebRTC's native library → UnsatisfiedLinkError.
- RFC 2617 §3.5's example nonce is 31 hex chars; a 32-char variant
  silently produces a different response hash (the test anchors on the
  RFC-exact value, independently re-computed with two tools).
- The default and emulator devShells carry different adb versions
  fighting over the same server port — use one per session.

## d) Quality verification this session

- Gate after every batch: `assembleDebug` + `zipalign -c -P 16 4` +
  `testDebugUnitTest` — all green at close.
- 26/26 tests green (10 failures during development, each diagnosed and
  fixed — two were REAL production bugs: digest regex greediness and
  missing request-line strictness; the rest were wrong test
  expectations, corrected rather than weakened).
- Both APKs (debug + debug-signed release) pass 16 KB alignment.
- On-device verification: install → launch → first-run prompt →
  settings save → origin reload → dialpad honest state — screenshotted.

## e) Still open (harvested into TODO_LIST.md)

- Thread-confinement audit of `CallEngine` (High — the one genuinely
  risky shortcut left).
- INVITE 401 ladder + silence watchdog (Medium; test seams ready).
- Second-call busy heads-up; CallStyle notifications (post-train).
- Live-stack everything (blocked on Q1): answer-path race, field
  contracts, two-way audio, ring-while-closed E2E.
- Logout deregistration decision (cookie-poll shipped vs JS bridge).

## f) Operator questions (unchanged + refined)

- **Q1**: live stack origin + test extension — now the ONLY blocker for
  the real trains; the emulator kit covers everything up to the wire.
- **Q3**: FSI auto-answer was removed as a defect (per plan assumption).
  If it was intended product behavior, revert = one-line change in
  `CallActivity.onCreate`.
- **Q2**: unchanged — measured by train task F013 once Q1 lands.

## g) Honesty statement

Nothing in this session rang a real phone: no live SIP gateway, no real
push server, no two-way audio. What IS proven: the app installs, launches,
renders, configures, persists, formats, aligns, and its protocol core
matches the RFCs under test. The README honesty contract still governs
everything that needs the live stack.
