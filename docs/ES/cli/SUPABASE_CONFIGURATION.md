# AppLoggers CLI — Configuración Supabase y Hardening Operativo

## Objetivo

Esta guía define la configuración recomendada para operar AppLoggers CLI en entornos reales:

1. Configuración de Supabase (tablas, índices, RLS y retención).
2. Provisión de credenciales seguras para lectura del CLI.
3. Hardening operativo y prácticas de seguridad.

Principios de acceso:

- El SDK móvil usa `anon key` para INSERT.
- El CLI usa `service_role key` para SELECT.
- No exponer `service_role` en cliente móvil ni frontend.

---

## Arquitectura de acceso

- SDK app → inserta telemetría en `app_logs` y `app_metrics` con `anon key`.
- CLI ops → consulta telemetría con `service_role key`.
- RLS → `anon` inserta, `service_role` lee.

---

## Paso 1 — Aplicar migraciones en Supabase

Orden recomendado:

1. `001_create_app_logs.sql`
2. `002_create_app_metrics.sql`
3. `003_create_indexes.sql`
4. `004_rls_policies.sql`
5. `005_retention_policy.sql`
6. `006_harden_authenticated_read_policies.sql`
7. `007_add_environment_anomaly_type.sql` — agrega `environment` y `anomaly_type` top-level en `app_logs`
8. `008_add_metrics_environment.sql` — agrega `environment` top-level en `app_metrics`
9. `009_add_missing_indexes.sql` — índices adicionales de performance
10. `010_session_variant.sql` — agrega `variant` a `app_logs`
11. `011_batch_integrity.sql` — agrega `log_batches` y `batch_id` para verificabilidad
12. `012_enterprise_indexes_views.sql`
13. `013_device_remote_config.sql`
14. `014_beta_tester_correlation.sql`
15. `015_add_client_timestamp.sql` — agrega `timestamp` cliente para HMAC reproducible
16. `016_add_metrics_user_id.sql`
17. `017_apploggers_schema.sql` — expone el esquema `apploggers` para operaciones CLI/service_role
18. `018_atomic_log_batch_ingest.sql` — agrega ingestión atómica, FK de `batch_id` y corrige `app_logs` para no aceptar `METRIC`
19. `019_metric_batch_integrity_and_key_versioning.sql` — agrega `metric_batches` y `key_id` para rotación de secretos
20. `020_security_advisor_hardening.sql` — endurece views, helper functions y policies según advisors de Supabase
21. `021_app_identity_source_columns.sql` — promueve `app_package`, `source_scope`, `source_file` y `source_method` a columnas top-level

Checklist post-migración:

- Existe tabla `app_logs`.
- Existe tabla `app_metrics`.
- Existen índices de diagnóstico (incluido `idx_app_logs_tag`).
- RLS habilitado en ambas tablas.
- Policies `sdk_insert_*` y `monitor_read_*` activas.
- No existen policies `authenticated_read_*` globales.
- Existen relaciones `apploggers.app_logs`, `apploggers.app_metrics`, `apploggers.log_batches` y `apploggers.metric_batches`.
- `app_logs` y `app_metrics` exponen `app_package`, `source_scope`, `source_file` y `source_method` como columnas top-level.

---

## Paso 2 — Obtener credenciales y configurar `cli.json`

Desde Supabase Dashboard:

1. Project Settings → API.
2. Copiar:
   - **Project URL** (ej: `https://xxxx.supabase.co`)
   - **service_role key** (solo backend/ops — nunca exponer en cliente)

El CLI crea `~/.apploggers/cli.json` automáticamente en el primer run. Editar ese archivo con las credenciales obtenidas:

```json
{
  "default_project": "my-app",
  "projects": [
    {
      "name": "my-app",
      "display_name": "My Application",
      "workspace_roots": ["D:/workspace/my-app"],
      "supabase": {
        "url": "https://xxxx.supabase.co",
        "api_key": "eyJhbGci...",
        "schema": "apploggers"
      }
    }
  ]
}
```

Rutas del archivo según plataforma:

```
Windows : C:\Users\<usuario>\.apploggers\cli.json
Linux   : /home/<usuario>/.apploggers/cli.json
macOS   : /Users/<usuario>/.apploggers/cli.json
```

