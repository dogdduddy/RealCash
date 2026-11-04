rootProject.name = "RealCash"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // NOTE: google() is intentionally absent. dl.google.com is blocked in the current
        // build environment, so no Android Gradle Plugin / androidx artifact can be resolved.
        // TODO(T3): add google() here once dl.google.com is reachable.
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // TODO(T3): add google() here once dl.google.com is reachable.
    }
}

// Pure Kotlin domain model, ledger reducer, calculators and notification parser.
include(":shared:domain")

// TODO(T4): include(":shared:data")   // SQLDelight event store, settings, parser-rule loader
// TODO(T5): include(":shared:ui")     // Compose Multiplatform screens
// TODO(T3): include(":androidApp")    // Requires the Android SDK / AGP (dl.google.com access)
