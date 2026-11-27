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

<!-- check-rows:ledger -->

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

~~1. **TODO_LIST re-parent** — all 39 rows (17 Ready + 7 High + 11 Medium +~~ done at `7574c79` — every TODO_LIST.md row still carries a J/M parent (TODO_LIST.md header, 2026-11-27)
4 Low) carry `Jxx (Myyy…)` parents; header gained the plan pointer; row
text stays self-contained; the sibling's 2 rows kept (v0.2.0 → J09,
Obtainium → J02/M008). The plan's harvest note is now TRUE.
~~2. **Plan coverage repair** — fine table grew 80 → 85 rows, renumbered~~ done at `7574c79` — plan archived under `docs/planning/archived/`
parent-sorted; added M008 (Obtainium), M042 (version-catalog plugin),
M043 (build-cache), M050 (detour CANCEL re-run), M070 (island
safe-area); totals synced (85 / 43 gated / 42 agent ~8h); mermaid's 12
M-range refs renumbered; harvest-note owner list corrected
(J01-J04, J09, J17-J27); §g rows are exactly M036/M081/M082.
~~3. **Gates green** — `annotate.go check-rows`: LEDGER-EXEMPT (fine table);~~ done at `7574c79` — docs-only pass, recorded in that commit's VERIFICATION section
`dprint fmt` 2 files; `dprint check` clean repo-wide. Docs-only: the
94-test JVM gate untouched.
~~4. **The redemption commit `7574c79`** — the daemon's hated~~ done at `7574c79` — commit present with the detailed message; blob `bf91f45` superseded
`chore: auto-commit` blob amended in place into a detailed 8-section
message (WHAT/WHY/VERIFICATION/NOTE). Verified before amending: blob
contained ONLY session-doc paths; HEAD unpushed on both remotes; the
fold-in diff was symmetric formatting churn.
~~5. **Both pushes + lockstep** — Forgejo (canonical) and GitHub twin at the~~ done at `7574c79` — push verified in-session (origin 43c9d98..7574c79, github 2e06567..7574c79)
identical SHA; the pre-existing GitHub drift (it sat 4+ commits behind)
is closed by hand-push as AGENTS prescribes until the mirror attaches.
~~6. **Nothing degraded** — archive stayed closed, no shipped work~~ done at `7574c79` — 31 docs paths, no `app/`/`scripts/` files in the commit
re-dispatched, sibling release-lane files untouched, no app/build files
touched at any point.

## b) PARTIALLY DONE

~~1. **The mission's own deliverable quality:** the plan (02:56) was~~ done at `7574c79` — the 5-row gap was repaired before the commit; lesson in AGENTS.md "Coverage claims need a mechanical cross-check"
delivered LAST TURN with a false "ALL todos covered / nothing dropped"
totals claim — 5 rows missing. Repaired this turn (same session, before
commit), so the final state is true, but the first delivery was
defective.
~~2. **Daemon hygiene:** repaired post-hoc exactly once (amend); no~~ **→ ROADMAP.md "Open questions" — "Auto-commit daemon policy"** (remedy recorded in AGENTS.md "Daemon-blob remedy"; prevention undecided)
structural prevention exists — the daemon is still active and will blob
this very report unless hand-committed first.
~~3. **Docs-health loop:** archive closed, gates green, TODO refilled — but~~ **Superseded — the agent track was executed afterwards: CHANGELOG.md `[Unreleased]` J05–J16 (97-test gate), commits `97e6929`..`dd2fb12`.**
the loop's PURPOSE (drive execution) has not produced a single executed
plan task yet.

## c) NOT STARTED

