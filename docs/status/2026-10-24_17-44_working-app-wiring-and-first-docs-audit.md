# Status Report — 2026-10-24 17:44

> Session scope: AGENTS.md creation → docs-health audit (5 docs built) →
> "implement a working app" (dead-wiring fixes across shell/push/call) → this
> self-review. Everything below derives from THIS session's work and what was
> noticed along the way. Format: `.md` at the operator's explicit path request
> (canonical HTML dashboard overridden by instruction).

## a) FULLY DONE

1. **AGENTS.md created** (root) — build commands, architecture flows, gotchas
   (DTMF equals-form, hostToken, mic-FGS ban, FCM dormancy); 8.1 KB, within
   budget; temporal pollution removed in-audit; dprint-formatted.
2. **Full doc set built** (docs-health BUILD): `FEATURES.md` (honest inventory,
   evidence column), `TODO_LIST.md`, `CHANGELOG.md` (Keep-a-Changelog, 0.1.0 +
   Unreleased), `ROADMAP.md` (themes + non-goals), `docs/DOMAIN_LANGUAGE.md`
   (19 terms, all greppable in code).
3. **docs-health AUDIT executed** — first audit, no baseline. As-found scores:
   Accuracy 9.75/10, Fitness 4.25/10 (5 missing must-haves + 1 Med-High).
   Findings fixed in-audit: AGENTS.md date pollution; two false-present-tense
   KDoc claims (`FcmReceiver` "kicked from MainActivity", `RingRouter` FSI
   check "runtime-check" that didn't exist).
4. **Session seam closed** — `SessionApi` now bridges read+write through the
   WebView's `CookieManager` (was a private in-memory jar that never saw the
   login cookie → `/api/session` could never authenticate).
5. **`CallLayer` created** (`shell/CallLayer.kt`) — idempotent one-engine
   process wire; callable from MainActivity resume AND ring pushes while
   closed; single-flight `attachOnce`.
6. **Push-device registration wired** — ntfy topic always, FCM token
   best-effort (`Tasks.await` guarded); FCM `onNewToken` rotation registers off
   the main thread. This was the #1 dead wire: server never learned any wake
   address before.
7. **Dialer entry shipped** — `call/DialpadActivity.kt` (numpad,
   mic-permission gate, single-call guard: live call → call screen instead of
   second dial) + dial button on the shell; hang-up control during outgoing
   ring-out added to `CallActivity`.
8. **Ring path hardened** — `RingRouter`: FSI runtime check
   (`canUseFullScreenIntent()`) with heads-up degrade, notifications-enabled
   guard; `ring_end` now stands down the Telecom connection
   (`standDownRinging`) + `CallEngine.cancelRinging()`, not just the
   notification; ring bootstraps `CallLayer` so the INVITE has a leg to land
   on.
9. **Telecom fixes** — active-connection tracking (was unreferenced: no way to
   tear a ringing connection down); BT/watch `onAnswer` now starts the call
   FGS (was: answer without foreground service).
10. **Leaks fixed** — `CallEngine.detach()` now disconnects the SipEngine
    (socket + refresh/watchdog jobs leaked before); engine churn on every
    `onResume` eliminated (was: new WSS connection per resume); ntfy service
    duplicate-socket-per-resume fixed, reconnect made single-flight, inverted
    socket-null guard corrected.
11. **`POST_NOTIFICATIONS` runtime request** on first launch (API 33+);
    strings + manifest entries added.
12. **Build gate verified** — `assembleDebug` BUILD SUCCESSFUL and
    `zipalign -c -P 16 4` PASS on the fresh APK (build-tools 35).
13. **Repo hygiene** — `.kotlin/` build-state untracked + gitignored.
14. **Docs synced** to the new reality (FEATURES rows flipped BROKEN→PARTIAL,
    TODO removals, CHANGELOG Unreleased entries) — no status split brains
    against the code as of build time.

## b) PARTIALLY DONE

- **"A working app"** — every end-to-end path is now WIRED (login → session →
  SIP register → dial → audio; push → FSI → answer), but **zero runtime
  verification exists**: no emulator in the devShell (`includeEmulator =
  false`), no device, no test. "Working" today = compiles + logically complete
  paths. The claim stops at the build gate.
