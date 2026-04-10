plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.applogger.sample"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        val loggerUrl = project.findProperty("APPLOGGER_URL") ?: ""
        val loggerKey = project.findProperty("APPLOGGER_ANON_KEY") ?: ""
        val loggerDebug = project.findProperty("APPLOGGER_DEBUG") ?: "false"
        val integritySecret = project.findProperty("APPLOGGERS_INTEGRITY_SECRET")
            ?: project.findProperty("APPLOGGER_INTEGRITY_SECRET")
            ?: ""
        val integritySecretId = project.findProperty("APPLOGGERS_INTEGRITY_SECRET_ID")
            ?: project.findProperty("APPLOGGER_INTEGRITY_SECRET_ID")
            ?: ""

        buildConfigField("String", "LOGGER_URL", "\"${loggerUrl}\"")
        buildConfigField("String", "LOGGER_KEY", "\"${loggerKey}\"")
        buildConfigField("boolean", "LOGGER_DEBUG", loggerDebug.toString())
        buildConfigField("String", "LOGGER_INTEGRITY_SECRET", "\"${integritySecret}\"")
        buildConfigField("String", "LOGGER_INTEGRITY_SECRET_ID", "\"${integritySecretId}\"")
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
    // implementation("com.github.zuccadev-labs.appLoggers:logger-core:0.2.0-alpha.10")
    // implementation("com.github.zuccadev-labs.appLoggers:logger-transport-supabase:0.2.0-alpha.10")

    // Test utilities
    testImplementation(project(":logger-test"))
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
