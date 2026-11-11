<!-- Status report 2026-11-11 11-43 — point-in-time snapshot. Scope-verified
     against this session's own command output; carried items are cited, not
     re-researched. -->

# Status: adb deploy of 0.2.1 to the Edge 60 — release lane recon

**Scope line — what this report VERIFIED and with what:** the phone deploy ran
09:18–09:30 CET against the attached Edge 60 over wireless adb (`adb-ZY22LG7MRS-w16Pva._adb-tls-connect._tcp`,
default devShell tree): installed-state dumpsys (0.2.0/vc200 before, 0.2.1/vc201
after), a signing-cert cross-check (pulled `base.apk` → `apksigner` SHA-256 vs
`~/.config/.android/debug.keystore`, exact match), `verify-gate.sh` (GATE EXIT 0,
50/50 contrast checks), `aapt dump badging` (versionCode 201), `adb install -r`
("Success"), monkey launch + `pidof` (16772) + AndroidRuntime crash scan (clean),
two screencaps (black/off-screen, then the lock screen showing the app's
notification icons), the git unit (daemon blob `d3b8894` amended into `8ac9ccc`,
origin = github = HEAD after hand push), and `release.sh --doctor` (forge
reachable, Forgejo 15.0.11, `token: MISSING`).

**What this report did NOT verify:** the app's in-shell UI on the phone (the
screen is fingerprint-locked and I do not bypass secure keyguards); which
notifications the four lock-screen icons represent (not enumerated via
`dumpsys notification`); whether the app registers against the live stack from
the phone post-install; mirror propagation after my push (I hand-pushed BOTH
remotes, so the mirror got nothing to prove today); the overlay/pill/detail-sheet
pixels on the phone; carried owner-gated rows (cited from `TODO_LIST.md`, not
re-verified today).

Headline: **the phone now runs 0.2.1 (versionCode 201) built from `8ac9ccc` —
deploy via adb DONE and verified; the Obtainium/forge update lane remains
blocked on an EMPTY token file, and the version spend (0.2.1 on a sideload)
creates a first-release numbering decision.**

---

## a) FULLY DONE

1. **Session recon** — git state (daemon blob `0653274` = `flake.lock` +
   `sync-wire-fixtures.sh` indent sweep, inspected with `git show`, already on
   origin, left alone), `version.txt` 0.2.0, no gradle running, release key +
   props present, `forgejo-token` 0 bytes.
2. **Housekeeping owed from last session** — job 075 was already gone (session
   restart cleared the job table); the orphaned emulator qemu (PID 3642118,
   up ~16 h) was found and killed (SIGTERM oddly failed, SIGKILL worked;
   `pgrep` confirmed clean after).
3. **Phone link on ONE adb tree** — default devShell's adb (37.0.1) owns port
   5037 and sees the Edge 60 via TLS wireless debugging.
4. **Installed-state recon** — 0.2.0/vc200, installed 2026-11-10 15:06,
   `installerPackageName=null` (sideload).
5. **Signing-cert cross-check (the deploy precondition)** — pulled the phone's
   `base.apk`, `apksigner verify --print-certs`: SHA-256
   `84e2c27f…5d7d81` == the local debug keystore fingerprint, byte-for-byte →
   in-place `-r` update would not be rejected.
6. **Version bump + CHANGELOG cut** — `release.sh bump patch` → 0.2.1;
   `[0.2.1] - 2026-11-11` heading cut per the script's own guidance (carries
   the wake-lane verdicts, overlay fix, detail sheet — exactly what this build
   ships).
7. **Full verify gate green** — `scripts/verify-gate.sh` GATE EXIT 0:
   assembleDebug + 138-test JVM suite + ktlint + KGP parity + 16 KB zipalign +
   50/50 contrast checks + dprint check.
8. **Artifact honesty check** — `aapt dump badging` on the built debug APK:
   `versionCode='201' versionName='0.2.1'` (the bump really got in).
9. **Install** — `adb install -r` streamed install, `Success`, app data
   preserved.
10. **Post-install package verification** — dumpsys: vc201/0.2.1,
    lastUpdateTime 2026-11-11 09:27:25.
