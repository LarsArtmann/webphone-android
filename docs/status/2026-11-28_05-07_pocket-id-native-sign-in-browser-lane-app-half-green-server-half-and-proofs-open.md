# Status Report — 2026-11-28 05:07 CET — Pocket ID sign-in natively: the browser-lane app half is green on its own classes; the server half, the whole gate and every on-device proof are still open

**Tree state at report time:** `HEAD` = `6f314a6`, working tree CLEAN. Every
change of this session was swept into history by the auto-commit daemon as
13 `chore: auto-commit` blobs between 04:18 and 05:00 (I committed nothing,
pushed nothing). The last WHOLE-gate proof on record is `3b101e5`
(`.gate-last-green`, 01:28, 244 JUnit cases) and PREDATES this session: the
gate has not been run on any tree that contains this work. What is proven on
`6f314a6` is narrower: the seven test classes this session touched or added
pass, 119/119 cases on API 31 and 34 (`/tmp/wp-research/test4.log`, 3m45s).
Android Lint, `ktlintCheck` (only `ktlintFormat` ran), the release build,
`scripts/test-count-check.py`, the emulator train and `dprint check` have NOT
run on it.

**Two remote facts the owner should know now:**

- `origin/main` (Forgejo) = `df43767` (04:35:29). That is one of MY
  intermediate states: `WebSurfacePolicyTest.kt` carried a duplicated block of
  seven test functions (23 `@Test` instead of 16), so its TEST sources do not
  compile at that commit (conflicting declarations — by construction; I did
  not compile that commit to check). I did not push it; something did. Local
  `HEAD` is five commits ahead and repaired.
- `github/main` = `e0787d9`, older than the session's start commit.

**Sibling repo `~/projects/webphone`:** read only, NOT modified (clean tree;
a second session is writing `docs/status/` there).

Scope: this session's run only (2026-11-28 ~03:49 to 05:07 CET).

## What was asked, and what I set out to do

The question was "How can we support PocketId based OAuth natively?". My
answer was approach A: run the OIDC dance in the SYSTEM browser (Chrome Custom
Tabs) and let the stack hand the finished session back to the shell through a
one-time ticket. The instruction that followed was to break it down, execute
and verify step by step until it works. In that answer I had scoped the work
as "write the cross-repo draft in `docs/drafts/` and implement the app half".

## Research findings this session produced (new facts, with their sources)

These changed the plan and invalidate parts of the repo's current docs:

- **The in-WebView Pocket ID login cannot work, with or without
  `assetlinks.json`.** In a WebView's FOR_APP WebAuthn mode Android signs the
  ceremony with the APP origin (`android:apk-key-hash:…`), and Pocket ID
  accepts exactly its own App URL: `RPOrigins: []string{deps.AppURL}` in
  `backend/internal/webauthn/service.go` (read from the repo on 2026-11-28).
  The request to widen it, pocket-id/pocket-id#1845, was closed NOT_PLANNED
  on 2026-10-15 with "Doing OAuth authentication in a web view is considered
  bad practice … open the login page in an external browser". So the TODO
  row "serve `assetlinks.json` on `auth.larsartmann.cloud` with
  `get_login_creds`" (TODO_LIST "Cross-repo: serve … on BOTH hosts") cannot
  unblock Pocket ID. The `pbx.artmann.tech` half (App Links,
  `handle_all_urls`) is unaffected by this. Source-level evidence only; not
  device-proven, and the live Pocket ID version was not checked.
- **RFC 8252 §8.12**: native apps "MUST NOT use embedded user-agents to
  perform authorization requests". §7.1: a private-use scheme "MUST use a URI
  scheme based on a domain name under their control, expressed in reverse
   order".
- **The application id's domain is not registered** (as far as this host can
  see): `larsartmann.software` has no A record and answers no NS query;
  `larsartmann.cloud` has NS records. Hence the return scheme
  `cloud.larsartmann.webphone`, not the application id.
