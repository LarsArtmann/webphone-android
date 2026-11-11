# Status — BuildFlow Gate Green: Fleet Gates, Docs Sweep, Formatter Lane (2026-11-11 14:56)

> **Session scope:** one pasted BuildFlow failure report ("18/43, 19
> findings, 2 failed steps") driven to `buildflow` EXIT=0 with all five
> docs gates PASS. Point-in-time snapshot; living docs remain the truth.
> Two OTHER sessions ran concurrently in this tree (the 13-39 ABI-gate
> session and the 14-21 v0.2.2 release cycle) — their work is cited, not
> re-audited here.

## a) FULLY DONE (verified this session)

- **cue-vet**: top-level `permissions: contents: read` added to
  `.github/workflows/ci.yml` (cue-policy #CIWorkflow fleet schema).
  Step green.
- **ruff EXE001**: `scripts/contrast-check.py` made executable. Step
  green.
- **shellcheck (9 findings)**: the three `SDK=` guards became
  `: "${ANDROID_HOME:?…}"` (emulator-smoke/photos/verify — train keeps
  `SDK`, it uses it); `ls|sort -V` → `find` in assetlinks.sh and both
  count sites in sync-wire-fixtures.sh; the unquoted `$DEVICES` listing
  in ring-diagnose.sh became a quoted `printf | sed` indent; the
  SC2016 `bash -c 'echo'` in verify-gate.sh became
  `nix develop -c printenv ANDROID_HOME` (resolution re-verified live).
  Direct shellcheck over `scripts/*.sh`: clean.
- **nix-fmt**: the flake now exports a `formatter` (a
  `writeShellScriptBin` walking `*.nix` via find). Plain `nixfmt`
  refuses the bare invocation `nix fmt` makes, and `pkgs.nixfmt-tree`
  pins its tree root to the store and dies inside buildflow's /tmp
  roundtrip — the wrapper is the only shape that passed. flake.nix
  re-canonicalized by nixfmt; both devShells re-evaluated OK; `nix fmt`
  idempotent. Step green.
- **docs-gates, all five gates PASS**:
  - _annotate_: 3 wrongly-struck routed items unstruck (routed canon =
    bare + arrow); one `non-issue` verdict re-led with the recognized
    `moot` marker; 4 inline-code-spacing repairs (AGENTS/FEATURES/
    superb-report/CHANGELOG, all `code`-suffix shapes reworded).
  - _freshness_: report 13-01 item 34 harvested into ROADMAP §5
    (`.forgejo/workflows` / gogenfilter tokens).
  - _audit-all_: all 19 unharvested historical items dispositioned —
    17 strikes via the annotate.go status-items tool (emit-keys →
    verify → apply), the two archived 13-56 arrows resolved by
    extending TODO's live-stack row to name AEC/NS and CANCEL-vs-BYE,
    and one phantom item (a wrap artifact putting `compileSdk` / `37.`
    at a line start) reflowed.
  - _cadence_: 21 → 15 live reports. Six zero-bare-row reports
    (01-36, 00-38, 02-04, 02-05, 11-43, 13-01) bannered with
    `> **Live remainder:**`, gated clean by `check-rows --strict` +
    `bare-rows` BEFORE `git mv`, archived with a six-row manifest in
    archived/README.md; the status README index rebuilt (15 rows,
    every link mechanically verified).
- **securitymd** (uncovered after the first fixes): SECURITY.md written
  to the gate's shape — Supported Versions, Security Practices,
  advisory link, response-time line.
- **vulture**: the urllib `opener.addheaders` false positive suppressed
  with `# noqa: V101` (annotation verified empirically: total 0).
- **Full gate**: `buildflow` EXIT=0, no ERROR block; `docs-gates run`
  5/5 PASS; dprint clean; regression notices remaining are timing-only
  (lychee/meta-lint/gitignore-upserter vs cold baselines).
- **Owning-repo fix**: buildflow's "run `docs-gates audit-all …`"
  advisory names a verb that does not exist — root-caused to
  docs-gates `pkg/provider/provider.go` (matrix gate name pasted into
  a CLI hint), fixed there with a `rerunHint` helper + test pin; both
  Go modules green (vet + tests).
- **Memory**: two gotchas recorded in AGENTS.md (the formatter trap,
  the fleet gates going live).

## b) PARTIALLY DONE

- **docs-gates provider fix**: source + tests done in the
  `~/projects/docs-gates` checkout, but UNCOMMITTED, unpushed, no
  release tag — and the deployed buildflow binary keeps emitting the
  wrong advisory until BuildFlow bumps its provider dependency. Fixed
  at source only.
- **Timing regressions** (lychee +88 %, meta-lint +750 %,
  gitignore-upserter +583 % vs baseline): observed, not investigated.
  Plausibly cold-cache/baseline noise (the README rebuild grew the
  link corpus), but that is suspicion, not proof.
- **`scripts/verify-gate.sh` never ran whole** on the final tree this
  session: its gradle legs (assemble + tests + ktlint) are the repo's
  own gate and were deliberately not started (idle gradle daemons from
  concurrent sessions; nothing Kotlin was touched); the one edited line
  was verified in isolation instead.

## c) NOT STARTED

- Deep annotation of the remaining unarchived reports (02-03: 46 bare
  rows, 02-23: 86, 12-57: 74, 01-40: 50, …) — only the audit-all
  flagged items were struck this session; the bare-row backlog is the
  next sweep's work.
- Any push: all of this is local (daemon commits only); both remotes
  untouched by this session, by policy.
- Emulator train: not run — no Kotlin/toolchain change in this session
  (scripts/docs/flake only), so the trigger did not fire.

## d) TOTALLY FUCKED UP (caught and repaired, but real)

- **The `rg -r` trap — hit TWICE more.** The exact three-strikes rule
  is IN this repo's AGENTS.md, and I still wrote `rg -rn …` and later
  `rg … -r ''` — both blocked by the hook, zero damage, full shame.
  Documented traps only work when read before the reflex.
- **`edit` mangled `runBattery`** in provider.go: my new_string dropped
  the function signature the old_string had captured — comment
  fragments glued, signature gone. Caught by immediate re-view,
  repaired, tests green after. Exact-match edits demand byte-level
  diff review of BOTH strings, every time.
- **`status-items` struck a wrapped item's first line only** (item 37's
  continuation left dangling, re-redding audit-all); hand-folded after.
  The skill documents the continuation hazard — I ran emit-keys but
  never checked the WRAP shape before applying. Same class as the
  2026-11-01 nix-email lesson.
- **README link typo**: wrote `rootkey` for `rootcause-passkey` in the
  12-57 row; my own link check caught it. Typing from memory instead
  of pasting.
- **Exit-code capture bug**: `buildflow | tail; echo $?` reports
  TAIL's exit, not buildflow's — the "green" claim was unproven until
  re-run with a clean redirect. Verification hygiene, not a nit.

## e) WHAT WE SHOULD IMPROVE

- Read the gate's expected shape BEFORE authoring for it: SECURITY.md
  was written twice because v1 guessed (a `--help`/source peek costs
  seconds).
- Archive to threshold-minus-one: this session landed exactly at 15,
  and THIS report makes 16 — the gate re-trips on the next honest
  report. The ritual needs headroom built in (handled ad hoc below).
- `annotate.go status-items` should refuse or warn when a numbered
  item's continuation lines are not folded into the strike (upstream
  docs-gates hardening; this session proved the failure mode again).
