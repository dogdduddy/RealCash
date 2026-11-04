// Root build script. Plugins are declared here (not applied) so every module resolves
// the same plugin versions from gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
