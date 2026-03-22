# AppLogger CLI — Guía de Instalación

**Última actualización**: 2026-03-21  
**Versión mínima**: Go 1.24+ (si compilas desde fuente)  
**Plataformas soportadas**: Windows, macOS, Linux (x86_64, ARM64)

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

### Opción 1: Instalador estándar de una línea (Recomendado)

```bash
# Linux
curl -fsSL https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.sh | bash

# macOS (Intel y Apple Silicon)
curl -fsSL https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.sh | bash
```

```powershell
# Windows PowerShell
irm https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.ps1 | iex
```

Este flujo:

- resuelve la última release `apploggers-v*`
- detecta plataforma y arquitectura
- descarga el binario correcto
- valida checksum SHA-256 de forma obligatoria
- deja el binario listo para usar
- aplica política de red con reintentos y timeouts para descargas

Notas de hardening del instalador:

- Linux/macOS: requiere `sha256sum` o `shasum`; si no existe verificador, la instalación falla.
- Windows: usa TLS 1.2 y reintentos de descarga con timeout configurable.

Parámetros de red (opcionales):

- Bash installer:
  - `APPLOGGERS_CURL_RETRY_MAX` (default `5`)
  - `APPLOGGERS_CURL_RETRY_DELAY` (default `2`)
  - `APPLOGGERS_CURL_CONNECT_TIMEOUT` (default `10`)
  - `APPLOGGERS_CURL_MAX_TIME` (default `120`)
  - `APPLOGGERS_CURL_RETRY_MAX_TIME` (default `300`)
- PowerShell installer:
  - `-DownloadRetries` (default `5`)
  - `-RetryDelaySeconds` (default `2`)
  - `-DownloadTimeoutSeconds` (default `120`)

Para fijar una versión específica:

```bash
APPLOGGERS_VERSION=apploggers-v0.1.1 curl -fsSL https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.sh | bash
```

```powershell
$env:APPLOGGERS_VERSION = 'apploggers-v0.1.1'
irm https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.ps1 | iex
```

## Upgrade en Sitio

Con una version reciente del CLI puedes actualizar directamente desde el binario:

```bash
apploggers upgrade
```

Version especifica:

```bash
apploggers upgrade --version apploggers-v0.1.1
```

Forzar reinstalacion aunque ya coincida la version:

```bash
apploggers upgrade --force
```

### Opción 2: Descargar Binario (Manual)

```bash
# Linux / macOS
VERSION="apploggers-vX.Y.Z"
curl -L "https://github.com/zuccadev-labs/appLoggers/releases/download/${VERSION}/apploggers-linux-amd64" -o apploggers
chmod +x apploggers
sudo mv apploggers /usr/local/bin/

# Windows (PowerShell)
$version = "apploggers-vX.Y.Z"
$url = "https://github.com/zuccadev-labs/appLoggers/releases/download/$version/apploggers-windows-amd64.exe"
$output = "$env:ProgramFiles\apploggers.exe"
Invoke-WebRequest -Uri $url -OutFile $output
```

### Opción 3: Compilar desde Fuente

```bash
git clone https://github.com/zuccadev-labs/appLoggers.git
cd appLoggers/cli
go build -o apploggers ./cmd/apploggers
sudo mv apploggers /usr/local/bin/
```

### Opción 4: Homebrew / Scoop / Winget (manifiestos de publicación)

Desde `apploggers-v*`, el workflow de release genera y publica estos manifiestos como assets:

- `manifests/homebrew/apploggers.rb`
- `manifests/scoop/apploggers.json`
- `manifests/winget/DevZucca.AppLoggerCLI*.yaml`

Esto deja la publicación lista para:

- Tap Homebrew propio
- Bucket Scoop propio
- PR al repositorio `microsoft/winget-pkgs`

---

## Instalación por Plataforma

### 📦 Windows

#### A. Descargar Binario Precompilado

