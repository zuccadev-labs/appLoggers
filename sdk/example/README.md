# Example — Uso del SDK AppLogger

Ejemplo práctico para integrar AppLogger en apps Android y mapear eventos que luego consume tu backend.

## Estructura

```text
example/
├── build.gradle.kts
└── src/main/kotlin/com/example/example/
    ├── ExampleApplication.kt
    ├── ExampleUseCases.kt
    └── ExampleViewModel.kt
```

## Configuración local

Define estas variables en `sdk/local.properties` para compilar y ejecutar el ejemplo:

```properties
APPLOGGER_URL=https://tu-proyecto.supabase.co
APPLOGGER_ANON_KEY=eyJhbGci...
APPLOGGER_DEBUG=true
APPLOGGERS_INTEGRITY_SECRET=secreto-largo-y-aleatorio
APPLOGGERS_INTEGRITY_SECRET_ID=10042026
```

`APPLOGGERS_INTEGRITY_SECRET` y `APPLOGGERS_INTEGRITY_SECRET_ID` se inyectan en `BuildConfig` y se aplican en `ExampleApplication.buildConfig()`.

Nota de seguridad: `local.properties` ya esta ignorado por git para evitar subir secretos.

## Casos de uso por tipo de componente

Esta seccion no depende de un proyecto especifico. Define que registrar, para que sirve y en que parte hacerlo.

### App cliente (Android/iOS)

Para que:
- Detectar degradacion de UX y fallos de negocio en tiempo real.

Donde:
- En `Application` para inicializacion global.
- En ViewModels/use-cases para eventos funcionales.
- En reproductor, checkout, login y navegacion.

Como (logs):
1. `debug` para trazas de desarrollo (estado de buffer, transiciones de pantalla).
2. `info` para flujo normal (contenido cargado, accion completada).
3. `warn` para anomalias recuperables (latencia alta, retry, degradacion de calidad).
4. `error` y `critical` para fallos de compra, auth o dependencias externas.

Como (metricas):
1. `screen_load_time` por pantalla.
2. `api_response_time` por endpoint.
3. `playback_start_ms`, `rebuffer_count`, `rebuffer_duration_ms` para streaming.

### Capa WebView

Para que:
- Correlacionar errores de contenido embebido con la app nativa.

Donde:
- En puente JS-Native y callbacks de navegacion.

Como (logs y metricas):
1. Loggear `webview_navigation_error`, `js_bridge_timeout`, `http_status`.
2. Medir `webview_dom_ready_ms` y `webview_first_interaction_ms`.
3. Incluir `screen`, `webview_url_host`, `session_id` en `extra/tags`.

### Servicio de autenticacion

Para que:
- Auditar trazabilidad de login sin exponer secretos.

Donde:
- En inicio de login, refresh token, validacion de sesion, logout.

Como (logs y metricas):
1. `info` en login correcto y refresh exitoso.
2. `warn` en refresh cercano a expirar o clock drift detectado.
3. `error/critical` en token invalido, proveedor caido o bloqueo total.
4. Metricas: `auth_latency_ms`, `token_refresh_success_rate`, `auth_fail_rate`.

### Backend gRPC

Para que:
- Observar salud de contratos RPC y latencia por metodo.

Donde:
- Interceptores gRPC (server y client), handlers de negocio.

Como (logs y metricas):
1. Loggear inicio/fin de llamada con `rpc.service`, `rpc.method`, `status_code`.
2. Registrar errores de serializacion, timeouts, cancelaciones y backpressure.
3. Medir `grpc_latency_ms`, `grpc_error_rate`, `grpc_payload_bytes`.
4. Etiquetar por `environment`, `region`, `server_type`.

### Tipos de servidor (API, worker, scheduler, edge)

Para que:
- Estandarizar observabilidad entre servicios heterogeneos.

Donde:
- Middleware global, colas de trabajo, jobs programados, funciones edge.

Como (logs y metricas):
1. API server: `request_id`, `route`, `status`, `duration_ms`.
2. Worker: `job_id`, `queue`, `attempt`, `retry_count`, `dead_letter`.
3. Scheduler: `task_name`, `window`, `next_run`, `drift_ms`.
4. Edge: `cold_start`, `upstream_latency_ms`, `rate_limit_hits`.

Snippet base:

```kotlin
val logger = AppLogger(ExampleApplication.buildConfig(), ExampleApplication.buildTransport())
val useCases = ExampleUseCases(logger)
useCases.exampleInfo()
useCases.exampleWarn()
```

Recomendacion de contrato:
- Definir taxonomia comun de tags (`domain`, `feature`, `server_type`, `platform`, `session_id`).
- Mantener nombres de metricas estables para dashboards y alertas.
- Nunca registrar PII, tokens, headers sensibles ni payloads completos.

## Validacion local

Este modulo participa en CI del SDK. Para validarlo localmente:

```bash
./gradlew :example:testDebugUnitTest :example:testReleaseUnitTest
```
