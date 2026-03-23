# CLI Live Setup Runbook

## Paso 1 — Verificar que el CLI está instalado

```bash
apploggers version --output json
```

Si el comando no existe, instalar:

```bash
# Linux / macOS
curl -fsSL https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.sh | bash
```

```powershell
# Windows PowerShell
irm https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.ps1 | iex
```

---

## Paso 2 — Configurar `~/.apploggers/cli.json`

El CLI crea el archivo automáticamente en el primer run. Editarlo con las credenciales reales del proyecto Supabase.

Ruta del archivo:

```
Windows : C:\Users\<usuario>\.apploggers\cli.json
Linux   : /home/<usuario>/.apploggers/cli.json
macOS   : /Users/<usuario>/.apploggers/cli.json
```

Configuración mínima:

```json
{
  "default_project": "my-app",
  "projects": [
    {
      "name": "my-app",
      "display_name": "My Application",
      "workspace_roots": ["/path/to/workspace"],
      "supabase": {
        "url": "https://YOUR_PROJECT.supabase.co",
        "api_key": "eyJhbGci..."
      }
    }
  ]
}
```

Obtener las credenciales desde Supabase Dashboard → Project Settings → API:

- **Project URL** → campo `url`
- **service_role key** → campo `api_key`

> No versionar este archivo. Contiene el `service_role key`.

### Alternativa: `api_key_env`

Si se prefiere no almacenar el key en el archivo, usar `api_key_env` con el nombre de la variable UPPERCASE. La URL siempre va en el json — no existe variable de entorno para la URL en este path:

```json
"supabase": {
  "url": "https://YOUR_PROJECT.supabase.co",
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

`api_key_env` debe contener el **nombre** de la variable, nunca el JWT directamente. Si la variable no está exportada, el CLI cae automáticamente a `api_key`.

---

## Paso 3 — Validación operativa

```bash
apploggers capabilities --output json
apploggers health --deep --output json
apploggers telemetry query --source logs --limit 5 --output json
apploggers telemetry query --source metrics --limit 5 --output json
```

`health --deep` verifica conectividad real a Supabase: latencia, existencia de tablas `app_logs` y `app_metrics`, y que las migraciones 007/008/009 estén aplicadas (columnas `environment`, `anomaly_type` e índices adicionales).

Se considera listo cuando `health --deep` retorna `ok: true` y las queries no retornan 403.

---

## Paso 4 — Multi-proyecto (opcional)

Agregar múltiples entradas en `projects` con `workspace_roots` para autodetección:

```json
{
  "default_project": "klinema",
  "projects": [
    {
      "name": "klinema",
      "workspace_roots": ["/workspace/klinema"],
      "supabase": {
        "url": "https://klinema.supabase.co",
        "api_key": "eyJhbGci..."
      }
    },
    {
      "name": "klinematv",
      "workspace_roots": ["/workspace/klinematv"],
      "supabase": {
        "url": "https://klinematv.supabase.co",
        "api_key": "eyJhbGci..."
      }
    }
  ]
}
```

Selección explícita de proyecto:

```bash
apploggers --project klinema telemetry query --source logs --limit 5 --output json
```

---

## Notas de seguridad

1. `service_role key` solo para operaciones de backend/ops — nunca en cliente móvil o frontend.
2. En Linux, restringir permisos: `chmod 600 ~/.apploggers/cli.json`.
3. Rotar el key periódicamente y actualizar el archivo.
