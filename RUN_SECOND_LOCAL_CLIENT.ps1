param(
    [string]$Target = $PSScriptRoot,
    [switch]$AllowNonAirgap
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$lab = (Resolve-Path -LiteralPath $Target).Path
$runtimeCheck = Join-Path $lab 'scripts\Check-ExternalRuntime.ps1'

if (-not (Test-Path -LiteralPath $runtimeCheck -PathType Leaf)) {
    throw "Missing canonical external-runtime verifier: $runtimeCheck"
}

& $runtimeCheck

$ports = 43594, 43595
$listeners = @(
    Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -in $ports }
)

$portNumbers = @(
    $listeners |
        Select-Object -ExpandProperty LocalPort -Unique
)
$ownerPids = @(
    $listeners |
        Select-Object -ExpandProperty OwningProcess -Unique
)

if (($portNumbers -notcontains 43594) -or
    ($portNumbers -notcontains 43595)) {
    throw "LocalLab server is not listening on both 43594 and 43595. Ready ports: $($portNumbers -join ',')"
}

if ($ownerPids.Count -ne 1) {
    throw "Expected one LocalLab Java PID to own both 43594 and 43595; found owners: $($ownerPids -join ',')"
}

$serverPid = [int]$ownerPids[0]
$server = Get-CimInstance Win32_Process -Filter "ProcessId=$serverPid" -ErrorAction SilentlyContinue

if (-not $server -or
    $server.Name -notmatch '^javaw?\.exe$' -or
    $server.CommandLine -notmatch 'SpawnPKLocalServer|spk\.local\.Main|SpawnPK-LocalLab') {
    $name = if ($server) { $server.Name } else { '<missing>' }
    $command = if ($server) { $server.CommandLine } else { '<missing>' }
    throw "Ports 43594/43595 owner PID $serverPid is not an expected SpawnPK LocalLab Java server. Name=$name Command=$command"
}

$airgapLauncher = Join-Path $lab 'scripts\Run-Client-Airgap.ps1'
$nonAirgapLauncher = Join-Path $lab 'RUN_CLIENT_LOCALHOST_NONAIRGAP.ps1'
$launcher = $airgapLauncher

if ($AllowNonAirgap) {
    $launcher = $nonAirgapLauncher
    Write-Host 'SECOND_CLIENT_NONAIRGAP_EXPLICIT externalEndpointsMayRemain=true' -ForegroundColor Red
    Write-Host 'Explicit diagnostic mode: the localhost client may retain external web/CDN endpoints.' -ForegroundColor Yellow
}
else {
    Write-Host 'SECOND_CLIENT_AIRGAP_DEFAULT externalEndpointAuthority=false' -ForegroundColor Green
}

if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw "Selected second-client launcher is missing: $launcher"
}

Write-Host '=== SpawnPK LocalLab second client ===' -ForegroundColor Cyan
Write-Host "Server PID : $serverPid"
Write-Host "Launcher   : $launcher"
Write-Host 'Account    : src (server auto-selects src when opensrc is already online)' -ForegroundColor Green
Write-Host 'Do not close the first client/server window while starting this one.' -ForegroundColor Yellow

Push-Location $lab
try {
    if ($AllowNonAirgap) {
        & $launcher -AllowExternalEndpoints
    }
    else {
        & $launcher
    }
}
finally {
    Pop-Location
}