- **Push registration** — wired, never exercised against live
  `/api/push-devices`; field contract (`device_id`/`transport`/`token`)
  unverified against the webphone server.
- **Cookie bridge** — implemented; never run against a real login flow
  (Set-Cookie shape, cookie flags, domain matching unverified).
- **FSI degrade + POST_NOTIFICATIONS** — implemented, never rendered once.
- **Dialpad** — functional but raw: no theming (a plain Button floats over the
  WebView), no accessibility descriptions, no recent-numbers, typed number
  lost on rotation (see d-5).
- **Docs evidence precision** — FEATURES/TODO line-number citations
  (`SessionApi.kt:103`, `CallEngine.kt:69`, etc.) were written BEFORE the code
  rewrites and are now stale by a few lines (see d-2).
- **AGENTS.md vs DOMAIN_LANGUAGE** — AGENTS documents the bootstrap race and
  CallLayer; DOMAIN_LANGUAGE doesn't yet (CallLayer, stand-down, bootstrap
  race missing as terms).

## c) NOT STARTED

1. **First device train** — ALL live-infrastructure validation (REGISTER,
   dial, answer, DTMF, hold, push lanes, REFER/NOTIFY).
2. **Settings surface** — `App.origin` still hardcoded
   `https://pbx.example.org`; the app is unusable against a real stack until
   this is set in code or a UI lands.
3. **Logout → `deregisterPushDevice`** — still zero callers (needs JS bridge
   or cookie poll).
4. **CI** — `.github/` holds only dependabot; `app/build.gradle.kts:11`
   claims "verified per release in CI" that does not exist.
5. **CallStyle-composed notification** — planned with first device train.
6. **Tests** — zero test source set; nothing started.
7. **Answer-path race verification** — does the server re-INVITE long enough
   for the closed-app bootstrap? Unknown until live test or a webphone-repo
   answer.
8. **Release build** — `assembleRelease` never run; R8 config (keeps only
   `org.webrtc.**` + `FcmReceiver`) unverified against minification.
9. **docs/status/ index** — this is the first status report; no README index
   exists yet.

## d) TOTALLY FUCKED UP!

1. **Auto-answer on full-screen-intent open (pre-existing, preserved, and
   missed twice).** `CallActivity.startCall()` calls `CallEngine.answer()` the
   moment the activity opens with mic permission granted — i.e. the incoming
   call is ANSWERED as soon as the FSI wakes the screen, without a user tap,
   racing the Answer/Decline buttons that render below. Privacy/UX bug. I
   edited `CallActivity` twice this session and flagged it never.
2. **The docs-health auditor shipped stale evidence.** FEATURES/TODO cite
   `file:line` numbers that my own later rewrites shifted (CallEngine +9
   lines, SessionApi rewritten, RingRouter rewritten to line-less citations
   only partially). The skill's own rule — verify every citation — was
   violated by the same session that wrote it.
3. **"Working fucking app" framing overstated.** Correct statement: all dead
   wiring is fixed and the build+alignment gate passes; without a real stack
   origin and live infra the app still does nothing user-visible. I caveated
   it, but the headline ("Fair. Fixing all of it now") invited the stronger
   reading.
4. **Outbound Telecom integration is a ghost system.**
   `CallConnectionService.onCreateOutgoingConnection` exists (and I extended
   it — `activeConnection` tracking for the outgoing path) but NOTHING ever
   invokes it: dialing goes straight to `CallEngine`, never
   `TelecomManager.placeCall`/`addNewOutgoingCall`. Outbound calls are
   invisible to BT/watches/OS call state. I wired the ghost's bookkeeping
   instead of noticing it's a ghost.
5. **Dialpad rotation loses the typed number.** Programmatic `EditText`
   without `android:id` → no view-state restoration; `DialpadActivity` has no
   `configChanges` → rotation recreates and blanks the number. Found in this
   self-review, not before shipping the file.
