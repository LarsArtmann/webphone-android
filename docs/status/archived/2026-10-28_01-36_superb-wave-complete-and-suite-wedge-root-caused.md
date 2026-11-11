# Status — SUPERB Wave Complete, Suite-Wedge Root-Caused, Toolchain Jumped Mid-Session (2026-10-28 01:36)

> **Live remainder:** open §f items live in TODO_LIST.md (live-stack/device-train/CI rows, R13-R16) and ROADMAP.md; the 2026-10-28 22-31 + 23-30 harvests dispositioned the list (archived 2026-11-11).

> **Session scope:** continuation of the 4th-wave session (R03→R11 per
> `docs/planning/2026-10-27_15-17_SUPERB-detour-hardening-and-train-unblocks.md`).
> This slice: the blocked final gate → root-causing the hours-long suite
> wedge → R07/R09 code+tests → R10/R11 docs sweep → annotations. Report
> written 2026-10-28 01:36 CET.
>
> **Headline caveat:** a SCHEDULED `crush-daily` session worked in this repo
> in PARALLEL all night. It independently fixed the same wedge (different
> half), added the ktlint gate + `.editorconfig`, reformatted every Kotlin
> file, renamed `DeepLink.kt` → `DeepLinkTarget.kt`, and at 01:36:26
> committed `af0084f` — a full toolchain jump (Gradle 9.8.2, AGP 9.4.1,
> Kotlin 2.4.21, compileSdk 37, build-tools 36.0.0) claiming its own
> 94-test gate. **My verification evidence is on the PRE-jump toolchain**;
> the current tree is their claim plus my green pre-jump gate. One cheap
> detached gate closes that gap (§b1, §f-1).

## TL;DR

- The agent wave is COMPLETE: R01-R07, R09, R10/R11 all shipped; R03's
  premise-bust fix stands on-device. Owner/credential rows untouched by
  design.
- The suite wedge (silent hours-long hangs, the thing that produced
  "BUILD FAILED in 1h42m with zero tests") is ROOT-CAUSED and dead: two
  independent defects, both fixed, union gated 94/0.
- The cost: two of my gate runs were SIGKILLed by harness session-scope
  teardown, and I gate-raced a concurrent agent session — no corruption,
  but the process lessons in §e are not optional anymore.

---

## a) FULLY DONE

1. **Suite-wedge root cause #1 — leaked ntfy service controllers.**
   `NtfySubscriptionServiceTest` built two REAL services and never
   destroyed them; each failed websocket armed `scheduleReconnect`'s raw
   `Thread{ sleep(backoff) }`, which resubscribed on a doubling backoff
   (1s→60s) for the LIFE of the test JVM — later classes' Robolectric
   teardowns ran under live reconnect churn (jstack-proven: pending
   `scheduleReconnect$lambda$4` threads + fresh OkHttp dispatchers inside
   a wedged worker). Fix: destroy-in-finally for both controllers, KDoc
   recording the why; same hygiene applied to `CallServiceTest`'s three
   controllers. Evidence: `/tmp/gate-v3.log` (92/0, EXIT=0, no stall at
   the previously-deterministic 27-test wedge point).
2. **Suite-wedge root cause #2 — `CallLayer.wiringEnabled` seam
   completed.** The interrupted session's seam (unit tests must never
   phone the live stack) existed but only `DeepLinkIntakeTest` flipped
   it. Extended to `WebSurfaceTest`, `WebSurfacePolicyTest`,
   `MainActivityLaunchTest` (all four shell-launching Robolectric
   classes). Evidence: the four files carry the `@Before`; committed in
   `d98eca8`.
3. **The interrupted session's unswept fixes ratified.** The three
   modified files at session start (`app/build.gradle.kts` per-test
   testLogging, `CallLayer.kt` seam, `DeepLinkIntakeTest` `@Before`)
   were read, judged mine-by-lineage, kept, and gated green.
4. **R07 — URI params stripped from dialables.**
   `tel:`/`callto:` split on the first `;` before the blank guard;
   `sip:` strips user params before `@`. `;transport=`, `;phone-context=`,
   `;postd=`, `;isub=` can never again reach the dialer as digits.
   Evidence: `DeepLinkTarget.kt` (renamed by the parallel session, content
   verified at lines 51/60) + new test `uri params are stripped from the
   dialable` (5 assertions incl. the blank guard seeing the STRIPPED
   form); green in gate v4.
