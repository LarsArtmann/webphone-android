// AGP 9 ships BUILT-IN Kotlin, but pinned to the KGP version AGP
// embeds. "Latest Kotlin" rides a buildscript classpath pin instead
// of the old org.jetbrains.kotlin.android plugin (removed everywhere
// — it is incompatible with built-in Kotlin). The version here MUST
// match `kotlin` in gradle/libs.versions.toml (kotlin-test aligns to
// it); change both or neither. buildscript repositories are exempt
// from FAIL_ON_PROJECT_REPOS by design.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ktlint) apply false
}

// The "change both or neither" rule above is a hand rule, and hand
// rules rot silently — a catalog bump that misses the classpath pin
// (or the reverse) surfaces as a confusing kotlin-test/KGP mismatch
// far from the cause. This task makes the drift a red build with the
// fix in the message (J07/M028). Wire: CI verify job + the
// verify-gate wrapper run it on every build. The two files resolve at
// configuration time and are captured by VALUE (the run { } scope —
// a config-cache requirement: neither the Project nor the script
// instance may leak into the action), then read fresh per execution.
val kgpParityCheck =
    run {
        val kgpBuildScript = rootProject.file("build.gradle.kts")
        val kotlinCatalog = rootProject.file("gradle/libs.versions.toml")
        tasks.register("kgpParityCheck") {
            group = "verification"
            description =
                "Fail when the buildscript kotlin-gradle-plugin pin and the catalog kotlin version drift apart."
            doLast {
                val catalogKotlin =
                    Regex("""(?m)^kotlin\s*=\s*"([^"]+)"""")
                        .find(kotlinCatalog.readText())
                        ?.groupValues
                        ?.get(1)
                        ?: error(
                            "gradle/libs.versions.toml has no `kotlin = \"...\"` entry — " +
                                "the catalog lost a load-bearing version.",
                        )
                val pinnedKotlin =
                    Regex("""kotlin-gradle-plugin:([^"\s]+)""")
                        .find(kgpBuildScript.readText())
                        ?.groupValues
                        ?.get(1)
                        ?: error(
                            "build.gradle.kts no longer pins kotlin-gradle-plugin on the " +
                                "buildscript classpath — the built-in-Kotlin pin vanished.",
                        )
                check(catalogKotlin == pinnedKotlin) {
                    "KGP pin drift: build.gradle.kts pins kotlin-gradle-plugin:$pinnedKotlin " +
                        "but gradle/libs.versions.toml says kotlin = \"$catalogKotlin\". " +
                        "Built-in Kotlin MUST match the catalog (kotlin-test aligns to it): " +
                        "change both or neither."
                }
            }
        }
    }
