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

## Resumen operativo sin confusiones

No existe una "clave RLS" separada en AppLoggers.

- RLS se define con migraciones SQL, grants y policies.
- El SDK sigue usando la `anon key` de Supabase para escribir.
- El CLI sigue usando la `service_role key` de Supabase para leer y auditar.
- La `integritySecret` es otra cosa: sirve para HMAC de batches, no para permisos RLS.

## Proceso completo paso a paso

1. Entrar a Supabase Dashboard y abrir el proyecto correcto.
2. Dentro del proyecto, abrir `Integrations > Data API > Settings`.
3. En esa pantalla revisar `Exposed schemas`.
4. Quitar `public` de `Exposed schemas` si el proyecto ya esta migrado a `apploggers`.
5. Quitar `public` de `Extra search path` para no dejarlo como ruta operativa por defecto.
6. Agregar `apploggers` a `Exposed schemas` y guardar.
7. No usar `Account > Access Tokens` para este flujo: esos tokens son de cuenta personal para management/API y no reemplazan `anon` ni `service_role` del proyecto.
8. Para obtener las credenciales del proyecto, usar el boton `Connect` del proyecto o entrar a la pantalla de `API Keys` del proyecto.
9. Copiar `Project URL` y `anon key` para el SDK movil.
10. Copiar `service_role key` solo para backend/CLI.
11. Abrir `SQL Editor` en Supabase Dashboard.
12. Ejecutar las migraciones de este repositorio en orden, desde `001_create_app_logs.sql` hasta `026_apploggers_custom_rls_logs_metrics.sql`.
13. Confirmar que las migraciones 023, 024 y 026 quedaron aplicadas, porque son las que dejan `apploggers` como schema fisico operativo, endurecen grants y fuerzan RLS custom.
14. Verificar por SQL que `apploggers.app_logs` y `apploggers.app_metrics` existen, tienen RLS activo y tienen las policies `sdk_insert_*` y `monitor_read_*`.
15. Configurar la app movil con `Project URL` + `anon key` + `integritySecret` si se quiere verificabilidad HMAC.
16. Configurar el CLI con `Project URL` + `service_role key` + `schema = apploggers`.
17. Ejecutar validaciones operativas: `health`, consultas de logs/metrics y `verify`.
18. Si alguna query devuelve 403 o `PGRST106`, revisar otra vez `Exposed schemas`, grants y policies antes de tocar keys.

---

## Paso 1 — Aplicar migraciones en Supabase

Recorrido exacto en Dashboard:

1. Entrar a `Supabase Dashboard`.
2. En la barra superior o en la lista de proyectos, hacer clic en el proyecto donde vive AppLoggers.
3. En el menu lateral izquierdo del proyecto, hacer clic en `Integrations`.
4. Dentro de `Integrations`, hacer clic en `Data API`.
5. Abrir la pestaña `Settings` de `Data API`.
6. Buscar el bloque `Exposed schemas`.
7. Abrir el selector de schemas expuestos.
8. Confirmar que `apploggers` este seleccionado.
9. Si `public` sigue seleccionado y el entorno ya quedo migrado a `apploggers`, quitar `public` de `Exposed schemas`.
10. Buscar el bloque `Extra search path`.
11. Si `public` aparece ahi, quitarlo para que no siga siendo el path operativo por defecto.
12. Dejar solo los schemas necesarios para el proyecto. Si `extensions` aparece, no tocarlo salvo que tengas una razon operativa valida.
13. Hacer clic en `Save`.
14. Ir al menu lateral del proyecto y abrir `SQL Editor`.
15. Crear una query nueva o abrir una pestaña de SQL vacia.
16. Ejecutar cada archivo SQL de este repositorio en el orden listado abajo.
17. Ejecutar primero las migraciones base y avanzar secuencialmente hasta `026_apploggers_custom_rls_logs_metrics.sql`.
18. No saltar `023`, `024` ni `026`, porque son las que dejan el schema `apploggers` operativo y endurecido.
19. Cuando termines, volver a `Integrations > Data API > Settings`.
20. Confirmar otra vez que `apploggers` sigue expuesto.
21. Confirmar que `public` ya no queda como schema operativo por defecto para AppLoggers.

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
17. `017_apploggers_schema.sql` — introduce el schema `apploggers` para operaciones CLI/service_role
18. `018_atomic_log_batch_ingest.sql` — agrega ingestión atómica, FK de `batch_id` y corrige `app_logs` para no aceptar `METRIC`
19. `019_metric_batch_integrity_and_key_versioning.sql` — agrega `metric_batches` y `key_id` para rotación de secretos
20. `020_security_advisor_hardening.sql` — endurece views, helper functions y policies según advisors de Supabase
21. `021_app_identity_source_columns.sql` — promueve `app_package`, `source_scope`, `source_file` y `source_method` a columnas top-level
22. `022_apploggers_cleanup_wrapper.sql` — normaliza cleanup/cron por `apploggers`
23. `023_apploggers_physical_schema.sql` — mueve tablas, funciones, triggers y vistas operativas a `apploggers`
24. `024_apploggers_hardening.sql` — endurece grants, `EXECUTE` expuesto y jobs de mantenimiento en `apploggers`
25. `025_drop_unused_indexes.sql` — elimina indices sin uso que no respaldan filtros ni jobs activos
26. `026_apploggers_custom_rls_logs_metrics.sql` — fuerza RLS en `app_logs` y `app_metrics`, y define policies custom de INSERT (`anon`) y SELECT (`service_role`)

