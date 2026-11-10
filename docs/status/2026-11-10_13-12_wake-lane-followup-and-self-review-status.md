# Status: wake-lane follow-up + the self-critique the report owes you (2026-11-10 13:12 CET)

**Scope line.** VERIFIED: `git log`/`git status` (main = `a02377e`, the
auto-commit daemon's sweep of the 11:48 status report; `d820810` remains the
session's only code commit; tree clean; both remotes were pushed to `d820810`
before the report blob landed). NOTHING new was researched, built, or tested
since the 11:48 report — per instruction, this file is the point-in-time
snapshot NOW: the same session state, plus the direct answers to "what did
you forget / what could you have done better / what could you still improve"
that the 11:48 report answered only implicitly. Do not read this as fresh
verification — every code claim was last proven by `scripts/verify-gate.sh`
exit 0 at ~11:35, before the 11:48 report.

**Where the previous report stands:** `docs/status/2026-11-10_11-48_no-ring-diagnosis-wake-lane-honesty-status.md`
(daemon-committed as `a02377e`). Its a–g sections remain the state inventory;
this follow-up adds the critique dimension and re-lists the state compactly so
this file stands alone.

## What did I FORGET?

| # | The forget                                                                                                                                                                                                                                                                                                                                               | Severity | Cost                                                                                                                                                                                                              |
| - | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | **Hand-commit finished units immediately.** I batched the commit until after the gate; the auto-commit daemon swept my mid-flight work into SEVEN `chore: auto-commit` blobs. AGENTS prescribes exactly this (the daemon-blob remedy says "hand-commit finished units immediately so the daemon never gets the chance") and I read it, yet still batched | Medium   | History noise; a careful soft-reset fold with the prescribed triple-check (paths-only-ours, lag counts printed, target ≠ origin/main) repaired it with zero loss — but the repair itself was risk I chose to take |
| 2 | **A persistent home for the diagnostic runbook.** The "why no ring" runbook (logcat filters, the two server switches, the pill verdicts) lives ONLY in the chat answer and this status file — both perishable. AGENTS/FEATURES got the wake-lane gotcha, but not the runbook                                                                             | Medium   | The next "why no ring" starts from archaeology again                                                                                                                                                              |
| 3 | **An UNKNOWN-masking window in my own design.** `registerPushDevice` 204 but socket never opens → ledger sits at UNKNOWN (no pill suffix) until the first onFailure (~10 s). I built honesty for every state except "accepted but never connected"                                                                                                       | Medium   | The pill can under-report in exactly the ntfy-unreachable scenario this whole session was about                                                                                                                   |
| 4 | **Meta-proof ordering.** I ran the break-fix cycle AFTER the first green gate. Breaking first is the same cost and proves earlier                                                                                                                                                                                                                        | Low      | One wasted gate cycle                                                                                                                                                                                             |
| 5 | **First watcher probe is +10 minutes.** I kept the watcher loop's `delay` FIRST; after wiring, the session could already be dead for 9 more minutes before the first probe                                                                                                                                                                               | Low      | Dead-session notice latency up to 10 min                                                                                                                                                                          |
| 6 | **Cross-repo TODO row.** The ring-E2E-proof obligation still lives only in webphone's TODO_LIST; this repo's TODO_LIST got no standing row                                                                                                                                                                                                               | Low      | Docs-health HARVEST will need to carry it across repos                                                                                                                                                            |
| 7 | **Robolectric-only visual coverage for a UI change.** The pill change shipped with label-content pins; no emulator train ran (not required by the AGENTS rule — that triggers on toolchain jumps — but this was a USER-VISIBLE change)                                                                                                                   | Low      | A rendering mistake the shadows cannot see reaches the phone                                                                                                                                                      |

## What could I have done BETTER?

| # | Improvement                                                                                                                                                                                                                                                         |
| - | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | Probe live-stack facts via the OWNER first thing (the 3 questions in §g of the 11:48 report should have opened the session, not closed it) — the session's biggest unknowns (`ringhook.enable`, `push.ntfy.url`) are two config values away from ending the mystery |
| 2 | Design WakeLane's state set against the FULL socket lifecycle before writing (STARTING/ACCEPTED-BUT-DEAD missing, see forget #3) — the data-model-first rule applies to state machines too                                                                          |
| 3 | Make the dead-session notice VISIBLE by default (min-priority channel is silent; a "ringing paused" notice nobody sees repeats the original sin) — I chose non-intrusive by instinct instead of by decision record                                                  |
| 4 | Ship the runbook as `scripts/ring-diagnose.sh` in the SAME commit as the honesty feature — the pill tells you WHAT is wrong, the script should tell you WHERE                                                                                                       |
| 5 | Break-fix meta-proof FIRST, then the full gate once                                                                                                                                                                                                                 |
| 6 | Small: `probeSession` duplicates request-building instead of reusing the shared seam; `assertTrue(... != null)` → `assertNotNull` in one pin (ktlint-neither, but the style bar)                                                                                    |

