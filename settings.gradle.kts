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
        // NOTE(T5): Compose Multiplatform (1.7 and later) depends on androidx artifacts (compose runtime, collection,
        // annotation, lifecycle, savedstate) that only Google Maven hosts, for the desktop and iOS targets too.
        // dl.google.com answers again; the content filter keeps every other dependency on Maven Central.
        // TODO(T3): widen the filter (com.android, com.google) for the Android app.
        google {
            mavenContent { includeGroupAndSubgroups("androidx") }
        }
    }
}

// Pure Kotlin domain model, ledger reducer, calculators and notification parser.
include(":shared:domain")

// SQLDelight event store, settings, parser-rule loader and the payment-intake orchestration.
include(":shared:data")

// Compose Multiplatform app shell and screens (desktop JVM for verification, iOS).
include(":shared:ui")
// TODO(T3): include(":androidApp")    // Requires the Android SDK / AGP (dl.google.com access)