> No versionar este archivo. Contiene el `service_role key`.

### Alternativa: `api_key_env` para no almacenar el key en el archivo

Si se prefiere no tener el key en el archivo, usar `api_key_env` con el nombre de la variable de entorno UPPERCASE. La URL siempre va en el json — no existe variable de entorno para la URL en este path:

```json
"supabase": {
  "url": "https://xxxx.supabase.co",
  "schema": "apploggers",
  "api_key_env": "APPLOGGER_SUPABASE_KEY"
}
```

`schema` es opcional. El valor recomendado y preferido es `apploggers`. Usa `public` solo para instalaciones legacy que todavía no aplicaron la migración 017.

No se necesita una key nueva para usar `apploggers`.

- SDK: mantiene `anon key` para INSERT.
- CLI: mantiene `service_role key` para lecturas, verify, explain y auditoría.

El cambio de schema depende de migraciones, grants y exposición del schema, no de emitir un token distinto.

Solo el key se exporta como variable de entorno:

```bash
# Linux / macOS
export APPLOGGER_SUPABASE_KEY="eyJhbGci..."

# Windows PowerShell
$env:APPLOGGER_SUPABASE_KEY = "eyJhbGci..."
```

Si `api_key_env` está definido pero la variable no está exportada o está vacía, el CLI cae automáticamente a `api_key`. Ambos campos pueden coexistir.

---

## Paso 3 — Configuración multi-proyecto

Cuando el CLI opera varias aplicaciones de telemetría distintas, agregar múltiples entradas en `projects`. El CLI detecta automáticamente el proyecto activo según el directorio de trabajo (`workspace_roots`).

```json
{
  "default_project": "klinema",
  "projects": [
    {
      "name": "klinema",
      "display_name": "Klinema Mobile",
      "workspace_roots": ["D:/workspace/klinema"],
      "supabase": {
        "url": "https://klinema.supabase.co",
        "api_key": "eyJhbGci..."
      }
    },
    {
      "name": "klinematv",
      "display_name": "Klinema TV",
      "workspace_roots": ["D:/workspace/klinematv"],
      "supabase": {
        "url": "https://klinematv.supabase.co",
        "api_key": "eyJhbGci..."
      }
    }
  ]
}
```

Variables de control de proyecto (no reemplazan `cli.json`, lo complementan):

| Variable | Propósito |
|---|---|
| `APPLOGGER_CONFIG` | Ruta alternativa al archivo de proyectos |
| `APPLOGGER_PROJECT` | Selección explícita del proyecto activo |

Precedencia de selección de proyecto:

1. `--project` (flag)
2. `APPLOGGER_PROJECT` (variable de entorno)
3. Matching por `workspace_roots` contra el directorio actual
4. `default_project`
5. Único proyecto configurado

---

## Paso 4 — Verificación operativa

```bash
apploggers health --output json
apploggers telemetry query --source logs --limit 5 --output json
apploggers telemetry query --source metrics --name response_time_ms --limit 5 --output json
apploggers verify --from 2026-04-01T00:00:00Z --to 2026-04-10T23:59:59Z --output json
apploggers telemetry agent-response --source logs --aggregate severity --preview-limit 3 --output agent
```

Se considera OK cuando:

- `health` retorna `ok: true`.
- Query de logs retorna estado exitoso sin 403.
- Query de metrics por `--name` funciona.
- `verify` retorna batches `OK` cuando el SDK está emitiendo `batch_id` y `batch_hash`.

## Paso 4.1 — Verificabilidad (Audit Trail)

El flujo de integridad de AppLoggers tiene 3 piezas:

1. El SDK agrupa eventos y asigna un `batch_id` común.
2. Con `integritySecret` configurado, calcula un HMAC-SHA256 canónico del batch.
3. El transporte escribe el manifiesto en `log_batches`; luego el CLI recomputa el hash con `apploggers verify`.

Condición importante:

- Si `integritySecret` está vacío, el SDK no crea `BatchIntegrityManager`.
- En ese caso `batch_id` queda `NULL` en `app_logs` y `log_batches` permanece vacío.
- Eso no es corrupción de datos: es falta de activación de la capa de verificabilidad.

Recomendación operativa:

1. Generar un secreto dedicado para integridad.
2. Inyectarlo al SDK en runtime real con `AppLoggerConfig.Builder.integritySecret(...)`.
3. Asignarle un `key_id` estable con `AppLoggerConfig.Builder.integritySecretId(...)`.
4. Configurar el mismo secreto en el CLI mediante `APPLOGGERS_INTEGRITY_SECRET` y, para históricos, `APPLOGGERS_INTEGRITY_SECRET_<KEY_ID>`.

Variables recomendadas:

```properties
# app / Android sample / staging local
APPLOGGERS_INTEGRITY_SECRET=secreto-largo-y-aleatorio
APPLOGGERS_INTEGRITY_SECRET_ID=10042026

# secretos históricos opcionales para verificación multi-clave
APPLOGGERS_INTEGRITY_SECRET_10042026=secreto-largo-y-aleatorio

# alias legacy aceptado por el CLI
APPLOGGER_INTEGRITY_SECRET=secreto-largo-y-aleatorio
```

Dónde inyectarlo en la app:

1. `local.properties` o secreto del pipeline móvil.
2. `build.gradle.kts` del módulo app para exponerlo como `BuildConfig.LOGGER_INTEGRITY_SECRET`.
3. Exponer también `BuildConfig.LOGGER_INTEGRITY_SECRET_ID`.
4. `AppLoggerConfig.Builder.integritySecret(BuildConfig.LOGGER_INTEGRITY_SECRET).integritySecretId(BuildConfig.LOGGER_INTEGRITY_SECRET_ID)` al inicializar el SDK.

Ejemplo Android:

```kotlin
val config = AppLoggerConfig.Builder()
  .endpoint(BuildConfig.LOGGER_URL)
  .apiKey(BuildConfig.LOGGER_KEY)
  .integritySecret(BuildConfig.LOGGER_INTEGRITY_SECRET)
  .integritySecretId(BuildConfig.LOGGER_INTEGRITY_SECRET_ID)
  .environment(if (BuildConfig.LOGGER_DEBUG) "development" else "production")
  .build()
```

Rotación sin romper históricos:

1. Definir un `APPLOGGERS_INTEGRITY_SECRET_ID` estable para la clave activa, por ejemplo `10042026`.
2. Publicar la app con `integritySecret` + `integritySecretId`; el SDK persistirá `key_id` en `log_batches` y `metric_batches`.
3. En el CLI, mantener el secreto activo en `APPLOGGERS_INTEGRITY_SECRET` y registrar secretos históricos como `APPLOGGERS_INTEGRITY_SECRET_<KEY_ID>`.
4. En producción corporativa, almacenar todas las claves en el gestor de secretos del entorno móvil/backend de build, nunca hardcodeadas.

Validación en staging:

1. Desplegar la app con `APPLOGGERS_INTEGRITY_SECRET` configurado.
2. Generar tráfico real: al menos un lote de logs y un lote de métricas.
3. Confirmar que `app_logs.batch_id` y `app_metrics.batch_id` ya no quedan en `NULL` para los nuevos eventos.
4. Confirmar que `log_batches` y `metric_batches` empiezan a poblarse con `key_id`.
5. Ejecutar `apploggers verify --signal both --from <inicio> --to <fin> --output json` con el mismo secreto en el entorno del CLI.

Consultas útiles en staging:

```sql
select count(*) as total_logs,
     count(batch_id) as logs_with_batch_id
from public.app_logs
where created_at >= now() - interval '30 minutes';

select count(*) as total_manifests
from public.log_batches
where created_at >= now() - interval '30 minutes';

select count(*) as total_metric_manifests,
  count(*) filter (where key_id is not null) as metric_manifests_with_key
from public.metric_batches
where created_at >= now() - interval '30 minutes';
```

Interpretación:

1. `logs_with_batch_id > 0` y `total_manifests > 0`: integridad activa para logs.
2. `app_metrics.batch_id` poblado y `metric_batches` con filas: integridad activa para métricas.
3. Si hay `batch_id` pero `verify` devuelve `NO_SECRET`, falta el secreto correspondiente a ese `key_id` del lado del CLI.

Nota de alcance actual:

1. La verificabilidad fuerte implementada hoy cubre batches de logs técnicos.
2. Métricas se verifican por separado en `metric_batches`; no se mezclan con logs.
3. `apploggers verify --signal logs`, `--signal metrics` y `--signal both` permiten auditar cada canal sin mezclarlos.

