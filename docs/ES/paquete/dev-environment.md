# Guía de Entorno de Desarrollo — AppLogger SDK

Guía de referencia para configurar un entorno de desarrollo completo y profesional para el proyecto AppLogger. Aplica tanto para contribuidores nuevos como para incorporación en un equipo corporativo.

---

## Índice

1. [Requisitos del Sistema](#1-requisitos-del-sistema)
2. [Instalación de Herramientas](#2-instalación-de-herramientas)
3. [Clonar y Configurar el Repositorio](#3-clonar-y-configurar-el-repositorio)
4. [Variables de Entorno Locales](#4-variables-de-entorno-locales)
5. [Git Hooks — Verificaciones Pre-Push](#5-git-hooks--verificaciones-pre-push)
6. [Verificaciones Locales Manuales](#6-verificaciones-locales-manuales)
7. [CI Local con act + Docker](#7-ci-local-con-act--docker)
8. [Flujo de Trabajo Diario](#8-flujo-de-trabajo-diario)
9. [Resolución de Problemas](#9-resolución-de-problemas)

---

## 1. Requisitos del Sistema

| Herramienta | Versión mínima | Notas |
|---|---|---|
| **Java (JDK)** | 17 LTS | Temurin recomendado |
| **Android Studio** | Ladybug 2024.2+ | O IntelliJ IDEA 2024.3+ |
| **Android SDK** | API 35 (compileSdk) | Con API 23 como minSdk |
| **Docker Desktop** | 24.0+ | Requerido solo para `act` |
| **Git** | 2.40+ | |
| **PowerShell** | 7.4+ (pwsh) | Solo Windows |

> **Espacio en disco:** ~8 GB libres para Gradle cache + Android SDK + Docker imagen `act`.

---

## 2. Instalación de Herramientas

### 2.1 JDK 17 — Eclipse Temurin (recomendado)

**Windows (winget):**
```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

**macOS (Homebrew):**
```bash
brew install --cask temurin@17
```

Verificar:
```bash
java -version
# openjdk version "17.0.x" 2024-xx-xx
# OpenJDK Runtime Environment Temurin-17.0.x
```

> **Importante en Windows:** Si tienes múltiples JDKs, asegúrate de configurar `JAVA_HOME` apuntando al Temurin 17:
> ```powershell
> $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.x.x-hotspot"
> # Para hacerlo permanente: System Properties → Environment Variables
> ```

### 2.2 Android Studio / Android SDK

1. Descargar [Android Studio](https://developer.android.com/studio) versión Ladybug 2024.2 o superior.
2. Durante el instalador, instalar el SDK de Android con:
   - **SDK Platform:** Android 35 (API 35)
   - **SDK Build-Tools:** 35.0.0
   - **NDK (Side by side):** opcional, no requerido
3. Anotar la ruta del `sdk.dir` (usarla en `local.properties`):
   - Windows: `C:\Users\<usuario>\AppData\Local\Android\Sdk`
   - macOS: `~/Library/Android/sdk`
   - Linux: `~/Android/Sdk`

### 2.3 Docker Desktop (para CI local con act)

**Windows:**
```powershell
winget install Docker.DockerDesktop
```

**macOS:**
```bash
brew install --cask docker
```

Verificar:
```bash
docker version
# Requiere: Server Version: 24.x o superior
```

> Asegurarse de que Docker Desktop esté corriendo antes de usar `act`.

### 2.4 act — GitHub Actions local runner

```powershell
# Windows
winget install nektos.act

# macOS
brew install act
```

Verificar:
```bash
act --version
# act version 0.2.x
```

### 2.5 GitHub CLI (`gh`)

```powershell
# Windows
winget install GitHub.cli

# macOS
brew install gh
```

Autenticación:
```bash
gh auth login
# Seleccionar: GitHub.com → HTTPS → Authenticate with browser
```

---

## 3. Clonar y Configurar el Repositorio

```bash
git clone https://github.com/zuccadev-labs/appLoggers.git
cd appLoggers
```

### 3.1 Registrar los git hooks del proyecto

Este proyecto tiene hooks en `.githooks/` que se activan automáticamente:

```bash
git config core.hooksPath .githooks
```

Verificar que funciona:
```bash
git config core.hooksPath
# .githooks
```

Hooks incluidos:

| Hook | Cuándo se ejecuta | Qué hace |
|---|---|---|
| `commit-msg` | Al hacer `git commit` | Valida formato Conventional Commits |
| `pre-push` | Al hacer `git push` | Corre Detekt + tests JVM localmente |

### 3.2 Hacer ejecutables los hooks (Linux/macOS)

```bash
chmod +x .githooks/commit-msg .githooks/pre-push
```

> En Windows (Git Bash / WSL), Git respeta el bit de ejecución del index. No se necesita acción adicional si usas PowerShell + Git nativo.

---

## 4. Variables de Entorno Locales

### 4.1 `local.properties`

Crear el archivo copiando el ejemplo:

```bash
cp local.properties.example local.properties
```

Editar con los valores reales:

```properties
# ─── Android SDK ─────────────────────────────────────────────
sdk.dir=C\:\\Users\\<tuUsuario>\\AppData\\Local\\Android\\Sdk

# ─── Supabase ────────────────────────────────────────────────
appLogger_supabaseUrl=https://hqvkrsmlphjnkefpfpzg.supabase.co
APPLOGGER_SUPABASE_ANON_KEY=<tu-anon-key>
APPLOGGER_SUPABASE_SERVICE_KEY=<tu-service-key>

# ─── Debug ───────────────────────────────────────────────────
APPLOGGER_DEBUG=true
```

> `local.properties` está en `.gitignore`. **Nunca lo commitees.** Contiene claves privadas.

Las claves de Supabase se obtienen en:  
`https://supabase.com/dashboard/project/<project-id>/settings/api`

### 4.2 `JAVA_HOME` en `gradle.properties` (SDK)

El archivo `sdk/gradle.properties` ya tiene una entrada `org.gradle.java.home` apuntando a la instalación de Temurin del desarrollador original. Si tu JDK está en otra ruta, editar esa línea:

```properties
# sdk/gradle.properties
org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.x.x-hotspot
```

> En CI esta línea se elimina automáticamente (`sed -i '/org.gradle.java.home/d' gradle.properties`) porque GitHub Actions usa su propia JDK.

---

## 5. Git Hooks — Verificaciones Pre-Push

El hook `pre-push` **bloquea automáticamente** cualquier push si las verificaciones fallan. Replica el comportamiento del CI remoto:

```
╔══════════════════════════════════════════════╗
║   AppLogger — Pre-Push Checks                ║
╚══════════════════════════════════════════════╝

[1/2] Ejecutando Detekt (análisis estático)...
✔ Detekt: OK

[2/2] Ejecutando tests unitarios (JVM)...
✔ Tests: OK

╔══════════════════════════════════════════════╗
║   ✔ Todas las verificaciones pasaron         ║
║   Push permitido.                            ║
╚══════════════════════════════════════════════╝
```

Si alguna verificación falla, el push se cancela con el error específico.

### Saltear el hook (casos de emergencia únicamente)

```bash
git push --no-verify
```

> **No usar en el flujo normal.** El CI de GitHub igualmente rechazará el cambio si hay errores.

---

## 6. Verificaciones Locales Manuales

Ejecutar individualmente las mismas tareas que corre el CI:

```bash
cd sdk

# Lint estático (replica CI job: lint)
./gradlew detekt

# Tests unitarios JVM (replica CI job: test — unit)
./gradlew :logger-core:jvmTest :logger-test:jvmTest

# Todos los tests JVM
./gradlew jvmTest

# Cobertura (requiere tests previos)
./gradlew jacocoTestReport

# Build completo
./gradlew assemble

# Todos los checks de una vez (orden correcto)
./gradlew detekt :logger-core:jvmTest :logger-test:jvmTest
```

Ver reportes generados:

| Tipo | Ruta |
|---|---|
| Detekt | `sdk/build/reports/detekt/detekt.html` |
| Tests unitarios | `sdk/logger-core/build/reports/tests/jvmTest/index.html` |
| Cobertura | `sdk/logger-core/build/reports/jacoco/jacocoTestReport/html/index.html` |

---

## 7. CI Local con act + Docker

`act` permite correr el pipeline de GitHub Actions dentro de contenedores Docker locales. Útil para verificar los jobs completos incluyendo setup de Java y Gradle.

### 7.1 Configuración inicial (solo una vez)

Copiar el archivo de secrets:
```bash
cp .act.secrets.example .act.secrets
# Completar los valores en .act.secrets
```

El archivo `.actrc` del proyecto ya está configurado con las imágenes correctas. No se necesita configuración adicional.

### 7.2 Correr jobs individuales

```bash
# Desde la raíz del repo:

# Job lint (Detekt)
act push -W .github/workflows/ci.yml --job lint

# Job test (unit tests + build)
act push -W .github/workflows/ci.yml --job test

# Job security (CodeQL + dependency submission)
# Requiere permisos de GitHub — no recomendado para uso local
```

### 7.3 Limitaciones conocidas de `act`

| Limitación | Descripción | Alternativa |
|---|---|---|
| Composite actions en v4 | Puede fallar con `unsupported object type` al resolver tags | Usar `./gradlew` directamente (Sección 6) |
| Android SDK | La imagen `act-latest` no incluye Android SDK | Solo correr jobs que no requieran compilación Android |
| Permisos de `gradlew` en Windows | `act` puede perder el execute bit al copiar desde NTFS al contenedor Linux | Mantener `chmod +x ./gradlew` en los workflows |
| CodeQL | Requiere autenticación GitHub y no funciona en local | Solo corre en CI remoto |
| Upload de artifacts | `actions/upload-artifact` puede fallar sin `ACTIONS_RUNTIME_TOKEN` | Validar compilación y tests localmente; dejar upload para CI remoto |
| Secretos | `.act.secrets` requiere valores reales para tests e2e | Usar mocks para desarrollo local |

> **Recomendación:** Para el desarrollo diario, usar las verificaciones manuales de la Sección 6. Reservar `act` para verificar cambios en los propios archivos `.github/workflows/`.

---

## 8. Flujo de Trabajo Diario

```
┌─────────────────────────────────────────────────────────────┐
│                   FLUJO NORMAL DE DESARROLLO                │
└─────────────────────────────────────────────────────────────┘

1. Crear rama desde dev
   git checkout dev && git pull
   git checkout -b feat/nombre-de-la-feature

2. Desarrollar y hacer commits
   git commit -m "feat(core): descripción clara"
   # → commit-msg hook: valida Conventional Commits

3. Verificar antes de push (automático vía hook)
   git push origin feat/nombre-de-la-feature
   # → pre-push hook: corre Detekt + jvmTest
   # → Si fallan: corregir y volver al paso 2

4. Abrir Pull Request
   gh pr create --base dev --title "feat(core): ..." --body "..."
   # → CI remoto: lint → test → security

5. Merge (solo cuando CI está en verde)
   # Revisar en https://github.com/zuccadev-labs/appLoggers/pulls
```

### Diagrama de ramas

```
main          ←──── PR (solo desde dev, cuando todo está verde)
  └── dev     ←──── PRs de features/fixes
        └── feat/xxx
        └── fix/yyy
        └── chore/zzz
```

---

## 9. Resolución de Problemas

### `./gradlew: Permission denied` en CI

El archivo `sdk/gradlew` necesita el bit de ejecución en git:
```bash
git update-index --chmod=+x sdk/gradlew
git commit -m "chore: fix gradlew execute permission"
```

### `JAVA_HOME not set` al correr Gradle

Asegurarse de que `org.gradle.java.home` en `sdk/gradle.properties` apunta al JDK 17 correcto. Ver Sección 4.2.

### `act` falla con `unsupported object type`

Esto ocurre al resolver tags de actions (p.ej. `setup-java@v4`) con ciertas versiones de `act`. Solución: usar las verificaciones locales directamente con Gradle (Sección 6).

### Tests fallan localmente pero pasan en CI

Verificar que `JAVA_HOME` apunta a JDK 17 (no 21, no 25). El CI usa estrictamente Temurin 17.

### Supabase e2e tests fallan localmente

Los tests en `SupabaseE2ETest.kt` requieren credenciales reales en `local.properties`. Asegurarse de que `appLogger_supabaseUrl`, `APPLOGGER_SUPABASE_ANON_KEY`, y `APPLOGGER_SUPABASE_SERVICE_KEY` estén configurados. Los tests e2e solo corren en CI cuando se hace push a `main`.

---

*Última actualización: 2026-03-18*
