# Status — Lint is in the gate, the toolchain is current, the tree is red mid-fix, origin moved (2026-11-25 05:56)

**Created:** 2026-11-25 05:56 CET · agent (Crush) · backlog basis: this session's work (01:43 to 05:56) plus the open rows of the two earlier reports (`2026-11-24_23-33_android-lint-never-gated-two-bugs-fixed-and-version-lag-audit.md`, `2026-11-25_00-42_lint-zero-errors-speaker-route-pinned-dependabot-root-cause.md`).

> **Read this first: the tree is RED right now, on purpose, and it is my doing.**
> HEAD holds four new test pins for the call foreground service that are
> AHEAD of their fix. `testDebugUnitTest` reports 163 completed, 4 failed.
> Everything else is green (assemble, ktlint, lint, 159 other tests). The
> gate fails closed at step 2, so `scripts/release.sh` cannot cut a release
> from this tree. The fix is designed and written out in (b1); it was not
> applied because the "status update, then wait" instruction arrived
> between the red run and the green run. Nothing was pushed by me.
>
> **Second thing: `origin/main` moved during the session.** Forgejo's `main`
> went from `e0787d9` to `f72e24b` at 04:42:41 (`git reflog show
> refs/remotes/origin/main`: "update by push"). I ran no `git push` in this
> session. I do not know who did (question g1). GitHub's mirror is still at
> `e0787d9`.
>
> **Session scope:** the continuation after "NOW GET SHIT DONE! THE WHOLE
> TODO LIST!". Work stopped mid-change-set when the "status update right
> now, then wait" instruction arrived. Point-in-time snapshot; the living
> docs remain the truth. Describes `ef1a53b` (`git rev-parse --short HEAD`
> at report time). The session started on `d550cd2`.
>
> **Verified here** (command, exit status, artifact):
>
> - **The tree as it stands, measured 05:54 to 05:56 on `ef1a53b`**:
>   `./gradlew ktlintCheck :app:lintDebug :app:testDebugUnitTest --continue`
>   → exit 1, `BUILD FAILED in 1m 59s`
>   (`/tmp/webphone-report-measure.log`). `ktlintCheck` and `lintDebug`
>   passed; `testDebugUnitTest` ran 163 tests, 159 `PASSED`, 4 `FAILED`
>   (the four d7 pins: `CallActivityTest` ×2, `CallServiceTest` ×2).
>   The lint text report ends `0 errors, 0 warnings, 1 hint`;
>   `python3 scripts/lint-lag-report.py` → exit 0, prints the one hint
>   (`OldTargetApi`).
> - Lint counts on the way: 14 warnings → 12 after the manifest fix
>   (01:45, exit 0) → 6 after the resource cleanup → 1 after the version
>   batch → `0 errors, 0 warnings, 1 hint` after the `lint {}` block.
>   Contrast check after the pair removal: `49 checks, 0 failure(s)`.
> - Full unit suite on the OLD toolchain after the resource and
>   notification edits: 157 `PASSED`, 0 `FAILED`, exit 0, 1m 29s
>   (`/tmp/webphone-suite-step2.log`).
> - Full unit suite on the NEW toolchain (Gradle 9.9.2, AGP 9.5.2,
>   Robolectric 4.18.1): 157 `PASSED`, 0 `FAILED`, exit 0, 1m 34s
>   (`/tmp/webphone-suite-upgraded.log`).
> - Notification-note pins, meta-proven: `--tests '*RingEventRouteTest'`
>   before the fix → exit 1, "11 tests completed, 2 failed"
>   (`/tmp/webphone-note-red.log`); after → 11 passed.
> - Post-skip pins, meta-proven: one of two new pins red before the fix
>   (`/tmp/webphone-post-red.log`); 10 of 10 green after
>   (`/tmp/webphone-post-green.log`); the permission branch red again with
>   its check disabled, green restored (`/tmp/webphone-post-red2.log`,
>   `-green2.log`).
> - `allWarningsAsErrors`, meta-proven: five deliberate redundant casts →
>   exit 1, `e: warnings found and -Werror specified` plus 5 × "Cast is
>   redundant." (`/tmp/webphone-wae-red.log`); removed → exit 0.
> - Hermeticity probe (a throwaway Robolectric test, since deleted):
>   `CallLayer.ensureWired` with the default seams answered
>   `I/SessionApi: GET /api/session answered 401` 613 ms later
>   (`/tmp/webphone-hermeticity-probe.log` and the test XML's system-out).
> - Fresh-clone test of the wrapper: `git clone` to `/tmp`, then
>   `nix develop -c ./gradlew --version` → exit 1, `Error: Unable to access
>   jarfile .../gradle/wrapper/gradle-wrapper.jar`
>   (`/tmp/webphone-fresh-clone-gradlew.log`).
> - Wrapper upgrade: `./gradlew --version` → `Gradle 9.9.2`, distribution
>   fetched and checked against `distributionSha256Sum`; after the second
>   `wrapper` pass the wrapper jar hashes to
>   `621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8`,
>   the `wrapperChecksum` `services.gradle.org/versions/current` publishes.
> - Lint semantics, probed: `warningsAsErrors = true` alone with one
>   warning present → exit 1, "Lint found 93 errors" in the run that also
>   had test sources on (`/tmp/webphone-lint-probe1.log`). With real drift
>   (lifecycle temporarily put back to 2.11.0) and the drift checks demoted
>   to informational → exit 0, `0 errors, 0 warnings, 1 hint`, and the hint
>   is printed in the text report (`/tmp/webphone-lint-probe2.log`).
> - Lint offline, run in a network namespace with no network
>   (`unshare -rn`): `BUILD SUCCESSFUL in 31s`, 1 hint
>   (`/tmp/webphone-lint-offline.log`).
> - `scripts/lint-lag-report.py` exit paths: live report 0, a report that
>   holds a drift hint 0, a report with a warning 1, a report without a
>   summary line 1, a missing report 1.
> - d7 pins before the fix: `--tests '*CallServiceTest' --tests
>   '*CallActivityTest'` → exit 1, "6 tests completed, 4 failed"
>   (`/tmp/webphone-d7-red.log`).
> - Git facts at report time: `git rev-parse origin/main github/main HEAD` = `f72e24b`, `e0787d9`, `ef1a53b`; `git rev-list --count
>   origin/main..HEAD` = 3; `git diff --stat e0787d9..HEAD` = 45 files,
>   +1947 / −183; `@Test` annotations under `app/src/test` = 163; working
>   tree clean at 05:56.
> - Registry facts fetched, not remembered: Gradle `9.9.2` (released
>   2026-11-19); AGP stable `9.5.2` (`9.6.0-alpha06` is the newest
>   pre-release); Robolectric `4.18.1`; ktlint engine `1.8.0`; ktlint
>   plugin `14.2.0` (latest); Kotlin stable `2.4.21` (latest stable,
>   `2.5.0-RC` is out); `actions/setup-java` `v6.3.0` = commit
>   `d1374f8ac74474200d16ef8ecee22c7ae7a51880`; `actions/checkout` `v7.0.1`,
>   `actions/upload-artifact` `v7.0.2`, `android-actions/setup-android`
>   `v4.0.4` all current; dprint `0.65.0`, plugins json `0.26.0`, markdown
>   `0.29.0`, dockerfile `0.8.0` (the repo pins `0.64.3`, `0.23.0`,
>   `0.22.1`, `0.4.1`). AGP 9.5 needs Gradle 9.6.0 or newer (release-notes
>   table, read by a fetch subagent).
> - AGP's `Lint` DSL surface read from the 9.5.2 API jar with `javap`
>   (`warningsAsErrors`, `informational`, `disable`, and `textReport` /
>   `textOutput` deprecated). ktlint's `KtlintExtension.version` property
>   read the same way from the 14.2.0 plugin jar (default `1.5.0`).
>
> **NOT verified here:**
>
> - **The whole gate has not run in its new eight-step form.** The new
>   step 4 (lint + lag report) ran on its own; `bash scripts/verify-gate.sh`
>   with all eight steps in sequence did not.
> - **The release variant was not built after the mipmap folder rename and
>   the toolchain jump.** The debug variant hit a stale-incremental-merge
>   state after the rename (the merged output lacked the launcher icon and
>   a cached broken output replayed it; `:app:mergeDebugResources
>   --rerun-tasks` repaired it). The release variant's merge state is from
>   before the rename and was never re-run, so `assembleRelease` may hit
>   the same "resource mipmap/ic_launcher not found". Untried.
> - **No emulator, no device.** The toolchain jump (Gradle, AGP,
>   Robolectric) asks for an emulator train by the repo's own rule; it was
>   not run.
> - **The d7 fix does not exist yet.** The leak it addresses (the
>   microphone-typed foreground service outliving a call that ends while
>   no call screen is started) is established by reading three callers of
>   `CallService.stop` and nothing in the engine that stops the service;
>   it was not reproduced, and no test shows the leak itself, only the
>   missing self-retire.
> - **A second defect I traced by reading only:** a call screen opened for
>   a NEW call first renders the previous call's `ENDED` state and used to
>   answer it with `CallService.stop`, whose stop path calls
>   `CallEngine.hangUp()`. If that lands after the new dial created its
>   media and before its INVITE left, it tears the new call down. That
>   needs WebRTC to reproduce and cannot run on the JVM. The timing on a
>   real phone is unknown.
> - **The fresh clone was only proven broken, never proven fixed.** The
>   wrapper jar became a tracked file in `1ced5f2` (`git ls-files
>   gradle/wrapper/` lists it); the positive clone test was not re-run.
> - **GitHub Actions has still never executed a job.** The workflow now
>   has a lint step, a lag-report step and a new `setup-java` pin, all
>   unexercised.
> - **Who pushed `f72e24b` to Forgejo, and whether anything now pushes
>   without a hand.** Not investigated; the reflog line is all I know.
> - `buildflow` was not run this session (an earlier dry run from
>   2026-11-24 is the only BuildFlow evidence). `docs-gates` was not run.
>   The Gradle 9.9.2 problems report was not opened; the console printed no
>   deprecation.
> - Robolectric 4.18's own breaking changes (SDK 23 dropped,
>   `shadows-httpclient` removed) were read from the release notes and
>   matched against this repo by reading; nothing here uses either.
> - dprint over the two files this report touches was run right after
>   writing (bare `fmt`, then `-- check`); its result is not in this file.