- **`androidx.browser` 1.10.0** is the newest release on Google Maven
  (`maven-metadata.xml`, last updated 2026-03-25); minSdk 23; transitively
  core 1.10, activity 1.9, kotlin-stdlib 2.1.20, guava `listenablefuture`.
- **Live IdP**: issuer `https://auth.larsartmann.cloud`, authorize
  `/authorize`, token `/api/oidc/token`; `/.well-known/assetlinks.json` still
  404.
- **Chromium's Android WebAuthn README** (`components/webauthn/android`): in
  APP mode origin verification is deferred to the platform; in CHROME mode
  the request goes to Credential Manager / GMS Core with no app-origin step.
  My reading that the IdP host needs nothing from us for the browser lane
  follows from that, but is an INFERENCE.
- The `agentic_fetch` tool is out of credits (HTTP "payment required"); I
  read the sources with plain `fetch`/`gh api` instead.

## The contract the app half is written against

Server side (`~/projects/webphone`): **nothing of this exists yet.**

| Hop | Wire | Purpose |
| --- | --- | --- |
| Capability | `GET /api/auth/oauth2/shell` → `204` (404 = stack never heard of it) | The shell must know before it opens a browser |
| Begin | `GET /api/auth/oauth2/begin?shell=android&app_state=<nonce>` | Names the caller; carries the shell's nonce |
| Return | callback → `303 cloud.larsartmann.webphone://auth/finish?ticket=<43 chars>&app_state=<nonce>`, no session cookie for the browser | The system launches the shell with the ticket |
| Redeem | `GET /api/auth/oauth2/finish?ticket=…` + header `X-WebPhone-Shell: android` → `303 /` with the session cookie, or `303 /login?sso_error=stale` | One-time, short-lived ticket becomes a cookie in the shell's jar |

## a) FULLY DONE

App half, on `6f314a6` (16 files, +1872/−19 lines against `19d3405`):

- **Dependency and manifest:** `androidx.browser:browser:1.10.0` in the
  catalog and `app/build.gradle.kts`; `<queries>` for `VIEW`+`BROWSABLE`+`https`
  (package visibility); a VIEW intent filter pinned to
  `cloud.larsartmann.webphone://auth/finish` on `MainActivity`.
- **`shell/SignInHandoff.kt` (new, 328 lines):** the return-address constants;
  `SignInAttempt` (32-byte base64url nonce, persisted in prefs
  `webphone-signin-handoff`, 10-minute TTL, dies with its stack) with the pure
  `verdictFor` (`ACCEPTED`/`OTHER_STACK`/`EXPIRED`/`OTHER_ATTEMPT`); the
  `SignInHandoff` object: `learnSupport()` (single-flight probe per origin),
  `support()`/`awaitSupport()` (keyed by origin), `teach()` (the pin seam),
  `begin()` (launch URL + attempt), `takeTicket()` (nonce fence, single use,
  a foreign link never voids a dance in flight), and the real launcher
  `openInBrowser()` (Custom Tab → plain browser tab → false).
- **`shell/DeepLinkTarget.kt`:** `DeepLinkTarget.SignInReturn(ticket, appState)`
  for the exact host and path, both facts required and shape-checked;
  lookalikes are `None`.
- **`shell/SessionApi.kt`:** `HandoffSupport {SUPPORTED, UNSUPPORTED,
  UNREACHABLE}` + pure classifier (2xx / 404 / everything else);
  `probeSignInHandoff()`; `HandoffRedemption {Landed(landing), Failed}` +
  pure `handoffLanding` (only the contract's 303 with an origin-relative
  path); `redeemSignInHandoff()` on a redirect-returning client with the
  shell header; a ticket outside `[A-Za-z0-9_-]{16,128}` is never presented.
