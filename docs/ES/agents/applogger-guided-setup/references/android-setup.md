# Android Setup Reference

## Dependency setup

Add JitPack in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add dependencies in the app module:

```kotlin
dependencies {
    // Reemplazar <latest-version> con la última release: https://github.com/zuccadev-labs/appLoggers/releases
    implementation("com.github.zuccadev-labs.appLoggers:logger-core:<latest-version>")
    implementation("com.github.zuccadev-labs.appLoggers:logger-transport-supabase:<latest-version>")

    testImplementation("com.github.zuccadev-labs.appLoggers:logger-test:<latest-version>")
}
```

## Required permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## local.properties policy

If the project uses `local.properties`:

1. Check whether these keys already exist: `APPLOGGER_URL`, `APPLOGGER_ANON_KEY`, `APPLOGGER_DEBUG`.
2. Add only missing keys.
3. Do not edit, remove, or rename any unrelated existing variable.

Example append-only update:

```properties
APPLOGGER_URL=https://YOUR-PROJECT.supabase.co
APPLOGGER_ANON_KEY=YOUR_ANON_KEY
APPLOGGER_DEBUG=false
```

## BuildConfig mapping (required if using `BuildConfig.LOGGER_*`)

```kotlin
import java.util.Properties

val appLoggerLocalProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    buildFeatures { buildConfig = true }

    defaultConfig {
        val loggerUrl   = appLoggerLocalProps.getProperty("APPLOGGER_URL", "")
        val loggerKey   = appLoggerLocalProps.getProperty("APPLOGGER_ANON_KEY", "")
        val loggerDebug = appLoggerLocalProps.getProperty("APPLOGGER_DEBUG", "false").toBoolean()

        buildConfigField("String",  "LOGGER_URL",   "\"$loggerUrl\"")
        buildConfigField("String",  "LOGGER_KEY",   "\"$loggerKey\"")
        buildConfigField("boolean", "LOGGER_DEBUG", loggerDebug.toString())
    }
}
```

## Debug mode via APPLOGGER_DEBUG (sin cambiar código)

El SDK lee `APPLOGGER_DEBUG` del manifest meta-data automáticamente. Si está presente y es `"true"`, activa `isDebugMode = true` sin necesidad de cambiar código:

```xml
<!-- AndroidManifest.xml -->
<application ...>
    <meta-data
        android:name="APPLOGGER_DEBUG"
        android:value="${APPLOGGER_DEBUG}" />
</application>
```

```groovy
// build.gradle (app module)
android {
    defaultConfig {
        manifestPlaceholders = [
            APPLOGGER_DEBUG: System.getenv("APPLOGGER_DEBUG") ?: "false"
        ]
    }
}
```

Regla efectiva: Logcat output ocurre solo cuando `isDebugMode=true` **y** `consoleOutput=true`. En producción (`isDebugMode=false`), Logcat está **siempre suprimido** sin importar `consoleOutput`.

## Canonical imports (Android)

```kotlin
import com.applogger.core.AppLoggerConfig
import com.applogger.core.AppLoggerHealth
import com.applogger.core.AppLoggerSDK
import com.applogger.core.BufferOverflowPolicy
import com.applogger.core.BufferSizeStrategy
import com.applogger.core.LogMinLevel
import com.applogger.core.OfflinePersistenceMode
import com.applogger.transport.supabase.SupabaseTransport
```

Do not use `com.applogger.sdk.*` imports.

## Initialization pattern

Preferred initialization point: custom `Application.onCreate()`.

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val transport = SupabaseTransport(
            endpoint = BuildConfig.LOGGER_URL,
            apiKey = BuildConfig.LOGGER_KEY,
            networkAvailabilityProvider = androidNetworkAvailabilityProvider(this)
        )

        AppLoggerSDK.initialize(
            context = this,
            config = AppLoggerConfig.Builder()
                .endpoint(BuildConfig.LOGGER_URL)
                .apiKey(BuildConfig.LOGGER_KEY)
                .environment("production")          // "production" | "staging" | "development"
                .debugMode(BuildConfig.LOGGER_DEBUG)
                .consoleOutput(BuildConfig.LOGGER_DEBUG)
                .minLevel(LogMinLevel.INFO)          // descarta DEBUG en producción
                .batchSize(20)
                .flushIntervalSeconds(30)
                .build(),
            transport = transport
        )

        // Validar configuración en debug
        if (BuildConfig.DEBUG) {
            AppLoggerConfig.Builder()
                .endpoint(BuildConfig.LOGGER_URL)
                .apiKey(BuildConfig.LOGGER_KEY)
                .build()
                .validate()
                .forEach { android.util.Log.w("AppLogger", "Config issue: $it") }
        }
    }
}
```

`androidNetworkAvailabilityProvider(context)` devuelve una lambda que usa `ConnectivityManager` con estado en caché — sin I/O en el hilo llamador.

## Session and user identity

```kotlin
// Al hacer login — nueva sesión + user ID
AppLoggerSDK.setAnonymousUserId(anonymousUUID)
AppLoggerSDK.newSession()