## a) FULLY DONE

Everything below is on `main`, unpushed past `f72e24b`, and green in the
05:56 measurement unless a row says otherwise.

- **The manifest mistake (d1 of 00:42) is fixed.**
  `tools:targetApi="tiramisu"`; both manifest warnings are gone (14 → 12).
- **Lint is at zero errors and zero warnings.** The last six warnings went
  like this:

  | Warning                   | Count | How                                                                                                                                         |
  | ------------------------- | ----- | ------------------------------------------------------------------------------------------------------------------------------------------- |
  | `UnusedResources`         | 5     | `chrome_glass_pressed`, `chrome_on_glass_muted` (+ its contrast pair), `ic_dialpad`, `caller_avatar` deleted; `line_busy` wired (next item) |
  | `ObsoleteSdkInt` (mipmap) | 1     | `git mv mipmap-anydpi-v26 mipmap-anydpi`                                                                                                    |
  | version drift             | 5     | version batch A                                                                                                                             |
  | `OldTargetApi`            | 1     | demoted to informational on purpose, still printed as the lag report's one standing hint                                                    |

- **The busy-line note now says why the call never rang.** A second ring
  on a live line posts "Missed call / Bob · Line busy, call declined
  automatically" instead of a bare "Missed call / Bob". The string
  existed unused; the router's own comment promised it.