Checklist post-migración:

- Existe tabla `app_logs`.
- Existe tabla `app_metrics`.
- Existen índices de diagnóstico (incluido `idx_app_logs_tag`).
- RLS habilitado en ambas tablas.
- Policies `sdk_insert_*` y `monitor_read_*` activas.
- No existen policies `authenticated_read_*` globales.
- Existen tablas base `apploggers.app_logs`, `apploggers.app_metrics`, `apploggers.log_batches` y `apploggers.metric_batches`.
- `app_logs` y `app_metrics` exponen `app_package`, `source_scope`, `source_file` y `source_method` como columnas top-level.
- No quedan tablas, funciones ni triggers operativos de AppLoggers en `public`.
- `anon` conserva solo los permisos operativos minimos: INSERT en logs/metrics/batches, SELECT en `device_remote_config`, SELECT/INSERT/UPDATE en `beta_tester_devices`.
- `authenticated` no conserva grants operativos sobre tablas ni `EXECUTE` sobre funciones de `apploggers`.
- Solo las RPC `ingest_log_batch` e `ingest_metric_batch` quedan expuestas a `anon`; `purge_old_logs` y `expire_beta_tester_mappings` quedan expuestas a `service_role`.
- Existen jobs `purge-old-logs-every-3-days` y `expire-beta-tester-mappings-weekly` apuntando a funciones de `apploggers`.
- `app_logs` y `app_metrics` tienen `FORCE ROW LEVEL SECURITY` habilitado.
- Policies activas en `apploggers.app_logs`: `sdk_insert_logs`, `monitor_read_logs`.
- Policies activas en `apploggers.app_metrics`: `sdk_insert_metrics`, `monitor_read_metrics`.

---

## Paso 2 — Obtener credenciales y configurar `cli.json`

Desde Supabase Dashboard:

1. No usar `Account > Access Tokens`: esos tokens son de la cuenta del usuario y no sustituyen las keys del proyecto para AppLoggers.
2. Si ya creaste un `Access Token` como en la pantalla de `Account > Access Tokens`, no cambia el proceso operativo descrito en esta guia.
3. Ese token sirve para autenticarte contra herramientas de cuenta, Management API o flujos del ecosistema Supabase que autentican al usuario operador.
4. Ese token no reemplaza la `anon key` o publishable key del proyecto para el SDK.
5. Ese token no reemplaza la `service_role key` del proyecto para el CLI de AppLoggers.
6. Ese token no reemplaza los grants ni las policies RLS del schema `apploggers`.
7. Para AppLoggers, el acceso operativo sigue dependiendo del proyecto y no de tu cuenta personal de Supabase.
8. Volver a la vista principal del proyecto.
9. Hacer clic en el boton `Connect` del proyecto.
10. En ese modal o panel, ubicar el `Project URL`.
11. Copiar el `Project URL`.
12. Ubicar la key publica del proyecto: `anon key` o publishable key equivalente.
13. Copiar esa key publica para usarla en el SDK movil.
14. Si el panel `Connect` no muestra claramente todas las keys necesarias, abrir la pantalla de `API Keys` del proyecto.
15. En `API Keys`, localizar la `service_role key` del proyecto.
16. Copiar la `service_role key`.
17. Confirmar otra vez que el proyecto expone `apploggers` dentro de `Integrations > Data API > Settings`.
18. En tu maquina, abrir o crear `~/.apploggers/cli.json`.
19. Cargar `url`, `api_key` y `schema = apploggers`.
20. Usar `Project URL` + `anon key` en la app.
21. Usar `Project URL` + `service_role key` en el CLI.

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

## Paso 2.1 — Crear y configurar la key de integridad (HMAC)

Primero, separa correctamente los conceptos:

- RLS no usa una key nueva.
- RLS queda resuelto por las migraciones, grants y policies sobre `apploggers`.
- `anon key` sigue siendo la key del SDK para INSERT.
- `service_role key` sigue siendo la key del CLI para lectura/operacion.
- La unica "key nueva" opcional en este flujo es la `integritySecret`, usada para HMAC de batches.

Esta key es independiente de `anon key` y `service_role key`.

- No reemplaza ninguna key de Supabase.
- Se usa solo para firmar y verificar batches de logs/metricas.
- Debe ser secreta, larga y aleatoria.

Origen de esta key:

