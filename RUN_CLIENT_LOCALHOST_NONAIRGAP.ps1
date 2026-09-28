Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$selector = Join-Path $PSScriptRoot 'scripts\Select-LocalLabJava.ps1'
$runtimeCheck = Join-Path $PSScriptRoot 'scripts\Check-ExternalRuntime.ps1'
$jar = Join-Path $PSScriptRoot 'local-client\client-localhost.jar'

foreach ($required in @($selector, $runtimeCheck, $jar)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing nonairgap diagnostic component: $required"
    }
}

. $selector
$java = Set-LocalLabJava

& $runtimeCheck

Write-Host 'NONAIRGAP_DIAGNOSTIC_EXPLICIT externalEndpointsMayRemain=true' -ForegroundColor Red
Write-Host 'This localhost client keeps the game socket local but may still contain external web/CDN endpoints.' -ForegroundColor Yellow
Write-Host 'Use RUN_SECOND_LOCAL_CLIENT.ps1 without -NonAirgap for the canonical airgap default.' -ForegroundColor Yellow

& $java.Path -jar $jar
if ($LASTEXITCODE -ne 0) {
    throw "NONAIRGAP diagnostic client exited with code $LASTEXITCODE using $($java.Path)"
}