6. **Ntfy lane dies on sticky restart (pre-existing, unfixed).**
   `START_STICKY` + process kill → service restarts with a null intent →
   server/topic null → `stopSelf()` → subscription silently dead until the
   next MainActivity resume. My idempotent-subscribe fix did not cover the
   sticky-restart lane.
7. **CANCEL gap.** Hang-up during outgoing ring-out sends BYE before the 200
   OK — SIP prescribes CANCEL pre-answer; early BYE is tolerated by some
   servers, mishandled by others. `SipEngine` has no CANCEL at all.
8. **Process slips (recovered):** one multiedit partially applied (caught by
   re-reading); one edit rejected on stale read after dprint reformatted
   (caught by re-reading); zipalign "passed" once against a stale APK during
   the failed build (meaningless check, re-run fresh).

## e) WHAT WE SHOULD IMPROVE!

- **Runtime verification loop.** No emulator in the devShell, no Robolectric,
  no device farm — every "fix" this session is type-checked only. Add an
  emulator to `flake.nix` or a JVM test layer before the next feature.
- **Unit tests for the pure logic NOW.** `SipMessage.parse/render`,
  `Digest.parseChallenge/authorization`, `DeviceIdentity`, the cookie bridge —
  all pure-JVM-testable protocol logic with zero tests. A SIP stack without
  message-level tests is indefensible.
- **Registration state is invisible.** `CallEngine.onRegistrationState = Unit`
  — the user can never tell whether SIP is up. Expose it in `CallState` and
  the shell UI.
- **Ghost-system discipline.** The outbound-Telecom ghost (d-4) and the unused
  `speaker` string (`strings.xml` defines it; no audio routing exists) both
  survived two review passes. Grep for dead entry points before declaring a
  layer "wired."
- **Citation discipline.** Docs cite `file:line`; code edits invalidate them.
  Either re-verify citations in the same change or cite symbols only.
- **Hardcoded UI strings** in `CallActivity` ("Muted", "Active", "Ended",
  "Close", dialpad hint "100") bypass `strings.xml` — i18n debt accumulating
  in exactly the surface users see.
- **Release lane unverified.** One `assembleRelease` run would prove or break
  the R8 config; it has never been run.
- **Prompt-timing UX.** `POST_NOTIFICATIONS` fires on cold start before any
  context — a bare system dialog as the first interaction. Ask after login
  instead.
- **Standing HARVEST habit.** New defects found during self-review must land
  in TODO_LIST in the same session (done for this report; keep it standing).

## f) Up to 50 things to get done next

Sorted by impact within tiers; effort rough.

**High impact**

1. First device train: dial → audio → hang-up against the live stack (needs
   real `App.origin`; blocks all honesty-contract retirement)
2. First device train: incoming push → FSI → answer → audio; verify the
   bootstrap race actually wins (ring while closed)
3. Fix the FSI auto-answer: require an explicit Answer tap (decision needed —
   see question 3)
4. Wire outbound calls through Telecom (`placeCall` with the self-managed
   account) so BT/watch see them — integrate or delete the ghost
5. Registration state → `CallState` + visible UI affordance (users must see
   "connected to PBX")
6. Settings surface for the stack origin (kill the hardcoded
   `pbx.example.org`)
7. Unit tests: `SipMessage` parse/render round-trips (pure JVM, hours, huge
   safety)
8. Unit tests: `Digest` (MD5 + SHA-256 + qop variants) against RFC vectors
9. CI workflow: `assembleDebug` + `zipalign -c -P 16 4` gate (the comment in
   `build.gradle.kts` claims it exists)
10. ntfy sticky-restart fix: persist server/topic (prefs) and re-subscribe on
    null-intent restart
11. CANCEL support in `SipEngine` (hang-up during ring-out)
12. Logout → `deregisterPushDevice` (JS bridge or cookie poll)