// Al hacer logout — limpiar identidad
AppLoggerSDK.clearAnonymousUserId()
AppLoggerSDK.newSession()

// Global extra — adjunta a todos los eventos posteriores
AppLoggerSDK.addGlobalExtra("ab_test", "checkout_v2")
AppLoggerSDK.addGlobalExtra("experiment", "group_b")
AppLoggerSDK.removeGlobalExtra("ab_test")
AppLoggerSDK.clearGlobalExtra()

// Session variant — A/B testing (campo top-level, no en extra)
AppLoggerSDK.setSessionVariant("checkout_v2")
AppLoggerSDK.setSessionVariant(null)  // limpiar

// User properties — suprimidas en modo STRICT/PERFORMANCE
AppLoggerSDK.setUserProperty("user_tier", "premium")
AppLoggerSDK.setUserProperty("subscription", "annual")
AppLoggerSDK.removeUserProperty("user_tier")
```

## Consent management

```kotlin
import com.applogger.core.ConsentLevel

// Cambiar nivel de consentimiento en runtime (persiste en SharedPreferences)
AppLoggerSDK.setConsent(ConsentLevel.MARKETING)   // telemetría completa — opt-in recibido
AppLoggerSDK.setConsent(ConsentLevel.PERFORMANCE) // solo métricas — T&C aceptados
AppLoggerSDK.setConsent(ConsentLevel.STRICT)       // solo errores — sin consentimiento

val level: ConsentLevel = AppLoggerSDK.getConsent()
```

Consent inference automática: CRITICAL/ERROR → STRICT, METRIC/WARN → PERFORMANCE, INFO/DEBUG → MARKETING.
En modo STRICT: `user_id=null`, `device_id` pseudonimizado.
Configurar nivel por defecto al inicializar: `.defaultConsentLevel(ConsentLevel.STRICT)` en el Builder.

## Device fingerprint (identificación persistente de dispositivo)

El SDK genera automáticamente un **device fingerprint pseudonimizado** al inicializar:

```
fingerprint = SHA-256(ANDROID_ID + ":" + package_name)
```

### Propiedades

| Propiedad | Valor |
|---|---|
| **Persistencia** | Sobrevive reinstalación de APK. Solo se resetea con factory reset. |
| **Único por app** | Dos apps distintas en el mismo dispositivo producen hashes diferentes. |
| **GDPR Art. 25** | Pseudonimizado — no se puede revertir al `ANDROID_ID` original. |
| **Determinista** | Mismo dispositivo + misma app = mismo hash, siempre. |

### Cómo funciona

1. El SDK captura `Settings.Secure.ANDROID_ID` (persiste entre reinstalaciones).
2. Combina con el `package_name` de la app: `"$androidId:$packageName"`.
3. Aplica `SHA-256` para producir un hash irreversible de 64 caracteres hex.
4. Lo inyecta automáticamente como `device_fingerprint` en el campo `extra` de cada evento.

### Uso en código

```kotlin
// Leer el fingerprint (después de initialize)
val fp = AppLoggerSDK.getDeviceFingerprint()
// Ejemplo: "a1b2c3d4e5f6...64 chars hex"
```

**No necesitas configurar nada** — el fingerprint se captura y adjunta automáticamente.

### Dónde queda en Supabase

El fingerprint viaja dentro del campo JSONB `extra` de la tabla `app_logs`:

```sql
-- Buscar todos los eventos de un dispositivo específico
SELECT * FROM app_logs
WHERE extra->>'device_fingerprint' = 'a1b2c3d4e5f6...'
ORDER BY created_at DESC;

