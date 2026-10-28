# Session-End Lockstep, Split Brains Found On Sight, and the Honest Ledger (2026-10-28 23:02)

Session window: 2026-10-28 22:31 → 23:02 CET — the completion of the
session-end procedure the previous report shipped mid-flight, plus the
self-critique pass the operator ordered. Companion to
[the 22-31 report](2026-10-28_22-31_superb-agent-track-j05-j16-webphone-21-and-m050-wedge.md)
(the agent-track execution itself is documented THERE — this report
covers the close-out and what the close-out's cross-checking surfaced).

## a) FULLY DONE

| # | Work                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Evidence                                                                                         |
| - | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| 1 | **Daemon blobs squashed safely — the lesson APPLIED this time**: a second blob (`9337124`, the report file) had stacked on `3f8c030` (the drafts file); BOTH were verified session-only (`git show --stat`) AND remotes-lag was checked BEFORE any reset (`git rev-list --count` = 9, both remotes at `befe000`)                                                                                                                                                                                                                                                                                                                                  | The over-broad-squash incident from §d of the 22-31 report cannot recur while this order is kept |
| 2 | **Soft-reset to `dd2fb12` + fold**: drafts file + report file staged cleanly, no third party's paths touched                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | `git status --short` showed exactly the two expected files                                       |
| 3 | **README status-index row** inserted above the 10-12 row (fresh view first — the dprint-reflow discipline held; no bounced edit this time)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | `docs/status/README.md` live-reports table, newest-first order preserved                         |
| 4 | **Session-end commit `51a6e7a`**: report + draft + index row, detailed message, attribution trailer                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | `git log`; 3 files, 205 insertions                                                               |
| 5 | **Both remotes pushed and VERIFIED in lockstep**: `origin/main` = `github/main` = `HEAD` = `51a6e7a`; the nine-commit local backlog (7 real + docs commit + blob remainder) is GONE                                                                                                                                                                                                                                                                                                                                                                                                                                                               | `git rev-parse origin/main github/main HEAD` printed one SHA three times                         |
| 6 | **Test count verified MECHANICALLY, not from memory**: `grep -rc "@Test"` across `app/src/test` = **97**, matching the corrected claim in `dd2fb12`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | The count-from-source lesson (same class as the coverage cross-check) actually executed          |
| 7 | **THREE split brains found and fixed on sight (commit `e4c8dff`)** — see §d #4 for how they got there: (i) AGENTS + FEATURES both claimed "94 tests" (stale by 3 since J16); (ii) AGENTS claimed CI triggers are `pull_request`/`workflow_dispatch` only and "never run", while `ci.yml` declares `push: branches: [main]` and FEATURES already recorded the newer truth (runs fire, jobs billing-blocked); (iii) three hard-won lessons from this arc existed ONLY in commit messages — now recorded in AGENTS: rev-list-count before ANY reset, meta-test every new wedge-pin, capture `Files` by value inside `run { }` under the config cache | `e4c8dff` diff; `grep -n -A6 "^on:" .github/workflows/ci.yml`; count re-verified mechanically    |
| 8 | **Hand-commit cadence applied**: the reconciliation unit was committed immediately after dprint, not left for the daemon                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | `e4c8dff` landed 23:00; zero new blobs since                                                     |
| 9 | Archived/planning files containing "94 tests" were deliberately NOT touched — point-in-time snapshots stay as written (the archive convention)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | Grep hits in `docs/status/archived/*` and the planning file left alone                           |

## b) PARTIALLY DONE

| # | Work                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | State                        |
| - | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------- |
| 1 | **Full verify-gate on the final tree (prior §f #1)**: STILL not run — and the debt is now PUSHED: nine commits reached both remotes without the six-step wrapper ever seeing the final stack. Per-step equivalents (tests, ktlint, dprint) each ran green after their commits, so confidence is decent, but the honest statement is: deferred verification is now on the canonical remote. If the gate finds anything, the fix rides on top — no history rewrite, no force-push | Next session's FIRST command |
| 2 | **TODO_LIST harvest**: two reports' §f sections (30 + 34 items) have not been harvested into `TODO_LIST.md`; the status-report skill's standing rule says §f belongs in the living TODO docs, not entombed in timestamped files. Deferred per the operator's report-then-wait order — harvesting is new work beyond "write the report and wait"                                                                                                                                 | §f #3                        |
| 3 | **M049 pointer into TODO_LIST's cross-repo row** (webphone#21 filed): carried from the prior report, still pending — same deferral                                                                                                                                                                                                                                                                                                                                              | §f #4                        |

## c) NOT STARTED

- **The plan's gated 43 rows — unchanged, exactly as gated**: M001-M008
  (owner infra: SystemNix deploy, PAT re-scope, Actions billing fix,
  release token, install/Obtainium), M009-M020 (device trains, gated on
  Q1), M032-M036 (release cut, CI evidence, M036's g1 gate), M054-M085
  (live contracts, logged-in tail, logout semantics, standdown, device
  batches, meta, post-train docs, decisions, the quiet 20%).
- **M050 re-run**: environment proven Wednesday (emulator boots, /dev/kvm
  present), the consent-Cancel → IdP-error → detour-clear leg never
  executed. Awaiting the fixed harness + an operator window.
- **M035 CI rehearsal**: the workflow has STILL never executed a single
  job (runs fire on push since the push trigger exists; every run dies
  at the billing gate before a job starts). First real execution is
  gated on the owner's billing fix (M001).

## d) TOTALLY FUCKED UP (own mistakes; new ones small, old ones carried)

1. **The index row I wrote went stale within FOUR MINUTES.** The README
   row says "7 commits local, unpushed" — and pushing was the very next
   step of the same procedure. The point-in-time convention technically
   covers it, but I KNEW the push was imminent and shipped a
   self-staling sentence anyway. The fix costs one clause ("pushed as
   `51a6e7a` minutes after this row was written"); this report is that
   correction. Same pattern the project polices elsewhere: docs that
   lie at birth.
2. **The blobs stacked because my cadence invited them.** The drafts
   file sat uncommitted for ~7 h (15:28 → 22:33); the report file got
   blobbed within minutes of writing. Two squashes were avoidable with
   the hand-commit habit that §e names — and which today's step 8
   finally applied.
3. **Carried from the arc, still counted**: M050's 7 h tail-piped wedge
   (my own recorded gotcha, violated the same day); the over-broad
   squash (reset to a lagging origin without counting); the vacuous
   stop-leak pin that shipped green-while-broken for ~20 min; the
   95-test miscount read from memory; two 404 URL guesses. All
   documented in the 22-31 report §d with repairs; nothing new of that
   severity today.
4. **MY ARC SHIPPED THE DOC SPLIT BRAINS AND LEFT THEM.** Three commits
   yesterday/today changed the test count (94 → 97) and the CI story;
   nobody reconciled AGENTS' header or FEATURES' row. The CI split was
   the worse one: AGENTS asserted a trigger set that contradicts the
   workflow file IN THE SAME REPO, and FEATURES already held the
   correct billing-blocked story — a textbook split brain (same concept,
   two places, drifting). Caught today ONLY because the self-review
   pass demanded a mechanical grep + trigger check. Both fixed in
   `e4c8dff`. The meta-lesson: any commit that changes a count, a
   trigger, or a claim must grep the header docs for the OLD value
   before finishing.
5. **Did I lie to you?** No. Every load-bearing claim made to you this
   window (SHAs match, tree clean, 97/97, blobs session-only) was
   mechanically verified BEFORE it was said. The one sloppy claim in
   the arc (the 95-count) was already amended before push and is
   re-verified today from source: 97.

## e) WHAT WE SHOULD IMPROVE

1. **Hand-commit the moment a unit is finished** — the drafts file's
   7 h nap invited both blobs; today's immediate-commit habit produced
   zero blobs. Make it reflexive, not occasional.
2. **Verify-gate per logical unit, not per session** — the wrapper is
   ~5 min up-to-date-cached; running it per unit means the
   "final tree never saw the whole gate" class (§b #1) cannot exist.
3. **Claim-drift grep**: before finishing ANY commit that changes a
   number or behavior claim (test count, trigger set, version,
   API level), grep the living docs (AGENTS, README, FEATURES,
   TODO_LIST, CHANGELOG) for the old value. Both of today's split
   brains were findable in one grep each.
4. **Keep the two new AGENTS disciplines visibly**: rev-list-count
   before ANY reset (applied today, worked), meta-test every wedge-pin
   (caught yesterday's vacuous design). They are recorded now — reread
   them at session start.
5. **Self-staling report prose**: when a report is KNOWN to be
   superseded minutes later by the same session's own next step, say so
   inline instead of shipping a sentence that is false before the push
   output finishes printing.
6. **The "v0.1 scaffold" status line** (README + AGENTS) vs `version.txt`
   0.2.0: still unreconciled — deliberately, because writing it
   truthfully depends on the release-first answer (§g #2). Queued, not
   forgotten (the sibling report's §f #49 tracks it too).

## f) NEXT (ranked, ≤ 50 — a brainstorm, not a commitment)

| #  | Thing                                                                                                                                                                                                            | Why                                                                                          | Effort        |
| -- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- | ------------- |
| 1  | FIRST next session: `bash scripts/verify-gate.sh` over the final tree                                                                                                                                            | Close §b #1 — the gate debt is pushed; prove the pushed stack green end-to-end               | 5 min         |
| 2  | M050 re-run with the fixed harness: emulator up, `adb root`, deep-link `begin`, `uiautomator dump` for Cancel bounds, logcat-watch `auth detour cleared`, output to a LOG FILE, boot/install phases time-bounded | The last agent-track row; credential-free; the 7 h wedge must not recur                      | 30 min        |
| 3  | HARVEST both reports' §f into `TODO_LIST.md` (docs-health HARVEST mode)                                                                                                                                          | §f belongs in the living TODO docs, not entombed; two reports' worth are piling up           | 30 min        |
| 4  | Add the M049 pointer into TODO_LIST's cross-repo row (webphone#21 filed)                                                                                                                                         | The contract note exists only in the draft + the forge                                       | 5 min         |
| 5  | Operator: answer §g (daemon policy, release-first, M050 end-state)                                                                                                                                               | Three decisions block rows                                                                   | 5 min owner   |
| 6  | M001-M003 (owner): SystemNix deploy + PAT re-scope + **Actions billing fix**                                                                                                                                     | Highest-leverage unlock; billing now blocks ALL CI evidence (runs fire, no job ever started) | 35 min owner  |
| 7  | After M001: force one `workflow_dispatch` run and archive its log as the FIRST CI evidence                                                                                                                       | Every CI claim in AGENTS/FEATURES is currently unproven by execution                         | 10 min        |
| 8  | M032-M033 (owner): Forgejo release token + cut v0.2.0 via `scripts/release.sh`                                                                                                                                   | The update lane stays UNPROVEN until the first real publish                                  | 20 min owner  |
| 9  | M005-M008 (owner): physical install + Obtainium onboarding                                                                                                                                                       | First device truth; retires the "debug-vs-release package" uninstall step                    | 45 min owner  |
| 10 | Q1 (operator): test extension + second account → unblocks M009-M020 trains                                                                                                                                       | The core promise (outbound + ring) is still unproven on hardware                             | 10 min owner  |
| 11 | M054-M056 (live contracts) after Q1                                                                                                                                                                              | Field-name drift surfaces at first boot                                                      | 60 min BLK    |
| 12 | M057-M062 (logged-in tail + live detour pass) after Pocket ID credential                                                                                                                                         | W104-W106 + in-shell SSO verified end-to-end                                                 | 60 min BLK    |
| 13 | M063-M064 (assetlinks served + verify-app-links)                                                                                                                                                                 | Deep-link UX without the chooser                                                             | 30 min        |
| 14 | M065-M067 logout semantics (KEEP-vs-PURGE, then implement)                                                                                                                                                       | Ghost rows in the push registry                                                              | 60 min BLK    |
| 15 | M068-M070 island-standdown + safe-area fix                                                                                                                                                                       | Cross-repo seams                                                                             | 100 min BLK   |
| 16 | M071-M073 device-verify batch (CallStyle, TalkBack, battery)                                                                                                                                                     | Post-train honesty retirement                                                                | 60 min BLK    |
| 17 | M076-M078 post-train docs sweep + CHANGELOG cut                                                                                                                                                                  | README truth                                                                                 | 100 min BLK   |
| 18 | M079-M080 the 8 ROADMAP Open questions + visibility end-state                                                                                                                                                    | Head/repo drift                                                                              | 45 min owner  |
| 19 | M082 AGENTS slim-down < 30 KB (g3) — grew again today (+3 bullets)                                                                                                                                               | The gotcha list keeps inflating; compression needs its own session                           | 60 min gated  |
| 20 | M083-M085 targetSdk 37, API 37 image, mockwebserver3                                                                                                                                                             | The quiet 20%                                                                                | gated/tail    |
| 21 | Fold M050's verdict into the AGENTS auth-detour gotcha                                                                                                                                                           | The detour's clear-path is unit-pinned only until then                                       | 10 min        |
| 22 | Pre-push hook (opt-in) running `verify-gate.sh`                                                                                                                                                                  | §e #2 enforcement                                                                            | 20 min        |
| 23 | `org.gradle.configuration-cache.problems=fail` once ktlint's violations clear                                                                                                                                    | Warn mode is load-bearing today                                                              | 15 min        |
| 24 | Watch the ktlint plugin for the `setVisible` fix; bump when released                                                                                                                                             | Kills the last Gradle-11 deprecation                                                         | 5 min ongoing |
| 25 | Gate step that fails on ANY new Kotlin compiler warning                                                                                                                                                          | The count is zero; freeze it                                                                 | 30 min        |
| 26 | Emulator smoke script hardening: boot/install timeouts + mandatory log-file output                                                                                                                               | §d #3's wedge becomes structurally impossible                                                | 20 min        |
| 27 | M035 local CI rehearsal (clean env, no signing props)                                                                                                                                                            | First real CI run should not be the first time anyone executes the workflow                  | 30 min        |
| 28 | Annotate + archive the sibling's three live reports (M081/g2) once their session ends                                                                                                                            | The archive's own rule; they are stale                                                       | 20 min        |
| 29 | Reconcile the "v0.1 scaffold" status line vs version.txt 0.2.0 (after §g #2)                                                                                                                                     | §e #6 — write it truthfully once the release decision exists                                 | 10 min        |
| 30 | After the mirror attaches (J01): verify Forgejo→GitHub keeps up (SHA-lag probe)                                                                                                                                  | Then hand-pushes stop                                                                        | 10 min        |
| 31 | Meta-test discipline as a checklist line in the PR/commit template                                                                                                                                               | §e #4 institutionalization                                                                   | 10 min        |
| 32 | Consider `forkEvery` cost audit (fresh JVM per class is wedge-proof but slow — measure the suite wall-time trade)                                                                                                | The fence bought safety; know its price                                                      | 15 min        |
| 33 | Sweep `docs/drafts/` convention into AGENTS (drafts live in-repo, never /tmp — currently tribal knowledge)                                                                                                       | Memory discipline                                                                            | 5 min         |
| 34 | Re-count tests from source at every session start (one grep) until a gate step does it                                                                                                                           | Cheap honesty anchor; today it caught nothing new but proved 97                              | 1 min         |

## g) QUESTIONS FOR THE OPERATOR (cannot self-answer — carried from 22-31, still open)

1. **Daemon policy**: the daemon blobbed the arc five times and my
   uncommitted files twice more tonight. Is the amend/squash remedy +
   faster hand-commit cadence the accepted steady state, or do you want
   the daemon disabled/paced for this repo (a SystemNix knob) while
   agent sessions run?
2. **Release-first?**: `version.txt` claims 0.2.0 while the release lane
   is unpublished and the whole agent-track just landed as code. Cut
   v0.2.0 NOW (needs the Forgejo token, M032/M033) so Obtainium's first
   update cycle starts from a real baseline — or let more work pile
   onto 0.2.0 first? (Also decides when the "v0.1 scaffold" status line
   can be rewritten truthfully.)
3. **M050's expected end-state**: when the consent Cancel fires and the
   IdP errors back to the origin, the CURRENT design commits the
   origin, clears the detour, and renders the IdP's error page
   in-shell. Keep that, or auto-retry/return to the island home
   instead? It decides whether M050 is a verification pass or a design
   change.
