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

<!-- check-rows:ledger -->

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

| #      | Item                                                                                                                                                                                                                                                                                                                                                                        | Where                                                          |
| ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| ~~1~~  | ~~Version single-source: `version.txt` → `versionName`, and `versionCode = major*10000 + minor*100 + patch` (0.2.0 → 200). Loud `check()` failures for malformed/overflowing versions.~~ done at `601ce57`                                                                                                                                                                  | ~~`app/build.gradle.kts:8-31`, `version.txt`~~                 |
| ~~2~~  | ~~Release signing wired from `~/.config/webphone-android/release.properties` (keystore path + credentials), with a **loud error** on half-configuration and a silent no-op (unsigned) when absent, so CI/fresh checkouts keep building.~~ done at `601ce57`                                                                                                                 | ~~`app/build.gradle.kts:33-56`, `:75-99`~~                     |
| ~~3~~  | ~~Dedicated RSA-4096 release key created (10 000 d validity), keystore + props `chmod 600`, fingerprint printed in assetlinks-ready form.~~ done at `b5fc373`                                                                                                                                                                                                               | ~~`scripts/release.sh:85-139`; `~/.config/webphone-android/`~~ |
| ~~4~~  | ~~``scripts/release.sh``: ``--init-key``, ``bump patch\~~ done at ``b5fc373`                                                                                                                                                                                                                                                                                                | ~~minor\~~                                                     | ~~major``, ``--doctor``, ``--dry-run``, and the full release path. Preflight refuses: wrong branch, dirty tree (except dry-run/doctor), concurrent gradle build, missing changelog section, existing tag (local or on the forge), local ``main``behind``origin/main`, missing token.~~ | ~~`scripts/release.sh`~~ |
| ~~5~~  | ~~The gate is the repo's own: `assembleRelease` + `testDebugUnitTest` + `ktlintCheck`, then `zipalign -c -P 16 4` on the **signed** artifact, `apksigner verify`, `scripts/contrast-check.py`.~~ done at `b5fc373`                                                                                                                                                          | ~~`scripts/release.sh:236-263`~~                               |
| ~~6~~  | ~~Release notes are derived from `CHANGELOG.md` (the `## [X.Y.Z]` section), plus version, versionCode and the signing fingerprint appended — no hand-written notes drift.~~ done at `b5fc373`                                                                                                                                                                               | ~~`scripts/release.sh:266-301`~~                               |
| ~~7~~  | ~~`scripts/forgejo-release.py` (stdlib only — the devShell has no `curl`/`tea`): `--check`, idempotent release reuse, asset replacement, streaming multipart upload, uploaded-vs-local size verification, and 401/403/404/409/413/422 hints mapped to actions.~~ done at `b5fc373`                                                                                          | ~~`scripts/forgejo-release.py`~~                               |
| ~~8~~  | ~~`publish()` tags `v<version>` (annotated), pushes `main` + tag to `origin`, then publishes. SSH banner noise suppressed (`core.sshCommand=ssh -o LogLevel=ERROR`).~~ done at `b5fc373`                                                                                                                                                                                    | ~~`scripts/release.sh:303-322`~~                               |
| ~~9~~  | ~~README rewritten: "Build and verify" now reflects the real toolchain (platform 37, build-tools 36, ktlint, signing reality) and a new "Distribution and updates" section with the Obtainium setup + "Cut a release".~~ done — README.md 'Distribution and updates' section (added d6b6e9e)                                                                                | ~~`README.md:47-125`~~                                         |
| ~~10~~ | ~~AGENTS memory: status header marked the lane implemented-but-device-unproven; release-lane command block; 6 new gotchas (update = tag+asset, version.txt authority, key location, debug-vs-release signature, Obtainium's insecure-TLS toggle, LAN/VPN reachability, probeable forge limits).~~ done — AGENTS.md:150-166 release-lane block + :325-335 release-key gotcha | ~~`AGENTS.md:12-27`, `:83-101`, `:310-345`~~                   |
| ~~11~~ | ~~CHANGELOG cut: `[Unreleased]` emptied, the accumulated features moved under `## [0.2.0] - 2026-10-28`, the lane documented there (it ships in the same release).~~ done — CHANGELOG.md:220 `## [0.2.0] - 2026-10-28` section                                                                                                                                              | ~~`CHANGELOG.md:11-25`~~                                       |
| ~~12~~ | ~~FEATURES: two new rows (release signing = `FULLY_FUNCTIONAL`; Obtainium lane = `PARTIALLY_FUNCTIONAL`, honest about the unproven device half).~~ done — FEATURES.md:109-110 (release signing + Obtainium lane rows)                                                                                                                                                       | ~~`FEATURES.md:104-106`~~                                      |
| ~~13~~ | ~~TODO_LIST: the stale "cut the `v0.1.0` tag" row replaced by "cut the first release (`v0.2.0`)" + a phone-side Obtainium row.~~ done — TODO_LIST.md:70 'Connect the phone to Obtainium' (the row's current successor)                                                                                                                                                      | ~~`TODO_LIST.md:60-61`~~                                       |
| ~~14~~ | ~~ROADMAP §3 rewritten: update distribution is settled (signed releases → Obtainium), with the public-endpoint and GitHub-mirror options recorded as raw ideas.~~ done — ROADMAP.md:49-52 (§3 'Settled 2026-10-28' paragraph)                                                                                                                                               | ~~`ROADMAP.md:38-56`~~                                         |
| ~~15~~ | ~~Deliberate restraint: `.github/workflows/ci.yml` was NOT touched (the sync PAT cannot push workflow files — touching it would have re-frozen the mirror).~~ done — deliberate non-change — .github/workflows/ci.yml:84-87 still builds the release unsigned, no signing secrets in CI                                                                                     | ~~(unchanged)~~                                                |

## b) PARTIALLY DONE

| #     | Item                                                                                                                                                                                                                          | What is missing                                                                                                                                                                                                                             |
| ----- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ~~1~~ | ~~**Publishing**~~ done — FEATURES.md:110 — v0.2.1 PUBLISHED 2026-11-11 with its APK; tags v0.2.1 + v0.2.2 exist                                                                                                              | ~~Everything up to (not including) the release creation + asset upload. No `v0.2.0` tag, no release, no asset on the forge.~~                                                                                                               |
| ~~2~~ | ~~**Upload code path** (`upload_asset`, multipart framing, `Content-Length`, the `attachment` field name, idempotent replace)~~ done — FEATURES.md:110 — the asset upload ran for real on v0.2.1 and was verified anonymously | ~~Zero real execution. Only the pre-flight GET path is proven (401 case). A local `http.server` mock was possible and skipped.~~                                                                                                            |
| 3     | Version → artifact consistency **→ TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-4**                                                                                                        | Proven for the current tree; **not automated**. Nothing stops a future tag from disagreeing with `version.txt` except preflight.                                                                                                            |
| 4     | Post-publish version discipline **→ TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-4** Post-publish version discipline                                                                       | Documented ("after a publish run `bump patch`") but not automated — the tree currently claims `0.2.0`, exactly the version about to be published.                                                                                           | Documented ("after a publish run `bump patch`") but not automated — the tree currently claims `0.2.0`, exactly the version about to be published. |
| 5     | App-Link signing story (R18) **→ TODO_LIST.md "Cross-repo: serve /.well-known/assetlinks.json from the stack" (R18)**                                                                                                         | The release fingerprint is printed by `--init-key` and `scripts/assetlinks.sh <keystore>` accepts it, but I never generated the release-key `assetlinks.json`, and TODO_LIST R18 still frames the fingerprint as "the install signing key". |
| 6     | CI coverage of the new lane **→ TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-4**                                                                                                           | CI builds debug only: it never runs `assembleRelease`, never validates `version.txt`/CHANGELOG pairing, never syntax-checks the two new scripts.                                                                                            |
| ~~7~~ | ~~`testDebugUnitTest` in the release gate~~ done — TODO_LIST.md:18 — the suite has re-executed since (97-test gate) on a changed Kotlin tree                                                                                  | ~~Passed, but as UP-TO-DATE (no Kotlin touched); a real end-to-end gate run has not re-executed the 94 tests.~~                                                                                                                             |

## c) NOT STARTED

| # | Item                                                                                                                                                                                  | Why it matters                                                           |
| - | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| 1 | The phone side: Obtainium install, `read:repository` token, "Allow insecure HTTP requests", first install **→ TODO_LIST.md "Connect the phone to Obtainium" (BLOCKED, owner device)** | This is the half the user actually asked for ("no more wireless debug"). |
| 2 | A publicly-reachable update endpoint (mobile-data updates) **→ ROADMAP.md §3 Distribution raw idea "A public update endpoint"**                                                       | Today updates only work on the home LAN or the NetBird VPN.              |
| 3 | GitHub mirror parity for releases/tags **→ ROADMAP.md §3 Distribution raw idea "GitHub releases as a second mirror"**                                                                 | The mirror has no releases; Obtainium has no fallback source.            |
| 4 | GitHub Actions billing fix (owner) — CI still cannot start jobs on the mirror **→ TODO_LIST.md "Owner: fix GitHub Actions billing"**                                                  | Releases are unrelated, but the guard rails are still dark.              |
| 5 | `assetlinks.json` served from the stack with the RELEASE fingerprint **→ TODO_LIST.md "Cross-repo: serve /.well-known/assetlinks.json from the stack" (R18)**                         | App Links verification (R18) and no-choice dialer/https intake.          |
| 6 | Signing-key backup verification (a human/offline action, but nothing tracks it) **→ TODO_LIST.md "Owner: back up `~/.config/webphone-android/release.keystor**                        | Losing the key = every device must uninstall.                            |

## d) TOTALLY FUCKED UP

| #     | Item                                                                                                                            | Severity | Detail                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| ----- | ------------------------------------------------------------------------------------------------------------------------------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ~~1~~ | ~~**`scripts/emulator-train.sh --release` is now BROKEN by my change**~~                                                        | ~~High~~ | ~~It hardcodes `app/build/outputs/apk/release/app-release-unsigned.apk` (`:43`) and dies if absent (`:44-47`). With signing configured AGP emits **only** `app-release.apk` — so the documented release smoke path now fails at its first check. It should just install `app-release.apk` (or sign the unsigned one only when no key exists).~~ done — scripts/emulator-train.sh:42-70 — --release now boots app-release.apk when present, falls back to the unsigned + debug-signed path; CHANGELOG entry owed (routed to TODO_LIST.md Release-lane hardening r |
| ~~2~~ | ~~**Stale gotcha left in AGENTS.md**~~                                                                                          | ~~Med~~  | ~~`AGENTS.md:219-222` still says "**No release signing config**: … Adding a signing config is a deliberate future step". It is now half-true at best and directly contradicts `:326-336`. I added new bullets without retiring the old one — exactly the "replace silently deletes/keeps memory" failure mode.~~ done — AGENTS.md:319-323 — now says "Release signing is opt-in by file presence"; the "No release signing config" text is gone (rg in AGENTS.md: 0 hi                                                                                           |
| ~~3~~ | ~~**Upload path shipped unexercised**~~ done — FEATURES.md:110 — the upload ran for real on v0.2.1 (2026-11-11), asset verified | ~~High~~ | ~~I shipped ~90 lines of multipart HTTP with a hand-computed `Content-Length` and a streamed body, tested only against a 401. If the field name, boundary framing or length is wrong, the first real publish fails — on the one run that matters. A 30-line `http.server` mock in `/tmp` would have proven it without a token.~~                                                                                                                                                                                                                                 |
| ~~4~~ | ~~**Two script bugs of my own making, caught only by manual testing**~~ done at `b5fc373`                                       | ~~Med~~  | ~~`bump` never set `MODE`, so `scripts/release.sh bump patch` fell through into the **full release** branch (stopped only by the changelog gate — luck, not design). `read_version` used `printf '%s'` (no newline), so `IFS=. read` hit EOF and `set -e` killed `bump` silently. Both are proof that the script had no test at all.~~                                                                                                                                                                                                                           |
| ~~5~~ | ~~**`set -euo pipefail` traps I wrote and then fixed the hard way**~~ done at `b5fc373`                                         | ~~Low~~  | ~~`tr </dev/urandom \~~                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | ~~head -c 32``(SIGPIPE 141),``pgrep … && die``(exit-on-no-match),``[ … ] && arr=(…)``(exit 1 when false),``awk … { exit }` (SIGPIPE). Each was a silent abort until traced.~~ |

## e) WHAT WE SHOULD IMPROVE

**Answering the three questions directly:**

**What did I forget?**

~~1. The emulator train (`--release`) — I changed the artifact layout and never~~ v: scripts/emulator-train.sh:42-70 — --release boots app-release.apk when present, unsigned + debug-signed path as fallback; CHANGELOG entry owed
re-read the one script that consumes it.
~~2. Retiring the now-wrong AGENTS gotcha while adding the new ones.~~ v: AGENTS.md:319-323 — "Release signing is opt-in by file presence"; the old "No release signing config" gotcha is gone
~~3. Actually exercising the upload code (mock server, or a scratch forge repo).~~ done — the upload ran for real on v0.2.1 (FEATURES.md:110)
3. Proving the Obtainium **version-comparison** assumption (`v0.2.0` tag vs
`0.2.0` installed). I could not read Obtainium's `standardizeVersion`
(Sourcegraph returned nothing) and shipped on convention. If it does not
strip the `v`, the phone sees a permanently "new" version — the first
symptom would be an update loop, and the fix is a one-line extraction
regex in Obtainium or a tag-scheme change. **→ TODO_LIST.md "Connect the phone to Obtainium" (BLOCKED, owner device)**
4. `version.txt` left pointing at the version being released (post-publish
bump is manual).
~~5. No `scripts/assetlinks.sh` run for the release key — a 30-second artifact~~ r: TODO_LIST.md "Cross-repo: serve /.well-known/assetlinks.json from the stack" (R18)
that R18 needs anyway. **→ TODO_LIST.md "Cross-repo: serve /.well-known/assetlinks.json from the stack" (R18)**
~~6. Nothing in CI guards the new files (`bash -n`, `py_compile`, version ↔~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
changelog pairing).
~~7. Cleaning `work/release/0.2.0/` (44 MB of staged copy) after the dry runs.~~ v: `ls work/release` lists only 0.2.1 and 0.2.2 — work/release/0.2.0 is gone

**What could I have done better?**

~~1. **Test the lane before polishing the prose.** The README/AGENTS work is~~ r: TODO_LIST.md "Adopt the process rules the 10-28/29 reports named into `AGENTS.md`"
~40% of the diff; the untested 90-line HTTP client is the part that can
actually lose the user's time.
~~2. **Write the mock test first**: an `http.server` in `/tmp` that accepts~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
`POST /api/v1/repos/x/y/releases`, returns a plausible JSON, then asserts
the multipart body parses and the size matches. That single test would
have de-risked the whole publish step.
~~3. **Never mix "make the artifact signed" with "keep the old artifact path~~ r: TODO_LIST.md "Adopt the process rules the 10-28/29 reports named into `AGENTS.md`"
assumptions alive"** — I should have grepped every consumer of
`app-release-unsigned.apk` in the same edit.
~~4. **Use a scratch repo for a real end-to-end publish.** Forgejo has~~ done — a real publish happened instead: v0.2.1, 2026-11-11 (FEATURES.md:110)
push-to-create over SSH; a throwaway repo + a token would have proven the
upload for real. Blocked only by token creation (UI/API with credentials).
~~4. **Configuration-cache hygiene**: reading env + file existence at~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
configuration time (`app/build.gradle.kts:39-56`) is exactly the pattern
that can produce a stale signed/unsigned decision. Either use
`providers.*` inputs or document `--no-configuration-cache` for signing
troubleshooting. I did neither.
~~5. **Make the script testable** from the start (`release.sh` is one 390-line~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
file with `set -euo pipefail` and no seam) — that is why two of its bugs
needed tracing instead of a test.

**What could I still improve?**

~~1. Give `release.sh` a `--post-publish` behaviour: auto-bump `version.txt`,~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
auto-cut the CHANGELOG heading, print the exact commit command.
~~2. Add a `scripts/release-selftest.sh` (or a Makefile-free flake check) that~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
runs `bash -n`, `py_compile`, a mock-server upload test, and the
version/changelog pairing check.
~~3. Make the update path work on mobile data (public endpoint) instead of~~ r: ROADMAP.md theme 3 raw idea "Release-mode UX and endpoint"
LAN/VPN only.
~~4. Show the installed version inside the app (Settings), so a sideloaded~~ r: ROADMAP.md theme 3 raw idea "Release-mode UX and endpoint"
phone can answer "what am I running?" without adb.
~~5. Consider certificate trust properly: the "Allow insecure HTTP requests"~~ r: ROADMAP.md theme 3 raw idea "Release-mode UX and endpoint"
toggle is a workaround for an internal CA, not a design. A publicly-trusted
forge vhost would let Obtainium verify TLS and would also let the app's
WebView reach the forge without special cases.

---

## f) Up to 50 things we should get done next

**P0 — unblock the ask (owner + agent, ~30 min total)**

1. ~~Create the Forgejo token (`write:repository`) and export it / write `~/.config/webphone-android/forgejo-token` (chmod 600).~~ done — FEATURES.md:110 — v0.2.1 PUBLISHED 2026-11-11, so a write token exists
2. ~~Run `nix develop -c bash scripts/release.sh` to publish `v0.2.0`.~~ **Superseded — v0.2.1 and v0.2.2 were published instead (FEATURES.md:110, git tags v0.2.1/v0.2.2); no v0.2.0 tag was ever cut.**
3. ~~**Verify the asset on the forge**: `python3 scripts/forgejo-release.py --check` must list `v0.2.0: webphone-android-0.2.0.apk`.~~ done — FEATURES.md:110 — v0.2.1 with webphone-android-0.2.1.apk verified anonymously
4. Create the phone-side token (`read:repository`) and add the app in Obtainium (Override Source → Forgejo (Codeberg)). **→ TODO_LIST.md "Connect the phone to Obtainium" (BLOCKED, owner device)**
5. Enable the per-app "Allow insecure HTTP requests" toggle; confirm the release list loads. **→ TODO_LIST.md "Connect the phone to Obtainium" (BLOCKED, owner device)**
6. Uninstall the current debug-signed build, install 0.2.0 from Obtainium, grant permissions, log in, verify REGISTERED. **→ TODO_LIST.md "Connect the phone to Obtainium" (BLOCKED, owner device)**
   ~~7. **Fix `scripts/emulator-train.sh --release`** to use `app-release.apk` (and only sign the unsigned artifact when it actually exists).~~ v: scripts/emulator-train.sh:42-70 — fixed exactly as asked; the CHANGELOG entry (#35) is owed and routed to the TODO_LIST.md Release-lane hardening row
   ~~8. **Retire the stale AGENTS gotcha** (`:219-222`) and fold its still-true half into the new signing bullet.~~ v: AGENTS.md:319-323 — retired; now "Release signing is opt-in by file presence" with the still-true half folded in
   ~~9. Back up `release.keystore` + `release.properties`; record where (and that losing them forces uninstalls).~~ r: TODO_LIST.md "Owner: back up `~/.config/webphone-android/release.keystore`"
7. Confirm Obtainium's version comparison with the `v` tag (if it misbehaves: extraction regex `^v?(.*)$`). **→ TODO_LIST.md "Connect the phone to Obtainium" (BLOCKED, owner device)**

**P1 — harden what shipped**

~~11. Mock-server test for `scripts/forgejo-release.py` (multipart framing, size check, idempotent replace).~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
~~12. Add the mock test + `bash -n` + `py_compile` + version↔CHANGELOG pairing to CI (or a flake check).~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
~~13. Decide the configuration-cache story for signing inputs (`providers.*` vs documented `--no-configuration-cache`).~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
14. Run `scripts/assetlinks.sh ~/.config/webphone-android/release.keystore` and paste the output into TODO_LIST R18 / the webphone repo. **→ TODO_LIST.md "Cross-repo: serve /.well-known/assetlinks.json from the stack" (R18)**
15. Update R18's wording from "debug/install signing key" to "release signing key". **→ TODO_LIST.md "Cross-repo: serve /.well-known/assetlinks.json from the stack" (R18)**
~~16. Add a post-publish auto-bump (or a `--after-publish` helper) so the tree never claims a published version.~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
~~17. Add an optional auto-changelog cut (`[Unreleased]` → `[X.Y.Z] - date`) behind a flag.~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
~~18. Expose `--prerelease` through `release.sh` (the python client already supports it).~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
~~19. Push the release APK through the release smoke on the emulator (post-fix #7) to prove R8 output installs while release-signed.~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
20. ~~Re-run `testDebugUnitTest` without the build cache (`--rerun-tasks`) once, for a real green.~~ done — TODO_LIST.md:18 — the suite has since re-executed (97-test gate) on changed Kotlin
~~21. Add a `--doctor` check that compares `version.txt` against the last built APK's versionCode (catches a stale build dir).~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
~~22. Delete or document `work/release/*` staging (44 MB per version; add a `--clean` or a README note).~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
~~23. Document the release-key rotation story (what happens when the key must change).~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
~~24. Record the exact Obtainium screens/fields in README with a screenshot (reproducible setup).~~ r: ROADMAP.md theme 3 raw idea "Release-mode UX and endpoint"

**P2 — make updates work everywhere**

25. Build a publicly-trusted endpoint for releases (LE cert vhost, or publish the APK to the `pbx.artmann.tech` static root) so Obtainium works on mobile data. **→ ROADMAP.md §3 Distribution raw idea "A public update endpoint"**
    ~~26. Decide whether that endpoint needs auth (APK is not secret; the repo is).~~ r: ROADMAP.md theme 3 raw idea "Release-mode UX and endpoint"
26. Mirror releases/tags to GitHub so the mirror is a usable fallback source (needs the PAT/billing fixes). **→ ROADMAP.md §3 Distribution raw idea "GitHub releases as a second mirror"**
    ~~28. Fix the invalid `gh` token (owner) — `gh auth login`.~~ r: ROADMAP.md §3 Distribution raw idea "GitHub releases as a second mirror" (owner: `gh auth login`; that row records the invalid token)
27. Regenerate the sync PAT with `repo`+`workflow` (owner) so CI and future workflow edits stop being a manual push. **→ TODO_LIST.md "Owner: regenerate the sync PAT with repo+workflow scope"**
28. Fix GitHub Actions billing (owner) so CI actually runs. **→ TODO_LIST.md "Owner: fix GitHub Actions billing"**
29. Revisit `autoVerify` App Links once #14 lands (R18). **→ TODO_LIST.md "Cross-repo: serve /.well-known/assetlinks.json from the stack" (R18)**
    ~~32. Consider a monochrome/GitHub-release-free single source of truth statement in README (avoid two divergent update stories).~~ v: README.md:124-126 "`version.txt` is the single source of the version" + AGENTS.md:452 "version.txt is the version; versionCode is derived" — the single source-of-truth statement exists

**P3 — polish and honesty**

~~33. Show the installed version (and signing fingerprint?) in the app's Settings.~~ r: ROADMAP.md theme 3 raw idea "Release-mode UX and endpoint"
~~34. Add a "check for updates" affordance in the app that deep-links to Obtainium (no silent self-update; single-call product decision stays).~~ r: ROADMAP.md theme 3 raw idea "Release-mode UX and endpoint"
~~35. Add a CHANGELOG entry for the emulator-train fix (#7) when it lands.~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)" (CHANGELOG entry owed — add to [Unreleased] Fixed)
36. ~~Extend the release gate with `dprint check` (only skipped because it needs `npx` network).~~ done — .github/workflows/ci.yml:107 (Formatting gate (dprint) step)
~~37. Add a flake check for the release key's presence in `--doctor` only (never fail a plain build).~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
38. ~~Document the attachment `allowed_types` risk + the exact SystemNix knob (`attachment.MAX_SIZE`, and `.apk` in `ALLOWED_TYPES` if ever enforced).~~ done — AGENTS.md:486-493 (Forgejo facts that bite the upload: MAX_SIZE knob + allowed_types risk)
~~39. Add a `scripts/release.sh --notes` example to the README (custom notes path).~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
~~40. Record in AGENTS that the forge is Forgejo 15.0.10 and that `--check` output is the fastest health probe.~~ done — AGENTS.md:486-487 (forge version 15.0.10 + the --check probe recorded)
~~41. Add a status-report/README link so the next session finds this report.~~ done — docs/status/README.md live index links this report
~~40. Consider making `version.txt` the tag source of truth in CI (`git describe --tags` sanity check).~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
~~41. Evaluate whether `bump` should also open a CHANGELOG stub section automatically.~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
42. ~~Reconsider `versionCode` layout if `patch` ever reaches 100 (currently a hard error — good, but document the escalation).~~ done — app/build.gradle.kts:26-28 (hard check with the overflow message naming the layout)
~~43. Add a second signature check: verify the APK's signer equals `release.properties`' expected cert (keystore vs artifact, the AGENTS lesson).~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
~~44. Keep `work/dryrun*.log` out of the repo tree or delete after a run.~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
~~45. Add `shellcheck` to the devShell so `release.sh` gets a real linter (not just `bash -n`).~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
~~46. Add a `docs/` note on how Obtainium resolves versions (tag vs title) next to the setup steps.~~ r: TODO_LIST.md "Release-lane hardening (02-03 §b3-4, §b6, §f1-2, 11-13, 16-24, 37-49)"
47. Reconcile the two "status" narratives: README's "v0.1 scaffold" header vs the 0.2.0 version now in `version.txt`. **→ TODO_LIST.md "Reconcile the v0.1 scaffold status line"**
~~48. Sweep the remaining `app-release-unsigned.apk` references (`README.md:61`, `AGENTS.md:220`, `AGENTS.md:335`) so every mention states the conditional correctly.~~ v: README.md:69 and AGENTS.md:322/464 all state the conditional (signed when release.properties exists, unsigned otherwise); `rg "No release signing config" AGENTS.md` → 0 hits

---

## g) Three questions I cannot answer myself

1. **Update channel scope:** is Obtainium on the LAN/NetBird VPN (with the
   per-app "Allow insecure HTTP requests" toggle) acceptable as the permanent
   story, or should I build a publicly-trusted release endpoint (LE-cert vhost,
   or publishing the APK to the `pbx.artmann.tech` root) so updates also work
   on mobile data? **→ ROADMAP.md §3 Distribution raw idea "A public update endpoint"**
   ~~2. **Release automation:** do you want `scripts/release.sh` to auto-bump~~ r: ROADMAP.md Open questions "Release automation"
   `version.txt` and auto-cut the CHANGELOG heading after a successful publish
   (fewer gates, less ritual), or keep both as manual steps that a human
   confirms (current behaviour, harder to get wrong silently)?
2. **Mirror parity:** should the GitHub mirror carry the same tags/releases
   (a second Obtainium source, and better off-site durability), or stay a
   code-only mirror until the PAT/billing issues are fixed? **→ ROADMAP.md §3 Distribution raw idea "GitHub releases as a second mirror"**

---

_Report written 2026-10-28 02:03 CET. Awaiting instructions._