-- Contar dispositivos únicos por sesión
SELECT session_id, extra->>'device_fingerprint' AS device
FROM app_logs
GROUP BY session_id, device;
```

El índice GIN en `extra` (migración 012) permite estas consultas de manera eficiente.

### Tabla `device_remote_config`

El fingerprint se usa como clave en la tabla `device_remote_config` para configuración remota por dispositivo:

```sql
-- Estructura de la tabla (migración 013)
CREATE TABLE device_remote_config (
    id                UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    device_fingerprint TEXT,           -- NULL = regla global (aplica a TODOS)
    environment       VARCHAR(50),     -- NULL = todos los entornos
    min_level         VARCHAR(20),     -- DEBUG|INFO|WARN|ERROR|CRITICAL
    debug_enabled     BOOLEAN,         -- Activar debug remotamente
    tags_allow        TEXT[],          -- Solo estos tags pasan
    tags_block        TEXT[],          -- Estos tags se descartan
    sampling_rate     DOUBLE PRECISION,-- 0.0 a 1.0 (1.0 = guardar todo)
    enabled           BOOLEAN DEFAULT true,
    notes             TEXT
);
```

## Remote config (configuración remota por dispositivo)

Permite controlar remotamente qué eventos registra cada dispositivo, sin tocar el código de la app.

### Activar en el SDK

```kotlin
AppLoggerConfig.Builder()
    .endpoint(url)
    .apiKey(key)
    .remoteConfigEnabled(true)              // Activa polling
    .remoteConfigIntervalSeconds(300)       // Cada 5 minutos (rango: 30-3600)
    // ... resto de config
    .build()
```

### Gestionar desde el CLI

```bash
# Ver todas las reglas activas
apploggers remote-config list

# Regla GLOBAL: solo errores y críticos para todos
apploggers remote-config set --min-level ERROR

# Regla POR DISPOSITIVO: activar debug para un dispositivo específico
apploggers remote-config set \
    --fingerprint "a1b2c3d4e5f6..." \
    --debug true \
    --min-level DEBUG \
    --notes "Debug usuario issue #456"

# Filtrar tags: solo capturar Auth y Network para un dispositivo
apploggers remote-config set \
    --fingerprint "a1b2c3d4e5f6..." \
    --tags-allow "Auth,Network"

# Sampling: capturar solo 10% de eventos globalmente
apploggers remote-config set --sampling-rate 0.1

# Desactivar una regla por ID
apploggers remote-config delete --id "uuid-de-la-regla"

# Eliminar todas las reglas de un dispositivo
apploggers remote-config delete --fingerprint "a1b2c3d4e5f6..."
```

### Reglas de prioridad

1. **Regla por dispositivo** (fingerprint específico) gana sobre regla global.
2. **Regla global** (fingerprint NULL) es el fallback.
3. **Config local** (AppLoggerConfig) es el fallback final si no hay reglas remotas.

### Protección de eventos críticos

**ERROR y CRITICAL nunca se filtran** por tag filtering ni sampling.
Solo `minLevel` puede filtrarlos (si se sube a CRITICAL).

### Refrescar manualmente

```kotlin
// Forzar refresco de config remota (útil después de cambiar reglas)
AppLoggerSDK.refreshRemoteConfig()
```

### Flujo completo: "quiero debuggear un dispositivo en producción"

1. El usuario reporta un problema. Pides su fingerprint o lo buscas en Supabase.
2. Desde el CLI: `apploggers remote-config set --fingerprint XYZ --debug true --min-level DEBUG`
3. El SDK del dispositivo recoge la regla en el próximo polling (máx 5 min por defecto).
4. El dispositivo ahora envía **todos** los eventos incluyendo DEBUG.
5. Diagnosticas el problema en Supabase.
6. Desactivas: `apploggers remote-config delete --fingerprint XYZ`
7. El dispositivo vuelve a su config local (producción normal).

## Identificación de apps en el mismo dispositivo

El SDK inyecta automáticamente `app_package` (el `packageName` de Android) como global extra en cada evento.
Esto permite distinguir apps diferentes que comparten el mismo dispositivo:

```sql
-- Eventos de una app específica en un dispositivo
SELECT * FROM app_logs
WHERE extra->>'device_fingerprint' = 'a1b2c3...'
  AND extra->>'app_package' = 'com.miempresa.app1';

