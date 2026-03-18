# AppLogger — Privacidad por Diseño y Cumplimiento Normativo

**Versión:** 0.1.1  
**Fecha:** 2026-03-17  
**Normativas:** GDPR (EU 2016/679) · LGPD (Brasil 13.709/2018) · CCPA (California)  
**Scope:** Estándar de privacidad para datos capturados por el SDK AppLogger

---

## Índice

1. [Marco Regulatorio Aplicable](#1-marco-regulatorio-aplicable)
2. [Clasificación de Datos en AppLogger](#2-clasificación-de-datos-en-applogger)
3. [Base Legal para el Tratamiento](#3-base-legal-para-el-tratamiento)
4. [Principios de Privacy by Design Implementados](#4-principios-de-privacy-by-design-implementados)
5. [Datos Prohibidos — Lo que AppLogger NUNCA Captura](#5-datos-prohibidos--lo-que-applogger-nunca-captura)
6. [Session ID — Seudoanonimización](#6-session-id--seudoanonimización)
7. [Consentimiento para User ID Opcional](#7-consentimiento-para-user-id-opcional)
8. [Seguridad en la Transmisión](#8-seguridad-en-la-transmisión)
9. [Derecho al Olvido y Retención de Datos](#9-derecho-al-olvido-y-retención-de-datos)
10. [Checklist de Cumplimiento para el Desarrollador](#10-checklist-de-cumplimiento-para-el-desarrollador)

---

## 1. Marco Regulatorio Aplicable

### 1.1 GDPR — Reglamento General de Protección de Datos (EU)

Aplicable a cualquier app que tenga usuarios en la Unión Europea, independientemente de dónde esté la empresa. Los puntos más relevantes para telemetría técnica:

- **Art. 4** — Definición de dato personal: cualquier información que permita identificar **directa o indirectamente** a una persona física.
- **Art. 5** — Principios: minimización, exactitud, limitación de almacenamiento, integridad.
- **Art. 6** — Legitimación del tratamiento: necesita base legal (interés legítimo para telemetría técnica).
- **Art. 25** — Protección de datos desde el diseño y por defecto (*Privacy by Design*).

### 1.2 LGPD — Lei Geral de Proteção de Dados (Brasil)

Aplicable a apps que operan en o para usuarios en Brasil. Paralela al GDPR en la mayoría de los conceptos:

- Los datos técnicos anónimos (sin correlación posible con un individuo) están fuera del alcance de la LGPD.
- La LGPD exige que el tratamiento de datos tenga **finalidad específica y legítima**, declarada al usuario.

### 1.3 CCPA — California Consumer Privacy Act (Estados Unidos)

Para apps con usuarios en California. La telemetría técnica sin PII generalmente queda fuera del alcance si:
- No identifica ni puede identificar razonablemente al consumidor.
- No se vende ni comparte con terceros para publicidad.

---

## 2. Clasificación de Datos en AppLogger

### 2.1 Tabla de clasificación completa

| Campo | ¿Es PII? | Categoría | Justificación |
|---|---|---|---|
| `device_info.brand` | No | Técnico | Marca del fabricante, no identifica a la persona |
| `device_info.model` | No | Técnico | Modelo de dispositivo, no a la persona |
| `device_info.os_version` | No | Técnico | Versión del sistema operativo |
| `device_info.api_level` | No | Técnico | Nivel de API de Android |
| `device_info.platform` | No | Técnico | `android_mobile`, `android_tv`, `jvm` |
| `device_info.app_version` | No | Técnico | Versión instalada de la app |
| `device_info.connection_type` | No | Técnico | `wifi`, `cellular`, `ethernet` — no ubicación |
| `device_info.is_tv` | No | Técnico | Clasificación de tipo de dispositivo |
| `device_info.is_low_ram` | No | Técnico | Característica de hardware |
| `session_id` | Pseudónimo | Técnico | UUID efímero por sesión. No persistente. No correlacionable sin datos adicionales fuera del scope de la librería |
| `tag` | No | Técnico | Origen del log (clase o módulo, no usuario) |
| `message` | Depende | Variable | **El desarrollador es responsable** de no incluir PII en mensajes |
| `throwable_type` | No | Técnico | Tipo de excepción Java/Kotlin |
| `throwable_msg` | Depende | Variable | **El desarrollador es responsable** de que los mensajes de excepción no contengan PII |
| `stack_trace` | No | Técnico | Trazas de código, no de personas |
| `extra` | Depende | Variable | **El desarrollador es responsable** del contenido de campos extra |
| `user_id` | Pseudónimo | **Opcional** | UUID anónimo. NULL por defecto. Solo con consentimiento explícito |
| IP de origen | **Sí** | **PROHIBIDO** | No se captura ni almacena en ningún campo |
| Nombre del usuario | **Sí** | **PROHIBIDO** | Nunca debe aparecer en mensajes o tags |
| Email | **Sí** | **PROHIBIDO** | Nunca debe aparecer en ningún campo |
| GPS / Ubicación | **Sí** | **PROHIBIDO** | Fuera del scope, no se solicita permiso |

### 2.2 Responsabilidad del Desarrollador Consumidor

`AppLogger` proporciona un marco seguro, pero el desarrollador que lo consume **es responsable** del contenido de los mensajes que loguea. Reglas que deben cumplirse en la app consumidora:

```kotlin
// ❌ MAL — incluye PII en el mensaje
AppLogger.error("AUTH", "User john.doe@example.com failed to login")

// ✅ BIEN — solo información técnica
AppLogger.error("AUTH", "Login failed: invalid credentials", extra = mapOf("error_code" to 401))

// ❌ MAL — incluye contenido del mensaje de WebSocket (puede tener datos del usuario)
AppLogger.debug("WS", "Received: $rawWebSocketMessage")

// ✅ BIEN — solo el evento técnico
AppLogger.debug("WS", "Message received", extra = mapOf("size_bytes" to message.toByteArray().size))
```

---

## 3. Base Legal para el Tratamiento

### 3.1 Interés Legítimo (Art. 6.1.f GDPR)

La telemetría técnica anónima puede justificarse bajo **interés legítimo** del desarrollador para:
- Detectar y corregir errores que afectan la experiencia del usuario.
- Garantizar la estabilidad y el rendimiento de la aplicación.
- Mejorar la compatibilidad con distintos modelos de dispositivos.

**Test de equilibrio**: el interés del desarrollador no sobrepasa los derechos del usuario porque:
1. Los datos son técnicos, no personales.
2. No se usan para perfilado ni publicidad.
3. Existe retención limitada (30 días por defecto).
4. No se comparten con terceros para ningún fin.

### 3.2 Disclosure en la Política de Privacidad de la App

Aunque la telemetría técnica puede operar bajo interés legítimo, es **buena práctica** mencionarla en la política de privacidad de la app consumidora:

```
Recopilación de datos técnicos:
La aplicación recopila automáticamente datos técnicos anónimos sobre el funcionamiento 
del software (registros de errores, versión del sistema operativo, modelo del dispositivo) 
con el fin de mejorar la estabilidad de la aplicación. Estos datos no incluyen información 
personal identificable y se eliminan automáticamente transcurridos 30 días.
```

---

## 4. Principios de Privacy by Design Implementados

Basado en el framework de **Ann Cavoukian** (7 principios de Privacy by Design):

| Principio | Implementación en AppLogger |
|---|---|
| **1. Proactivo, no reactivo** | El esquema de DB y los traits están diseñados sin PII desde el inicio |
| **2. Privacidad como configuración por defecto** | Sin configuración, `user_id` es `null`. El SDK nunca solicita permisos de ubicación o contactos |
| **3. Privacidad embebida en el diseño** | El trait `DeviceInfoProvider` tiene una lista cerrada de campos. No hay campo genérico `rawData` |
| **4. Funcionalidad completa — no zero-sum** | La privacidad no limita la utilidad diagnóstica del SDK |
| **5. Seguridad de extremo a extremo** | TLS en transmisión, RLS en base de datos, API key de solo inserción |
| **6. Visibilidad y transparencia** | El código es open source: cualquiera puede auditar qué se captura |
| **7. Respeto por la privacidad del usuario** | Minimización de datos, retención limitada, sin compartir con terceros |

---

## 5. Datos Prohibidos — Lo que AppLogger NUNCA Captura

El SDK tiene **prohibiciones técnicas en el código**, no solo en la documentación:

### 5.1 Sin acceso a permisos de sistema

El SDK no solicita, ni requiere como prerequisito, ninguno de estos permisos de Android:

```xml
<!-- NINGUNO de estos permisos debe aparecer en el Manifest del SDK -->
<!-- ACCESS_FINE_LOCATION       → Ubicación GPS -->
<!-- ACCESS_COARSE_LOCATION     → Ubicación red -->
<!-- READ_CONTACTS               → Contactos -->
<!-- READ_PHONE_STATE            → IMEI, número de teléfono -->
<!-- CAMERA                      → Cámara -->
<!-- RECORD_AUDIO                → Micrófono -->
```

### 5.2 Validación en el `DeviceInfoProvider`

```kotlin
// El DeviceInfoProvider de Android solo accede a fuentes no-PII
internal class AndroidDeviceInfoProvider(
    private val context: Context,
    private val appVersion: String,
    private val appBuild: Int
) : DeviceInfoProvider {

    override fun get(): DeviceInfo = DeviceInfo(
        brand           = Build.BRAND,              // No PII
        model           = Build.MODEL,              // No PII
        osVersion       = Build.VERSION.RELEASE,    // No PII
        apiLevel        = Build.VERSION.SDK_INT,    // No PII
        platform        = PlatformDetector.detect(context).name.lowercase(),
        appVersion      = appVersion,               // No PII
        appBuild        = appBuild,                 // No PII
        isLowRamDevice  = isLowRamDevice(context),  // No PII
        connectionType  = getConnectionType(context) // No PII — NUNCA GPS
    )

    // NOTA: getConnectionType() solo devuelve "wifi" | "cellular" | "ethernet" | "none"
    // Nunca devuelve el SSID de la red, ni la IP, ni datos de la celda
}
```

---

## 6. Session ID — Seudoanonimización

### 6.1 Generación y Ciclo de Vida

El `session_id` es un UUID v4 generado **en cada inicio de la app**:

```kotlin
internal class SessionManager {
    // Se genera en memoria al iniciar el SDK. No se persiste en SharedPreferences ni disco.
    val sessionId: String = UUID.randomUUID().toString()
}
```

- **No es persistente**: un nuevo `session_id` sin relación con el anterior se genera en cada lanzamiento.
- **No es correlacionable**: sin datos adicionales (que la librería no captura ni almacena), no es posible determinar si dos sesiones pertenecen al mismo dispositivo o persona.
- **No es el Android ID**: no se usa `Settings.Secure.ANDROID_ID` (que es persistente y vinculable al dispositivo).

### 6.2 Impacto en GDPR

Bajo el GDPR, un `session_id` generado así:
- **No es dato personal** según el Considerando 26: si la posibilidad de identificación requiere "medios razonablemente posibles", y esos medios no existen (no hay vinculación persistente), el dato es anónimo.
- **No requiere consentimiento** ni base legal adicional.

---

## 7. Consentimiento para User ID Opcional

Cuando la app desea correlacionar logs con un usuario (por ejemplo, para que el soporte diga "revisa los logs de la sesión del usuario X"), debe obtener su consentimiento de forma explícita:

### 7.1 Flujo recomendado

```kotlin
// 1. El usuario acepta la política de privacidad que menciona la telemetría
// 2. La app genera un UUID anónimo para ese usuario (NO usar el ID del sistema de auth)
// 3. Pasar ese UUID al SDK

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLoggerSDK.initialize(context = this, config = /* ... */)
    }
}

// Después de que el usuario acepta los términos:
fun onUserAcceptedPrivacyPolicy(userConsentGiven: Boolean) {
    if (userConsentGiven) {
        val anonymousUserId = generateAnonymousUserId() // UUID persistente, no el ID real
        AppLoggerSDK.setAnonymousUserId(anonymousUserId)
    }
}

// La función que genera el UUID anónimo del usuario (NUNCA usar email, nombre o ID del backend)
private fun generateAnonymousUserId(): String {
    val prefs = getSharedPreferences("logger_prefs", Context.MODE_PRIVATE)
    return prefs.getString("anon_user_id", null)
        ?: UUID.randomUUID().toString().also { newId ->
            prefs.edit().putString("anon_user_id", newId).apply()
        }
}
```

### 7.2 Qué NO hacer con el User ID

```kotlin
// ❌ MAL — usar el ID del sistema de autenticación (correlacionable con datos reales)
AppLoggerSDK.setAnonymousUserId(firebaseAuth.currentUser?.uid ?: "")

// ❌ MAL — usar el email hasheado (reversible, puede identificar al usuario)
AppLoggerSDK.setAnonymousUserId(email.hashCode().toString())

// ✅ BIEN — UUID opaco, sin relación con ningún sistema de identidad
AppLoggerSDK.setAnonymousUserId(UUID.randomUUID().toString())
```

---

## 8. Seguridad en la Transmisión

### 8.1 TLS Obligatorio

El `LogTransport` debe rechazar conexiones sin TLS:

```kotlin
internal class SupabaseTransport(
    private val endpoint: String,
    private val apiKey: String
) : LogTransport {

    init {
        // Validación en tiempo de inicialización: el endpoint debe usar HTTPS
        require(endpoint.startsWith("https://")) {
            "AppLogger: endpoint must use HTTPS. HTTP is not allowed for log transport."
        }
    }
}
```

### 8.2 API Key — Solo `anon key`

La API key embebida en la app **nunca debe ser la `service_role` key** de Supabase, ya que:
- La `service_role` key tiene acceso total a la base de datos (SELECT, UPDATE, DELETE sobre todos los datos).
- Si es extraída del APK (trivial con decompiladores), comprometería toda la base de datos.

La `anon key` con RLS configurado es segura porque:
- Solo tiene permiso de INSERT en `app_logs` (por la política de RLS).
- No puede leer, modificar ni borrar datos aunque sea extraída.

### 8.3 Validación en el Backend (Supabase Edge Function, opcional)

Para un nivel adicional de seguridad, se puede añadir una Edge Function que valide el origen antes de insertar:

```typescript
// supabase/functions/ingest-logs/index.ts
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

serve(async (req) => {
    const body = await req.json()

    // Validar que el cuerpo contiene los campos mínimos requeridos
    if (!body.level || !body.message || !body.session_id) {
        return new Response("Invalid payload", { status: 400 })
    }

    // Sanitizar: eliminar cualquier campo que no esté en la lista permitida
    const sanitized = {
        level:          body.level,
        tag:            body.tag?.substring(0, 100) ?? "",
        message:        body.message?.substring(0, 10000) ?? "",
        throwable_type: body.throwable_type?.substring(0, 200) ?? null,
        device_info:    body.device_info ?? {},
        session_id:     body.session_id,
        sdk_version:    body.sdk_version ?? "0.0.0"
        // IP: NO se incluye aunque req.headers tenga x-forwarded-for
    }

    // Insertar en la tabla
    // ...
})
```

---

## 9. Derecho al Olvido y Retención de Datos

### 9.1 Retención Automática

Como se documenta en [db-migration.md](db-migration.md), los logs se purgan automáticamente:
- `app_logs`: 30 días por defecto (configurable por el operador).
- `app_metrics`: 7 días por defecto.

### 9.2 Derecho al Olvido (GDPR Art. 17)

Dado que los logs no contienen PII directa y el `session_id` no es persistente, técnicamente no aplica el derecho al olvido a nivel individual.

Sin embargo, si un usuario solicita la eliminación de sus datos y la app optó por incluir un `user_id` anónimo:

```sql
-- Eliminar todos los logs asociados a un user_id anónimo específico
DELETE FROM app_logs WHERE user_id = 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx';
```

Esta operación solo es ejecutable por `service_role` (no desde el cliente Android), protegiendo contra borrados maliciosos.

---

## 10. Checklist de Cumplimiento para el Desarrollador

Antes de lanzar una app que integra `AppLogger`, verificar:

### 10.1 SDK — Configuración

- [ ] El endpoint de logs usa **HTTPS** (no HTTP).
- [ ] Se usa la `anon key` de Supabase, **no** la `service_role key`.
- [ ] La API key **no está hardcodeada** en el código fuente (viene de `BuildConfig` mapeado desde `local.properties` o variables de entorno CI/CD).
- [ ] `user_id` está en `null` por defecto. Solo se proporciona si el usuario dio consentimiento explícito.

### 10.2 Mensajes de Log — Contenido

- [ ] Ningún mensaje de log contiene: emails, nombres, números de teléfono, contraseñas, tokens de autenticación, direcciones, DNI/CPF/NIE.
- [ ] Los payloads de gRPC/WebSocket **no se loguean** como mensajes de texto.
- [ ] Los campos `extra` no contienen datos de negocio que revelen información personal.

### 10.3 Política de Privacidad de la App

- [ ] La política de privacidad de la app menciona la recopilación de datos técnicos anónimos.
- [ ] Se especifica el período de retención (ej: 30 días).
- [ ] Se menciona que los datos no se comparten con terceros para publicidad o perfilado.

### 10.4 Base de Datos

- [ ] RLS habilitado en las tablas `app_logs` y `app_metrics`.
- [ ] El rol `anon` solo tiene permisos de INSERT.
- [ ] La purga automática está configurada (pg_cron o equivalente).
- [ ] No existen campos para IP, nombre, email o número de teléfono en el esquema.
