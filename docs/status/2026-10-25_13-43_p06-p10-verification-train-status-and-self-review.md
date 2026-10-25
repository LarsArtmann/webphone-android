# P06–P10 Verification Train — Status & Self-Review (2026-10-25 13:43)

> **Scope:** THIS session only (10:17 → 13:43): executing the
> [10-17 plan](../planning/2026-10-25_10-17_SUPERB-post-hardening-verification-and-daily-driver-plan.md)
> rows P06–P10, plus P08 investigation. The plan's errata block and `TODO_LIST.md`
> were updated live; this file is the point-in-time snapshot with the self-review.
>
> **Snapshot rot header — claims verified as of 13:43 CET:** local HEAD `f4c9ca4`
> (the concurrent session's narrative commit landed after my `28ba2be`); both
> remotes carried `28ba2be` at push time; CI fired and failed on GitHub billing
> (3s, account-level) — that verdict is owner-action-gated and will stay red until
> the billing fix.

---

## a) FULLY DONE

1. **P09 — WebView behavior tail (W110–W114), shipped in `WebSurface.kt`:**
   - `DownloadListener`: same-origin `download` links ride the system
     DownloadManager **with the session cookie** (attachment routes are
     session-gated); foreign downloads hand off via `ACTION_VIEW` — the session
     cookie never leaves the origin. Filename derivation: Content-Disposition
     (quoted/unquoted) → URL path segment → `"download"`, control chars and
     path separators stripped, leading dots trimmed.
   - **HTTP-error policy DECIDED + written next to the code** (W111):
     `onReceivedHttpError` is a documented no-op — origin pages (proxy 502, the
     styled 404) RENDER; the offline overlay is transport-failure-only, exposed
     via the unit-tested `onMainFrameTransportFailure(isMainFrame)` seam.
   - 5 new tests in `WebSurfacePolicyTest.kt`: scope guard wired through
     `shouldOverrideUrlLoading` (same-origin false / foreign + `tel:` hand-off
     with intent assertions), transport main-frame vs sub-resource split,
     HTTP-error-doesn't-mask, download policy (enqueue count + no cookie-lane
     for foreign), filename table.
