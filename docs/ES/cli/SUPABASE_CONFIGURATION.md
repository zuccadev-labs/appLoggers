# AppLogger CLI - Guia Detallada de Configuracion Supabase y Usuario Operativo

Ultima actualizacion: 2026-03-21

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

- `appLogger_supabaseUrl`
- `appLogger_supabaseKey` (service_role)

Aliases compatibles:

- `APPLOGGER_SUPABASE_URL`
- `APPLOGGER_SUPABASE_KEY`
- `SUPABASE_URL`
- `SUPABASE_KEY`

Opcionales:

- `appLogger_supabaseSchema` (default `public`)
- `appLogger_supabaseLogTable` (default `app_logs`)
- `appLogger_supabaseMetricTable` (default `app_metrics`)
- `appLogger_supabaseTimeoutSeconds` (default `15`)

## Paso 2b - Configuracion multi-proyecto para CLI + Wails

Cuando el CLI opere varias aplicaciones de telemetria distintas
(`klinema`, `klinematv`, etc.), la configuracion recomendada ya no es un solo
par global de variables, sino un registro de proyectos compartido entre el CLI
y la futura app de escritorio en Wails.

Variables de control:

- `APPLOGGER_CONFIG`: ruta al archivo JSON de proyectos.
- `APPLOGGER_PROJECT`: seleccion explicita del proyecto activo.
- `--config`: override por linea de comandos.
- `--project`: override por linea de comandos.

Ruta default del archivo:

- Windows/Linux/macOS: `~/.apploggers/cli.json`
- Fallback legacy compatible: `$(os.UserConfigDir)/applogger/cli.json`

Ejemplo recomendado:

```json
{
  "default_project": "klinema",
  "projects": [
    {
      "name": "klinema",
      "display_name": "Klinema Mobile",
      "workspace_roots": [
        "D:/workspace/klinema"
      ],
      "supabase": {
        "url": "https://klinema.supabase.co",
        "api_key_env": "APPLOGGER_KLINEMA_SUPABASE_KEY",
        "schema": "public",
        "logs_table": "app_logs",
        "metrics_table": "app_metrics",
        "timeout_seconds": 15
      }
    },
    {
      "name": "klinematv",
      "display_name": "Klinema TV",
      "workspace_roots": [
        "D:/workspace/klinematv"
      ],
      "supabase": {
        "url": "https://klinematv.supabase.co",
        "api_key_env": "APPLOGGER_KLINEMATV_SUPABASE_KEY"
      }
    }
  ]
}
```

Precedencia de resolucion:

1. `--project`
2. `APPLOGGER_PROJECT`
3. Matching por `workspace_roots` contra el directorio actual
4. `default_project`
5. Unico proyecto configurado
6. Fallback legacy a `appLogger_supabase*` / `APPLOGGER_SUPABASE_*` / `SUPABASE_*`

Practica corporativa recomendada:

- Guardar solo URL y metadata en el JSON.
- Guardar el `service_role` en variables de entorno o secreto del sistema usando `api_key_env`.
- Hacer que Wails administre el registro de proyectos y lance el CLI con el mismo modelo.
- En SSE transmitir el proyecto resuelto y nunca la credencial cruda.

## Paso 3 - Crear usuario operativo del CLI

Recomendacion: usar un usuario tecnico dedicado para ejecuciones automatizadas.

### Linux

Crear usuario sin login interactivo:

```bash
sudo useradd --system --home /opt/apploggers --shell /usr/sbin/nologin apploggers
sudo mkdir -p /opt/apploggers
sudo chown -R apploggers:apploggers /opt/apploggers
```

Guardar secretos en archivo root-only y exportarlos en un servicio systemd:

```bash
sudo install -m 600 -o root -g root /dev/null /etc/apploggers.env
```

Contenido sugerido de `/etc/apploggers.env`:

```env
appLogger_supabaseUrl=https://YOUR_PROJECT.supabase.co
appLogger_supabaseKey=YOUR_SERVICE_ROLE_KEY
appLogger_supabaseSchema=public
appLogger_supabaseLogTable=app_logs
appLogger_supabaseMetricTable=app_metrics
appLogger_supabaseTimeoutSeconds=15
```

### Windows

Opciones recomendadas:

1. Cuenta de servicio dedicada para Task Scheduler.
2. Secretos inyectados por pipeline (Azure DevOps/GitHub Actions/Runner local).
3. Evitar guardar secrets en scripts versionados.

PowerShell para sesion actual:

```powershell
$env:appLogger_supabaseUrl = "https://YOUR_PROJECT.supabase.co"
$env:appLogger_supabaseKey = "YOUR_SERVICE_ROLE_KEY"
```

### CI/CD

- Guardar secretos en vault del CI.
- Nunca imprimir key completa en logs.
- Enmascarar variables sensibles.

Ejemplo GitHub Actions:

```yaml
env:
  appLogger_supabaseUrl: ${{ secrets.APPLOGGER_SUPABASE_URL }}
  appLogger_supabaseKey: ${{ secrets.APPLOGGER_SUPABASE_KEY }}
```

Para runners multi-proyecto, tambien puede inyectarse:

```yaml
env:
  APPLOGGER_CONFIG: /opt/applogger/cli.json
  APPLOGGER_PROJECT: klinema
  APPLOGGER_KLINEMA_SUPABASE_KEY: ${{ secrets.APPLOGGER_KLINEMA_SUPABASE_KEY }}
```

Para `act` en local, definir el mismo par en `.act.secrets`. Si el workflow referencia un secret ausente, `act` lo inyecta vacio.

## Paso 4 - Verificacion operativa del CLI

Comandos de verificacion minima:

```bash
apploggers health --output json
apploggers telemetry query --source logs --limit 5 --output json
apploggers telemetry query --source metrics --name response_time_ms --limit 5 --output json
apploggers telemetry agent-response --source logs --aggregate severity --preview-limit 3 --output agent
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

1. Verificar key cargada en `appLogger_supabaseKey`.
2. Validar migraciones 004 y 006 aplicadas.

### Error de tabla no encontrada

Causa probable: migraciones 002/003 no aplicadas.

Accion:

- Aplicar migraciones pendientes y volver a ejecutar query.

### Timeouts

- Ajustar `appLogger_supabaseTimeoutSeconds` (1..120).
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
