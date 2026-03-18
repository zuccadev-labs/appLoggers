plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.sqldelight) apply false
}

allprojects {
    group = findProperty("GROUP")?.toString() ?: "com.github.zuccadev"
    version = findProperty("VERSION_NAME")?.toString() ?: "0.1.1"
}