2. **P10 — contrast guard as code:**
   - `scripts/contrast-check.py`: WCAG relative luminance + ratio, parses the
     REAL tokens from `values{,-night}/colors.xml`, checks both modes,
     alpha-composites the chrome glass over named bases (worst case), typo
     guard for unresolvable tokens, **fail exit proven** (injected failing pair
     → exit 1) and composite-math sanity-checked (50% black over white = 127).
   - `scripts/contrast-pairs.json`: 32 pairs → **50 checks**, 0 failures on the
     current palette. The audit's three fixed pairs (muted-on-raised 5.10:1,
     white-on-answer 3.61:1, white-on-end-call 4.72:1) are pinned against
     regression.
   - CI step added (`python3 scripts/contrast-check.py`); dprint-clean.
   - Found + fixed a REAL latent failure while building it: dark `wp_danger`
     (#E5484D) was 4.31:1 on dark surface — text-grade nowhere. Now `#F87171`
     (6.1:1), documented in-place; `wp_on_danger` deliberately unlisted until a
     filled danger surface exists.
3. **P08 — first-push CI investigation (F041):** the `push` trigger VERIFIED
   (both 09:25/09:33 runs + my 12:31 run fired). Jobs cannot start:
   _"recent account payments have failed or your spending limit needs to be
   increased"_ — GitHub billing, owner-only, NO workflow bug. Documented in
   TODO_LIST + plan errata.
4. **P06 subset — emulator verification pass, via the NEW
   `scripts/emulator-verify.sh`** (committed; owns its emulator lifetime,
   single-emulator adb discipline, uninstall-before-install):
   - **W101 dark seam:** mean luminance 243 → 146, dominant color `#12151B`
     (the web app's OWN dark theme via API 33+ darkening). VERIFIED.
   - **W102 light:** back to 243. VERIFIED.
   - **W103 offline overlay + Retry:** airplane-mode cold boot → overlay is the
     exact `#F5F8F7` `wpBackground` fill (uniform top band) with the accent
     retry control in the center band; Retry tap recovered the page
     ("webphone loaded" logged, page-level stddev restored). VERIFIED.
   - **W107 renderer kill:** `kill <sandboxed_process pid>` → **app pid
     SURVIVED**, fresh WebView reloaded the origin. VERIFIED.
   - **F024 dialpad DayNight:** 152 dark vs 249 light. VERIFIED.
   - **Console bridge live:** WebSurface logcat lines carry
     `sourceId:lineNumber` (ui.js:63) — the seam works against the live stack.
   - Bonus finding: the live stack (pbx.artmann.tech) is emulator-reachable;
     the island's own offline banner also fired during airplane mode.
5. **Docs truth:** CHANGELOG entries (downloads, HTTP-error policy, contrast
   gate, 60-test note), AGENTS.md gotchas extended (downloads/HTTP-error
   contract + "contrast is a GATE" rule), TODO_LIST re-statused (P09/P10 rows
   removed as done; P06 partial with evidence; P07 re-ranked BLOCKED with
   code-read rationale; P08 BLOCKED billing), plan-doc execution-errata block.
6. **Pushed:** `28ba2be` on BOTH remotes (origin + github over SSH — history
   carries `ci.yml`), tips verified via ls-remote; CI run fired (billing-red,
   expected).

## b) PARTIALLY DONE

1. **P06 (the rest):** W104 (attach → picker), W105 (external-link hand-off),
   W106 (tab-nav back stack + `canGoBack` boundary) are NOT verifiable
   headless without a logged-in session — the shell renders the login page
   only. Needs credentials/test extension (Q1-adjacent). TODO_LIST row updated
   to 60min residual.
2. **P07 — ntfy kill-cycle (F035/F036):** the LIVE `am kill` cycle did not
   exercise the sticky path: a fresh emulator has no server/topic in prefs, so
   `NtfySubscriptionService.onStartCommand` self-stops (`START_NOT_STICKY`)
   BEFORE the kill — BY DESIGN (verified by code read + `dumpsys` capture).
   The FGS start itself IS verified (`am start-foreground-service` →
   ActivityManager "Allowed"). The null-intent resubscribe path remains
   unit-pinned only. Re-ranked BLOCKED on a configured session.
3. **F038 (smoke dry run):** the first `emulator-smoke.sh` run proved
   boot/install/launch (Status: ok, cold 3.4s) but FAILED at its final
   screenshot step (my `work/` dir didn't exist yet — ordering bug, see d5).
   The pass overall is "run + mostly green", not clean-green; I did not re-run
   the smoke script to closure (replaced by emulator-verify.sh).

## c) NOT STARTED (from this session's plan rows — untouched)

- P01–P05, P11–P27 of the 10-17 plan (owner-blocked items, auth-ladder soak —
  which the concurrent session has since started — release smoke, docs-health
  big pass, etc.). Not this session's scope; state carried in TODO_LIST.

## d) TOTALLY FUCKED UP (brutal honesty)

1. **Three instances of the SAME exact-match-edit failure mode:** (i) the
   WebSurface companion-object multiedit left dangling braces; (ii) a test-file
   multiedit silently DELETED the same-origin assertion while moving a comment;
   (iii) the TODO_LIST P09/P10 removal edit swallowed the adjacent P07 ntfy row.
   All caught by re-viewing — but I should stop doing surgical multiedits on
   blocks whose full span I haven't re-read. Rule: one logical edit, verify the
   span, re-view after.
2. **Guessed Android nullability despite the "never guess APIs" lesson:**
   passed `null` into `@NonNull`-annotated callback params → compiler-intrinsic
   NPEs → 2 avoidable red test rounds. javap discipline existed; I skipped it
   for params, only for classes.
3. **`groupValues` ""-vs-null bug:** cost one red round; my isolation attempt
   (kotlinc) wasn't on PATH and I theorized instead of re-deriving from the
   failure message. One wasted cycle.
4. **Emulator lifecycle fumbles:** (i) first detached boot forgot
   `ANDROID_AVD_HOME` (the smoke script I had READ exports it) → "Unknown AVD
   name"; (ii) `INSTALL_FAILED_UPDATE_INCOMPATIBLE` on run 1 — should
   uninstall-first from the start (the fix is now in the script); (iii) I
   trusted `setsid` to keep the emulator alive — it got reaped TWICE (~2 min
   lifetime) before I redesigned the script to own the emulator start-to-finish.
5. **`work/` scratch in a daemon-swept repo, fenced too late:** the auto-commit
   daemon swept emulator PNGs and a 10k-line logcat (plus a probe.sh copy)
   into git history; my push published them. Now `work/` is gitignored +
   untracked, but the blobs live in remote history forever (private repo,
   tolerable size — still my mess: scratch output never belonged in an
   unignored dir here).
6. **Screenshot dir ordering bug:** `mkdir -p work` ran AFTER the script whose
   redirect needed it — first-run evidence lost (see b3).
7. **Lucky coordinate:** the W103 Retry tap at hard-coded (540,1380) worked,
   but it was a guess, not located from view bounds — the script is brittle
   across layouts/densities. Should use `uiautomator dump` to find the button.

## e) WHAT WE SHOULD IMPROVE

1. **Scratch-output discipline:** any new output dir must be gitignored IN THE
   SAME change that creates it, in daemon-swept repos. (Generalize: "the
   daemon commits within seconds — treat the working tree as publishable at
   all times.")
2. **Multiedit hygiene:** never span unrelated rows in one old_string; re-view
   the target region immediately before and after every multiedit (three
   strikes this session proves the rule).
3. **API verification includes PARAMETER nullability**, not just method shape:
   javap `-v` annotations or write the call and let the first compile answer.
4. **Emulator work is script-owned, never shell-owned:** detached host
   processes get reaped; `emulator-verify.sh`'s own-your-emulator pattern is
   the keeper. Consider folding the retry-tap locator (uiautomator) in.
5. **Contrast manifest is a RECONSTRUCTION:** the 10:00 audit's 31-pair
   transcript lived in a session log that isn't in the repo; my 32 pairs are
   coverage-equivalent by design, not the same list. Future audits: land the
   pair list as a file at audit time.
6. **W110 wiring gap, named honestly:** `setDownloadListener` attachment is
   review-trusted glue (only `handleDownload` is directly tested) — acceptable
   per the file's philosophy, but a Robolectric `getDownloadListener()` hook
   test would close it cheaply.
7. **Cookie-header injection untested:** ShadowDownloadManager doesn't expose
   request headers, so "same-origin download carries the cookie" is verified
   by code read + enqueue count, not bytes. If this matters, assert via a
   local HTTP stub someday.
8. **Docs split-brain guard:** P06–P10 outcomes now live in BOTH the plan
   errata and TODO_LIST (snapshot vs live-home — by convention OK, but the
   next docs-health pass should confirm no drift crept in).

## f) NEXT — up to 50, ranked by impact (session-derived; P-IDs = 10-17 plan)

1. Owner: fix GitHub billing/spending limit → watch the FIRST truly green CI run (P08 completion).
2. Owner: deploy SystemNix → attaches the Forgejo→GitHub push mirror (P01).
3. Owner: re-scope the sync PAT with `workflow` scope (P01; unfreezes ci.yml mirror pushes).
4. Live-stack session (test extension) → unlocks W104 attach, W105 links, W106 back-nav (P06 residual, ~60min).
5. With a session: ntfy live kill-cycle — configure server/topic, `am kill`, prove sticky resubscribe (P07/F035–F036).
6. With a session: the answer-path race measurement, push→FSI vs INVITE (P05/F017).
7. First device train end-to-end against the live stack (P04).
8. Robolectric hook test: `ShadowWebView.getDownloadListener()` fires `handleDownload` (W110 wiring closure).
9. Replace the hard-coded retry tap with `uiautomator dump`-derived coordinates in `emulator-verify.sh`.
10. Add `scripts/contrast-check.py` to AGENTS "Build and verify" command block (currently only in Gotchas + CI).
11. README parity check: downloads work, HTTP-error policy, contrast gate (README sells contracts).
12. Land the 31-pair audit transcript as a file if it can be recovered from the 10:00 session log.
13. Auth-ladder soak completion (P13 — concurrent session started it; their `SipEngineOverWireTest` was red mid-flight at 13:33, check `f4c9ca4` state).
14. Logout deregistration decision: KEEP vs PURGE push-device rows (P12/F045).
15. WebView UX tail: photo-picker polish, predictive back, console gating, overlay re-render (P14/W130–W133).
16. Regression batch: ring_end-after-answer, topic rotation, double-REGISTER storm (P16/W150–W152).
17. `runCatching`-swallow sweep + TelecomOutgoing generation cleanup (P17/W160–W161).
18. CallStyle fallback test + RegistrationFailure surfacing + duration subtitle (P15/P19).
19. `emulator-train.sh --release` R8 smoke (P21/F085).
20. Docs-health big pass: FEATURES flips, status index, AGENTS refresh after two trains (P23).
21. v0.1.0 tag + CHANGELOG cut (P11 — awaiting operator timing).
22. `wp_on_danger` values: mint passing pairs the day a filled danger surface appears (manifest note).
23. Battery-exemption ask (F052) emulator verify — code shipped, verify pending.
24. Mirror-lag watch cadence: `scripts/mirror-lag.sh` after pushes until the mirror attaches.
25. Contrast: add pairs for any NEW colored surface as it lands (rule now in AGENTS).
26. Island console noise: Permissions-Policy warnings ('payment'/'usb' unrecognized) surface per page load in logcat — consider filtering in `onConsoleMessage`.
27. Consider asserting DownloadManager cookie bytes via a local stub HTTP server (e7 above) if MMS saves matter operationally.
28. Emulator verify: add a CallActivity dark-mode shot when a live call is drivable (call screen is the always-dark surface).
29. Gate bump: the verification bar is now 60 tests — update anywhere "55" is still cited (plan/AGENTS say 55 in places; CHANGELOG updated).
30. Next contrast re-audit date: none needed (CI-gated now) — remove any stale "audit" TODO if one exists.

## g) Questions I CANNOT answer myself

1. **GitHub billing:** when will the account payment/spending limit be fixed
   (or should this repo move CI to the Forgejo runner instead)? CI stays red
   on every push until then — the workflow itself is ready and verified.
2. **Live-stack credentials:** may I use (or be given) a test extension +
   password against pbx.artmann.tech for the logged-in verification pass
   (W104/W105/W106 + the ntfy kill-cycle)? Without them those stay blocked.
3. **Push scope judgment call:** my push published daemon-swept, mid-flight
   test files of the concurrent session (their work, now superseded by their
   own `f4c9ca4`). Keep pushing both remotes at session end regardless, or
   hold pushes when the tree carries another session's un-committed work?

---

_Written by the P06–P10 session; auto-commit daemon will sweep this file. Not manually committed (harness rule: no unprompted commits). Awaiting instructions._
