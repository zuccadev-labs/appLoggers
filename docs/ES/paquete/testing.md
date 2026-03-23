# AppLogger — Guía de Testing

**Frameworks:** JUnit 5 · Kotlin Coroutines Test · MockK · Robolectric

---

## Índice

1. [Filosofía de Testing](#1-filosofía-de-testing)
2. [Utilitarios de Test del SDK](#2-utilitarios-de-test-del-sdk)
3. [Tests Unitarios del Core](#3-tests-unitarios-del-core)
4. [Tests de Integración — Transporte](#4-tests-de-integración--transporte)
5. [Tests en la App Consumidora](#5-tests-en-la-app-consumidora)
6. [Configuración de Gradle](#6-configuración-de-gradle)

---

## 1. Filosofía de Testing

El diseño por traits hace que cada componente sea **testeable de forma aislada**. No se necesita un dispositivo real ni red para testear el 90% del SDK.

### 1.1 Pirámide de Tests

```
           /──────────────\
          /  E2E (manual)  \        ← Dispositivo real + Supabase staging
         /──────────────────\
        /   Integration       \     ← FakeTransport + Robolectric
       /────────────────────────\
      /      Unit Tests          \  ← JUnit 5 + MockK + runTest
     /────────────────────────────\
```

### 1.2 Regla de Oro del Testing de AppLogger

El SDK **nunca debe crashear ni lanzar excepciones** al código de la app. Cada test debe verificar que bajo condiciones adversas (red caída, buffer lleno, crash en el transport), el SDK absorbe el fallo silenciosamente.

---

## 2. Utilitarios de Test del SDK

El módulo `logger-test` provee implementaciones de prueba listas para usar. Agregar en `build.gradle.kts`:

```kotlin
testImplementation("com.github.zuccadev-labs.appLoggers:logger-test:<latest-version>")
```

### 2.1 `NoOpTestLogger` — Para tests que no necesitan verificaciones

```kotlin
import com.applogger.test.NoOpTestLogger

// Usar cuando el logger es un parámetro requerido pero no es el foco del test
val logger: AppLogger = NoOpTestLogger()
```

> Nota: `NoOpTestLogger` es el alias público del módulo `logger-test`. No usar `NoOpLogger` directamente — es `internal` al SDK.

### 2.2 `InMemoryLogger` — Para verificar qué se logueó

`InMemoryLogger` implementa `AppLogger` y almacena todos los eventos en memoria. Soporta `addGlobalExtra()` — los pares globales se mezclan en cada evento almacenado.

```kotlin
import com.applogger.test.InMemoryLogger

val logger = InMemoryLogger()

// Ejecutar el código bajo test
myComponent.performOperation()

// Verificar los logs emitidos
assertEquals(1, logger.errorCount)
assertTrue(logger.lastError?.message?.contains("expected error text") == true)
logger.assertLogged(LogLevel.ERROR, tag = "PAYMENT")
logger.assertNotLogged(LogLevel.DEBUG)

// Verificar global extra
logger.addGlobalExtra("ab_test", "checkout_v2")
myComponent.doSomething()
// Todos los eventos posteriores tendrán extra["ab_test"] = "checkout_v2"
```

### 2.3 `FakeTransport` — Para verificar envíos al backend

```kotlin
import com.applogger.test.FakeTransport

val fakeTransport = FakeTransport(shouldSucceed = true)

// Simular fallo de red retryable:
val failingTransport = FakeTransport(shouldSucceed = false, retryable = true)

// Simular respuesta 429 con Retry-After:
val rateLimitedTransport = FakeTransport(
    shouldSucceed = false,
    retryable = true,
    retryAfterMs = 5_000L   // el BatchProcessor respetará este delay
)

// Verificar que se enviaron N eventos:
assertEquals(3, fakeTransport.sentEvents.size)
assertEquals(LogLevel.ERROR, fakeTransport.sentEvents.first().level)
```

### 2.4 `FakeHealthProvider` — Para tests de health

Para testear componentes que dependen del estado de salud del SDK, implementar `AppLoggerHealthProvider`:

```kotlin
import com.applogger.core.AppLoggerHealthProvider
import com.applogger.core.HealthStatus

class FakeHealthProvider(private val status: HealthStatus) : AppLoggerHealthProvider {
    override fun snapshot() = status
}

// En tests:
val provider = FakeHealthProvider(
    HealthStatus(
        isInitialized = true,
        transportAvailable = false,
        bufferedEvents = 42,
        deadLetterCount = 0,
        consecutiveFailures = 3,
        lastSuccessfulFlushTimestamp = System.currentTimeMillis() - 600_000L
    )
)
val viewModel = MyViewModel(healthProvider = provider)
```

---

## 3. Tests Unitarios del Core

### 3.1 Test del `RateLimitFilter`

```kotlin
@Test
fun `rate limit filter blocks events exceeding threshold`() {
    val filter = RateLimitFilter(maxEventsPerMinutePerTag = 3)
    val tag = "TEST_TAG"

    repeat(3) {
        assertTrue(filter.passes(buildTestEvent(tag = tag, level = LogLevel.INFO)))
    }

    // El 4to evento es bloqueado
    assertFalse(filter.passes(buildTestEvent(tag = tag, level = LogLevel.INFO)))
}

@Test
fun `rate limit filter always passes ERROR and CRITICAL events`() {
    val filter = RateLimitFilter(maxEventsPerMinutePerTag = 1)
    val tag = "TEST_TAG"

    filter.passes(buildTestEvent(tag = tag, level = LogLevel.INFO))
    filter.passes(buildTestEvent(tag = tag, level = LogLevel.INFO))

    // ERROR siempre pasa, incluso después del rate limit
    assertTrue(filter.passes(buildTestEvent(tag = tag, level = LogLevel.ERROR)))
}
```

### 3.2 Test del Pipeline Completo (con FakeTransport)

```kotlin
@Test
fun `error event triggers immediate flush`() = runTest {
    val fakeTransport = FakeTransport(shouldSucceed = true)
    val logger = buildTestLogger(
        transport = fakeTransport,
        batchSize = 20,
        flushIntervalSeconds = 300
    )

    logger.error("TAG", "Something failed")
    advanceUntilIdle()

    assertEquals(1, fakeTransport.sentEvents.size)
    assertEquals(LogLevel.ERROR, fakeTransport.sentEvents.first().level)
}

@Test
fun `debug events are not sent in production mode`() = runTest {
    val fakeTransport = FakeTransport(shouldSucceed = true)
    val logger = buildTestLogger(transport = fakeTransport, isDebugMode = false)

    logger.debug("TAG", "Debug message")
    advanceUntilIdle()

    assertTrue(fakeTransport.sentEvents.isEmpty())
}
```

### 3.3 Test de Resiliencia — Transport Failure

```kotlin
@Test
fun `failed transport does not crash the logger`() = runTest {
    val failingTransport = FakeTransport(shouldSucceed = false, throwException = true)
    val logger = buildTestLogger(transport = failingTransport)

    assertDoesNotThrow {
        logger.error("TAG", "Error message")
    }
    advanceUntilIdle()
    // La app sigue funcionando
}
```

### 3.4 Test de `retryAfterMs` — Respeto del Retry-After

```kotlin
@Test
fun `batch processor respects retryAfterMs from transport`() = runTest {
    val transport = FakeTransport(
        shouldSucceed = false,
        retryable = true,
        retryAfterMs = 5_000L
    )
    val logger = buildTestLogger(transport = transport)

    logger.error("TAG", "Error")
    advanceUntilIdle()

    // El BatchProcessor debe esperar al menos retryAfterMs antes del siguiente intento
    assertEquals(0, transport.sentEvents.size)  // aún no reintentó
}
```

### 3.5 Test de `AppLoggerConfig.validate()`

```kotlin
@Test
fun `validate detects blank endpoint`() {
    val config = AppLoggerConfig.Builder()
        .endpoint("")
        .apiKey("eyJvalid")
        .build()
    assertTrue(config.validate().any { it.contains("endpoint is blank") })
}

@Test
fun `validate detects HTTP endpoint in production`() {
    val config = AppLoggerConfig.Builder()
        .endpoint("http://insecure.example.com")
        .apiKey("eyJvalid")
        .debugMode(false)
        .build()
    assertTrue(config.validate().any { it.contains("HTTPS") })
}

@Test
fun `validate detects debug mode in production environment`() {
    val config = AppLoggerConfig.Builder()
        .endpoint("https://valid.supabase.co")
        .apiKey("eyJvalid")
        .debugMode(true)
        .environment("production")
        .build()
    assertTrue(config.validate().any { it.contains("isDebugMode=true") })
}

@Test
fun `validate returns empty list for valid config`() {
    val config = AppLoggerConfig.Builder()
        .endpoint("https://valid.supabase.co")
        .apiKey("eyJvalid")
        .environment("staging")
        .build()
    assertTrue(config.validate().isEmpty())
}
```

### 3.6 Test de `NoOpTestLogger` antes de `initialize()`

```kotlin
@Test
fun `calling logger before initialize does not crash`() {
    assertDoesNotThrow {
        AppLoggerSDK.error("TAG", "Early message")
        AppLoggerSDK.critical("TAG", "Critical before init", RuntimeException("test"))
        AppLoggerSDK.flush()
    }
}
```

### 3.7 Test de `InMemoryLogger.addGlobalExtra`

```kotlin
@Test
fun `global extra is merged into all subsequent events`() {
    val logger = InMemoryLogger()
    logger.addGlobalExtra("experiment", "group_b")

    logger.info("TAG", "Event after global extra")

    val event = logger.events.last()
    assertEquals("group_b", event.extra?.get("experiment")?.toString()?.trim('"'))
}

@Test
fun `per-call extra overrides global extra on key collision`() {
    val logger = InMemoryLogger()
    logger.addGlobalExtra("source", "global")

    logger.info("TAG", "Override", extra = mapOf("source" to "per_call"))

    val event = logger.events.last()
    assertEquals("per_call", event.extra?.get("source")?.toString()?.trim('"'))
}
```

---

## 4. Tests de Integración — Transporte

### 4.1 Test del `SupabaseTransport` (con servidor de staging)

```kotlin
@Test
@Tag("integration")
fun `supabase transport sends batch successfully`() = runTest {
    val transport = SupabaseTransport(
        endpoint = SUPABASE_STAGING_URL,
        apiKey   = SUPABASE_STAGING_ANON_KEY
    )

    val events = List(3) { buildTestEvent(level = LogLevel.INFO) }
    val result = transport.send(events)

    assertIs<TransportResult.Success>(result)
}

@Test
@Tag("integration")
fun `supabase transport fails gracefully with invalid key`() = runTest {
    val transport = SupabaseTransport(
        endpoint = SUPABASE_STAGING_URL,
        apiKey   = "invalid_key"
    )

    val events = listOf(buildTestEvent(level = LogLevel.ERROR))
    val result = transport.send(events)

    assertIs<TransportResult.Failure>(result)
    assertFalse((result as TransportResult.Failure).retryable) // 401 no es retryable
}
```

---

## 5. Tests en la App Consumidora

La app consumidora puede inyectar `InMemoryLogger` en sus ViewModels para testear sin dependencia del SDK de producción.

### 5.1 ViewModel con Logger inyectado

```kotlin
class PaymentViewModel(
    private val paymentRepository: PaymentRepository,
    private val logger: AppLogger = AppLoggerSDK
) : ViewModel() {

    fun processPayment(amount: Double) {
        viewModelScope.launch {
            try {
                paymentRepository.pay(amount)
                logger.info("PAYMENT", "Payment successful", extra = mapOf("amount" to amount))
            } catch (e: Exception) {
                logger.error("PAYMENT", "Payment failed", throwable = e)
            }
        }
    }
}

@Test
fun `payment failure is logged as error`() = runTest {
    val inMemoryLogger = InMemoryLogger()
    val failingRepo    = mockk<PaymentRepository> { coEvery { pay(any()) } throws IOException("timeout") }

    val viewModel = PaymentViewModel(
        paymentRepository = failingRepo,
        logger            = inMemoryLogger
    )

    viewModel.processPayment(99.0)
    advanceUntilIdle()

    assertEquals(1, inMemoryLogger.errorCount)
    inMemoryLogger.assertLogged(LogLevel.ERROR, tag = "PAYMENT")
}
```

### 5.2 Test de componente con `AppLoggerHealthProvider`

```kotlin
class OfflineBannerViewModel(
    private val healthProvider: AppLoggerHealthProvider = AppLoggerHealth
) : ViewModel() {
    fun shouldShowOfflineBanner(): Boolean = !healthProvider.snapshot().transportAvailable
}

@Test
fun `shows offline banner when transport unavailable`() {
    val provider = FakeHealthProvider(
        HealthStatus(isInitialized = true, transportAvailable = false,
                     bufferedEvents = 0, deadLetterCount = 0, consecutiveFailures = 0)
    )
    val vm = OfflineBannerViewModel(healthProvider = provider)
    assertTrue(vm.shouldShowOfflineBanner())
}
```

---

## 6. Configuración de Gradle

### 6.1 Dependencias de test

```kotlin
// logger-core/build.gradle.kts
commonTest.dependencies {
    implementation(libs.kotlin.test)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.turbine)
}

jvmTest.dependencies {
    implementation(libs.junit5.api)
    runtimeOnly(libs.junit5.engine)
    implementation(libs.mockk)
}
```

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
    excludeTags("integration")
}
```

### 6.2 Correr solo tests de integración (staging)

```bash
./gradlew test -Pintegration \
  -PSUPABASE_STAGING_URL="https://staging.supabase.co" \
  -PSUPABASE_STAGING_ANON_KEY="eyJ..."
```
