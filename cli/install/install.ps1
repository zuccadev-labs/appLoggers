param(
    [string]$Version = $env:APPLOGGER_CLI_VERSION,
    [string]$InstallDir = $env:APPLOGGER_CLI_INSTALL_DIR,
    [string]$ConfigDir = $env:APPLOGGER_CLI_CONFIG_DIR,
    [int]$DownloadRetries = 5,
    [int]$RetryDelaySeconds = 2,
    [int]$DownloadTimeoutSeconds = 120
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$Repo = 'zuccadev-labs/appLoggers'

function Write-Log {
    param([string]$Message)
    Write-Host "[applogger-cli] $Message"
}

function Resolve-Version {
    param([string]$RequestedVersion)

    if ($RequestedVersion) {
        if ($RequestedVersion -notlike 'applogger-cli-v*') {
            throw 'APPLOGGER_CLI_VERSION must match applogger-cli-v*.'
        }
        return $RequestedVersion
    }

    $releases = Invoke-WithRetry -Action {
        Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases?per_page=100" -TimeoutSec $DownloadTimeoutSeconds
    }
    $release = $releases | Where-Object { $_.tag_name -like 'applogger-cli-v*' } | Select-Object -First 1
    if (-not $release) {
        throw 'Unable to resolve latest applogger-cli release tag.'
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

if (-not $InstallDir) {
    $InstallDir = Join-Path $env:LOCALAPPDATA 'Programs\AppLoggerCLI'
}

if (-not $ConfigDir) {
    $ConfigDir = Join-Path $env:USERPROFILE '.apploggers'
}

[string]$ConfigFile = Join-Path $ConfigDir 'cli.json'

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$tag = Resolve-Version -RequestedVersion $Version
$assetName = 'applogger-cli-windows-amd64.exe'
$checksumName = "$assetName.sha256"
$releaseBase = "https://github.com/$Repo/releases/download/$tag"

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
New-Item -ItemType Directory -Force -Path $ConfigDir | Out-Null
if (-not (Test-Path -LiteralPath $ConfigFile)) {
        @'
{
    "default_project": "",
    "projects": []
}
'@ | Set-Content -Path $ConfigFile -Encoding UTF8
        Write-Log "Created config template at $ConfigFile"
}

$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("applogger-cli-" + [System.Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

try {
    $downloadPath = Join-Path $tempDir $assetName
    $checksumPath = Join-Path $tempDir $checksumName
    $finalPath = Join-Path $InstallDir 'applogger-cli.exe'

    Write-Log "Installing $assetName from $tag"
    Download-File -Uri "$releaseBase/$assetName" -OutFile $downloadPath
    Download-File -Uri "$releaseBase/$checksumName" -OutFile $checksumPath

    $expectedHash = (Get-Content -Path $checksumPath -Raw).Split([char[]]@(' ', "`t", "`r", "`n"), [System.StringSplitOptions]::RemoveEmptyEntries)[0].ToLowerInvariant()
    $actualHash = (Get-FileHash -Path $downloadPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($expectedHash -ne $actualHash) {
        throw 'Checksum mismatch for downloaded applogger-cli binary.'
    }

    Move-Item -Force $downloadPath $finalPath
    Ensure-PathContains -Directory $InstallDir
    Write-Log "Installed to $finalPath"
    & $finalPath version
}
finally {
    Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
}