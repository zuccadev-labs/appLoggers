plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.applogger.sample"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        buildConfigField("String", "LOGGER_URL", "\"${project.findProperty("appLogger.url") ?: ""}\"")
        buildConfigField("String", "LOGGER_KEY", "\"${project.findProperty("appLogger.anonKey") ?: ""}\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Importar el SDK como dependencia de proyecto (desarrollo local)
    implementation(project(":logger-core"))
    implementation(project(":logger-transport-supabase"))

    // En una app real que consume el SDK publicado:
    // implementation("com.github.devzucca.appLoggers:logger-core:0.1.1-alpha.3")
    // implementation("com.github.devzucca.appLoggers:logger-transport-supabase:0.1.1-alpha.3")

    // Test utilities
    testImplementation(project(":logger-test"))
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
