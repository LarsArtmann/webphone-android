# Annotation, Push, and Concurrent-Session Fixes — Status & Self-Review (2026-10-25 14:33)

**Session scope:** the post-handoff execution window (~14:05 → 14:33 CET):
deliver the 13:56 SUPERB-pass report, execute W193 (annotate the superseded
06-38 pareto plan), push everything to both remotes, re-verify CI and the
mirror, and close out the whole todo list. One live concurrent agent session
detected and accommodated (its 13:43 report was unformatted + unindexed —
fixed). Per operator order: report, then WAIT.

**Snapshot facts at 14:33:** local `main` = `origin/main` = `github/main` =
`f585c6d` (verified equal); `scripts/mirror-lag.sh` → "mirror OK"; working
tree clean; repo-wide `dprint check` clean; CI red ×2 on billing (owner-side,
unchanged); 72-test JVM gate + zipalign + contrast gate still the 13:41 green
(only `.md` files changed since — code untouched this window).

## a) FULLY DONE

- **13:56 report delivered** (`59126d8`): dprint-formatted, indexed in
  `docs/status/README.md` (newest-first preserved), real-message commit beat
  the daemon.
- **W193 executed end-to-end** (`fa7f144`): all 51 done-rows of the 06-38
  plan's fine table struck with per-cell markers + evidence text; the 41
  still-open rows (trains, Q1, billing, tag, owner calls) left bare by design
  under a `<!-- check-rows:ledger -->` exemption; `check-rows` reports
  complete; dprint clean.
- **Every W193 citation verified BEFORE writing** (not trusted from the
  handoff): 10-00 §a actually documents T06-T24 (lines 34-55 confirmed),
  ROADMAP Decisions actually contains TLS / API 31-33 / CallEndpoint /
  WorkManager / crash-reporting / snapshots / WebView bullets, `3b3d723`
  really touched ROADMAP.md + AGENTS.md, `LICENSE`, `scripts/mirror-lag.sh`,
  `scripts/emulator-photos.sh` exist, `--release` smoke mode is in
  `emulator-train.sh`, `local.properties` is gone.
- **Both remotes pushed twice; tips verified equal** (`fa7f144`, then
  `f585c6d`); mirror-lag probe green after each push.
- **CI re-verified after the push:** both new runs fire on `push` and die in
  14-18 s with ZERO steps executed and no log produced — the account-billing
  block, exactly as diagnosed in P08. No workflow defect; owner fix pending.
- **Owner-blocked rows confirmed visible** in `TODO_LIST.md`: billing (L28),
  Q1 device train (L29) + race (L31) + logged-in pass (L32), v0.1.0 tag
  timing (L43).
- **Concurrent session's 13:43 report rescued** (`f585c6d`): it was committed
  but had NEVER been dprint-formatted (repo CI runs `dprint check` — a
  guaranteed red once billing lands) and was missing from the status index.
  Formatted + indexed.
- **Good news discovered:** `git log 28ba2be..f585c6d -- .github/` is EMPTY —
  the eventual Forgejo push-mirror sync of this range will NOT trip the PAT
  workflow-scope trap (no workflow-file changes in the unmirrored window).

## b) PARTIALLY DONE

- **CI pipeline (F041):** decision (GitHub Actions), trigger, firing runs —
  done. First GREEN run — blocked on the owner's billing fix. Every push
  burns one red run until then. **→ TODO_LIST (R13/N029)**
- **Mirror train (P01):** direct `github` pushes keep the two remotes equal,
  but the Forgejo→GitHub push MIRROR itself is still not attached
  (SystemNix phase-5, deploy-gated). My direct pushes mask the gap; the
  moment someone pushes only to Forgejo, lag returns silently. **→ TODO_LIST (R12/N025)**
- ~~**W193 scope:** the 06-38 plan is annotated, but the SUPERSEDING 10-17
  SUPERB plan has 27 P-rows and ZERO strikes — its table still reads
  all-open even though the 13:56 report documents the outcomes. A W193-b is
  warranted (agent-executable now).~~ done — THIS sweep (2026-10-28) annotated the 10-17 plan row-by-row
