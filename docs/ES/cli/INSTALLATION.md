# AppLoggers CLI — Guía de Instalación

**Última actualización**: 2026-03-22
**Plataformas soportadas**: Windows, macOS, Linux (x86_64, ARM64)
**Requisito mínimo para compilar desde fuente**: Go 1.24+

---

## Índice

- [Instalación Rápida](#instalación-rápida)
- [Instalación por Plataforma](#instalación-por-plataforma)
- [Compilar desde Fuente](#compilar-desde-fuente)
- [Configuración Inicial](#configuración-inicial)
- [Verificación](#verificación)
- [Upgrade en Sitio](#upgrade-en-sitio)
- [Solución de Problemas](#solución-de-problemas)

---

## Instalación Rápida

### Opción 1: Instalador de una línea (Recomendado)

```bash
# Linux / macOS
curl -fsSL https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.sh | bash
```

```powershell
# Windows PowerShell
irm https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.ps1 | iex
```

El instalador:

- Resuelve la última release `apploggers-v*` automáticamente
- Detecta plataforma y arquitectura
- Descarga el binario correcto
- Valida checksum SHA-256 de forma obligatoria
- Crea `~/.apploggers/cli.json` con un template de ejemplo si no existe
- Agrega el binario al `PATH`

Notas de seguridad:

- Linux/macOS: requiere `sha256sum` o `shasum`; si no existe verificador, la instalación falla.
- Windows: usa TLS 1.2 y reintentos de descarga con timeout configurable.

### Parámetros opcionales del instalador

**Bash (Linux/macOS):**

| Variable | Default | Propósito |
|---|---|---|
| `APPLOGGERS_VERSION` | última release | Tag específico a instalar (ej: `apploggers-vX.Y.Z`) |
| `APPLOGGERS_INSTALL_DIR` | `/usr/local/bin` o `~/.local/bin` | Directorio de instalación |
| `APPLOGGERS_CONFIG_DIR` | `~/.apploggers` | Directorio de configuración |
| `APPLOGGERS_CURL_RETRY_MAX` | `5` | Reintentos de descarga |
| `APPLOGGERS_CURL_CONNECT_TIMEOUT` | `10` | Timeout de conexión (segundos) |
| `APPLOGGERS_CURL_MAX_TIME` | `120` | Timeout total de descarga (segundos) |

```bash
# Instalar una versión específica
APPLOGGERS_VERSION=apploggers-vX.Y.Z \
  curl -fsSL https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.sh | bash
```

**PowerShell (Windows):**

| Parámetro | Default | Propósito |
|---|---|---|
| `-Version` / `$env:APPLOGGERS_VERSION` | última release | Tag específico a instalar |
| `-InstallDir` / `$env:APPLOGGERS_INSTALL_DIR` | `%LOCALAPPDATA%\Programs\AppLoggers` | Directorio de instalación |
| `-ConfigDir` / `$env:APPLOGGERS_CONFIG_DIR` | `%USERPROFILE%\.apploggers` | Directorio de configuración |
| `-DownloadRetries` | `5` | Reintentos de descarga |
| `-DownloadTimeoutSeconds` | `120` | Timeout de descarga (segundos) |

```powershell
# Instalar una versión específica
$env:APPLOGGERS_VERSION = 'apploggers-vX.Y.Z'
irm https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.ps1 | iex
```

---

### Opción 2: Descargar Binario Manualmente

```bash
# Linux / macOS
VERSION="apploggers-vX.Y.Z"
curl -L "https://github.com/zuccadev-labs/appLoggers/releases/download/${VERSION}/apploggers-linux-amd64" -o apploggers
chmod +x apploggers
sudo mv apploggers /usr/local/bin/
```

```powershell
# Windows PowerShell
$version = "apploggers-vX.Y.Z"
$url = "https://github.com/zuccadev-labs/appLoggers/releases/download/$version/apploggers-windows-amd64.exe"
Invoke-WebRequest -Uri $url -OutFile "$env:ProgramFiles\apploggers.exe"
```

### Opción 3: Compilar desde Fuente

```bash
git clone https://github.com/zuccadev-labs/appLoggers.git
cd appLoggers/cli
go build -o apploggers ./cmd/applogger-cli
sudo mv apploggers /usr/local/bin/
```

### Opción 4: Gestores de paquetes

El workflow de release genera y publica manifiestos listos para usar:

- Homebrew: `apploggers.rb`
- Scoop: `apploggers.json`
- Winget: `DevZucca.AppLoggers.*.yaml`

Consulta los assets de la última release en [GitHub Releases](https://github.com/zuccadev-labs/appLoggers/releases).

---

## Instalación por Plataforma

### Windows

1. Ve a [GitHub Releases](https://github.com/zuccadev-labs/appLoggers/releases)
2. Descarga `apploggers-windows-amd64.exe`
3. Colócalo en una carpeta dentro de `PATH` (ej: `C:\Program Files\AppLoggers\`)
4. Verifica:

```powershell
apploggers version --output json
```

Para agregar al PATH de forma permanente:

```powershell
[Environment]::SetEnvironmentVariable(
    "Path",
    [Environment]::GetEnvironmentVariable("Path", "User") + ";C:\Program Files\AppLoggers",
    "User"
)
# Reinicia PowerShell después
```

### macOS

```bash
# Detectar arquitectura automáticamente
ARCH=$(uname -m | sed 's/x86_64/amd64/')
VERSION="apploggers-vX.Y.Z"

curl -L \
  "https://github.com/zuccadev-labs/appLoggers/releases/download/${VERSION}/apploggers-darwin-${ARCH}" \
  -o apploggers

chmod +x apploggers
sudo mv apploggers /usr/local/bin/
apploggers version --output json
```

### Linux

```bash
ARCH=$(dpkg --print-architecture 2>/dev/null || uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/')
VERSION="apploggers-vX.Y.Z"

curl -L \
  "https://github.com/zuccadev-labs/appLoggers/releases/download/${VERSION}/apploggers-linux-${ARCH}" \
  -o apploggers

chmod +x apploggers
sudo mv apploggers /usr/local/bin/
apploggers version --output json
```

### Docker

```dockerfile
FROM golang:1.24-alpine AS builder
WORKDIR /app/cli
COPY cli/ .
RUN go build -o apploggers ./cmd/applogger-cli

FROM alpine:latest
RUN apk --no-cache add ca-certificates
COPY --from=builder /app/cli/apploggers /usr/local/bin/
ENTRYPOINT ["apploggers"]
```

---

## Compilar desde Fuente

### Requisitos

| Herramienta | Versión mínima |
|---|---|
| Go | 1.24 |
| Git | 2.30+ |

### Pasos

```bash
git clone https://github.com/zuccadev-labs/appLoggers.git
cd appLoggers/cli
go mod download
go build -o apploggers ./cmd/applogger-cli
sudo mv apploggers /usr/local/bin/
apploggers version --output json
```

### Cross-compilation

```bash
# Linux ARM64
GOOS=linux GOARCH=arm64 go build -o apploggers-linux-arm64 ./cmd/applogger-cli

# macOS Intel
GOOS=darwin GOARCH=amd64 go build -o apploggers-darwin-amd64 ./cmd/applogger-cli

# macOS Apple Silicon
GOOS=darwin GOARCH=arm64 go build -o apploggers-darwin-arm64 ./cmd/applogger-cli

# Windows
GOOS=windows GOARCH=amd64 go build -o apploggers-windows-amd64.exe ./cmd/applogger-cli
```

---

## Configuración Inicial

El CLI crea `~/.apploggers/cli.json` automáticamente en el primer run. Este archivo es la única fuente de configuración — no hay variables de entorno que gestionar.

Editar el archivo con los datos del proyecto Supabase (URL y `service_role key` desde Supabase Dashboard → Project Settings → API):

```json
{
  "default_project": "my-app",
  "projects": [
    {
      "name": "my-app",
      "display_name": "My Application",
      "workspace_roots": [],
      "supabase": {
        "url": "https://your-project.supabase.co",
        "api_key": "eyJhbGci..."
      }
    }
  ]
}
```

Rutas del archivo:

```
Windows : C:\Users\<usuario>\.apploggers\cli.json
Linux   : /home/<usuario>/.apploggers/cli.json
macOS   : /Users/<usuario>/.apploggers/cli.json
```

> No versionar este archivo — contiene el `service_role key`. El SDK móvil usa `anon key`; el CLI usa `service_role key`. Nunca exponer `service_role` en clientes móviles o frontend.

Si se prefiere no almacenar el key en el archivo, usar `api_key_env` con el nombre de la variable UPPERCASE. La URL siempre va en el json — no existe variable de entorno para la URL en este path:

```json
"supabase": {
  "url": "https://your-project.supabase.co",
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

Ver [SUPABASE_CONFIGURATION.md](./SUPABASE_CONFIGURATION.md) para configuración corporativa completa.

---

## Verificación

```bash
# Verificar instalación
apploggers version --output json

# Verificar conectividad con Supabase
apploggers health --output json

# Primer query
apploggers telemetry query \
  --source logs \
  --aggregate severity \
  --limit 10 \
  --output json
```

---

## Upgrade en Sitio

```bash
# Actualizar a la última versión
apploggers upgrade

# Versión específica
apploggers upgrade --version apploggers-vX.Y.Z

# Forzar reinstalación
apploggers upgrade --force
```

---

## Solución de Problemas

### "apploggers: command not found"

El binario no está en PATH.

```bash
# Verificar si existe
which apploggers || ls -la /usr/local/bin/apploggers

# Reinstalar
curl -fsSL https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.sh | bash
```

### "permission denied"

```bash
chmod +x /usr/local/bin/apploggers
```

### "missing Supabase URL" o "missing Supabase API key"

El archivo `~/.apploggers/cli.json` no tiene `url` o `api_key` configurados.

```bash
# Verificar archivo de configuración
cat ~/.apploggers/cli.json
```

Asegurarse de que `supabase.url` y `supabase.api_key` (o `supabase.api_key_env`) tienen valores no vacíos.

### "authorization failed: invalid API key"

La key de Supabase es inválida o expiró.

1. Ve a [Supabase Dashboard](https://supabase.com/dashboard) → Settings → API
2. Copia la `service_role` key
3. Actualiza `api_key` en `~/.apploggers/cli.json`

### "GOARCH and OS env vars not allowed" (al compilar)

```bash
unset GOOS GOARCH
go build -o apploggers ./cmd/applogger-cli
```

---

## Siguiente Paso

- Referencia de comandos y consultas: [README.md](./README.md)
- Configuración Supabase corporativa: [SUPABASE_CONFIGURATION.md](./SUPABASE_CONFIGURATION.md)

→ [GitHub Issues](https://github.com/zuccadev-labs/appLoggers/issues)
