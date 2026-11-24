# Status — Android Lint at zero errors, the speaker route pinned, Dependabot's killer found (2026-11-25 00:42)

**Created:** 2026-11-25 00:42 CET · agent (Crush) · backlog basis: this session's work plus the open rows of the 23:33 report (`2026-11-24_23-33_android-lint-never-gated-two-bugs-fixed-and-version-lag-audit.md` §f).

> **Session scope:** the continuation of the 23:33 audit after the "WHOLE
> TODO LIST" instruction. Work stopped mid-change-set when the "status
> update now, then wait" instruction arrived, so several items below are
> measured but unfinished. Point-in-time snapshot; the living docs remain
> the truth. Describes `db36e7b` (`git rev-parse --short HEAD` at report
> time). The session started on `e0787d9`; both remotes are still there.
>
> **Verified here** (command, exit status, artifact):
>
> - `bash scripts/verify-gate.sh` on the two-bug-fix tree (`b7cda29` plus
>   the dprint-formatted 23:33 report in the working tree, committed as
>   `f0665aa`) → exit 0, `GATE GREEN`, 151 `PASSED` and 0 `FAILED` lines
>   (`/tmp/webphone-gate-baseline.log`): assembleDebug, testDebugUnitTest
>   (1m 53s), ktlintCheck + kgpParityCheck, 16 KB zipalign, abi-check,
>   contrast (50 checks, 0 failures), dprint check.
> - `nix develop -c ./gradlew :app:lintDebug --console=plain` right after
>   → exit 1, "Lint found 8 errors and 34 warnings"
>   (`/tmp/webphone-lint-postfix.log`).
> - `nix run nixpkgs#dprint -- fmt` on the 23:33 report → "Formatted 1
>   file"; `-- check` → exit 0.
> - Raw registry metadata fetched with the `fetch` tool: Google Maven
>   `group-index.xml` for `androidx.core`, `androidx.appcompat`,
>   `androidx.webkit`; `maven-metadata.xml` for `androidx.test:core-ktx`,
>   `com.squareup.okhttp3:okhttp`, `io.getstream:stream-webrtc-android`.
> - Dependabot root cause: `gh api repos/LarsArtmann/webphone-android/pulls/{7,8,9,10}`
>   (created_at, closed_at, merged_at), `gh api .../branches` (only
>   `main`), and Gitea's `services/mirror/mirror_push.go` read from
>   `raw.githubusercontent.com/go-gitea/gitea/main` (the push runs with
>   `Force: true, Mirror: true`).
> - Speaker-route pins, meta-proven: `:app:testDebugUnitTest --tests
>   '*SpeakerRouteTest'` against the engine WITHOUT the release → exit 1,
>   "5 tests completed, 2 failed", `java.lang.AssertionError at
>   SpeakerRouteTest.kt:76` and `:92` (`/tmp/webphone-speaker-red.log`).
>   After wiring the release: the same filter plus `*CallEngineTest`,
>   `*CallServiceTest`, `*RingEventRouteTest` → exit 0, 20 `PASSED`, 0
>   `FAILED` (`/tmp/webphone-speaker-green.log`).
> - The tree as it stands, measured at 00:43:22 on `db36e7b`:
>   `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
>   ktlintCheck :app:lintDebug` → exit 0, `BUILD SUCCESSFUL in 19s`;
>   `ktlintCheck` ran over the main and test source sets; the lint text
>   report
>   (`app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt`,
>   mtime 00:43:22) holds **0 errors and 14 warnings**.
> - Git facts: `git rev-list --count origin/main..HEAD` = 7; `git
>   rev-parse origin/main github/main` = both `e0787d9`; `git diff --stat
>   e0787d9..HEAD` = 16 files, +820 / −68; `@Test` annotations in
>   `app/src/test` = 156; working tree clean at 00:42.
> - The broken intermediate `CallActivity.kt` described in d2 was never
>   committed: `db36e7b` is the only commit that touched the file, it
>   holds 615 lines, its last line is `}` and `scheduleEndAutoFinish` has
>   its body.
>
> **NOT verified here:**
>
> - **The full unit suite and the whole gate did NOT run on the final
>   tree.** The 20 targeted tests ran before the last edit (the
>   `CallActivity` switch to `SpeakerRoute`). After it only compile,
>   `ktlintCheck` and lint ran. `testDebugUnitTest`, `assembleDebug`,
>   zipalign, abi-check, contrast-check and `dprint check` are unproven
>   for `bb5bf11`..`db36e7b`.
> - **No emulator, no device.** The speaker route is proven only against
>   Robolectric's `ShadowAudioManager`: both "speaker off clears the
>   request" and "the call's end releases it" are shadow facts. The
>   Telecom call-direction fix from the 23:33 report is just as unproven
>   on hardware.
> - **`tools:targetApi="tiramisu"` (the correction planned in f1) was not
>   tried.** That it clears both manifest warnings is an expectation.
> - **Forgejo's own push-mirror code was not read.** Both Codeberg raw
>   URLs tried answered 404. The `--mirror` behaviour comes from Gitea,
>   the project Forgejo forks from; that Forgejo 15.0.10 behaves the same
>   is an inference that fits the observed deletions, not a reading.
> - **Which timer fired the 02:26Z sync** (the 8h interval or
>   `sync_on_commit`) is inferred from the one-second gap between the two
>   deletions and from the PR ages; the forge's mirror log was not read.
> - Whether lint honours a `lint.xml` `severity="warning"` under
>   `warningsAsErrors = true` (the policy sketched in b2): not probed.
> - d7 (the foreground service outliving a call that ends in the
>   background) rests on one grep of the `CallService.stop` callers. It
>   was neither reproduced nor traced further.
> - Whether an existing test covers the NEW skip path in `RingEvent.post`
>   (API 33+, no `POST_NOTIFICATIONS` → skip and log): not checked. The
>   20-test run passed with it, which proves no regression, not coverage.
> - CI: unchanged since the 23:33 report (no GitHub Actions job has ever
>   executed); nothing new was checked.