11. **Launch + liveness + crash scan** — monkey LAUNCHER intent, pid 16772
    alive after 6 s, `-s AndroidRuntime:E` logcat scan found no crash lines.
12. **Screen-state handling** — first screencap was BLACK (screen off);
    `KEYCODE_WAKEUP` + screencap #2 captured the LOCK SCREEN showing the app's
    notification icons (four webphone glyphs) — app visibly alive and
    notifying. Secure fingerprint lock was NOT bypassed (`wm dismiss-keyguard`
    is a no-op there, by design).
13. **Git unit landed clean** — the daemon swept version.txt + CHANGELOG into
    blob `d3b8894`; the remedy conditions were verified (blob = this session's
    two paths only; `git rev-list --count origin/main..HEAD` = 1 with BOTH
    remotes lagging; diff understood) and the blob was amended into `8ac9ccc`
    with a real message; origin AND github pushed — all three SHAs equal.
14. **Release-lane doctor probe** — `release.sh --doctor`: version line
    correct (`v0.2.1 (versionCode 201)`), signing key found, forge reachable
    (Forgejo 15.0.11, attachment limit 2048 MiB / 5 files), verdict
    `token: MISSING (set FORGEJO_TOKEN)`.
15. **dprint anomaly investigated** — `nix run nixpkgs#dprint -- fmt
    CHANGELOG.md` fails ("No files found to format with the specified
    plugins") while the bare `fmt`/`check` forms (what the gate runs) work and
    the tree is format-clean. Explicit-file form is broken, bare form is fine.

## b) PARTIALLY DONE

1. **"Deploy a new version to my phone"** — the CODE is on the phone and
   verified at package/process/notification level; the durable UPDATE LANE is
   not: no forge release exists, the token is empty, and the phone cannot take
   a release-signed build until the one-time uninstall swap (debug→release
   key). The adb deploy satisfies the request; Obtainium-first remains
   unproven end-to-end.
2. **On-device verification** — three of four levels proven (package, process,
   notifications); the UI level (shell rendering, pill state, detail sheet)
   is blocked by the secure lock. Needs an unlock window.
3. **"First physical install" TODO row** — the install half is DONE as of
   today; the first-run UI walk half remains (same unlock constraint).
4. **Release-lane readiness** — doctor passes everything EXCEPT the token:
   forge reachable, attachment limits fine, signing key present, version line
   correct. One missing credential away from publishable.
5. **dprint hygiene** — the tree is format-clean and the gate enforces it, but
   the documented explicit-file `fmt` form silently no-ops; the AGENTS command
   needs a correction (this unit).

## c) NOT STARTED

All carried — cited from `TODO_LIST.md`/last session's report, NOT re-researched:

- Forge release publish (`v0.2.1` or `v0.2.2`, see §g1) — token-gated.
- Obtainium one-time setup on the phone + the debug→release signature swap.
- Pocket ID live sign-in on the phone (R17).
- Stack switches: `push.ntfy.url` configured, `ringhook.enable` armed.
- E2E ring proof (`ring-diagnose.sh` verdict) + post-proof docs sweep
  (F11–F13, F80–F83).
- GitHub Actions billing fix; sync PAT `repo`+`workflow` scope; SystemNix
  deploy for mirror phase-5.
- Cross-repo: `assetlinks.json` serving; island `safe-area-inset-top` fix
  (webphone repo).
- `scripts/ring-diagnose.sh` dogfood against the attached phone (queued last
  session, deliberately untouched today to keep the deploy tight).
- Overlay RECOVERY-path verification on-device (airplane OFF → successful
  reload dismisses the overlay) — Robolectric-pinned only.
- The three §g questions from the 2026-11-11 02-04 report (still unanswered).

## d) TOTALLY FUCKED UP

Nothing was destroyed, nothing broken shipped, both remotes and the phone are
consistent. Honest near-misses, owned:

1. **Launched the app while the screen was OFF** — monkey fired, pid proved
   alive, but the first screencap was a useless black frame; visual proof
   waited on a wake + rescreenshot and was still limited to the lock screen.
   Wake-before-launch costs one command and was forgotten.
2. **A compound shell line masked a tool failure** — `dprint -- fmt
   CHANGELOG.md && git diff --stat && pgrep -f gradle || echo "gradle clear"`
   turned dprint's hard failure into the innocuous-looking "gradle clear"
   line. The file happened to be format-clean (bare-form test + gate check
   prove it), but the pattern is a masking anti-pattern; verification steps
   belong in separate commands.
3. **A version number got spent in 10 seconds** — bumping to 0.2.1 for the
   sideload means a future `v0.2.1` forge publish will read as "up to date"
   on the phone's Obtainium (installed 0.2.1 already). Defensible for provable
   newness (dumpsys diff is the deploy evidence) but it converts the
   first-release version into a §g decision debt (below).
4. **~2 minutes burned on a devShell rebuild I never needed** — the emulator
   devShell re-fetched GC'd SDK derivations before I aborted; the emulator was
   not needed for this task at all. A `pgrep qemu`-first order would have
   skipped it (store GC cost, see §e).

## e) WHAT WE SHOULD IMPROVE

**What I forgot (and fixed/owed):**

- Memory maintenance at discovery time — the token-empty state, the deploy
  technique, and the dprint anomaly belong in AGENTS.md (amended in this
  unit) and the harvest rows belong in TODO_LIST.md (same unit).
- The notification inventory (`dumpsys notification`) that would have named
  the four lock-screen icons instead of leaving them unidentified.
- The screen-state check before launching for visual proof.
- Parallelizing the doctor probe WITH the gate (independent probes; ran after).

**What could have been done better:**

- Wake the screen BEFORE `monkey` → the launch is then visually provable.
- Separate verification commands (the `&&…||` masking, above).
- Decide the sideload version policy BEFORE bumping: `adb install -r` accepts
  an equal versionCode, so today's deploy could have stayed 0.2.0 and saved
  the number for the first forge release. Provable-newness won; a §g question
  is the price.
- The doctor probe and the gate could have overlapped (minutes saved).

**What we should still improve (systemic):**

- Codify today's manual dance as `scripts/device-deploy.sh`: pgrep-gradle
  guard → cert cross-check → (bump?) → gate → `install -r` → dumpsys verify →
  wake + launch + screencap + crash-scan. The dance is proven twice now;
  script it.
- nix GC roots (or `keep-outputs`) for the two devShells — the store GC since
  last session cost minutes of SDK re-downloads this session and will again.
- A visual-verification strategy for the secure-locked phone: either a
  scheduled unlock window for pixel passes, or accept notification-level
  liveness proofs as the phone-side standard and keep pixel proofs on the
  emulator.
- The dprint wrapper: make the explicit-file form work or fix the AGENTS
  command to the bare form (corrected in this unit).
- Notification hygiene audit: four webphone notification icons on a lock
  screen immediately after launch — enumerate them and decide whether the
  persistent set should be minimized (FGS promotion/silent) or is intended.

## f) Up to 50 things we should get done next

Agent-executable (this repo):

1. `scripts/device-deploy.sh` — codify the verified deploy dance (§e).
2. Dogfood `scripts/ring-diagnose.sh` against the attached Edge 60 (queued
   2026-11-11 02-04 §f2; free verification of the shipped tool).
3. Phone notification inventory: `dumpsys notification` — identify the four
   lock-screen icons; verdict on persistent-notification minimization.
4. Investigate/fix the dprint explicit-file anomaly (`fmt <file>` no-ops,
   bare `fmt`/`check` work) — root cause, then either fix or document.
5. Verify the overlay RECOVERY path on-device (airplane OFF → reload
   dismisses overlay) — carried; needs an unlocked-phone window.
6. In-shell pixel pass ON THE PHONE (pill states, detail sheet, overlay) —
   needs the same unlock window.
7. Verify the phone's post-install session state matches design (signed-out
   → pill "Not connected", lane UNKNOWN).
