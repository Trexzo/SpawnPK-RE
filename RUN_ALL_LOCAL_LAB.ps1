Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# R85 JAVA11+ AUTOSELECT BEGIN
# Keep this marker for VERIFY_OFFLINE_READY.ps1 compatibility, but use the
# current canonical selector shared by scripts/Run-Server.ps1 and
# scripts/Run-Client-Airgap.ps1. It prefers the proven Java 17 runtime.
$__r85Selector = Join-Path $PSScriptRoot 'scripts\Select-LocalLabJava.ps1'
if (-not (Test-Path -LiteralPath $__r85Selector -PathType Leaf)) {
    throw "Missing LocalLab Java selector: $__r85Selector"
}
. $__r85Selector
$__r85JavaInfo = Set-LocalLabJava
Write-Host ("R85_LAUNCH_JAVA_OK major={0} path={1}" -f $__r85JavaInfo.Major,$__r85JavaInfo.Path) -ForegroundColor Green
# R85 JAVA11+ AUTOSELECT END

$runtimeCheck = Join-Path $PSScriptRoot 'scripts\Check-ExternalRuntime.ps1'
if (-not (Test-Path -LiteralPath $runtimeCheck -PathType Leaf)) {
    throw "Missing LocalLab external-runtime preflight: $runtimeCheck"
}
& $runtimeCheck
Write-Host 'CURRENT_LOCALLAB_RUNTIME_PRECHECK_PASS' -ForegroundColor Green

$ports = 43594,43595
$listeners = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -in $ports } |
    Select-Object -ExpandProperty OwningProcess -Unique

foreach ($ownerPid in $listeners) {
    $p = Get-CimInstance Win32_Process -Filter "ProcessId=$ownerPid" -ErrorAction SilentlyContinue
    if (-not $p) { continue }

    $isJava = $p.Name -match '^javaw?\.exe$'
    $isSpawnLab = $p.CommandLine -match 'SpawnPKLocalServer|spk\.local\.Main|SpawnPK-LocalLab'
    $isRoatLab = $p.CommandLine -match 'RoatPKZ-LocalLab.*roat-local-server'

    if ($isJava -and ($isSpawnLab -or $isRoatLab)) {
        $label = if ($isRoatLab) { 'RoatPKZ LocalLab' } else { 'previous SpawnPK LocalLab' }
        Write-Host "Stopping conflicting $label server PID $ownerPid..." -ForegroundColor Yellow
        Stop-Process -Id $ownerPid -Force
    } else {
        throw "SpawnPK port conflict: PID $ownerPid ($($p.Name)) does not look like a known LocalLab server. Command: $($p.CommandLine)"
    }
}

$deadline = (Get-Date).AddSeconds(5)
do {
    Start-Sleep -Milliseconds 250
    $busy = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -in $ports }
} while ($busy -and (Get-Date) -lt $deadline)

if ($busy) {
    $busy | Format-Table LocalAddress,LocalPort,OwningProcess
    throw 'Ports 43594/43595 are still occupied. SpawnPK LocalLab was not started.'
}

$root = $PSScriptRoot
$serverScript = Join-Path $root 'scripts\Run-Server.ps1'
$watcherScript = Join-Path $root 'WATCH_CLIENT_NETWORK.ps1'
$clientScript = Join-Path $root 'scripts\Run-Client-Airgap.ps1'
$serverJar = Join-Path $root 'server\build\SpawnPKLocalServer.jar'

if (-not (Test-Path -LiteralPath $serverJar -PathType Leaf)) {
    throw "Missing current LocalLab server JAR: $serverJar. Run .\BOOTSTRAP.ps1 or .\scripts\Build-Server.ps1 first."
}

foreach ($required in @($serverScript,$watcherScript,$clientScript)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing LocalLab launcher component: $required"
    }
}

Write-Host 'Starting localhost server in a new PowerShell...' -ForegroundColor Green
Start-Process powershell.exe -WorkingDirectory $root -ArgumentList @(
    '-NoExit','-ExecutionPolicy','Bypass','-File',"`"$serverScript`""
)

$ready = $false
$readyDeadline = (Get-Date).AddSeconds(30)
$readyPortNumbers = @()
while ((Get-Date) -lt $readyDeadline) {
    Start-Sleep -Milliseconds 250
    $readyPortNumbers = @(
        Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $_.LocalPort -in $ports } |
            Select-Object -ExpandProperty LocalPort -Unique
    )

    if (($readyPortNumbers -contains 43594) -and ($readyPortNumbers -contains 43595)) {
        $ready = $true
        break
    }
}
if (-not $ready) {
    throw "Local server did not begin listening on both 43594 and 43595 within 30 seconds. Ready ports: $($readyPortNumbers -join ',')"
}
Write-Host 'SERVER_PORTS_READY game=43594 aux=43595' -ForegroundColor Green

Write-Host 'Starting loopback network watcher...' -ForegroundColor Green
Start-Process powershell.exe -WorkingDirectory $root -ArgumentList @(
    '-NoExit','-ExecutionPolicy','Bypass','-File',"`"$watcherScript`""
)

$existingAirgapPids = @(
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Name -match '^javaw?\.exe$' -and
            $_.CommandLine -match '(?i)client-airgap\.jar'
        } |
        Select-Object -ExpandProperty ProcessId
)

Write-Host 'Starting airgap client...' -ForegroundColor Green
Start-Process powershell.exe -WorkingDirectory $root -ArgumentList @(
    '-NoExit','-ExecutionPolicy','Bypass','-File',"`"$clientScript`""
)

$airgapClient = $null
$clientDeadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $clientDeadline) {
    Start-Sleep -Milliseconds 250
    $airgapClient = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Name -match '^javaw?\.exe$' -and
            $_.CommandLine -match '(?i)client-airgap\.jar' -and
            $_.ProcessId -notin $existingAirgapPids
        } |
        Select-Object -First 1

    if ($airgapClient) {
        break
    }
}

if (-not $airgapClient) {
    throw 'Airgap client did not start a new client-airgap.jar Java process within 30 seconds.'
}

Write-Host "AIRGAP_CLIENT_PROCESS_READY pid=$($airgapClient.ProcessId)" -ForegroundColor Green

Start-Sleep -Seconds 2
$stableAirgapClient = Get-CimInstance Win32_Process -Filter "ProcessId=$($airgapClient.ProcessId)" -ErrorAction SilentlyContinue
if (-not $stableAirgapClient -or
    $stableAirgapClient.Name -notmatch '^javaw?\.exe
 -or
    $stableAirgapClient.CommandLine -notmatch '(?i)client-airgap\.jar') {
    throw "Airgap client PID $($airgapClient.ProcessId) exited or changed before the stabilization check."
}

$stablePorts = @(
    Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -in $ports } |
        Select-Object -ExpandProperty LocalPort -Unique
)
if (($stablePorts -notcontains 43594) -or ($stablePorts -notcontains 43595)) {
    throw "Local server listeners were lost after client startup. Listening ports: $($stablePorts -join ',')"
}

Write-Host "AIRGAP_CLIENT_PROCESS_STABLE pid=$($stableAirgapClient.ProcessId) dwellSeconds=2" -ForegroundColor Green
Write-Host 'LOCAL_LAB_WINDOWS_STARTED_V521' -ForegroundColor Cyan
