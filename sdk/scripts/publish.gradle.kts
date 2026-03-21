// scripts/publish.gradle.kts
// Aplicar en cada módulo publicable: apply(from = rootProject.file("scripts/publish.gradle.kts"))

afterEvaluate {
    publishing {
        publications.withType<MavenPublication> {
            groupId = project.findProperty("GROUP")?.toString() ?: "com.github.zuccadev-labs"
            version = project.findProperty("VERSION_NAME")?.toString() ?: "0.1.1-alpha.3"

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
                url = uri("https://maven.pkg.github.com/zuccadev-labs/appLoggers")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: ""
                    password = System.getenv("GITHUB_TOKEN") ?: ""
                }
            }
        }
    }
}
