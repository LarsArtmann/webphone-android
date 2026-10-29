# Gate Green Twice, the §f Harvest, and Three More Split Brains (2026-10-28 23:30)

Session window: 2026-10-28 23:10 → 23:30 CET. Scope was the two pending
rows from the close-out: run the full gate, then harvest. Both done; the
harvest surfaced three more split brains on the way.

## a) FULLY DONE

| # | Work                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Evidence                                                                                                     |
| - | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| 1 | **A fresh daemon blob was folded per the remedy BEFORE any work**: `757ce6e` ("auto-commit 11 changed file(s)") held a maintenance sweep, not my paths — judged line by line before folding: a formatter-only pass over all 8 scripts (tabs → 4 spaces, trailing pipes; `bash -n` green on every script, `py_compile` green, `git diff -w` proves zero semantic change, heredoc content kept its tabs for `<<-EOF`), the `upload-artifact` bump v4.6.2 → v7.0.2 (SHA cross-checked against the GitHub releases API; v4→v7 breaking changes — Node 24 default, ESM, opt-in `archive:false` — do not touch this workflow's `name:`/`path:` usage), and a nixpkgs flake.lock refresh | Folded as `523a566` with a full-description message; rev-list count and remotes-lag checked before the reset |
| 2 | **§f #1 CLOSED — the pushed stack is proven**: `bash scripts/verify-gate.sh` ran GREEN end-to-end (6/6: assemble 1m51s, tests 2m29s, ktlint+parity 10s, 16 KB zipalign, 50 contrast checks 0 failures, dprint) on the tree that includes the maintenance sweep — the gate debt from the 23:02 session is retired, and the flake bump is empirically validated                                                                                                                                                                                                                                                                                                                     | `/tmp/webphone-gate-2320.log`; "GATE GREEN" banner                                                           |
| 3 | **The HARVEST (§f #3 + #4) shipped as `5dbf61f`**: TODO_LIST refilled (Ready = the post-track survivors: M050 re-run with harness hardening + verdict fold, emulator script timeouts, M035 local rehearsal, warning-freeze gate step, opt-in pre-push hook, `forkEvery` cost audit, test-count gate step, the webphone#21 tracking row replacing the filed M049 note; +1 Medium CI-evidence row; +9 Low rows), ROADMAP gained M042's non-goal verdict and the three §g questions, CHANGELOG `[Unreleased]` records the J05-J16 track. Every one of the 64 §f items (30 + 34, overlapping) is dispositioned in the commit message — none dropped silently                          | `5dbf61f` diff; the commit body carries the routing ledger                                                   |
| 4 | **Three MORE split brains found and fixed on sight** (the `e4c8dff` lesson compounding): (i) AGENTS Conventions still claimed `targetSdk/compileSdk = 36` while `app/build.gradle.kts` says `compileSdk = 37` / `targetSdk = 36`; (ii) the gate script's OWN step-2 label claimed "94 tests" — a hardcoded count in prose rots on every test addition, so the count is dropped from the label entirely (the previous session's grep fixed docs but missed script prose); (iii) the drafts convention (`docs/drafts/`, filed from the real file, draft kept) lived only in memory — now an AGENTS bullet                                                                           | All three in `5dbf61f`; the SDK claim verified against the build file before editing                         |
| 5 | **Gate-per-unit applied**: the gate re-ran GREEN on the harvest tree BEFORE the commit (cached, minutes) — the "final tree never saw the whole gate" class stays dead                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | `/tmp/webphone-gate-2.log`                                                                                   |
| 6 | **A SECOND mid-work daemon blob was absorbed with zero damage**: the daemon swept three harvest files at 23:27:47 (mid-unit, pre-dprint), leaving the commit half-formed; verified session-only + remotes-lagged, folded and AMENDED into the single harvest commit `5dbf61f` pre-push                                                                                                                                                                                                                                                                                                                                                                                            | `git show --stat HEAD` = all 5 files; nothing pushed during the amend window                                 |

## b) PARTIALLY DONE

| # | Work                                                                                                                                                                                                                                     | State                    |
| - | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------ |
| 1 | M050 re-run — not this window (the operator's order scoped gate + harvest); the Ready row now carries the hardened-harness requirements inline (log file, time-bounded phases, `uiautomator` bounds) so the 7 h wedge class cannot recur | Ready row 1 in TODO_LIST |

## c) NOT STARTED

- Everything else the harvest ROUTED, not executed: the owner/gated rows
  (M001-M008, Q1, M032-M033, the trains), the cheap hardening rows, M082.
  TODO_LIST's Ready section IS the next-work list; §g answers still gate
  three rows.

## d) TOTALLY FUCKED UP (own mistakes, all repaired)

1. **The 10-minute multi-file doc window invited blob #2.** I wrote four
   docs across sequential tool calls and the daemon committed three of
   them mid-unit — the commit-when-THE-UNIT-finishes habit is too coarse
   for doc sweeps; the daemon is faster than a multi-file unit. The fold
   cost one reset + one amend (both verified: session-only paths, remotes
   lagged, pre-push) and one re-commit — zero damage, but the prevention
   is now sharpened: during DOC sweeps, commit per FILE.
2. **The blob caught a PRE-dprint TODO_LIST** (swept between my write
   and my format pass), so the first harvest commit carried unformatted
   tables and the commit needed the amend. Caught immediately by the
   post-commit status check (modified files present = the commit was
   incomplete).
3. **Did I lie to you? No.** Every load-bearing claim was mechanically
   verified before it was made: the action-bump SHA against the releases
   API, the SDK claims against the build file, the gate results from its
   log, the test-suite state from the gate run itself.

## e) WHAT WE SHOULD IMPROVE

1. **Per-FILE hand-commits during doc sweeps** (sharpened from the
   per-unit rule — see §d #1).
2. **The claim-drift grep must cover script prose, not just docs** — the
   "94 tests" label lived in `scripts/verify-gate.sh`, which the `e4c8dff`
   grep of AGENTS/README/FEATURES/TODO_LIST/CHANGELOG never sees. New
   rule: grep `scripts/` too when a count or claim changes.
3. **Counts never belong in prose labels** — the gate label now says
   "wedge-fenced", not "94 tests"; the count lives in the run itself.

## f) NEXT (ranked)

1. M050 re-run under the hardened harness (Ready row 1) — then fold the
   verdict into the AGENTS auth-detour gotcha.
2. The cheap hardening rows: emulator script timeouts, warning-freeze
   gate step, pre-push hook, `forkEvery` audit, test-count gate step.
3. Owner: the §g trio (now in ROADMAP Open questions) — daemon policy,
   release-first v0.2.0, M050 end-state; plus M001-M003 which unblock CI
   evidence and the mirror.
4. After M003: first CI evidence run (the new Medium row).

## g) QUESTIONS FOR THE OPERATOR (cannot self-answer — carried, now in ROADMAP)

1. **Daemon policy** — steady state vs disable/pace (SystemNix knob).
2. **Release-first for v0.2.0** — cut now (needs the Forgejo token) or
   let work pile onto 0.2.0?
3. **M050's Android end-state** — in-shell IdP error page (current) vs
   auto-retry / island home.

All three live in `ROADMAP.md` → Open questions as of this session.
