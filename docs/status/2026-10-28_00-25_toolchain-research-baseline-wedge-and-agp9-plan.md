# Toolchain research, baseline wedge, and the AGP 9 plan (R28)

_Session scope: the user asked "Kotlin linters? Latest Kotlin? Latest Android
APIs/SDKs? JetPack Compose?" and demanded stepwise execution. This report covers
exactly that session: research COMPLETE, execution NOT STARTED (zero repo files
changed), and the baseline verification job WEDGED and killed. Nothing was
committed by hand; the daemon picks this file up._

## The answer summary (research-verified, primary registries)

| Surface        | Current                  | Latest stable (2026-10-28)                    | Verdict                                                     |
| -------------- | ------------------------ | --------------------------------------------- | ----------------------------------------------------------- |
| Kotlin         | 2.1.0                    | **2.4.21** (2.5.0-Beta2 is pre-release)       | 3 minors behind                                             |
| AGP            | 8.7.3                    | **9.4.1** (9.5.0-rc02 / 9.6.0-alpha01 exist)  | major migration (built-in Kotlin)                           |
| Gradle wrapper | 8.11.1                   | **9.8.2**                                     | behind (AGP 9 needs ≥9.1)                                   |
| compileSdk     | 36                       | **37** (Android 17, stable 2026-06-16)        | AGP 9.1+ required for 37                                    |
| targetSdk      | 36                       | 37                                            | decision pending (train)                                    |
| Compose        | none                     | BOM **2026.09.00**                            | NOT NOW (documented call)                                   |
| Kotlin linter  | none ("review-enforced") | ktlint 1.8.0 / ktlint-gradle **14.2.0**       | adopt ktlint; detekt stalled at 1.23.8 (2025-02) — rejected |
| core-ktx       | 1.15.0                   | 1.19.1                                        | bump                                                        |
| appcompat      | 1.7.0                    | 1.8.0                                         | bump                                                        |
| lifecycle      | 2.8.7                    | 2.11.0                                        | bump                                                        |
| okhttp         | 4.12.0                   | 5.5.1 (classic mockwebserver still published) | major bump, tests cover it                                  |
| coroutines     | 1.9.0                    | 1.11.0                                        | bump                                                        |
| robolectric    | 4.14.1                   | 4.17                                          | bump (SDK 37 support)                                       |
| androidx.test  | 1.6.1                    | 1.7.0                                         | bump                                                        |
| firebase-msg   | 24.1.0                   | 26.0.1 (dormant lane)                         | bump                                                        |
| stream-webrtc  | 1.3.10                   | 1.3.10 (latest, unchanged)                    | no-op                                                       |

AGP 9 migration mechanics (official docs + JetBrains blog, verified):

- Built-in Kotlin is ON by default; the `org.jetbrains.kotlin.android` plugin
  must be REMOVED (it is incompatible, duplicate `kotlin` extension).
- `android.kotlinOptions{}` is gone; compiler options move to top-level
  `kotlin { compilerOptions {} }`; under built-in Kotlin `jvmTarget` defaults to
  `compileOptions.targetCompatibility` (so the current 17/17 block can simply
  absorb it).
- KGP ≥ 2.2.10 is embedded. A NEWER KGP is pinnable from the root build file:
  `buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21") } }`
  — that is how "latest Kotlin" rides AGP 9. Downgrading instead requires
  `android.builtInKotlin=false` (temporary; gone in AGP 10).
- `kotlin("test")` in the module script must become an explicit
  `kotlin-test` dependency (version-aligned to the pinned KGP).
- AGP 9.0 floor: Gradle 9.1.0, JDK 17, build-tools 36.0.0, max API 36.1;
  API 37 needs AGP 9.1+. Old variant API (`applicationVariants` etc.) removed —
  this repo never used it.
- Robolectric 4.17 covers SDK 36/37 test shadows; zipalign `-P 16` exists in
  build-tools ≥ 35, so the 16 KB gate moves cleanly from 35.0.0 to 36.0.0.

