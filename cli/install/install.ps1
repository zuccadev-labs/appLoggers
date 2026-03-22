param(
    [string]$Version = $env:APPLOGGERS_VERSION,
    [string]$InstallDir = $env:APPLOGGERS_INSTALL_DIR,
    [string]$ConfigDir = $env:APPLOGGERS_CONFIG_DIR,
    [int]$DownloadRetries = 5,
    [int]$RetryDelaySeconds = 2,
    [int]$DownloadTimeoutSeconds = 120
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$Repo = 'zuccadev-labs/appLoggers'

function Write-Log {
    param([string]$Message)
    Write-Host "[apploggers] $Message"
}

function Resolve-Version {
    param([string]$RequestedVersion)

    if ($RequestedVersion) {
        if ($RequestedVersion -notlike 'apploggers-v*') {
            throw 'APPLOGGERS_VERSION must match apploggers-v*.'
        }
        return $RequestedVersion
    }

    $releases = Invoke-WithRetry -Action {
        Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases?per_page=100" -TimeoutSec $DownloadTimeoutSeconds
    }
    $release = $releases | Where-Object { $_.tag_name -like 'apploggers-v*' } | Select-Object -First 1
    if (-not $release) {
        throw 'Unable to resolve latest apploggers release tag.'
    }
    return $release.tag_name
}

function Invoke-WithRetry {
    param(
        [scriptblock]$Action
    )

    $attempt = 1
    while ($attempt -le $DownloadRetries) {
        try {
            return & $Action
        }
        catch {
            if ($attempt -ge $DownloadRetries) {
                throw
            }
            Write-Log "Attempt $attempt failed; retrying in $RetryDelaySeconds second(s)..."
            Start-Sleep -Seconds $RetryDelaySeconds
            $attempt++
        }
    }
}

function Download-File {
    param(
        [string]$Uri,
        [string]$OutFile
    )

    Invoke-WithRetry -Action {
        Invoke-WebRequest -Uri $Uri -OutFile $OutFile -TimeoutSec $DownloadTimeoutSeconds
    }
}

function Ensure-PathContains {
    param([string]$Directory)

    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    $segments = @()
    if ($userPath) {
        $segments = $userPath -split ';'
    }

    if ($segments -notcontains $Directory) {
        $newPath = if ($userPath) { "$userPath;$Directory" } else { $Directory }
        [Environment]::SetEnvironmentVariable('Path', $newPath, 'User')
        Write-Log "Added $Directory to the user PATH. Restart PowerShell to pick it up in new sessions."
    }
}

function Write-ExampleConfig {
    param([string]$ConfigFile)

    @'
{
  "_comment": "AppLoggers CLI configuration file. Edit this file to configure your projects.",
  "_docs": "https://github.com/zuccadev-labs/appLoggers/tree/main/docs/ES/cli",
  "default_project": "my-app",
  "projects": [
    {
      "name": "my-app",
      "display_name": "My Application",
      "workspace_roots": [],
      "supabase": {
        "url": "https://your-project.supabase.co",
        "api_key_env": "APPLOGGER_SUPABASE_KEY",
        "schema": "public",
        "logs_table": "app_logs",
        "metrics_table": "app_metrics",
        "timeout_seconds": 15
      }
    }
  ]
}
'@ | Set-Content -Path $ConfigFile -Encoding UTF8
    Write-Log "Created config template at $ConfigFile"
}

if (-not $InstallDir) {
    $InstallDir = Join-Path $env:LOCALAPPDATA 'Programs\AppLoggers'
}

if (-not $ConfigDir) {
    $ConfigDir = Join-Path $env:USERPROFILE '.apploggers'
}

[string]$ConfigFile = Join-Path $ConfigDir 'cli.json'

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$tag = Resolve-Version -RequestedVersion $Version
$assetName = 'apploggers-windows-amd64.exe'
$checksumName = "$assetName.sha256"
$releaseBase = "https://github.com/$Repo/releases/download/$tag"

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
New-Item -ItemType Directory -Force -Path $ConfigDir | Out-Null
if (-not (Test-Path -LiteralPath $ConfigFile)) {
    Write-ExampleConfig -ConfigFile $ConfigFile
}

$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("apploggers-" + [System.Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

try {
    $downloadPath = Join-Path $tempDir $assetName
    $checksumPath = Join-Path $tempDir $checksumName
    $finalPath = Join-Path $InstallDir 'apploggers.exe'

    Write-Log "Installing $assetName from $tag"
    Download-File -Uri "$releaseBase/$assetName" -OutFile $downloadPath
    Download-File -Uri "$releaseBase/$checksumName" -OutFile $checksumPath

    $expectedHash = (Get-Content -Path $checksumPath -Raw).Split([char[]]@(' ', "`t", "`r", "`n"), [System.StringSplitOptions]::RemoveEmptyEntries)[0].ToLowerInvariant()
    $actualHash = (Get-FileHash -Path $downloadPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($expectedHash -ne $actualHash) {
        throw 'Checksum mismatch for downloaded apploggers binary.'
    }

    Move-Item -Force $downloadPath $finalPath
    Ensure-PathContains -Directory $InstallDir
    Write-Log "Installed to $finalPath"
    & $finalPath version
}
finally {
    Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
}