- No la genera Supabase.
- No la genera hoy el CLI de `apploggers`.
- La genera tu equipo con un generador criptograficamente seguro y luego la distribuye por secretos de CI/CD o `local.properties` no versionado.

Generar key nueva con un CSPRNG real:

```bash
# Linux / macOS (OpenSSL)
openssl rand -hex 32
```

```powershell
# Windows PowerShell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToHexString($bytes).ToLower()
```

Configurar en la app (sin versionar):

```properties
APPLOGGERS_INTEGRITY_SECRET=secreto-largo-y-aleatorio
APPLOGGERS_INTEGRITY_SECRET_ID=10042026
```

Mapear a `BuildConfig` y al SDK:

```kotlin
AppLoggerConfig.Builder()
  .integritySecret(BuildConfig.LOGGER_INTEGRITY_SECRET)
  .integritySecretId(BuildConfig.LOGGER_INTEGRITY_SECRET_ID)
```

Configurar en el CLI para verificaciones:

```bash
APPLOGGERS_INTEGRITY_SECRET=secreto-largo-y-aleatorio
APPLOGGERS_INTEGRITY_SECRET_10042026=secreto-largo-y-aleatorio
```

Regla de seguridad:

- Nunca usar la `anon key` de Supabase como `integritySecret`.
- Nunca commitear `APPLOGGERS_INTEGRITY_SECRET` en el repositorio.
- Tratar esta key como secreto de aplicacion, no como credencial de Supabase.

### Alternativa: `api_key_env` para no almacenar el key en el archivo

Si se prefiere no tener el key en el archivo, usar `api_key_env` con el nombre de la variable de entorno UPPERCASE. La URL siempre va en el json — no existe variable de entorno para la URL en este path:

```json
"supabase": {
  "url": "https://xxxx.supabase.co",
  "schema": "apploggers",
  "api_key_env": "APPLOGGER_SUPABASE_KEY"
}
```

`schema` es opcional. El valor soportado y recomendado es `apploggers`.

Despues de las migraciones 023 y 024, `public` deja de ser un path operativo soportado para AppLoggers.

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

Verificacion SQL de hardening recomendada:

```sql
select grantee, table_name, string_agg(privilege_type, ', ' order by privilege_type) as privileges
from information_schema.role_table_grants
where table_schema = 'apploggers'
  and grantee in ('anon', 'authenticated', 'service_role')
group by grantee, table_name
order by grantee, table_name;

select grantee, routine_name, privilege_type
from information_schema.routine_privileges
where routine_schema = 'apploggers'
  and grantee in ('PUBLIC', 'anon', 'authenticated', 'service_role')
order by grantee, routine_name, privilege_type;

select jobname, schedule, command, active
from cron.job
where jobname in ('purge-old-logs-every-3-days', 'expire-beta-tester-mappings-weekly')
order by jobname;
```

Resultado esperado:

- `authenticated` no aparece con grants operativos sobre tablas ni con `EXECUTE` en routines de `apploggers`.
- `anon` solo aparece con los grants minimos descritos arriba.
- `PUBLIC` no conserva `EXECUTE` sobre routines de `apploggers`.
- Ambos jobs de mantenimiento existen y apuntan a `apploggers`.

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
from apploggers.app_logs
where created_at >= now() - interval '30 minutes';

select count(*) as total_manifests
from apploggers.log_batches
where created_at >= now() - interval '30 minutes';

select count(*) as total_metric_manifests,
  count(*) filter (where key_id is not null) as metric_manifests_with_key
from apploggers.metric_batches
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
3. No se aplicó la migración 023 que convierte `apploggers` en schema físico operativo.
4. No se aplicó la migración 024 que normaliza grants, routines expuestas y jobs de mantenimiento.
5. El schema `apploggers` no está expuesto al rol operativo o el entorno quedó a mitad de migración.

### Uso correcto de Supabase MCP para migraciones

Para DDL usa `mcp_supabase_apply_migration`, no `mcp_supabase_execute_sql`.

Flujo recomendado:

1. `mcp_supabase_list_migrations` para identificar el último paso aplicado.
2. Leer el siguiente archivo SQL en `docs/ES/migraciones/`.
3. `mcp_supabase_apply_migration` con nombre snake_case y el SQL completo del archivo.
4. Repetir en orden hasta llegar a 024.
5. `mcp_supabase_list_tables` y `mcp_supabase_get_advisors` para validar el estado final.

Reserva `mcp_supabase_execute_sql` para verificaciones puntuales, backfills DML controlados y queries diagnósticas.

Acciones:

1. Verificar que `api_key` en `cli.json` contiene el `service_role key`.
2. Validar migraciones 004 y 006 aplicadas.
3. Validar `GRANT USAGE ON SCHEMA apploggers TO service_role;`, que las tablas operativas existan en `apploggers` y que la migracion 024 haya revocado grants amplios a `authenticated` y `PUBLIC`.

### Error de tabla no encontrada

Causa probable: migraciones base no aplicadas o `schema=apploggers` sin las migraciones 023/024.

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
