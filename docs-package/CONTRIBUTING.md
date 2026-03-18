# Contribuir a AppLogger

¡Primero, gracias por considerarlo! AppLogger es un proyecto de código abierto y toda contribución suma.

---

## Índice

1. [Código de Conducta](#1-código-de-conducta)
2. [¿Cómo Contribuir?](#2-cómo-contribuir)
3. [Configuración del Entorno de Desarrollo](#3-configuración-del-entorno-de-desarrollo)
4. [Estándares de Código](#4-estándares-de-código)
5. [Proceso de Pull Request](#5-proceso-de-pull-request)
6. [Reportar Bugs](#6-reportar-bugs)
7. [Proponer Nuevas Funcionalidades](#7-proponer-nuevas-funcionalidades)
8. [Implementar un LogTransport Personalizado](#8-implementar-un-logtransport-personalizado)

---

## 1. Código de Conducta

Este proyecto sigue el [Contributor Covenant v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/). En resumen: trato respetuoso con todos los participantes independientemente de experiencia, género, nacionalidad o cualquier otra característica personal.

---

## 2. ¿Cómo Contribuir?

### Contribuciones bienvenidas

- **Bugfixes**: errores reproducibles con un test que falla antes del fix.
- **Nuevas implementaciones de `LogTransport`**: Firebase, Datadog, Amplitude, servidor gRPC propio, etc.
- **Mejoras de documentación**: correcciones, ejemplos adicionales, traducciones.
- **Optimizaciones de performance**: especialmente para Android TV / devices con recursos limitados.
- **Tests adicionales**: cobertura de casos extremos.

### Lo que no aceptamos

- Cambios en la API pública `AppLogger` que rompan backwards compatibility sin una propuesta de RFC aprobada.
- Nuevas dependencias obligatorias en `logger-core` (el core debe seguir siendo liviano).
- Código que capture PII o que relaje las garantías de privacidad actuales.

---

## 3. Configuración del Entorno de Desarrollo

### 3.1 Prerrequisitos

- **JDK 17** (recomendado: Eclipse Temurin)
- **Android Studio Iguana+** o IntelliJ IDEA 2024+
- **Git 2.40+**

### 3.2 Fork y clone

```bash
# 1. Fork del repositorio en GitHub (botón "Fork")

# 2. Clonar tu fork
git clone https://github.com/TU_USUARIO/app-logger.git
cd app-logger

# 3. Añadir el upstream
git remote add upstream https://github.com/TuOrganizacion/app-logger.git
```

### 3.3 Verificar que todo compila y los tests pasan

```bash
./gradlew build
./gradlew test
```

Si algo falla en este punto, abrir un issue antes de continuar.

### 3.4 Estructura de ramas

| Rama | Propósito |
|---|---|
| `main` | Código estable del último release |
| `develop` | Integración de features en desarrollo |
| `feature/nombre-descriptivo` | Tu contribución |
| `fix/nombre-del-bug` | Un bugfix |

---

## 4. Estándares de Código

### 4.1 Kotlin Style Guide

Seguimos la [guía oficial de estilo de Kotlin](https://kotlinlang.org/docs/coding-conventions.html) con estas adiciones:

- Indentación: **4 espacios** (no tabs).
- Longitud máxima de línea: **120 caracteres**.
- Todos los `interface` públicos deben tener KDoc.
- Las funciones `internal` e `private` no requieren KDoc pero sí comentarios inline si la lógica no es evidente.

### 4.2 Principios de Clean Code en este proyecto

```kotlin
// ✅ Nombres descriptivos que expresan intención
fun buildFilter(config: AppLoggerConfig): LogFilter

// ❌ Abreviaturas que requieren contexto externo
fun bldFltr(cfg: AppLoggerConfig): LogFilter

// ✅ Funciones cortas con una sola responsabilidad
internal fun ThrowableInfo?.format(): String

// ❌ Funciones que hacen múltiples cosas no relacionadas
internal fun processAndSendAndLog(): Unit

// ✅ No comentar el qué (que ya dice el código), sino el por qué
// runBlocking está justificado aquí: el proceso va a morir, no hay riesgo de ANR
runBlocking(Dispatchers.IO) { logger.flush() }

// ❌ Comentarios que repiten el código
// Llama a flush
logger.flush()
```

### 4.3 Diseño por Traits — Regla de Oro

Antes de añadir código en `AppLoggerImpl` o `BatchProcessor`, preguntarse:
> ¿Podría este comportamiento ser abstraído en un trait e inyectado?

Si la respuesta es sí, crear el trait en `logger-core` y pasar la implementación como dependencia.

### 4.4 Ningún cambio en el core sin test

Toda modificación en `logger-core` debe estar acompañada de:
1. Un test unitario que falle sin el fix / sin la feature.
2. Un test unitario que pase con el fix / con la feature.

---

## 5. Proceso de Pull Request

### 5.1 Antes de abrir el PR

```bash
# Mantener tu rama actualizada con upstream/develop
git fetch upstream
git rebase upstream/develop

# Verificar que los tests pasan
./gradlew test

# Verificar lint
./gradlew lint
```

### 5.2 Template de Pull Request

Al abrir un PR, el template pedirá:

```markdown
## Descripción
[Qué hace este PR en 1-2 oraciones]

## Tipo de cambio
- [ ] Bugfix (fix no-breaking que corrige un issue)
- [ ] Nueva funcionalidad (cambio no-breaking que añade funcionalidad)
- [ ] Breaking change (fix o feature que cambia la API existente)
- [ ] Documentación

## Tests añadidos
- [ ] Sí — los tests nuevos cubren el cambio
- [ ] No — explicación: ...

## Checklist
- [ ] Mi código sigue el estilo del proyecto
- [ ] He actualizado el CHANGELOG.md
- [ ] Los tests unitarios pasan localmente
- [ ] No he añadido dependencias obligatorias al módulo logger-core
```

### 5.3 Revisión y Merge

- Los PRs requieren **1 review aprobado** de un maintainer.
- Los PRs que cambian la API pública requieren **2 reviews**.
- El CI (GitHub Actions) debe estar en verde antes del merge.
- Se hace **Squash Merge** a `develop` para mantener historial limpio.

---

## 6. Reportar Bugs

Usar el template de issue "Bug Report" en GitHub. Incluir:

1. **Versión del SDK** (`AppLogger 0.1.1`).
2. **Plataforma** (Android Mobile / Android TV / JVM).
3. **Versión de Android** y modelo del dispositivo.
4. **Pasos para reproducir** el problema.
5. **Comportamiento esperado** vs **comportamiento actual**.
6. **Stack trace** si aplica (sin PII).

---

## 7. Proponer Nuevas Funcionalidades

Para cambios significativos (nuevos traits, cambios de API, nuevos módulos), abrir un issue de tipo "Feature Request / RFC" antes de implementar. Esto evita trabajo duplicado o en la dirección incorrecta.

El issue debe describir:
- **El problema que resuelve** (no la solución directamente).
- **Casos de uso** concretos.
- **Impacto en la API existente**: ¿es backwards compatible?

---

## 8. Implementar un LogTransport Personalizado

Si quieres contribuir un nuevo transporte (Firebase, Amplitude, etc.), crear un módulo separado `logger-transport-nombre`:

```kotlin
// logger-transport-firebase/build.gradle.kts
dependencies {
    implementation(project(":logger-core"))
    implementation("com.google.firebase:firebase-database:20.x.x")
}
```

```kotlin
// Implementar el trait LogTransport
class FirebaseTransport(...) : LogTransport {
    override suspend fun send(events: List<LogEvent>): TransportResult { ... }
    override fun isAvailable(): Boolean { ... }
}
```

El módulo del transporte debe incluir:
- Su propio `README.md` con instrucciones de uso.
- Al menos un test de integración (con un proyecto Firebase de staging).
- Un ejemplo de configuración en el `AppLoggerConfig.Builder`.
