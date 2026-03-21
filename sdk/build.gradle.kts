plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
}

allprojects {
    group = findProperty("GROUP")?.toString() ?: "com.github.zuccadev-labs"
    version = findProperty("VERSION_NAME")?.toString() ?: "0.1.1-alpha.4"
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/detekt.yml")
    source.setFrom(
        "logger-core/src/commonMain/kotlin",
        "logger-core/src/androidMain/kotlin",
        "logger-core/src/iosMain/kotlin",
        "logger-core/src/jvmMain/kotlin",
        "logger-transport-supabase/src/commonMain/kotlin",
        "logger-test/src/commonMain/kotlin"
    )
}

subprojects {
    afterEvaluate {
        // POM enrichment for all modules with maven-publish
        if (plugins.hasPlugin("maven-publish")) {
            extensions.configure<PublishingExtension> {
                publications.withType<MavenPublication> {
                    groupId = project.findProperty("GROUP")?.toString() ?: "com.github.zuccadev-labs"
                    version = project.findProperty("VERSION_NAME")?.toString() ?: "0.1.1-alpha.4"

                    pom {
                        name.set(project.findProperty("POM_NAME")?.toString() ?: "AppLogger")
                        description.set(project.findProperty("POM_DESCRIPTION")?.toString() ?: "")
                        url.set(project.findProperty("POM_URL")?.toString() ?: "")

                        licenses {
                            license {
                                name.set(project.findProperty("POM_LICENCE_NAME")?.toString() ?: "MIT License")
                                url.set(project.findProperty("POM_LICENCE_URL")?.toString() ?: "")
                            }
                        }

                        developers {
                            developer {
                                id.set(project.findProperty("POM_DEVELOPER_ID")?.toString() ?: "")
                                name.set(project.findProperty("POM_DEVELOPER_NAME")?.toString() ?: "")
                            }
                        }

                        scm {
                            url.set(project.findProperty("POM_SCM_URL")?.toString() ?: "")
                        }
                    }
                }

                // GitHub Packages repo (only used in CI with ./gradlew publish)
                repositories {
                    maven {
                        name = "GitHubPackages"
                        url = uri("https://maven.pkg.github.com/zuccadev-labs/appLoggers")
                        credentials {
                            username = System.getenv("GITHUB_ACTOR") ?: ""
                            password = System.getenv("GITHUB_TOKEN") ?: ""
                        }
                    }
                }
            }
        }

        if (plugins.hasPlugin("org.jetbrains.kotlin.multiplatform") || plugins.hasPlugin("org.jetbrains.kotlin.android")) {
            apply(plugin = "jacoco")
            apply(plugin = "org.jetbrains.dokka")

            tasks.withType<Test> {
                finalizedBy(tasks.matching { it.name == "jacocoTestReport" })
            }

            tasks.register<JacocoReport>("jacocoTestReport") {
                dependsOn(tasks.withType<Test>())
                reports {
                    xml.required.set(true)
                    html.required.set(true)
                    csv.required.set(false)
                }
                val mainClasses = fileTree(layout.buildDirectory.dir("classes/kotlin/jvm/main"))
                classDirectories.setFrom(mainClasses)
                sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/jvmMain/kotlin"))
                executionData.setFrom(fileTree(layout.buildDirectory).include("jacoco/*.exec"))
            }
        }
    }
}