8. M050 re-run with the hardened harness (carried Ready row).
9. Fold pill/overlay/detail-sheet screencaps into `scripts/emulator-train.sh`
   (carried Ready row).
10. Emulator train/smoke script timeout hardening (carried Ready row).
11. Warning-freeze gate step (carried Ready row).
12. Opt-in pre-push hook running the gate (carried Ready row).
13. M035 local CI rehearsal in a clean env (carried Ready row).
14. Test-count honesty gate step (carried Ready row).
15. `forkEvery` cost audit (carried Ready row).
16. nix GC roots / `keep-outputs` for both devShells (new, §e).
17. Track webphone#21 acceptance (carried Ready row).
18. Mirror zero-lag proof across a workflow-file change (carried; blocked on
    the PAT row).

Owner-gated (prepared, not executable by an agent):

19. Fill `~/.config/webphone-android/forgejo-token` (0-byte today; Forgejo
    token with `write:repository`) — then `release.sh --doctor` goes green.
20. Decide the first-release numbering: publish `v0.2.1` as bookkeeping
    (Obtainium will call it up-to-date — the phone runs 0.2.1) vs bump to
    `v0.2.2` first so Obtainium offers a real first update (§g1).
21. Cut the first release with `scripts/release.sh` (gate + sign + tag + push
    - forge publish).