**Medium impact**
13. `assembleRelease` once; fix whatever R8 breaks
14. Cookie bridge live verification (Set-Cookie shape, flags, domain)
15. Push-device contract verification against live `/api/push-devices`
16. Dialpad rotation fix (`android:id`/`onSaveInstanceState` or
`configChanges`)
17. CallActivity `onNewIntent` handling (re-ring while the call screen is
alive)
18. Second incoming call while a call is live: FSI suppression/busy UX
19. Audio focus request during calls (`AudioManager`, speakerphone routing —
wire the dead `speaker` string or remove it)
20. Extract hardcoded UI strings to `strings.xml`
21. Accessibility pass: dialpad/in-call button contentDescriptions, large
touch targets
22. Robolectric or emulator smoke test for MainActivity → CallLayer wiring
23. FSI-degrade path runtime check (deny FSI on a test device, observe
heads-up)
24. `POST_NOTIFICATIONS` prompt moved to post-login context
25. Registration-failure surfaced in dialer (disable Call button until
registered?)
26. Re-verify/refresh all `file:line` citations in FEATURES/TODO (or switch
to symbol-only citations)
27. DOMAIN_LANGUAGE: add CallLayer, stand-down, bootstrap race, single-call
guard terms
28. CHANGELOG: note `.kotlin/` untracking; add a git tag `v0.1.0` or drop the
dated 0.1.0 heading convention mismatch (no tags exist)
29. `docs/status/README.md` index (first entry exists now)
30. CallStyle-composed in-call notification (Telecom polish)
31. BT answer path: log/handle `CallService.start` failure from `onAnswer`
(currently swallowed by `runCatching`)
32. Emulator added to `flake.nix` devShell (or documented device-train kit)

**Lower impact / polish**
33. Dialpad: recent-numbers memory (last 5, prefs)
34. Dialpad: long-press backspace = clear all
35. Dialpad: proper FAB/theming instead of a raw Button over the WebView
36. CallActivity: auto-finish a few seconds after ENDED (manual Close only
today)
37. `CallEngine.pendingInvite` thread-safety audit (OkHttp threads vs main)
38. `CallLayer.shutdown` vs in-flight `ensureWired` race audit (post-shutdown
re-attach is currently possible and maybe fine — decide deliberately)
39. Deprecation sweep: `onBackPressed` → OnBackPressedDispatcher (works today;
`enableOnBackInvokedCallback` is already set)
40. App icon: currently default adaptive launcher placeholder
41. Network security config / cert pinning consideration for the stack origin
42. `gradle.properties`: enable configuration cache (build speed)
43. Dependabot: add `github-actions` ecosystem once workflows exist
44. dprint `check` mode wired into CI (formatting gate)
45. README "Status" section refresh after the device train (honesty contract
markers retire one by one)
46. Investigate `SipEngine` re-REGISTER after 401-stale-nonce (auth retry
ladder robustness)
47. Watchdog: re-register even when socket looks alive but registration
expired (silence detection beyond ping frames)
48. Session timeout UX: cookie expiry mid-call behavior (call survives, next
resume re-auths — verify)
49. LICENSE file decision (none present)
50. ROADMAP revisit after device train (multi-line question, provisioning)

## g) Questions I can NOT figure out myself

1. **Live stack for the first device train:** is there a webphone stack URL +
   a test extension account I can point the app at (and does its
   `/api/session` actually return `websocket_url`, or is the `/sip`
   derivation the real contract)? Without this, items f-1/f-2/f-14/f-15 stay
   blocked.
2. **Server re-INVITE behavior (cross-repo contract):** when the webphone
   server fans out a `ring` push, does it re-INVITE the WSS leg long enough
   for a cold-bootstrapped client to catch it — or does the INVITE fire once?
   This decides whether the answer-path race is won by design or needs a
   server-side retry/payload change (the answer lives in the webphone repo's
   `internal/push`, not here).
3. **Product decision — FSI auto-answer:** is answer-on-activity-open
   intended behavior ("opening the call screen means answering") or a bug
   (require an explicit Answer tap, FSI just reveals the buttons)? It changes
   d-3/f-3 and the CallStyle work.

---

_Report basis: this session only (AGENTS.md → docs audit → implementation →
self-review). No unrelated research performed. Auto-commit daemon will pick
this file up; TODO_LIST updated with the newly-found defects (d-1, d-4, d-5,
d-6, d-7, e-citations) in the same session._
