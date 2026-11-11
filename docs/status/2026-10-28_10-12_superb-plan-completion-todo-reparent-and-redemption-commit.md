# SUPERB Plan Completed — TODO Re-Parent, Coverage Repair, Redemption Commit (2026-10-28 10:12)

> **Session verdict:** the planning/bookkeeping mission is DONE and pushed
> (`7574c79`, both remotes, SHA-lockstep) — but this session executed ZERO
> of the plan's own work items. Two consecutive sessions produced docs and
> order; the next session must start at M021, not another document.
> **As-found honesty:** the plan shipped (02:56) with a FALSE "nothing
> dropped" coverage claim; this turn found the 5 missing rows and repaired
> it before commit. Details in §d.

## Basis

- Operator order: complete the SUPERB plan mission — TODO re-parent, gates,
  detailed commit, push both remotes, report with tables; Verschlimmbesserung
  explicitly threatened.
- `TODO_LIST.md` (39 rows across 4 tiers), the plan file, the archived
  corpus, and the live sibling release-lane session (untouched: `scripts/
  release.sh`, `scripts/forgejo-release.py`, its 3 live reports).

## Timeline of this session

| When     | What                                                                                                                                                            |
| -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 02:56    | Plan v1 written (prior turn) with the harvest-note coverage claim                                                                                               |
| ~03:02   | Fresh git state: 30 dirty doc paths verified, all this session's                                                                                                |
| ~03:04   | TODO_LIST re-parent: 40 edits (header pointer + 39 row parents)                                                                                                 |
| ~03:06   | **Coverage gap found:** 5 TODO rows absent from the plan's fine table                                                                                           |
| ~03:12   | Plan amended: 85-row parent-sorted fine table spliced via assert-guarded script; totals/mermaid/harvest-note synced; stale-ref asserts green                    |
| ~03:15   | Gates: `annotate.go check-rows` → LEDGER-EXEMPT; dprint fmt (2 files); `dprint check` repo-wide clean                                                           |
| 03:22:44 | The auto-commit daemon swept the whole 52-path tree into blob `bf91f45` mid-session                                                                             |
| ~03:25   | Blob verified session-docs-only (no `scripts/`, `app/`, sibling files); both remotes lagged HEAD; dirty diff = pure dprint re-padding (148/148)                 |
| ~03:26   | **Amended in place → `7574c79`** — detailed 8-section message, 31 files (+795/−154), 21 archive renames preserved                                               |
| ~03:27   | Pushed: origin `43c9d98..7574c79`, github `2e06567..7574c79`; fetch-verify = all three SHAs `7574c79` (lockstep; GitHub had drifted behind Forgejo before this) |
| 10:12    | This report + README index + two AGENTS lessons (uncommitted, per "wait for instructions")                                                                      |

## a) FULLY DONE

1. **TODO_LIST re-parent** — all 39 rows (17 Ready + 7 High + 11 Medium +
   4 Low) carry `Jxx (Myyy…)` parents; header gained the plan pointer; row
   text stays self-contained; the sibling's 2 rows kept (v0.2.0 → J09,
   Obtainium → J02/M008). The plan's harvest note is now TRUE.
2. **Plan coverage repair** — fine table grew 80 → 85 rows, renumbered
   parent-sorted; added M008 (Obtainium), M042 (version-catalog plugin),
   M043 (build-cache), M050 (detour CANCEL re-run), M070 (island
   safe-area); totals synced (85 / 43 gated / 42 agent ~8h); mermaid's 12
   M-range refs renumbered; harvest-note owner list corrected
   (J01-J04, J09, J17-J27); §g rows are exactly M036/M081/M082.
3. **Gates green** — `annotate.go check-rows`: LEDGER-EXEMPT (fine table);
   `dprint fmt` 2 files; `dprint check` clean repo-wide. Docs-only: the
   94-test JVM gate untouched.
4. **The redemption commit `7574c79`** — the daemon's hated
   `chore: auto-commit` blob amended in place into a detailed 8-section
   message (WHAT/WHY/VERIFICATION/NOTE). Verified before amending: blob
   contained ONLY session-doc paths; HEAD unpushed on both remotes; the
   fold-in diff was symmetric formatting churn.
5. **Both pushes + lockstep** — Forgejo (canonical) and GitHub twin at the
   identical SHA; the pre-existing GitHub drift (it sat 4+ commits behind)
   is closed by hand-push as AGENTS prescribes until the mirror attaches.
6. **Nothing degraded** — archive stayed closed, no shipped work
   re-dispatched, sibling release-lane files untouched, no app/build files
   touched at any point.

## b) PARTIALLY DONE

1. **The mission's own deliverable quality:** the plan (02:56) was
   delivered LAST TURN with a false "ALL todos covered / nothing dropped"
   totals claim — 5 rows missing. Repaired this turn (same session, before
   commit), so the final state is true, but the first delivery was
   defective.
2. **Daemon hygiene:** repaired post-hoc exactly once (amend); no
   structural prevention exists — the daemon is still active and will blob
   this very report unless hand-committed first.
