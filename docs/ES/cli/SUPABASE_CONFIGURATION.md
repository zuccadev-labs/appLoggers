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

Checklist post-migración:

- Existe tabla `app_logs`.
- Existe tabla `app_metrics`.
- Existen índices de diagnóstico (incluido `idx_app_logs_tag`).
- RLS habilitado en ambas tablas.
- Policies `sdk_insert_*` y `monitor_read_*` activas.
- No existen policies `authenticated_read_*` globales.

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
        "api_key": "eyJhbGci..."
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
  "api_key_env": "APPLOGGER_SUPABASE_KEY"
}
```

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
apploggers telemetry agent-response --source logs --aggregate severity --preview-limit 3 --output agent
```

Se considera OK cuando:

- `health` retorna `ok: true`.
- Query de logs retorna estado exitoso sin 403.
- Query de metrics por `--name` funciona.

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

Acciones:

1. Verificar que `api_key` en `cli.json` contiene el `service_role key`.
2. Validar migraciones 004 y 006 aplicadas.

### Error de tabla no encontrada

Causa probable: migraciones 001/002 no aplicadas.

Acción: aplicar migraciones pendientes y volver a ejecutar query.

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
