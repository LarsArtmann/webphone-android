# Status Report — 2026-11-28 01:27 CET — Whole list, second round: gate hardening, release lane, deploy and CI rehearsal shipped

**Tree state at report time:** `HEAD` = `3b101e5` (daemon commits plus two
hand commits by a SECOND agent session that works in this checkout at the
same time). My last whole-gate proof is `78a90da`, `GATE GREEN`, clean tree.
A gate run on `3b101e5` passed steps 1-7 (244 JUnit cases) and failed step 8
(dprint: an unformatted `TODO_LIST.md` table). The fix is `nix run nixpkgs#dprint -- fmt`;
it is applied right after this report is written and the gate is re-run
(read `.gate-last-green` for the verdict).

Scope: this session's run only (2026-11-27 ~17:00 to 2026-11-28 01:27),
continuing the 16:59 report.

## a) FULLY DONE

Gate and build:

- **ktlint findings fixed**, whole gate green on the then-final tree
  (`/tmp/webphone-gate-final4.log`, 187 cases).
- **AGP 9.5.2 to 9.5.3**, build and tests green on it.
- **SDK-floor pins everywhere:** every Robolectric class runs with
  `@Config(sdk = [31, 34])` (eleven more classes; 187 to 230 cases on my
  tree). The floor run found one real fact: `CallAttributes.Builder` throws
  on API 31, so `TelecomOutgoingAttributesTest` is pinned to `[34]` (31-33
  dial directly; correct by contract, now documented in its KDoc).
- **Gate step 2 runs `scripts/test-count-check.py`:** every `@Test` in
  source must appear as an executed JUnit case; the XML is the only count
  anyone may quote. Negative-proven (one report hidden: four names
  listed, exit 1). Also in CI.
- **Gate step 9 (new):** `bash -n`, `py_compile`, shellcheck over
  `scripts/*.sh`, the mock-forge test and the evidence lint.
- **`.gate-last-green` marker** written by every green run: commit, date,
  number of uncommitted paths on top of it.
- **`scripts/evidence-lint.py`:** every report time, `docs/status/…` path
  and plan id a TODO row cites must exist in the status and planning trees
  (live or archived). 59 rows, 0 broken. It found real rot on its first run
  (six "no such report" cites that were plans, not reports; the lint now
  covers both trees).
- **`scripts/test-forgejo-release.py`:** the release client against an
  in-process mock forge, 11 assertions (create, body, prerelease flag,
  asset bytes equal, re-publish reuses and replaces, wrong token 401,
  missing asset, no token). Gate and CI.
- **CI workflow:** `tags: ["v*"]` trigger and a step that refuses a tag
  that disagrees with `version.txt`; test-count, mock-forge and script-lint
  steps added.
- **`scripts/ci-rehearsal.sh` (M035):** every CI step in a clean clone with
  no signing properties. GREEN (`/tmp/wp-ci-rehearsal.log`): the release
  APK built UNSIGNED, both zipalign checks, both ABI gates, lint 0/0.

Release lane (`scripts/release.sh`, each piece exercised):

- Preflight refuses a versionCode that does not exceed the newest local or
  forge `v*` tag (harness: 203 > 202 ok, a stale 0.2.2 refused).
- Gate compares the APK's signer with the keystore named in the properties
  (exercised on the built release APK: equal, 64 hex chars).
- `--prerelease` (end-to-end through the mock) and `--post-publish`
  (runs `bump patch` after a publish). `--doctor` runs clean up to the
  expected "no `## [0.2.3]` section" stop.
- `docs/KEY_ROTATION.md`: keep, rotate, check-which-key.

Scripts and harness:

- **`scripts/meta-prove.sh` works on a throwaway copy** (rsync to mktemp),
  so the daemon can no longer commit a deliberately broken line. Proven:
  the `CallEngine.hangUp` pin RED on broken, GREEN on restored, main tree
  untouched. The fingerprint also caught my own wrong indentation in a
  first attempt, as designed.
- **`scripts/device-deploy.sh`:** signer cross-check against the INSTALLED
  build (stops on mismatch, never uninstalls), gate, install, dumpsys
  version verify, wake + launch + pidof, crash scan, screenshot. Exercised
  on the emulator: fresh install, update, mismatch stop.
- **Emulator scripts:** every adb call time-bounded
  (`ADB_TIMEOUT`, default 120 s), AVD creation bounded, every run tees
  into `app/build/reports/emulator/*.log`, a signature mismatch uninstalls
  and retries once (found by a real failed smoke run over the release-keyed
  install).
