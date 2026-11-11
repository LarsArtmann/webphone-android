# Archived status reports — closed history

Every report here was ANNOTATED (each numbered item carries an inline
verdict: `done at <hash>` / `done — evidence` / `Won't implement` /
`**→ routed**` to TODO_LIST or ROADMAP) and then archived by the
2026-10-28 docs-health sweep. **Do not re-open resolved work from these
files** — the living backlog is `TODO_LIST.md`, decisions live in
`ROADMAP.md`. Tables mixing struck rows with routed-arrow rows carry a
`<!-- check-rows:ledger -->` exemption (per-row verdicts, by design).

## Archive manifest (2026-10-28 sweep)

| Report                                                 | Classification       | Deciding reason                                                                                                                  |
| ------------------------------------------------------ | -------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| 2026-10-24_17-44 working-app wiring + first docs audit | ANNOTATED → ARCHIVED | Every open item resolved: device-train/race routed to TODO (R15/R16/R20), CallStyle shipped (10-00 T21)                          |
| 2026-10-24_18-55 pareto execution + first device smoke | ANNOTATED → ARCHIVED | F055 proven (13-56), still-open bullets routed (trains, logout) or shipped (401 ladder, busy UX, CallStyle)                      |
| 2026-10-24_19-14 full status + brutal self-review      | ANNOTATED → ARCHIVED | All 43 bare f-items resolved (26 done with evidence, 17 routed to TODO/ROADMAP/FEATURES); b/c tables closed                      |
| 2026-10-25_03-12 full status + MVP path                | ANNOTATED → ARCHIVED | Same resolution set as 19-14 (restates it); MVP-onboarding row closed (README carries the 6-step path)                           |
| 2026-10-25_04-58 UI redesign                           | ANNOTATED → ARCHIVED | 42 f-items resolved; dark-mode premise corrected (09-59 shipped, 13-43 W101 proven); remainder routed                            |
| 2026-10-25_05-48 Forgejo + GitHub mirror wiring        | ANNOTATED → ARCHIVED | Owner actions routed (R12/N025-N028); default-branch + README + dependabot verification debts closed by the 06-27 audit          |
| 2026-10-25_06-27 docs-health audit #1                  | ANNOTATED → ARCHIVED | Its own "zero files archived" blocker is resolved by THIS sweep; f50 free resolutions closed (onAnswer logging verified in code) |
| 2026-10-25_09-59 WebView optimization                  | ANNOTATED → ARCHIVED | All 45 f-rows + b/c/e/g resolved (downloads, HTTP-error policy, dark-mode proof, stances → ROADMAP)                              |
| 2026-10-25_10-00 execution pass (core hardening)       | ANNOTATED → ARCHIVED | All 50 f-items + b/c/g resolved — the pass's own follow-ups shipped in 13-43/13-56 or routed to R-IDs                            |
| 2026-10-25_13-43 P06-P10 verification train            | ANNOTATED → ARCHIVED | 30 f-items + b/g resolved; the W104-W106 residual routed (R19)                                                                   |
| 2026-10-25_13-56 SUPERB pass                           | ANNOTATED → ARCHIVED | All 50 f-items + b/c resolved (W193 done, pushes done, island safe-area now a TODO row)                                          |
| 2026-10-25_14-33 annotation + pushes                   | ANNOTATED → ARCHIVED | W193-b executed by THIS sweep; harvest executed; owner rows routed (R12-R14, R24)                                                |
| 2026-10-27_15-11 deep links + in-shell OAuth           | ANNOTATED → ARCHIVED | The R01-R11 wave shipped (00-20); b1 premise-bust resolved by R03; operator questions routed                                     |
| 2026-10-28_00-20 R03 redirect truth                    | ANNOTATED → ARCHIVED | Post-report gate closed green (94/94); R07/R09/R10/R11 shipped; N024 = this sweep                                                |
| 2026-10-28_00-25 toolchain research (R28)              | ANNOTATED → ARCHIVED | Executed by R29 (01-33) — all 12 c-items + 50 f-items verified done, parked rows carried in TODO/ROADMAP                         |
| 2026-10-28_01-33 toolchain jump landed (R29)           | SKIP → ARCHIVED      | Closure report with zero numbered open items; residuals self-routed to TODO rows                                                 |
| 2026-10-28_01-35 post-jump status (R30)                | ANNOTATED → ARCHIVED | 50 f-items + b/c/e resolved; README audit + KGP-parity + wedge-proofing harvested into TODO this sweep                           |

## Archive manifest (2026-11-11 buildflow-gate sweep)

Cadence-driven (21 unarchived > threshold 15); audit-all struck to zero
first, then check-rows --strict + bare-rows gated clean BEFORE each git
mv. Open routed §f items carry the `> **Live remainder:**` banner.

| Report                                                    | Classification        | Deciding reason                                                                                                                                                               |
| --------------------------------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-10-28_01-36 SUPERB wave complete + wedge root-caused | ANNOTATED → ARCHIVED  | audit-all straggler #37 struck (watcher answered by the wake-lane rework); wrap artifact reflowed; rest routed (TODO/ROADMAP)                                                 |
| 2026-11-11_00-38 wave-3 batch meta-proven                 | ANNOTATED → ARCHIVED  | F71 struck (ROADMAP §6 = the plan's raw-idea parking set); landed state carried by 02-04                                                                                      |
| 2026-11-11_02-04 agent track complete                     | ROUTED → ARCHIVED     | Zero bare rows; open §f items harvested by the 11-43/12-57/13-01 chains (banner)                                                                                              |
| 2026-11-11_02-05 wave-3 landed (draft)                    | SUPERSEDED → ARCHIVED | Draft superseded by 02-04 minutes later (its own header says so); banner points at the owners                                                                                 |
| 2026-11-11_11-43 adb deploy 0.2.1 + release recon         | ROUTED → ARCHIVED     | Zero bare rows; §f tail harvested into TODO (device-deploy row) by 13-01 (banner)                                                                                             |
| 2026-11-11_13-01 both remotes public                      | ROUTED → ARCHIVED     | Freshness-verified green (§f harvested into TODO/ROADMAP) at archive time (banner)                                                                                            |
| 2026-10-28_02-49 docs-health mega-sweep                   | ROUTED → ARCHIVED     | §b table ledger-marked: 4 rows routed to fresh TODO rows (evidence-path sweep, AGENTS slimming, sibling archives, verify-gate), 1 moot; cadence headroom for the 14-56 report |
