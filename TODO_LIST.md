<!-- TODO_LIST.md — LIVING document. Items verified against code at add time.
     DONE items are REMOVED, not kept. History lives in CHANGELOG.md. -->

# TODO List

> Short-term, actionable, bounded work items, verified against the actual code.
> For long-term vision and unrefined ideas, use ROADMAP.md.
> Items are ranked by impact. Status is verified, not assumed.
> Evidence cites a code path plus the source status report
> (`docs/status/<date>_<name>.md` §section) or plan
> (`docs/planning/…`, P-IDs) for every row.

> 2026-10-25 (afternoon pass) rebuild: the W01 emulator verification pass
> (dark/light, offline overlay + Retry, renderer kill, back nav, uimode
> recreation, smoke dry-run, photos), the ntfy kill-cycle proof, the first
> `--release` smoke, the MockWebServer REGISTER soak, the WebView UX tail,
> the regression pins, the runCatching sweep + TelecomOutgoing generation,
> the docs decisions sweep, and the contrast gate (concurrent session) all
> SHIPPED and moved to CHANGELOG — 13 rows struck. Rows below are what
> remains; P-IDs refer to `docs/planning/2026-10-25_10-17_SUPERB-*`.

## High Impact

| Task                                                                                                                                                                                                                   | Status       | Impact | Effort | Evidence                                                                                                      |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ | ------ | ------ | ------------------------------------------------------------------------------------------------------------- |
| Owner: run the SystemNix deploy — phase 5 attaches the push mirror                                                                                                                                                     | 🔵 `BLOCKED` | High   | 10min  | Owner-only (`nix run .#deploy`, sudo); mirror is deploy-gated. Source: 05-48 §b1/f1 (P01)                     |
| Owner: regenerate the sync PAT with `repo`+`workflow` scope (sops secret)                                                                                                                                              | 🔵 `BLOCKED` | High   | 15min  | Unfreezes domains (114 behind) and this repo's `ci.yml` mirror pushes. Source: 05-48 §b2/f2 (P01)             |
| Owner: fix GitHub Actions billing (failed payment / spending limit) — the push trigger WORKS (runs fire), but jobs refuse to start: "recent account payments have failed or your spending limit needs to be increased" | 🔵 `BLOCKED` | High   | 10min  | Owner-only: GitHub Billing & plans, then re-watch the next push. No workflow defect. Source: 10-25 P08 (F041) |
| First device train: exercise the app end-to-end against the live stack                                                                                                                                                 | 🔵 `BLOCKED` | High   | 1 day  | Needs operator: stack origin + test extension (Q1). Source: 06-38 §T04, 10-00 §c (P04)                        |
| First physical install + first-run walk of the redesigned build                                                                                                                                                        | 🔵 `BLOCKED` | High   | 30min  | Needs the operator's phone. Source: 03-12 §f1, 04-58 §f1 (P03)                                                |
| Verify the answer-path race (push → FSI vs INVITE)                                                                                                                                                                     | 🔵 `BLOCKED` | High   | 2h     | Needs the live stack (Q1); plan task F017 measures it (P05)                                                   |
| Emulator: composer attach flow + external-link hand-off + tab-nav back stack with a LOGGED-IN session (W104/W105/W106 need the island login; the rest of W01 is verified)                                              | 🔵 `BLOCKED` | High   | 30min  | Pocket ID passkey = operator credential (Q1-adjacent). Source: 10-17 §P06 (W104-W106)                         |

## Medium Impact

| Task                                                                                                                                                                    | Status       | Impact | Effort | Evidence                                                                                                             |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ | ------ | ------ | -------------------------------------------------------------------------------------------------------------------- |
| Logout deregistration: JS bridge vs cookie-poll                                                                                                                         | 🔵 `BLOCKED` | Med    | 1h     | Operator picks KEEP vs PURGE (F045). Source: 06-38 §T14 (P12)                                                        |
| Live contract checks: session fields, payload shapes, CSRF                                                                                                              | 🔵 `BLOCKED` | Med    | 1h     | Needs the live stack (Q1); folded into the trains. Source: 06-38 §T15 (P04/P05)                                      |
| Island-standdown seam (webphone "native shell present" signal)                                                                                                          | 🔵 `BLOCKED` | Med    | 2h     | Owner nod first; duplicate registration observed. Source: 09-59 §f42 (P24)                                           |
| CallStyle device verification (post-time style + fallback)                                                                                                              | 🔵 `BLOCKED` | Med    | 30min  | Needs both trains. Source: 06-38 §F063 (P18)                                                                         |
| TalkBack device walkthrough + battery-prompt allow/deny verify                                                                                                          | 🔵 `BLOCKED` | Med    | 1h     | Code pass done; device + REGISTERED state needed. Source: 10-00 §f30 (P22)                                           |
| Cut the `v0.1.0` annotated tag (CHANGELOG entries are in; the tag itself is the operator's timing call)                                                                 | 🔵 `BLOCKED` | Med    | 5min   | Before-or-after the trains is the operator's call (F074b). Source: 10-00 §b (P11)                                    |
| Cross-repo: serve `/.well-known/assetlinks.json` from the stack (webphone repo, SHA256 of the install signing key) so the Android App Link verifies without the chooser | 🔵 `BLOCKED` | Med    | 30min  | Owner of both repos; android side shipped (filter + intake, 2026-10-27). Unverified links still open via the chooser |
| Verify the OIDC auth detour against the live Pocket ID (login completes in-shell, session mints in the WebView jar)                                                     | 🔵 `BLOCKED` | Med    | 30min  | Needs operator IdP credential; policy pinned in `WebSurfacePolicyTest`. Adjacent: W104-W106 (P06)                    |

## Low Impact

| Task                                                                                   | Status       | Impact  | Effort | Effort bar                                                                                        |
| -------------------------------------------------------------------------------------- | ------------ | ------- | ------ | ------------------------------------------------------------------------------------------------- |
| Repo meta tail: Forgejo description (needs token), icon parity (owner both-or-neither) | 🔵 `BLOCKED` | Low     | 30min  | Source: 06-38 §T25 (P26; photos/ gitignore + formatter + values-de decisions RESOLVED in ROADMAP) |
| Post-train docs sweep: honesty retirement, CHANGELOG cut for the next version          | 🔵 `BLOCKED` | Low-Med | 100min | Needs both trains green. Source: 06-38 §T27 (P27)                                                 |
