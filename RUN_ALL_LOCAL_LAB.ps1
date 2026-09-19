
# R85 JAVA11+ AUTOSELECT BEGIN
$__r85Selector = Join-Path $PSScriptRoot 'tools\R85_SelectJava11Plus.ps1'
if(-not(Test-Path -LiteralPath $__r85Selector -PathType Leaf)){throw 'Missing R8.5 Java selector helper'}
. $__r85Selector
$__r85JavaInfo = Set-R85Java11Plus
Write-Host ("R85_LAUNCH_JAVA_OK major={0} path={1}" -f $__r85JavaInfo.Major,$__r85JavaInfo.Path) -ForegroundColor Green
# R85 JAVA11+ AUTOSELECT END
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
& .\VERIFY_OFFLINE_READY.ps1

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
Write-Host 'Starting localhost server in a new PowerShell...' -ForegroundColor Green
Start-Process powershell.exe -ArgumentList @(
    '-NoExit','-ExecutionPolicy','Bypass','-Command',
    "Set-Location '$root'; .\RUN_SERVER_LOCAL_WORLD.ps1"
)

$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Milliseconds 200
    if (Get-NetTCPConnection -LocalPort 43594 -State Listen -ErrorAction SilentlyContinue) {
        $ready = $true
        break
    }
}
if (-not $ready) { throw 'Local server did not begin listening on 43594.' }

Write-Host 'Starting loopback network watcher...' -ForegroundColor Green
Start-Process powershell.exe -ArgumentList @(
    '-NoExit','-ExecutionPolicy','Bypass','-Command',
    "Set-Location '$root'; .\WATCH_CLIENT_NETWORK.ps1"
)

Write-Host 'Starting airgap client...' -ForegroundColor Green
Start-Process powershell.exe -ArgumentList @(
    '-NoExit','-ExecutionPolicy','Bypass','-Command',
    "Set-Location '$root'; .\RUN_CLIENT_AIRGAP.ps1"
)

Write-Host 'LOCAL_LAB_WINDOWS_STARTED_V521' -ForegroundColor Cyan