22. Obtainium one-time setup on the phone (add app, read-only token,
    "Allow insecure HTTP requests" ON).
23. The one-time debug→release swap: uninstall (loses origin-default +
    session; default origin is the operator's stack anyway) → release install.
24. Pocket ID live sign-in on the phone (R17).
25. Stack switches: `push.ntfy.url` + `ringhook.enable` on the consuming
    stack.
26. E2E ring proof + `ring-diagnose.sh` on-record verdict + post-proof docs
    sweep (F11–F13, F80–F83).
27. GitHub Actions billing fix (M003) → then the first real CI run evidence.
28. Sync PAT `repo`+`workflow` scope regeneration (M002).
29. SystemNix deploy attaching the push mirror (M001).
30. Battery-prompt open question: does the app's once-per-install battery ask
    still fire now that adb pre-granted the whitelist? (carried g2).
31. targetSdk 36→37 after a device train validates Android 17 behavior
    (carried).

Cross-repo (webphone):

32. Serve `/.well-known/assetlinks.json` (App-Link verification).
33. Island `safe-area-inset-top` fix (W01 status-bar overlap).
34. webphone#21 acceptance watch (OIDC begin stays a 302).

Carried/parked (unchanged, listed for completeness):

35. Reconcile the "v0.1 scaffold" status line vs `version.txt` (now 0.2.1).
36. ROADMAP Open questions review (M079-M080).
37. AGENTS slim-down below 30 KB (M082).
38. API 37 emulator system image (M084).
39. mockwebserver3 migration (M085).
40. Loopback live-check script (plan D1).
41. Runtime contract digest handshake (plan D1).
42. `/config.js` key fixtures (plan D1).
43. Logout deregistration: JS bridge vs cookie-poll (operator decision).
44. Island-standdown seam (owner nod first).
45. TalkBack device walkthrough + battery-prompt verify.
46. CallStyle device verification (post-time style + fallback).
47. Answer-path race verification (push FSI vs INVITE timing).
48. Live contract checks against the LIVE stack (the live-stack leg of D1).
49. Post-train docs sweep: honesty retirement + CHANGELOG cut for the next
    version (J25).
50. Annotate + archive today's report and the 02-04 report when superseded
    (the archive's own rule).

## g) Three questions I can NOT figure out myself

1. **First-release numbering:** when the token lands, should `scripts/release.sh`
   publish `v0.2.1` as-is (pure bookkeeping — your phone already runs a
   0.2.1 sideload, so Obtainium will show it as up-to-date), or should I bump
   to `0.2.2` first so the FIRST forge release is also the FIRST update
   Obtainium actually offers? (This decides the publish runbook I prepare.)
2. **Release-first or proof-first** (carried unanswered from 02-04 §g3): do
   you want the release published as soon as the token exists, or held until
   the E2E ring proof is green (stack switches + phone sign-in) so the first
   release carries a proven ring-while-closed lane?
3. **Persistent notifications:** the lock screen showed FOUR webphone
   notification icons immediately after launch. Do you want them all
   (foreground-service + lane notices are visibility by design), or should I
   audit and minimize the persistent set (silent/FGS-promoted, fewer glyphs
   on the lock screen)? I can enumerate WHICH notifications they are myself —
   whether you WANT them visible is the part I cannot decide.