- ~~**TODO_LIST harvest of this report's (f):** pending — operator ordered
  report-then-wait; items below are not yet routed into TODO_LIST/ROADMAP.~~ done — routed via the 10-17 + 15-17 plan harvests; this sweep re-harvested the remainder

## c) NOT STARTED

- **Agent-executable now:** ~~annotate the 10-17 SUPERB plan (27 bare P-rows);~~
  done this sweep; ~~docs-health skill note on the status-items × dprint hazard;~~
  landed upstream in SKILL.md; ~~a prepared
  F041 "first green run" verification checklist for the moment billing
  lands.~~ NOT-DO — no checklist needed; watch it at R13.
- **Q1/live-stack gated (largest block):** first device train (T04/T05),
  answer-path race measurement (F017), logged-in emulator pass
  (W104/W105/W106), live contract checks (session fields, payload shapes,
  CSRF), CallStyle device verify (F063), TalkBack + battery allow/deny on
  device, AEC/NS hardware check (F081), R8 release real-call verify (F082),
  FreeSWITCH CANCEL-vs-BYE confirm (F084), physical install + first-run
  walk, logout KEEP-vs-PURGE (F045/F046), island-standdown seam (P24).
- **Owner-only:** Actions billing, PAT `repo`+`workflow` scope + SystemNix
  deploy (also unfreezes `domains` at 114 behind), Forgejo repo description,
  icon parity (both-or-neither with webphone), FCM lane drop-in, v0.1.0 tag
  timing.
- **Post-train docs sweep:** honesty-contract marker retirement + next
  CHANGELOG cut (F087-F089).

## d) TOTALLY FUCKED UP

1. **The dprint × status-items mauling (the real screw-up).** I annotated the
   06-38 plan with `status-items`, whose whole-line strike form
   (`~~row~~ verdict`) is UNSAFE in a repo where dprint is a CI gate. dprint
   reformatted the table: verdict text DESTROYED, strike marker shoved into
   the first cell, last column dropped — and the auto-commit daemon swept
   the damaged state into history (`b5faa5e`) before I noticed. Three
   compounding failures: (a) I used a tool output format I had not verified
   against the repo's formatter — I read the tool source only AFTER the
   damage; (b) I violated the session's own canon "commit in the SAME
   command chain as the mutation" for the fourth time across both windows;
   (c) no one-row canary before a 51-row batch. Recovery was clean and
   forward-fixed (pristine blob from `eeafea6`, the `rows` subcommand's
   per-cell strikes which DO survive dprint, ledger exemption, `fa7f144`)
   — but `b5faa5e` is a permanent wart in history.
2. **README write race with the live concurrent session:** the file's
   mod-time changed 6 s after my read, bouncing my first index edit. No
   damage — their change was just a dprint re-alignment and I re-viewed
   before retrying — but with two live sessions this is one unlucky
   interleaving away from a silently clobbered index row.
3. **Minor:** double `v:v:` kind prefix in my spec→rows conversion (caught
   on head-inspection before use); one `check-rows` invocation from the
   wrong cwd (re-ran); nothing else.

## e) WHAT WE SHOULD IMPROVE

1. **Multi-session coordination is the top systematic risk.** Two agents
   committed to this repo within the same hour (13:43 + 13:56 reports, the
   README race, the earlier contrast split-brain). Cheap mitigation: a
   session-claim convention (a `.session-claim` note or a fixed claim file)
   checked before writes, or disjoint file-set claims per session recorded
   in a comment. Needs an operator nod — it is a convention, not code.
2. **Tool-interaction canaries:** whenever a batch annotation tool meets a
   repo formatter, dry-run the FORMATTER on ONE mutated row before applying
   the batch. The annotate toolchain has two strike shapes; only one
   survives dprint. One canary row would have saved the entire d)1.
3. **Same-chain commits, enforced by habit:** any mutation the daemon can
   sweep must end with `git commit` inside the same bash invocation. This
   is already canon; it needs to be non-optional muscle memory.
4. **Push the hazard upstream:** one paragraph in the docs-health SKILL.md
   ("status-items output is dprint-unsafe on tables; use `rows` per-cell
   strikes in formatter-gated repos") prevents the next session from
   repeating d)1. Small, skill-repo change.
