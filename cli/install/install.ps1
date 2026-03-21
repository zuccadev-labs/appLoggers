Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

param(
    [string]$Version = $env:APPLOGGER_CLI_VERSION,
    [string]$InstallDir = $env:APPLOGGER_CLI_INSTALL_DIR
)

$Repo = 'devzucca/appLoggers'

function Write-Log {
    param([string]$Message)
    Write-Host "[applogger-cli] $Message"
}

function Resolve-Version {
    param([string]$RequestedVersion)

    if ($RequestedVersion) {
        return $RequestedVersion
    }

    $releases = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases?per_page=100"
    $release = $releases | Where-Object { $_.tag_name -like 'applogger-cli-v*' } | Select-Object -First 1
    if (-not $release) {
        throw 'Unable to resolve latest applogger-cli release tag.'
    }
    return $release.tag_name
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

$tag = Resolve-Version -RequestedVersion $Version
$assetName = 'applogger-cli-windows-amd64.exe'
$checksumName = "$assetName.sha256"
$releaseBase = "https://github.com/$Repo/releases/download/$tag"

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("applogger-cli-" + [System.Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

try {
    $downloadPath = Join-Path $tempDir $assetName
    $checksumPath = Join-Path $tempDir $checksumName
    $finalPath = Join-Path $InstallDir 'applogger-cli.exe'

    Write-Log "Installing $assetName from $tag"
    Invoke-WebRequest -Uri "$releaseBase/$assetName" -OutFile $downloadPath
    Invoke-WebRequest -Uri "$releaseBase/$checksumName" -OutFile $checksumPath

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