~~1. **All 29 agent-executable-now fine rows** (M021-M053): wedge-proof~~ **Superseded — CHANGELOG.md `[Unreleased]` J05–J16 track (`97e6929`, `fec6741`, `9624909`, `dd2fb12`); the per-item verdicts are inline in §f 1-28 below.**
suite, verify-gate wrapper, KGP parity, CI release leg, J11's ungated
half, dependabot audit, gradle hygiene, deprecation sweep,
micro-investigations, detour CANCEL re-run, test pins. ~11h of work,
zero minutes spent.
~~2. **All 43 gated rows** (owner actions, Q1 stack, credentials, phone).~~ **→ TODO_LIST.md "High Impact" / "Medium Impact" / "Low Impact" BLOCKED rows**
~~3. **The standing operator decisions:** §g triple (M036/M081/M082), the 8~~ **→ TODO_LIST.md "Operator: review the 8 ROADMAP Open questions + decide the visibility end-state (M079-M080)"**
ROADMAP Open questions (M079), KEEP-vs-PURGE (M065), visibility
(M080), standdown nod (M068).
~~4. **The 1%/4% tier outcomes:** first green CI run (J10), first release~~ **→ TODO_LIST.md "First CI evidence", "First device train", "Connect the phone to Obtainium"**
(J09), both device trains (J03/J04) — untouched by design (gated), but
nothing agent-side is queued ahead of them either beyond the plan.

## d) TOTALLY FUCKED UP

~~1. **The false coverage claim (repaired).** The 02:56 plan asserted~~ done at `7574c79` — repaired in that commit; lesson filed in AGENTS.md "Coverage claims need a mechanical cross-check"
"Everything in TODO_LIST … is covered above — nothing dropped" while
its fine table missed 5 TODO rows. Root cause: coverage was CLAIMED
from memory instead of verified by a mechanical TODO↔fine-table
cross-check; the count line ("80 fine tasks") was treated as proof.
Caught on re-read this turn, fixed in `7574c79`, lesson filed into
AGENTS. This is the session's one genuine process failure.
~~2. **Near-miss, not quite fucked:** the daemon blob (`bf91f45`) ate the~~ done at `7574c79` — blob `bf91f45` amended in place; nothing lost (commit object still resolvable)
finished tree ~20 minutes before I would have committed. Had I not
re-run `git status` after dprint, I would have reported a hand-commit
that no longer existed as authored. Sequencing (work → gates → commit)
left the daemon window open; only the final verification step caught
reality. Zero data loss; amend recovered fully.

Nothing else qualifies. The end state is verified green, and both remotes
hold the identical tree.

## e) WHAT WE SHOULD IMPROVE

~~1. **Mechanical coverage lint:** a script that cross-checks every~~ done — rule recorded in AGENTS.md "Coverage claims need a mechanical cross-check" (the script itself is not built; §f29 stays open)
TODO_LIST row has a J-parent, every cited J/M-ID exists in the plan,
and every fine row maps to a TODO row or report item. Would have
caught the 5-row gap in seconds.
~~2. **Commit finished units immediately** — never leave a completed tree~~ done — recorded in AGENTS.md "Daemon-blob remedy": "hand-commit finished units immediately"
uncommitted for the daemon to blob. The redemption commit should not
be a recurring heroic.
~~3. **Recorded amend procedure** (now in AGENTS): amend a daemon blob only~~ done — AGENTS.md "Daemon-blob remedy (2026-10-28)" carries checks (a)-(c) and "never amend after a push"
after verifying (a) it contains solely this session's paths, (b) both
remotes lag HEAD, (c) the fold-in diff is understood. Never amend after
a push.
~~4. **Structural daemon fix is operator-side** (SystemNix): pace it, scope~~ **→ ROADMAP.md "Open questions" — "Auto-commit daemon policy"**
it away from `docs/`, or make it refuse trees with staged renames.
`bf91f45` was not the first blob and won't be the last.
~~5. **Gate ordering fixed:** content edits → check-rows → dprint fmt →~~ done — AGENTS.md "verify-gate.sh (eight steps)" fixes the order: assemble, tests, ktlint + parity, lint, zipalign, contrast, dprint check
dprint check, one pass, no re-runs.
~~6. **AGENTS growth discipline:** lessons land as compact bullets only —~~ **→ TODO_LIST.md "M082: AGENTS slim-down below 30 KB"**
M082 targets <30 KB; this session added 2 bullets, keep net growth
near zero.
~~7. **GitHub drift was pre-existing** (`2e06567` vs `43c9d98` before this~~ **→ TODO_LIST.md "Verify the Forgejo→GitHub mirror keeps up (`scripts/mirror-lag.sh`)" and "Owner: run the SystemNix deploy"**
session): hand-pushes are the stopgap; J01/M001 (mirror attach) is the
cure — highest-leverage owner action.
~~8. **Session shape:** two consecutive sessions of planning/bookkeeping~~ **Superseded — the execution default was overtaken by the shipped J05–J16 track (CHANGELOG.md `[Unreleased]`).**
with zero plan execution. Default next sessions to EXECUTION
(M021 first), documents only at session end.