3. **Docs-health loop:** archive closed, gates green, TODO refilled — but
   the loop's PURPOSE (drive execution) has not produced a single executed
   plan task yet.

## c) NOT STARTED

1. **All 29 agent-executable-now fine rows** (M021-M053): wedge-proof
   suite, verify-gate wrapper, KGP parity, CI release leg, J11's ungated
   half, dependabot audit, gradle hygiene, deprecation sweep,
   micro-investigations, detour CANCEL re-run, test pins. ~11h of work,
   zero minutes spent.
2. **All 43 gated rows** (owner actions, Q1 stack, credentials, phone).
3. **The standing operator decisions:** §g triple (M036/M081/M082), the 8
   ROADMAP Open questions (M079), KEEP-vs-PURGE (M065), visibility
   (M080), standdown nod (M068).
4. **The 1%/4% tier outcomes:** first green CI run (J10), first release
   (J09), both device trains (J03/J04) — untouched by design (gated), but
   nothing agent-side is queued ahead of them either beyond the plan.

## d) TOTALLY FUCKED UP

1. **The false coverage claim (repaired).** The 02:56 plan asserted
   "Everything in TODO_LIST … is covered above — nothing dropped" while
   its fine table missed 5 TODO rows. Root cause: coverage was CLAIMED
   from memory instead of verified by a mechanical TODO↔fine-table
   cross-check; the count line ("80 fine tasks") was treated as proof.
   Caught on re-read this turn, fixed in `7574c79`, lesson filed into
   AGENTS. This is the session's one genuine process failure.
2. **Near-miss, not quite fucked:** the daemon blob (`bf91f45`) ate the
   finished tree ~20 minutes before I would have committed. Had I not
   re-run `git status` after dprint, I would have reported a hand-commit
   that no longer existed as authored. Sequencing (work → gates → commit)
   left the daemon window open; only the final verification step caught
   reality. Zero data loss; amend recovered fully.

Nothing else qualifies. The end state is verified green, and both remotes
hold the identical tree.

## e) WHAT WE SHOULD IMPROVE

1. **Mechanical coverage lint:** a script that cross-checks every
   TODO_LIST row has a J-parent, every cited J/M-ID exists in the plan,
   and every fine row maps to a TODO row or report item. Would have
   caught the 5-row gap in seconds.
2. **Commit finished units immediately** — never leave a completed tree
   uncommitted for the daemon to blob. The redemption commit should not
   be a recurring heroic.
3. **Recorded amend procedure** (now in AGENTS): amend a daemon blob only
   after verifying (a) it contains solely this session's paths, (b) both
   remotes lag HEAD, (c) the fold-in diff is understood. Never amend after
   a push.
4. **Structural daemon fix is operator-side** (SystemNix): pace it, scope
   it away from `docs/`, or make it refuse trees with staged renames.
   `bf91f45` was not the first blob and won't be the last.
5. **Gate ordering fixed:** content edits → check-rows → dprint fmt →
   dprint check, one pass, no re-runs.
6. **AGENTS growth discipline:** lessons land as compact bullets only —
   M082 targets <30 KB; this session added 2 bullets, keep net growth
   near zero.
7. **GitHub drift was pre-existing** (`2e06567` vs `43c9d98` before this
   session): hand-pushes are the stopgap; J01/M001 (mirror attach) is the
   cure — highest-leverage owner action.
8. **Session shape:** two consecutive sessions of planning/bookkeeping
   with zero plan execution. Default next sessions to EXECUTION
   (M021 first), documents only at session end.

## f) NEXT — 48 concrete things, in order

