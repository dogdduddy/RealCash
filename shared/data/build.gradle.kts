import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Persistence (SQLDelight) and application-level orchestration on top of :shared:domain:
// the event store, the wallet repository, settings, parser rules and the payment-intake pipeline.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm {
        compilerOptions {
            // Same bytecode target as :shared:domain so the future Android app can consume it.
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    // TODO(T3): add androidTarget() (AndroidSqliteDriver actual) once the Android SDK / AGP can be resolved.
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        // expect/actual classes (DriverFactory) are still Beta in Kotlin 2.2.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:domain"))
            // Domain types used in this module's public API (LocalDate, @Serializable models).
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("RealCashDatabase") {
            packageName.set("com.jim.realcash.data.db")
        }
    }
}
