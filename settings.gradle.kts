rootProject.name = "RealCash"

pluginManagement {
    repositories {
        // Android Gradle Plugin (com.android.*) and its tooling dependencies live on Google Maven.
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // androidx, com.android (AGP runtime pieces such as lint / aapt2) and com.google artifacts.
        // Compose Multiplatform also pulls androidx artifacts for the desktop and iOS targets.
        google()
        mavenCentral()
    }
}

// Pure Kotlin domain model, ledger reducer, calculators and notification parser.
include(":shared:domain")

// SQLDelight event store, settings, parser-rule loader and the payment-intake orchestration.
include(":shared:data")

// Compose Multiplatform app shell and screens (Android, desktop JVM for verification, iOS).
include(":shared:ui")

// Android application: activity host, payment NotificationListenerService, Phase-0 notification sample collector.
include(":androidApp")
