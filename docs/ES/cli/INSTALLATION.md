# AppLogger CLI — Guía de Instalación

**Última actualización**: 2026-03-20  
**Versión mínima**: Go 1.24+ (si compilas desde fuente)  
**Plataformas soportadas**: Windows, macOS, Linux (x86_64, ARM64)

---

## Índice

- [Instalación Rápida](#instalación-rápida)
- [Instalación por Plataforma](#instalación-por-plataforma)
- [Compilar desde Fuente](#compilar-desde-fuente)
- [Configuración Inicial](#configuración-inicial)
- [Verificación](#verificación)
- [Solución de Problemas](#solución-de-problemas)

---

## Instalación Rápida

### Opción 1: Instalador estándar de una línea (Recomendado)

```bash
# Linux
curl -fsSL https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.sh | bash

# macOS (Intel y Apple Silicon)
curl -fsSL https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.sh | bash
```

```powershell
# Windows PowerShell
irm https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.ps1 | iex
```

Este flujo:

- resuelve la última release `applogger-cli-v*`
- detecta plataforma y arquitectura
- descarga el binario correcto
- valida checksum SHA-256
- deja el binario listo para usar

Para fijar una versión específica:

```bash
APPLOGGER_CLI_VERSION=applogger-cli-v0.1.0 curl -fsSL https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.sh | bash
```

```powershell
$env:APPLOGGER_CLI_VERSION = 'applogger-cli-v0.1.0'
irm https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.ps1 | iex
```

### Opción 2: Descargar Binario (Manual)

```bash
# Linux / macOS
VERSION="applogger-cli-vX.Y.Z"
curl -L "https://github.com/devzucca/appLoggers/releases/download/${VERSION}/applogger-cli-linux-amd64" -o applogger-cli
chmod +x applogger-cli
sudo mv applogger-cli /usr/local/bin/

# Windows (PowerShell)
$version = "applogger-cli-vX.Y.Z"
$url = "https://github.com/devzucca/appLoggers/releases/download/$version/applogger-cli-windows-amd64.exe"
$output = "$env:ProgramFiles\applogger-cli.exe"
Invoke-WebRequest -Uri $url -OutFile $output
```

### Opción 3: Compilar desde Fuente

```bash
git clone https://github.com/devzucca/appLoggers.git
cd appLoggers/cli
go build -o applogger-cli ./cmd/applogger-cli
sudo mv applogger-cli /usr/local/bin/
```

### Opción 4: Homebrew (macOS, Linux)

```bash
# Aun no disponible
```

---

## Instalación por Plataforma

### 📦 Windows

#### A. Descargar Binario Precompilado

1. Ve a [GitHub Releases](https://github.com/devzucca/appLoggers/releases)
2. Descarga `applogger-cli-windows-amd64.exe`
3. Coloca el archivo en una carpeta dentro de `PATH` (ej: `C:\Program Files\applogger-cli\`)
4. Abre **PowerShell** o **CMD** y verifica:

```powershell
applogger-cli version --output json
```

#### B. Agregar a PATH (PowerShell)

```powershell
# Opción 1: Directamente al descargar
$downloadPath = "$env:USERPROFILE\Downloads\applogger-cli-windows-amd64.exe"
$installPath = "$env:ProgramFiles\applogger-cli.exe"
Copy-Item $downloadPath $installPath

# Opción 2: Agregar carpeta a PATH (permanente)
[Environment]::SetEnvironmentVariable(
    "Path",
    [Environment]::GetEnvironmentVariable("Path", "User") + ";C:\Users\TuUsuario\AppLocalData\Local\applogger-cli",
    "User"
)
# Reinicia PowerShell después
```

#### C. Compilar desde Fuente

```powershell
# 1. Instalar Go (si no lo tienes)
# Descargar desde https://golang.org/dl

# 2. Clonar y compilar
git clone https://github.com/devzucca/appLoggers.git
cd appLoggers\cli
go build -o applogger-cli.exe .\cmd\applogger-cli

# 3. Mover a PATH
Move-Item applogger-cli.exe "$env:ProgramFiles\applogger-cli.exe"
```

---

### 🍎 macOS

#### A. Descargar Binario Precompilado

```bash
# Detectar arquitectura
ARCH=$(uname -m)  # "arm64" (Apple Silicon) o "x86_64" (Intel)
VERSION="applogger-cli-vX.Y.Z"

# Descargar
curl -L \
  "https://github.com/devzucca/appLoggers/releases/download/${VERSION}/applogger-cli-darwin-${ARCH}" \
  -o applogger-cli

chmod +x applogger-cli
sudo mv applogger-cli /usr/local/bin/

# Verificar
applogger-cli version --output json
```

#### B. Compilar desde Fuente

```bash
# 1. Asegúrate de tener Go 1.24+
go version

# 2. Clonar y compilar
git clone https://github.com/devzucca/appLoggers.git
cd appLoggers/cli
go build -o applogger-cli ./cmd/applogger-cli

# 3. Instalar globalmente
sudo mv applogger-cli /usr/local/bin/
chmod +x /usr/local/bin/applogger-cli
```

#### C. Homebrew (Cuando esté disponible)

```bash
brew install devzucca/apploggers/applogger-cli
```

---

### 🐧 Linux

#### A. Descargar Binario Precompilado

```bash
# Detectar arquitectura
ARCH=$(dpkg --print-architecture)  # "amd64", "arm64", etc.
VERSION="applogger-cli-vX.Y.Z"

# Descargar
curl -L \
  "https://github.com/devzucca/appLoggers/releases/download/${VERSION}/applogger-cli-linux-${ARCH}" \
  -o applogger-cli

chmod +x applogger-cli
sudo mv applogger-cli /usr/local/bin/

# Verificar
applogger-cli version --output json
```

#### B. Compilar desde Fuente

```bash
# 1. Instalar Go (si no lo tienes)
sudo apt-get update
sudo apt-get install -y golang        # O descargar desde golang.org/dl

# 2. Clonar y compilar
git clone https://github.com/devzucca/appLoggers.git
cd appLoggers/cli
go build -o applogger-cli ./cmd/applogger-cli

# 3. Instalar globalmente
sudo mv applogger-cli /usr/local/bin/
sudo chmod +x /usr/local/bin/applogger-cli
```

#### C. Docker (Para CI/CD o ambientes aislados)

```dockerfile
# Dockerfile
FROM golang:1.25-alpine AS builder
WORKDIR /app
COPY . .
WORKDIR /app/cli
RUN go build -o applogger-cli ./cmd/applogger-cli

FROM alpine:latest
RUN apk --no-cache add ca-certificates
COPY --from=builder /app/cli/applogger-cli /usr/local/bin/
ENTRYPOINT ["applogger-cli"]
```

```bash
# Usar imagen Docker
docker build -t applogger-cli:latest .
docker run applogger-cli:latest --version
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
git clone https://github.com/devzucca/appLoggers.git
cd appLoggers

# 2. Descargar dependencias
cd cli
go mod download

# 3. Compilar
go build -o applogger-cli ./cmd/applogger-cli

# 4. (Opcional) Compilar para múltiples plataformas
make build-all

# 5. Instalar (Linux / macOS)
sudo mv applogger-cli /usr/local/bin/
chmod +x /usr/local/bin/applogger-cli

# 6. Verificar
applogger-cli version --output json
```

### Compilar para Otras Plataformas

```bash
# Linux ARM64
GOOS=linux GOARCH=arm64 go build -o applogger-cli-linux-arm64 ./cmd/applogger-cli

# macOS Intel
GOOS=darwin GOARCH=amd64 go build -o applogger-cli-darwin-amd64 ./cmd/applogger-cli

# macOS Apple Silicon
GOOS=darwin GOARCH=arm64 go build -o applogger-cli-darwin-arm64 ./cmd/applogger-cli

# Windows
GOOS=windows GOARCH=amd64 go build -o applogger-cli-windows-amd64.exe ./cmd/applogger-cli
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

$env:APPLOGGER_SUPABASE_URL = "https://TU_PROJECT_REF.supabase.co"
$env:APPLOGGER_SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Verifica que funcionó
applogger-cli health --output json
```

#### CMD (Windows)

```cmd
set APPLOGGER_SUPABASE_URL=https://TU_PROJECT_REF.supabase.co
set APPLOGGER_SUPABASE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

REM Verifica
applogger-cli health --output json
```

#### Bash / Zsh (macOS, Linux)

```bash
export APPLOGGER_SUPABASE_URL="https://TU_PROJECT_REF.supabase.co"
export APPLOGGER_SUPABASE_KEY="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Verifica
applogger-cli health --output json
```

#### Persistir Variables (Opcional)

**Bash/Zsh:**
```bash
# Agregar a ~/.bashrc o ~/.zshrc
echo 'export APPLOGGER_SUPABASE_URL="https://TU_PROJECT_REF.supabase.co"' >> ~/.bashrc
echo 'export APPLOGGER_SUPABASE_KEY="..."' >> ~/.bashrc
source ~/.bashrc
```

**PowerShell:**
```powershell
# Agregar a tu perfil PowerShell
$profile  # te muestra la ruta
Add-Content -Path $profile -Value @"
`$env:APPLOGGER_SUPABASE_URL = "https://TU_PROJECT_REF.supabase.co"
`$env:APPLOGGER_SUPABASE_KEY = "..."
"@
```

### 2. Configuración Avanzada (Opcional)

| Variable | Default | Propósito |
|---|---|---|
| `APPLOGGER_SUPABASE_URL` | — | URL de tu proyecto Supabase |
| `APPLOGGER_SUPABASE_KEY` | — | Llave `service_role` para consultas del CLI |
| `APPLOGGER_SUPABASE_SCHEMA` | `public` | Esquema en PostgreSQL |
| `APPLOGGER_SUPABASE_LOG_TABLE` | `app_logs` | Tabla de logs |
| `APPLOGGER_SUPABASE_METRIC_TABLE` | `app_metrics` | Tabla de métricas |
| `APPLOGGER_SUPABASE_TIMEOUT_SECONDS` | `15` | Timeout HTTP (1-120) |

> Seguridad: usa `service_role` solo en backend/entornos de operaciones.
> El SDK movil debe usar anon key y nunca exponer `service_role`.

---

## Verificación

### 1. Verificar Instalación

```bash
applogger-cli version --output json
# Output: {"name":"applogger-cli","version":"applogger-cli-vX.Y.Z",...}

applogger-cli --syncbin-metadata
# Output: JSON con metadatos Syncbin
```

### 2. Verificar Conectividad con Supabase

```bash
applogger-cli health --output json
# Debe retornar: {"ok": true, "services": {"supabase": "available", ...}}
```

### 3. Primer Query

```bash
applogger-cli telemetry query \
  --source logs \
  --aggregate severity \
  --limit 10 \
  --output json
```

---

## Solución de Problemas

### "applogger-cli: command not found"

**Causa**: El binario no está en PATH.

**Solución**:
```bash
# Verificar si existe
ls -la /usr/local/bin/applogger-cli

# Si no existe, descargarlo nuevamente
VERSION="applogger-cli-vX.Y.Z"
curl -L "https://github.com/devzucca/appLoggers/releases/download/${VERSION}/applogger-cli-linux-amd64" \
  -o /usr/local/bin/applogger-cli
chmod +x /usr/local/bin/applogger-cli
```

### "permission denied"

**Causa**: El archivo no tiene permisos de ejecución.

```bash
chmod +x /usr/local/bin/applogger-cli
```

### "health check failed: APPLOGGER_SUPABASE_URL not set"

**Causa**: Las variables de entorno no están configuradas.

```bash
# Verifica si están cargadas
echo $APPLOGGER_SUPABASE_URL
echo $APPLOGGER_SUPABASE_KEY

# Si están vacías, configúralas
export APPLOGGER_SUPABASE_URL="..."
export APPLOGGER_SUPABASE_KEY="..."
```

### "GOARCH and OS env vars not allowed"

**Causa**: Conflicto de variables de entorno durante compilación.

```bash
# Limpiar antes de compilar
unset GOOS GOARCH
go build -o applogger-cli ./cmd/applogger-cli
```

### "authorization failed: invalid API key"

**Causa**: La llave de Supabase es inválida o expiró.

**Solución**:
1. Ve a [Supabase Dashboard](https://supabase.com/dashboard)
2. Settings → API
3. Copia la llave `service_role` nuevamente
4. Reconfigura: `export APPLOGGER_SUPABASE_KEY="..."`

---

## Siguiente Paso

Una vez instalado, consulta [../../cli/README.md](../../cli/README.md) para:
- Comandos disponibles
- Ejemplos de consultas
- Modo agent para automatización
- Integración con agentes IA

Para configuración de Supabase a nivel operativo (migraciones, RLS,
service_role y usuario del CLI), consulta:
- [SUPABASE_CONFIGURATION.md](./SUPABASE_CONFIGURATION.md)

Para preguntas o issues:  
→ [GitHub Issues](https://github.com/devzucca/appLoggers/issues)
