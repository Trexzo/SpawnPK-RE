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

$root = $PSScriptRoot
$serverScript = Join-Path $root 'scripts\Run-Server.ps1'
$watcherScript = Join-Path $root 'WATCH_CLIENT_NETWORK.ps1'
$clientScript = Join-Path $root 'scripts\Run-Client-Airgap.ps1'
$serverJar = Join-Path $root 'server\build\SpawnPKLocalServer.jar'

function Get-LauncherOwnedProcessIds {
    param(
        [System.Diagnostics.Process[]]$Roots,
        [string]$Label
    )

    $rootPids = @(
        $Roots |
            Where-Object { $null -ne $_ -and $_.Id -gt 0 -and -not $_.HasExited } |
            ForEach-Object { [int]$_.Id } |
            Select-Object -Unique
    )

    if ($rootPids.Count -eq 0) {
        return @()
    }

    try {
        $snapshot = @(Get-CimInstance Win32_Process -ErrorAction Stop)
    }
    catch {
        throw "$Label ownership enumeration failed: $($_.Exception.Message)"
    }

    $depthByPid = @{}
    foreach ($rootPid in $rootPids) {
        if ($rootPid -eq $PID) {
            throw "$Label refused current launcher PID as an owned child root: $rootPid"
        }
        $depthByPid[$rootPid] = 0
    }

    $changed = $true
    while ($changed) {
        $changed = $false
        foreach ($process in $snapshot) {
            $processId = [int]$process.ProcessId
            $parentId = [int]$process.ParentProcessId
            if ($processId -eq $PID -or
                $depthByPid.ContainsKey($processId) -or
                -not $depthByPid.ContainsKey($parentId)) {
                continue
            }
            $depthByPid[$processId] = [int]$depthByPid[$parentId] + 1
            $changed = $true
        }
    }

    return @(
        $depthByPid.GetEnumerator() |
            Sort-Object Value -Descending |
            ForEach-Object { [int]$_.Key }
    )
}

function Stop-LauncherOwnedProcessTree {
    param(
        [System.Diagnostics.Process[]]$Roots,
        [string]$Label
    )

    $ownedPids = @(Get-LauncherOwnedProcessIds -Roots $Roots -Label $Label)
    foreach ($ownedPid in $ownedPids) {
        $live = Get-Process -Id $ownedPid -ErrorAction SilentlyContinue
        if ($null -eq $live) {
            continue
        }
        Stop-Process -Id $ownedPid -Force -ErrorAction Stop
    }

    Write-Host (
        'LOCALLAB_OWNED_PROCESS_CLEANUP_COMPLETE ' +
        "label=$Label owned=$($ownedPids -join ',')"
    ) -ForegroundColor Yellow
}

function Throw-LauncherFailureWithCleanup {
    param(
        [System.Management.Automation.ErrorRecord]$PrimaryFailure,
        [System.Management.Automation.ErrorRecord]$CleanupFailure,
        [string]$Label
    )

    if ($null -eq $CleanupFailure) {
        throw $PrimaryFailure
    }

    $message = (
        "$Label failed and launcher-owned cleanup was incomplete. " +
        "Primary: $($PrimaryFailure.Exception.Message) " +
        "Cleanup: $($CleanupFailure.Exception.Message)"
    )
    throw [System.Exception]::new($message, $PrimaryFailure.Exception)
}


if (-not (Test-Path -LiteralPath $serverJar -PathType Leaf)) {
    throw "Missing current LocalLab server JAR: $serverJar. Run .\BOOTSTRAP.ps1 or .\scripts\Build-Server.ps1 first."
}

foreach ($required in @($serverScript,$watcherScript,$clientScript)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing LocalLab launcher component: $required"
    }
}

Write-Host 'LOCAL_LAB_REPLACEMENT_PREFLIGHT_PASS serverJar=true launchers=3' -ForegroundColor Green

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

$ownedChildren = @()