5. **R09 — deep link while a call is live routes to the call screen.**
   `MainActivity.routeDeepLink` mirrors `DialpadActivity`'s exact
   live-phase guard (`!= IDLE && != ENDED`) one hop earlier: a `tel:`
   link mid-call opens `CallActivity` with NO number extra — the
   single-call policy holds on every entry path. Evidence:
   `MainActivity.kt` live-call branch + new test `a tel link while a
   call is live opens the call screen not the dialer` (7 tests in the
   intake class); green in gate v4.
6. **R10 — ROADMAP §4 "Auth surface".** Raw ideas only, per the file's
   register: OAuth outside the WebView (Custom Tabs + App-Link
   callback), a detour TTL, the multi-host IdP allowance question.
7. **R11 — docs sweep.** FEATURES: the auth-detour row rewritten to the
   corrected two-seam model (redirect seam blesses, clicks never do,
   belt for POSTs, on-device proof), the deep-link row rewritten
   (param strip, warm `setIntent` pin, live-call routing, emulator-proven
   train), the test-suite row 72→94 with the no-live-stack seam noted.
   CHANGELOG: the now-factually-wrong OIDC bullet corrected, a Fixed
   entry for the detour-never-fires defect, three Added entries
   (intake hardening, assetlinks.sh, 94 tests incl. the controller
   destroy discipline). TODO_LIST: the ENTIRE Ready wave struck per the
   file's done-items-are-removed discipline. Annotations: plan 15-17,
   report 15-11, report 00-20 all carry done-at headers. Evidence:
   `dprint check` green on all 8 touched files; daemon committed.
8. **Count reconciliation.** The handoff's "93 expected" was wrong:
   91 + 1 genuinely-new R06 test (the warm-tel one; the other was an
   upgrade) = 92 at gate v3, then +R07 +R09 = 94 at gate v4 — XML-counted
   both times, never prose-carried.

## b) PARTIALLY DONE

1. **Final gate on the CURRENT (post-jump) tree.** My 94/0 + zipalign +
   ktlint evidence is pre-`af0084f` (Gradle 8.14.4/AGP 8.7.3/compileSdk
   36). The parallel session's commit message claims "all verified at
   the 94-test gate" on the new toolchain, but I have NOT independently
   re-gated post-jump. Remaining: ONE detached
   `testDebugUnitTest assembleDebug ktlintCheck` + the build-tools 36
   zipalign path (the old `$ANDROID_HOME/build-tools/35.0.0` invocation
   is dead — it already 127'd this session). Effort: S (~5 min once the
   tree stops moving).
2. **The ktlint gate is local-only.** Green here; CI has never executed
   a single run (GitHub Actions billing blocker, R13/F041). Until billing
   is fixed, the gate exists only on machines that run it.
3. **Detour CANCEL leg — unit-pinned, never device-proven.** The
   consent-Cancel → origin-return → `auth detour cleared` flow is pinned
   in `WebSurfacePolicyTest` but the emulator attempt died on estimated
   tap coordinates; the uiautomator-dump redo (f8 of the 00-20 report)
   was not picked up this session. Effort: S-M.
4. **The auth-detour blessing model is minimal-by-design:** single
   authority, no TTL. Correct for the observed Pocket ID topology;
   multi-host IdPs and a time cap live in ROADMAP §4 as raw ideas, not
   code.

## c) NOT STARTED

- **f9 — the dialpad's default "100" display** (opened via FAB with no
  extra): investigation not started; operator question stands (§g3).
- **R12-R27 — all owner/credential-blocked rows** (deliberately out of
  agent scope): SystemNix deploy (push mirror), PAT `workflow` scope,
  GitHub Actions billing, first device train + physical install, R17
  live Pocket ID login, R18 server-side `assetlinks.json` (generator
  IS shipped: `scripts/assetlinks.sh`), R19 logged-in island flows,
  R20 live contract checks, R21 logout KEEP-vs-PURGE, R22 island
  standdown, R23 CallStyle device verify + TalkBack, R24 `v0.1.0` tag,
  R25 repo meta, R26/R27 post-train docs sweep.
- **targetSdk 37 behavior-change train** — NEW, created by the parallel
  session's jump (compileSdk 37, targetSdk deliberately held at 36 per
  its commit message; raising it needs a device train first).
- **HARVEST of this report's §f into TODO_LIST/ROADMAP** — TODO_LIST's
  Ready section is empty right now by design; the next session should
  harvest §f with docs-health routing rigor (most items below the top
  ~10 are ROADMAP fuel).

## d) TOTALLY FUCKED UP

