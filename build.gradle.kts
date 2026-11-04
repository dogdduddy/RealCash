// Root build script. Plugins are declared here (not applied) so every module resolves
// the same plugin versions from gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // NOTE(T3): the Android plugins must sit on the root classpath next to the Kotlin plugin: the Kotlin
    // Gradle plugin reaches into AGP classes for androidTarget(), which fails when AGP is loaded in a
    // subproject's own classloader. kotlin-android is the same artifact as kotlin-multiplatform and has to be
    // declared here too, otherwise subprojects get "plugin already on the classpath with an unknown version".
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}
