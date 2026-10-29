# Self-Critique: Gate Green, the Harvest, and Two Claim-Grep Blind Spots (2026-10-29 01:40)

Session window: 2026-10-28 23:10 → 2026-10-29 01:40 CET. The operator's
order scoped this window to the two pending rows — run the full gate,
then harvest — and then asked the standing question: what was forgotten,
what could be better. Companion to [the 23:30 execution report](2026-10-28_23-30_gate-green-twice-harvest-and-three-more-split-brains.md)
(which documents the work as it happened; THIS report is the adversarial
pass over that work, and it found two things the execution pass missed).

## a) FULLY DONE

| # | Work                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Evidence                                                                                        |
| - | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| 1 | **§f #1 closed: the full gate GREEN twice.** First pass on the pushed stack (assemble 1m51s, tests 2m29s, ktlint+KGP-parity 10s, 16 KB zipalign, 50 contrast checks 0 failures, dprint) — the 23:02 session's deferred-verification debt is retired, and the pushed nine-commit stack is proven end-to-end. Second pass on the harvest tree before the harvest commit (gate-per-unit, §e of the 23:02 report, actually applied)                                                                                                                                                                                                                                                                   | Gate logs (`GATE GREEN` banner, both runs); commit `523a566` → `bda6d32` all gated              |
| 2 | **The fresh maintenance blob was folded per the remedy, not blindly**: `757ce6e` ("auto-commit 11 changed file(s)") was NOT my session's paths — read line by line first: formatter-only pass over all 8 scripts (tabs → 4 spaces, trailing pipes, long lines wrapped; `bash -n` green ×8, `py_compile` green, `git diff -w` zero semantic change, heredoc content kept tab indentation for `<<-EOF`), `upload-artifact` v4.6.2 → v7.0.2 (SHA cross-checked against the GitHub releases API; the v4→v7 breaking changes — Node 24 default, ESM, opt-in `archive:false` — do not touch this workflow), nixpkgs flake refresh                                                                       | `523a566`; rev-list count and remotes-lag checked BEFORE the reset (the over-broad-squash rule) |
| 3 | **The HARVEST shipped (`5dbf61f`)**: TODO_LIST refilled — Ready = the post-track survivors (M050 re-run with harness hardening + verdict fold baked in, emulator script timeouts, M035 local CI rehearsal, warning-freeze gate step, opt-in pre-push hook, `forkEvery` cost audit, test-count gate step, the webphone#21 tracking row replacing the filed M049 note) plus +1 Medium (first CI evidence after the billing fix) and +9 Low rows; ROADMAP gained M042's non-goal verdict and the three §g questions; CHANGELOG `[Unreleased]` records the J05-J16 track. All 64 §f items (30 + 34, overlapping) dispositioned in the commit body — routed, deduped, or closed; none dropped silently | `5dbf61f` diff + commit message ledger                                                          |
| 4 | **Three more split brains found and fixed on sight**: (i) AGENTS Conventions claimed `targetSdk/compileSdk = 36` while the build file says `compileSdk = 37` / `targetSdk = 36` (verified before editing); (ii) the gate script's OWN step-2 label claimed "94 tests" — a hardcoded count in prose rots on every test addition, so the count was dropped from the label entirely (the `e4c8dff` reconciliation grepped docs but never `scripts/`); (iii) the drafts convention (`docs/drafts/`, filed from the real file, draft kept) lived only in memory — now an AGENTS bullet                                                                                                                 | All three in `5dbf61f`                                                                          |
| 5 | **A second mid-work daemon blob absorbed with zero damage**: the daemon swept three harvest files mid-unit (pre-dprint), leaving a half-formed commit; verified session-only + remotes-lagged, folded + amended into `5dbf61f` pre-push, caught immediately by the post-commit status check (dirty files present = commit incomplete)                                                                                                                                                                                                                                                                                                                                                             | `git show --stat HEAD` after the amend = all 5 files                                            |
| 6 | **Lockstep held**: every unit hand-committed the moment it finished; final push moved both remotes `14e7455..bda6d32` together; one SHA ×3 verified                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | `git rev-parse origin/main github/main HEAD`                                                    |
| 7 | **The critique-suspected claim-grep hole was CLOSED mechanically, not assumed**: this pass suspected README/FEATURES still carried stale claims (the AGENTS compileSdk fix was STUMBLED UPON during a view, not found by a sweep — which means other stale claims could hide elsewhere). The mechanical grep over README + FEATURES for "94 test"/"94/94"/"compileSdk" came back CLEAN (FEATURES:107 already says compileSdk 37 — the `e4c8dff` pass got FEATURES right)                                                                                                                                                                                                                          | grep output, this window; nothing to fix — but the sweep is now a recorded step, not luck       |
| 8 | **One real CHANGELOG gap found by THIS critique and fixed in this window**: the maintenance sweep's dependency bumps (upload-artifact v7, nixpkgs) were never recorded under `[Unreleased]` — a dependency change of the repo, exactly what the file exists for. Now recorded — with an irony worth keeping: the daemon committed that exact hunk (`a381f9c`) minutes before my hand-commit, so the fix landed one commit EARLIER than the report carrying it (the daemon beat a 15-minute doc window even at 01:40; per-FILE cadence, not per-unit, is the only cadence that wins)                                                                                                               | The CHANGELOG hunk lives in `a381f9c`; this report + index in `43350c6`                         |

## b) PARTIALLY DONE

| # | Work                                                                                                                                                                                                                                                                                        | State                                                   |
| - | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| 1 | **M050 re-run** — not this window by design (the operator scoped gate + harvest). The Ready row now carries the hardened-harness requirements inline (log-file output, time-bounded boot/install, `uiautomator` bounds, logcat-watch) so the 7 h wedge class cannot recur when it runs      | Ready row 1 in TODO_LIST; still the last open track row |
| 2 | **The evidence trail for the gate runs lives in `/tmp`** — the reports cite log files that die at reboot. The GREEN claim is re-runnable in ~2 min cached, so this is a provenance nit, not a hole — but a one-line "last gate: GREEN at <commit>" marker file would make the claim durable | §f #35                                                  |

## c) NOT STARTED

- Everything the harvest ROUTED rather than executed, unchanged from the
  23:30 report: the owner/gated rows (M001-M008, Q1, M032-M033), the
  device trains, the live-contract batch, M082, the quiet 20%. TODO_LIST
  is the ranked list; three rows still gate on the §g answers.

## d) TOTALLY FUCKED UP (own mistakes, all repaired or contained)

1. **The CHANGELOG gap (found by this critique, §a #8).** I wrote the
   `[Unreleased]` entry myself and recorded the track but NOT the
   dependency bumps from the sweep — the very commits I had just read
   line by line. Writing a summary while the details are fresh and still
   dropping one is exactly the "read from memory" failure class; the fix
   is mechanical: when summarizing a set of commits, diff the summary
   against `git show --stat` of each.
2. **The claim-drift discipline has a hole I only suspected until now.**
   The compileSdk fix happened because I VIEWED that AGENTS section, not
   because any procedure looked for it. This critique turned luck into a
   step (mechanical grep over ALL living docs — including README and
   FEATURES, which I had not re-checked this window — plus `scripts/`
   prose, per the 94-label lesson). The grep ran clean, but clean-by-luck
   is not clean-by-procedure.
3. **The multi-file doc window invited daemon blob #2** (carried from the
   23:30 report §d): four docs written across sequential tool calls; the
   daemon committed three of them mid-unit. Prevention sharpened to
   per-FILE hand-commits during doc sweeps. Zero damage this time — the
   fold + amend were verified — but the cadence, not the daemon, is the
   root cause.
4. **A tiny provenance loss in the M049 row conversion**: the old Ready
   row cited `archived 00-20 §f30`; the replacement webphone#21 tracking
   row cites the draft + the issue + the two live reports but dropped the
   archived cite. Recoverable (the draft links the chain), but the
   convention is that every row stays self-contained. Noted, not
   re-edited — one cite in a Low row does not justify another tree
   churn today.
5. **Did I lie to you? No.** Every load-bearing claim was mechanically
   verified before it was made: the action-bump SHA against the GitHub
   releases API, the SDK claims against `app/build.gradle.kts`, the gate
   results from its own log, the lockstep from `rev-parse`, and this
   critique's grep claims from the grep itself.

## e) WHAT WE SHOULD IMPROVE

1. **Summary-vs-source diff for every CHANGELOG/docs summary**: after
   writing a summary of commits, walk `git show --stat` per commit and
   check each file appears (or is deliberately excluded). The §d #1 gap
   was invisible from inside the summary.
2. **The claim-drift grep is a PROCEDURE, not an instinct**: when any
   count/claim changes, grep ALL living docs AND `scripts/` prose for
   the old value — mechanically, every time, not when a view happens to
   land on the line. (Candidate for an AGENTS bullet — §f #36.)
3. **Per-FILE hand-commits during doc sweeps** (sharpened from per-unit):
   the daemon's sweep interval is shorter than a four-file writing pass.
4. **Counts never live in prose labels** — the gate label now says
   "wedge-fenced"; the count lives in the run itself.
5. **Evidence durability**: re-runnable claims (the gate) are fine
   uncited; one-shot claims (this window's logs) should land in-repo or
   carry their key numbers inline, as this report does.
6. **Gate-per-unit held this window** — keep it: the second gate pass
   (harvest tree, pre-commit) is what made the harvest commit boring.

## f) NEXT (ranked, ≤ 50 — a brainstorm, not a commitment)

| #  | Thing                                                                                                                                                    | Why                                                                    | Effort          |
| -- | -------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- | --------------- |
| 1  | M050 re-run under the hardened harness (log file, bounded phases, `uiautomator` bounds) — then fold the verdict into the AGENTS auth-detour gotcha       | The last open track row; credential-free                               | 30min           |
| 2  | Emulator smoke/train script hardening: `timeout` on boot + install, mandatory log file                                                                   | The 7 h wedge becomes structurally impossible                          | 20min           |
| 3  | M035 local CI rehearsal (fresh checkout, no signing props)                                                                                               | First real CI run should not be the first execution                    | 30min           |
| 4  | Warning-freeze gate step (fail on ANY new Kotlin warning)                                                                                                | The zero is since `fec6741`; freeze it                                 | 30min           |
| 5  | Pre-push hook (opt-in) running `verify-gate.sh` (`.githooks` is configured and empty)                                                                    | §e #2 of 23:02, enforcement                                            | 20min           |
| 6  | `forkEvery` cost audit (measure the fresh-JVM wall-time trade)                                                                                           | The fence bought safety; know its price                                | 15min           |
| 7  | Test-count as a gate step (re-count `@Test` from source)                                                                                                 | The 94-label split brain is the precedent                              | 5min            |
| 8  | Track webphone#21 to acceptance (the begin-302 contract)                                                                                                 | The detour seam depends on the server staying a 302                    | 5min ongoing    |
| 9  | Owner: M001 SystemNix deploy (attaches the push mirror)                                                                                                  | Highest-leverage unlock; hand-pushes stop after                        | 10min owner     |
| 10 | Owner: M002 PAT re-scope (repo+workflow)                                                                                                                 | Unfreezes `ci.yml` mirror pushes + domains (114 behind)                | 15min owner     |
| 11 | Owner: M003 Actions billing fix                                                                                                                          | Runs fire, zero jobs started; ALL CI evidence is blocked on it         | 10min owner     |
| 12 | After M003: force one `workflow_dispatch` run, archive its log as the FIRST CI evidence                                                                  | Every CI claim in AGENTS/FEATURES is execution-unproven                | 10min           |
| 13 | Owner: M032/M033 Forgejo release token + cut v0.2.0 via `scripts/release.sh`                                                                             | The update lane is UNPROVEN until the first publish                    | 20min owner     |
| 14 | Owner: M005-M008 physical install + Obtainium onboarding (incl. the one-time debug-build uninstall)                                                      | First device truth                                                     | 45min owner     |
| 15 | Operator: Q1 test extension + second account                                                                                                             | Unblocks the trains; the core promise is unproven on hardware          | 10min owner     |
| 16 | First device train M009-M014 (after Q1)                                                                                                                  | Install/launch/ring against the live stack                             | 1 day BLK       |
| 17 | Verify the answer-path race (push → FSI vs INVITE) on the live stack (after Q1)                                                                          | The open bootstrap-race question                                       | 2h BLK          |
| 18 | M057-M062 logged-in tail + live Pocket ID detour pass (needs IdP credential)                                                                             | W104-W106 + in-shell SSO end-to-end                                    | 60min BLK       |
| 19 | M054-M056 live contract checks (after Q1)                                                                                                                | Field-name drift surfaces at first boot                                | 60min BLK       |
| 20 | M063-M064 serve assetlinks + `verify-app-links`                                                                                                          | Deep-link UX without the chooser                                       | 30min           |
| 21 | M065-M067 logout semantics (KEEP vs PURGE, then implement)                                                                                               | Ghost rows in the push registry                                        | 60min BLK       |
| 22 | M068-M070 island-standdown seam + the safe-area fix (cross-repo)                                                                                         | Duplicate registration observed; top controls untappable in-shell      | 100min BLK      |
| 23 | M071-M073 device-verify batch (CallStyle post-time, TalkBack, battery prompt)                                                                            | Post-train honesty retirement                                          | 60min BLK       |
| 24 | M076-M078 post-train docs sweep + CHANGELOG cut                                                                                                          | README truth after the trains                                          | 100min BLK      |
| 25 | Owner: M079-M080 the 8 ROADMAP Open questions + visibility end-state                                                                                     | Head/repo drift                                                        | 45min owner     |
| 26 | M082 AGENTS slim-down < 30 KB (40,334 bytes, growing) — gated on g3                                                                                      | The gotcha list inflates every session                                 | 60min gated     |
| 27 | M083-M085 targetSdk 37 (after a device train), API 37 image, mockwebserver3                                                                              | The quiet 20%                                                          | gated/tail      |
| 28 | Reconcile the "v0.1 scaffold" status line vs 0.2.0 (after the release-first answer)                                                                      | §e #6 of 23:02; write it truthfully once the decision exists           | 10min gated     |
| 29 | Flip `configuration-cache.problems` to `fail` once the ktlint plugin's violations clear                                                                  | Warn mode is load-bearing today                                        | 15min gated     |
| 30 | Watch the ktlint plugin for the `setVisible` fix; bump when released                                                                                     | Kills the last Gradle-11 deprecation (M041)                            | 5min when out   |
| 31 | Annotate + archive the sibling session's three live reports (M081/g2) once its session ends                                                              | The archive's own rule; two are already stale                          | 20min           |
| 32 | After M001: verify the mirror keeps up (`scripts/mirror-lag.sh`)                                                                                         | Then hand-pushes stop                                                  | 10min           |
| 33 | Meta-test discipline as a PR/commit template line (GitHub-mirror only — Forgejo ignores `.github/`)                                                      | §f #31 of 23:02; AGENTS already carries the discipline                 | 10min           |
| 34 | Answer the §g trio (daemon policy, release-first, M050 end-state) — they now live in ROADMAP Open questions                                              | Three rows unblock on these                                            | 5min owner      |
| 35 | Durable gate evidence: a one-line "last gate: GREEN at <sha> <date>" marker (or append to the report convention)                                         | §b #2 — /tmp logs die at reboot                                        | 10min           |
| 36 | Record the claim-drift-grep-covers-scripts lesson as an AGENTS bullet (it lives in two reports today, not in the memory doc)                             | §e #2 — memory belongs in AGENTS, not in timestamped files             | 5min            |
| 37 | Summary-vs-source diff habit: after any docs summary of commits, walk `git show --stat` per commit (the §d #1 check)                                     | Catches dropped details while they are cheap                           | habit, 2min/use |
| 38 | Restore the archived-00-20 cite to the webphone#21 tracking row (next time TODO_LIST's Ready table is touched anyway)                                    | §d #4 — self-contained rows                                            | 1min            |
| 39 | Emulator-shell sanity after the nixpkgs bump (the gate validated the BUILD shell; the `.#emulator` shell shares the lock but was not booted this window) | The M050 re-run will exercise it anyway — fold into row 1's pre-flight | 0min (fold)     |
| 40 | Decide the CHANGELOG policy for CI-only dependency bumps (recorded this window under Changed — keep or move to a chore convention)                       | One-line policy call, prevents the next gap or the next over-recording | 5min owner      |

## g) QUESTIONS FOR THE OPERATOR (cannot self-answer — the trio, carried a third time)

1. **Daemon policy**: the daemon blobbed work twice MORE this window
   (once on a foreign maintenance tree, once mid-harvest). Both folds
   were clean and verified — but is amend/fold-remedy + per-file
   hand-commit cadence the accepted steady state, or do you want the
   daemon disabled/paced for this repo (a SystemNix knob) while agent
   sessions run?
2. **Release-first for v0.2.0**: `version.txt` says 0.2.0, the release
   lane is shipped but unpublished, and the whole agent track just
   landed. Cut v0.2.0 NOW (needs the Forgejo token, M032/M033) so
   Obtainium's first update cycle starts from a real baseline — or let
   more work pile onto 0.2.0 first? (Also decides when the "v0.1
   scaffold" status line can be rewritten truthfully.)
3. **M050's Android end-state**: when the consent Cancel fires and the
   IdP errors back to the origin, the current design commits the origin,
   clears the detour, and renders the IdP's error page in-shell. Keep
   that, or auto-retry / return to the island home instead? It decides
   whether M050 is a verification pass or a design change.