5. **Report delivery is a 3-step chain** (fmt + index + commit): the
   concurrent session skipped two of three. A pre-commit `dprint check`
   hook would make the omission impossible instead of ritual.
6. **History wart accepted, not rewritten:** `b5faa5e` contains the mangled
   table. Private repo, forward-fixed, `filter-repo` not worth the risk.
   Recorded so nobody "discovers" it and panics.

## f) TOP 25 NEXT (ranked; tier 0 is agent-executable RIGHT NOW)

| #  | Task                                                                                                | Status         | Effort | Gate / Source                            |
| -- | --------------------------------------------------------------------------------------------------- | -------------- | ------ | ---------------------------------------- |
| ~~1~~  | ~~Annotate the 10-17 SUPERB plan (27 bare P-rows) with 13-56/13-43 report evidence (W193-b)~~ done — THIS sweep (2026-10-28) — the 10-17 plan is annotated row-by-row in the same pass | ~~agent-ready~~ | ~~30min~~ | ~~this session's discovery; `rows` pattern~~ |
| ~~2~~  | ~~Add the status-items × dprint hazard note to docs-health SKILL.md~~ done — landed upstream — docs-health SKILL.md now carries the dprint-damage triage + rows-shape guidance (formatter-gated repos) | ~~agent-ready~~ | ~~10min~~ | ~~d)1 lesson; skill repo~~ |
| ~~3~~  | ~~Prepare the F041 first-green-run checklist (gh run watch + job/artifact checks) for billing-landing~~ **NOT-DO — the first green run needs no pre-written checklist — watch it when billing lands (R13/N029).** | ~~agent-ready~~ | ~~15min~~ | ~~a) CI rows~~ |
| ~~4~~  | ~~Session-claim convention (multi-agent write coordination) → AGENTS.md~~ **NOT-DO — never adopted — operator nod never came; the freshness-check habit in later reports covers it.** | ~~needs nod~~ | ~~20min~~ | ~~d)2 / e)1~~ |
| ~~5~~  | ~~Harvest this report's (f) into TODO_LIST/ROADMAP~~ done — via the 10-17 + 15-17 plan harvests; this sweep re-harvests the remainder (R28/R30 lists) | ~~after wait~~ | ~~10min~~ | ~~docs-health HARVEST~~ |
| 6  | Owner: fix GitHub Actions billing (failed payment / spending limit) **→ TODO_LIST (GitHub billing fix, R13/N027)** | BLOCKED owner  | 10min  | F041; TODO_LIST L28                      |
| 7  | Owner: Q1 — test extension + account on `pbx.artmann.tech` **→ TODO_LIST (Q1 test extension + account — the standing blocker)** | BLOCKED owner  | ?      | gates rows 11-20                         |
| 8  | Owner: v0.1.0 tag timing call (F074b) **→ TODO_LIST (v0.1.0 annotated tag, R24/N062)** | BLOCKED owner  | 5min   | TODO_LIST L43                            |
| 9  | Owner: SystemNix deploy → phase-5 push-mirror attach **→ TODO_LIST (Owner: SystemNix deploy → phase-5 attach, R12/N025)** | BLOCKED owner  | 10min  | P01; TODO_LIST L26                       |
| 10 | Owner: regenerate sync PAT with `repo`+`workflow` (sops) — unfreezes domains (114 behind) **→ TODO_LIST (Owner: PAT repo+workflow, R12/N026)** | BLOCKED owner  | 15min  | P01; TODO_LIST L27                       |
| 11 | First device train: outbound + inbound end-to-end vs live stack (T04/T05) **→ TODO_LIST (first device train, R15/R16)** | BLOCKED Q1     | 1 day  | TODO_LIST L29                            |
| 12 | Measure the answer-path race: push → FSI vs SIP INVITE (F017) **→ TODO_LIST (Verify the answer-path race, R16/N041)** | BLOCKED Q1     | 2h     | TODO_LIST L31                            |
| 13 | Logged-in emulator pass: composer attach, external-link hand-off, back stack (W104-W106) **→ TODO_LIST (logged-in emulator pass, R19/N049-N051)** | BLOCKED Q1     | 30min  | TODO_LIST L32                            |
| 14 | Live contract checks: `/api/session` fields, payload shapes, CSRF dance **→ TODO_LIST (Live contract checks, R20/N052-N053)** | BLOCKED Q1     | 1h     | TODO_LIST L39                            |
| 15 | CallStyle device verification, both trains (F063) **→ TODO_LIST (CallStyle device verification, R23/N059)** | BLOCKED trains | 30min  | TODO_LIST L41                            |
| 16 | TalkBack device walkthrough + battery-prompt allow/deny **→ TODO_LIST (TalkBack + battery allow/deny, R23/N060-N061)** | BLOCKED device | 1h     | TODO_LIST L42                            |
| 17 | AEC/NS engages on real hardware (audio check, F081) **→ TODO_LIST (AEC/NS on hardware, R15/N037)** | BLOCKED trains | 10min  | 06-38 T26                                |
| 18 | R8 release build: real-call verify of webrtc consumer rules (F082) **→ TODO_LIST (R8 keeps vs a real release call, R15/R16)** | BLOCKED trains | 12min  | 06-38 T26                                |
| 19 | Confirm FreeSWITCH sends CANCEL (not BYE) on caller hangup (F084) **→ TODO_LIST (FreeSWITCH CANCEL-vs-BYE, R16/N038)** | BLOCKED stack  | 10min  | 06-38 T26                                |
| 20 | Physical install + first-run walk of the redesigned build **→ TODO_LIST (First physical install, R14/N030-N032)** | BLOCKED phone  | 30min  | TODO_LIST L30                            |
| 21 | Logout deregistration: KEEP-vs-PURGE + JS-bridge vs cookie-poll (F045/F046) **→ TODO_LIST (Logout deregistration, R21/N054-N056)** | BLOCKED owner  | 1h     | TODO_LIST L38                            |
| 22 | Island-standdown seam ("native shell present" signal) **→ TODO_LIST (island-standdown seam, R22/N057-N058)** | BLOCKED nod    | 2h     | TODO_LIST L40                            |
| 23 | Post-train docs sweep: honesty retirement + next CHANGELOG cut (F087-F089) **→ TODO_LIST (post-train docs sweep, R26/N066)** | BLOCKED trains | 100min | TODO_LIST L50                            |
| 24 | Forgejo repo description + icon parity (both-or-neither) **→ TODO_LIST (Forgejo description + icon parity, R25/N064-N065)** | BLOCKED owner  | 30min  | TODO_LIST L49                            |
| 25 | FCM lane drop-in when `google-services.json` appears **→ FEATURES (FCM lane DISABLED — awaits google-services.json)** | BLOCKED owner  | 10min  | AGENTS gotcha; 06-38 F090                |

## g) THREE QUESTIONS I CANNOT ANSWER MYSELF

1. **GitHub Actions billing:** will you fix it now (Billing & plans → payment **→ TODO_LIST (GitHub billing fix, R12c/N027 — owner)**
   / spending limit), or is red-CI the accepted state until the trains? It
   gates F041 (first green run) and every future push shows red; nothing in
   this repo can change it.
2. **Q1 provisioning:** can you create the test extension + account on **→ TODO_LIST (Q1 — gates the trains, the logged-in pass, and the contracts)**
   `pbx.artmann.tech` this session? It unblocks the single largest block of
   remaining work (rows 11-14 above) and the answer-path race question that
   has been open since the ring-while-closed design.
3. **v0.1.0 tag:** cut it NOW (CHANGELOG `[0.1.0]` section complete since **→ TODO_LIST (v0.1.0 tag timing, R24/N062 — operator call)**
   10-23; marks the code truth; trains would land in a 0.1.1) or ONLY after
   both trains green (tag = "daily-driver credible" claim)? My recommendation
   is after-trains — the honesty contract is still loaded — but it is your
   timing call.

---

_Written by the post-handoff execution session. Committed immediately per
this session's standing order (daemon races). Awaiting instructions._
