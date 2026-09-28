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

try {
    Push-Location -LiteralPath $PSScriptRoot
    $callerLocationPushed = $true

    . $selector
    $java = Set-LocalLabJava

& $runtimeCheck

Write-Host 'NONAIRGAP_DIAGNOSTIC_EXPLICIT externalEndpointsMayRemain=true' -ForegroundColor Red
Write-Host 'This localhost client keeps game/AUX sockets local but may still contain external web/CDN endpoints.' -ForegroundColor Yellow
Write-Host 'Use RUN_SECOND_LOCAL_CLIENT.ps1 without -AllowNonAirgap for the canonical airgap default.' -ForegroundColor Yellow

& $java.Path -jar $jar
if ($LASTEXITCODE -ne 0) {
    throw "NONAIRGAP diagnostic client exited with code $LASTEXITCODE using $($java.Path)"
}
}
finally {
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