- **`shell/CallLayer.kt`:** the signed-out branch of one wire attempt is now
  `onSignedOut()` (badge OFF + learn the stack's handoff support), so the
  verdict is normally in hand before the user reaches the login card.
- **`shell/WebSurface.kt`:** pure `signInLane()` (`NOT_ENTRY`/`BROWSER`/
  `AWAIT_SUPPORT`/`DETOUR`); `shouldOverrideUrlLoading` intercepts only a
  main-frame GET to the exact begin hop; a held tap (`AWAIT_SUPPORT`, one at
  a time, 5 s bound, abandoned wherever the detour dies) resumes on the main
  thread; `handOffToBrowser()`.
- **`shell/MainActivity.kt`:** `completeSignInHandoff()` (claim, redeem off
  the UI thread) and `applySignInHandoff()` (land where the stack says, or
  return to the origin root with a toast; `CallLayer.rewire` either way);
  the `handoffRedeemer` seam.
- **`strings.xml`:** `signin_handoff_failed`.
- **KDoc truth fix in `WebSurface.optInToPasskeys`:** it claimed "the
  app-side prerequisite is DONE here"; it now states the Pocket ID verdict.
- **Tests:** `@Test` count 175 → 218 (+43): new `SignInHandoffTest` (15),
  `SignInLaneTest` (4), `SessionApiHandoffTest` (9, MockWebServer through
  the real client and the real cookie bridge), `MainActivitySignInHandoffTest`
  (6, cold and warm intake); `DeepLinkTargetTest` +2; `WebSurfacePolicyTest`
  +7 (and it gained the `@Config(sdk = [31, 34])` pin it was missing, so its
  16 tests are 32 cases). Touched-class run: 119/119 green.

## b) PARTIALLY DONE

- **The feature end to end.** The app half is complete and unit-proven; the
  server half is not written, so on today's live stack the probe answers 404,
  the verdict is `UNSUPPORTED`, and every sign-in takes the OLD WebView lane.
  That is the designed no-regression behaviour, but it also means the new
  lane has never run for real, anywhere.
- **Verification.** One targeted test run green. Not run: the whole suite
  (expected ≈ 244 + 84 new cases, a guess), `ktlintCheck`, Android Lint
  (`QueryPermissionsNeeded` and friends are untested), `assembleRelease`
  (R8 with `androidx.browser`), zipalign/ABI/contrast/dprint, the nine-step
  gate, the emulator train that AGENTS asks for after a dependency jump.
- **Documentation inside the code.** Several KDoc blocks and one manifest
  comment cite `internal/server/auth/sso_handoff.go`, webphone's
  `AndroidShellFinishURL` and "the cross-repo note under `docs/drafts/`".
  None of those three exists today. They are true only once the server half
  and the draft are written; until then they are promises in comments.

## c) NOT STARTED

- The cross-repo draft in `docs/drafts/` (the one deliverable I named first).
- The server half in `~/projects/webphone`: shell cookie on `begin`, ticket
  callback, `finish` redemption, capability route, Go tests, its CHANGELOG
  line, its gates.
- The on-device proof of the ONE assumption everything rests on: that Chrome
  (Custom Tab) follows the stack's 303 to a custom-scheme URI and launches
  the app. Also unproven: that the Custom Tab lives in the shell's task and
  closes on return; that Pocket ID's passkey ceremony completes in that
  browser.
- The loopback live-check extension (`scripts/loopback-live-check.sh` +
  `LoopbackLiveContractTest`) against a real webphone binary.
- Every living doc: `AGENTS.md` (architecture table, control flow, gotchas,
  and its stale counts "165 `@Test`, 230 JUnit cases"),
  `docs/DOMAIN_LANGUAGE.md` (sign-in handoff, handoff ticket, shell nonce,
  the two lanes), `FEATURES.md`, `TODO_LIST.md` (the dead assetlinks-on-IdP
  row; new rows for the server half and the device train), `ROADMAP.md` (the
  "OAuth outside the WebView" raw idea is now half shipped), `CHANGELOG.md`
  `[Unreleased]`, the README honesty markers.
- Annotating `docs/drafts/2026-11-11_stack-serve-assetlinks-on-idp-host-for-webview-passkeys.md`
  as superseded for the Pocket ID purpose.
- `scripts/meta-prove.sh` on the new pins.

## d) TOTALLY FUCKED UP (own mistakes)

All repaired on disk; two of them live on in history:

- **A non-compiling commit is in local history.** I wrote `SignInHandoff.kt`
  with a reference to a private function of another object
  (`SignInAttempt.of(preferences)`); the daemon committed that state as
  `cbfb75e` (04:20:37) before my first compile (04:25) showed it. Repaired in
  `d2844f5`.
- **A second one is on `origin`.** `WebSurfacePolicyTest.kt` got the seven
  new lane tests twice: a `multiedit` reported "Applied 3 of 4 edits (1
  failed)" while the fourth HAD applied; I believed the report and appended
  the block again. The daemon committed that as `df43767` (04:35:29), and
  `origin/main` is sitting on it. Repaired in `e420018`.
