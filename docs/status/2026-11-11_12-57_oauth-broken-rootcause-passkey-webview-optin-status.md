<!-- Status report: 2026-11-11 12:57 CET. Session: "why is OAuth broken +
     why can't we stay logged in / re-login with fingerprints" — diagnosed
     to one root cause, fixed the app half, emulator-proven, stack half
     drafted; the phone itself was NOT touched. -->

# Status: the passkey-login root cause found and the app half fixed (WebView WebAuthn opt-in), emulator-proven; stack half (assetlinks on the IdP host) drafted and waiting

## Scope line

VERIFIED this session (≈12:05→12:57 CET): root cause of "OAuth broken" +
"fingerprint re-login" + "stay logged in" — **one chain**: Pocket ID is
passkey-ONLY (no password door; docs.pocket-id.org) → Android WebView ships
WebAuthn DISABLED and the app never opted in (no androidx.webkit) → the
IdP's "Authenticate" button silently did nothing → every forced logout
(app-data wipes, the 30-day session cap) was a dead end. "Stay logged in"
itself was NEVER broken (server cookie persistent 7d-sliding/30d-abs,
webphone `internal/session/service.go:356`; app flushes the jar on
pause/page-finish). FIX SHIPPED (app half): androidx.webkit 1.18.0 +
`WebSurface.optInToPasskeys` (FOR_APP, guarded, logged) +
`scripts/assetlinks.sh` now emits both relations. EMULATOR-PROVEN:
`WebSurface: webauthn in webview: FOR_APP enabled (provider readback=1)`
on WebView 133.0.6943.137 (≥M131), live island loads, no crash; full
verify-gate GREEN ×3 (one ktlint import-order stumble, fixed
mechanically). Stack half (assetlinks `get_login_creds` on
`auth.larsartmann.cloud` — 404 there, probed) DRAFTED
(`docs/drafts/2026-11-11_stack-serve-assetlinks-on-idp-host-for-webview-passkeys.md`,
TODO_LIST row upgraded to High) but NOT deployed — the in-shell login
still dead-ends at the RP check until the stack serves it. Committed +
pushed origin (`f66ea26` on top of the daemon blobs + a CONCURRENT
session's push). NOT done: the phone (Edge 60) never touched; no
release cut; FEATURES.md untouched.

## What did I FORGET?

| # | The forget                                                                                                                                                                                                                                                                                                                                                               | Severity | Note                                                                                                                       |
| - | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------- | -------------------------------------------------------------------------------------------------------------------------- |
| 1 | **The actual broken DEVICE was never checked.** The whole diagnosis came from code + live HTTP probes + the emulator; the Edge 60 (the phone where you hit this) was not adb-touched — its WebView version (probably ≥131, unverified), its current login-page state, and whether a passkey is even provisioned ON it are all unknown                                    | High     | The phone was paired 2026-11-10 (`adb-ZY22LG7MRS`); one `dumpsys webviewupdate` would have closed the WebView-version half |
| 2 | **FEATURES.md got no row** — AGENTS/CHANGELOG/TODO_LIST/draft all updated, the feature inventory skipped; the next inventory-vs-code cross-check will flag passkey support as undocumented                                                                                                                                                                               | Med      | House convention (docs-health) expects FEATURES to carry shipped capabilities                                              |
| 3 | **No in-page JS proof.** `window.PublicKeyCredential` visibility inside THIS WebView was never probed (no JS injection seam in the shell; no remote debugging enabled) — the verdict log + provider version is the proof I settled for; a `setWebContentsDebuggingEnabled` + CDP probe would prove the API surface itself                                                | Med      | Acceptable for the app half (the setting readback is authoritative), but the deeper proof is cheap to add                  |
| 4 | **The interim workaround was asserted, not verified:** Pocket ID's one-time admin login code (the only non-passkey door) — I never checked the LIVE sign-in page exposes a login-code entry reachable in-shell; if it doesn't, the phone stays locked out even with operator willingness                                                                                 | Med      | One `fetch` of the sign-in page (or a screenshot pass) would answer it                                                     |
| 5 | **Shared-doc edits while a CONCURRENT session was live in this repo** — `4b9cc33` (release-lane token unblocked, repos went public) landed mid-session from another agent; I edited AGENTS/TODO_LIST without checking for active sibling sessions, and did NOT reconcile what "repos went public" means for rows I saw (Obtainium private-token row, insecure-HTTP note) | Med      | No clobber happened (lucky + frequent daemon commits), but it was luck, not discipline                                     |
| 6 | **Mirror-lag never re-checked after my push** — pushed origin (`4b9cc33..f66ea26`), did not run `scripts/mirror-lag.sh` or fetch `github/main` to confirm the mirror caught up (row 49 says plain commits propagate, but verification is the norm)                                                                                                                       | Low      | 10-second check                                                                                                            |
| 7 | **A unit-testable seam was left inline** — `optInToPasskeys` mixes decision + side effect; a pure `webauthnVerdict(isFeatureSupported)` would pin the log strings and the NONE/FOR_APP branch in the JVM suite (the meta-proof rule: my pin is the logcat line, device-proven once)                                                                                      | Low      | The guard path IS covered (Robolectric launches pass), the strings/branch are not pinned                                   |
| 8 | **Login-code reachability + phone passkey provisionment** feed directly into the answer to "why can't I log in on the phone" — I reported the workaround from docs only (see forget #4) and did not ask the operator whether their passkey lives on the phone at all (a laptop-only passkey cannot sign in on the phone EVEN WITH the fix)                               | Med      | Folded into question set (g) instead of guessing                                                                           |

## What could I have done BETTER?

| # | The better move                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| - | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | **Hand-commit each finished unit immediately** — the doc edits + code edits sat in the tree while the gate ran; the daemon blobbed them into 3 `chore: auto-commit` commits, one of which MIXES code and docs (`f1250a1` swept AGENTS/TODO_LIST + the ktlint-reordered WebSurface). My consolidation plan (soft-reset into one clean commit) died correctly when the concurrent session pushed the blobs — but the mess was avoidable: commit-then-verify, not verify-then-hope |
| 2 | **Check for sibling sessions FIRST** (`pgrep`-style: fresh commits in `git log`, running gates) before touching shared docs — AGENTS literally documents the crush-daily dispatch; the collision window was real                                                                                                                                                                                                                                                                |
| 3 | **Run `ktlintFormat` before the first gate** — the import-order violation (WebSettings before ValueCallback) cost a full gate cycle (~4 min) on a mechanically-fixable style slip; the "gate is the verifier, not the formatter" ordering habit                                                                                                                                                                                                                                 |
| 4 | **Probe the phone in the same session it was last attached** — the fix's weakest link is the unverified device half (WebView version, passkey presence, current screen state); the emulator proof is necessary but not the user's actual surface                                                                                                                                                                                                                                |
| 5 | **Batch the live probes in one pass:** begin-redirect-chain headers, assetlinks 404, Pocket ID sign-in-page login-code visibility — three fetches answered three questions; I ran two and assumed the third                                                                                                                                                                                                                                                                     |
| 6 | **Fold the emulator proof into the SAME session as the gotcha draft** — I wrote the gotcha with "a device train reads it from logcat", then proved it 20 minutes later and had to amend the same bullet; write docs after the proof when the proof is minutes away                                                                                                                                                                                                              |

## What could I STILL improve?

1. **JS-level device proofs need a seam:** enable WebView remote debugging
   (debug builds only) so a CDP socket can evaluate `window.PublicKeyCredential`
   / drive the actual ceremony without UI automation — turns "the setting is
   on" into "the API is live".
2. **Verdict-seam refactor:** extract the supported/NONE/FOR_APP decision +
   its log strings into a pure function with JVM pins (strings included);
   the device line stays the integration check.
3. **The stack half is THE gate now:** the app half alone is necessary-not-
   sufficient — until `auth.larsartmann.cloud` serves `get_login_creds`
   assetlinks with the RELEASE fingerprint, the login still fails at the RP
   check. The draft is file-ready; the filing/deploy is owner-adjacent.
4. **Concurrency etiquette as a habit:** session-start check for sibling
   agents + re-fetch before pushing; this session got lucky.
5. **Docs sweep discipline:** FEATURES.md belongs in the same edit-set as
   CHANGELOG/AGENTS/TODO_LIST every time.

## a) FULLY DONE

| #  | Item                                                                                                                                                                                                                                                                                                                      | Evidence                                                                                                                                                                                                         |
| -- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1  | **Root cause of "OAuth broken" + "no fingerprint re-login" — one chain, three receipts**                                                                                                                                                                                                                                  | Pocket ID passkey-only (docs.pocket-id.org intro); WebView WebAuthn default-OFF + M131 opt-in (MDN BCD #29452, Android docs, androidx source via sourcegraph); assetlinks 404 on auth.larsartmann.cloud (probed) |
| 2  | **"Stay logged in" cleared of blame** — server cookie persistent (7d sliding / 30d abs cap), app-side flush on pause + page-finish + renderer-rebuild; renewal Set-Cookie lands in the shared jar                                                                                                                         | webphone `internal/session/service.go:356` (`MaxAge: int(ttl.Seconds())`), `config.go:683-684`; `WebSurface.pause`/`onPageFinished`; `SessionApi.cookieJar` writes back + flushes                                |
| 3  | **App-half fix shipped: androidx.webkit 1.18.0 + `WebSurface.optInToPasskeys`** (FOR_APP not FOR_BROWSER, `isFeatureSupported`-guarded, `runCatching`, verdict LOGGED; KDoc carries the full WHY + the assetlinks dependency)                                                                                             | `gradle/libs.versions.toml`, `app/build.gradle.kts`, `WebSurface.kt` (`optInToPasskeys` + class-KDoc bullet)                                                                                                     |
| 4  | **Emulator device proof** — API 36 AVD, debug APK install + launch; logcat `WebSurface: webauthn in webview: FOR_APP enabled (provider readback=1)`; provider `com.google.android.webview 133.0.6943.137` (≥M131); live island loaded (`webphone loaded; sip domain pbx.artmann.tech`); no crash; emulator killed cleanly | /tmp/webphone-smoke.log + logcat grep (12:46 CET); `dumpsys webviewupdate`                                                                                                                                       |
| 5  | **Verification gate GREEN ×3** (attempt 1 red at ktlint import order → `ktlintFormat` → green; re-green twice after doc edits): assembleDebug + testDebugUnitTest (full suite incl. Robolectric launches exercising the guard under Robolectric) + ktlint + 16 KB zipalign + contrast + dprint                            | /tmp/webphone-gate.log, -gate2.log, -gate3.log; `GATE EXIT 0` ×2                                                                                                                                                 |
| 6  | **`scripts/assetlinks.sh` emits BOTH relations** (`handle_all_urls` + `get_login_creds`) with per-host serving duties + the RELEASE-keystore warning in the header                                                                                                                                                        | `scripts/assetlinks.sh` (header comment + emitted JSON)                                                                                                                                                          |
| 7  | **Cross-repo stack half drafted with receipts** — `docs/drafts/2026-11-11_stack-serve-assetlinks-on-idp-host-for-webview-passkeys.md` (JSON to serve, both hosts, verify steps, source receipts incl. the 404 probe)                                                                                                      | the draft file; TODO_LIST row 70 upgraded Med→High (both hosts, both relations, release fingerprint)                                                                                                             |
| 8  | **Docs updated in-house style:** CHANGELOG `[Unreleased]` (Added + Fixed), AGENTS gotcha (passkey-only IdP + WebView opt-in + emulator proof folded in), TODO_LIST rows 70/71 amended                                                                                                                                     | the three files; dprint-clean                                                                                                                                                                                    |
| 9  | **All work committed + pushed origin** (`f66ea26` docs proof on top of daemon blobs `66fcc0d`/`6923a92`/`f1250a1` + concurrent session's `4b9cc33`); NO history rewrite (blobs were already pushed by the sibling session — the amend remedy correctly refused)                                                           | `git log origin/main..HEAD` empty after push; `4b9cc33..f66ea26 main -> main`                                                                                                                                    |
| 10 | **Daemon-blob remedy applied correctly under pressure** — verified blob contents were solely this session's paths, printed `rev-list --count origin/main..HEAD` before any reset consideration, and ABORTED the squash the moment fetch showed the blobs pushed (never amend after push)                                  | session log; the blobs stay as-is in the pushed history                                                                                                                                                          |

## b) PARTIALLY DONE

1. **The passkey login fix END-TO-END** — app half DONE + device-proven;
   stack half (assetlinks on `auth.larsartmann.cloud`) drafted but NOT
   served. Until the stack lands it, the login still fails at the RP
   check: the user-visible behavior on the phone is UNCHANGED until the
   stack fix + a build carrying the opt-in reach the device.
2. **The phone** — never touched this session (last state 2026-11-11
   morning: 0.2.1/vc201 sideloaded, signed out, login blocked). The fix
   rides either a v0.2.2 release (Obtainium) or another adb deploy;
   neither attempted (release lane is owner-gated; the token was just
   reported unblocked by the sibling session — unverified by me).
3. **Re-login with fingerprint** — possible-in-principle after both
   halves; the FINAL proof (a real ceremony with the operator passkey)
   stays R17-blocked (operator credential), plus the phone-resident-
   passkey question (g2).

## c) NOT STARTED (owner/user-gated or discovered-this-session)

1. Stack serving `/.well-known/assetlinks.json` on `auth.larsartmann.cloud`
   AND `pbx.artmann.tech` (the draft is ready; SystemNix/Caddy or webphone
   static serving — owner's call where).
2. The draft FILED into webphone/SystemNix (cross-repo filings are drafted
   here, filed from the real file — not done).
3. v0.2.2 release cut + publish + Obtainium cycle carrying the opt-in.
4. FEATURES.md row for passkey/WebAuthn support.
5. In-page `PublicKeyCredential` probe (remote debugging seam).
6. Phone-side verification sweep (WebView version, POST_NOTIFICATIONS +
   battery whitelist re-check per the AGENTS gotcha, login-page state).
7. R17 (live Pocket ID login with the operator passkey) — unchanged,
   now additionally gated on the stack half.

## d) TOTALLY FUCKED UP

1. **Nothing broke.** No data loss, no reverted work, no force-push, no
   collision damage; gate green, tree clean at `f66ea26`, emulator killed.
2. Honest scars, not fuckups: (a) the git history now carries 3 daemon
   blobs of my work pushed by a sibling session — unfixable without a
   forbidden force-push, cosmetic only; (b) one wasted gate cycle on the
   ktlint import order (self-inflicted, mechanical); (c) the phone-blind
   spot (forget #1) — the fix is proven on an AVD, not on the device
   where you hit the bug.

## e) WHAT WE SHOULD IMPROVE

1. **Ship the stack half** — it is now the ONLY blocker between the user
   and a working fingerprint login (modulo the phone-resident-passkey
   question). Small Caddy/static change, big unlock.
2. **A "device-login readiness" probe script** (webphone-android side):
   one command that checks the phone's WebView version, the app's
   installed version, POST_NOTIFICATIONS, battery whitelist, and the
   assetlinks endpoints (both hosts) — the answer to every future "why
   can't I log in / didn't it ring" report in one shot (extends
   `ring-diagnose.sh`'s spirit to the auth lane).
3. **JS-provable WebView:** remote debugging seam in debug builds for
   in-page API probes (passkey availability, island console capture).
4. **Session-start sibling check:** fresh-commit sniff + gate-owner check
   before shared-doc edits (the AGENTS busy-build guard covers Gradle,
   not markdown).
5. **Commit-per-unit discipline:** the daemon blob + concurrent-push
   incident is the second reminder this week; hand-commit finished units
   the moment the gate greens.
6. **FEATURES.md in the standard edit-set** (CHANGELOG + AGENTS +
   TODO_LIST + FEATURES) so inventories never drift.

## f) Things to get done next

Ranked by this session's impact; "(new)" = born this session, otherwise
the TODO_LIST row stands as cited.

| #  | Task                                                                                                                                                                                                  | Source                |
| -- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------- |
| 1  | Serve assetlinks.json (both relations, RELEASE fingerprint) on auth.larsartmann.cloud AND pbx.artmann.tech — the login unlock                                                                         | (new) row 70 + draft  |
| 2  | File the draft into webphone/SystemNix (from the real file, per the cross-repo convention) and track to acceptance                                                                                    | (new)                 |
| 3  | Decide v0.2.2: cut the release (token reportedly unblocked by the sibling session — verify first), publish, Obtainium update on the phone                                                             | row 68 + (new)        |
| 4  | Verify the forge token + whether the repos going public changes Obtainium setup (private-token row, insecure-HTTP note) — reconcile rows 49/68/69 with the sibling session's "public on both remotes" | (new) row 69          |
| 5  | Phone sweep once attached: WebView provider version, app version, POST_NOTIFICATIONS + battery whitelist re-check, current login-page state                                                           | (new) + AGENTS gotcha |
| 6  | Live passkey login proof (R17): complete the OIDC dance in-shell once both halves are live; pill climbs to `Connected · wake on`                                                                      | row 71 (R17)          |
| 7  | Verify Pocket ID's login-code entry is reachable in-shell (the interim workaround) — fetch/sign-in-page pass                                                                                          | (new) forget #4       |
| 8  | Confirm the phone has a PHONE-RESIDENT passkey for Pocket ID (a laptop-only passkey can never sign in on the phone) — owner question + Pocket ID admin check                                          | (new) g2              |
| 9  | Extend `emulator-train.sh` to grep the `webauthn in webview` verdict line — the train self-reports the opt-in forever                                                                                 | (new)                 |
| 10 | Extract `webauthnVerdict` pure seam + JVM pins (log strings, NONE/FOR_APP branches)                                                                                                                   | (new) forget #7       |
| 11 | Remote-debugging seam (debug builds) for in-page probes (`window.PublicKeyCredential`, ceremony error classes)                                                                                        | (new)                 |
| 12 | FEATURES.md: add the passkey/WebAuthn row (+ reconcile the inventory with this session's CHANGELOG)                                                                                                   | (new) forget #2       |
| 13 | Device-login readiness probe script (auth-lane `ring-diagnose.sh` sibling)                                                                                                                            | (new) e2              |
| 14 | `scripts/mirror-lag.sh` after every push until zero-lag is proven                                                                                                                                     | AGENTS norm           |
| 15 | E2E ring proof + post-proof docs sweep (needs the stack switches + a signed-in phone)                                                                                                                 | row 51                |
| 16 | First device train end-to-end (operator inputs Q1)                                                                                                                                                    | row 52                |
| 17 | First-run UI walk on the phone (unlocked window needed)                                                                                                                                               | row 53                |
| 18 | Answer-path race verification (push → FSI vs INVITE) on the live stack                                                                                                                                | row 54                |
| 19 | Logged-in emulator flows (composer attach, hand-off, back stack) once login works                                                                                                                     | row 55                |
| 20 | M050 re-run with the hardened harness (deep-link begin → detour clear-path proof)                                                                                                                     | row 29                |
| 21 | Emulator smoke/train script hardening (timeouts) — partially done upstream? re-verify                                                                                                                 | row 30                |
| 22 | M035 local CI rehearsal in a clean env                                                                                                                                                                | row 31                |
| 23 | Warning-freeze gate step (Kotlin warnings at zero — keep it there)                                                                                                                                    | row 32                |
| 24 | Opt-in pre-push hook running verify-gate.sh                                                                                                                                                           | row 33                |
| 25 | forkEvery cost audit (wall-time trade verdict)                                                                                                                                                        | row 34                |
| 26 | Test-count honesty gate step (re-count `@Test` mechanically)                                                                                                                                          | row 35                |
| 27 | Track webphone#21 to acceptance (begin stays a server 302)                                                                                                                                            | row 36                |
| 28 | Fold pill/overlay pixel screenshots into the emulator train                                                                                                                                           | row 37                |
| 29 | `scripts/device-deploy.sh`: codify the proven phone-deploy dance (0.2.1 went over adb; the next fix delivery may need it again)                                                                       | row 38 + (new)        |
| 30 | Dogfood `ring-diagnose.sh` on the attached phone                                                                                                                                                      | row 39                |
| 31 | Phone notification inventory (the four lock-screen icons verdict)                                                                                                                                     | row 40                |
| 32 | dprint explicit-file anomaly root-cause (fmt <file> no-ops)                                                                                                                                           | row 41                |
| 33 | nix GC roots / keep-outputs for both devShells (GC forced SDK re-downloads yesterday)                                                                                                                 | row 42                |
| 34 | Owner: SystemNix deploy (mirror attach — now OBSERVED-alive; confirm and close row 48)                                                                                                                | row 48                |
| 35 | Owner: sync PAT with `workflow` scope (workflow-file mirrors still rejected)                                                                                                                          | row 49                |
| 36 | Owner: GitHub Actions billing fix (runs fire, jobs refuse)                                                                                                                                            | row 50                |
| 37 | First CI evidence run archived after billing fix                                                                                                                                                      | row 61                |
| 38 | Logout deregistration seam (JS bridge vs cookie-poll; KEEP vs PURGE owner pick)                                                                                                                       | row 63                |
| 39 | Live contract checks against the live stack (session fields, CSRF)                                                                                                                                    | row 64                |
| 40 | Island standdown seam (duplicate-registration signal)                                                                                                                                                 | row 65                |
| 41 | CallStyle device verification (post-time style + fallback)                                                                                                                                            | row 66                |
| 42 | TalkBack walkthrough + battery-prompt verify                                                                                                                                                          | row 67                |
| 43 | Connect the phone to Obtainium (first install needs the debug build gone; insecure-HTTP toggle) — may be reshaped by the repos going public                                                           | row 69                |
| 44 | Island safe-area-inset-top cross-repo fix (web header under the status bar)                                                                                                                           | row 62                |
| 45 | targetSdk 36 → 37 after a device train validates Android 17 behavior                                                                                                                                  | row 72                |
| 46 | Consider a longer SessionMaxTTL or a visible "session expires" affordance server-side IF the 30-day cap keeps forcing logins (owner/product call, webphone repo)                                      | (new)                 |
| 47 | AGENTS: fold the "repos went public" reality into the forge/Obtainium gotchas once confirmed (certificate + token guidance changes)                                                                   | (new)                 |
| 48 | Add the webauthn verdict + assetlinks endpoints to `ring-diagnose.sh`'s checklist (auth lane feeds the ring lane: no session → no ring)                                                               | (new)                 |
| 49 | Sweep the passkey diagnosis into a webphone apidocs note (the Android shell's RP/assetlinks requirement is stack knowledge)                                                                           | (new)                 |
| 50 | Re-run `scripts/verify-gate.sh` + the emulator train after ANY webkit/WebView-provider bump (the M131 floor moves with the provider)                                                                  | (new)                 |

## g) Questions I cannot figure out myself

1. **What EXACTLY did you see on the phone when login failed?** (a) the
   Pocket ID page loaded in the shell and the "Authenticate" button did
   nothing; (b) the IdP opened in Chrome instead of the shell; (c) an
   error page / `?sso_error=…` card. I diagnosed (a) from code + probes;
   if reality was (b) or (c), my root-cause chain needs a second look
   before the stack half is built.
2. **Does your Pocket ID account have a passkey provisioned ON the Edge
   60 itself** (fingerprint-locked on that device), or do your passkeys
   live on the laptop / a hardware key? If the phone has no resident
   passkey, the ceremony can never complete there even after both fix
   halves ship — the remedy would be enrolling a phone passkey first
   (via the Pocket ID admin or a one-time login code).
3. **Ship path + stack filing permission:** (a) cut v0.2.2 through
   `scripts/release.sh` now (the sibling session reports the token
   unblocked — do you confirm I may use it?), or adb-deploy a 0.2.1+
   build to the phone first; and (b) may I file the assetlinks change
   into SystemNix/webphone myself (commit + push there), or is the
   stack session/owner handling it?

💘 Generated with Crush