## a) FULLY DONE

- **Baseline gate green on the two-bug-fix tree.** The 23:33 report left
  this open (its §f item 1). `scripts/verify-gate.sh` → exit 0 on
  `b7cda29` (+ the formatted report): 151 tests passed, every artifact
  check green. The two fixes from that report (API 31–33 ring crash,
  outbound call direction) break nothing in the suite.
- **Post-fix lint measured.** 10 → 8 errors, 34 warnings (its §f item
  2): the two real bugs were the two errors the fixes removed.
- **Six "current" verdicts confirmed from raw metadata** (its §f item
  18; the 23:33 table rested on lint's live lookup alone):

  | Artifact                           | Pinned | Newest stable | Preview exists | Metadata lastUpdated |
  | ---------------------------------- | ------ | ------------- | -------------- | -------------------- |
  | androidx.core:core-ktx             | 1.19.1 | 1.19.1        | 1.20.0-alpha01 | n/a (group index)    |
  | androidx.appcompat:appcompat       | 1.8.0  | 1.8.0         | none           | n/a (group index)    |
  | androidx.webkit:webkit             | 1.18.0 | 1.18.0        | 1.19.0-alpha02 | n/a (group index)    |
  | androidx.test:core-ktx             | 1.7.0  | 1.7.0         | 1.8.0-alpha04  | 2026-11-11           |
  | com.squareup.okhttp3:okhttp        | 5.5.1  | 5.5.1         | none           | 2026-10-22           |
  | io.getstream:stream-webrtc-android | 1.3.10 | 1.3.10        | none           | 2025-09-11           |

  All six are current. The WebRTC artifact is 14 months old.
- **Dependabot root cause found** (the 23:33 question g1, the "which
  client" half). The Forgejo → GitHub push mirror is not a
  branch-preserving copy: each sync is the equivalent of
  `git push --force --mirror`, and `--mirror` deletes remote refs that
  do not exist locally. The forge checkout has `main` only, so every
  branch Dependabot opens on GitHub is deleted at the next sync and
  GitHub closes the PR as `head_ref_deleted` under the token's user
  (LarsArtmann). Observed lifetimes: PR #8 81 minutes (2026-11-15
  19:06:29Z → 20:27:56Z), PR #9 6 h 57 min (2026-11-22 19:29:07Z →
  2026-11-23 02:26:48Z), PR #10 6 h 56 min (19:30:05Z → 02:26:49Z). #9
  and #10 died one second apart: one sync. The only Dependabot PR that
  ever merged (#7) did so 3 h 23 min after it opened. Consequence: a
  Dependabot PR on this repo lives at most one mirror interval.
- **Android Lint errors 8 → 0, warnings 34 → 14**, measured at 00:43:22
  on `db36e7b` (commits `bb5bf11`, `ef47d57`, `db36e7b`):

  | Check                          | Before                     | After                      | How                                                                                                                       |
  | ------------------------------ | -------------------------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
  | NewApi (error)                 | 6                          | 0                          | `MainActivity.onDestroy`: explicit `SDK_INT >= 33` guard; `TelecomOutgoing.onCallControlReady`: `@RequiresApi(34)`        |
  | MissingPermission (error)      | 1                          | 0                          | `RingEvent.post`: explicit `POST_NOTIFICATIONS` check (API 33+); `runCatching` kept as the belt for every other rejection |
  | GestureBackNavigation (error)  | 1                          | 0                          | `@SuppressLint` plus a WHY comment on `MainActivity.onBackPressed` (the API 31/32 back path)                              |
  | UseKtx                         | 17                         | 0                          | applied: `edit {}` ×6, `toUri()` ×10, `isVisible` ×1 (six files)                                                          |
  | RequiresFeature                | 2                          | 0                          | `@SuppressLint` plus a WHY paragraph on `WebSurface.optInToPasskeys` (the check IS there, inside `runCatching`)           |
  | BatteryLife                    | 1                          | 0                          | `@SuppressLint` plus a WHY comment on `MainActivity.maybeRequestBatteryExemption` (sideload lane)                         |
  | SetAndClearCommunicationDevice | 1                          | 0                          | real fix: new `SpeakerRoute`, next item                                                                                   |
  | UnusedAttribute                | 1                          | 1                          | NOT resolved, my edit was wrong (d1)                                                                                      |
  | ObsoleteSdkInt                 | 1                          | 2                          | one ADDED by the same wrong edit (d1); the other is `mipmap-anydpi-v26`                                                   |
  | OldTargetApi                   | 1                          | 1                          | untouched (targetSdk 36 is deliberate)                                                                                    |
  | UnusedResources                | 5                          | 5                          | investigated only (b1)                                                                                                    |
  | version drift                  | 5                          | 5                          | untouched (version batch pending)                                                                                         |
  | **Total**                      | **8 errors + 34 warnings** | **0 errors + 14 warnings** | 30 findings resolved, 1 added                                                                                             |

  One behavior note inside the permission edit: on API 33+ without
  `POST_NOTIFICATIONS` the post is now skipped with a logcat line
  ("notification <id> skipped: POST_NOTIFICATIONS not granted") instead
  of attempting `notify` and logging the rejection. The user-visible
  outcome is the same: no notification.
- **The speaker route is the call's, not the process's** (the 23:33
  §f item 9; it was a lint warning and a real defect). A
  `setCommunicationDevice` request outlives the call that made it, so
  before this change one speakerphone tap put every later call of the
  process on the loudspeaker.
  - New `call/SpeakerRoute.kt` (84 lines): `toggle` requests the
    built-in speaker or clears the request; `isActive` reads the live
    route from the platform; `release` drops only a request made here.
  - `CallEngine.teardownLocked` calls `SpeakerRoute.release()` on every
    end-of-call path (hang-up, decline, remote BYE, detach), whichever
    surface is alive.
  - `CallActivity` uses it; `toggleSpeaker()` and `speakerActive()` are
    gone (2 insertions, 28 deletions, `db36e7b`).
  - Pins: `SpeakerRouteTest`, five tests. Meta-proof red then green
    (evidence in the scope line). The red run proved the defect is real:
    with the route left as it was, a hung-up call kept the speaker.
  - **Behavior change, device-unverified:** "speaker off" now clears
    the request (the platform default routes again, so a connected
    headset wins) instead of forcing the earpiece.
- **Everything above compiles and is ktlint-clean.** `:app:compileDebugKotlin`,
  `:app:compileDebugUnitTestKotlin`, `ktlintCheck` → exit 0 on
  `db36e7b`.
- **Six of the 23:33 §f rows closed fully** by this session (1, 2, 8, 9,
  18, 20), plus the diagnosis half of 16.

## b) PARTIALLY DONE

- **Lint to zero warnings.** Works now: 0 errors, 14 warnings. Open:
  the wrong `tools:targetApi` (d1; fix is one word), 5 dead resources,
  the `mipmap-anydpi` rename, the `OldTargetApi` suppression, 5
  version-drift lines. Investigated but not applied: none of the five
  resources is referenced anywhere; `chrome_on_glass_muted` IS listed
  in `scripts/contrast-pairs.json:29`, so deleting that color means
  deleting that pair too. Blocker: the stop instruction. Effort: S.
- **Android Lint in the gates.** Nothing written. A policy was chosen
  but neither applied nor verified: `warningsAsErrors = true` once the
  tree is at zero warnings, every deliberate suppression documented at
  its site, and the version-drift checks kept non-fatal but PRINTED by
  the gate as a lag report (the Dependabot replacement). Open empirical
  question: whether a `lint.xml` `severity="warning"` survives
  `warningsAsErrors`. Blocker: the stop instruction, and question g1
  (fatal or visible-only drift). Effort: S–M.
- **Verification of the final tree.** Works now: compile, ktlint, lint,
  and 20 targeted tests (before the last edit). Open: the full
  `testDebugUnitTest` and the whole gate on `db36e7b`. Blocker: none
  technical. Effort: S (about 15 min unattended).
- **Docs for this session.** Nothing written. `CHANGELOG.md` has no
  `Fixed` entries for the three fixes; `AGENTS.md` still says "138
  tests", "viewBinding is enabled" and "Dependabot handles version
  bumps", and has none of the lint gotchas; the `TODO_LIST.md`
  warning-freeze row still claims a zero warning count;
  `docs/status/README.md` has an index row for NEITHER of today's
  reports. Blocker: the stop instruction. Effort: S–M.
- **The Dependabot decision** (the 23:33 §f item 16). The cause is
  known (a). Open: what replaces the mechanism, and the AGENTS line.
  Blocker: question g1. Effort: S once answered.
- **Coverage of the new code paths.** `SpeakerRoute` has five pins, none
  at the API 31 floor; the new skip path in `RingEvent.post` has no pin
  of its own that I know of. Effort: S.

## c) NOT STARTED

Carried from the 23:33 §f, by its numbering. Priorities unchanged.

- 3, 4, 5, 13 (CHANGELOG, AGENTS, TODO corrections, harvest, index
  rows). Priority: high.
- 6 (owner: squash the daemon blobs). Priority: high before any push.
- 7 (wire lint into `ci.yml` + `scripts/verify-gate.sh`). Priority:
  critical.
- 10 (version batch A), 11 (ktlint engine 1.8.0), 12
  (`allWarningsAsErrors`). Priority: high, medium, medium.
- 14 (device train: outbound direction), 15 (release), 17 (API-floor
  pins for the other surfaces), 32 (push). Priority: high; 15 and 32
  need the owner.
- 19 (dead resources), 21 (`viewBinding`), 22 (`mipmap-anydpi`), 23
  (`OldTargetApi` suppression). Priority: low.
- 24 (dprint), 25 (nixpkgs lock), 26 (dependency-analysis), 27
  (OSV-Scanner), 28 (architecture tests), 29 (detekt ROADMAP row), 30
  (targetSdk 37), 31 (`SECURITY.md`, docs-gate cadence). Priority: as
  ranked there.

## d) TOTALLY FUCKED UP

- **d1. My manifest edit is wrong.** To state that
  `enableOnBackInvokedCallback` needs API 33 I wrote
  `tools:targetApi="s"` on `<application>` (`AndroidManifest.xml:36`).
  `s` is API 31, the minSdk. Result: lint kept the `UnusedAttribute`
  warning AND added an `ObsoleteSdkInt` one ("Unnecessary; SDK_INT is
  always >= 31"). Severity: low, no runtime effect (`tools:` attributes
  are stripped from the APK), but the file now states the wrong fact.
  Fix: `tools:targetApi="tiramisu"` (API 33). Root cause: I never
  looked the codename up; lint's own message had named 33.
- **d2. A structural edit deleted more than intended.** My `CallActivity`
  edit removed `scheduleEndAutoFinish()`'s body and the class's closing
  brace along with the two speaker functions. Caught one tool call
  later by reading the file tail; restored; final diff reviewed
  (`db36e7b`, 2 insertions, 28 deletions). The broken state existed on
  disk for about a minute and was not committed (checked, see the scope
  line). Severity: none in the end. Root cause: the replaced text
  spanned two functions and my replacement ended on the next function's
  KDoc instead of before it.
- **d3. Twelve files changed before the first compile.** The KTX and
  lint-error edits went in batch; the first compile of them was the
  SpeakerRoute red run (by accident), the first `ktlintCheck` and lint
  only at 00:43. It happened to be clean. Had it not been, the daemon
  would have committed the breakage in up to three blobs. Severity:
  process, no damage this time.
- **d4. Three device-unvalidated behavior changes now sit on `main`,
  unreleased.** The API 31–33 ring fix (safe by construction: a guard
  around a method that did not exist), the Telecom call direction
  (23:33 report), and the speaker route (off = platform default, end =
  release). The last two change what a real phone does on paths no
  device has run since. Severity: medium until a device train.
- **d5. Still true from the 23:33 report:** `1b85f89` is red by design;
  the history is 7 daemon blobs ahead of both remotes (`e0787d9`);
  nothing is pushed; the owner's phone runs a build with none of the
  fixes.
- **d6. Dependabot was never able to work on this repo.** Not a run of
  bad luck: the push mirror deletes its branches on a timer. The AGENTS
  line "Dependabot handles version bumps" has been structurally false
  since the mirror attached (about 2026-11-11), and the only remaining
  automated drift signal is five lint warnings in a lint nobody runs.
  Severity: medium (silent staleness, already four weeks old).
- **d7. Noticed while reading, unverified: the call's foreground service
  may outlive the call.** By grep, `CallService.stop` has exactly three
  callers: `CallActivity.kt:243` (renders ENDED), `CallActivity.kt:440`
  (the hang-up button), `CallNotificationReceiver.kt:26` (the shade's
  Hang up). Nothing in `CallEngine` or `CallConnectionService` stops
  it. If a call ends while no `CallActivity` is started (the user is in
  another app), the `microphone|phoneCall` service may keep running
  until the activity returns or the process dies. Severity: unknown
  until reproduced; a lingering mic-typed foreground service is a
  battery and privacy concern, not a lost call. Root cause: unverified.
- **d8. Cosmetic:** the checkout still carries five stale
  `refs/remotes/github/dependabot/*` refs for branches the mirror
  deleted long ago.
- **d9. A slip I repeated:** `git rev-parse --short A B C` fails
  ("Needed a single revision") because `--short` swallows the next
  token. I had noted it earlier in the session and used it again. The
  working form is `git rev-parse A B C | cut -c1-7`.

**What I forgot.** (1) To compile and run `ktlintCheck` per edit batch
instead of once for twelve files. (2) To look up the `tools:targetApi`
codename (d1). (3) To add the `docs/status/README.md` index row for the
23:33 report before producing a second report. (4) To pin the new skip
path in `RingEvent.post` and to give `SpeakerRoute` an API 31 floor
test. (5) To keep the todo list current after the first update.

**What I could have done better.** (1) Bounding structural edits at
function boundaries and diffing immediately (d2). (2) Reading Forgejo's
push-mirror code instead of settling for Gitea's. (3) Not spending the
first fifteen minutes of a "whole list" session on a full gate before
any edit: the baseline was worth having, but lint could have been
resolved first and the gate run once at the end.

**What can still improve.** Everything in (e) and (f).

## e) WHAT WE SHOULD IMPROVE

- **Close the loop per batch.** Compile + `ktlintCheck` + `lintDebug`
  is about 20 s incremental. Run it after every two or three files, not
  after twelve. Impact: a daemon-swept breakage becomes impossible in
  practice.
- **Gate Android Lint and let the gate report version lags.** Lint took
  this repo from two real bugs to zero errors in one session; it is
  also the last drift signal alive (d6). Impact: closes the class
  "compiles against API 37, breaks on API 31" for good, and makes "are
  we current?" a gate output instead of a memory.
- **Settle the update mechanism on purpose.** Options as I see them:
  (a) keep Dependabot and merge within its ≤ 8 h life; (b) run
  Dependabot on the forge instead, if Forgejo supports it (unverified);
  (c) drop Dependabot and rely on the lint drift report plus a scheduled
  bump session; (d) change the mirror in SystemNix to a push that does
  not prune (unverified whether Forgejo's push mirror can be configured
  that way). Impact: ends four weeks of silent staleness.
- **An API-floor pin for every minSdk-sensitive surface**, and a pin for
  every new version guard the moment it is written. Impact: the 23:33
  crash class; this session added one guard (`RingEvent.post`) and one
  new API surface (`SpeakerRoute`) without their floor pins.
- **Read the tool's vocabulary before writing its config.** One lookup
  would have prevented d1. Impact: small each time, compounding.
- **Decide who owns audio routing on API 34+ Telecom sessions.** The
  `addCall` session has `CallControl.requestCallEndpointChange`; the
  app routes through `AudioManager`. Whether they agree on a real
  device is unverified. Impact: the speaker button and the Bluetooth
  UI could disagree.
- **Squash before the first push.** Eight-plus meaningful changes are
  hiding in `chore: auto-commit` blobs, one of them red by design.
- **One unattended command for "pin → red → fix → green".** A red tree
  that waits for the next tool call becomes a red commit.
- **Stop conditions for agents.** The session was halted mid-change-set
  by instruction; the report measured the tree instead of guessing,
  which is the right shape, but the change set should have been smaller.

## f) Up to 50 things we should get done next

Ranked by impact. Category: Bug / Feature / Quality / Cleanup /
Documentation / Process. Rows 1–11 are agent-executable now; the rest
need the owner (question g) or a device.

| #  | Task                                                                                                                                                                                                                                                                                                                                                       | Impact   | Effort | Category      |
| -- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ------ | ------------- |
| 1  | Fix d1: `tools:targetApi="tiramisu"` on `<application>`; re-run `:app:lintDebug`, expect 12 warnings                                                                                                                                                                                                                                                       | Medium   | S      | Quality       |
| 2  | Run `bash scripts/verify-gate.sh` on the final tree (`db36e7b` or later); record the verdict in this report's appendix                                                                                                                                                                                                                                     | Critical | S      | Quality       |
| 3  | Wire `:app:lintDebug` into `scripts/verify-gate.sh` and `ci.yml`: errors fatal, the version-drift lines printed as a lag report                                                                                                                                                                                                                            | Critical | S      | Quality       |
| 4  | Lint to zero warnings: delete the 5 dead resources and the `chrome_on_glass_muted` pair in `scripts/contrast-pairs.json`; `git mv mipmap-anydpi-v26 mipmap-anydpi`; suppress `OldTargetApi` with the targetSdk-36 rationale                                                                                                                                | High     | S      | Cleanup       |
| 5  | `CHANGELOG.md` `[Unreleased]`: `Fixed` = API 31–33 ring crash, outbound call direction, speaker route released at call end; `Changed` = speaker off returns to the platform default                                                                                                                                                                        | High     | S      | Documentation |
| 6  | `AGENTS.md`: lint gotchas (never gated, now zero errors; `canUseFullScreenIntent()` is API 34; `CallAttributes.Builder` takes a direction; `SpeakerRoute` owns the speaker request; the single-SDK blind spot; ktlint engine 1.5.0; detekt re-verified 2026-11-24); correct "138 tests", "viewBinding is enabled", "Dependabot handles version bumps" (d6) | High     | S      | Documentation |
| 7  | `TODO_LIST.md` / `ROADMAP.md`: correct the warning-freeze row and the Dependabot assumption, add this §f; ROADMAP: detekt re-check trigger (dated), Telecom-endpoint routing evaluation                                                                                                                                                                    | High     | S      | Documentation |
| 8  | `docs/status/README.md`: index rows for the 23:33 and the 00:42 report; HARVEST both §f lists; `docs-gates run` (cadence was already red, 18 live reports now)                                                                                                                                                                                             | High     | S      | Documentation |
| 9  | Version batch A (Gradle 9.9.2 wrapper, AGP 9.5.2, Robolectric 4.18.1, firebase-messaging 26.0.2, lifecycle 2.11.1, setup-java v6.3.0); full gate + emulator train; clears the 5 drift warnings                                                                                                                                                             | High     | M      | Quality       |
| 10 | Pins: `RingEvent.post` skip path (API 33+, no `POST_NOTIFICATIONS`); `SpeakerRoute` at `@Config(sdk = [31])`                                                                                                                                                                                                                                               | Medium   | S      | Quality       |
| 11 | Kotlin `allWarningsAsErrors = true` after the redundant cast in `WebSurfacePolicyTest.kt:345`                                                                                                                                                                                                                                                              | Medium   | S      | Quality       |
| 12 | Pin the ktlint engine explicitly (1.8.0); `ktlintFormat`; review the diff of the three newer minors                                                                                                                                                                                                                                                        | Medium   | M      | Quality       |
| 13 | Owner (g1): settle fatal-versus-visible drift and the Dependabot mechanism; write the answer into AGENTS                                                                                                                                                                                                                                                   | High     | S      | Process       |
| 14 | Owner (g2): keep or revert "speaker off = platform default"                                                                                                                                                                                                                                                                                                | High     | S      | Bug           |
| 15 | Device train: outbound call shows outgoing on BT and watch; speaker on then off with a headset; speaker call, hang up, second call must NOT start on speaker                                                                                                                                                                                               | High     | M      | Bug           |
| 16 | API 31–33 ring proof on a real Android 12 or 13 device, or an API 31–33 emulator image (the AVD is API 36 only)                                                                                                                                                                                                                                            | High     | M      | Quality       |
| 17 | Owner (g3): push both remotes and release the fixes (`bump patch` + `scripts/release.sh`) before or after the device train                                                                                                                                                                                                                                 | High     | M      | Feature       |
| 18 | Owner: squash the daemon blobs into hand-written commits before the first push                                                                                                                                                                                                                                                                             | High     | S      | Process       |
| 19 | d7: trace `CallConnectionService.kt:69` (the ENDED branch), reproduce on a device; if confirmed, stop the service from an engine-level observer                                                                                                                                                                                                            | Medium   | M      | Bug           |
| 20 | API-floor pins for Telecom inbound, `DialpadActivity`, `CallActivity`, `WebSurface`, `MainActivity` at SDK 31                                                                                                                                                                                                                                              | High     | M      | Quality       |
| 21 | `SECURITY.md`: response-time line (buildflow finding)                                                                                                                                                                                                                                                                                                      | Medium   | S      | Documentation |
| 22 | Evaluate `CallControl.requestCallEndpointChange` versus `AudioManager` routing for API 34+ sessions; ROADMAP if adopted                                                                                                                                                                                                                                    | Medium   | M      | Quality       |
| 23 | OSV-Scanner pass over a Gradle lockfile; note the prebuilt libwebrtc from 2025-09                                                                                                                                                                                                                                                                          | High     | M      | Quality       |
| 24 | dprint 0.65.0 and its plugins; one dprint version for CI and the local gate                                                                                                                                                                                                                                                                                | Medium   | M      | Quality       |
| 25 | `nix flake update` (nixpkgs locked 2026-11-07); gate                                                                                                                                                                                                                                                                                                       | Low      | S      | Cleanup       |
| 26 | Dependency-analysis plugin one-shot (`projectHealth`)                                                                                                                                                                                                                                                                                                      | Low      | S      | Quality       |
| 27 | Architecture tests evaluation (ArchUnit / Konsist) for the prose-only conventions                                                                                                                                                                                                                                                                          | Medium   | M      | Quality       |
| 28 | targetSdk 36 → 37 after an API 37 train (existing row)                                                                                                                                                                                                                                                                                                     | Medium   | M      | Feature       |
| 29 | Drop `viewBinding = true` (no `res/layout/`, zero binding imports) and its AGENTS line                                                                                                                                                                                                                                                                     | Low      | S      | Cleanup       |
| 30 | `git remote prune github` (5 stale `dependabot/*` refs, local only)                                                                                                                                                                                                                                                                                        | Low      | S      | Cleanup       |
| 31 | README "honesty contract": re-check its markers against the three fixes                                                                                                                                                                                                                                                                                    | Medium   | S      | Documentation |
| 32 | `FEATURES.md`: reflect the lint gate once wired                                                                                                                                                                                                                                                                                                            | Low      | S      | Documentation |
| 33 | One unattended command for pin → red → fix → green                                                                                                                                                                                                                                                                                                         | Low      | S      | Process       |

Rows 13, 14, 17 and 18 need the owner; 15, 16, 22 and 28 need a device
or an image this machine does not have.

## g) Questions I can not figure out myself

1. **Now that Dependabot cannot survive the push mirror, what should
   version drift do to the gate, and do you want Dependabot kept at
   all?** Fatal drift (a new upstream release turns the gate red until
   the bump is validated) keeps the repo current but can block an
   unrelated release; visible-only drift (the gate prints the lag
   lines) never blocks and can be ignored. I tried to settle it by
   looking for an existing policy: AGENTS only says "Dependabot handles
   version bumps", which d6 shows is false. The answer decides f3, f9
   and f13, and whether the mirror in SystemNix should change.
2. **Keep "speaker off returns to the platform default", or restore the
   forced earpiece?** The old code forced the earpiece, which sends the
   call to the earpiece even when a headset is connected; the new code
   behaves like the platform dialer. The repo holds no note saying the
   forcing was deliberate, but it may have been your choice for your
   phone. The revert is one line in `SpeakerRoute.toggle` plus two
   changed assertions; only you can say which behavior is the product.
3. **Ship before or after a device train?** Once the gate is green, the
   three fixes could go to the phone tonight through the usual
   `bump patch` + `scripts/release.sh`, but two of them (Telecom call
   direction, speaker routing) are shadow-proven only, and I cannot
   place a real call from here (the emulator has no SIP session, the
   phone is fingerprint-locked). I also will not push without your
   explicit word. Which risk do you prefer: the known defects staying
   on the phone, or an unvalidated routing change reaching it?