- **On-sight defect: two notices shared one notification slot.** The
  missed-call note and the "Ringing paused" sign-in-expired notice both
  posted under id 1004, so whichever came second silently replaced the
  other (the ids were introduced in `ba328a5` and `d820810`). The
  missed-call note is 1005 now. Pinned: `a missed-call note and the
  sign-in-expired notice coexist`, red then green.
- **On-sight defect: the JVM suite phoned the live stack.** Three test
  classes reached production seams with their defaults on:
  `RingEventRouteTest` and `RingEventApiFloorTest` (every `ring` asks the
  call layer to wire itself, which resumes the session over real HTTP)
  and `CallLayerSessionDeadTest` (the teardown's best-effort
  deregistration is a CSRF fetch plus a DELETE). The seams are closed in
  those three classes (`@Before`). The probe above is the proof of the
  mechanism.
- **On-sight defect: the Gradle wrapper jar was not in the repository.**
  A global `*.jar` ignore kept `gradle/wrapper/gradle-wrapper.jar` out
  of every clone (GitHub's `gradle/wrapper/` holds only the properties
  file), so `./gradlew` failed on any fresh checkout, CI's first step
  included. The repo `.gitignore` now negates the rule for that one path
  (a repo rule outranks the global one), and the jar is tracked.
- **Version batch A.** Gradle wrapper 9.8.2 → 9.9.2 with
  `distributionSha256Sum`; AGP 9.5.1 → 9.5.2; Robolectric 4.17 → 4.18.1;
  firebase-messaging 26.0.1 → 26.0.2; lifecycle 2.11.0 → 2.11.1.
  `assembleDebug` and the full suite are green on the result.