- **Train pixel evidence:** `work/verify/train-pill.png` (the "Not
  connected" pill) and `train-overlay.png` ("Can't reach the phone
  system" + Retry, cold load under airplane mode), both looked at.
- **`scripts/pin-devshells.sh`:** GC roots `result-devshell` (178 store
  paths) and `result-emulator` (221), gitignored.
- **`.githooks/pre-push`** (opt-in: `git config core.hooksPath .githooks`).
- **`forkEvery = 1` verdict: free.** 170 s with a JVM per class, 181 s in
  one JVM (230 cases each). Kept.

Emulator (API 36 AVD, live Pocket ID):

- **M050 done:** warm shell, deep-link `begin`, logcat `auth detour
  blessed via redirect: auth.larsartmann.cloud`, the consent page renders
  in-shell, BACK, logcat `auth detour cleared (back on origin:
  https://pbx.artmann.tech/login)`. Two harness facts recorded in AGENTS:
  `uiautomator dump` does not expose WebView nodes (Cancel is at about
  300,1500 on 1080x2400); Pocket ID's Cancel opens the IdP's passkey page,
  only BACK reaches the origin.
- Train green in debug mode with the hardened script.

Docs:

- AGENTS.md: process rules from the 10-28/29 reports, eleven new gotchas,
  nine-step gate, toolchain line, the dprint caveat corrected.
- README: the repo is PUBLIC (the step still said private), how Obtainium
  resolves a version.
- CHANGELOG `[Unreleased]`: twelve new entries.
- TODO_LIST: 17 rows removed as done, one marked BLOCKED UPSTREAM with
  evidence (API 37: the pinned nixpkgs `androidenv/repo.json` has no
  `system-images/android-37` entry).
- docs-gates: all ten PASS. buildflow: EXIT 0.

## b) PARTIALLY DONE

- **Gate on the final tree.** Green at `78a90da`. The later tree carries the
  second session's work; a run on `3b101e5` failed only dprint (see the
  header). The re-run after `dprint fmt` is the last action of this session.
- **Release lane:** `--post-publish` is not exercised end-to-end (it would
  publish). No end-to-end release was cut (0.2.3 is unpublished by policy).
- **Emulator scripts:** the `--release` train and `emulator-verify.sh` were
  not re-run after the timeout changes.
- **API-floor pins:** on SDK 31 only the classes exist twice; whether every
  31-specific shadow behaves like a real API 31 device is unproven (no
  device).
- **AGENTS.md grew again** (about 60 lines this round; the 220-line bar and
  the M082 slim-down row stand). The split is an owner question.

## c) NOT STARTED

- d7 on the emulator with a live call; device train for the 2026-11-25
  batch; `ring-diagnose.sh` on the Edge 60; the phone notification
  inventory (all need the live stack, the phone, or both).
- webphone#21 acceptance check (webphone repo).
- Owner-only: key backup, squash decision, device trains, targetSdk 37,
  release.
- `.buildflow.yml` comment says "no Python test suite"; there is now a
  non-pytest test script. Not reworded.

## d) TOTALLY FUCKED UP (own mistakes, all repaired)

1. **A typo (`""`) in `device-deploy.sh`** produced a broken quote; found by
   shellcheck before any run.
2. **The fresh-install path of `device-deploy.sh` died silently** (`pm
   path` exits non-zero for an unknown package under `set -e`). Found on
   the emulator, fixed with `|| true`.
3. **The first meta-prove in copy mode reported RESTORE INCOMPLETE:** my
   harness restored eight spaces where the line has twelve. The script was
   right; my command was wrong. Re-run correct.
4. **My meta-probe rewrite used `rm -rf` on the temp copy** in the first
   draft, against the house rule. Replaced with `trash` before it ran.
5. **The evidence lint's first draft flagged seven rows as "no evidence"**
   (code cites and cross-repo cites) and used a dict comprehension with a
   `None` value as a list substitute. Rewritten to check only broken cites.
6. **The mock-forge handler had the wrong signature** (bound method passed
   where a handler class is needed). Rewritten as a closure class.
7. **A `re.sub` templating crash** (`\s` in a replacement string) aborted
   one edit script halfway; redone with plain string replacement.
8. **I edited a running script's sibling while a train was in flight** (I
   waited for the train before touching `emulator-train.sh` itself, but
   not for the others). No harm, but it was luck of ordering.
9. **The gate label said `N/8` after I added a ninth step.** Fixed after
   the green run, which is itself a breach of "gate last" (see e1).

## e) WHAT WE SHOULD IMPROVE

1. **Gate last, and I mean last.** I again changed a file (the step labels)
   after a green run. The marker makes this visible now; the rule stands.