## f) NEXT — 48 concrete things, in order

**Agent-executable NOW (the plan's ~11h track; 1-28 = M021-M053):**

~~1. M021 task-level fence on `testDebugUnitTest`~~ done — J05 wedge-proof fence shipped (timeout=20min + forkEvery=1), commit 97e6929 (docs/status/2026-10-28_22-31 row 2)
~~2. M022 per-test timeouts (MockWebServer/WebSocket suites)~~ done at `97e6929` — 90 s per-test JUnit rule + injected fence client (CHANGELOG.md `[Unreleased]` "Wedge-proof JVM suite (J05)")
~~3. M023 `forkEvery`/fresh-executor policy~~ **→ TODO_LIST.md "`forkEvery` cost audit: fresh-JVM-per-class is wedge-proof but slow"** (forkEvery=1 shipped at `97e6929`; the cost verdict is open)
~~4. M024 shared test OkHttp dispatcher; migrate sockets~~ r: TODO_LIST.md "M024: one shared OkHttp dispatcher for the test sources"
~~5. M025 gate run: 94+ green with fence armed~~ done at `97e6929` — CHANGELOG.md `[Unreleased]`: the J05-J16 track ran at the 97-test gate
~~6. M026 write `scripts/verify-gate.sh`~~ done at `fec6741` — `scripts/verify-gate.sh` exists (CHANGELOG.md `[Unreleased]` "scripts/verify-gate.sh (J06)")
~~7. M027 wire wrapper into AGENTS + end-to-end run~~ done — AGENTS.md names `bash scripts/verify-gate.sh` as the one-command gate; first end-to-end green run recorded in `docs/status/2026-10-28_23-30_…md` row 2
~~8. M028 KGP↔catalog parity check task~~ done — root `kgpParityCheck` task (AGENTS.md "KGP-parity check", CHANGELOG.md `[Unreleased]` "KGP-pin parity gate (J07)")
~~9. M029 parity prove-it (scratch bump → fail → revert)~~ done — proven by scratch bump → red → revert → green (CHANGELOG.md `[Unreleased]` "KGP-pin parity gate (J07)")
~~10. M030 CI `assembleRelease` in verify job~~ done — `.github/workflows/ci.yml` runs `./gradlew assembleRelease` in the verify job (CHANGELOG.md `[Unreleased]` "CI release leg (J08)")
~~11. M031 CI artifact release `mapping.txt`~~ done — `.github/workflows/ci.yml` "Upload R8 mapping" → `release-mapping` (CHANGELOG.md `[Unreleased]` "CI release leg (J08)")
~~12. M037 fix the 2 wrong-actor verdicts (00-25 f40, 01-35 c7)~~ done — J11 wrong-actor repair, three archived verdicts corrected, commit 9624909 (22-31 row 7)
~~13. M038 full gradle gate over the post-sweep tree~~ **→ TODO_LIST.md "Re-run `scripts/verify-gate.sh` WHOLE on the final tree"**
~~14. M039 dependabot audit post catalog-reshaping~~ done at `fec6741` — J12-J15 hygiene batch, `--warning-mode all` inventory (CHANGELOG.md `[Unreleased]`; 22-31 report row 8)
~~15. M040 `--warning-mode all` inventory~~ done — J12-J15 warning-mode-all inventory: 5 warnings + 1 deprecation, 2 fixed + 4 kept, commit fec6741 (22-31 row 8)
~~16. M041 kill the Gradle-10 deprecation source~~ **→ TODO_LIST.md "Watch the ktlint plugin for the `setVisible` fix; bump when released" (kills the last Gradle deprecation)**
~~17. M042 version-catalog update plugin eval (adopt or non-goal)~~ **NOT-DO — ROADMAP.md "Non-goals": version-catalog update plugin evaluated, NOT adopted (M042, 2026-10-28).**
~~18. M043 build-cache eval (adopt or non-goal)~~ done at `fec6741` — "adopt build cache" (22-31 report row 8: `org.gradle.caching=true`)
~~19. M044 FcmReceiver `@Deprecated` 2-liner~~ done — FCM-26 deprecation suppressions recorded with WHY (AGENTS keep-decisions; 22-31 row 8)
~~20. M045 Telecom + `onBackPressed` keep-decisions recorded~~ done — keep-decisions recorded in AGENTS.md: "`CAPABILITY_SELF_MANAGED` keep-decision" and "`onBackPressed` super-call keep-decision (2026-10-28)"
~~21. M046 dialpad "100" blank-vs-seeded~~ done — M046 dialpad self-extension hint removed; cold dialpad honestly blank, commit fec6741 (22-31 row 8)
~~22. M047 viewbinding-on-test-classpath check~~ done — M047 viewbinding-on-test-classpath confirmed harmless (AGP runtime; 22-31 row 8)
~~23. M048 ktlint/dprint ownership boundary check~~ done — M048 ktlint/dprint boundary re-verified (22-31 row 8)
~~24. M049 cross-repo 302-note to webphone~~ **→ TODO_LIST.md "Track webphone#21 to acceptance"** (note FILED upstream, CHANGELOG.md `[Unreleased]` J15/M049)
~~25. M050 detour CANCEL-leg emulator re-run (uiautomator)~~ **→ TODO_LIST.md "M050 re-run with the hardened harness"**
~~26. M051 ntfy ACTION_STOP stopped-flag pin~~ done — J16 ntfy stop-leak pin, meta-proven both ways, commit dd2fb12 (22-31 row 9)
~~27. M052 `wiringEnabled` pin + AttachGuardTest review~~ done at `dd2fb12` — `CallLayerWiringGateTest` pins the wiringEnabled default (CHANGELOG.md `[Unreleased]` "Test pins (J16)")
~~28. M053 one Robolectric SDK policy~~ done at `dd2fb12` — policy stated in CHANGELOG.md `[Unreleased]` "one Robolectric SDK policy (`app/src/test/resources/robolectric.properties`, sdk=34)"; the remaining `@Config(sdk = [31, 34])` classes are the documented loud exceptions

**Session metallurgy (new, grounded in this session):**

~~29. TODO↔plan parent cross-check lint script (see e1); wire into the gate~~ r: TODO_LIST.md "Adopt the process rules the 10-28/29 reports named into AGENTS.md"
~~30. Hand-commit each finished unit immediately (starve the daemon)~~ done — recorded in AGENTS.md "Daemon-blob remedy": "hand-commit finished units immediately so the daemon never gets the chance"
~~31. Make `mirror-lag` fetch+rev-parse a fixed session-end checklist item~~ **→ TODO_LIST.md "Push `main` to both remotes + `scripts/mirror-lag.sh` after the next hand commit"**
~~32. Index every new status report in `docs/status/README.md` at write time~~ done — `docs/status/README.md` indexes this report (line 29) and every later report
~~30. Run `check-rows` over TODO_LIST after structural table edits too~~ r: TODO_LIST.md "Adopt the process rules the 10-28/29 reports named into AGENTS.md"
~~34. Keep AGENTS lessons to compact bullets (net-zero growth toward M082)~~ **→ TODO_LIST.md "M082: AGENTS slim-down below 30 KB"**
~~31. Optional plan appendix: f-list↔M-row provenance map for auditors~~ r: TODO_LIST.md "TODO↔plan parent cross-check lint script"
~~32. Re-verify after ANY daemon commit: `git status` must be clean and~~ r: TODO_LIST.md "Adopt the process rules the 10-28/29 reports named into AGENTS.md"
`git log` must not contain new blobs eating finished work

**Waiting on the operator (gated highlights; the full 43 are in the plan):**

~~37. M001-M003 — the J01 bundle (deploy + PAT + billing): highest leverage~~ **→ TODO_LIST.md "Owner: run the SystemNix deploy", "Owner: regenerate the sync PAT", "Owner: fix GitHub Actions billing" (BLOCKED)**
~~38. Q1 test extension → fires M009-M020 (trains) + M054-M056 (contracts)~~ **→ ROADMAP.md "Open questions" — "Live-stack access (the device-train blocker)"**
~~39. M032-M033 — Forgejo token → cut v0.2.0 (sibling's lane)~~ done — CHANGELOG.md `[0.2.0] - 2026-10-28`: signed release APKs published to Forgejo releases
~~40. Pocket ID credential → fires M057-M062 (logged-in tail + live login)~~ **→ TODO_LIST.md "Verify the OIDC auth detour against the live Pocket ID" (BLOCKED)**
~~41. M063-M064 — assetlinks.json served + verify-app-links green~~ **→ TODO_LIST.md "Cross-repo: serve `/.well-known/assetlinks.json` from the stack"**
~~42. M065-M067 — KEEP-vs-PURGE answer → logout deregistration~~ **→ ROADMAP.md "Open questions" — "Logout semantics (drives the deregistration design)"**
~~43. M068-M070 — standdown nod + safe-area coordination~~ **→ TODO_LIST.md "Island-standdown seam" and "Cross-repo: island `safe-area-inset-top` fix"**
~~44. M074-M083 — repo meta, post-train docs, decisions, targetSdk 37 tail~~ **→ TODO_LIST.md "Repo meta tail", "Post-train docs sweep", "targetSdk 36 → 37"**

**Meta:**

~~45. Decide the next-track shape (see g3) before starting M021~~ **Superseded — the track ran as the J05-J16 agent track (CHANGELOG.md `[Unreleased]`); the shape question is moot.**
~~46. Keep the new TODO convention: every future TODO row gets a J-parent~~ done — TODO_LIST.md header: "Each row carries its SUPERB-plan parent (J-coarse / M-fine IDs)"
at add time, never retroactively
~~47. This report + README index + AGENTS bullets sit UNCOMMITTED by order~~ done at `befe000` — that commit adds this file + the `docs/status/README.md` index line + two AGENTS lessons
("wait for instructions"); hand-commit them with the next order before
the daemon blobs them
~~48. After the trains (J03/J04) land: M071-M073, M076-M078, M083-M085~~ **→ TODO_LIST.md "First device train" and "Device train for the 2026-11-25 batch"**
unblock automatically — nothing to do now

## g) THREE QUESTIONS I CANNOT ANSWER MYSELF

~~1. **Daemon policy:** when the auto-commit daemon blobs a finished~~ **→ ROADMAP.md "Open questions" — "Auto-commit daemon policy (§g, asked 2026-10-28 twice)"**
pre-push tree, is amend-in-place (this session's remedy, `7574c79`)
the blessed standard — or do you want blob PREVENTION instead
(hand-commit immediately after each unit, and/or pace/scope the daemon
in SystemNix)? I can repair blobs; only you can retire them.
~~2. **ID canon:** should J-/M-IDs become the project's canonical task~~ r: ROADMAP.md Open questions "Task vocabulary"
vocabulary — TODO rows, CHANGELOG entries, and future reports CITE
M-IDs instead of re-describing work? That cements today's re-parent as
the last retroactive one.
~~3. **Next-track shape:** execute the agent track (M021→M053, ~11h) as ONE~~ **Superseded — the track ran as the J05-J16 agent track (CHANGELOG.md `[Unreleased]`); the shape question is moot.**
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
