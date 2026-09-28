param(
    [string]$LocalLabUserHome = $env:SPAWNPK_LOCALLAB_USER_HOME
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$hadCallerJavaHome = Test-Path Env:JAVA_HOME
$callerJavaHome = $env:JAVA_HOME
$callerPath = $env:Path
$callerLocationPushed = $false
$launchSnapshot = $null
$expectedAirgapSha256 = '83b3e27e2aae50512d044ae4c74d84afb36df8b8a8051b5eb0c9275427363c33'

try {
    Push-Location -LiteralPath $PSScriptRoot
    $callerLocationPushed = $true

$runtimeCheck = Join-Path $PSScriptRoot 'scripts\Check-ExternalRuntime.ps1'
if (-not (Test-Path -LiteralPath $runtimeCheck -PathType Leaf)) {
    throw "Missing LocalLab external-runtime verifier: $runtimeCheck"
}
& $runtimeCheck

# Keep the standalone legacy launcher deterministic too. The canonical
# scripts/Run-Client-Airgap.ps1 already selects Java before delegating here,
# but users and older tooling may still invoke this file directly.
$selector = Join-Path $PSScriptRoot 'scripts\Select-LocalLabJava.ps1'
if (-not (Test-Path -LiteralPath $selector -PathType Leaf)) {
    throw "Missing LocalLab Java selector: $selector"
}
. $selector
$java = Set-LocalLabJava

$jar = Join-Path $PSScriptRoot 'local-client\client-airgap.jar'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Missing $jar" }

# Bind this launch to invocation-owned bytes rather than reopening the mutable
# canonical JAR after its coherent-triplet verification.
$launchSnapshot = [IO.Path]::GetTempFileName()
Copy-Item -LiteralPath $jar -Destination $launchSnapshot -Force

$snapshotSha256 = (
    Get-FileHash -LiteralPath $launchSnapshot -Algorithm SHA256
).Hash.ToLowerInvariant()

if ($snapshotSha256 -ne $expectedAirgapSha256) {
    throw (
        'Invocation-owned AIRGAP client SHA-256 mismatch. ' +
        "Expected: $expectedAirgapSha256 Actual: $snapshotSha256"
    )
}

Write-Host (
    'AIRGAP_CLIENT_LAUNCH_SNAPSHOT_VERIFIED ' +
    "sha256=$snapshotSha256"
) -ForegroundColor Green

Write-Host 'Launching the locally supplied AIRGAP client verified by scripts\Check-ExternalRuntime.ps1.' -ForegroundColor Cyan
Write-Host 'The certified airgap runtime routes LocalLab socket/update/web authority to loopback.' -ForegroundColor Cyan
Write-Host 'Use fake local credentials only.' -ForegroundColor Yellow

$javaArgs = @()
if (-not [string]::IsNullOrWhiteSpace($LocalLabUserHome)) {
    $resolvedHome = [IO.Path]::GetFullPath($LocalLabUserHome)
    $trimChars = [char[]]@(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $resolvedHome = $resolvedHome.TrimEnd($trimChars)

    $realHome = [Environment]::GetFolderPath(
        [Environment+SpecialFolder]::UserProfile
    )
    if (-not [string]::IsNullOrWhiteSpace($realHome)) {
        $realHome = [IO.Path]::GetFullPath($realHome).TrimEnd($trimChars)
        if ([StringComparer]::OrdinalIgnoreCase.Equals(
                $resolvedHome,
                $realHome
            )) {
            throw 'Refusing LocalLab isolated user.home because it resolves to the real OS user home.'
        }
    }

    $cacheRoot = Join-Path $resolvedHome '.spawnpk'
    $dataRoot = Join-Path $resolvedHome '.spawnpk-data'

    if (-not (Test-Path -LiteralPath $cacheRoot -PathType Container)) {
        throw "Isolated LocalLab user.home is not seeded: missing cache root $cacheRoot"
    }

    if (-not (Test-Path -LiteralPath $dataRoot -PathType Container)) {
        New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null
    }

    $javaArgs += "-Duser.home=$resolvedHome"

    Write-Host "LOCAL_LAB_CLIENT_HOME_ISOLATED home=$resolvedHome" -ForegroundColor Green
    Write-Host "LOCAL_LAB_CLIENT_CACHE_ROOT $cacheRoot" -ForegroundColor Green
    Write-Host "LOCAL_LAB_CLIENT_DATA_ROOT $dataRoot" -ForegroundColor Green
} else {
    Write-Host 'LOCAL_LAB_CLIENT_HOME_DEFAULT no isolated user.home requested' -ForegroundColor DarkGray
}

& $java.Path @javaArgs -jar $launchSnapshot
if ($LASTEXITCODE -ne 0) {
    throw "AIRGAP client exited with code $LASTEXITCODE using $($java.Path)"
}

}
finally {
    if ($null -ne $launchSnapshot -and
        (Test-Path -LiteralPath $launchSnapshot -PathType Leaf)) {
        Remove-Item -LiteralPath $launchSnapshot -Force -ErrorAction SilentlyContinue
    }

    if ($callerLocationPushed) {
        Pop-Location
    }

    if ($hadCallerJavaHome) {
        $env:JAVA_HOME = $callerJavaHome
    }
    else {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    }
    $env:Path = $callerPath
}
