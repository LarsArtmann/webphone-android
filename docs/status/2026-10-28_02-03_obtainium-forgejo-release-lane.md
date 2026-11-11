<!-- Status report — point-in-time snapshot. History lives in CHANGELOG.md. -->

# Status: Obtainium-via-Forgejo release lane (session 2026-10-28 02:03)

**Ask:** "How can we support Obtainium based updates via forgejo, so I do not
need to use wireless debug anymore."

**Answer delivered:** Obtainium's Forgejo source already does the job against
the plain REST API (`GET /api/v1/repos/{owner}/{repo}/releases`: tag =
available version, attached `.apk` = artifact). So the work was to make this
repo produce installable, signed, versioned releases on the forge — not to
invent an update protocol. That lane is implemented, gated and locally
proven; **no release has been published and no phone has updated through
it**, because publishing needs a Forgejo token only the owner can mint.

**Session scope:** 7 auto-commit commits (`af0084f..0bfb782`), 10 files
touched, +1314/−37 lines. No Kotlin source changed.

---

## Verification evidence (what is actually proven)

| Check                                                   | Result                                                                                                                                           |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `./gradlew assembleRelease`                             | green; emits `app-release.apk` (45,845,640 B) signed with the new key                                                                            |
| `aapt2 dump badging` (release + debug)                  | `versionCode='200' versionName='0.2.0'`                                                                                                          |
| `apksigner verify --print-certs`                        | `CN=webphone-android, O=Lars Artmann`; SHA-256 `F8:DC:B6:63:94:00:BA:48:19:CB:A9:57:CC:A1:A5:88:5E:44:81:FA:F4:91:48:6C:C4:DD:94:8C:C1:2F:B8:75` |
| `zipalign -c -P 16 4` (debug + signed release)          | both PASS                                                                                                                                        |
| `testDebugUnitTest` + `ktlintCheck`                     | green (tests were UP-TO-DATE in the last runs — unchanged Kotlin)                                                                                |
| `scripts/release.sh --dry-run` (full gate)              | green: build → sign → align → apksigner → 50/50 contrast → staged `work/release/0.2.0/webphone-android-0.2.0.apk` (44 MB) + `notes.md`           |
| `scripts/forgejo-release.py --check`                    | `Forgejo 15.0.10`; attachment `max_size` 2048 MiB, `max_files` 5; `token: MISSING`                                                               |
| `forgejo-release.py` with a bogus token                 | HTTP 401 + the exact remedy printed (error-path UX proven)                                                                                       |
| `scripts/release.sh bump minor` (isolated scratch repo) | `0.2.0 → 0.3.0`; `bump patch → 0.3.1`                                                                                                            |
| `scripts/release.sh --doctor`                           | preflight + forge probe + "token missing" warning, exit 0                                                                                        |
| `scripts/release.sh --help`                             | prints the documented header                                                                                                                     |
| `dprint check`                                          | green (3 files formatted)                                                                                                                        |
| **Upload path (`POST …/releases/{id}/assets`)**         | **NEVER EXECUTED** — see §d                                                                                                                      |

---

## a) FULLY DONE