- **The ktlint engine is pinned and current: 1.8.0** (was the plugin's
  default 1.5.0), in the version catalog as `ktlintEngine`. 1.8.0's
  `blank-line-between-when-conditions` rule added roughly 50 blank lines across 11 files; `ktlintCheck` is green.
- **Android Lint is in the gate, with warnings fatal.** `app/build.gradle.kts`
  has a `lint {}` block: `warningsAsErrors = true`, and the four
  version-ish checks (`GradleDependency`,
  `AndroidGradlePluginVersion`, `NewerVersionAvailable`, `OldTargetApi`)
  demoted to informational. `scripts/lint-lag-report.py` prints the
  informational lines as the version-lag report and refuses a report that
  still holds an error or a warning. `scripts/verify-gate.sh` is eight
  steps now (step 4 = lint + lag report); `ci.yml` runs both after the
  KGP parity gate.
- **Kotlin warnings are build failures** (`allWarningsAsErrors`, main and
  test sources), meta-proven red and green.
- **CI pins.** `actions/setup-java` v6.2.0 → v6.3.0 (SHA-pinned); the
  job is named "Build, test, lint, align, format"; the header comment
  that still described the resolved `workflow`-scope trap is corrected.
- **Pins added.** `RingEvent.post` names both refusals in logcat
  ("notifications are disabled", "POST_NOTIFICATIONS not granted"), each
  pinned; `SpeakerRouteApiFloorTest` runs the speaker route at the API 31
  floor.
- **The ktlint rule `when-entry-bracing` is disabled, with its reason in
  `.editorconfig`.** See (d4): it was tried first.
- **Open rows of the 00:42 report closed by this session:** 1, 4, 10, 11, 12, half of 3 (the `ci.yml` half) and half of 9. NOT done: 29 (drop `viewBinding`).

## b) PARTIALLY DONE

