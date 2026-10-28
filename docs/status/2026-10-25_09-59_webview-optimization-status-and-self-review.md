<!-- Status report: point-in-time snapshot. Claims verified as of 2026-10-25 09:59 CET.
     Session scope: the WebView-optimization train only (what this session did and noticed).
     Later sessions: annotate, never rewrite. -->

# WebView optimization — status & self-review

Session: 2026-10-25, ~09:30–10:00 CET. Task: "optimize the web view" for the
webphone-android shell. Everything below is about THIS session's run and what
it directly surfaced. Repo state at write time: HEAD `fee923f`, working tree
carries 2 uncommitted files (AGENTS.md dprint reflow + WebSurfaceTest.kt fix)
awaiting the daemon sweep; both remotes unpushed (hand-push ritual).

## a) FULLY DONE

All items below are committed (daemon commits `0103bc2`…`fee923f`) and verified
by the repo's full local gate: `assembleDebug` green, `testDebugUnitTest`
55/55 (was 46; +9 new), `zipalign -c -P 16 4` OK on the debug APK, `dprint
check` clean.

| # | Item                                                                                                                                                                                                                                                                                                                                                                                  | Evidence                                                                        |
| - | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| 1 | `shell/WebSurface.kt` — the hardened WebView host: same-origin navigation scope (pure `navigationVerdict`), native offline overlay + Retry, renderer-crash rebuild (`onRenderProcessGone` consumed), file chooser (`onShowFileChooser`), explicit `getUserMedia` deny, console→logcat bridge, cookie flush (page-finish + pause), pause/resume pairing, API 33+ algorithmic darkening | New file; compiles; seams tested                                                |
| 2 | MainActivity slimmed to chrome-only; delegates WebView lifecycle (pause/resume/back/origin/destroy) to WebSurface                                                                                                                                                                                                                                                                     | `MainActivity.kt` diff; launch test green                                       |
| 3 | Debug-only `chrome://inspect` (`WebView.setWebContentsDebuggingEnabled` gated on `FLAG_DEBUGGABLE`, not BuildConfig — this build generates none)                                                                                                                                                                                                                                      | `App.kt`                                                                        |
| 4 | `android:isLightTheme` pinned (`true` day / `false` night) so WebView darkening can trust the theme                                                                                                                                                                                                                                                                                   | `values/themes.xml`, `values-night/themes.xml` (explicit parent added — see d3) |
| 5 | Offline overlay strings (title / body with origin arg / retry)                                                                                                                                                                                                                                                                                                                        | `values/strings.xml`                                                            |
| 6 | Tests: `NavigationVerdictTest` (7 cases: same-origin, explicit default port, host case, subdomain-foreign, port-foreign, internal schemes, system schemes, null) + `WebSurfaceTest` (overlay show/retry cycle reloads origin; renderer rebuild swaps instance + reloads; container invariants)                                                                                        | 9/9 green in `testDebugUnitTest`                                                |
| 7 | Docs: CHANGELOG `[Unreleased]` entries (Added + Fixed); ROADMAP "WebView dark-mode" open question RESOLVED and removed (its premise was stale — see e2); AGENTS.md shell-table row + a "WebView hardening home" gotcha                                                                                                                                                                | Files updated, dprint-clean                                                     |
| 8 | Evidence found in the webphone repo: served pages ARE theme-aware (`color-scheme: dark light` at `app.css :root` + island style, `prefers-color-scheme` blocks, `theme-preload.js`, manual `wp-theme` override) — the algorithmic-darkening approach is page-native dark, not recoloring                                                                                              | grep of `internal/web/assets/*`                                                 |

## b) PARTIALLY DONE

