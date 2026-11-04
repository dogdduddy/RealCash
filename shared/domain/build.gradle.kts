import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin domain layer: model, event-sourced ledger, calculators, notification parser.
// No platform dependencies. Only stdlib, kotlinx-datetime and kotlinx-serialization.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

kotlin {
    jvm {
        compilerOptions {
            // NOTE: bytecode target 17 (not the host JDK 21) so the Android app
            // module can consume this library without extra desugaring settings.
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        // kotlin.time.Instant / Clock and kotlin.uuid.Uuid are still experimental in Kotlin 2.2.
        // The plan allows opting in at module level.
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

// Android library side of the KMP module (classic com.android.library + androidTarget()).
android {
    namespace = "com.jim.realcash.domain"
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
