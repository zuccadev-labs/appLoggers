# AppLogger — Guía de Publicación del Paquete

**Versión:** 0.1.1  
**Fecha:** 2026-03-17  
**Plataformas de publicación:** JitPack · GitHub Packages · Maven Central · Swift Package Manager (iOS consumo)

---

## Índice

1. [Preparación del Proyecto](#1-preparación-del-proyecto)
2. [Configuración de maven-publish](#2-configuración-de-maven-publish)
3. [Publicar en JitPack](#3-publicar-en-jitpack)
4. [Publicar en GitHub Packages](#4-publicar-en-github-packages)
5. [Publicar en Maven Central (Sonatype)](#5-publicar-en-maven-central-sonatype)
6. [Versionado con Git Tags](#6-versionado-con-git-tags)
7. [CI/CD con GitHub Actions](#7-cicd-con-github-actions)
8. [Proceso de Release Checklist](#8-proceso-de-release-checklist)

---

## 1. Preparación del Proyecto

### 1.1 Estructura recomendada de versión

El archivo `gradle/libs.versions.toml` (Version Catalog) centraliza todas las versiones del proyecto. Consultar el archivo directamente para las versiones actualizadas:

```bash
cat gradle/libs.versions.toml
```

### 1.2 Configuración de `gradle.properties`

```properties
# gradle.properties
GROUP=com.github.TuOrganizacion
POM_ARTIFACT_ID=app-logger
VERSION_NAME=0.1.1

POM_NAME=AppLogger
POM_DESCRIPTION=Kotlin Multiplatform SDK for structured technical telemetry on Android/TV/iOS/JVM
POM_URL=https://github.com/TuOrganizacion/app-logger
POM_LICENCE_NAME=MIT License
POM_LICENCE_URL=https://opensource.org/licenses/MIT
POM_DEVELOPER_ID=tu-github-username
POM_DEVELOPER_NAME=Tu Nombre
POM_SCM_URL=https://github.com/TuOrganizacion/app-logger
```

---

## 2. Configuración de maven-publish

### 2.1 Script compartido `publish.gradle.kts`

Crear en la raíz del proyecto `scripts/publish.gradle.kts`:

```kotlin
// scripts/publish.gradle.kts
// Aplicar en cada módulo publicable: apply(from = rootProject.file("scripts/publish.gradle.kts"))

plugins {
    `maven-publish`
    signing
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId    = project.findProperty("GROUP").toString()
                artifactId = project.findProperty("POM_ARTIFACT_ID").toString()
                version    = project.findProperty("VERSION_NAME").toString()

                // Para módulos Android (AAR)
                from(components["release"])

                // Para módulos Kotlin/JVM puro (JAR)
                // from(components["java"])

                pom {
                    name.set(project.findProperty("POM_NAME").toString())
                    description.set(project.findProperty("POM_DESCRIPTION").toString())
                    url.set(project.findProperty("POM_URL").toString())

                    licenses {
                        license {
                            name.set(project.findProperty("POM_LICENCE_NAME").toString())
                            url.set(project.findProperty("POM_LICENCE_URL").toString())
                        }
                    }

                    developers {
                        developer {
                            id.set(project.findProperty("POM_DEVELOPER_ID").toString())
                            name.set(project.findProperty("POM_DEVELOPER_NAME").toString())
                        }
                    }

                    scm {
                        url.set(project.findProperty("POM_SCM_URL").toString())
                    }
                }
            }
        }

        repositories {
            // GitHub Packages
            maven {
                name = "GitHubPackages"
                url  = uri("https://maven.pkg.github.com/TuOrganizacion/app-logger")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }

    // Firma de artefactos (requerido para Maven Central)
    signing {
        val signingKey     = System.getenv("GPG_SIGNING_KEY")
        val signingPasswd  = System.getenv("GPG_SIGNING_PASSWD")
        if (signingKey != null && signingPasswd != null) {
            useInMemoryPgpKeys(signingKey, signingPasswd)
            sign(publishing.publications["release"])
        }
    }
}
```

### 2.2 Aplicar el script en cada módulo

```kotlin
// logger-android/build.gradle.kts
apply(from = rootProject.file("scripts/publish.gradle.kts"))

android {
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}
```

### 2.3 Publicación KMP (Android + iOS + JVM)

Para módulos Kotlin Multiplatform, `maven-publish` genera múltiples publicaciones por target. Configuración recomendada:

```kotlin
// logger-core/build.gradle.kts
kotlin {
    androidTarget()
    iosX64(); iosArm64(); iosSimulatorArm64()
    jvm()
}

publishing {
    publications {
        // Se generan automáticamente:
        // - kotlinMultiplatform (metadata)
        // - androidRelease
        // - jvm
        // - iosArm64, iosX64, iosSimulatorArm64
    }
}
```

Comandos útiles de publicación:

```bash
# Publicar todo (metadata + targets) a un repo Maven
./gradlew publishAllPublicationsToGitHubPackagesRepository

# Publicar solo Android release
./gradlew publishAndroidReleasePublicationToGitHubPackagesRepository

# Publicar solo metadata KMP
./gradlew publishKotlinMultiplatformPublicationToGitHubPackagesRepository
```

Para iOS, el consumo habitual se hace vía XCFramework generado en CI y distribuido por release de GitHub o por repositorio SwiftPM.

---

## 3. Publicar en JitPack

JitPack construye el artefacto directamente desde el repositorio de GitHub. No requiere configuración adicional en JitPack.

### 3.1 Crear un release en GitHub

```bash
git tag -a v0.1.1 -m "Release 0.1.1"
git push origin v0.1.1
```

### 3.2 Activar la build en JitPack

1. Ir a [jitpack.io](https://jitpack.io)
2. Buscar `TuOrganizacion/app-logger`
3. Hacer clic en **Get it** junto al tag `v0.1.1`
4. JitPack construye el artefacto automáticamente

### 3.3 Consumo desde la app

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.TuOrganizacion:app-logger:v0.1.1")
}
```

### 3.4 Badge en README

```markdown
[![](https://jitpack.io/v/TuOrganizacion/app-logger.svg)](https://jitpack.io/#TuOrganizacion/app-logger)
```

---

## 4. Publicar en GitHub Packages

GitHub Packages es ideal para distribución interna dentro de una organización.

```bash
# Publicar desde local (requiere GITHUB_TOKEN con permisos write:packages)
export GITHUB_ACTOR="tu-username"
export GITHUB_TOKEN="ghp_xxxxxxxxxxxx"

./gradlew publishReleasePublicationToGitHubPackagesRepository
```

### 4.1 Consumo desde otra app de la organización

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/TuOrganizacion/app-logger")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

---

## 5. Publicar en Maven Central (Sonatype)

Maven Central es el destino final para distribución pública general. Requiere cuenta en Sonatype OSSRH.

### 5.1 Prerrequisitos

1. Cuenta en [central.sonatype.com](https://central.sonatype.com)
2. Par de claves GPG para firmar los artefactos
3. `groupId` verificado (para `com.github.TuOrg`, JitPack gestiona; para un dominio propio, requerirá verificación)

### 5.2 Configuración adicional en `publish.gradle.kts`

```kotlin
repositories {
    maven {
        name = "MavenCentral"
        url = if (version.toString().endsWith("SNAPSHOT"))
            uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        else
            uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
        credentials {
            username = System.getenv("OSSRH_USERNAME")
            password = System.getenv("OSSRH_PASSWORD")
        }
    }
}
```

---

## 6. Versionado con Git Tags

### 6.1 Convención de tags

```bash
# Estable
git tag -a v0.1.1 -m "Initial alpha release"

# Alpha (API puede cambiar)
git tag -a v0.2.0-alpha.1 -m "New transport trait alpha"

# Beta (feature complete, en pruebas)
git tag -a v0.2.0-beta.1 -m "Beta for v0.2.0"

# Hotfix
git tag -a v0.1.2 -m "Fix crash handler chain"
```

### 6.2 Leer la versión desde el tag en Gradle

```kotlin
// build.gradle.kts raíz
fun getCurrentVersion(): String {
    return try {
        val stdout = ByteArrayOutputStream()
        exec {
            commandLine("git", "describe", "--tags", "--match", "v*", "--abbrev=0")
            standardOutput = stdout
        }
        stdout.toString().trim().removePrefix("v")
    } catch (e: Exception) {
        "0.0.0-SNAPSHOT"
    }
}

allprojects {
    version = getCurrentVersion()
}
```

---

## 7. CI/CD con GitHub Actions

### 7.1 Workflow de Release Automático

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - uses: gradle/gradle-build-action@v2

      - name: Run Tests
        run: ./gradlew test

      - name: Publish to GitHub Packages
        env:
          GITHUB_ACTOR: ${{ github.actor }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: ./gradlew publishReleasePublicationToGitHubPackagesRepository

      - name: Create GitHub Release
        uses: ncipollo/release-action@v1
        with:
          generateReleaseNotes: true
          token: ${{ secrets.GITHUB_TOKEN }}
```

### 7.2 Workflow de CI en PRs

```yaml
# .github/workflows/ci.yml
name: CI

on:
  pull_request:
    branches: [ main, develop ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - uses: gradle/gradle-build-action@v2
      - name: Run Unit Tests
        run: ./gradlew test
      - name: Lint
        run: ./gradlew lint
```

---

## 8. Proceso de Release Checklist

Antes de crear un tag de release, verificar:

### 8.1 Código

- [ ] Todos los tests unitarios pasan: `./gradlew test`
- [ ] No hay fallos de lint: `./gradlew lint`
- [ ] El `CHANGELOG.md` tiene la entrada para esta versión
- [ ] El `VERSION_NAME` en `gradle.properties` coincide con el tag que se va a crear
- [ ] No hay `TODO` o `FIXME` críticos en el código publicado

### 8.2 Documentación

- [ ] El `README.md` principal tiene la versión actualizada en el snippet de instalación
- [ ] Los cambios de API están documentados en `docs-package/architecture.md`
- [ ] Si hay breaking changes, están marcados en `CHANGELOG.md` con `BREAKING:`
- [ ] La matriz de compatibilidad (Android/iOS/JVM) está actualizada en `docs-develop/integration-guide.md`

### 8.3 Seguridad

- [ ] No hay API keys hardcodeadas en ningún archivo del repositorio
- [ ] `local.properties` está en `.gitignore`
- [ ] Las dependencias externas no tienen vulnerabilidades conocidas: `./gradlew dependencyCheckAnalyze`

### 8.4 Post-release

- [ ] El tag fue creado y pushed: `git push origin v1.x.x`
- [ ] La build de JitPack fue activada y exitosa
- [ ] El GitHub Release tiene release notes generadas
- [ ] El XCFramework iOS fue generado y adjuntado al release
- [ ] El manifiesto de SwiftPM apunta a la versión liberada
