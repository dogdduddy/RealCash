import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Compose Multiplatform app shell and screens on top of :shared:data.
// Targets: desktop JVM (verification on this host: compile, unit tests, offscreen screenshots) and iOS.
// TODO(T3): add androidTarget() once the Android SDK / AGP can be resolved.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:data"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
        }
        jvmMain.dependencies {
            // Skiko runtime + desktop window APIs for the host OS.
            implementation(compose.desktop.currentOs)
            // Dispatchers.Main (AWT event thread) for the desktop window and lifecycle code.
            implementation(libs.kotlinx.coroutines.swing)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.jim.realcash.ui.DesktopMainKt"
    }
}

// Offscreen screenshot rendering needs no display; keep AWT headless and let Skiko fall back to software.
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.renderApi", "SOFTWARE")
    systemProperty("realcash.screenshotDir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
}
