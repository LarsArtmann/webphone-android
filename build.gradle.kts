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