Compose decision (recommended, not yet recorded in ROADMAP): the app is a
WebView shell with three small programmatic screens; Compose adds a runtime,
compiler plugin, and a rewrite for zero user value at v0.1. Default for any NEW
native surface becomes Compose (BOM 2026.09.00 + `org.jetbrains.kotlin.plugin.compose`),
the existing screens stay views. Not adopting now.

## a) FULLY DONE

1. Full version matrix above, verified against PRIMARY sources only
   (dl.google.com maven-metadata, repo1.maven.org, plugins.gradle.org,
   services.gradle.org — no blog-version guesses).
2. AGP 9 / built-in-Kotlin migration mechanics researched to DSL-snippet level
   (release notes + migration guide + JetBrains AGP9 post).
3. Current build surface fully read: `gradle/libs.versions.toml`,
   `app/build.gradle.kts`, root `build.gradle.kts`, `settings.gradle.kts`,
   `gradle.properties`, wrapper properties, `flake.nix`, CI workflow,
   TODO_LIST context.
4. 12-step execution plan created and tracked in the session todo list.
5. `assembleDebug` SUCCEEDED on the current toolchain (app-debug.apk, 55 MB,
   18:54).
6. Orphaned earlier-session build (18:41 `testDebugUnitTest` client + its test
   executor) identified and killed — it was competing for the same build dir.
7. Wedged baseline job diagnosed with evidence (5h28m elapsed, 26s CPU, no test
   XML written) and killed; gradle daemons stopped; `.gradle/*.lock` cleared.

## b) PARTIALLY DONE

1. **Baseline verification**: assembleDebug half is green; the
   `testDebugUnitTest` half never completed (wedge, below). Baseline is
   therefore UNPROVEN — the 91-test suite has no green run from this session.
2. **Target-version decision set**: Gradle 9.8.2 + AGP 9.4.1 + built-in Kotlin
   - KGP 2.4.21 classpath pin + compileSdk 37 + build-tools 36.0.0 + ktlint
     14.2.0. Decided, designed, not applied — zero repo files changed.
3. **ktlint adoption designed** (plugin + root `.editorconfig` + CI step +
   format pass) but not implemented.

## c) NOT STARTED (all execution steps)

1. `flake.nix`: platformVersions 37 + buildToolsVersions 36.0.0.
2. Wrapper → 9.8.2.
3. AGP 9.4.1 catalog bump; remove `kotlin-android` plugin entries from both
   plugin blocks; root buildscript KGP 2.4.21 pin.
4. `app/build.gradle.kts` migration: drop `kotlinOptions`, `compileSdk = 37`,
   `buildToolsVersion = "36.0.0"`, `kotlin("test")` → catalog `kotlin-test`.
5. `gradle.properties`: drop `android.suppressUnsupportedCompileSdk=36`.
6. Dependency refresh (8 bumps from the matrix above).
7. ktlint install + format pass + green check + CI wiring.
8. zipalign gate path 35.0.0 → 36.0.0 (CI + AGENTS + flake comment).
9. `assembleRelease` under AGP 9 / R8 (proguard keeps re-verified).
10. Docs: AGENTS.md (build section + AGP9 gotchas), CHANGELOG, ROADMAP Compose
    decision, TODO_LIST rows (targetSdk-37 gate, mockwebserver3 migration).
11. dprint fmt pass over touched `.md`/`.yml`.
12. Emulator train re-run under the new toolchain.

## d) TOTALLY FUCKED UP!

1. **The baseline wedge (worst miss of the session).** My baseline job ran
   **5 hours 28 minutes consuming 26 seconds of CPU** — the test phase hung
   (no results XML ever written) and I did not notice because I piped the whole
   build through `tail -30`, which buffers ALL output until exit. Zero
   visibility by construction. It surfaced only when the user complained, hours
   in. The wedge itself is most plausibly caused by d)2.
