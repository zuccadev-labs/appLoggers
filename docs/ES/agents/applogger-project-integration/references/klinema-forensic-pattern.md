# Klinema Forensic Pattern

Este patrón resume una integración real de AppLogger en una app Android TV/headless con servicios foreground, gRPC, WebSocket y autenticación.

## Patrón comprobado

1. Inicializador dedicado llamado desde `Application.onCreate()`.
2. `SupabaseTransport` explícito con `androidNetworkAvailabilityProvider(context)`.
3. `AppLoggerConfig.Builder()` con `environment`, `debugMode`, `consoleOutput`, `minLevel`, `batchSize`, `flushIntervalSeconds`, y `remoteConfigEnabled`.
4. `config.validate()` en builds de desarrollo antes de inicializar.
5. Identidad inicial seudónima desde `getDeviceFingerprint()`.
6. `app_package` inyectado como `global extra`.
7. `Thread.setDefaultUncaughtExceptionHandler(...)` para capturar fallos fatales y luego encadenar al handler previo.
8. Tag taxonomy fija por dominios: `BOOT`, `AUTH`, `API`, `GRPC`, `DISCOVERY`, `FILTER`, `SYSTEM`, `CRASH`.
9. Métricas operativas con dimensiones estables como `reason`, `status`, `platform`, `action`.
10. `AppLoggerHealth.snapshot()` al iniciar y antes de apagar, seguido de `AppLoggerSDK.flush()`.

## Qué tomar como estándar

1. Centralizar bootstrap e identidad.
2. Tratar los tags como contrato operativo, no como strings libres.
3. Combinar logs y métricas para eventos relevantes.
4. Inicializar antes de arrancar servicios o servidores embebidos.
5. Validar health en startup y shutdown.

## Qué mejorar si aparece en un consumidor

1. No imprimir user IDs, emails o tokens en el mensaje aunque el SDK ya los correlacione por otro canal.
2. No mezclar mensajes decorativos o con emojis si luego el equipo operativo depende de búsquedas exactas o alertas.
3. No duplicar endpoint/key en `BuildConfig` y archivos assets salvo necesidad explícita.
4. No dejar versiones viejas de `logger-core`, `logger-transport-supabase` y `logger-test` desalineadas.