import java.time.Duration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
}

// version.txt (repo root) is the SINGLE source of the app version — the
// tag Obtainium compares against, the APK's own versionName, and the
// versionCode Android uses to accept an update all derive from it.
// Duplicating the number in this file is how a device ends up refusing
// its own update (a same-or-lower versionCode is silently not an update),
// so it is read here, never retyped.
val versionFile = rootProject.file("version.txt")
check(versionFile.isFile) {
    "version.txt is missing at the repo root. It holds the release version " +
        "(a MAJOR.MINOR.PATCH line, e.g. 0.2.0); restore it before building."
}
val versionNameValue = versionFile.readText().trim().removePrefix("v")
val versionParts = versionNameValue.split(".")
check(versionParts.size == 3 && versionParts.all { it.toIntOrNull() != null }) {
    "version.txt must hold a MAJOR.MINOR.PATCH version, found '$versionNameValue'."
}
val (versionMajor, versionMinor, versionPatch) = versionParts.map { it.toInt() }
check(versionPatch < 100 && versionMinor < 100 && versionMajor < 2_000_000) {
    "version.txt '$versionNameValue' overflows the versionCode layout " +
        "(major*10000 + minor*100 + patch)."
}
// Readable and monotonic per release: 0.2.0 -> 200, 0.2.7 -> 207.
val computedVersionCode = versionMajor * 10_000 + versionMinor * 100 + versionPatch

// Release signing: the key material lives OUTSIDE the repository, in
// ~/.config/webphone-android/ — a keystore committed to a repo is a
// keystore whose password is committed too. When the properties file is
// absent (CI, or a fresh checkout) the release build stays UNSIGNED
// exactly as before, so nothing that builds today starts failing.
// `scripts/release.sh --init-key` writes both files once.
val releasePropertiesFile =
    file(
        System.getenv("WEBPHONE_RELEASE_PROPERTIES")
            ?: "${System.getProperty("user.home")}/.config/webphone-android/release.properties",
    )
val releaseProperties =
    Properties().apply {
        if (releasePropertiesFile.isFile) {
            releasePropertiesFile.inputStream().use { load(it) }
        }
    }
val releaseKeystore = releaseProperties.getProperty("storeFile")?.let { file(it) }
val releaseSigningReady = releaseKeystore?.isFile == true
if (releasePropertiesFile.isFile && !releaseSigningReady) {
    error(
        "Release signing is half-configured: ${releasePropertiesFile.path} exists but its " +
            "storeFile ('${releaseProperties.getProperty("storeFile") ?: "<unset>"}') is not a " +
            "readable file. Fix the path or delete the file (an unsigned release still builds).",
    )
}

android {
    namespace = "software.larsartmann.webphone"
    compileSdk = 37
    // build-tools 36: AGP 9's floor; its zipalign carries -P 16 — the
    // packaging step must produce 16 KB page-aligned uncompressed .so
    // files (the Android 16 constraint; verified per release in CI).
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "software.larsartmann.webphone"
        minSdk = 31
        targetSdk = 36
        versionCode = computedVersionCode
        versionName = versionNameValue
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseProperties.getProperty("storePassword")
                keyAlias = releaseProperties.getProperty("keyAlias")
                keyPassword = releaseProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The signature is what makes an Obtainium update possible at
            // all: Android refuses an APK whose signing key differs from
            // the installed one (the update would demand an uninstall,
            // losing the session cookie + settings).
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Android 16 (API 36): every bundled native library must be 16 KB
    // page-aligned (Play blocks unaligned apps targeting API 35+ from
    // 2027-02; sideload shows the compat dialog). stream-webrtc-android
    // ships the aligned libwebrtc since 1.3.10 — verify per release
    // with `zipalign -c -P 16` on the merged APK (decision doc,
    // docs/planning/2026-10-23_16-13 in the webphone repo).
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // The old `kotlinOptions { jvmTarget }` block is GONE under AGP 9
    // built-in Kotlin: jvmTarget follows compileOptions.targetCompatibility
    // (17). Compiler options would move to a top-level `kotlin {}` block
    // if this project ever needs non-defaults.
    buildFeatures {
        viewBinding = true
    }

    // JVM unit tests ride the host JVM: android.jar method stubs must
    // not throw, and Robolectric (T15's smoke tests) needs resources.
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all { test ->
                // A silent suite is how a wedge hid for hours: every
                // test must print its outcome as it finishes.
                test.testLogging {
                    events("passed", "failed", "skipped")
                }
                // J05 wedge-proof fence: a hung suite must FAIL the
                // build, not hide. The task timeout aborts a wedged run
                // after 20 minutes; forkEvery = 1 gives every class a
                // fresh JVM so cross-class static leakage (an engine
                // scope left running, a stale dispatcher) cannot poison
                // the next class.
                test.timeout = Duration.ofMinutes(20)
                test.forkEvery = 1
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.okhttp)
    implementation(libs.coroutines)
    implementation(libs.webrtc)
    implementation(libs.firebase.messaging)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
