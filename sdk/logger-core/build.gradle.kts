import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import java.io.File

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    `maven-publish`
}

val sdkVersionName = providers.gradleProperty("VERSION_NAME").orElse("0.0.0-dev")
val generatedVersionDir = layout.buildDirectory.dir("generated/version/commonMain/kotlin")

val generateAppLoggerVersion by tasks.registering {
    val outDir = generatedVersionDir.get().asFile
    outputs.dir(outDir)

    doLast {
        val pkgDir = File(outDir, "com/applogger/core")
        pkgDir.mkdirs()

        File(pkgDir, "AppLoggerVersion.kt").writeText(
            """
            package com.applogger.core

            /**
             * Single source of truth for the SDK version.
             *
             * Generated from Gradle property `VERSION_NAME`.
             */
            object AppLoggerVersion {
                const val NAME = "${sdkVersionName.get()}"
            }
            """.trimIndent()
        )
    }
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "11" }
        }
        publishLibraryVariants("release")
    }

    // XCFramework for SPM / CocoaPods distribution
    val xcf = XCFramework("AppLogger")

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "AppLogger"
            isStatic = true
            xcf.add(this)
        }
    }

    jvm()

    sourceSets {
        getByName("commonMain").kotlin.srcDir(generatedVersionDir)

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }

        jvmTest.dependencies {
            implementation(libs.junit5.api)
            runtimeOnly(libs.junit5.engine)
            implementation(libs.mockk)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
            api(libs.androidx.lifecycle.process)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }
    .configureEach {
        dependsOn(generateAppLoggerVersion)
    }

tasks.matching { it.name.contains("SourcesJar", ignoreCase = true) }
    .configureEach {
        dependsOn(generateAppLoggerVersion)
    }

android {
    namespace = "com.applogger.core"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

sqldelight {
    databases {
        create("AppLoggerDatabase") {
            packageName.set("com.applogger.db")
            verifyMigrations.set(false)
        }
    }
}

val isWindowsHost = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

if (isWindowsHost) {
    tasks.matching { it.name.contains("AppLoggerDatabaseMigration") }.configureEach {
        enabled = false
    }
}