2. **Two agent sessions in one checkout is the real risk.** The daemon
   commits whatever is on disk, so one session's half-written file lands on
   top of the other's green tree (the red `SettingsActivityTest` I saw at
   23:00 was theirs, the unformatted TODO table at 01:27 may be either).
   Either one session per checkout, or worktrees per session.
3. **dprint should run in a pre-commit-shaped place.** Both sessions can
   leave an unformatted table; the gate catches it only at the end.
4. **Doc counts rot by design.** AGENTS now says "165 `@Test`, 230 cases"
   and the tree already has 175 and 244. Quote only `.gate-last-green`
   and `scripts/test-count-check.py`; remove counts from AGENTS.
5. **The API-floor pins doubled the suite for one real finding.** Worth it,
   but the next doubling should be a decision, not a default.
6. **Harness coordinates from screenshots** are estimates; the M050 note
   gives one. A reusable "tap by screenshot fraction" helper would help.

## f) NEXT (ranked)

1. Run `dprint fmt` + the whole gate on the merged tree; read
   `.gate-last-green`.
2. One session per checkout, or give the second session a worktree.
3. Re-run `emulator-train.sh --release` and `emulator-verify.sh` after the
   timeout changes.
4. Remove hard-coded test counts from AGENTS.md and FEATURES.md.
5. Squash decision for the daemon-blob history (owner).
6. Split AGENTS.md Gotchas into `docs/reference/*.md` (owner nod; M082).
7. Reword the `.buildflow.yml` "no Python test suite" comment.
8. Exercise `--post-publish` against the mock forge (a `--fake-publish`
   mode in `release.sh`).
9. d7 proof on the emulator with a live call.
10. Device train for the 2026-11-25 batch.
11. `ring-diagnose.sh` on the Edge 60.
12. Phone notification inventory (four lock-screen icons).
13. Cut and publish 0.2.3 (owner).
14. Back up the release key and prove a restore (owner).
15. webphone#21 acceptance check in the webphone repo.
16. Serve `assetlinks.json` on both hosts (cross-repo draft exists).
17. Re-check the nixpkgs androidenv index for API 37 after the next
    `nix flake update`.
18. targetSdk 37 after a device train.
19. dependency-analysis retry under AGP 9.5.3.
20. mockwebserver3 migration.
21. Configuration-cache `problems=fail` when the ktlint plugin clears.
22. Annotate and archive `2026-11-11_12-57` and `14-56` once their §f
    chains are represented.
23. Evidence-path prefix sweep in the archived reports.
24. Loopback live-check against a real deployment (the other session's
    script; run it once on this side).
25. A screenshot-fraction tap helper for emulator scripts.
26. `docs-gates` upstream `status-items` fold guard.
27. Make the emulator-verify pass part of the train (one entry point).
28. A pin for `scripts/verify-gate.sh` itself (a failing step must fail
    the gate; one negative run per step).
29. GitHub Actions billing (owner) and one real CI log.
30. Push `main` to both remotes and check `scripts/mirror-lag.sh`.
31. Obtainium on the phone (owner).
32. Reconcile the "v0.1 scaffold" status line with `version.txt` 0.2.3.
33. `META-INF`/proguard keep-rule review after the AGP bump (release
    smoke).
34. ntfy read-auth decision (owner).
35. The eight ROADMAP open questions (owner).
36. Prerelease lane dry run: publish a `0.2.3-rc` as prerelease to prove
    Obtainium ignores it (needs a version scheme decision).
37. Add `scripts/ci-rehearsal.sh` to AGENTS "Build and verify" block.
38. Key-rotation doc linked from the release script's `--init-key`
    output.
39. A `--check` mode for the pre-push hook that only prints whether the
    marker matches HEAD.
40. Emulator train on a second ABI (arm64 image) once one is in nixpkgs.

## g) QUESTIONS I CANNOT ANSWER MYSELF

1. **Two sessions, one checkout.** The second Crush session committed
   `097d58a` and `3b101e5` (line picker, handover-pack) while I worked.
   Is that intended? Should each session get its own worktree, and which
   of us owns `TODO_LIST.md` and `AGENTS.md`?
2. **History.** The daemon folded this round's work into about 30
   `chore: auto-commit` blobs; `origin/main` was at `fb1ea81` at 16:59
   and nobody pushed since. Squash the unpushed range before the next
   push, or keep it?
3. **AGENTS.md** is now past 970 lines against buildflow's 220 bar. May I
   move the Gotchas into `docs/reference/*.md` with a short index in
   AGENTS, or is the single file wanted on purpose?