1. Ve a [GitHub Releases](https://github.com/zuccadev-labs/appLoggers/releases)
2. Descarga `apploggers-windows-amd64.exe`
3. Coloca el archivo en una carpeta dentro de `PATH` (ej: `C:\Program Files\apploggers\`)
4. Abre **PowerShell** o **CMD** y verifica:

```powershell
apploggers version --output json
```

#### B. Agregar a PATH (PowerShell)

```powershell
# Opción 1: Directamente al descargar
$downloadPath = "$env:USERPROFILE\Downloads\apploggers-windows-amd64.exe"
$installPath = "$env:ProgramFiles\apploggers.exe"
Copy-Item $downloadPath $installPath

# Opción 2: Agregar carpeta a PATH (permanente)
[Environment]::SetEnvironmentVariable(
    "Path",
    [Environment]::GetEnvironmentVariable("Path", "User") + ";C:\Users\TuUsuario\AppLocalData\Local\apploggers",
    "User"
)
# Reinicia PowerShell después
```

#### C. Compilar desde Fuente

```powershell
# 1. Instalar Go (si no lo tienes)
# Descargar desde https://golang.org/dl

# 2. Clonar y compilar
git clone https://github.com/zuccadev-labs/appLoggers.git
cd appLoggers\cli
go build -o apploggers.exe .\cmd\apploggers

# 3. Mover a PATH
Move-Item apploggers.exe "$env:ProgramFiles\apploggers.exe"
```

---

### 🍎 macOS

#### A. Descargar Binario Precompilado

```bash
# Detectar arquitectura
ARCH=$(uname -m)  # "arm64" (Apple Silicon) o "x86_64" (Intel)
VERSION="apploggers-vX.Y.Z"

# Descargar
curl -L \
  "https://github.com/zuccadev-labs/appLoggers/releases/download/${VERSION}/apploggers-darwin-${ARCH}" \
  -o apploggers

chmod +x apploggers
sudo mv apploggers /usr/local/bin/

# Verificar
apploggers version --output json
```

#### B. Compilar desde Fuente

```bash
# 1. Asegúrate de tener Go 1.24+
go version

# 2. Clonar y compilar
git clone https://github.com/zuccadev-labs/appLoggers.git
cd appLoggers/cli
go build -o apploggers ./cmd/apploggers

# 3. Instalar globalmente
sudo mv apploggers /usr/local/bin/
chmod +x /usr/local/bin/apploggers
```

#### C. Homebrew (con tap propio)

```bash
# 1) copiar el formula generado desde los assets de release
# 2) publicarlo en tu tap (ej: devzucca/homebrew-applogger)
brew install devzucca/apploggers/apploggers
```

#### D. Winget (publicación comunitaria)

```powershell
# usar los manifiestos winget generados en el release
# y abrir PR a microsoft/winget-pkgs
winget install DevZucca.AppLoggerCLI
```

---

### 🐧 Linux

#### A. Descargar Binario Precompilado

```bash
# Detectar arquitectura
ARCH=$(dpkg --print-architecture)  # "amd64", "arm64", etc.
VERSION="apploggers-vX.Y.Z"

# Descargar
curl -L \
  "https://github.com/zuccadev-labs/appLoggers/releases/download/${VERSION}/apploggers-linux-${ARCH}" \
  -o apploggers

chmod +x apploggers
sudo mv apploggers /usr/local/bin/

# Verificar
apploggers version --output json
```

#### B. Compilar desde Fuente

```bash
# 1. Instalar Go (si no lo tienes)
sudo apt-get update
sudo apt-get install -y golang        # O descargar desde golang.org/dl

# 2. Clonar y compilar
git clone https://github.com/zuccadev-labs/appLoggers.git
cd appLoggers/cli
go build -o apploggers ./cmd/apploggers

# 3. Instalar globalmente
sudo mv apploggers /usr/local/bin/
sudo chmod +x /usr/local/bin/apploggers
```

#### C. Docker (Para CI/CD o ambientes aislados)

```dockerfile
# Dockerfile
FROM golang:1.25-alpine AS builder
WORKDIR /app
COPY . .
WORKDIR /app/cli
RUN go build -o apploggers ./cmd/apploggers

FROM alpine:latest
RUN apk --no-cache add ca-certificates
COPY --from=builder /app/cli/apploggers /usr/local/bin/
ENTRYPOINT ["apploggers"]
```

```bash
# Usar imagen Docker
docker build -t apploggers:latest .
docker run apploggers:latest --version
```

---

## Compilar desde Fuente

### Requisitos

| Herramienta | Versión mínima | Verificar con |
|---|---|---|
| Go | 1.24 | `go version` |
| Git | 2.30+ | `git --version` |
| GNU Make | 4.3+ (opcional) | `make --version` |

### Pasos

```bash
# 1. Clonar el repositorio
git clone https://github.com/zuccadev-labs/appLoggers.git
cd appLoggers

# 2. Descargar dependencias
cd cli
go mod download

# 3. Compilar
go build -o apploggers ./cmd/apploggers

# 4. (Opcional) Compilar para múltiples plataformas
make build-all

# 5. Instalar (Linux / macOS)
sudo mv apploggers /usr/local/bin/
chmod +x /usr/local/bin/apploggers

# 6. Verificar
apploggers version --output json
```

### Compilar para Otras Plataformas

```bash
# Linux ARM64
GOOS=linux GOARCH=arm64 go build -o apploggers-linux-arm64 ./cmd/apploggers

# macOS Intel
GOOS=darwin GOARCH=amd64 go build -o apploggers-darwin-amd64 ./cmd/apploggers

# macOS Apple Silicon
GOOS=darwin GOARCH=arm64 go build -o apploggers-darwin-arm64 ./cmd/apploggers

# Windows
GOOS=windows GOARCH=amd64 go build -o apploggers-windows-amd64.exe ./cmd/apploggers
```

---

## Configuración Inicial

### 1. Configurar Supabase (Backend de Telemetría)

El CLI necesita acceso a tu proyecto Supabase para consultar logs y métricas.

#### PowerShell (Windows)

```powershell
# Obtener credenciales de Supabase
# 1. Ve a https://supabase.com/dashboard
# 2. Selecciona tu proyecto
# 3. Settings → API → copia los valores

$env:appLogger_supabaseUrl = "https://TU_PROJECT_REF.supabase.co"
$env:appLogger_supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Verifica que funcionó
apploggers health --output json
```

#### CMD (Windows)

```cmd
set appLogger_supabaseUrl=https://TU_PROJECT_REF.supabase.co
set appLogger_supabaseKey=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

REM Verifica
apploggers health --output json
```

#### Bash / Zsh (macOS, Linux)

```bash
export appLogger_supabaseUrl="https://TU_PROJECT_REF.supabase.co"
export appLogger_supabaseKey="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Verifica
apploggers health --output json
```

#### Persistir Variables (Opcional)

**Bash/Zsh:**
```bash
# Agregar a ~/.bashrc o ~/.zshrc
echo 'export appLogger_supabaseUrl="https://TU_PROJECT_REF.supabase.co"' >> ~/.bashrc
echo 'export appLogger_supabaseKey="..."' >> ~/.bashrc
source ~/.bashrc
```

**PowerShell:**
```powershell
# Agregar a tu perfil PowerShell
$profile  # te muestra la ruta
Add-Content -Path $profile -Value @"
`$env:appLogger_supabaseUrl = "https://TU_PROJECT_REF.supabase.co"
`$env:appLogger_supabaseKey = "..."
"@
```

### 2. Configuración Avanzada (Opcional)

| Variable | Default | Propósito |
|---|---|---|
| `appLogger_supabaseUrl` | — | URL de tu proyecto Supabase |
| `appLogger_supabaseKey` | — | Llave `service_role` para consultas del CLI |
| `appLogger_supabaseSchema` | `public` | Esquema en PostgreSQL |
| `appLogger_supabaseLogTable` | `app_logs` | Tabla de logs |
| `appLogger_supabaseMetricTable` | `app_metrics` | Tabla de métricas |
| `appLogger_supabaseTimeoutSeconds` | `15` | Timeout HTTP (1-120) |

> Seguridad: usa `service_role` solo en backend/entornos de operaciones.
> El SDK movil debe usar anon key y nunca exponer `service_role`.

---

## Verificación

### 1. Verificar Instalación

```bash
apploggers version --output json
# Output: {"name":"apploggers","version":"apploggers-vX.Y.Z",...}

apploggers --syncbin-metadata
# Output: JSON con metadatos Syncbin
```

### 2. Verificar Conectividad con Supabase

```bash
apploggers health --output json
# Debe retornar: {"ok": true, "services": {"supabase": "available", ...}}
```

### 3. Primer Query

```bash
apploggers telemetry query \
  --source logs \
  --aggregate severity \
  --limit 10 \
  --output json
```

---

## Solución de Problemas

### "apploggers: command not found"

**Causa**: El binario no está en PATH.

**Solución**:
```bash
# Verificar si existe
ls -la /usr/local/bin/apploggers

# Si no existe, descargarlo nuevamente
VERSION="apploggers-vX.Y.Z"
curl -L "https://github.com/zuccadev-labs/appLoggers/releases/download/${VERSION}/apploggers-linux-amd64" \
  -o /usr/local/bin/apploggers
chmod +x /usr/local/bin/apploggers
```

### "permission denied"

**Causa**: El archivo no tiene permisos de ejecución.

```bash
chmod +x /usr/local/bin/apploggers
```

### "health check failed: appLogger_supabaseUrl not set"

**Causa**: Las variables de entorno no están configuradas.

```bash
# Verifica si están cargadas
echo $appLogger_supabaseUrl
echo $appLogger_supabaseKey

# Si están vacías, configúralas
export appLogger_supabaseUrl="..."
export appLogger_supabaseKey="..."
```

### "GOARCH and OS env vars not allowed"

**Causa**: Conflicto de variables de entorno durante compilación.

```bash
# Limpiar antes de compilar
unset GOOS GOARCH
go build -o apploggers ./cmd/apploggers
```

### "authorization failed: invalid API key"

**Causa**: La llave de Supabase es inválida o expiró.

**Solución**:
1. Ve a [Supabase Dashboard](https://supabase.com/dashboard)
2. Settings → API
3. Copia la llave `service_role` nuevamente
4. Reconfigura: `export appLogger_supabaseKey="..."`

---

## Siguiente Paso

Una vez instalado, consulta [./README.md](./README.md) para:
- Comandos disponibles
- Ejemplos de consultas
- Modo agent para automatización
- Integración con agentes IA

Para configuración de Supabase a nivel operativo (migraciones, RLS,
service_role y usuario del CLI), consulta:
- [SUPABASE_CONFIGURATION.md](./SUPABASE_CONFIGURATION.md)

Para preguntas o issues:  
→ [GitHub Issues](https://github.com/zuccadev-labs/appLoggers/issues)
