# Toolchain jump landed; the executor wedge slain (R29)

_Session scope: the user's go-order on the R28 plan (defaults applied:
atomic jump, targetSdk holds at 36). Research was complete in R28;
this session EXECUTED and VERIFIED everything, and root-caused the
baseline wedge that R28 left as an open question._

## The four answers (now TRUE in the repo, not just researched)

| Surface  | Was (2026-10-27)       | Is (2026-10-28)                                   |
| -------- | ---------------------- | ------------------------------------------------- |
| Linter   | none (review-enforced) | **ktlint 14.2.0** (official style, CI-gated)      |
| Kotlin   | 2.1.0                  | **2.4.21** (buildscript classpath pin)            |
| APIs/SDK | compileSdk 36, AGP 8.7 | **compileSdk 37, AGP 9.4.1, Gradle 9.8.2, bt 36** |
| Compose  | none                   | **NOT now** (decision recorded in ROADMAP)        |

## The wedge: root-caused and FIXED (R28's open question 1, answered)

Two stacked causes, both proven by jstack on a live wedged executor
(~20 s CPU, zero progress, no test XMLs — the exact R28 signature):

1. **The tests phoned the live stack.** MainActivity's onCreate/resume
   calls `CallLayer.ensureWired`, which starts a REAL session-HTTP +
   SIP-WSS + ntfy-websocket wire. The deep-link/auth tests (newest,
   2026-10-27 — the wedge started with them) launch MainActivity 6×.
   Live sockets + infinite non-daemon ntfy reconnect threads leaked
   into the executor JVM. Fix: `CallLayer.wiringEnabled` test seam
   (default true; Robolectric classes flip it off; per-sandbox
   classloader means no leak) + daemon reconnect threads.
2. **`ActivityScenario.close()` deadlocks on a BARE launch** of the
   singleTask shell: its wait-for-DESTROYED loop runs ON the Robolectric
   main thread, so the destroy dispatch can never be delivered
   (control experiment: a VIEW-intent launch closes fine solo; the
   bare launch hangs solo). Fix: synchronous
   `Robolectric.buildActivity(...).setup()` +
   `pause().stop().destroy()` everywhere (`DeepLinkIntakeTest`,
   `MainActivityLaunchTest`); `App.origin` now set BEFORE the drive.

The foreign build spawner (R28 question 1) is ANSWERED: the
`crush-daily` scheduler dispatches autonomous agent sessions that run
the gate `testDebugUnitTest assembleDebug` on this repo; each kill
respawns one. A sibling agent worked the same files concurrently
(AttachGuardTest, WebSurface hermeticity) — complementary, merged
around, nothing clobbered. Operational rule recorded in AGENTS.md:
pre-flight `pgrep -f gradle`, never two builds on one dir.

## Landed (all verified)

1. Baseline: **94 tests GREEN** on the old toolchain (after the wedge
   fixes), streamed logs, per-test outcome printing wired into
   `app/build.gradle.kts`.
2. Atomic toolchain jump: flake platform 37 + build-tools 36.0.0
   (emulator shell keeps API 36 + its system image, build-tools 36),
   wrapper 9.8.2, AGP 9.4.1, built-in Kotlin with the KGP 2.4.21
   classpath pin (root `build.gradle.kts`; the catalog `kotlin` and
   the pin MUST move together), `kotlinOptions` deleted (jvmTarget
   follows targetCompatibility 17), `kotlin("test")` →
   `kotlin-test-junit` catalog entry (the bare `kotlin-test`
   aggregator does NOT resolve `kotlin.test.Test` under the built-in
   toolchain — found and fixed in the first gate run),
   `android.suppressUnsupportedCompileSdk` removed.
3. Dependencies: core-ktx 1.19.1, appcompat 1.8.0, lifecycle 2.11.0,
   okhttp 5.5.1 (+mockwebserver), coroutines 1.11.0, robolectric 4.17,
   androidx.test 1.7.0, firebase-messaging 26.0.1; unused `okhttp-ws`
   alias removed.
4. ktlint adoption: plugin 14.2.0 both build files, root
   `.editorconfig` (official style, max-line 120), `ktlintFormat` to
   zero findings (47 files, mechanical), `DeepLink.kt` →
   `DeepLinkTarget.kt` (git mv, filename rule), two hand-wrapped
   > 120-char lines.
5. CI: ktlint gate step added; zipalign path moved to build-tools
   36.0.0.
6. Emulator train under the new toolchain: **PASS** (install Success,
   launch, `webphone-train.png` screenshot; API 36 AVD takes the
   compileSdk-37 APK fine).

## Verification evidence

- `assembleDebug` + `testDebugUnitTest` + `assembleRelease` green
  (94 tests, 0 failures; R8 mapping.txt 10.9 MB written).
- `zipalign -c -P 16 4` OK on BOTH APKs from build-tools/36.0.0.
- `ktlintCheck` green.
- Train exit 0.

## Known residuals (recorded, not churned)

- Four Kotlin deprecation warnings surfaced by compileSdk 37:
  `onBackPressed` (the DOCUMENTED API 31/32 bridge — keep),
  `CAPABILITY_SELF_MANAGED` (platform-blessed inbound path),
  Firebase token/`onNewToken` (dormant lane). None actionable at v0.1.
- targetSdk stays 36 (deliberate; TODO row gates it on the device
  train). API 37 emulator image + mockwebserver3 parked as TODO rows.
- R8 full-mode sanity + baseline-profile check under AGP 9: open
  (ROADMAP stretch).
- Remotes lag local (nothing pushes automatically — owner pushes by
  hand per repo policy).