-- Comparar comportamiento entre apps en el mismo dispositivo
SELECT extra->>'app_package' AS app,
       level,
       COUNT(*) AS total
FROM app_logs
WHERE extra->>'device_fingerprint' = 'a1b2c3...'
GROUP BY app, level;
```

Nota: aunque el fingerprint ya incluye el package_name en su hash (son diferentes por app),
el campo `app_package` permite queries legibles sin necesidad de conocer el hash.

## Beta tester (monitoreo de testers con correo opcional)

Para apps en **Play Store beta/internal testing track**, el SDK permite marcar al usuario
como beta tester con su correo para identificación. Esto es **opcional** y requiere
consentimiento explícito del tester.

### Concepto

- `APPLOGGER_BETA_TESTER=true` → flag de build que activa el modo beta.
- El **desarrollador** captura el correo desde su propia lógica (login, Google Sign-In, formulario, etc.).
- El SDK solo recibe el correo ya capturado vía `setBetaTester(email)`.
- Si hay 100 testers, cada uno tiene su correo capturado en runtime — no se configuran 100 variables.

### Configuración en local.properties

```properties
# Solo en builds beta — NO incluir en producción
APPLOGGER_BETA_TESTER=true
```

### Mapping en build.gradle

```kotlin
android {
    defaultConfig {
        val isBeta = appLoggerLocalProps
            .getProperty("APPLOGGER_BETA_TESTER", "false").toBoolean()
        buildConfigField("boolean", "IS_BETA_TESTER", isBeta.toString())
    }
}
```

### Uso en código — el desarrollador captura el correo

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Inicializar AppLoggerSDK normalmente
        AppLoggerSDK.initialize(this, config, transport)

        // 2. Si es build beta, capturar el correo del tester
        //    El correo viene de TU lógica — no del SDK
        if (BuildConfig.IS_BETA_TESTER) {
            captureBetaTesterEmail()
        }
    }

    private fun captureBetaTesterEmail() {
        // Opción A: Google Sign-In (ya autenticado)
        val account = GoogleSignIn.getLastSignedInAccount(this)
        account?.email?.let { AppLoggerSDK.setBetaTester(it) }

        // Opción B: Firebase Auth (si ya tiene sesión)
        // val user = FirebaseAuth.getInstance().currentUser
        // user?.email?.let { AppLoggerSDK.setBetaTester(it) }

        // Opción C: SharedPreferences (si lo guardaste en un onboarding previo)
        // val email = getSharedPreferences("app", MODE_PRIVATE)
        //     .getString("tester_email", null)
        // email?.let { AppLoggerSDK.setBetaTester(it) }

        // Opción D: Login propio del app
        // Cuando el usuario hace login en tu pantalla:
        // AppLoggerSDK.setBetaTester(loginResponse.email)
    }
}
```

### Qué se inyecta en cada evento

Cuando `setBetaTester()` está activo, cada evento lleva en `extra`:

```json
{
  "is_beta_tester": "true",
  "beta_tester_email": "tester@example.com",
  "device_fingerprint": "a1b2c3...",
  "app_package": "com.miempresa.app"
}
```

### Queries en Supabase

```sql
-- Todos los errores de beta testers
SELECT * FROM app_logs
WHERE extra->>'is_beta_tester' = 'true'
  AND level IN ('ERROR', 'CRITICAL')
ORDER BY created_at DESC;

-- Errores de un tester específico
SELECT * FROM app_logs
WHERE extra->>'beta_tester_email' = 'tester@example.com'
ORDER BY created_at DESC;

-- Resumen por tester: cuántos errores reportó cada uno
SELECT extra->>'beta_tester_email' AS tester,
       level,
       COUNT(*) AS total
FROM app_logs
WHERE extra->>'is_beta_tester' = 'true'
GROUP BY tester, level
ORDER BY total DESC;

-- Testers que más crashean — priorizar debugging
SELECT extra->>'beta_tester_email' AS tester,
       extra->>'device_fingerprint' AS device,
       extra->>'app_package' AS app,
       COUNT(*) AS crashes
FROM app_logs
WHERE extra->>'is_beta_tester' = 'true'
  AND level = 'CRITICAL'
GROUP BY tester, device, app
ORDER BY crashes DESC;
```

### Limpiar datos de tester

```kotlin
// En código — cuando el tester sale del programa beta
AppLoggerSDK.clearBetaTester()
```

