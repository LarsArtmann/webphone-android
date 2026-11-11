# Status Report — 2026-11-11 13:01 — Both Remotes Public + Release-Lane Token Unblocked

> **Live remainder:** open §f items live in TODO_LIST.md and ROADMAP.md (freshness-verified green at archive time; archived 2026-11-11).

**Session scope:** one task — "make this repo public" — executed end-to-end on a
two-remote repo (Forgejo canonical `origin`, GitHub mirror `github`), plus the
credential and documentation work the flip dragged in. All observations below are
from THIS session unless explicitly marked as a concurrent-session sighting.

## Scope line — what this report VERIFIED vs NOT

**VERIFIED (command/artifact):**

- Secret hygiene of the ENTIRE object store: `git cat-file --batch-all-objects
  --batch | grep -aoE "BEGIN [A-Z ]*PRIVATE KEY|keystore\.jks"` → zero hits;
  history filename audit (`git log --all --diff-filter=A --name-only`) → one
  status doc, content-checked clean; HEAD literal scan → only RFC 2617 test
  vectors (`Circle Of Life`, `fixture-topic`).
- GitHub visibility: `gh repo view --json visibility` → `PUBLIC`.
- Forgejo visibility: unauthenticated `GET /api/v1/repos/lars/webphone-android`
  → HTTP 200 on BOTH `http://127.0.0.1:3000` and `https://forgejo.home.lan`;
  `scripts/release.sh --doctor` → `repo: lars/webphone-android (private=False)`.
- Release-lane token: `--doctor` → `token: valid for lars` (scopes
  `write:repository,read:user`), file `~/.config/webphone-android/forgejo-token`
  is 40 chars, chmod 600.
- Sops token content: decrypted (in RAM, per the SystemNix sanctioned recipe) and
  inspected length-only → literally `CHANGE_ME` (9 chars).
- Doc commits: `4b9cc33` (TODO_LIST rows); AGENTS.md status/mirrors edits landed
  via daemon blob `f1250a1`; the token-scope gotcha fix landed at report time.
- Remote parity at 13:01: `git rev-list --count origin/main..HEAD` → `0`.

**NOT verified (and why):**

- The concurrent session's v0.2.1 publish (`52d0f93` "bump version to 0.2.2
  after publishing v0.2.1", seen in `git log` at 13:01) — I did not probe the
  forge releases endpoint, the APK asset, or the phone; out of this session's
  task and the user asked for no unrelated research.
- CI actually executing on public GitHub (billing unblock is a hypothesis, not a
  proven run).