- **Edits without a prior `View`** (three rejected edits at the start; no
  damage).
- **I ignored this repo's own gotcha "javap-first for platform/shadow
  APIs":** I used `ShadowInstrumentation.checkActivities`, which is
  package-private, and the deprecated `CookieManager.removeAllCookie()`; two
  compile failures under warnings-as-errors that a `javap` would have
  prevented.
- **Sloppy first drafts I caught only on re-reading:** a recursive no-op
  helper left at the end of a test file, a nonsense assertion
  (`handoffLanding(303, "///")` expected `"/fax"`), a helper that tested
  `Uri` instead of my code, and one vacuous test, all removed before the
  first run.
- **Three tests were wrong, not the code:** the "real launcher" tests used
  the recorder seam instead of the real launcher; a foreign lookalike was
  asserted to stay in the shell (the ordinary verdict correctly hands it
  off); a `Secure` cookie was expected to store over loopback `http`.
- **I wrote the feature top-down in large single writes** across eight
  production files before the first test existed, on a repo where the daemon
  commits every two to three minutes. Small compile-test-first steps would
  have kept both broken commits out of history.

## e) WHAT WE SHOULD IMPROVE

- **Order of work:** draft and contract first, then the thinnest end-to-end
  slice proven on a device (the browser hop alone needs no server change: a
  stub that 303s to the return URI proves it), then the breadth of tests and
  polish. I built the breadth first and the riskiest assumption is still
  untested.
- **Never trust a partial-failure report from a batch edit; grep the file
  after every batch.** And keep each on-disk state compiling: the daemon
  commits within minutes and something pushes to `origin`.
- **Comments must not cite files that do not exist.** Land the server file
  and the draft, or reword the four sites.
