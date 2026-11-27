# Status Report — 2026-11-27 16:59 CET — The whole list driven: docs-gates green for the first time, buildflow green, gate RED at one style step

**Tree state at report time: RED at gate step 3 (ktlint), three style findings in test code I wrote this session.** Steps 1 and 2 are green (assembleDebug, 187/187 tests). The fix is one command (`nix develop -c ./gradlew :app:ktlintFormat`, then the gate again); I did not run it because the instruction was to report and wait. `origin/main` equals `HEAD` (`fb1ea81`), so that red state is on the forge.

## a) FULLY DONE

- **d7, the call service outliving the call.** `CallService` now collects `CallEngine.state` (started after `startForeground`, skipping the state present at start) and retires on the transition into ENDED. `ACTION_STOP`, `CallService.stop()` and its three callers are gone; the screen and the notification receiver only call `CallEngine.hangUp()`. The four RED pins from the 05:56 report are green (`CallServiceTest` 4, `CallActivityTest` 2).
- **`CallEngine.hangUp()` on an idle or ended line is a no-op.** It used to re-publish ENDED as locally ended, overwriting a remote hang-up's verdict. Pin `hangUp on an already ended call keeps the remote party's verdict`, meta-proven red (`/tmp/webphone-hangup-red.log`) then green.
- **viewBinding dropped** (`app/build.gradle.kts`); no layout used it.
- **Release variant rebuilt** after the mipmap rename: `app-release.apk` 45.9 MB, `application-icon` = `res/E4.xml` (R8 renames it; `unzip -l mipmap` finding nothing is not the merge glitch), `zipalign -c -P 16 4` OK, ABI gate green, badging 203 / 0.2.3.
- **Whole gate green once** on the tree before the last additions (`/tmp/webphone-gate-final2.log`: all 8 steps, `GATE GREEN`, 164/164).
- **Fresh-clone proof (positive):** `git clone --no-hardlinks` to /tmp, the wrapper jar is tracked (SHA-256 `621008af…`, equals Gradle's published checksum), `./gradlew --version` prints Gradle 9.9.2.
- **Emulator train, both modes:** debug APK (boot 29 s, install Success, launch, screenshot) and `--release` with the real release-keyed `app-release.apk` (boot 33 s, install Success, launch, `webphone-release-smoke.png`). The release mode was BROKEN before (`scripts/emulator-train.sh` hard-coded `app-release-unsigned.apk`); it now boots `app-release.apk` when present and falls back to align + debug-sign when not. CHANGELOG entry added.
- **Stale claims fixed on sight:** AGENTS "No release signing config" gotcha (false since 2026-11-11) rewritten to "Release signing is opt-in by file presence"; README's "first run prompts for it" (false: there is a shipped default) rewritten; README build block lists lint and the gate.
- **Report corrections (carried from 05:56):** 19 live reports (not 20), "roughly 50 blank lines across 11 files", the accurate closed-rows list, 29 marked NOT done at the time, "What I forgot" items 5 and 6. Index rows for the 05:56 and 00:42 reports added to `docs/status/README.md`.
- **`scripts/meta-prove.sh` (row 33):** break the fix, watch the pin go RED, restore, watch it go GREEN; restore runs through a trap on every exit, the tree is fingerprinted before and after, a VACUOUS or RED-on-restored verdict exits non-zero. shellcheck clean after shfmt.
- **`TestHermeticityGuardTest`:** a dependency-free source guard; any test file reaching a wired entry point (`MainActivity` launch, `RingEvent.`, `ensureWired`) must set `wiringEnabled = false`, and any file running `onSessionDead` must set the `deregisterPushDevice` seam. Meta-proven with the new script (RED on broken, GREEN on restored). First rule draft was over-broad (it flagged the ntfy tests, which use a MockWebServer and explicit URLs); tightened.
- **API 31 floor pins** without duplicating bodies: `DialpadActivityTest`, `MainActivityLaunchTest`, `WebSurfaceTest`, `RingEventRouteTest` run with `@Config(sdk = [31, 34])` (+22 cases). One pin is API 33+ by design (`POST_NOTIFICATIONS`) and is pinned to `[34]`.
- **dprint plugins bumped:** json 0.26.0, markdown 0.29.0, dockerfile 0.8.0 (pretty_yaml stays 0.6.0). The CLI stays 0.64.3 on purpose: that is what nixpkgs carries (nixos-unstable 2026-11-21) and what CI pins, so gate and CI are one version; 0.65.0 produces identical output on this tree (checked).
- **`nix flake update`:** nixpkgs 2026-11-07 → 2026-11-21, flake-utils and nix-systems moved. `nix flake check --no-build` passes on the updated devShell.
- **OSV-Scanner:** 106 resolved runtime packages (from an uncommitted Gradle lockfile generated through an init script), no issues.
- **`git remote prune github`:** three stale `dependabot/*` remote branches pruned.
- **Docs:** CHANGELOG `[Unreleased]` (Added: lint in the gate + lag report, warnings-as-errors; Fixed: 7 entries; Changed: 4 entries), AGENTS.md (164→187 tests, eight-step gate, lint block, toolchain versions, four new gotchas, Dependabot line replaced), FEATURES.md (toolchain row, 187-test row, two new rows), TODO_LIST.md (harvest below), ROADMAP.md (three decisions, two open questions, two raw ideas), README.
- **docs-gates: ALL TEN GREEN for the first time** (`docs-gates: PASS`, `/tmp/webphone-docsgates5.log`). The archive ritual closed a debt the 05:56 report listed as already red:
  - The seven overdue late-October reports (30 days old, 290 bare rows) are annotated and archived to `docs/status/archived/` with the `> **Live remainder:**` banner; manifest section added to `archived/README.md`; the seven rows moved out of the live index.
  - Every unrouted item was harvested first (see TODO rows below), then routed by cluster; nothing was struck without evidence.
  - Cadence: 19 → 12 unarchived reports (threshold 15). Audit-all: one straggler (`00-38` item 42, the warning-freeze step) struck with `1ced5f2` + `allWarningsAsErrors`.
- **buildflow: green** (`✓ BuildFlow passed`, EXIT 0, `/tmp/webphone-buildflow-fix4.log`) after fixing what it found: shfmt on `scripts/meta-prove.sh`, ruff-format on `abi-check.py` / `lint-lag-report.py` / `contrast-check.py`, three mypy errors (`contrast-check.py:33` Optional handling, `forgejo-release.py:149` untyped `parts` list), two bandit findings (suppressed at both sites with the reason: the repo's own resource XML), securitymd's response-time wording. New `.buildflow.yml` skips two steps with rationale (below).
- **TODO_LIST harvest for the archive:** Ready rows "Release-lane hardening", "Adopt the process rules", "Durable gate evidence", "M024 shared test dispatcher", "TODO↔plan lint script"; High rows "Owner: back up the release key", "Owner: phone DNS over NetBird"; Medium row "Cross-repo (SystemNix): phone-DNS/tunnel follow-ups". ROADMAP: "Release automation", "Task vocabulary", "CHANGELOG for CI-only bumps" questions; the "Release-mode UX and endpoint" raw idea; decisions "drift visible not fatal" and "Konsist rejected".

## b) PARTIALLY DONE

- **The verify gate on the final tree.** Steps 1-2 green; step 3 RED: `RingEventRouteTest.kt:3` and `WebSurfaceTest.kt:3` import ordering (the `Config` import I inserted landed in the wrong slot) and `TestHermeticityGuardTest.kt:25` function-signature layout. Steps 4-8 did not run in that pass. An earlier full pass was green. **This is the first thing to do.**
- **Emulator trains** prove install + launch only. Nothing on a device. The d7 fix is pinned on the JVM and not proven on an emulator (needs a live call).
- **dependency-analysis:** blocked upstream. 3.20.4 (latest, 2026-11-16) fails to apply under AGP 9.5.2 (`AndroidComponentsExtension` not found; AGP 9.5 renamed it). One-shot init script, nothing committed; TODO row with the trigger.
- **Konsist:** evaluated and rejected (latest is `1.0.0-beta.1`; the one wanted rule ships without it).
- **`CallControl.requestCallEndpointChange`:** evaluated, not taken (the `addCall` endpoint callbacks are no-ops; the speaker is driven through AudioManager; not verifiable on the JVM). ROADMAP raw idea with a device trigger.
- **AGENTS.md is 916 lines** (buildflow's bar: 220). I added about 45. The AGENTS slim-down is an existing TODO row (M082), not addressed.

## c) NOT STARTED

- Owner-only, carried: squash or keep history; device train for the 2026-11-25 batch and for d7; API 37 image train; targetSdk 37; release; the speaker-default and drift-policy confirmations; backing up the release key.
- API-floor pins at SDK 31 for `TelecomOutgoing` attributes (`TelecomOutgoingAttributesTest` still default SDK only) and for `SettingsActivity` / `CallActivity` / `CallService` screens.
- The TODO rows harvested today (release-lane hardening, process rules into AGENTS, durable gate marker, M024, TODO↔plan lint).

## d) TOTALLY FUCKED UP (own mistakes, all repaired)

1. **I shipped test code that failed the style gate, twice.** The d7 fix failed ktlint on import order (fixed by `ktlintFormat`); the LAST additions failed it again on the same rule plus one layout rule, and that is the red tree in §b. I ran the targeted test classes but not `ktlintCheck` after the last three edits.
2. **I reported a green gate and then made the tree red without re-running the gate.** The 164/164 green run was real, but I kept editing afterwards.
3. **A `status-items` spec routed four verdicts onto the wrong lines** (the 10-29 report): the emitted keys are `N@word` and I iterated over whitespace-split words, then did not read the `--verify` output of that file (I read only its last line). Found by reading where the markers landed; repaired by hand.
4. **A normalization regex over-matched.** `glob('2026-10-2*.md')` hit 18 archived files, not the seven I meant, and inserted "routed:" into about 380 verdict lines in 15 settled archives. Reverted those 15 files from HEAD before anything committed them (verified with `git status`).
5. **I let buildflow's auto-fix break the flake twice.** deadnix deletes `self` from the `outputs` pattern; nix then refuses the flake (no `...`). I restored `flake.nix` from HEAD twice before adding the skip. `nix flake check --no-build` passes on the restored file.
6. **An annotator's `git show HEAD:… > file` wiped a sibling's in-progress annotations** (the 10-29 file, mid-run). The sibling re-applied them. My prompt allowed that restore; it should not have.
7. **The auto-commit daemon captured intermediate states:** `7879ec6` holds a test file with a seam line deliberately removed (a meta-probe break), `9f5bc79` / `2de2677` / `9057a59` hold mangled intermediate versions of two reports. The working tree is correct; history is not clean. All of it is now on `origin/main`.
8. **A Python heredoc crashed five times on lookups** (dprint had re-padded the table I was parsing) and each crash left nothing written, so I re-ran from scratch each time instead of reading the row shape once first.

## e) WHAT WE SHOULD IMPROVE

1. **The gate must be the LAST action, after the last edit.** Two red trees in one session came from editing after a green run.
2. **Run `ktlintFormat` (or `ktlintCheck`) after every Kotlin edit**, not only the test classes: the import-order rule fires on any inserted import.
3. **Do not use `status-items` on tables** (it strikes routed rows and emits a `| ~~ | N |` shape dprint then mangles); use `rows`. For prose, read the `--verify` output of EVERY file.
4. **Pass explicit file lists to a regex rewrite, never a glob**, and print the match count per file before writing.
5. **Do not let an annotator restore another agent's file from HEAD.** Say it in the prompt.
6. **A meta-probe that breaks a tracked file can be swept by the daemon mid-probe.** The script's restore should run inside one command with the test run, or the probe should work on a scratch copy.
7. **`buildflow --fix` is not safe unsupervised here** (deadnix). Now skipped with a rationale; review every auto-fix diff before accepting it.
8. **The status-report template could carry a "gate state at report time" first line** always; this report has it and it is the most useful line in it.

## f) Up to 50 things we should get done next

**P0 — the red tree**

1. `nix develop -c ./gradlew :app:ktlintFormat`, re-run `bash scripts/verify-gate.sh` to GREEN on the final tree (three findings, all mine).
2. Re-run `docs-gates run` after the report lands (this file must pass routing/freshness).

**P1 — owner (cannot self-answer)**

3. Owner: confirm you pushed `origin/main` to `fb1ea81` (it equals HEAD at report time, 0 ahead); decide whether the intermediate commits (§d 7) get squashed or stay.
4. Owner: back up `~/.config/webphone-android/release.keystore` + `release.properties` and prove the backup restores.
5. Owner: phone DNS over NetBird (what "DNS problems" means; CA install; setup key vs login expiry) — TODO High row.
6. Owner: decide the speaker default, the drift policy and targetSdk 37 (ROADMAP Open questions).
7. Owner: AGENTS.md is 916 lines against a 220 bar; decide where the Gotchas move (question 2 below).

**P2 — verification the JVM cannot give**

8. Prove d7 on the emulator with a live call: leave the in-call screen, remote hang-up, `dumpsys activity services` shows no `CallService`.
9. Device train for the 2026-11-25 batch (API 31-33 ring path, outbound direction, speaker route release, missed-call note).
10. API 37 emulator image train.

**P3 — code and tooling**

11. Release-lane hardening (TODO row): mock-forge publish test, CI `bash -n` + `py_compile` + version↔tag pairing, `--post-publish`, `--prerelease`, `--doctor` versionCode check, key-rotation doc, signer check, shellcheck in the gate, Obtainium version note.
12. Adopt the process rules into AGENTS.md (TODO row; eight rules from the 10-28/29 reports).
13. Durable gate evidence marker (`last gate: GREEN at <sha>`).
14. M024 shared test OkHttp dispatcher.
15. TODO↔plan cross-check lint script.
16. Bump AGP 9.5.2 → 9.5.3 (the lag report names it; a patch, run the gate).
17. API-floor pins at SDK 31 for `TelecomOutgoingAttributesTest`, `CallActivityTest`, `CallServiceTest`, `SettingsActivityTest`.
18. Make `scripts/meta-prove.sh` immune to the daemon sweep (§e 6).
19. dependency-analysis retry when it loads under AGP 9.5.
20. Move the gate's dprint to 0.65.0 the day nixpkgs carries it (with CI).
21. AGENTS slim-down (M082), if the owner answers question 2.
22. Cross-repo SystemNix follow-ups for the phone DNS (TODO Medium row; draft in `docs/drafts/` first).

## g) QUESTIONS FOR THE OPERATOR (cannot self-answer)

1. **History:** `origin/main` now equals HEAD (`fb1ea81`), so the daemon's intermediate commits, including two deliberately-broken probe states, are on the forge. Did you push that, and do you want it squashed before anything builds on it, or kept as is?
2. **AGENTS.md:** it is 916 lines against buildflow's 220-line bar and I was told to record every gotcha there. May I split the Gotchas into `docs/reference/*.md` files with AGENTS keeping a short index, or must AGENTS stay the single file?
3. **Version drift:** the gate prints drift and stays green (my decision, in ROADMAP). Do you want AGP-class drift to become fatal on a cadence (for example fatal whenever a patch release exists for a week), or keep it visible-only?

---

_Git facts at report time:_ HEAD = `origin/main` = `fb1ea81`, `github/main` = `e0787d9`, 0 ahead of origin, 19 uncommitted paths (docs sweep, scripts, `.buildflow.yml`). `@Test` annotations 165; JUnit cases 187 (multi-SDK classes). Gate: steps 1-2 green, step 3 red, 4-8 not reached in the last pass.