- Tree health after the foreign `WebSurface.kt` import reorder (concurrent
  session's change; verified diff is import-order-only, but no verify-gate run).
- The superseded `webphone-android-release` token still being live (not re-probed
  after the finding — deletion has no CLI path).
- Anything on the phone (no device attached to this session).

## a) FULLY DONE

1. **Pre-public secret audit of everything in git** — every object scanned for
   private-key/keystore material (zero), full history filenames swept, HEAD
   literal patterns swept. Verdict: clean to publish. (This gate is what made
   the flip responsible rather than reckless.)
2. **GitHub flip → PUBLIC**, verified via API (`gh repo view`).
3. **Forgejo flip → public** — `PATCH /repos/lars/webphone-android
   {"private": false}` over the local API (`http://127.0.0.1:3000`, no TLS
   hassle) as user `lars`; anonymous read proven on localhost AND
   `forgejo.home.lan`.
4. **Release-lane credential unblocked** — the 0-byte `forgejo-token` placeholder
   is now a real, correctly-scoped token (`write:repository,read:user`, name
   `webphone-android`), stored 0600, and `release.sh --doctor` is fully green
   for the first time. This unblocks the publish half of the update lane
   (TODO_LIST J09's token blocker is gone).
5. **Discovery: the sops `forgejo_token` in SystemNix is a fake** — literal
   `CHANGE_ME`, never a credential. Any consumer trusting sops-as-source-of-truth
   silently holds a placeholder. Flagged in AGENTS.md.
6. **Docs brought honest**: AGENTS.md status paragraph + "Repository & mirrors"
   section rewritten (public since 2026-11-11, token provenance, superseded-token
   note); TODO_LIST release row (token DONE, blocker moved to the numbering
   decision) and Obtainium row (forge token now OPTIONAL — public reads).
7. **AGENTS.md token-scope gotcha corrected at report time** — the old line
   "Tokens need `write:repository` to publish" was WRONG: the release script's
   preflight `GET /api/v1/user` demands `read:user` (the doctor 403'd the
   write-only token). Now states `write:repository,read:user` + phone-side
   optionality.
8. **Daemon-blob triage done by the book** — blob `f1250a1` swept my AGENTS.md +
   TODO_LIST edits together with a FOREIGN `WebSurface.kt` import reorder;
   amend correctly REFUSED (not solely this session's paths), foreign diff
   diagnosed as pure ktlint import ordering (benign, likely a concurrent
   gate session).

## b) PARTIALLY DONE

1. **"Public" ≠ "public-ready".** LICENSE exists (verified). Missing: SECURITY.md,
   CONTRIBUTING.md, repo description/topics/website metadata on both forges,
   GitHub secret scanning + push protection (free on public repos, not enabled),
   Dependabot alerts (not enabled).
2. **The publish lane is proven only at preflight depth in THIS session**
   (`--doctor`). The first real publish happened in a concurrent session
   (git log sighting only, see Scope line).
3. **Mirror health**: parity (`origin/main..HEAD` = 0) reached at 13:01 — but by
   a concurrent push, not by me; the standing "hand-push both remotes" ritual
   and `scripts/mirror-lag.sh` cadence are untested by this session.
4. **Token hygiene is half-done**: the working token is minted/stored, but the
   superseded `webphone-android-release` token (write-only) could not be deleted
   (no CLI subcommand, API deletion needs scopes the working token deliberately
   lacks) — it sits live on the forge.

## c) NOT STARTED

1. Superseded forge token revocation (UI or an admin-scoped API call).
2. sops `forgejo_token` placeholder repair (SystemNix-side: sudo sops edit, or
   delete the key + document the runtime mint path).
3. Public-presence docs and forge metadata (see b1) — nothing created.
4. GitHub secret scanning / push protection / Dependabot alerts — not enabled.
5. All device/owner-gated TODO_LIST rows (assetlinks stack half, live OIDC login,
   Obtainium phone connect, wake-lane E2E ring proof, targetSdk 37 train, …) —
   untouched, by design (this session's task never opened that front).

## d) TOTALLY FUCKED UP

**Nothing broken or destructive shipped.** Honest near-misses, all caught in
session:

1. **First token mint stranded an orphan credential.** `--token-name` is unique
   per user; the re-mint under the same name failed ("access token name has been
   used already"), so a write-only token named `webphone-android-release` stays
   live on the forge with its value known to no one (never persisted). Wasted a
   cycle; leaves cruft that only a UI/API action can remove.
2. **The doctor REJECTED my first token** (403, `read:user`) — meaning the
   AGENTS.md token-scope guidance was wrong until today and I initially minted to
   that wrong spec. The repo's own memory lied; the preflight caught it. Fixed at
   report time (a7).
3. **A fake credential sat in real infrastructure.** `secrets.yaml:forgejo_token
   = CHANGE_ME` would have silently failed any consumer that trusted sops
   presence as credential existence (and DID mislead me for one probe).
4. **History hygiene nick, not broken**: the session's main AGENTS.md change is
   entombed in a `chore: auto-commit 3 changed file(s)` blob message because a
   foreign file rode along — the amend remedy was correctly refused, so the blob
   stands.

## e) WHAT WE SHOULD IMPROVE

1. **Probe before minting**: check token-name uniqueness / list existing tokens
   first; use one stable name per purpose and never re-mint under it.
2. **Codify the forge-CLI mint path.** This session did live shell archaeology on
   a store-hashed binary (`/nix/store/04vs…-forgejo-lts-15.0.11/bin/forgejo`).
   A NixOS upgrade invalidates that path — document stable discovery
   (`systemctl cat forgejo | grep ExecStart`) + the exact `--config
   /var/lib/forgejo/custom/conf/app.ini` invocation in AGENTS.md or a script.
3. **Never trust sops key PRESENCE as credential EXISTENCE** — a probe against
   the real consumer (what `--doctor` does) is the only validation that counts.
   `CHANGE_ME` proved the point.
4. **Ending-state claims need freshness stamps**: my closing line "remotes lag
   local HEAD" was stale within the hour (concurrent push). On a repo with a
   concurrent scheduler, state claims should carry `as of <time>` or be re-probed
   at the end.
5. **Foreign worktree changes**: diagnose AND run the relevant gate (or explicitly
   hand off) — I described the `WebSurface.kt` reorder but ran no gate over it.
6. **Scope documentation should be empirical**: AGENTS.md said `write:repository`
   sufficed for publishing; one doctor run against the real script disproved it.
   Write scope/contract docs from probe results, not from memory.

## f) Up to 50 things we should get done next

Ranked by impact; tags: `[SESSION]` born this session, `[TODO]` already on
TODO_LIST/known, `[NEW]` public-presence, `[ROADMAP]` fuel — most of these are
brainstorm, not commitment; docs-health HARVEST must apply routing rigor.

1. Verify the concurrent v0.2.1 publish actually landed COMPLETE on the forge
   (tag + `.apk` asset — an asset-less release is invisible to Obtainium). `[SESSION]`
2. Confirm the phone's next update path: install 0.2.1 via Obtainium, then 0.2.2
   as the first real OTA cycle. `[TODO]`
3. Revoke the superseded `webphone-android-release` forge token. `[SESSION]`
4. Enable GitHub secret scanning + push protection (free on public repos). `[NEW]`
5. Enable Dependabot alerts on the public repo. `[NEW]`
6. Add repo description + topics + website link on GitHub and Forgejo. `[NEW]`
7. SECURITY.md (how to report — the operator stack is now publicly named). `[NEW]`
8. CONTRIBUTING.md (verify-gate contract, dprint/ktlint, flake-only builds). `[NEW]`
9. README front-door pass for strangers: loud LAN/VPN-only caveat for the forge
   install path. `[NEW]`
10. Fix the sops `forgejo_token` = `CHANGE_ME` placeholder in SystemNix. `[SESSION]`
11. Codify the forge-CLI token-mint recipe (stable binary discovery + scopes) in
    AGENTS.md or `scripts/`. `[SESSION]`
12. Verify CI runs on public GitHub (billing unblock hypothesis → proof). `[SESSION]`
13. Regenerate the mirror PAT with `workflow` scope (the standing mirror trap). `[TODO]`
14. Serve `assetlinks.json` on `pbx.artmann.tech` AND the IdP host (App Links +
    WebView passkeys, cross-repo). `[TODO]`
15. Live Pocket ID OIDC login proof in-shell (R17). `[TODO]`
16. Connect the phone to Obtainium (forge token now optional). `[TODO]`
17. Wake-lane E2E ring proof (server `ringhook.enable` + `push.ntfy.*`). `[TODO]`
18. targetSdk 36 → 37 device train (Android 17 behavior validation). `[TODO]`
19. API 37 emulator image (the deliberate devShell split TODO row). `[TODO]`
20. Island `safe-area-inset-top` fix (cross-repo webphone). `[TODO]`
21. TalkBack device walkthrough. `[TODO]`
22. CallStyle post-time device verification. `[TODO]`
23. Battery-prompt allow/deny device verify. `[TODO]`
24. Island-standdown seam (owner nod first). `[TODO]`
25. Re-run `scripts/ring-diagnose.sh` recon (POST_NOTIFICATIONS + battery
    whitelist) on the next "didn't ring" report. `[TODO]`
26. Verify release-key backup exists OFF this machine (`release.keystore` +
    `release.properties` — losing them bricks every installed device). `[SESSION]`
27. Run `release.sh --dry-run` with the fresh token as a dress rehearsal for the
    0.2.2 publish. `[SESSION]`
28. After 0.2.2 publishes: `bump patch` ritual → 0.2.3. `[TODO]`
29. Decide GitHub's role: community front door vs mirror-only (drives 6–9, 31,
    45, 44). `[NEW]` — also question g2
30. Self-host docs for a public audience: pointing the shell at YOUR stack. `[NEW]`
31. GitHub issue templates + public triage policy. `[NEW]`
32. Verify anonymous HTTPS git clone works on the public Forgejo repo (LAN/VPN). `[SESSION]`
33. Sanity-probe the public Forgejo API surface for anything unintended now that
    anonymous read is on. `[NEW]`
34. `.forgejo/workflows` CI on the forge side (gogenfilter pilot pattern). `[ROADMAP]`
35. Gitleaks CI job is now viable on a PUBLIC repo (the hosted-minutes caveat is
    gone) — port the SystemNix pattern. `[SESSION]`
36. CI badge in README once runs are proven green. `[NEW]`
37. Record the §g1 numbering outcome (v0.2.1-as-bookkeeping, 0.2.2 next) wherever
    the concurrent session left it half-recorded. `[SESSION]`
38. Verify CHANGELOG carries the 0.2.2 entry post-bump. `[SESSION]`
39. Run the wire-contract resync ritual if webphone moved (`--check` gate). `[TODO]`
40. Run the verify gate over the current tree (the foreign `WebSurface.kt`
    reorder + my doc edits have never had a green gate this session). `[SESSION]`
41. Emulator train re-run to prove install/launch of the 0.2.2 tree. `[TODO]`
42. docs-health HARVEST: route this report's (f) items into TODO_LIST/ROADMAP. `[SESSION]`
43. Document the token-name convention in AGENTS.md (one name per purpose,
    scopes spelled out). `[SESSION]`
44. Annotate TODO_LIST rows 68/69 with the actual publish outcome once the forge
    state is probed (1). `[SESSION]`
45. Grep the repo for any remaining "private" claims now stale after the flip. `[SESSION]`
46. FCM lane go/no-go decision (dormant since v0.1 — re-affirm or plan). `[ROADMAP]`
47. Public docs: is `pbx.artmann.tech` as shipped default acceptable long-term
    once strangers build the app? (operator policy call) `[ROADMAP]`
48. Obtainium "Allow insecure HTTP requests" caveat: keep loud in public README
    (it is NOT a pattern to copy). `[TODO]`
49. App-Link user-selection state on the phone post-assetlinks
    (`pm set-app-links-user-selection`). `[TODO]`
50. First-run experience for a stranger WITHOUT the operator's stack: a saved
    origin flow test in Settings (default origin is operator-specific). `[ROADMAP]`

## g) Questions I can NOT figure out myself

1. **The concurrent v0.2.1 publish** — was that session's publish human-approved
   and phone-verified, or should this session's successor treat it as unproven
   and start with (f) items 1–2 (probe the forge release asset + phone state)?
2. **Public presence scope** — is GitHub meant to become the community front
   door (issues open, README landing page, self-host docs, SECURITY/
   CONTRIBUTING), or does the LAN forge stay the canonical-personal surface and
   GitHub remain a mirror? This decides items 6–9, 29, 31, 36.
3. **Push ritual after parity** — AGENTS.md mandates hand-pushing BOTH remotes
   until `scripts/mirror-lag.sh` proves zero lag; parity held at 13:01. Should
   the ritual downgrade to "push origin, let the mirror carry" now, or keep the
   double-push discipline?