```bash
# Borrar todos los datos de un tester (GDPR Art. 17)
apploggers erase --user-id "tester@example.com"
```

### Base legal

| Aspecto | Fundamento |
|---|---|
| **Consentimiento** | GDPR Art. 6.1.a — el tester acepta participar en el programa beta |
| **Contrato** | GDPR Art. 6.1.b — el testing es parte del acuerdo de beta testing |
| **Derecho a borrado** | GDPR Art. 17 — `apploggers erase` elimina todos los datos |
| **Minimización** | Solo se captura el correo, no otros datos personales |

### Dos apps en el mismo dispositivo (caso frontend + backend)

Si tienes dos apps en el mismo dispositivo:
- **App A** (frontend con auth): conoce el correo → `setBetaTester(email)`
- **App B** (backend/servicio): no conoce el correo → no llama `setBetaTester`

Ambas comparten el **mismo `ANDROID_ID`** pero tienen **fingerprints diferentes** porque
el hash incluye el package_name. Sin embargo el `device_id` (sin sobreescribir) es igual.

Para correlacionar eventos de ambas apps en el mismo dispositivo:

```sql
-- Usar device_id (compartido entre apps del mismo dispositivo)
SELECT extra->>'app_package' AS app,
       level,
       message,
       created_at
FROM app_logs
WHERE device_id = 'DEVICE_ID_AQUI'
ORDER BY created_at DESC;
```

Para vincular ambas apps al mismo tester, configura el mismo `user_id` en ambas:

```kotlin
// En ambas apps — el ID compartido que tú defines
AppLoggerSDK.setAnonymousUserId("shared-tester-id-123")
```

## Minimal verification

```kotlin
AppLoggerSDK.info("BOOT", "AppLogger initialized")

val health = AppLoggerHealth.snapshot()
println("initialized=${health.isInitialized}, transport=${health.transportAvailable}, buffered=${health.bufferedEvents}")

// Verificar fingerprint
println("device_fingerprint=${AppLoggerSDK.getDeviceFingerprint()}")
```

## OperationTrace — Spans de performance

Mide el tiempo de cualquier operación y emite una métrica `trace.<name>` automáticamente:

```kotlin
import com.applogger.core.AppLoggerSDK
import com.applogger.core.OperationTrace

// Abrir span
val trace = AppLoggerSDK.startTrace("api_call", "endpoint" to url)

try {
    val response = api.fetch(url)
    trace.end(mapOf("status_code" to response.status))
    // → emite métrica trace.api_call con duration_ms
} catch (e: Exception) {
    trace.endWithError(e, failureReason = "network_error")
    // → emite evento ERROR Trace.api_call
}
```

Fluent API:
```kotlin
AppLoggerSDK.startTrace("video_load", "content_id" to id)
    .tag("quality", "4K")
    .bytes(responseBytes.toLong())
    .withTimeout(10_000)
    .end()
```

Ver skill `applogger-advanced-features` para el API completa.

## DataBudgetManager — Límite diario de datos

Para apps sensibles al ancho de banda, configura un límite diario de bytes:

```kotlin
AppLoggerSDK.initialize(
    context = this,
    config = AppLoggerConfig.Builder()
        .endpoint(BuildConfig.LOGGER_URL)
        .apiKey(BuildConfig.LOGGER_KEY)
        .environment("production")
        .dailyDataLimitMb(50)   // 50 MB/día móvil, 100 MB/día WiFi (2× automático)
        // 0 (default) = sin límite
        .build(),
    transport = transport
)
```

Cuando se alcanza el límite, los eventos no críticos se descartan hasta el siguiente día UTC.
ERROR y CRITICAL nunca se descartan.

## Guardrails

1. Do not log PII (names, emails, phone numbers, device IMEI).
2. Do not log tokens or API keys.
3. Keep `debugMode=false` in production builds.
4. Always set `environment` to distinguish production from staging data.
5. Call `validate()` during development to catch config issues early.
6. The device fingerprint is pseudonymized (SHA-256) — it is not raw PII.
7. Remote config polling does not affect app performance (background timer, best-effort).
8. Always call `trace.end()` or `trace.endWithError()` — unclosed spans do not emit any event.
9. `dailyDataLimitMb = 0` disables the budget (default). Set a positive value only when bandwidth cost matters.