try {
    Write-Host 'Starting localhost server in a new PowerShell...' -ForegroundColor Green
    $serverWindow = Start-Process powershell.exe -WorkingDirectory $root -ArgumentList @(
        '-NoExit','-ExecutionPolicy','Bypass','-File',"`"$serverScript`""
    ) -PassThru
    $ownedChildren += $serverWindow

    $ready = $false
    $serverOwnerPid = $null
    $readyDeadline = (Get-Date).AddSeconds(30)
    $readyPortNumbers = @()
    $readyOwnerPids = @()
    while ((Get-Date) -lt $readyDeadline) {
        Start-Sleep -Milliseconds 250
        $readyConnections = @(
            Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
                Where-Object { $_.LocalPort -in $ports }
        )
        $readyPortNumbers = @($readyConnections | Select-Object -ExpandProperty LocalPort -Unique)
        $readyOwnerPids = @($readyConnections | Select-Object -ExpandProperty OwningProcess -Unique)

        if (($readyPortNumbers -contains 43594) -and
            ($readyPortNumbers -contains 43595) -and
            $readyOwnerPids.Count -eq 1) {
            $candidateServerPid = [int]$readyOwnerPids[0]
            $candidateServer = Get-CimInstance Win32_Process -Filter "ProcessId=$candidateServerPid" -ErrorAction SilentlyContinue
            $serverOwnedPids = @(Get-LauncherOwnedProcessIds -Roots @($serverWindow) -Label 'RUN_ALL_LOCAL_LAB server')
            if ($candidateServer -and
                $candidateServer.Name -match '^javaw?\.exe$' -and
                $candidateServer.CommandLine -match 'SpawnPKLocalServer|spk\.local\.Main|SpawnPK-LocalLab' -and
                $candidateServerPid -in $serverOwnedPids) {
                $serverOwnerPid = $candidateServerPid
                $ready = $true
                break
            }
        }
    }
    if (-not $ready) {
        throw "Local server did not establish both 43594/43595 on one launcher-owned LocalLab Java process within 30 seconds. Ready ports: $($readyPortNumbers -join ',') owners: $($readyOwnerPids -join ',')"
    }
    Write-Host "SERVER_PROCESS_READY pid=$serverOwnerPid rootPid=$($serverWindow.Id) game=43594 aux=43595" -ForegroundColor Green
    Write-Host 'SERVER_PORTS_READY game=43594 aux=43595' -ForegroundColor Green

    Write-Host 'Starting loopback network watcher...' -ForegroundColor Green
    $watcherWindow = Start-Process powershell.exe -WorkingDirectory $root -ArgumentList @(
        '-NoExit','-ExecutionPolicy','Bypass','-File',"`"$watcherScript`""
    ) -PassThru
    $ownedChildren += $watcherWindow

    Write-Host 'Starting airgap client...' -ForegroundColor Green
    $clientWindow = Start-Process powershell.exe -WorkingDirectory $root -ArgumentList @(
        '-NoExit','-ExecutionPolicy','Bypass','-File',"`"$clientScript`""
    ) -PassThru
    $ownedChildren += $clientWindow

    $airgapClient = $null
    $clientDeadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $clientDeadline) {
        Start-Sleep -Milliseconds 250
        $clientOwnedPids = @(Get-LauncherOwnedProcessIds -Roots @($clientWindow) -Label 'RUN_ALL_LOCAL_LAB client')
        $clientCandidates = @(
            Get-CimInstance Win32_Process -ErrorAction Stop |
                Where-Object {
                    $_.ProcessId -in $clientOwnedPids -and
                    $_.Name -match '^javaw?\.exe$' -and
                    $_.CommandLine -match '(?i)client-airgap\.jar'
                }
        )
        if ($clientCandidates.Count -gt 1) {
            throw "Multiple airgap Java descendants found for client window PID $($clientWindow.Id): $(@($clientCandidates.ProcessId) -join ',')"
        }
        if ($clientCandidates.Count -eq 1) {
            $airgapClient = $clientCandidates[0]
            break
        }
    }

    if ($null -eq $airgapClient) {
        throw "Client window PID $($clientWindow.Id) did not produce one owned client-airgap.jar Java descendant within 30 seconds."
    }

    Write-Host "AIRGAP_CLIENT_PROCESS_READY pid=$($airgapClient.ProcessId) rootPid=$($clientWindow.Id)" -ForegroundColor Green

    Start-Sleep -Seconds 2
    $stableAirgapClient = Get-CimInstance Win32_Process -Filter "ProcessId=$($airgapClient.ProcessId)" -ErrorAction SilentlyContinue
    $stableClientOwnedPids = @(Get-LauncherOwnedProcessIds -Roots @($clientWindow) -Label 'RUN_ALL_LOCAL_LAB client stability')
    if (-not $stableAirgapClient -or
        $stableAirgapClient.Name -notmatch '^javaw?\.exe$' -or
        $stableAirgapClient.CommandLine -notmatch '(?i)client-airgap\.jar' -or
        [int]$stableAirgapClient.ProcessId -notin $stableClientOwnedPids) {
        throw "Airgap client PID $($airgapClient.ProcessId) exited, changed, or escaped recorded client-window ownership before stabilization."
    }

    $stableServerConnections = @(
        Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $_.LocalPort -in $ports }
    )
    $stablePorts = @($stableServerConnections | Select-Object -ExpandProperty LocalPort -Unique)
    $stableOwnerPids = @($stableServerConnections | Select-Object -ExpandProperty OwningProcess -Unique)
    $stableServerOwnedPids = @(Get-LauncherOwnedProcessIds -Roots @($serverWindow) -Label 'RUN_ALL_LOCAL_LAB server stability')
    if (($stablePorts -notcontains 43594) -or
        ($stablePorts -notcontains 43595) -or
        $stableOwnerPids.Count -ne 1 -or
        [int]$stableOwnerPids[0] -ne $serverOwnerPid -or
        $serverOwnerPid -notin $stableServerOwnedPids) {
        throw "Local server listener ownership changed after client startup. Ports: $($stablePorts -join ',') owners: $($stableOwnerPids -join ',') expectedOwner=$serverOwnerPid"
    }

    Write-Host "AIRGAP_CLIENT_PROCESS_STABLE pid=$($stableAirgapClient.ProcessId) rootPid=$($clientWindow.Id) dwellSeconds=2" -ForegroundColor Green
    Write-Host 'LOCAL_LAB_WINDOWS_STARTED_V521' -ForegroundColor Cyan
}
catch {
    $primaryFailure = $_
    $cleanupFailure = $null
    try {
        Stop-LauncherOwnedProcessTree -Roots $ownedChildren -Label 'RUN_ALL_LOCAL_LAB'
    }
    catch {
        $cleanupFailure = $_
    }
    Throw-LauncherFailureWithCleanup -PrimaryFailure $primaryFailure -CleanupFailure $cleanupFailure -Label 'RUN_ALL_LOCAL_LAB'
}