- **`AWAIT_SUPPORT` is silent for up to five seconds.** A held tap shows
  nothing; a second tap is swallowed. It needs a visible state (the offline
  overlay's sibling, or a toast) and should die on Back.
- **Probe chatter:** `onSignedOut()` re-asks the stack on every resume while
  signed out. One verdict per origin per process would do, re-asked only on
  an origin change or a forced retry.
- **Failure UX in the browser:** an IdP refusal or a cancel lands the
  Custom Tab on `/login?sso_error=…` inside the BROWSER, where the extension
  form also lives; the shell's pending attempt then lingers until its TTL.
  A relay of the failure back to the shell (`…&error=<code>`) would keep the
  user in one surface.
- **The stale premise in the docs:** the repo still tells every reader the
  IdP-host `assetlinks.json` is the missing half of Pocket ID login. It is
  not; that row and the 2026-11-11 draft mislead the next session.
- **Housekeeping that drifted:** `AGENTS.md` test counts (165/230) were
  already stale before this session (175 `@Test` at `19d3405`, 244 cases per
  the 01:27 report).
- **Machine load:** load average 32 → 65 during this session; the targeted
  run took 3m45s for 119 cases. Two sessions building at once would be worse
  than slow.

## f) NEXT (ranked)

1. Run `bash scripts/verify-gate.sh` WHOLE on `HEAD`; read `.gate-last-green`. Nothing else is worth much before that.
2. Owner: decide the push (see g2). `origin/main` is at a commit whose tests do not compile.
3. Owner: decide the server-half scope (see g1).
4. Emulator proof of the browser hop, server-independent: stub on the host that 303s to `cloud.larsartmann.webphone://auth/finish?…`; `am start` it in the browser; logcat must show `SignInHandoff: return link without an open attempt — ignored`. If Chrome does not follow it, the design changes here and nothing else matters.
5. The same hop with an OPEN attempt, to prove the intent reaches the shell and the tab closes.
6. Server half in webphone: `internal/server/auth/sso_handoff.go` (ticket store: 32 random bytes, sha256 key, single use, 2-minute TTL, swept on issue).
7. Server: `SSOBegin` reads `shell` + `app_state` (shape `[A-Za-z0-9_-]{16,128}`, else 400) and parks the `wp_sso_shell` cookie with the other attempt cookies' attributes.
8. Server: `SSOCallback` with the shell cookie mints the row, delivers NO cookie, 303s to the return URI; failures stay on `/login?sso_error=…`.
9. Server: `SSOFinish` (`X-WebPhone-Shell` header required, else 400; live ticket → cookie + CSRF rotate + `303 /`; anything else → `303 /login?sso_error=stale`), under the login limiter.
10. Server: capability route (`204`, `no-store`), both new routes inside the existing `deps.SSO != nil` block.
11. Server: split `mintSession` into row birth + delivery so the handoff mints without delivering (no second birth path).
12. Server: Go tests (capability on/off, begin params, the full begin→callback→finish→`/api/session` chain, refusal vectors, single use, expiry), then `nix fmt`, `buildflow`, the CHANGELOG `[Unreleased]` line; check the `server` depth pin (5300) and `changelog-guard`.
13. The cross-repo draft `docs/drafts/2026-11-28_webphone-oidc-shell-handoff.md` with the contract table above, the security reasoning (nonce fence, header requirement, TTLs) and the source receipts.
14. Extend `scripts/loopback-live-check.sh` + `LoopbackLiveContractTest`: with OAuth2 env set the loopback binary must answer the probe 204, a junk ticket `303 /login?sso_error=stale`, a header-less redemption 400.
15. Emulator, then phone: the full dance against the live stack once it runs the server half (Pocket ID passkey in the browser, return, redemption, line pill climbs).
16. Android Lint on the new code; fix whatever it names.
17. `ktlintCheck`; `scripts/test-count-check.py`.
18. `assembleRelease` (R8 with `androidx.browser`), zipalign, ABI gate, contrast, `dprint check`; the emulator train after the dependency jump.
19. `scripts/meta-prove.sh` on the pins that guard a behaviour: the nonce fence (`takeTicket`), the held tap (lane `AWAIT_SUPPORT`), single use, the cookie landing, the manifest pin.
20. Docs: `AGENTS.md` (architecture table row for `SignInHandoff`, control-flow step 1, gotchas: the Pocket ID verdict, the return scheme pairing, the lanes; refresh the counts from the XML).
21. Docs: `docs/DOMAIN_LANGUAGE.md` (sign-in handoff, handoff ticket, shell nonce, browser lane, detour lane).
22. Docs: `FEATURES.md` row for the browser lane (`PARTIALLY_FUNCTIONAL`: JVM-proven, blocked on the stack half and a device train); reword the "In-app OIDC login (auth detour)" row.
23. Docs: `TODO_LIST.md`: split the assetlinks row (IdP-host `get_login_creds` half: dead for Pocket ID, cite #1845; App-Link half stands); add the server-half row, the device-train row, the emulator-hop row.
24. Docs: `ROADMAP.md` auth surface: the "OAuth outside the WebView" idea moved out of raw ideas; add the App-Link return form and the failure relay as the new raw ideas.
25. Docs: `CHANGELOG.md` `[Unreleased]`; README honesty markers.
26. Annotate the 2026-11-11 assetlinks draft as superseded for Pocket ID; keep the draft.
27. Visible feedback for a held tap; make the hold die on Back and on navigation.
28. One probe per origin per process; re-ask on origin change only.
29. Tests to add: a renderer rebuild mid-hold cancels the held tap; `retry()`/`loadUri()` abandon it; a recreated shell (day/night switch re-runs `onCreate` with the same intent) does not redeem twice; the token-shape boundaries 16 and 128; a second `begin` while one attempt is open.
30. Server option to weigh: also reject a redemption whose `Sec-Fetch-Site` is `cross-site` (belt behind the header requirement).
31. Server option to weigh: relay IdP refusals back to the shell (`error=` on the return URI) so the user never has to read the browser tab.
32. Decide whether the attempt's stale `Intent` should be cleared from `MainActivity` after redemption (`setIntent` to the launcher intent).
33. Confirm no nonce or ticket reaches logcat from OUR code (system `ActivityTaskManager` logs intent URIs; note it in the security comment).
34. Custom Tab theming: brand-green toolbar via `setDefaultColorSchemeParams`, light and dark.
35. Optional `CustomTabsSession` warm-up so the first tap opens faster.
36. Make the four comment sites true (server file, `AndroidShellFinishURL`, the draft) or reword them.
37. A shared contract artifact, as the repo already does for JSON: webphone renders the handoff URL shapes into `docs/contracts/wire/`, this repo vendors and `WireContractTest` pins them (`scripts/sync-wire-fixtures.sh --check`).
38. `scripts/assetlinks.sh`: annotate that the `get_login_creds` relation it emits serves an RP with a configurable origin list (webphone's own passkey lane), not Pocket ID.
39. webphone side after the code: NixOS module needs no new knob (the return URI is a constant); document it in the stack runbook and `docs/lessons.md`.
40. `scripts/ci-rehearsal.sh` once the gate is green (CI itself has never executed).
41. Owner: version bump and the `## [0.2.x]` changelog section if this should ship with the next release.
42. Ask (or check) whether an auto-push now exists for this repo: `origin/main` moved to `df43767` without a push from this session.
43. Coordinate build slots with the `crush-daily` sessions (load average 65 during this run).
44. Keep the WebView detour for stacks whose IdP can authenticate in a WebView; decide whether the shell should SAY so when a Pocket ID-style stack has no handoff (today it silently takes a lane that cannot complete).
45. Sanity-check the live Pocket ID version against #1845's reporter's 2.18.0 before quoting the verdict in the README.
46. Accessibility: the failure toast is a `Toast`; consider an `announce`-style live region as the island does.
47. Re-read `AGENTS.md` "Process rules" (claim-drift grep over `scripts/` and `docs/`, summary-vs-source diff) before closing: both were skipped by this session.
48. Record the two lesson candidates in `AGENTS.md`: batch-edit partial-failure reports lie; on this repo every on-disk state must compile because the daemon commits within minutes.
49. After the server half lands: re-vendor wire fixtures and re-run `scripts/sync-wire-fixtures.sh --check`.
50. Final whole gate on the FINAL tree, then `.gate-last-green`, then report.

## g) QUESTIONS I CANNOT ANSWER MYSELF

1. **Scope of the server half.** Should I implement it directly in `~/projects/webphone` (about 150 lines of Go plus tests, a CHANGELOG line, its gates; that repo has a live second session and a daemon that pushes), or stop at the app half and file the cross-repo issue with the contract above for a webphone session to pick up?
2. **Push.** `origin/main` is at `df43767`, whose test sources do not compile, and I did not push it. May I push `HEAD` once the whole gate is green, or is every push yours?
3. **The return address.** The scheme `cloud.larsartmann.webphone` becomes a shipped contract with the first release that carries it (changing it later is a coordinated app + stack change). Freeze it now, or do you want the App-Link form (`https://pbx.artmann.tech/api/auth/oauth2/finish`, needs the stack's `assetlinks.json`) as the long-term primary with the scheme as the interim transport?