1. **The JVM suite was silently untrustworthy for an unknown period.**
   Every test class alphabetically after `NtfySubscriptionServiceTest`
   ran under zombie reconnect churn; historical "91/0 green" claims
   passed partly by luck (machine-load-dependent timing). Severity: it
   nearly invalidated this wave's gate (both of today's first runs died
   at the same deterministic point). FIXED — root-caused, not patched:
   no leaked controller can arm a thread anymore, and the parallel
   session additionally made ntfy reconnect threads daemon so a future
   leak cannot hold a JVM. Lesson recorded in the test KDocs + AGENTS.md.
2. **Two gate runs SIGKILLed by harness session-scope teardown**
   (`session-67.scope` at 01:02:10, two seconds after the log froze).
   ~30 minutes lost. My first response was systemd/journal/watchdog
   archaeology across three timers; the cheap hypothesis (run the gate
   detached via `setsid`, poll the log) worked immediately and should
   have been tried FIRST. Severity: wasted wall-clock, no damage.
3. **I gate-raced a concurrent agent session — repeatedly.** The
   `crush-daily` rule ("pre-flight `pgrep -f gradle`; NEVER run a
   concurrent build") is now in AGENTS.md, but I discovered the parallel
   session only when its rewrite tripped my edit's file-modified guard.
   Both sessions ran gates against the same working tree for ~40
   minutes; my v4 gate validated THEIR code too, and their toolchain
   jump re-validated MINE — lucky, not designed. Worse: my R09 test
   edit accidentally REPLACED the R06 warm-tel test instead of
   appending (caught and restored in the next edit — but a multiedit
   that assumes file shape without re-reading after a foreign write is
   exactly how work gets silently destroyed).
4. **rg `-r` — fourth strike.** The recursive-search/read-the-flag-
   meaning trap, now hook-blocked on this host. Documented in AGENTS.md
   since the second strike; I still typed it.
5. **Prose-carried gate expectations drift.** "93 expected" propagated
   through two session handoffs and was simply wrong (arithmetic on a
   stale base). Derive counts from `@Test`/XML at gate time; never
   carry them in narrative state.

## e) WHAT WE SHOULD IMPROVE

1. **Detached gates by default.** Harness-managed background shells die
   with their session scope (proven twice). Fix: a `scripts/gate.sh`
   that does `setsid nohup` + log-file + tail, and AGENTS.md telling
   every session to use it for anything longer than ~2 minutes.
2. **A mechanical concurrency guard.** The AGENTS.md rule ("never a
   concurrent build") is prose. A repo-local lockfile (or a pre-flight
   script checking `pgrep -f gradle` + active `crush` sessions) turns
   the rule into a refusal. Cost: ~20 lines. This session is the
   argument for it.
3. **Kill the zombie class at the root.** `NtfySubscriptionService`'s
   raw `Thread { sleep(backoff) }` is the smell that made a test leak
   catastrophic. Structured coroutines (a service scope + `delay()`)
   would make cancellation scope-bound — the daemon-thread band-aid the
   parallel session added works, but coroutines remove the category.
4. **Derive gate expectations, never carry them.** A one-liner at gate
   end (XML-derived test count printed in the log) makes drift visible
   immediately.
5. **Time-box archaeology.** After the first unexplained process death:
   reproduce cheaply (detached rerun) BEFORE reading kernel journals,
   watchdog sources, and cgroup docs. The journal was honest — the
   killer was my own harness.
6. **Two-agent coordination protocol.** When `crush-daily` can dispatch
   sessions into a repo where an operator-driven session is live, the
   rule needed is not "don't collide" but "claim before writing": a
   session-claim line at the top of AGENTS.md or a lockfile, checked
   before any batch of edits.
7. **The formatter/ktlint adoption worked well** — big reformat landed
   atomically with a green gate. Keep that pattern: mechanical sweeps
   ride their own commit with their own evidence.
8. **Test-logging is load-bearing now** (per-test PASS/FAIL lines made
   the wedge VISIBLE as a missing line). Keep it; consider a Gradle
   test idle-timeout so a future wedge FAILS the build instead of
   hanging a session.

## f) Up to 50 things we should get done next

Ranked by impact; S <30min, M 30min-2h, L >2h. Items 1-10 are the
harvest-worthy core; 11+ are ROADMAP-fuel brainstorm (docs-health
HARVEST must apply routing rigor — most are not bounded TODO_LIST rows).

1. **Re-gate the post-jump tree end-to-end** (detached
   `testDebugUnitTest assembleDebug ktlintCheck` + build-tools 36
   zipalign; confirm the parallel session's 94/0 claim on Gradle
   9.8.2/AGP 9.4.1/compileSdk 37). Impact: Critical. S. Verification.
2. **Owner: fix GitHub Actions billing** — every gate (tests, ktlint,
   dprint, contrast, 16KB) is local-only until CI runs. R13/F041.
   Critical. S (10min, owner).
3. **Owner: Pocket ID test credential for R17** — the live detour login
   is the last unproven leg of the auth story. Critical. S (owner).
4. **Owner: SystemNix deploy** → phase 5 attaches the Forgejo→GitHub
   push mirror. R12. Critical. S (owner).
5. **Owner: regenerate the sync PAT with `repo`+`workflow` scope** (sops
   secret) — unblocks `ci.yml` mirror pushes and the 114-commit `domains`
   lag. R12/R13. Critical. S (owner).
6. **Device-proof the detour CANCEL leg** with uiautomator-dump
   coordinates (consent Cancel → origin return → `auth detour cleared`
   in logcat). Closes the last unproven detour edge. High. S-M.
7. **First device train** (needs operator + test extension, R14/R15) —
   everything honesty-contract hinges on it. Critical. L.
8. **Serve `/.well-known/assetlinks.json` from the stack** (server repo,
   feed it `scripts/assetlinks.sh` output) → real App-Link verification,
   kills the Chrome-bounce for implicit https links. R18. High. S.
9. **Verify the answer-path race** (push → FSI vs INVITE landing) on the
   live stack. R16. High. M.
10. **Investigate the dialpad's default "100"** (FAB launch with no
    extra displays "100" — intended seeding or leftover?). f9. Medium. S.