## What could I STILL improve (next sessions)?

1. WakeLane: add the STARTING/accepted-but-dead state (kill the UNKNOWN window).
2. First watcher probe immediately after wiring (loop body before delay).
3. Promote the 1004 notice to a visible channel or re-post strategy — WITH a recorded decision, not instinct.
4. `scripts/ring-diagnose.sh`: logcat filters + notification/battery grant state + a hint table keyed by pill verdict.
5. `ringhook.enable` + `push.ntfy.url` on the live host (owner switches) → then the E2E ring proof.
6. Device train: pill rendering, 1004 notice, FSI ring, battery-exemption ask — on the real phone.
7. ntfy read-auth decision: the subscribe sends NO Authorization header; anonymous-read-denied means a forever-reconnect loop. Needs the owner's ntfy config answer.
8. DISCONNECTED flicker: grace period or "retrying" flavor for transient reconnects.
9. Standing cross-repo ring-E2E row in THIS repo's TODO_LIST.
10. HARVEST the 11:48 report's §f (50 items) into TODO_LIST/ROADMAP (docs-health) — still not done.

## a) FULLY DONE (unchanged since 11:48, all under `d820810`)

Root-cause diagnosis (6-link chain, ranked modes) · WakeLane ledger + pill
verdict + reasons-as-contentDescription · flap-safe tri-state session probe
(only 401/403 tears down) · dead-session "Ringing paused" notification (id
1004) + lane stop + ledger · ntfy socket ARMED/DISCONNECTED reporting ·
REFUSED surfaced from registration · 9 new test pins, meta-proven (break →
FAIL → restore → pass) · FEATURES/AGENTS memory updates · pre-existing
TODO_LIST dprint drift fixed on sight · 7 daemon blobs folded into one honest
commit · pushed to origin AND github.

## b) PARTIALLY DONE

The no-ring FIX itself (app armable + honest; server switches unconfirmed;
phone unproven) · wake-lane visibility (UNKNOWN window) · notice latency
(≤10 min) · notice visibility (silent channel) · DISCONNECTED flicker
handling · cross-repo TODO row.

## c) NOT STARTED

Device train · server-side enablement + E2E ring proof · ntfy auth decision ·
`scripts/ring-diagnose.sh` · HARVEST of the 50-item list · pill-tap detail
surface.

## d) TOTALLY FUCKED UP

Nothing NEW is broken on disk: `main` = `a02377e` (docs blob) atop `d820810`
(the session's only code commit), last gate green, tree clean. The FUCKED-UP
ledger is inherited/process-shaped, not new damage: (1) the user's phone is
STILL silent in practice — the fix is code-complete but unproven on the device
and blocked on two server switches; (2) GitHub Actions billing still red
(every push since 2026-11-08 fails in seconds, jobs never start — owner-side);
(3) the mirror PAT still lacks `workflow` scope (hand-push remains the lane);
(4) my own process slip: 7 daemon blobs (repaired, zero loss).

## e) WHAT WE SHOULD IMPROVE

See "What could I have done BETTER?" above — that table IS section e,
answered directly instead of re-listed.

## f) Things to get done next

The 11:48 report's §f carries the full 50-item brainstorm (unchanged, still
unharvested). The sharpened TOP of that list after this critique: server
switches (2 owner values) → E2E ring proof on the phone → UNKNOWN-window fix →
first-probe-immediately → visible dead-session notice → `ring-diagnose.sh` →
ntfy auth decision → HARVEST.

## g) Questions I cannot figure out myself (unchanged, still open)

1. Has the phone EVER rung while closed (any build), or was this the first live attempt?
2. Is the live pbx host modifiable this week (`push.ntfy.url` + `ringhook.enable`), or must the E2E proof run on a VM train first?
3. Which phone/OEM + Android version runs the app, and did it arrive via Obtainium (release APK) or debug sideload?