| #  | Item                                                                                                                                                                                                                                                                                                                                 | Where                                                      |
| -- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------- |
| 1  | Version single-source: `version.txt` → `versionName`, and `versionCode = major*10000 + minor*100 + patch` (0.2.0 → 200). Loud `check()` failures for malformed/overflowing versions.                                                                                                                                                 | `app/build.gradle.kts:8-31`, `version.txt`                 |
| 2  | Release signing wired from `~/.config/webphone-android/release.properties` (keystore path + credentials), with a **loud error** on half-configuration and a silent no-op (unsigned) when absent, so CI/fresh checkouts keep building.                                                                                                | `app/build.gradle.kts:33-56`, `:75-99`                     |
| 3  | Dedicated RSA-4096 release key created (10 000 d validity), keystore + props `chmod 600`, fingerprint printed in assetlinks-ready form.                                                                                                                                                                                              | `scripts/release.sh:85-139`; `~/.config/webphone-android/` |
| 4  | `scripts/release.sh`: `--init-key`, `bump patch\|minor\|major`, `--doctor`, `--dry-run`, and the full release path. Preflight refuses: wrong branch, dirty tree (except dry-run/doctor), concurrent gradle build, missing changelog section, existing tag (local or on the forge), local `main` behind `origin/main`, missing token. | `scripts/release.sh`                                       |
| 5  | The gate is the repo's own: `assembleRelease` + `testDebugUnitTest` + `ktlintCheck`, then `zipalign -c -P 16 4` on the **signed** artifact, `apksigner verify`, `scripts/contrast-check.py`.                                                                                                                                         | `scripts/release.sh:236-263`                               |
| 6  | Release notes are derived from `CHANGELOG.md` (the `## [X.Y.Z]` section), plus version, versionCode and the signing fingerprint appended — no hand-written notes drift.                                                                                                                                                              | `scripts/release.sh:266-301`                               |
| 7  | `scripts/forgejo-release.py` (stdlib only — the devShell has no `curl`/`tea`): `--check`, idempotent release reuse, asset replacement, streaming multipart upload, uploaded-vs-local size verification, and 401/403/404/409/413/422 hints mapped to actions.                                                                         | `scripts/forgejo-release.py`                               |
| 8  | `publish()` tags `v<version>` (annotated), pushes `main` + tag to `origin`, then publishes. SSH banner noise suppressed (`core.sshCommand=ssh -o LogLevel=ERROR`).                                                                                                                                                                   | `scripts/release.sh:303-322`                               |
| 9  | README rewritten: "Build and verify" now reflects the real toolchain (platform 37, build-tools 36, ktlint, signing reality) and a new "Distribution and updates" section with the Obtainium setup + "Cut a release".                                                                                                                 | `README.md:47-125`                                         |
| 10 | AGENTS memory: status header marked the lane implemented-but-device-unproven; release-lane command block; 6 new gotchas (update = tag+asset, version.txt authority, key location, debug-vs-release signature, Obtainium's insecure-TLS toggle, LAN/VPN reachability, probeable forge limits).                                        | `AGENTS.md:12-27`, `:83-101`, `:310-345`                   |
| 11 | CHANGELOG cut: `[Unreleased]` emptied, the accumulated features moved under `## [0.2.0] - 2026-10-28`, the lane documented there (it ships in the same release).                                                                                                                                                                     | `CHANGELOG.md:11-25`                                       |
| 12 | FEATURES: two new rows (release signing = `FULLY_FUNCTIONAL`; Obtainium lane = `PARTIALLY_FUNCTIONAL`, honest about the unproven device half).                                                                                                                                                                                       | `FEATURES.md:104-106`                                      |
| 13 | TODO_LIST: the stale "cut the `v0.1.0` tag" row replaced by "cut the first release (`v0.2.0`)" + a phone-side Obtainium row.                                                                                                                                                                                                         | `TODO_LIST.md:60-61`                                       |
| 14 | ROADMAP §3 rewritten: update distribution is settled (signed releases → Obtainium), with the public-endpoint and GitHub-mirror options recorded as raw ideas.                                                                                                                                                                        | `ROADMAP.md:38-56`                                         |
| 15 | Deliberate restraint: `.github/workflows/ci.yml` was NOT touched (the sync PAT cannot push workflow files — touching it would have re-frozen the mirror).                                                                                                                                                                            | (unchanged)                                                |

## b) PARTIALLY DONE

| # | Item                                                                                                                        | What is missing                                                                                                                                                                                                                             |
| - | --------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | **Publishing**                                                                                                              | Everything up to (not including) the release creation + asset upload. No `v0.2.0` tag, no release, no asset on the forge.                                                                                                                   |
| 2 | **Upload code path** (`upload_asset`, multipart framing, `Content-Length`, the `attachment` field name, idempotent replace) | Zero real execution. Only the pre-flight GET path is proven (401 case). A local `http.server` mock was possible and skipped.                                                                                                                |
| 3 | Version → artifact consistency                                                                                              | Proven for the current tree; **not automated**. Nothing stops a future tag from disagreeing with `version.txt` except preflight.                                                                                                            |
| 4 | Post-publish version discipline                                                                                             | Documented ("after a publish run `bump patch`") but not automated — the tree currently claims `0.2.0`, exactly the version about to be published.                                                                                           |
| 5 | App-Link signing story (R18)                                                                                                | The release fingerprint is printed by `--init-key` and `scripts/assetlinks.sh <keystore>` accepts it, but I never generated the release-key `assetlinks.json`, and TODO_LIST R18 still frames the fingerprint as "the install signing key". |
| 6 | CI coverage of the new lane                                                                                                 | CI builds debug only: it never runs `assembleRelease`, never validates `version.txt`/CHANGELOG pairing, never syntax-checks the two new scripts.                                                                                            |
| 7 | `testDebugUnitTest` in the release gate                                                                                     | Passed, but as UP-TO-DATE (no Kotlin touched); a real end-to-end gate run has not re-executed the 94 tests.                                                                                                                                 |

## c) NOT STARTED

| # | Item                                                                                                      | Why it matters                                                           |
| - | --------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| 1 | The phone side: Obtainium install, `read:repository` token, "Allow insecure HTTP requests", first install | This is the half the user actually asked for ("no more wireless debug"). |
| 2 | A publicly-reachable update endpoint (mobile-data updates)                                                | Today updates only work on the home LAN or the NetBird VPN.              |
| 3 | GitHub mirror parity for releases/tags                                                                    | The mirror has no releases; Obtainium has no fallback source.            |
| 4 | GitHub Actions billing fix (owner) — CI still cannot start jobs on the mirror                             | Releases are unrelated, but the guard rails are still dark.              |
| 5 | `assetlinks.json` served from the stack with the RELEASE fingerprint                                      | App Links verification (R18) and no-choice dialer/https intake.          |
| 6 | Signing-key backup verification (a human/offline action, but nothing tracks it)                           | Losing the key = every device must uninstall.                            |

## d) TOTALLY FUCKED UP

| # | Item                                                                 | Severity | Detail                                                                                                                                                                                                                                                                                                                                      |
| - | -------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | **`scripts/emulator-train.sh --release` is now BROKEN by my change** | High     | It hardcodes `app/build/outputs/apk/release/app-release-unsigned.apk` (`:43`) and dies if absent (`:44-47`). With signing configured AGP emits **only** `app-release.apk` — so the documented release smoke path now fails at its first check. It should just install `app-release.apk` (or sign the unsigned one only when no key exists). |
| 2 | **Stale gotcha left in AGENTS.md**                                   | Med      | `AGENTS.md:219-222` still says "**No release signing config**: … Adding a signing config is a deliberate future step". It is now half-true at best and directly contradicts `:326-336`. I added new bullets without retiring the old one — exactly the "replace silently deletes/keeps memory" failure mode.                                |
| 3 | **Upload path shipped unexercised**                                  | High     | I shipped ~90 lines of multipart HTTP with a hand-computed `Content-Length` and a streamed body, tested only against a 401. If the field name, boundary framing or length is wrong, the first real publish fails — on the one run that matters. A 30-line `http.server` mock in `/tmp` would have proven it without a token.                |
| 4 | **Two script bugs of my own making, caught only by manual testing**  | Med      | `bump` never set `MODE`, so `scripts/release.sh bump patch` fell through into the **full release** branch (stopped only by the changelog gate — luck, not design). `read_version` used `printf '%s'` (no newline), so `IFS=. read` hit EOF and `set -e` killed `bump` silently. Both are proof that the script had no test at all.          |
| 5 | **`set -euo pipefail` traps I wrote and then fixed the hard way**    | Low      | `tr </dev/urandom \| head -c 32` (SIGPIPE 141), `pgrep … && die` (exit-on-no-match), `[ … ] && arr=(…)` (exit 1 when false), `awk … { exit }` (SIGPIPE). Each was a silent abort until traced.                                                                                                                                              |

## e) WHAT WE SHOULD IMPROVE

**Answering the three questions directly:**

**What did I forget?**

1. The emulator train (`--release`) — I changed the artifact layout and never
   re-read the one script that consumes it.
2. Retiring the now-wrong AGENTS gotcha while adding the new ones.
3. Actually exercising the upload code (mock server, or a scratch forge repo).
4. Proving the Obtainium **version-comparison** assumption (`v0.2.0` tag vs
   `0.2.0` installed). I could not read Obtainium's `standardizeVersion`
   (Sourcegraph returned nothing) and shipped on convention. If it does not
   strip the `v`, the phone sees a permanently "new" version — the first
   symptom would be an update loop, and the fix is a one-line extraction
   regex in Obtainium or a tag-scheme change.
5. `version.txt` left pointing at the version being released (post-publish
   bump is manual).
6. No `scripts/assetlinks.sh` run for the release key — a 30-second artifact
   that R18 needs anyway.
7. Nothing in CI guards the new files (`bash -n`, `py_compile`, version ↔
   changelog pairing).
8. Cleaning `work/release/0.2.0/` (44 MB of staged copy) after the dry runs.

**What could I have done better?**

1. **Test the lane before polishing the prose.** The README/AGENTS work is
   ~40% of the diff; the untested 90-line HTTP client is the part that can
   actually lose the user's time.
2. **Write the mock test first**: an `http.server` in `/tmp` that accepts
   `POST /api/v1/repos/x/y/releases`, returns a plausible JSON, then asserts
   the multipart body parses and the size matches. That single test would
   have de-risked the whole publish step.
3. **Never mix "make the artifact signed" with "keep the old artifact path
   assumptions alive"** — I should have grepped every consumer of
   `app-release-unsigned.apk` in the same edit.
4. **Use a scratch repo for a real end-to-end publish.** Forgejo has
   push-to-create over SSH; a throwaway repo + a token would have proven the
   upload for real. Blocked only by token creation (UI/API with credentials).
5. **Configuration-cache hygiene**: reading env + file existence at
   configuration time (`app/build.gradle.kts:39-56`) is exactly the pattern
   that can produce a stale signed/unsigned decision. Either use
   `providers.*` inputs or document `--no-configuration-cache` for signing
   troubleshooting. I did neither.
6. **Make the script testable** from the start (`release.sh` is one 390-line
   file with `set -euo pipefail` and no seam) — that is why two of its bugs
   needed tracing instead of a test.

**What could I still improve?**

1. Give `release.sh` a `--post-publish` behaviour: auto-bump `version.txt`,
   auto-cut the CHANGELOG heading, print the exact commit command.
2. Add a `scripts/release-selftest.sh` (or a Makefile-free flake check) that
   runs `bash -n`, `py_compile`, a mock-server upload test, and the
   version/changelog pairing check.
3. Make the update path work on mobile data (public endpoint) instead of
   LAN/VPN only.
4. Show the installed version inside the app (Settings), so a sideloaded
   phone can answer "what am I running?" without adb.
5. Consider certificate trust properly: the "Allow insecure HTTP requests"
   toggle is a workaround for an internal CA, not a design. A publicly-trusted
   forge vhost would let Obtainium verify TLS and would also let the app's
   WebView reach the forge without special cases.

---

## f) Up to 50 things we should get done next

**P0 — unblock the ask (owner + agent, ~30 min total)**

1. Create the Forgejo token (`write:repository`) and export it / write `~/.config/webphone-android/forgejo-token` (chmod 600).
2. Run `nix develop -c bash scripts/release.sh` to publish `v0.2.0`.
3. **Verify the asset on the forge**: `python3 scripts/forgejo-release.py --check` must list `v0.2.0: webphone-android-0.2.0.apk`.
4. Create the phone-side token (`read:repository`) and add the app in Obtainium (Override Source → Forgejo (Codeberg)).
5. Enable the per-app "Allow insecure HTTP requests" toggle; confirm the release list loads.
6. Uninstall the current debug-signed build, install 0.2.0 from Obtainium, grant permissions, log in, verify REGISTERED.
7. **Fix `scripts/emulator-train.sh --release`** to use `app-release.apk` (and only sign the unsigned artifact when it actually exists).
8. **Retire the stale AGENTS gotcha** (`:219-222`) and fold its still-true half into the new signing bullet.
9. Back up `release.keystore` + `release.properties`; record where (and that losing them forces uninstalls).
10. Confirm Obtainium's version comparison with the `v` tag (if it misbehaves: extraction regex `^v?(.*)$`).

**P1 — harden what shipped**

11. Mock-server test for `scripts/forgejo-release.py` (multipart framing, size check, idempotent replace).
12. Add the mock test + `bash -n` + `py_compile` + version↔CHANGELOG pairing to CI (or a flake check).
13. Decide the configuration-cache story for signing inputs (`providers.*` vs documented `--no-configuration-cache`).
14. Run `scripts/assetlinks.sh ~/.config/webphone-android/release.keystore` and paste the output into TODO_LIST R18 / the webphone repo.
15. Update R18's wording from "debug/install signing key" to "release signing key".
16. Add a post-publish auto-bump (or a `--after-publish` helper) so the tree never claims a published version.
17. Add an optional auto-changelog cut (`[Unreleased]` → `[X.Y.Z] - date`) behind a flag.
18. Expose `--prerelease` through `release.sh` (the python client already supports it).
19. Push the release APK through the release smoke on the emulator (post-fix #7) to prove R8 output installs while release-signed.
20. Re-run `testDebugUnitTest` without the build cache (`--rerun-tasks`) once, for a real green.
21. Add a `--doctor` check that compares `version.txt` against the last built APK's versionCode (catches a stale build dir).
22. Delete or document `work/release/*` staging (44 MB per version; add a `--clean` or a README note).
23. Document the release-key rotation story (what happens when the key must change).
24. Record the exact Obtainium screens/fields in README with a screenshot (reproducible setup).

**P2 — make updates work everywhere**

25. Build a publicly-trusted endpoint for releases (LE cert vhost, or publish the APK to the `pbx.artmann.tech` static root) so Obtainium works on mobile data.
26. Decide whether that endpoint needs auth (APK is not secret; the repo is).
27. Mirror releases/tags to GitHub so the mirror is a usable fallback source (needs the PAT/billing fixes).
28. Fix the invalid `gh` token (owner) — `gh auth login`.
29. Regenerate the sync PAT with `repo`+`workflow` (owner) so CI and future workflow edits stop being a manual push.
30. Fix GitHub Actions billing (owner) so CI actually runs.
31. Revisit `autoVerify` App Links once #14 lands (R18).
32. Consider a monochrome/GitHub-release-free single source of truth statement in README (avoid two divergent update stories).

**P3 — polish and honesty**

33. Show the installed version (and signing fingerprint?) in the app's Settings.
34. Add a "check for updates" affordance in the app that deep-links to Obtainium (no silent self-update; single-call product decision stays).
35. Add a CHANGELOG entry for the emulator-train fix (#7) when it lands.
36. Extend the release gate with `dprint check` (only skipped because it needs `npx` network).
37. Add a flake check for the release key's presence in `--doctor` only (never fail a plain build).
38. Document the attachment `allowed_types` risk + the exact SystemNix knob (`attachment.MAX_SIZE`, and `.apk` in `ALLOWED_TYPES` if ever enforced).
39. Add a `scripts/release.sh --notes` example to the README (custom notes path).
40. Record in AGENTS that the forge is Forgejo 15.0.10 and that `--check` output is the fastest health probe.
~~41. Add a status-report/README link so the next session finds this report.~~ done — docs/status/README.md live index links this report
42. Consider making `version.txt` the tag source of truth in CI (`git describe --tags` sanity check).
43. Evaluate whether `bump` should also open a CHANGELOG stub section automatically.
44. Reconsider `versionCode` layout if `patch` ever reaches 100 (currently a hard error — good, but document the escalation).
45. Add a second signature check: verify the APK's signer equals `release.properties`' expected cert (keystore vs artifact, the AGENTS lesson).
46. Keep `work/dryrun*.log` out of the repo tree or delete after a run.
47. Add `shellcheck` to the devShell so `release.sh` gets a real linter (not just `bash -n`).
48. Add a `docs/` note on how Obtainium resolves versions (tag vs title) next to the setup steps.
49. Reconcile the two "status" narratives: README's "v0.1 scaffold" header vs the 0.2.0 version now in `version.txt`.
50. Sweep the remaining `app-release-unsigned.apk` references (`README.md:61`, `AGENTS.md:220`, `AGENTS.md:335`) so every mention states the conditional correctly.

---

## g) Three questions I cannot answer myself

1. **Update channel scope:** is Obtainium on the LAN/NetBird VPN (with the
   per-app "Allow insecure HTTP requests" toggle) acceptable as the permanent
   story, or should I build a publicly-trusted release endpoint (LE-cert vhost,
   or publishing the APK to the `pbx.artmann.tech` root) so updates also work
   on mobile data?
2. **Release automation:** do you want `scripts/release.sh` to auto-bump
   `version.txt` and auto-cut the CHANGELOG heading after a successful publish
   (fewer gates, less ritual), or keep both as manual steps that a human
   confirms (current behaviour, harder to get wrong silently)?
3. **Mirror parity:** should the GitHub mirror carry the same tags/releases
   (a second Obtainium source, and better off-site durability), or stay a
   code-only mirror until the PAT/billing issues are fixed?

---

_Report written 2026-10-28 02:03 CET. Awaiting instructions._