11. Unattended-gate guardrail: add a Gradle test idle-timeout (wedge
    FAILS instead of hanging). High. S.
12. `scripts/gate.sh` — the detached-gate pattern as a script (setsid +
    log + XML count tail). High. S.
13. Concurrency pre-flight script + repo-local gate lockfile (mechanize
    the AGENTS.md rule). High. S.
14. Replace `NtfySubscriptionService`'s raw `Thread{sleep}` reconnect
    with a structured-coroutine scope (cancellation by scope, zombie
    class dies). Medium. M.
15. Derive-and-print the XML test count at gate end (expectation drift
    guard). Medium. S.
16. Lint/review rule: `Robolectric.buildService`/`buildActivity` MUST
    tear down (code-quality-scan rule or review-checklist line).
    Medium. S.
17. R19: emulator pass — composer attach, external-link hand-off,
    tab-nav back stack WITH a logged-in island session. High. M
    (blocked on Q1 credential).
18. R20: live contract checks (session fields, payload shapes, CSRF
    dance) against the real stack. Medium. M.
19. R21: operator decision logout KEEP-vs-PURGE → deregistration design
    (JS bridge vs cookie-poll). Medium. S decision + M impl.
20. R22: island-standdown seam (owner nod first; duplicate registration
    observed). Medium. M.
21. R23a: CallStyle device verification (post-time style + fallbacks).
    Medium. S.
22. R23b: TalkBack walkthrough + battery-prompt allow/deny verify.
    Medium. M.
23. R24: cut the `v0.1.0` annotated tag (operator timing call).
    Medium. S.
24. R25: Forgejo description + icon-parity decision (both-or-neither).
    Low. S.
25. R26/R27: post-train docs sweep — honesty retirement, next CHANGELOG
    cut. Low-Med. L.
26. targetSdk 36→37 behavior-change audit (new debt from the toolchain
    jump; needs the device train first). Medium. L.
27. Re-run the full emulator train (`emulator-train.sh`) on the post-jump
    tree — boot + install + launch + screenshot proof on
    AGP 9/compileSdk 37. High. M.
28. Update the AGENTS.md verification gate to include `ktlintCheck` in
    the one-command gate line (the gate is now tests + assemble +
    zipalign + ktlint + dprint — say so in one place). Medium. S.
29. Consistency pass: AGENTS.md/dprint ownership lines vs the new
    `.editorconfig` (dprint owns JSON/YAML/MD/Dockerfile; ktlint owns
    Kotlin) — remove any stale "Kotlin style by review" phrasing.
    Low. S.
30. After the mirror attaches: verify mirror lag
    (`git rev-parse origin/main github/main` equal) and push the
    backlog by hand once. High. S.
31. SessionApi has no dedicated unit test class — add MockWebServer
    tests for resume/CSRF/push-device upsert (the server seam's client
    side is the least-tested IO edge). Medium. M.
32. Document the post-R18 real-device App-Link expectation
    (`pm set-app-links-user-selection` is emulator-only simulation;
    real devices need verified assetlinks + user consent). Low. S.
33. Instrument the detour CANCEL path with the same transition-only
    debug logging the bless path got — makes the next train's logcat
    self-evidencing. Low. S.