- Multi-session etiquette: three sessions edited one tree (ci.yml,
  FEATURES, CHANGELOG, 13-39 report all carried foreign changes). No
  collision damage occurred, but nothing but luck and the edit tool's
  mod-time check protected the shared files.
- Owning-repo fixes should be committed the moment they're green — the
  daemon blob is a worse commit message than the fix deserves.

## f) Up to 50 things we should get done next

1. Commit + push + tag the docs-gates `rerunHint` advisory fix in
   `~/projects/docs-gates` (it sits uncommitted; needs owner push
   authorization). `[SESSION]` (repo: docs-gates)
2. BuildFlow: bump the docs-gates provider dependency once tagged and
   rebuild the deployed binary — until then the audit-all advisory
   stays wrong in practice. `[TODO]` (repo: BuildFlow)
3. Re-run `bash scripts/verify-gate.sh` WHOLE on the final tree (the
   gradle legs have not seen this session's script edits end-to-end).
   `[SESSION]`
4. Annotate + archive `2026-10-28_02-03` (46 bare rows) — the oldest
   "not yet annotated" sibling report. `[TODO]`
5. Annotate + archive `2026-10-28_02-23` (86 bare rows) — the largest
   single-file debt. `[TODO]`
6. Annotate + archive `2026-11-11_12-57` (74 bare rows) once its §f
   chain is fully represented. `[TODO]`
7. Standing bare-row backlog sweep over the live reports (~400 rows at
   last count), keeping the status-README archive-section count in
   sync as part of the ritual. `[ROADMAP]`
8. Investigate the lychee timing regression (+88 %) — link count grew
   with the README rebuild; decide baseline-refresh vs real cost.
   `[TODO]`
9. Upstream to docs-gates: `status-items` continuation-fold guard
   (this session's item-37 miss is the repro; the hint tests already
   pin both message paths, only the guard is new). `[TODO]` (repo:
   docs-gates)
10. SECURITY.md: consider a `security.txt` once a real contact address
    exists (currently GitHub-advisory only). `[ROADMAP]`
11. Owner: GitHub Actions billing fix — every CI row stays `DISABLED`
    until then (TODO row exists; still the blocker). `[OWNER]`
12. Owner: re-scope the mirror sync PAT with `workflow` scope (the
    ABI-gate ci.yml commits currently mirror-drop). `[OWNER]`
13. Push `main` to both remotes and run `scripts/mirror-lag.sh` after
    the next hand commit. `[SESSION]`
14. Verify the 14-21 v0.2.2 release cycle finished (publish + `bump
    patch` ritual) — its report landed mid-flight relative to this
    session. `[SESSION]`
15. Evidence-path prefixing sweep over the archived reports (the ≈1,000
    verdicts still cite pre-archive `docs/status/…` paths; one `sed`
    pass, human-resolvable today). `[TODO]`
16. AGENTS.md slimming pass: the decision block duplicated between
    AGENTS gotchas and ROADMAP Decisions (36,995 bytes at the 02-49
    measure; larger now). `[TODO]`

## g) Questions for the owner (cannot be answered from this repo)

1. **Push authorization**: may I commit + push the docs-gates provider
   fix (and tag a patch release) in `~/projects/docs-gates`, or do you
   want to review the diff first? Same question for a BuildFlow
   dependency bump afterwards.
2. **Cadence policy**: threshold 15 with archive-to-14 headroom, or
   raise the threshold to 16-17 and keep archiving in batches? Either
   answers the "next honest report re-trips the gate" tension this
   session hit.
3. **Multi-session etiquette**: three agents shared this tree today
   (ci.yml/FEATURES/CHANGELOG edits interleaved). Do you want a
   convention (e.g. per-session branches, or a claim line in a living
   doc), or is last-writer-wins on shared docs acceptable?
