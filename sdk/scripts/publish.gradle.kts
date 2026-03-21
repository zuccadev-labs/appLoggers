// scripts/publish.gradle.kts
// Aplicar en cada módulo publicable: apply(from = rootProject.file("scripts/publish.gradle.kts"))

val resolvedGroup = project.findProperty("GROUP")?.toString() ?: "com.github.devzucca"
val resolvedVersion = project.findProperty("VERSION_NAME")?.toString() ?: "0.0.0-dev"

afterEvaluate {
    publishing {
        publications.withType<MavenPublication> {
            groupId = project.findProperty("GROUP")?.toString() ?: resolvedGroup
            version = project.findProperty("VERSION_NAME")?.toString() ?: resolvedVersion

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

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/devzucca/appLoggers")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: ""
                    password = System.getenv("GITHUB_TOKEN") ?: ""
                }
            }
        }
    }
}
