# Status — R03 Redirect Truth, Deep-Link Train, Detour Hardening (2026-10-28 00:20)

> **DONE-AT 2026-10-28 (post-report):** the gate this report waited on
> closed GREEN (94 tests / 0 failures, zipalign 16KB OK) after two
> fixes: the suite-wedge root cause (leaked ntfy service controllers
> kept reconnect threads armed for the whole JVM — destroy-in-finally)
> and the Robolectric teardown doctrine (synchronous ActivityController,
> never ActivityScenario.close). R07/R09/R10/R11 then shipped; the wave
> is COMPLETE. The report's "next tasks" list rows covering R07-R11 are
> done; the owner/credential rows stand.

> **Session scope:** execution of the agent-executable wave from
> `docs/planning/2026-10-27_15-17_SUPERB-detour-hardening-and-train-unblocks.md`
> — R03 (redirect reality-check) first, then R01 (detour hardening), R02
> (emulator deep-link train), R04 (assetlinks.sh), R05 (memory hygiene),
> R06 (onNewIntent seam). R07-R11 researched but not landed (below).
>
> **Headline: R03's verdict is ASSUMPTION BROKEN — and that is the win.**
> The auth-detour design rested on "HTTP redirects never consult
> `shouldOverrideUrlLoading`". Modern Chromium DOES consult it for server
> redirects. The on-device proof caught the IdP hop being judged and
> ejected to Chrome BEFORE any commit could bless it — meaning the shipped
> session-1 detour could never have activated in real OAuth. The fix (the
> `isRedirect` seam) is implemented, unit-tested, and PROVEN on the
> emulator: the Pocket ID consent + authenticate pages render inside the
> shell with the bless log line and zero Chrome tasks.
>
> **Test count: 91 green** at the last completed gate (R01 wave). The
> R06-wave gate (93 expected) is NOT yet confirmed — see (d)1.

## a) FULLY DONE

Each item has evidence. "Gate" = `assembleDebug` + `testDebugUnitTest` green
(91 tests, 0 failures, counted from result XMLs) + 16 KB zipalign on the
then-current APK.