34. Consider the detour TTL (ROADMAP §4) as a bounded task if the
    operator wants it. Low. M.
35. WebSurface: dedupe the bless/clear logging authorities map (memory
    growth is bounded by distinct authorities — fine, but assert the
    bound in a test). Low. S.
36. DialpadActivity refactor: extract recents persistence into a small
    store class (the file is ~460 lines and growing). Low. M.
    ~~37. Session watcher: revisit 10-min cookie-poll vs push-driven teardown
    after R21's decision lands. Low. M.~~ done — answered by the 2026-11-10 wake-lane rework: the 10-minute watcher stays, teardown only on a definitive 401/403 (tri-state probe; AGENTS.md wake-lane gotcha)
37. Add the `Android 15+ mic-FGS background-start ban` answer-path check
    to the device-train checklist (CallService only ever starts from
    user interaction — prove it). Medium. S (checklist line).
38. Pin the Robolectric `@Config(sdk=[34])` rationale in AGENTS.md (why
    34 and not compileSdk-matching 37 after the jump — the answer may
    be "shadow coverage", but it should be written down). Low. S.
39. Verify `assembleRelease` + R8 on the post-jump toolchain (the
    release lane was green pre-jump; AGP 9 changed minification
    defaults). Medium. M.
40. Contrast gate: run `contrast-check.py` on the current tree (it is a
    CI step that has also never run remotely; local run proves the
    tokens post-reformat). Medium. S.
41. Confirm Dependabot's new-toolchain reality (Gradle 9.8.2 wrapper
    updates, AGP 9.4.1, Kotlin 2.4.21 entries all resolve) after the
    jump. Low. S.
42. Cross-check `libs.versions.toml` vs the root buildscript Kotlin pin
    equality rule the jump introduced (documented in af0084f — a drift
    here breaks the build opaquely; a catalog-check task could enforce
    it). Medium. S.
43. Write the session-4 retrospective errata into the 15-17 plan (the
    premise-bust + the two-wedge story) so the plan is self-contained
    for future readers. Low. S.
44. Prune `/tmp` evidence artifacts (gate logs, jstacks, screenshots)
    into `docs/status/attachments/` or delete — they are the only
    surviving copies of the wedge proof. Low. S.
45. Emulator: decide whether `crush-daily` sessions may boot the AVD
    (two adb trees, port 5037 contention — the AGENTS.md gotcha) and
    encode the answer in the emulator scripts' pre-flight. Low. S.
46. Add a `.gitignore`/docs note for `/tmp`-independent evidence when
    trains matter (screenshots belong in the repo, not `/tmp`).
    Low. S.
47. README: the 94-test + ktlint + toolchain facts belong in the build
    section once the post-jump gate closes (§f-1). Low. S.
48. ROADMAP: record the "scheduled sessions may refactor under you"
    reality — the multi-agent workflow is a permanent fact of this
    repo, and its risks (§d3) deserve a named mitigation. Low. S.
49. Close the loop: after §f-1's gate is green, flip TODO_LIST's Ready
    section from empty to the harvested top-10 of this list. Medium. S.

## g) Three questions I cannot answer myself

1. **Pocket ID test credentials for the live detour login (R17).** The
   in-shell OIDC dance is emulator-proven up to the IdP pages; the
   login itself (authenticate → callback → session mints in the WebView
   jar) needs a real account on `auth.larsartmann.cloud`. Everything
   else in the auth story is proven; this is the last leg.
2. **Where should the IdP's cancel/error redirect land?** If Pocket ID
   cancels back to a stack URL (e.g. `/login?error=...`), the detour's
   CANCEL-leg proof (§f6) and the server seam both depend on that
   target existing — it is a webphone-server-repo decision I cannot
   make or discover from this repo.
3. **Is the dialpad's default "100" intended?** A FAB launch with no
   extra displays "100" seeded in the number field. If it is intended
   (e.g. the operator's own extension as a placeholder), I will leave
   it and document it; if it is leftover, I will find the seeding line
   and remove it. I could not find where it comes from — it may not
   even be seeding but a stale render.

---

**HARVEST note:** §f items 1-10 (plus 11-16 as tooling rows) are
TODO_LIST-ready; the tail is ROADMAP fuel. TODO_LIST's Ready section is
intentionally empty after the R11 sweep — the next docs-health HARVEST
should refill it from this list with routing rigor.

**WAITING FOR INSTRUCTIONS.** Nothing was pushed (local tip
`af0084f` + the daemon sweeps; last operator-instructed push was
`2e06567`).