2. **Launched a build without checking for a running one.** An orphaned 18:41
   build from an earlier session was already writing `app/build` when I started
   mine at 18:53 — two concurrent Gradle test executors on one build directory.
   "Read before write" was violated at the process level, not the file level.
3. **Kill sloppiness.** Clearing the orphan took three escalating attempts
   (SIGTERM, SIGTERM again, SIGKILL, re-SIGKILL the executor) — flailing where
   one `pgrep` + one decisive kill tree should have done it.
4. **Foreign builds keep spawning.** While I was tearing down MY wedged job, a
   NEW client appeared (`gradle-wrapper.jar testDebugUnitTest assembleDebug` —
   not my arg set). Something OUTSIDE this session is building this repo. I
   stopped fighting it (correct call) but never identified the source; the next
   session inherits that ambiguity.
5. **Ordering sin, minor**: ~45 minutes of version research happened before the
   ground truth was proven. The baseline was launched early (good) but in a
   broken invisible form (bad), so the safety net I built was made of nothing.

## e) WHAT WE SHOULD IMPROVE!

1. Never pipe a long build through `tail` — stream to a log file
   (`>build.log 2>&1`) and poll the tail on a clock.
2. Pre-flight every build: `pgrep -f gradle` (one build per build dir),
   `./gradlew --stop`, check `.gradle/*.lock`.
3. Timebox every wait: escalate on 10 minutes of silence, not on user anger.
4. Make test runs fail loudly: wire `testOptions.unitTests.all { testLogging
   events = [...] }` and add per-test timeouts so a hung WebSocket/Robolectric
   test RED-lines instead of wedging the executor silently.
5. Identify the foreign build spawner (concurrent session? scheduled runner?)
   before the next build session — otherwise the wedge recurs and we will blame
   the wrong cause.
6. Land the toolchain jump as ONE atomic change with the full gate run once —
   AGP9 + Gradle + Kotlin are not independently viable intermediate states
   (Gradle 9.8.2 alone breaks AGP 8.7.3).

## f) Next: up to 50 things

**Group A — unblock and re-baseline (do first):**

1. Identify/neutralize the foreign build spawner; confirm an idle build dir.
2. Rerun baseline streamed to a log file; if tests hang again, `jstack` the
   executor, name the hanging test, fix it at the root cause.
3. Wire unit-test logging (passed/failed/skipped events) into
   `app/build.gradle.kts`.
4. Add a per-test timeout policy for network-ish tests (MockWebServer/WebSocket
   suites are the wedge suspects).
5. Add a `scripts/verify.sh` (or flake app) wrapper: stop daemons → build →
   test → zipalign → lint, so the gate is one command and pre-flight is
   built-in.

**Group B — the atomic toolchain jump:**

6. `flake.nix`: platform 37 + build-tools 36.0.0; verify with
   `ls $ANDROID_HOME/platforms`.
7. Wrapper `distributionUrl` → `gradle-9.8.2-bin.zip`.
8. Catalog: `agp = 9.4.1`; drop the `kotlin-android` plugin entry; keep
   `kotlin = "2.4.21"` (now the kotlin-test version ref).
9. Root `build.gradle.kts`: `buildscript { repositories { google(); mavenCentral() };
   dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21") } }`.
10. Remove the `kotlin.android` alias from BOTH plugin blocks.
11. `app/build.gradle.kts`: delete the `kotlinOptions` block (jvmTarget follows
    targetCompatibility 17).
12. `testImplementation(kotlin("test"))` → `testImplementation(libs.kotlin.test)`
    (new catalog entry).
13. `compileSdk = 37`, `buildToolsVersion = "36.0.0"`.
14. Remove `android.suppressUnsupportedCompileSdk=36` + comment from
    `gradle.properties`.
15. Full gate on AGP 9: assembleDebug + testDebugUnitTest + zipalign
    (`build-tools/36.0.0/zipalign -c -P 16 4`).