| #  | Done                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | Evidence                                                                                                                            |
| -- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| 1  | **R03 executed — redirect premise BUSTED with a logcat smoking gun**: after the deep-linked `begin` load, ActivityTaskManager recorded our app (uid 10219, source MainActivity) handing `https://auth.larsartmann.cloud/...` (Pocket ID) to Chrome — proof the 302 target was JUDGED by `shouldOverrideUrlLoading` and handed off before any `doUpdateVisitedHistory` commit                                                                                                          | emulator logcat START record, 2026-10-27 ~18:15; N011 "assumption breaks" path taken                                                |
| 2  | **The redirect seam fix** (`WebSurface.navigateOut(url, isServerRedirect, isMainFrame)`): a MAIN-FRAME server redirect while no detour is live blesses that authority in-shell (the origin's own chain, same trust argument); clicks (`isRedirect=false`) still hand off (W105 preserved); iframe redirects never bless; non-http(s) redirects never bless                                                                                                                            | `app/src/main/java/.../shell/WebSurface.kt`; unit test `a server redirect blesses the detour but a click never does` (5 assertions) |
| 3  | **R03 on-device proof of the fix**: deep link → logcat `WebSurface: auth detour blessed via redirect: auth.larsartmann.cloud`; Pocket ID consent page AND the authenticate page render INSIDE the shell (native FAB floating over it); MainActivity stayed top-resumed the whole time; no Chrome task                                                                                                                                                                                 | emulator screenshots + logcat, 18:20-18:21                                                                                          |
| 4  | **The two lying KDoc blocks corrected** (the "redirects NEVER consult SOUL" premise at `doUpdateVisitedHistory` and inside `onCommittedNavigation`) — comments now name both seams and which arrivals each owns                                                                                                                                                                                                                                                                       | `WebSurface.kt` diff                                                                                                                |
| 5  | **N005 detour-transition debug log** — logs only TRANSITIONS (bless/clear, deduped by authority) in the `WebSurface` tag; produced the exact on-device proof line                                                                                                                                                                                                                                                                                                                     | code + observed logcat                                                                                                              |
| 6  | **R01/N001 — `retry()` clears `authDetour`** before reloading the origin (an offline retry never commits, so the commit hooks alone would leave a stale blessing)                                                                                                                                                                                                                                                                                                                     | code + test `retry after an offline detoured page clears the stale blessing`                                                        |
| 7  | **R01/N003 — `onPageStarted` belt**: main-frame page START runs the same bless/clear machine (idempotent; catches loads whose commit may never fire, e.g. crashed renderer mid-load)                                                                                                                                                                                                                                                                                                  | code + test `onPageStarted is the belt - foreign start blesses, origin start clears`                                                |
| 8  | **R02 deep-link train, ALL rows** (see table below)                                                                                                                                                                                                                                                                                                                                                                                                                                   | emulator, one adb per session, `adb root` discipline                                                                                |
| 9  | **R04 `scripts/assetlinks.sh`** with keystore AUTO-DISCOVERY + apksigner cross-check that REFUSES to emit a fingerprint that doesn't sign the built APK — provocation-tested (wrong keystore → refuses, exit 1)                                                                                                                                                                                                                                                                       | script + `pm get-app-links` signature match `84:E2:C2:…:5D:7D:81`                                                                   |
| 10 | **R05 memory hygiene** — AGENTS.md: status header refreshed (91 tests, deep links + detour shipped with honest R17 caveat), zipalign full-path invocation (build-tools 35, NOT on PATH), rg `-r` trap gotcha, CORRECTED auth-detour gotcha (the session-1 premise text replaced), the debug-keystore XDG trap, the app-links resolution reality (unverified autoVerify loses to Chrome; `pm set-app-links-user-selection` simulates post-R18), uiautomator-dump coordinate discipline | AGENTS.md diff                                                                                                                      |
| 11 | **R06/N017+N018 — `onNewIntentDelegate` test seam** + warm-intent tests pinning BOTH `setIntent` (activity.intent re-aims) and routing (https shell path + tel dialer prefill)                                                                                                                                                                                                                                                                                                        | `MainActivity.kt`, `DeepLinkIntakeTest.kt` (2 new/updated tests)                                                                    |

**R02 train detail** (all through the REAL intent-resolution stack):

| Link                                              | Path taken                                                                                                 | Result                                                                                            |
| ------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `tel:+491701234567`                               | implicit → system resolver (WebPhone vs Google Phone) → Just once                                          | DialpadActivity prefilled `+491701234567`                                                         |
| `https://pbx.artmann.tech/messages`               | implicit → resolved to the APP after `pm set-app-links-user-selection` (user-selection simulates post-R18) | `onNewIntent` → shell loads the path; server bounces unauthenticated to the login door (expected) |
| `https://pbx.artmann.tech/deeplink-path-probe-42` | implicit (same)                                                                                            | the origin's styled 404 ECHOES the exact path in-shell — decisive intake proof                    |
| `sip:100@pbx.artmann.tech`                        | implicit, DIRECT (sole handler, no resolver)                                                               | dialpad prefilled `100` (user part)                                                               |
| `callto:+49170987654`                             | implicit, DIRECT                                                                                           | dialpad prefilled `+49170987654`                                                                  |
| `https://evil.example/phish`                      | explicit start (foreign hosts never route to us)                                                           | home state, WebView untouched, ZERO Chrome tasks, no hand-off loop                                |

## b) PARTIALLY DONE

1. ~~**The R06-wave gate is NOT green-confirmed.** What works: the R06 code +~~ done — RESOLVED post-report — the clean gate closed GREEN (94/94) after the teardown doctrine fixes; recorded in the header
   ~~tests compile; the R01-R05 gate passed at 91/0. What remains: a clean~~
   ~~`testDebugUnitTest` (93 expected) + zipalign on the CURRENT APK + dprint~~
   ~~on the touched `.md` files. Blocker: the Gradle queue (see (d)1) — at~~
   ~~report time the clean re-run is STILL waiting (2 gradlew clients, empty~~
   ~~results dir). Effort: S once the daemon queue is untangled.~~
2. ~~**R07 (sip:/tel: URI edges) — designed, not implemented.** Known: Android's~~ done — R07 shipped (URI params stripped; DeepLinkTargetTest)
   ~~`schemeSpecificPart` returns the DECODED form (proven by the existing~~
   ~~`tel:%20` → None test), so a literal `;`-param strip~~
   ~~(`substringBefore(';')`) uniformly covers `;transport=`, `;phone-context=`,~~
   ~~`;postd=`, `;isub=` (RFC 3261 §19.1 / RFC 3966). Effort: S. Not started~~
   ~~because editing files under a running gate risks half-built states.~~
3. **Cancel-leg of the detour on device**: my tap (estimated coordinates) **→ TODO_LIST (re-run the detour CANCEL leg with uiautomator coordinates — agent-executable)**
   landed on the IdP's "Alternative Sign In Methods" instead of the consent
   Cancel, so the IdP→origin return (detour CLEAR on device) is still only
   unit-pinned, not device-proven. The IdP-internal navigation (consent →
   authenticate page) IS device-proven. Effort: S with uiautomator dump
   (emulator currently shut down).
4. ~~**TODO_LIST / FEATURES / CHANGELOG flips for this session** — not yet~~ done — R11/N023 (TODO_LIST Ready note, CHANGELOG bullets, FEATURES rows landed)
   ~~written (planned as R11/N023-N024). The Ready rows R01-R04 should flip to~~
   ~~done-at. Effort: S.~~

## c) NOT STARTED

| Item                                                                                                                                                                                                                                                                                      | Why                                | Still wanted?                 |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- | ----------------------------- |
| R07 code+tests (N019-N020)                                                                                                                                                                                                                                                                | queued behind the gate (b2)        | yes — next code task          |
| R09 live-call tel: → `CallActivity` route (N021)                                                                                                                                                                                                                                          | not reached                        | yes                           |
| R10 ROADMAP additions (N022)                                                                                                                                                                                                                                                              | not reached                        | yes                           |
| R11 docs sweep + `done at` annotations (N023-N024)                                                                                                                                                                                                                                        | not reached                        | yes                           |
| R08 in-shell dialable short-circuit                                                                                                                                                                                                                                                       | operator question (g1 of the plan) | blocked on answer             |
| R12-R27 (owner/credential/BLK: SystemNix deploy, PAT re-scope, billing, CI watch, physical install, outbound + ring trains, live Pocket ID login, assetlinks serving, logged-in tail, live contracts, logout dereg, standdown, device batch, tag, repo meta, post-train docs, visibility) | owner-only actions                 | yes — unchanged from the plan |

## d) TOTALLY FUCKED UP

1. **The Gradle queue self-inflicted outage (severity: blocked the session's
   final gate; root cause: me).** I stacked multiple `nix develop -c
   ./gradlew …` invocations while the API 36 emulator devoured CPU, and a
   STALE wrapper client from an earlier command sat queued ahead of the live
   gate. Result: a "BUILD FAILED in 1h 42m 22s" that ran ZERO tests (empty
   results dir) — a pure resource-starvation failure masquerading as a test
   failure. Mitigation applied: killed the stale client (PID 658967), shut
   the emulator down cleanly (`adb emu kill`), re-ran clean — which was
   STILL queued at report time (2 clients alive, no results). Post-report
   action: `pkill -f gradlew`, then one clean gate.
2. **Session-1's core premise was WRONG and lived in AGENTS.md as doctrine
   for hours.** "HTTP redirects never consult shouldOverrideUrlLoading" was
   shipped as a gotcha in the previous session; R03 busted it on the first
   honest device test. Mitigation: corrected model + corrected gotcha +
   on-device proof. Lesson (now obvious in hindsight): device-train FIRST,
   doctrine SECOND — never write platform-behavior "facts" into AGENTS.md
   from documentation memory.
3. **assetlinks.sh v1 silently emitted the WRONG fingerprint.** It read
   `~/.android/debug.keystore` (the classic path) while the nix JDK's AGP
   actually signs with `~/.config/.android/debug.keystore` (XDG-resolved) —
   two keystores, different certs, both present on this machine. Serving v1's
   output would have BRICKED app-link verification at R18 with no error
   anywhere. Caught only because I cross-checked against the emulator's
   `pm get-app-links` signature. v2 discovers + refuses mismatches. Lesson:
   the ARTIFACT (apksigner on the built APK) is the truth; keystores are
   claims.
4. **Screenshot-scaled tap coordinates wasted ~5 commands and a false
   conclusion.** Taps estimated from downscaled screenshots missed their
   targets by >100 px (the resolver row, the consent Cancel) and produced a
   phantom mystery ("the dialpad opened itself") until the timeline was
   reconstructed from file mtimes + START records. uiautomator dump (exact
   bounds) fixed it instantly. Recorded in AGENTS.md.
5. **Minor: the first Chrome first-run screen swallowed a BACK keyevent**, so
   an "unchanged screen" observation was actually "Chrome on top, app
   working invisibly behind it" — I briefly tested through the wrong window.
   Fix habit: after any foreign app opens, `am force-stop` it / check
   `topResumedActivity` before interpreting screenshots.

## e) WHAT WE SHOULD IMPROVE

1. **One gate at a time, nothing else running.** Emulator and Gradle never
   overlap: emulator work → `adb emu kill` → gate. Impact: the 1h42m stall
   would have been ~40s.
2. **Kill stale background builds before starting new ones** (`pgrep -af
   gradlew` as a pre-gate reflex; the wrapper clients pile up silently).
3. **uiautomator dump FIRST for every tap** — screenshot estimates are
   structurally wrong on sheets/resolvers (safe-area + scale).
4. **Failed gates need full log capture** — my `grep -E "FAILED|BUILD"`
   filters hid the real error line of the 1h42m failure. Write full output
   to a file, filter for display.
5. **Verify claims against artifacts**: keystore fingerprints against
   apksigner output; "tests pass" against result-XML counts (done today —
   91 counted, not claimed); platform behavior against the device.
6. **Emulator-train scripts could ship with the ritual**: append
   `uiautomator dump` + `logcat -d -s WebSurface` to the smoke script's
   output so every train session produces provenance, not just pixels.
7. **Doctrine discipline**: any AGENTS.md "the platform does X" gotcha gets
   a `proven <date>` or `unverified` tag at write time — the stale-premise
   incident is the recurring cost of skipping this.
8. ~~**Consider a detour TTL** (design note, not urgent): a blessed IdP~~ done — recorded — ROADMAP Auth theme carries the detour-TTL raw idea (decide with the operator)
   ~~authority lives until the next origin commit; a time bound would shrink~~
   ~~the theoretical window. Single-authority + cookie scoping already bound~~
   ~~the risk; decide with the operator, don't unilaterally reshape.~~

## f) Next tasks (harvest ground — ranked, with impact/effort/category)

**Agent-executable NOW (this wave's tail):**

1. ~~Untangle the Gradle queue (`pkill -f gradlew`, one clean daemon), then run the full gate: 93 tests + zipalign + dprint on touched files. Impact: Critical, Effort: S, Quality.~~ done — clean gate closed GREEN (94/94, zipalign OK) post-report
2. ~~R07/N019-N020: strip `;`-params in `deepLinkTarget` (sip before/after `@`, tel/callto) + tests for `;transport=`, `;phone-context=`, `;postd=`, `;isub=`, params-only-blank → None. Impact: Medium, S, Feature.~~ done — R07 (sip/tel URI edges + tests)
3. ~~R09/N021: a Dial deep link while a call is LIVE routes to `CallActivity` (single-call policy one hop earlier) + test. Impact: Medium, S, Feature.~~ done — R09/N021 (live-call Dial link → CallActivity)
4. ~~R11/N023: FEATURES/CHANGELOG flips for R01-R06 (incl. the redirect-seam FIX — a behavioral change worth a changelog bullet). Impact: Medium, S, Documentation.~~ done — R11/N023 (FEATURES/CHANGELOG flips landed)
5. ~~R11/N024: annotate the 15-17 plan + 15-11 report with `done at` markers (docs-health ANNOTATE). Impact: Medium, S, Documentation.~~ done — THIS sweep — the 15-17 plan + 15-11 report are annotated row-by-row (2026-10-28)
6. ~~TODO_LIST harvest: flip R01-R06 Ready rows to done-at; re-parent R07/R09/R10/R11 remainders. Impact: Medium, S, Documentation.~~ done — TODO_LIST Ready note flipped R01-R06; this sweep re-harvests remainders
7. ~~R10/N022: ROADMAP raw ideas — "OAuth outside the WebView" (Custom Tabs + app-link callback), detour-TTL note, multi-host IdP allowance note. Impact: Low, S, Documentation.~~ done — R10/N022 (ROADMAP raw ideas: OAuth outside the WebView, detour TTL, multi-host IdP)
8. Re-run the detour CANCEL leg on the emulator with uiautomator coordinates: consent Cancel → IdP error redirect → origin commit → `auth detour cleared` in logcat. Impact: Medium, S, Quality. **→ TODO_LIST (detour CANCEL leg re-run with uiautomator coordinates)**
9. Investigate the DialpadActivity default "100" display (observed opening via FAB with no intent extra — seeded from where?) and decide blank-vs-seeded. Impact: Low, S, Bug/Quality. **→ TODO_LIST (investigate the dialpad default "100" display — blank-vs-seeded)**
10. ~~Push both remotes when the operator says so (last explicit instruction covered up to `2e06567`; this session is unpushed). Impact: Medium, S, Ops.~~ done — policy — NOTHING pushes automatically (AGENTS); the operator pushes by hand (confirmed 2026-10-27)

**Owner/BLK (unchanged from the plan, re-ranked):**

11. R12a: SystemNix deploy → phase-5 attaches the push mirror. Impact: Critical, S, Ops. **→ TODO_LIST (Owner: SystemNix deploy, R12/N025)**
12. R12b: regenerate sync PAT with `repo`+`workflow`, sops update, redeploy. Impact: Critical, S, Ops. **→ TODO_LIST (Owner: PAT repo+workflow, R12/N026)**
13. R12c: GitHub billing fix (payment/spending limit). Impact: Critical, S, Ops. **→ TODO_LIST (GitHub billing fix, R12/N027)**
14. Mirror-lag verify (`scripts/mirror-lag.sh`) after the deploy. Impact: High, S, Ops. **→ TODO_LIST (mirror-lag verify after the deploy, R12/N028)**
15. R13: watch the first green CI run; fix what it exposes. Impact: High, S, Quality. **→ TODO_LIST (first green CI run, R13/N029)**
16. R14: physical install + first-run walk (fresh APK, mic+notification grants, battery Unrestricted). Impact: Critical, S, Feature. **→ TODO_LIST (First physical install, R14/N030-N032)**
17. R15: outbound device train (session, REGISTER, INVITE ladder, audio, hangup) — needs Q1 test extension. Impact: Critical, L, Feature. **→ TODO_LIST (outbound device train, R15/N033-N038)**
18. R16: ring train (ring-while-closed, bootstrap race measurement, answer paths, ring_end). Impact: Critical, L, Feature. **→ TODO_LIST (ring train + race, R16/N039-N044)**
19. R17: live Pocket ID detour pass — login IN-SHELL, cookie mints in the WebView jar, registration follows. Impact: High, S, Feature. **→ TODO_LIST (live Pocket ID detour pass, R17/N045-N046)**
20. R18: serve `scripts/assetlinks.sh` output from webphone + `adb verify-app-links` green + implicit stack links without user-selection. Impact: High, S, Feature. **→ TODO_LIST (assetlinks served + verified, R18/N047-N048)**
21. R19: logged-in emulator tail (W104 composer attach, W105 external hand-off — NOTE: W105 now interacts with the redirect seam, re-verify, W106 back stack). Impact: Medium, S, Quality. **→ TODO_LIST (logged-in emulator tail, R19/N049-N051)**
22. R20: live contract checks folded into the trains (session fields, payload shapes, CSRF). Impact: Medium, M, Quality. **→ TODO_LIST (Live contract checks, R20/N052-N053)**
23. R21: logout deregistration (KEEP-vs-PURGE answer → JS bridge or cookie-poll). Impact: Medium, M, Feature. **→ TODO_LIST (Logout deregistration, R21/N054-N056)**
24. R22: island-standdown seam (native-shell-present signal, owner nod first). Impact: Medium, L, Feature. **→ TODO_LIST (island-standdown seam, R22/N057-N058)**
25. R23: device-verify batch (CallStyle post-time, TalkBack, battery allow/deny). Impact: Medium, M, Quality. **→ TODO_LIST (device-verify batch, R23/N059-N061)**
26. R24: `v0.1.0` annotated tag + CHANGELOG cut (operator timing). Impact: Medium, S, Release. **→ TODO_LIST (v0.1.0 tag + CHANGELOG cut, R24/N062-N063)**
27. R25: Forgejo description (token) + icon parity both-or-neither. Impact: Low, S, Cleanup. **→ TODO_LIST (Forgejo description + icon parity, R25/N064-N065)**
28. R26: post-train docs sweep — honesty-marker retirement. Impact: Medium, M, Documentation. **→ TODO_LIST (post-train docs sweep, R26/N066-N068)**
29. R27: visibility end-state decision (private vs public flip). Impact: Low, S, Decision. **→ TODO_LIST / ROADMAP Open questions (visibility end-state, R27/N069)**

**New items discovered this session:**

30. Cross-repo note to webphone: the OIDC begin→IdP hop MUST stay a server 302 (never a client-side JS redirect "optimization") or the Android detour seam breaks. Impact: Medium, S, Documentation. **→ TODO_LIST (Cross-repo: the begin→IdP hop must stay a server 302 — note for webphone)**
31. ~~Cross-repo observation: the island's unauthenticated probe appears to have redirected the backgrounded WebView to /login while the dialpad covered it (18:12 observation) — confirm intended server behavior for hidden shells. Impact: Low, S, Investigation.~~ **NOT-DO — cosmetic observation; no user impact demonstrated — revisit only if a train surfaces it.**
32. Emit a release-keystore assetlinks variant once a signing config exists (the script already accepts an explicit keystore argument). Impact: Low, S, Feature. **→ TODO_LIST (release-keystore assetlinks variant — rides the release-signing decision)**
33. tel: resolver UX note for end users (pick WebPhone → Always) in the eventual user docs. Impact: Low, S, Documentation. **→ TODO_LIST (post-train docs sweep — tel: resolver UX note goes into the eventual user docs, R26)**
34. ~~Consider `shouldInterceptRequest` debug logging for nav diagnosability (optional, only if trains need it). Impact: Low, S, Quality.~~ **NOT-DO — only if trains need it — logcat + the detour-transition log cover diagnosis today.**
35. After R17's live login: re-verify `retry()`-clears and the belt against a REAL logged-in flow (not just unit pins). Impact: Medium, S, Quality. **→ TODO_LIST (re-verify retry-clear + belt against the REAL logged-in flow, R17/N045-N046)**

## g) Top questions (up to 3, unanswerable from the repo)

1. **Pocket ID test credentials for R17**: can you mint a test identity (or **→ TODO_LIST (live Pocket ID pass, R17/N045-N046 — operator credential)**
   an extension + login method) on `auth.larsartmann.cloud` the emulator can
   complete a login with? Without it, the detour's RETURN leg (IdP → origin
   callback → session cookie mints in the WebView jar → registration
   follows) stays unit-pinned but never device-proven — and that is the
   last unproven half of the OAuth story.
2. **IdP cancel/error redirect target**: when the user taps Cancel on the **→ ROADMAP Open questions (IdP cancel/error redirect target — webphone-server question)**
   Pocket ID consent (or the IdP errors), where does the server land the
   callback — island root `/`, a styled origin error page, or the IdP? The
   Android detour clears on ANY origin commit, so all three work, but the
   on-device assertion (and any future UX polish) depends on knowing the
   intended target. I tried: `internal/sso/sso.go` handles the success
   callback; I did not find an explicit error-callback handler.
3. **Dialpad default "100"**: opening the dialer via the shell FAB (no **→ TODO_LIST (investigate the dialpad default "100" display — blank-vs-seeded)**
   intent extra) showed `100` in the number display on the emulator. Is
   that your extension number seeded from somewhere I haven't found
   (prefs? a default?), and should a plain FAB tap open the dialer BLANK
   instead?

---

_Next session entry point: (f)1 → (f)2 → … then the BLK wall. The plan's
IDs (R07/R09/R10/R11) map 1:1 onto items 2/3/7/4-5._
