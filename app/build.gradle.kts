plugins {
    alias(libs.plugins.android.application)
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
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