16. `assembleRelease` (R8 keeps `org.webrtc.**` / `FcmReceiver` still applied;
    check mapping output exists).

**Group C — dependency refresh:**

17. core-ktx 1.15.0 → 1.19.1.
18. appcompat 1.7.0 → 1.8.0.
19. lifecycle 2.8.7 → 2.11.0.
20. okhttp 4.12.0 → 5.5.1 (main + mockwebserver same version; classic artifact
    confirmed still published).
21. coroutines 1.9.0 → 1.11.0 (impl + test).
22. robolectric 4.14.1 → 4.17.
23. androidx.test core-ktx 1.6.1 → 1.7.0.
24. firebase-messaging 24.1.0 → 26.0.1 (dormant lane; compile-only risk).
25. webrtc stays 1.3.10 (already latest) — re-run zipalign gate anyway.
26. Decide mockwebserver → mockwebserver3 migration (or park as TODO row).
27. Remove the unused `okhttp-ws` catalog entry (WebSocket ships in the main
    okhttp artifact; nothing references it).
28. Full gate again after the batch.

**Group D — ktlint adoption:**

29. Catalog + app plugins: `org.jlleitschuh.gradle.ktlint` 14.2.0.
30. Root `.editorconfig` (ktlint official style; tune `max-line-length` to the
    prose-heavy KDoc reality instead of fighting it).
31. `ktlintFormat`, then review the diff for anything semantically touched.
32. `ktlintCheck` green.
33. CI: add the ktlint step to the `verify` job.

**Group E — decisions + docs:**

34. targetSdk 37: HOLD at 36 until a device train (recommendation) — TODO row.
35. Compose NOT-NOW decision → ROADMAP note (WebView is the app; Compose for NEW
    native surfaces only; BOM 2026.09.00 reference).
36. AGENTS.md: build/verify section rewrite (new commands, ktlint, zipalign
    36.0.0 path) + gotcha rows (AGP9 built-in Kotlin, the KGP classpath pin,
    detekt rejection rationale).
37. CHANGELOG entry for the toolchain jump.
38. TODO_LIST harvest from this report.
39. dprint fmt over touched `.md`/`.yml`.
40. README toolchain mentions, if any.

**Group F — verification + closure:**

41. Emulator train under the new toolchain (`scripts/emulator-train.sh`):
    install + launch + screenshot.
42. Sweep build output for new AGP9/config-cache warnings; keep logs
    signal-only (the repo's own discipline).
43. Check Dependabot still parses the catalog after the plugin-entry removal.
44. Mirror-lag check; owner pushes by hand per repo policy.

**Stretch / ROADMAP fuel:**

45. detekt re-evaluation when 2.x stabilizes (KGP 2.4 compat is the blocker).
46. API 37 emulator system image for the train (multi-GB; deferred).
47. Kotlin 2.5.0 pin when stable.
48. `android.builtInKotlin` has NO opt-out in AGP 10 — note in gotchas so
    nobody plans around the escape hatch.
49. baseline-profile / R8 full-mode sanity under AGP 9.
50. Consider the Gradle version-catalog update plugin for future bumps
    (replaces manual matrix fetches like this session's).

## g) Questions I cannot figure out myself

1. **Foreign build spawner**: while I tore down my wedged job, a NEW gradle
   client (`testDebugUnitTest assembleDebug`, not my invocation) appeared. Is
   another session/agent/scheduled job building this repo right now? If yes, no
   build work should run concurrently from this session again.
2. **Risk appetite before the first live device train**: land the full jump
   (AGP 9.4.1 + Kotlin 2.4.21 + okhttp 5.5.1 + all bumps) in one atomic step as
   planned, or split toolchain (AGP/Gradle/Kotlin) now and library bumps after
   the train? Every bump is untested against live infra either way.
3. **targetSdk 37 timing**: hold at 36 until a device train validates Android
   17 behavior changes (my recommendation), or bump to 37 now (Play-forward;
   sideload MVP unaffected)?
