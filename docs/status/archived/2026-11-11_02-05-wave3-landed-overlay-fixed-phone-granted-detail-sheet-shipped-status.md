<!-- Status report: 2026-11-11 ~02:05 CET. Session: "just go fix it all

> **Live remainder:** open §f items live in TODO_LIST.md and ROADMAP.md §6 (this draft is superseded by the 02-04 report; archived 2026-11-11).
     yourself" — the wave-3 batch landed, the overlay bug died, the
     phone got its grants, the docs corpus caught up, and the wake-off
     detail sheet shipped. -->

# Status: wave-3 landed, overlay fixed on-device, phone grants granted, detail sheet shipped

## a) FULLY DONE

1. **Wave-3 batch (verdicts + notices + diagnosis) — committed AND gate-green.**
   The daemon had swept the batch into 8 blobs overnight; origin already held 7
   of them, so NO rewrite (force-push forbidden). The never-run gate ran:
   build+tests green, ktlint red (mechanical — `ktlintFormat`), dprint red on
   the status report (canonicalized), then `GATE EXIT 0`. Pushed BOTH remotes
   (`bb990e4`).
2. **Offline overlay root-cause fix (F29–F33) — emulator-proven, meta-proven,
   gate-green.** Airplane-mode repro CONFIRMED the bug live: the native overlay
   was up but Chromium's raw `net::ERR_INTERNET_DISCONNECTED` page sat under it
   and would flash through on every Retry (dismissal fired at load START).
   Fix: dismissal is SUCCESS-GATED (`WebSurface.mainFrameLoadFailed`; hide only
   on `onPageFinished` for a load that never transport-failed; `retry()`/
   `loadUri()`/`loadOriginIfChanged()` now only re-resolve the body text).
   Re-verified on the emulator: overlay holds through a Retry tap, app alive,
   no crash. Pin meta-proven (break→FAIL→restore→PASS). Commit `9fa3f4a`
   (docs) + code blobs, pushed both remotes.
3. **Phone recon + grants (F23–F28).** Edge 60 online on the emulator adb tree:
   versionName 0.2.0/vc200 ✓. POST_NOTIFICATIONS was **granted=false** and the
   battery whitelist **EMPTY** — both adb-granted (`pm grant`, `dumpsys
   deviceidle whitelist +`) and verified granted. DNS reality recorded: the
   phone's WiFi DHCP hands ISP resolvers (193.47.158.254/255, `play.pl`) —
   `*.home.lan` cannot resolve there (AGENTS gotcha added).
4. **Pixel review (F66–F67).** Pill renders "Not connected" (correct: signed
   out → wiring bails at `resumeSession` BEFORE the lane starts — UNKNOWN by
   design, not a bug); overlay renders; W01 status-bar overlap still present
   (known cross-repo row). Wake-detail sheet pixel-verified after the string
   split (body vs stamp no longer duplicate).
5. **Docs harvest (F70–F74, F84).** TODO_LIST: billing/PAT owner rows enriched
   with exact runbooks, E2E-ring+docs-sweep row, pixel-train row, ntfy
   read-auth row, duplicate billing row collapsed, mirror-lag row updated with
   the OBSERVED-LIVE mirror evidence. ROADMAP: parking theme (P1–P7 + shipped
   note for P8/P9). FEATURES: verdict/refusal/diagnosis/FCM rows + detail-sheet
   row. CHANGELOG: Added/Fixed entries. AGENTS: 9-state ledger + sticky
   refusals, cap-422-not-409, success-gated overlay, rewire-not-ensureWired,
   phone DNS/grants, 1004 channel, hermetic deregister seam, 138-test count.
6. **Wake-off detail sheet (F77–F79) — shipped, pixel-verified.** Tap pill →
   sheet (full sentence + "Last checked" from the new `WakeLane` probe ledger +
   Retry = `CallLayer.rewire` because ensureWired no-ops on a wired-but-refused
   lane; ARMED hides Retry). 4 pins incl. the real pill-tap→sheet→Retry path,
   meta-proven. Full gate `GATE EXIT 0` (138 passes). Squashed the daemon's 5
   blobs into ONE hand-commit `b0aacf4` (verified: session files only, both
   remotes lagged, count printed before the soft reset), pushed both remotes.

## b) PARTIALLY DONE

- Nothing in this session's scope. The two adb trees stayed disciplined (all
  emulator work rode the emulator devShell's adb).

## c) NOT STARTED (owner/user-gated, unchanged)

- F01–F03 Forgejo token → release → bump; F19–F22 Obtainium proof (needs the
  ONE manual uninstall); F04–F06 Pocket ID sign-in; F07–F09 stack switches;
  F11–F13 E2E ring verdict; F80–F83 post-verdict docs sweep; Actions billing;
  mirror PAT `workflow` scope. All rows/runbooks are in TODO_LIST now.

## d) TOTALLY FUCKED UP

1. The previous session's summary claimed "HEAD = a58a68c = both remotes,
   NOTHING committed" — stale by hours; the daemon had already made 8 blobs
   and origin had 7 of them. Lesson: a session handoff MUST print `git log
   --oneline -8` + `git rev-parse HEAD origin/main github/main`, not trust
   memory.
2. I nearly added a duplicate ACTIONS row to TODO_LIST (a Ready billing row
   AND a High billing row existed) — collapsed instead; and one multiedit
   replaced the webphone#21 row instead of appending (caught on re-view,
   restored).

## e) WHAT WE SHOULD IMPROVE

- `emulator-smoke.sh` job 075 STILL shows "running" hours later — its `tail`
  pipe never flushed (the re-committed buffering gotcha) and the script has
  zero timeouts. The Ready row covers the hardening; do it before the next
  emulator session.
- The ktlintFormat→gate→ktlint-red loop cost two full gate runs today; run
  `ktlintFormat` BEFORE the first gate on any new test file.

## f) Things to get done next

1. Owner: Forgejo `write:repository` token → `scripts/release.sh --doctor` →
   publish v0.2.0 → `bump patch` → CHANGELOG stub → push.
2. User: the ONE manual uninstall on the phone → Obtainium install of the
   release-signed build (F19–F22).
3. Owner: stack switches (`push.ntfy.url`, `ringhook.enable`) → then F11–F13
   E2E ring proof with `scripts/ring-diagnose.sh`.
4. Post-verdict: annotate the three 2026-11-10 reports + flip README honesty
   markers (F80–F83).
5. Agent (any session): fold pixel screenshots into `emulator-train.sh`
   (Ready row); emulator script timeouts; warning-freeze gate step.

## g) Questions I cannot figure out myself

1. (Owner) The Forgejo push mirror is ALIVE (an origin push landed on GitHub
   with no hand push). Should the AGENTS "NOTHING pushes automatically" bullet
   be rewritten to "only the mirror pushes, after origin" once
   `scripts/mirror-lag.sh` proves zero lag?
2. (Owner) ntfy subscribe sends NO auth header today — is anonymous topic
   read the intended end-state, or does the stack want the auth-header seam
   (TODO_LIST Low row)?
3. (User) The phone now has POST_NOTIFICATIONS granted + battery whitelist
   via adb. Keep these as the canonical setup (and note them in the
   first-run walk), or do you want the in-app prompts to be the only path?