| # | What works                                          | What remains open                                                                                                                                                                                                                                 | Effort            |
| - | --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------- |
| ~~1~~ | ~~Dark mode: implemented + reasoned (API 33+)~~ done — docs/status/2026-10-25_13-43 §a4 W101/W102 (island dark 243→146, light 243 — visual proof) | ~~ZERO visual verification — the emulator train was never run this session; behavior is from platform docs + CSS evidence, not eyes. API 31–32 stays light with the web app's own toggle as the documented fallback~~ | ~~M (emulator pass)~~ |
| ~~2~~ | ~~Renderer-crash rebuild: logic + test seam~~ done — docs/status/2026-10-25_13-43 §a4 W107 (real renderer kill; app pid survived) | ~~The real `onRenderProcessGone` callback was never exercised (no on-device kill test); `rebuildAfterRendererDeath()` is only proven via its test seam~~ | ~~S–M~~ |
| 3 | File chooser: implemented, multi-select passthrough **→ TODO_LIST (logged-in emulator pass, R19/N049 — W104 attach click-through)** | Never clicked through on an emulator/device (Robolectric can't drive the picker); accept-type filtering relies on `createIntent()` — `EXTRA_MIME_TYPES` tightening not added                                                                      | S–M               |
| ~~4~~ | ~~Commit/verify state~~ done — superseded — both remotes pushed (14:33 tips equal f585c6d); push trigger live; first green run → TODO R13 | ~~2 files still uncommitted (daemon will sweep); remotes NOT pushed (`git push origin main && git push github main` is the hand ritual); CI has NEVER run (push-trigger decision blocked on PAT workflow scope) — the local gate is the only signal~~ | ~~S~~ |
| ~~5~~ | ~~Client wiring (scope guard, error→overlay)~~ done — docs/status/2026-10-25_13-43 §a1 P09 (5 delegation tests in WebSurfacePolicyTest) | ~~Only the pure verdict + surface seams are tested; the `WebViewClient` delegation itself (shouldOverrideUrlLoading / onReceivedError → overlay) has no test~~ | ~~S~~ |

## c) NOT STARTED

Planned/considered this session, deliberately deferred (no code written):

1. ~~Emulator train (`scripts/emulator-train.sh`) — the repo has the self-serve~~ done — docs/status/2026-10-25_13-43 §a4 + 13-56 §a (emulator pass: W101-W107, F024, F038, F058)
   ~~kit; I chose JVM gates only. Priority: HIGH (it validates half of section a).~~
2. ~~`DownloadListener` — never even considered during design; long-press/save of~~ done — docs/status/2026-10-25_13-43 §a1 W110 (DownloadListener shipped)
   ~~inbound MMS attachments in the WebView will silently no-op. Priority: MEDIUM.~~
3. ~~Predictive back (`OnBackInvokedCallback`) for WebView history — the~~ done — docs/status/2026-10-25_13-56 §a W131 (predictive-back bug found + fixed)
   ~~deprecated `onBackPressed` still works via fallback, manifest already opts~~
   ~~in (`enableOnBackInvokedCallback`). Priority: LOW–MEDIUM.~~
4. ~~`onReceivedHttpError` (main frame) — a reverse-proxy 502 currently renders~~ done — docs/status/2026-10-25_13-43 §a1 W111 (policy DECIDED: HTTP errors render, overlay transport-only)
   ~~the proxy's page under the (invisible) overlay-free surface. Deliberate~~
   ~~scope cut; undecided whether the overlay should cover it. Priority: MEDIUM.~~
5. ~~Photo-picker (`ACTION_PICK_IMAGES`, API 33+) instead of the generic GET_CONTENT~~ done — docs/status/2026-10-25_13-56 §a W130 (photo picker on API 33+)
   ~~for the attach flow. Priority: LOW.~~
6. Startup/baseline-profile work (cold start re-fetches HTML; webphone serves **→ ROADMAP theme 1 (baseline profiles — measure a real device first)**
   `no-store` so caching can't help). Priority: LOW.
7. ~~values-de for native strings (overlay included) — pre-existing app-wide~~ done — ROADMAP Decisions (values-de not now; strings extractor-safe)
   ~~English-only pattern; not my call to change unilaterally. Priority: LOW.~~

## d) TOTALLY FUCKED UP

Nothing broken SHIPPED — the tip is green — but the session had four real
screw-ups, all process failures, all caught by gates or re-viewing:

1. **CHANGELOG edit mangled the file.** A replace anchored on the "Design
   system" bullet head spliced my sections in the wrong place, ate that
   bullet's first line, and duplicated `### Fixed`/`### Changed` headers.
   Caught by immediate re-view; repaired before the daemon swept. Root cause:
   anchored an edit at a section BOUNDARY without re-reading enough following
   context. Classic exact-match discipline lapse.
2. **Guessed Android APIs instead of verifying first — 2 failed build rounds.**
   `ConsoleMessage.sourceLine()` does not exist (it's `sourceId()`), and
   `level()` is actually `messageLevel()` — verified via `javap` on the
   platform-36 android.jar only AFTER two red builds. The API I "remembered"
   was pre-rename documentation.
3. **values-night style without an explicit parent** — dot-name implicit
   inheritance resolved to a nonexistent `Theme` style → AAPT link failure.
   A known Android footgun I should have written correctly the first time.
4. **Wrote tests against an imagined Robolectric API** (`ShadowWebView.loadedUrls`)
   instead of `javap`-ing the shadow jar first — another red round trip. Total:
   4 failed gate runs before green, every one avoidable by verify-before-write.
5. (Daemon-caused, named for honesty: intermediate daemon commits `c259e58`/
   `a3285c3` contain the BROKEN intermediate Kotlin — history has red
   intermediates even though the tip is green. Known daemon nature; nothing to
   fix, but bisects will hit them.)

## e) WHAT WE SHOULD IMPROVE

1. ~~**Verify third-party API surfaces before writing code** — `javap` on~~ done — AGENTS.md gotcha landed (13-56 W192: javap-first for platform/shadow APIs)
   ~~android.jar / the Robolectric shadow jar costs seconds; this session paid 3~~
   ~~red builds for skipping it. Candidate AGENTS.md gotcha line.~~
2. ~~**The ROADMAP's dark-mode premise was stale and nobody noticed for days**~~ done — fixed this session (§a8: served pages ARE theme-aware); ROADMAP premise removed
   ~~("the island's own pages do not dark-mode" — they have for a while).~~
   ~~Lesson: cross-repo claims about the webphone side should cite what was~~
   ~~grepped, and docs-health VERIFY should spot-check claims whose subject repo~~
   ~~moved recently. (Fixed this session; the pattern remains.)~~
3. ~~**TODO_LIST drift observed, not fixed** (scope discipline): "Battery-~~ done — docs/status/2026-10-25_10-00 §a T16 (battery ask shipped — the row drift resolved itself)
   ~~optimization exemption prompt for the ntfy lane" is listed TODO but~~
   ~~`maybeRequestBatteryExemption()` EXISTS in MainActivity (asked post-~~
   ~~registration, once per install). Row needs removal or correction.~~
4. ~~**The uiMode/configChanges staleness TODO (f33) got a bigger blast radius**:~~ done — docs/status/2026-10-25_10-00 §a T06 (uiMode root fix — bigger blast radius mooted)
   ~~day/night switch mid-session now also leaves the WebView on the old scheme~~
   ~~(isLightTheme resolves at activity creation). Its TODO row should gain this~~
   ~~evidence line so the next session sizes it correctly.~~
5. ~~**Client-delegation code is untested by design of this session** — pure~~ done — AGENTS.md "WebView hardening home" gotcha states the client-delegation policy
   ~~policy extracted and tested, thin Android-glue delegation trusted. That's a~~
   ~~defensible line, but it should be a STATED convention, not an accident.~~
6. ~~**No on-device verification loop in this session at all** — the repo owns an~~ done — scripts/emulator-verify.sh (13-43 §a4) owns the emulator start-to-finish
   ~~emulator kit; "optimize the WebView" without looking at the WebView once is~~
   ~~the biggest process gap of the run.~~

## f) Next tasks (ranked; harvested from THIS session only)

Impact: C=critical, H=high, M=medium, L=low. Effort: S <30min, M 30min–2h, L >2h.
Category: B=bug, F=feature, Q=quality, C=cleanup, D=documentation.

| #  | Task                                                                                                                                                                          | Imp | Eff | Cat |
| -- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --- | --- | --- |
| ~~1~~  | ~~Run the emulator train: visually verify dark mode, offline overlay, attach flow, external-link hand-off~~ done — docs/status/2026-10-25_13-43 §a4 + 13-56 §a (emulator pass: W101-W107, F024, F038, F058) | ~~H~~ | ~~M~~ | ~~Q~~ |
| ~~2~~  | ~~On-emulator renderer kill (`adb shell am crash` / kill chromium pid) to exercise the real `onRenderProcessGone`~~ done — docs/status/2026-10-25_13-43 §a4 W107 (app survived, WebView rebuilt) | ~~H~~ | ~~S~~ | ~~Q~~ |
| ~~3~~  | ~~Add `DownloadListener` for inbound MMS attachment saves, or document the no-op decision~~ done — docs/status/2026-10-25_13-43 §a1 W110 | ~~M~~ | ~~S~~ | ~~F~~ |
| ~~4~~  | ~~Decide + implement main-frame HTTP-error policy (proxy 502: overlay vs proxy page)~~ done — docs/status/2026-10-25_13-43 §a1 W111 (policy decided + documented) | ~~M~~ | ~~S~~ | ~~F~~ |
| ~~5~~  | ~~Test the WebViewClient delegation: fake `WebResourceRequest` → verdict path; error callback → overlay~~ done — docs/status/2026-10-25_13-43 §a1 P09 (5 delegation tests) | ~~M~~ | ~~S~~ | ~~Q~~ |
| ~~6~~  | ~~Fix stale TODO_LIST battery-exemption row (code exists — cite MainActivity)~~ done — docs/status/2026-10-25_10-00 §a T16 (ask shipped; row drift closed) | ~~M~~ | ~~S~~ | ~~C~~ |
| ~~7~~  | ~~Update TODO_LIST f33 (uiMode staleness) with the new WebView-darkening blast radius~~ done — docs/status/2026-10-25_10-00 §a T06 (uiMode root fix) | ~~M~~ | ~~S~~ | ~~C~~ |
| ~~8~~  | ~~Add the javap-first gotcha (verify android.jar/shadow APIs before writing) to AGENTS.md~~ done — docs/status/2026-10-25_13-56 §a W192 (AGENTS javap-first gotcha) | ~~M~~ | ~~S~~ | ~~D~~ |
| ~~9~~  | ~~HARVEST this report's section f into TODO_LIST/ROADMAP (docs-health)~~ done — harvested via the 10-17 + 15-17 plans; this sweep completes it | ~~H~~ | ~~S~~ | ~~C~~ |
| ~~10~~ | ~~Owner: push both remotes by hand after the daemon sweep (origin + github)~~ done — pushed repeatedly (14:33 tips equal); owner-push policy documented in AGENTS | ~~H~~ | ~~S~~ | ~~C~~ |
| ~~11~~ | ~~Photo-picker (ACTION_PICK_IMAGES, API 33+) for the attach flow~~ done — docs/status/2026-10-25_13-56 §a W130 | ~~L~~ | ~~S~~ | ~~F~~ |
| ~~12~~ | ~~`EXTRA_MIME_TYPES` tightening on the file chooser (accept-attr passthrough verified only implicitly)~~ **NOT-DO — image-only inputs route to the photo picker (W130) — deeper MIME tightening has no demand.** | ~~L~~ | ~~S~~ | ~~Q~~ |
| ~~13~~ | ~~Predictive-back `OnBackInvokedCallback` for WebView history~~ done — docs/status/2026-10-25_13-56 §a W131 (predictive-back fix) | ~~L~~ | ~~M~~ | ~~F~~ |
| 14 | Overlay a11y pass: announce on show, focus order vs chrome, TalkBack read-through **→ TODO_LIST (TalkBack walkthrough, R23/N060 — overlay included)** | M   | S   | Q   |
| ~~15~~ | ~~Console bridge: gate verbose logs behind debuggable (currently logs everything always)~~ done — docs/status/2026-10-25_13-56 §a W132 | ~~L~~ | ~~S~~ | ~~Q~~ |
| ~~16~~ | ~~Overlay: include failing URL + error code on debuggable builds for field diagnosis~~ **NOT-DO — console→logcat bridging already gives field diagnosis; no demand.** | ~~L~~ | ~~S~~ | ~~F~~ |
| ~~17~~ | ~~Decide + document Safe Browsing stance (default on; operator TLS origin)~~ done — ROADMAP Decisions (Safe Browsing default-on) | ~~L~~ | ~~S~~ | ~~D~~ |
| ~~18~~ | ~~Decide + document zoom/pinch stance (page allows pinch; kept for a11y)~~ done — ROADMAP Decisions (pinch-zoom kept — accessibility) | ~~L~~ | ~~S~~ | ~~D~~ |
| ~~19~~ | ~~Decide + document UA-string stance (default; responsive layout)~~ done — ROADMAP Decisions (UA stays WebView-default) | ~~L~~ | ~~S~~ | ~~D~~ |
| ~~20~~ | ~~Consider offline HTML snapshot policy (LOAD_CACHE_ELSE_NETWORK) — probably moot under no-store, document why~~ **NOT-DO — moot under no-store; the caching reality is documented in ROADMAP.** | ~~L~~ | ~~S~~ | ~~D~~ |
| ~~21~~ | ~~values-de for native strings incl. overlay — or pin English-only by policy~~ done — ROADMAP Decisions (values-de not now) | ~~L~~ | ~~M~~ | ~~F~~ |
| ~~22~~ | ~~CI push-trigger decision (BLOCKED: PAT workflow scope — owner)~~ done — trigger added (10-00 §a F040; runs fire) — first green run → TODO R13 (billing) | ~~H~~ | ~~S~~ | ~~C~~ |
| 23 | First device train vs live stack (BLOCKED: test extension — owner) **→ TODO_LIST (first device train, R15/R16 — Q1)** | H   | L   | Q   |
| ~~24~~ | ~~Thread-confinement audit of CallEngine (pre-existing High TODO)~~ done — docs/status/2026-10-25_10-00 §a T07 | ~~H~~ | ~~M~~ | ~~Q~~ |
| ~~25~~ | ~~Stale theme colors after day/night switch (pre-existing; now see #7)~~ done — docs/status/2026-10-25_10-00 §a T06 | ~~H~~ | ~~S~~ | ~~B~~ |
| 26 | Answer-path race verification (BLOCKED: live stack) **→ TODO_LIST (Verify the answer-path race, R16/N041)** | H   | M   | Q   |
| 27 | Logout deregistration JS bridge (BLOCKED: operator pick keep-vs-purge rows) **→ TODO_LIST (Logout deregistration, R21/N054-N056)** | M   | M   | F   |
| ~~28~~ | ~~F055: ntfy sticky-restart kill-cycle on the emulator~~ done — docs/status/2026-10-25_13-56 §a F035 | ~~M~~ | ~~S~~ | ~~Q~~ |
| ~~29~~ | ~~emulator-smoke.sh adb robustness (pin `-s`, fail-fast)~~ done — docs/status/2026-10-25_10-00 §a T11 | ~~M~~ | ~~S~~ | ~~Q~~ |
| ~~30~~ | ~~TalkBack + contrast audit over every programmatic surface (incl. new overlay)~~ done — contrast gated (13-43 P10, CI) — TalkBack → TODO R23 | ~~M~~ | ~~M~~ | ~~Q~~ |
| ~~31~~ | ~~CallStyle-composed in-call notifications (plan T21)~~ done — docs/status/2026-10-25_10-00 §a T21 | ~~M~~ | ~~M~~ | ~~F~~ |
| ~~32~~ | ~~INVITE 401 re-auth ladder in SipEngine~~ done — docs/status/2026-10-25_10-00 §a T09 | ~~M~~ | ~~M~~ | ~~B~~ |
| ~~33~~ | ~~Second-incoming-call UX: busy heads-up instead of silent 486~~ done — docs/status/2026-10-25_10-00 §a T20/T22 (missed-call policy) | ~~M~~ | ~~S~~ | ~~F~~ |
| ~~34~~ | ~~CallLayer shutdown vs in-flight ensureWired race decision~~ done — docs/status/2026-10-25_10-00 §a T08 (AttachGuard) | ~~M~~ | ~~S~~ | ~~Q~~ |
| ~~35~~ | ~~Recents chips stale after a call~~ done — docs/status/2026-10-25_10-00 §a T23 | ~~L~~ | ~~S~~ | ~~B~~ |
| ~~36~~ | ~~Port scripts/mirror-lag.sh~~ done — docs/status/2026-10-25_10-00 §a F078 | ~~L~~ | ~~S~~ | ~~C~~ |
| 37 | Set the Forgejo repo description **→ TODO_LIST (Forgejo repo description, R25/N064)** | L   | S   | C   |
| ~~38~~ | ~~LICENSE decision + file~~ done — docs/status/2026-10-25_10-00 §a F075 (MIT LICENSE) | ~~L~~ | ~~S~~ | ~~C~~ |
| ~~39~~ | ~~TLS-pinning consideration memo~~ done — ROADMAP Decisions (TLS NOT pinned) | ~~L~~ | ~~S~~ | ~~D~~ |
| ~~40~~ | ~~Missed-call notification on `ring_end` unanswered (ROADMAP)~~ done — docs/status/2026-10-25_10-00 §a T20/T22 | ~~M~~ | ~~S~~ | ~~F~~ |
| 41 | Baseline profiles for startup + call path (ROADMAP) **→ ROADMAP theme 1 (baseline profiles)** | L   | L   | Q   |
| 42 | Cross-repo: a server-driven "native shell present" signal so the island can stand down its own SIP registration inside the shell (duplicate registration noticed; owner call) **→ TODO_LIST (island-standdown seam, R22/N057-N058)** | M   | L   | F   |
| ~~43~~ | ~~Kotlin formatter/linter decision (style is review-enforced today; ktlint/detekt via dprint plugin — propose, owner decides)~~ done — ktlint adopted (2026-10-28 toolchain jump); detekt REJECTED (ROADMAP Decisions) | ~~L~~ | ~~S~~ | ~~Q~~ |
| ~~44~~ | ~~A11y: `web_offline_body` string hardcodes the origin at build time of the overlay — re-resolve on show if origin changed mid-session~~ done — docs/status/2026-10-25_13-56 §a W133 (origin re-resolved at show time) | ~~L~~ | ~~S~~ | ~~B~~ |
| ~~45~~ | ~~WebSurface: re-render overlay body text in `showOffline()` (today it is baked once in init — stale after a Settings origin change until recreation)~~ **NOT-DO — DUPLICATE — same fix as row 44, shipped once (W133).** | ~~L~~ | ~~S~~ | ~~B~~ |

(45 rows — the guide's warning applies: rows 20+ are ROADMAP fuel, not
commitments. #44/#45 are the same fix seen twice; harvest as one.)

## g) Questions I can NOT answer myself

1. ~~**WebView mic policy:** I hard-denied `getUserMedia` in the WebView (call~~ done — policy DECIDED — the deny stands, documented in AGENTS.md ("getUserMedia asks are DENIED by design; call audio is native")
   ~~audio is native, per the platform decision). Side effect: island-placed~~
   ~~calls AND island live-transcription are effectively dead in the shell. Keep~~
   ~~the hard deny, or grant mic when the app holds RECORD_AUDIO so the island~~
   ~~fallback works? I cannot decide the product stance from the repo (the~~
   ~~non-goal only forbids MOVING the call contract, not running the island~~
   ~~beside it).~~
2. ~~**API 31–32 theming:** below API 33 the WebView stays light; the web app's~~ done — DECIDED — ROADMAP Decisions (API 31-32 keeps the web app toggle as the documented fallback)
   ~~own theme toggle is my documented fallback. Is that acceptable, or do you~~
   ~~want the legacy force-dark shim for 31/32 (recolor-based, uglier, and it~~
   ~~fights the page's own themes)? Depends on which Android versions you~~
   ~~actually run — not discoverable from the repo.~~
3. ~~**Ritual split:** after the daemon sweeps the last 2 files — do YOU run the~~ done — the harvest executed via the 10-17 + 15-17 plans; pushes handled (14:33 tips equal, later pushes by owner policy)
   ~~end-of-session `git push origin main && git push github main`, or should I~~
   ~~(and should I also run the docs-health HARVEST of section f now, rather~~
   ~~than leaving it to the next session)?~~

— Session self-grade: the CODE outcome is solid (9 hardening features, 9
tests, all gates green, honest docs); the PROCESS was sloppy (4 avoidable red
builds, 1 mangled file, zero visual verification). The delta between the two
is exactly section e.
