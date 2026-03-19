# AppLogger CLI - Guia Detallada de Configuracion Supabase y Usuario Operativo

Ultima actualizacion: 2026-03-19

## Objetivo

Esta guia define la configuracion recomendada para operar AppLogger CLI en entornos reales:

1. Configuracion de Supabase (tablas, indices, RLS y retencion).
2. Provision de credenciales seguras para lectura del CLI.
3. Definicion de usuario operativo del CLI (SO/CI) y practicas de hardening.

Importante:

- El SDK movil debe usar anon key para INSERT.
- El CLI de operaciones debe usar service_role key para SELECT.
- No expongas service_role en cliente movil ni frontend.

## Arquitectura de acceso

- SDK app: inserta telemetria en `app_logs` y `app_metrics` con anon key.
- CLI ops: consulta telemetria con service_role key.
- RLS: anon inserta, service_role lee.

## Paso 1 - Aplicar migraciones en Supabase

Orden recomendado:

1. `001_create_app_logs.sql`
2. `002_create_app_metrics.sql`
3. `003_create_indexes.sql`
4. `004_rls_policies.sql`
5. `005_retention_policy.sql`
6. `006_harden_authenticated_read_policies.sql`

Checklist post-migracion:

- Existe tabla `app_logs`.
- Existe tabla `app_metrics`.
- Existen indices de diagnostico (incluido `idx_app_logs_tag`).
- RLS habilitado en ambas tablas.
- Policies `sdk_insert_*` y `monitor_read_*` activas.
- No existen policies `authenticated_read_*` globales.

## Paso 2 - Obtener credenciales correctas

Desde Supabase Dashboard:

1. Project Settings.
2. API.
3. Copiar:
   - Project URL (ej: `https://xxxx.supabase.co`)
   - service_role key (solo backend/ops)

Variables requeridas para CLI:

- `APPLOGGER_SUPABASE_URL`
- `APPLOGGER_SUPABASE_KEY` (service_role)

Opcionales:

- `APPLOGGER_SUPABASE_SCHEMA` (default `public`)
- `APPLOGGER_SUPABASE_LOG_TABLE` (default `app_logs`)
- `APPLOGGER_SUPABASE_METRIC_TABLE` (default `app_metrics`)
- `APPLOGGER_SUPABASE_TIMEOUT_SECONDS` (default `15`)

## Paso 3 - Crear usuario operativo del CLI

Recomendacion: usar un usuario tecnico dedicado para ejecuciones automatizadas.

### Linux

Crear usuario sin login interactivo:

```bash
sudo useradd --system --home /opt/applogger-cli --shell /usr/sbin/nologin applogger-cli
sudo mkdir -p /opt/applogger-cli
sudo chown -R applogger-cli:applogger-cli /opt/applogger-cli
```

Guardar secretos en archivo root-only y exportarlos en un servicio systemd:

```bash
sudo install -m 600 -o root -g root /dev/null /etc/applogger-cli.env
```

Contenido sugerido de `/etc/applogger-cli.env`:

```env
APPLOGGER_SUPABASE_URL=https://YOUR_PROJECT.supabase.co
APPLOGGER_SUPABASE_KEY=YOUR_SERVICE_ROLE_KEY
APPLOGGER_SUPABASE_SCHEMA=public
APPLOGGER_SUPABASE_LOG_TABLE=app_logs
APPLOGGER_SUPABASE_METRIC_TABLE=app_metrics
APPLOGGER_SUPABASE_TIMEOUT_SECONDS=15
```

### Windows

Opciones recomendadas:

1. Cuenta de servicio dedicada para Task Scheduler.
2. Secretos inyectados por pipeline (Azure DevOps/GitHub Actions/Runner local).
3. Evitar guardar secrets en scripts versionados.

PowerShell para sesion actual:

```powershell
$env:APPLOGGER_SUPABASE_URL = "https://YOUR_PROJECT.supabase.co"
$env:APPLOGGER_SUPABASE_KEY = "YOUR_SERVICE_ROLE_KEY"
```

### CI/CD

- Guardar secretos en vault del CI.
- Nunca imprimir key completa en logs.
- Enmascarar variables sensibles.

Ejemplo GitHub Actions:

```yaml
env:
  APPLOGGER_SUPABASE_URL: ${{ secrets.APPLOGGER_SUPABASE_URL }}
  APPLOGGER_SUPABASE_KEY: ${{ secrets.APPLOGGER_SUPABASE_SERVICE_ROLE_KEY }}
```

## Paso 4 - Verificacion operativa del CLI

Comandos de verificacion minima:

```bash
applogger-cli health --output json
applogger-cli telemetry query --source logs --limit 5 --output json
applogger-cli telemetry query --source metrics --name response_time_ms --limit 5 --output json
applogger-cli telemetry agent-response --source logs --aggregate severity --preview-limit 3 --output agent
```

Se considera OK cuando:

- `health` retorna `ok: true`.
- Query de logs retorna estado exitoso sin 403.
- Query de metrics por `--name` funciona.

## Paso 5 - Seguridad recomendada

- Rotar service_role key periodicamente.
- Limitar acceso al entorno que contiene secrets.
- Evitar copiar la key en terminal compartida o tickets.
- No usar service_role en aplicaciones cliente.
- Mantener RLS y migracion 006 aplicadas en todos los entornos.

## Paso 6 - Troubleshooting

### Error 403 al consultar

Causas probables:

1. Se uso anon/publishable key en CLI.
2. RLS/policies no aplicadas en el entorno.

Acciones:

1. Verificar key cargada en `APPLOGGER_SUPABASE_KEY`.
2. Validar migraciones 004 y 006 aplicadas.

### Error de tabla no encontrada

Causa probable: migraciones 002/003 no aplicadas.

Accion:

- Aplicar migraciones pendientes y volver a ejecutar query.

### Timeouts

- Ajustar `APPLOGGER_SUPABASE_TIMEOUT_SECONDS` (1..120).
- Reducir rango temporal y limite en query.

## Matriz de responsabilidades

- Equipo Mobile/SDK:
  - Usa anon key para escritura.
- Equipo Ops/Plataforma:
  - Gestiona service_role key en secretos.
  - Opera AppLogger CLI para lectura y diagnostico.
- Seguridad:
  - Audita acceso a secretos y rotacion.

## Referencias

- `docs/ES/migraciones/004_rls_policies.sql`
- `docs/ES/migraciones/006_harden_authenticated_read_policies.sql`
- `docs/ES/cli/INSTALLATION.md`
- `docs/ES/cli/README.md`
- `cli/README.md`
