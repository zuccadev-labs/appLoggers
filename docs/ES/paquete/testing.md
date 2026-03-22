# AppLogger — Guía de Testing

**Versión:** 0.1.1-alpha.7  
**Fecha:** 2026-03-17  
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

El módulo `logger-test` provee implementaciones de prueba listas para usar:

### 2.1 `NoOpLogger` — Para Tests que no necesitan verificaciones

```kotlin
// Usar cuando el logger es un parámetro requerido pero no es el foco del test
val logger: AppLogger = NoOpLogger()
```

### 2.2 `InMemoryLogger` — Para verificar qué se logueó

```kotlin
// Uso en tests:
val logger = InMemoryLogger()

// Ejecutar el código bajo test
myComponent.performOperation()

// Verificar los logs emitidos
assertEquals(1, logger.errorCount)
assertTrue(logger.lastError?.message?.contains("expected error text") == true)
logger.assertLogged(LogLevel.ERROR, tag = "PAYMENT")
logger.assertNotLogged(LogLevel.DEBUG)  // En modo producción, debug no se loguea
```

### 2.3 `FakeTransport` — Para verificar envíos al backend

```kotlin
val fakeTransport = FakeTransport(shouldSucceed = true)

// Simular fallo de red:
val failingTransport = FakeTransport(shouldSucceed = false, retryable = true)

// Verificar que se enviaron N eventos:
assertEquals(3, fakeTransport.sentEvents.size)
assertEquals(LogLevel.ERROR, fakeTransport.sentEvents.first().level)
```

---

## 3. Tests Unitarios del Core

### 3.1 Test del `RateLimitFilter`

```kotlin
@Test
fun `rate limit filter blocks events exceeding threshold`() {
    val filter = RateLimitFilter(maxEventsPerMinutePerTag = 3)
    val tag = "TEST_TAG"

    // Los primeros 3 eventos del mismo tag pasan
    repeat(3) {
        val event = buildTestEvent(tag = tag, level = LogLevel.INFO)
        assertTrue(filter.passes(event))
    }

    // El 4to evento es bloqueado
    val blockedEvent = buildTestEvent(tag = tag, level = LogLevel.INFO)
    assertFalse(filter.passes(blockedEvent))
}

@Test
fun `rate limit filter always passes ERROR and CRITICAL events`() {
    val filter = RateLimitFilter(maxEventsPerMinutePerTag = 1)
    val tag = "TEST_TAG"

    // Agotar el rate limit con INFO
    filter.passes(buildTestEvent(tag = tag, level = LogLevel.INFO))
    filter.passes(buildTestEvent(tag = tag, level = LogLevel.INFO))

    // ERROR siempre pasa, incluso después del rate limit
    val errorEvent = buildTestEvent(tag = tag, level = LogLevel.ERROR)
    assertTrue(filter.passes(errorEvent))
}
```

### 3.2 Test del Pipeline Completo (con FakeTransport)

```kotlin
@Test
fun `error event triggers immediate flush`() = runTest {
    val fakeTransport = FakeTransport(shouldSucceed = true)
    val logger = buildTestLogger(
        transport = fakeTransport,
        batchSize = 20,              // Batch alto — normalmente no se llena
        flushIntervalSeconds = 300   // Intervalo largo — el flush periódico no dispara
    )

    // Un ERROR debe forzar flush inmediato sin esperar a que el batch se llene
    logger.error("TAG", "Something failed")

    advanceUntilIdle()  // Kotlin Coroutines Test: ejecutar todas las coroutines pendientes

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

    // No debe lanzar ninguna excepción
    assertDoesNotThrow {
        logger.error("TAG", "Error message")
    }
    advanceUntilIdle()
    // La app sigue funcionando
}
```

### 3.4 Test de `NoOpLogger` antes de `initialize()`

```kotlin
@Test
fun `calling logger before initialize does not crash`() {
    // AppLoggerSDK comienza como NoOpLogger — nunca lanza excepciones
    assertDoesNotThrow {
        AppLoggerSDK.error("TAG", "Early message")
        AppLoggerSDK.critical("TAG", "Critical before init", RuntimeException("test"))
        AppLoggerSDK.flush()
    }
}
```

---

## 4. Tests de Integración — Transporte

### 4.1 Test del `SupabaseTransport` (con servidor de staging)

```kotlin
@Test
@Tag("integration")  // Marcar para no correr en CI normal
fun `supabase transport sends batch successfully`() = runTest {
    val transport = SupabaseTransport(
        endpoint = SUPABASE_STAGING_URL,         // Variable de entorno en CI
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
    assertEquals(false, (result as TransportResult.Failure).retryable) // 401 no es retryable
}
```

---

## 5. Tests en la App Consumidora

La app consumidora puede inyectar `InMemoryLogger` en sus ViewModels para testear sin dependencia del SDK de producción.

### 5.1 ViewModel con Logger inyectado

```kotlin
// ViewModel de producción
class PaymentViewModel(
    private val paymentRepository: PaymentRepository,
    private val logger: AppLogger = AppLoggerSDK  // Default: SDK de producción
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

// Test del ViewModel
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

---

## 6. Configuración de Gradle

### 6.1 Dependencias de test

Las versiones se gestionan desde `gradle/libs.versions.toml`. Configuración en `logger-core/build.gradle.kts`:

```kotlin
// logger-core/build.gradle.kts — sourceSets
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
