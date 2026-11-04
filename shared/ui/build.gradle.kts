import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Compose Multiplatform app shell and screens on top of :shared:data.
// Targets: Android (hosted by :androidApp), desktop JVM (verification on this host: compile, unit tests,
// offscreen screenshots) and iOS.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
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
            // NOTE(T3): RealCashRoot maps the platform back event (Android back button / gesture) to the back stack.
            implementation(libs.compose.ui.backhandler)
            // Reviewed money artwork (assets/img/drawable) is exposed as Res.drawable.* on every platform.
            implementation(compose.components.resources)
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

// Android library side of the KMP module (classic com.android.library + androidTarget()).
android {
    namespace = "com.jim.realcash.ui"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.android.buildTools.get()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The reviewed assets live in assets/img (manifest, LOG.md); they are served as Compose resources from there
// instead of being copied into src/commonMain/composeResources, so the repo keeps one copy per image.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.jim.realcash.ui.resources"
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = provider { layout.projectDirectory.dir("../../assets/img") },
    )
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