- **d7, the call service outliving the call.** Four pins are written and
  RED; the fix is not applied. The design, ready to type:
  1. `CallService` starts a collector on `Dispatchers.Main.immediate` AFTER
     `startForeground` succeeded (a service started with
     `startForegroundService` must not be brought down before that call).
     It drops the StateFlow's initial value (the phase at start is never
     a verdict: an outbound dial starts the service while the engine still
     shows the previous call's `ENDED`) and retires the service on a
     transition to `ENDED`: `stopForeground(STOP_FOREGROUND_REMOVE)` then
     `stopSelf()`, idempotent. The scope is cancelled in `onDestroy`.
  2. `ACTION_STOP`, `CallService.stop()` and its three call sites go away:
     every caller either already calls `CallEngine.hangUp()` itself or
     (the call screen's `ENDED` render) has no call to end. With the
     service ending on its own, the explicit stop is redundant, and its
     implicit `CallEngine.hangUp()` is what makes the stale-`ENDED` stop
     dangerous.
  3. `CallActivity.render` keeps `scheduleEndAutoFinish()` and loses
     `CallService.stop(this)`; `hangUpAndFinish()` keeps
     `CallEngine.hangUp()` and loses the stop; `CallNotificationReceiver`'s
     hang-up keeps `CallEngine.hangUp()` and loses the stop.
     Expected: the four pins go green; the suite returns to 163 of 163.
     Effort: S. Blocker: the stop instruction.
- **Docs for this session.** Nothing written except this report and the
  two index rows in `docs/status/README.md`. `CHANGELOG.md`, `AGENTS.md`,
  `TODO_LIST.md`, `ROADMAP.md`, `FEATURES.md`, `README.md`, `SECURITY.md`
  are all behind (the list is in §f). Effort: M.
- **The gate.** Each step has run on its own at some point in the session
  except the release-variant ones; the eight-step wrapper as a whole has
  not. Effort: S (about 15 min unattended).
- **Hermeticity.** Three classes are closed. There is no guard that stops
  the next test class from reaching the stack again; a source-based rule
  (Konsist) is the candidate, not written. Effort: S to M.
- **Index rows.** `docs/status/README.md` gets rows for the 00:42 report
  and this one in the same edit as this file (the 00:42 report's own
  "what I forgot" item).

## c) NOT STARTED

Carried from the 00:42 §f unless marked new. Priorities unchanged.

- 8: HARVEST of the §f lists, `docs-gates run` (cadence was already red;
  19 live reports now).
- 13 (owner: drift policy and Dependabot), 14 (owner: speaker off), 15
  (device train), 16 (API 31 to 33 ring proof on an image), 17 (owner:
  push and release), 18 (owner: squash the daemon blobs).
- 20: API-floor pins for Telecom inbound, `DialpadActivity`,
  `WebSurface`, `MainActivity` at SDK 31. (`CallActivity` has two pins
  from this session, at the default SDK 34, not at the floor.)
- 22: `CallControl.requestCallEndpointChange` versus `AudioManager`.
- 23 OSV-Scanner, 24 dprint 0.65.0 and plugins, 25 `nix flake update`,
  26 dependency-analysis, 27 architecture tests, 28 targetSdk 37 (needs
  an API 37 train), 30 `git remote prune github`, 33 the pin-to-green
  script.
- New: the release variant's merge state (see NOT verified); the positive
  fresh-clone proof; the emulator train the toolchain jump asks for; 29: drop `viewBinding` (`app/build.gradle.kts`, one line, no layout uses it);
  `CallEngine.hangUp()` on an already ended call re-publishes `ENDED`
  with `endedBy = LOCAL` (noticed, untouched).

## d) TOTALLY FUCKED UP

- **d1. I stopped between a red pin and its fix.** Four failing tests are
  committed (`ef1a53b`) and `main` is red. The pattern I used all session
  for every other pin (write the pin, run red, apply the fix, run green,
  all inside a few commands) was broken exactly once, at the largest
  change, and that is the state the session paused in. It fails closed
  (the gate stops at step 2), so nothing can ship from here by accident.
  Root cause: I ran the red run as its own step and had not written the
  fix yet.
- **d2. `origin/main` moved and I cannot say who moved it.** 04:42:41,
  "update by push", `f72e24b`. Earlier pushes in the reflog went to both
  remotes a second apart (a script or a hand pushing both); this one
  touched Forgejo only. If something now pushes on its own, the repo's
  written rule ("nothing pushes any remote automatically") is false and
  every "unpushed" statement in these reports needs re-checking.
- **d3. A throwaway probe became two commits.** The hermeticity probe
  test sat in the tree for a few minutes and the auto-commit daemon
  committed it (`20490f9`), then its deletion (`1ced5f2`). Harmless, but
  it is noise in a history the owner still has to squash.
- **d4. I formatted ten main source files before deciding I wanted the
  rule.** `ktlintFormat` with engine 1.8.0 rewrote every `when` in ten
  main files (about 300 lines) and only then did the compiler show what
  the bracing does to a `when` used as a statement: `-> Unit` became
  `{ Unit }` and five sites warned "Expression is unused". I reverted the
  files with `git restore` (all were changes I had made minutes earlier,
  none committed) and disabled the rule. The order should have been:
  read what a new rule does to this code, decide, then format.
- **d5. I printed about 250 report lines into my own context, twice**
  (the lint probes). The counts by check id were all that mattered.
- **d6. An edit call re-indented a block wrongly** (`RingEvent.kt`): the
  tool matched on whitespace-equivalent text and re-indented my
  replacement to the wrong depth, caught by ktlint one run later and
  repaired by hand. Whitespace-sensitive replacements now go through a
  small script that asserts the match count.
- **d7. Still true, and larger:** every behavior change of this session
  is shadow-proven only. Since 00:42 these were added: the notification
  id, the busy-line text, the avatar hidden from screen readers, two
  logcat lines, and a toolchain jump (Gradle, AGP, Robolectric). None has
  run on the emulator or the phone.
- **d8. The 00:42 open questions are still open**, because "get shit
  done" answers none of them: I chose drift-visible-not-fatal, kept
  "speaker off returns to the platform default", and pushed nothing.
- **d9. Two defects I found by reading and left alone** (they are in §f):
  the stale-`ENDED` stop that can hang up a new call, and `hangUp()` on
  an ended call rewriting `endedBy`.
- **d10. CI is a bigger unknown than before.** It had never run; now it
  also carries a lint step that needs network for its version checks
  (offline it passes silently without drift lines, measured) and a
  wrapper fix that was never proven in a positive clone.

**What I forgot.** (1) To write the fix before pausing (d1). (2) To
re-check the remote refs after the first hour; I assumed they had not
moved because I had not moved them (d2). (3) To delete the probe in the
same command that created it (d3). (4) To run the release variant after the rename. (5) To drop `viewBinding` (row 29). (6) The 23:33 report's
index row is still missing; this report's edit adds the 00:42 row and its own.

**What I could have done better.** (1) Treat "pin, red, fix, green" as
one unit that is never split across a pause. (2) Read a new lint or
format rule's effect before applying it repo-wide (d4). (3) Print
summaries. (4) Re-run the release merge right after repairing the debug
one, since the cause was the same rename.

**What can still improve.** Everything in (e) and (f).

## e) WHAT WE SHOULD IMPROVE

- **Never split a meta-proof across a pause.** The fix is written before
  the first red run, so a red tree lives for one command, not a session.
- **Decide a rule before formatting with it.** For each new ktlint or
  lint rule: what does it do to THIS code, does the compiler agree, then
  apply or disable with the reason at the config.
- **The gate needs the release variant too.** The wrapper checks the
  debug APK; the artifact the phone receives is the release one, and its
  merge state is exactly where today's glitch would have surfaced at
  release time.
- **Remote state is a fact to measure, not assume.** Read
  `git rev-parse origin/main github/main` at the start and the end of a
  session; today it would have caught the push at once.
- **A suite-wide hermeticity guard.** Three classes were leaking requests
  to production and nothing could notice; a Konsist rule ("a test class
  that routes a ring or tears the session down must flip the seam") can.
- **One mechanism for a lifetime.** The call service had two ways to end
  (the UI's stop, and nothing else); after (b1) it has one (the engine).
  The same shape is worth checking for the ntfy service.
- **Summaries over raw output** in agent sessions: counts by id, then
  open one example.

## f) Up to 50 things we should get done next

Ranked by impact. Rows 1 to 20 are agent-executable; the rest need the
owner or a device.

| #  | Task                                                                                                                                                                                                                                           | Impact   | Effort | Category      |
| -- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ------ | ------------- |
| 1  | Apply the d7 fix exactly as written in (b1); the four red pins go green; suite 163 of 163                                                                                                                                                      | Critical | S      | Bug           |
| 2  | Run `bash scripts/verify-gate.sh` (eight steps) on the green tree and record the verdict                                                                                                                                                       | Critical | S      | Quality       |
| 3  | Release variant: `:app:mergeReleaseResources` (`--rerun-tasks` if the rename glitch bites), `assembleRelease`, zipalign and `abi-check` on the release APK                                                                                     | Critical | S      | Quality       |
| 4  | Positive fresh-clone proof: clone to `/tmp`, `nix develop -c ./gradlew --version`                                                                                                                                                              | High     | S      | Quality       |
| 5  | Emulator train after the toolchain jump: `nix develop .#emulator -c bash scripts/emulator-train.sh`                                                                                                                                            | High     | M      | Quality       |
| 6  | `CHANGELOG.md` `[Unreleased]`: Fixed (API 31 to 33 ring crash, outbound direction, speaker route release, shared notification id, tests phoning production, missing wrapper jar), Changed (busy-line note, speaker off)                        | High     | S      | Documentation |
| 7  | `AGENTS.md`: corrections (test count, viewBinding, Dependabot, gate is eight steps, Gradle 9.9.2 / AGP 9.5.2 / Robolectric 4.18.1 / ktlint engine 1.8.0) and the new gotchas (lint block, lag report, wrapper jar, rename glitch, hermeticity) | High     | M      | Documentation |
| 8  | `TODO_LIST.md` / `ROADMAP.md`: correct the warning-freeze row, add the open rows of all three §f lists; `docs-gates run`                                                                                                                       | High     | S      | Documentation |
| 9  | `FEATURES.md` and the README honesty contract: the lint gate, the new pins, the re-checked markers                                                                                                                                             | Medium   | S      | Documentation |
| 10 | `SECURITY.md`: response-time line (BuildFlow finding)                                                                                                                                                                                          | Medium   | S      | Documentation |
| 11 | Hermeticity guard: Konsist rule for test classes that reach the wiring seams                                                                                                                                                                   | Medium   | M      | Quality       |
| 12 | API-floor pins at SDK 31 for Telecom inbound, `DialpadActivity`, `CallActivity`, `WebSurface`, `MainActivity`                                                                                                                                  | High     | M      | Quality       |
| 13 | `CallEngine.hangUp()` on an ended call: decide and pin (no-op, or keep re-publishing)                                                                                                                                                          | Medium   | S      | Bug           |
| 14 | dprint 0.65.0 and its plugins; review what `buildflow` auto-configure would write for `dprint.json` first; one dprint version for CI and the gate                                                                                              | Medium   | M      | Quality       |
| 15 | `nix flake update`, then the gate                                                                                                                                                                                                              | Low      | S      | Cleanup       |
| 16 | OSV-Scanner pass over a Gradle lockfile; note the prebuilt libwebrtc from 2025-09                                                                                                                                                              | High     | M      | Quality       |
| 17 | Dependency-analysis plugin one-shot (`projectHealth`)                                                                                                                                                                                          | Low      | S      | Quality       |
| 18 | Architecture tests evaluation beyond the hermeticity rule                                                                                                                                                                                      | Medium   | M      | Quality       |
| 19 | Evaluate `CallControl.requestCallEndpointChange` versus `AudioManager` routing; ROADMAP if adopted                                                                                                                                             | Medium   | M      | Quality       |
| 20 | `git remote prune github`; the pin-to-green script (`scripts/`)                                                                                                                                                                                | Low      | S      | Process       |
| 21 | Owner: say who pushed `f72e24b`, and whether a daemon pushes now; AGENTS depends on the answer                                                                                                                                                 | High     | S      | Process       |
| 22 | Owner: squash the daemon blobs (now 3 past `f72e24b`, one of them red) before the next push                                                                                                                                                    | High     | S      | Process       |
| 23 | Owner: device train. Now on the list: outbound direction on BT and watch, speaker on then off with a headset, a second call after a speaker call, the busy-line note, a call ended while the screen is away                                    | High     | M      | Bug           |
| 24 | Owner: push both remotes and release, after rows 1 to 5 are green                                                                                                                                                                              | High     | M      | Feature       |
| 25 | Owner: keep or overrule the three choices in (d8) and the ktlint rule exception in (d4)                                                                                                                                                        | Medium   | S      | Process       |
| 26 | API 31 to 33 ring proof on a real device or image (the AVD is API 36 only)                                                                                                                                                                     | High     | M      | Quality       |
| 27 | targetSdk 36 → 37 after an API 37 train (existing row)                                                                                                                                                                                         | Medium   | M      | Feature       |
| 28 | detekt re-check trigger, dated, in ROADMAP                                                                                                                                                                                                     | Low      | S      | Documentation |
| 29 | First real GitHub Actions run (billing), with the lint and lag-report steps                                                                                                                                                                    | Medium   | M      | Process       |
| 30 | Optional: upload the lint SARIF report as a code-scanning alert                                                                                                                                                                                | Low      | S      | Quality       |

## g) Questions I can not figure out myself

1. **Did you push `f72e24b` to Forgejo at 04:42 today?** The reflog of
   this checkout says "update by push" and names no author. I did not
   push. If it was you, nothing changes. If it was not, something now
   pushes from this checkout without a hand, and I need to know that
   before I touch any remote state. I looked as far as the reflog and
   the hooks directory (empty) and stopped there, because the answer is
   not in this repository.
2. **The ktlint rule I switched off, `when-entry-bracing`: is compact or
   braced the style you want?** The rule is the official style's; it
   makes `-> Unit` into `{ Unit }` in statement `when`s, which the
   compiler then flags as an unused expression. I kept the compact
   arrows and wrote the reason into `.editorconfig`. That is a taste
   call with a real cost either way (five compiler warnings against
   about 300 reformatted lines), and the repository holds no note that
   settles it.
3. **Carried from 00:42, still open: ship the fixes before or after a
   device train, and is "speaker off returns to the platform default"
   the product you want?** The list of shadow-proven behavior changes
   has grown since then (d7). I can make the gate green; I cannot place
   a call.
