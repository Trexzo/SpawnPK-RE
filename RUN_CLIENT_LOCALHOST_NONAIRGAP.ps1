param(
    [switch]$AllowExternalEndpoints
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

if (-not $AllowExternalEndpoints) {
    throw 'Refusing NONAIRGAP diagnostic launch without explicit -AllowExternalEndpoints opt-in. Use the canonical airgap launcher by default.'
}

$selector = Join-Path $PSScriptRoot 'scripts\Select-LocalLabJava.ps1'
$runtimeCheck = Join-Path $PSScriptRoot 'scripts\Check-ExternalRuntime.ps1'
$jar = Join-Path $PSScriptRoot 'local-client\client-localhost.jar'

foreach ($required in @($selector, $runtimeCheck, $jar)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing nonairgap diagnostic component: $required"
    }
}

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

    . $selector
    $java = Set-LocalLabJava

    & $runtimeCheck

    $expectedLocalhostSha256 =
        '15ceb89669ddfe0a666e65b4a5291705af692a50e479bbde17e751eceb7fd23e'

    Write-Host 'NONAIRGAP_DIAGNOSTIC_EXPLICIT externalEndpointsMayRemain=true' -ForegroundColor Red
    Write-Host 'This localhost client keeps game/AUX sockets local but may still contain external web/CDN endpoints.' -ForegroundColor Yellow
    Write-Host 'Use RUN_SECOND_LOCAL_CLIENT.ps1 without -AllowNonAirgap for the canonical airgap default.' -ForegroundColor Yellow

    $launchRoot = Join-Path (
        [IO.Path]::GetTempPath()
    ) (
        'SpawnPK-localhost-' + [Guid]::NewGuid().ToString('N')
    )
    [void](New-Item -ItemType Directory -Path $launchRoot)

    $launchSnapshot = Join-Path $launchRoot 'client-localhost.jar'
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

    if ($snapshotSha256 -ne $expectedLocalhostSha256) {
        throw (
            'NONAIRGAP private launch snapshot SHA-256 mismatch. ' +
            "Expected: $expectedLocalhostSha256 Actual: $snapshotSha256"
        )
    }

    Write-Host (
        'NONAIRGAP_CLIENT_LAUNCH_SNAPSHOT_VERIFIED ' +
        "sha256=$snapshotSha256 basename=client-localhost.jar guard=no-write-no-delete"
    ) -ForegroundColor Green

    & $java.Path -jar $launchSnapshot
    $clientExit = $LASTEXITCODE
    if ($clientExit -ne 0) {
        throw "NONAIRGAP diagnostic client exited with code $clientExit using $($java.Path)"
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
            'NONAIRGAP diagnostic client launch failed; cleanup was also incomplete. ' +
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
        'NONAIRGAP launcher cleanup was incomplete after caller-state restoration. ' +
        ($cleanupFailures -join ' | ')
    )
}