Diagnóstico rápido:

- `batch_id` nulo en todos los logs + `log_batches` vacío: integridad no activada en el SDK desplegado.
- `app_metrics.batch_id` nulo + `metric_batches` vacío: integridad de métricas no activada en el SDK desplegado.
- `batch_id` presente pero `batch_hash` vacío: batching activo sin secreto compartido.
- `verify` con `NO_SECRET`: el CLI no recibió el secreto activo o histórico correspondiente al `key_id` persistido.

---

## Paso 5 — Seguridad recomendada

- No versionar `~/.apploggers/cli.json` — contiene el `service_role key`.
- Rotar el `service_role key` periódicamente.
- Limitar acceso al sistema de archivos donde vive el archivo.
- No usar `service_role` en aplicaciones cliente o frontend.
- Mantener RLS y migración 006 aplicadas en todos los entornos.
- En Linux, restringir permisos del archivo: `chmod 600 ~/.apploggers/cli.json`.

---

## Paso 6 — Troubleshooting

### Error 403 al consultar

Causas probables:

1. Se usó `anon/publishable key` en lugar de `service_role key`.
2. RLS/policies no aplicadas en el entorno.
3. Se configuró `schema=apploggers` sin aplicar la migración 017.
4. El schema `apploggers` no está expuesto al rol operativo o el entorno quedó a mitad de migración.

### Uso correcto de Supabase MCP para migraciones

Para DDL usa `mcp_supabase_apply_migration`, no `mcp_supabase_execute_sql`.

Flujo recomendado:

1. `mcp_supabase_list_migrations` para identificar el último paso aplicado.
2. Leer el siguiente archivo SQL en `docs/ES/migraciones/`.
3. `mcp_supabase_apply_migration` con nombre snake_case y el SQL completo del archivo.
4. Repetir en orden hasta llegar a 021.
5. `mcp_supabase_list_tables` y `mcp_supabase_get_advisors` para validar el estado final.

Reserva `mcp_supabase_execute_sql` para verificaciones puntuales, backfills DML controlados y queries diagnósticas.

Acciones:

1. Verificar que `api_key` en `cli.json` contiene el `service_role key`.
2. Validar migraciones 004 y 006 aplicadas.
3. Si se usa `schema`, validar `GRANT USAGE ON SCHEMA apploggers TO service_role;`.

### Error de tabla no encontrada

Causa probable: migraciones 001/002 no aplicadas o `schema=apploggers` sin la migración 017.

Acción: aplicar migraciones pendientes y volver a ejecutar query.

### `batch_id`, `trace_id` o `variant` aparecen NULL

Interpretación correcta:

1. `batch_id`:
  Requiere batching con integridad activada. Si toda la tabla está en `NULL`, el SDK desplegado no está enviando batches firmados o no tiene `integritySecret` configurado.
2. `trace_id` y `variant`:
  Son opt-in. Solo se llenan cuando la app instrumentada llama explícitamente a `setTraceId(...)` o `setSessionVariant(...)`.

No tratar estos `NULL` como incidente de DB sin primero verificar versión/configuración del SDK desplegado.

### `api_key_env` ignorado / key no resuelto

Verificar que:

1. `api_key_env` contiene el **nombre** de la variable (ej: `"APPLOGGER_SUPABASE_KEY"`), no el JWT.
2. La variable está exportada en el proceso que ejecuta el CLI.
3. Si la variable no está disponible, definir `api_key` como fallback.

### Timeouts

- Ajustar `timeout_seconds` en `cli.json` (1..120).
- Reducir rango temporal y límite en query.

---

## Matriz de responsabilidades

| Equipo | Responsabilidad |
|---|---|
| Mobile / SDK | Usa `anon key` para escritura |
| Ops / Plataforma | Gestiona `service_role key` en `cli.json`, opera el CLI para lectura y diagnóstico |
| Seguridad | Audita acceso al archivo de configuración y rotación de credenciales |

---

## Referencias

- `docs/ES/migraciones/004_rls_policies.sql`
- `docs/ES/migraciones/006_harden_authenticated_read_policies.sql`
- `docs/ES/cli/INSTALLATION.md`
- `docs/ES/cli/README.md`