**Agent-executable NOW (the plan's ~11h track; 1-28 = M021-M053):**

~~1. M021 task-level fence on `testDebugUnitTest`~~ done — J05 wedge-proof fence shipped (timeout=20min + forkEvery=1), commit 97e6929 (docs/status/2026-10-28_22-31 row 2)
2. M022 per-test timeouts (MockWebServer/WebSocket suites)
3. M023 `forkEvery`/fresh-executor policy
4. M024 shared test OkHttp dispatcher; migrate sockets
5. M025 gate run: 94+ green with fence armed
6. M026 write `scripts/verify-gate.sh`
7. M027 wire wrapper into AGENTS + end-to-end run
8. M028 KGP↔catalog parity check task
9. M029 parity prove-it (scratch bump → fail → revert)
10. M030 CI `assembleRelease` in verify job
11. M031 CI artifact release `mapping.txt`
~~12. M037 fix the 2 wrong-actor verdicts (00-25 f40, 01-35 c7)~~ done — J11 wrong-actor repair, three archived verdicts corrected, commit 9624909 (22-31 row 7)
13. M038 full gradle gate over the post-sweep tree
14. M039 dependabot audit post catalog-reshaping
~~15. M040 `--warning-mode all` inventory~~ done — J12-J15 warning-mode-all inventory: 5 warnings + 1 deprecation, 2 fixed + 4 kept, commit fec6741 (22-31 row 8)
16. M041 kill the Gradle-10 deprecation source
17. M042 version-catalog update plugin eval (adopt or non-goal)
18. M043 build-cache eval (adopt or non-goal)
~~19. M044 FcmReceiver `@Deprecated` 2-liner~~ done — FCM-26 deprecation suppressions recorded with WHY (AGENTS keep-decisions; 22-31 row 8)
20. M045 Telecom + `onBackPressed` keep-decisions recorded
~~21. M046 dialpad "100" blank-vs-seeded~~ done — M046 dialpad self-extension hint removed; cold dialpad honestly blank, commit fec6741 (22-31 row 8)
~~22. M047 viewbinding-on-test-classpath check~~ done — M047 viewbinding-on-test-classpath confirmed harmless (AGP runtime; 22-31 row 8)
~~23. M048 ktlint/dprint ownership boundary check~~ done — M048 ktlint/dprint boundary re-verified (22-31 row 8)
24. M049 cross-repo 302-note to webphone
25. M050 detour CANCEL-leg emulator re-run (uiautomator)
~~26. M051 ntfy ACTION_STOP stopped-flag pin~~ done — J16 ntfy stop-leak pin, meta-proven both ways, commit dd2fb12 (22-31 row 9)
27. M052 `wiringEnabled` pin + AttachGuardTest review
28. M053 one Robolectric SDK policy

**Session metallurgy (new, grounded in this session):**

29. TODO↔plan parent cross-check lint script (see e1); wire into the gate
30. Hand-commit each finished unit immediately (starve the daemon)
31. Make `mirror-lag` fetch+rev-parse a fixed session-end checklist item
32. Index every new status report in `docs/status/README.md` at write time
33. Run `check-rows` over TODO_LIST after structural table edits too
34. Keep AGENTS lessons to compact bullets (net-zero growth toward M082)
35. Optional plan appendix: f-list↔M-row provenance map for auditors
36. Re-verify after ANY daemon commit: `git status` must be clean and
    `git log` must not contain new blobs eating finished work

**Waiting on the operator (gated highlights; the full 43 are in the plan):**

37. M001-M003 — the J01 bundle (deploy + PAT + billing): highest leverage
38. Q1 test extension → fires M009-M020 (trains) + M054-M056 (contracts)
39. M032-M033 — Forgejo token → cut v0.2.0 (sibling's lane)
40. Pocket ID credential → fires M057-M062 (logged-in tail + live login)
41. M063-M064 — assetlinks.json served + verify-app-links green
42. M065-M067 — KEEP-vs-PURGE answer → logout deregistration
43. M068-M070 — standdown nod + safe-area coordination
44. M074-M083 — repo meta, post-train docs, decisions, targetSdk 37 tail

**Meta:**

45. Decide the next-track shape (see g3) before starting M021
46. Keep the new TODO convention: every future TODO row gets a J-parent
    at add time, never retroactively
47. This report + README index + AGENTS bullets sit UNCOMMITTED by order
    ("wait for instructions"); hand-commit them with the next order before
    the daemon blobs them
48. After the trains (J03/J04) land: M071-M073, M076-M078, M083-M085
    unblock automatically — nothing to do now

## g) THREE QUESTIONS I CANNOT ANSWER MYSELF

1. **Daemon policy:** when the auto-commit daemon blobs a finished
   pre-push tree, is amend-in-place (this session's remedy, `7574c79`)
   the blessed standard — or do you want blob PREVENTION instead
   (hand-commit immediately after each unit, and/or pace/scope the daemon
   in SystemNix)? I can repair blobs; only you can retire them.
2. **ID canon:** should J-/M-IDs become the project's canonical task
   vocabulary — TODO rows, CHANGELOG entries, and future reports CITE
   M-IDs instead of re-describing work? That cements today's re-parent as
   the last retroactive one.
3. **Next-track shape:** execute the agent track (M021→M053, ~11h) as ONE
   session with per-task commits and a closing report, or task-per-session
   with a status report between each? (Affects how much daemon window and
   how many reports the corpus accrues.)

## Verification status

- `annotate.go check-rows` on the plan: LEDGER-EXEMPT (expected) — 1 file
- `dprint check` repo-wide: clean
- 85 fine rows verified by assert (sequence M001-M085, no gaps, no stale
  refs: no `M086`, no `80 fine`, no old M-ranges, no old harvest IDs)
- 39 TODO parents verified by grep count; blockquote pointer in place
- `git log`: `7574c79` detailed commit on top; worktree was clean at
  10:12 before this report's edits
- Remotes: origin = github = local = `7574c79` at last push

## Honesty notes

- This session wrote ZERO production code and executed ZERO plan tasks —
  it closed planning debt (which was real and ordered) and nothing else.
- The daemon blob amendment changed history (rewrote `bf91f45`); safe
  because both remotes lagged, verified before the amend. Never repeat
  after a push.
- GitHub had silently drifted behind Forgejo before this session's push;
  the lockstep now is hand-push luck, not the (still unattached) mirror.
