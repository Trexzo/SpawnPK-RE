param(
    [string]$LocalLabUserHome = $env:SPAWNPK_LOCALLAB_USER_HOME
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$hadCallerJavaHome = Test-Path Env:JAVA_HOME
$callerJavaHome = $env:JAVA_HOME
$callerPath = $env:Path
$callerLocationPushed = $false

$primaryFailure = $null
$cleanupFailures = @()
$launchRoot = $null
$launchSnapshot = $null
$snapshotGuard = $null

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
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw "Missing $jar"
    }

    $expectedAirgapSha256 =
        '024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7'

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
    }
    else {
        Write-Host 'LOCAL_LAB_CLIENT_HOME_DEFAULT no isolated user.home requested' -ForegroundColor DarkGray
    }

    $launchRoot = Join-Path (
        [IO.Path]::GetTempPath()
    ) (
        'SpawnPK-airgap-' + [Guid]::NewGuid().ToString('N')
    )
    [void](New-Item -ItemType Directory -Path $launchRoot)

    $launchSnapshot = Join-Path $launchRoot 'client-airgap.jar'
    [IO.File]::Copy($jar, $launchSnapshot, $false)

    $snapshotGuard = [IO.File]::Open(
        $launchSnapshot,
        [IO.FileMode]::Open,
        [IO.FileAccess]::Read,
        [IO.FileShare]::Read
    )

    $snapshotSha256 = (
        Get-FileHash -InputStream $snapshotGuard -Algorithm SHA256
    ).Hash.ToLowerInvariant()

    if ($snapshotSha256 -ne $expectedAirgapSha256) {
        throw (
            'AIRGAP private launch snapshot SHA-256 mismatch. ' +
            "Expected: $expectedAirgapSha256 Actual: $snapshotSha256"
        )
    }

    Write-Host (
        'AIRGAP_CLIENT_LAUNCH_SNAPSHOT_VERIFIED ' +
        "sha256=$snapshotSha256 basename=client-airgap.jar guard=no-write-no-delete"
    ) -ForegroundColor Green

    & $java.Path @javaArgs -jar $launchSnapshot
    $clientExit = $LASTEXITCODE
    if ($clientExit -ne 0) {
        throw "AIRGAP client exited with code $clientExit using $($java.Path)"
    }
}
catch {
    $primaryFailure = $_
}
finally {
    if ($null -ne $snapshotGuard) {
        try {
            $snapshotGuard.Dispose()
        }
        catch {
            $cleanupFailures += "snapshot guard dispose: $($_.Exception.Message)"
        }
    }

    if ($null -ne $launchSnapshot -and
        (Test-Path -LiteralPath $launchSnapshot)) {
        try {
            Remove-Item -LiteralPath $launchSnapshot -Force -ErrorAction Stop
        }
        catch {
            $cleanupFailures += "snapshot leaf cleanup: $($_.Exception.Message)"
        }
    }

    if ($null -ne $launchRoot -and
        (Test-Path -LiteralPath $launchRoot)) {
        try {
            Remove-Item -LiteralPath $launchRoot -ErrorAction Stop
        }
        catch {
            $cleanupFailures += "snapshot directory cleanup: $($_.Exception.Message)"
        }
    }

    if ($callerLocationPushed) {
        try {
            Pop-Location
        }
        catch {
            $cleanupFailures += "caller location restore: $($_.Exception.Message)"
        }
    }

    try {
        if ($hadCallerJavaHome) {
            $env:JAVA_HOME = $callerJavaHome
        }
        else {
            Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        }
        $env:Path = $callerPath
    }
    catch {
        $cleanupFailures += "caller Java environment restore: $($_.Exception.Message)"
    }
}

if ($null -ne $primaryFailure) {
    if ($cleanupFailures.Count -ne 0) {
        $message = (
            'AIRGAP client launch failed; cleanup was also incomplete. ' +
            "Primary: $($primaryFailure.Exception.Message) " +
            "Cleanup: $($cleanupFailures -join ' | ')"
        )
        throw [System.Exception]::new(
            $message,
            $primaryFailure.Exception
        )
    }

    throw $primaryFailure
}

if ($cleanupFailures.Count -ne 0) {
    throw (
        'AIRGAP launcher cleanup was incomplete after caller-state restoration. ' +
        ($cleanupFailures -join ' | ')
    )
}
