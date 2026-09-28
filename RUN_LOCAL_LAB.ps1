Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = $PSScriptRoot
$runtimeCheck = Join-Path $repo 'scripts\Check-ExternalRuntime.ps1'
$serverScript = Join-Path $repo 'scripts\Run-Server.ps1'
$clientScript = Join-Path $repo 'scripts\Run-Client-Airgap.ps1'
$serverJar = Join-Path $repo 'server\build\SpawnPKLocalServer.jar'

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


foreach ($required in @(
    $runtimeCheck,
    $serverScript,
    $clientScript,
    $serverJar
)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing LocalLab quick-start component: $required"
    }
}

& $runtimeCheck

$ports = 43594, 43595
$busy = @(
    Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -in $ports }
)

if ($busy.Count -ne 0) {
    $busy | Format-Table LocalAddress, LocalPort, OwningProcess -AutoSize
    throw 'LocalLab ports 43594/43595 are already occupied.'
}

$ownedChildren = @()

try {
    $serverWindow = Start-Process powershell.exe -WorkingDirectory $repo -ArgumentList @(
        '-NoExit',
        '-ExecutionPolicy',
        'Bypass',
        '-File',
        ('"' + $serverScript + '"')
    ) -PassThru
    $ownedChildren += $serverWindow

    $ready = $false
    $serverPid = $null
    $readyPorts = @()
    $readyOwnerPids = @()
    $deadline = (Get-Date).AddSeconds(30)

    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 250

        $connections = @(
            Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
                Where-Object { $_.LocalPort -in $ports }
        )
        $readyPorts = @($connections | Select-Object -ExpandProperty LocalPort -Unique)
        $readyOwnerPids = @($connections | Select-Object -ExpandProperty OwningProcess -Unique)

        if ($readyOwnerPids.Count -gt 1) {
            throw "LocalLab quick-start found multiple owners across 43594/43595: $($readyOwnerPids -join ',')"
        }

        if ($readyOwnerPids.Count -eq 1) {
            $candidatePid = [int]$readyOwnerPids[0]
            $candidate = Get-CimInstance Win32_Process -Filter "ProcessId=$candidatePid" -ErrorAction SilentlyContinue
            $serverOwnedPids = @(Get-LauncherOwnedProcessIds -Roots @($serverWindow) -Label 'RUN_LOCAL_LAB server')

            if (-not $candidate -or
                $candidate.Name -notmatch '^javaw?\.exe$' -or
                $candidate.CommandLine -notmatch 'SpawnPKLocalServer|spk\.local\.Main|SpawnPK-LocalLab' -or
                $candidatePid -notin $serverOwnedPids) {
                $name = if ($candidate) { $candidate.Name } else { '<missing>' }
                $command = if ($candidate) { $candidate.CommandLine } else { '<missing>' }
                throw "LocalLab quick-start listener owner PID $candidatePid is not the expected launcher-owned SpawnPK LocalLab Java server. Name=$name Command=$command"
            }

            if (($readyPorts -contains 43594) -and ($readyPorts -contains 43595)) {
                $serverPid = $candidatePid
                $ready = $true
                break
            }
        }
    }

    if (-not $ready) {
        throw "Local server did not establish both 43594/43595 on one launcher-owned LocalLab Java process within 30 seconds. Ready ports: $($readyPorts -join ',') owners: $($readyOwnerPids -join ',')"
    }

    Write-Host "QUICKSTART_SERVER_PROCESS_READY pid=$serverPid game=43594 aux=43595" -ForegroundColor Green
    Write-Host 'QUICKSTART_SERVER_PORTS_READY game=43594 aux=43595' -ForegroundColor Green

    $clientWindow = Start-Process powershell.exe -WorkingDirectory $repo -ArgumentList @(
        '-NoExit',
        '-ExecutionPolicy',
        'Bypass',
        '-File',
        ('"' + $clientScript + '"')
    ) -PassThru
    $ownedChildren += $clientWindow

    $airgapClient = $null
    $clientDeadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $clientDeadline) {
        Start-Sleep -Milliseconds 250
        $clientOwnedPids = @(Get-LauncherOwnedProcessIds -Roots @($clientWindow) -Label 'RUN_LOCAL_LAB client')
        $clientCandidates = @(
            Get-CimInstance Win32_Process -ErrorAction Stop |
                Where-Object {
                    $_.ProcessId -in $clientOwnedPids -and
                    $_.Name -match '^javaw?\.exe$' -and
                    $_.CommandLine -match '(?i)client-airgap\.jar'
                }
        )
        if ($clientCandidates.Count -gt 1) {
            throw "Quick-start found multiple airgap Java descendants for client window PID $($clientWindow.Id): $(@($clientCandidates.ProcessId) -join ',')"
        }
        if ($clientCandidates.Count -eq 1) {
            $airgapClient = $clientCandidates[0]
            break
        }
    }

    if ($null -eq $airgapClient) {
        throw "Quick-start client window PID $($clientWindow.Id) did not produce one owned client-airgap.jar Java descendant within 30 seconds."
    }

    Start-Sleep -Seconds 2
    $stableClient = Get-CimInstance Win32_Process -Filter "ProcessId=$($airgapClient.ProcessId)" -ErrorAction SilentlyContinue
    $stableClientOwnedPids = @(Get-LauncherOwnedProcessIds -Roots @($clientWindow) -Label 'RUN_LOCAL_LAB client stability')
    if (-not $stableClient -or
        $stableClient.Name -notmatch '^javaw?\.exe$' -or
        $stableClient.CommandLine -notmatch '(?i)client-airgap\.jar' -or
        [int]$stableClient.ProcessId -notin $stableClientOwnedPids) {
        throw "Quick-start airgap client PID $($airgapClient.ProcessId) exited, changed, or escaped recorded client-window ownership before stabilization."
    }

    $stableServerConnections = @(
        Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $_.LocalPort -in $ports }
    )
    $stablePorts = @($stableServerConnections | Select-Object -ExpandProperty LocalPort -Unique)
    $stableOwnerPids = @($stableServerConnections | Select-Object -ExpandProperty OwningProcess -Unique)
    if (($stablePorts -notcontains 43594) -or
        ($stablePorts -notcontains 43595) -or
        $stableOwnerPids.Count -ne 1 -or
        [int]$stableOwnerPids[0] -ne $serverPid) {
        throw "Quick-start LocalLab listener ownership changed after client startup. Ports: $($stablePorts -join ',') owners: $($stableOwnerPids -join ',') expectedOwner=$serverPid"
    }

    Write-Host "QUICKSTART_AIRGAP_CLIENT_PROCESS_STABLE pid=$($stableClient.ProcessId) rootPid=$($clientWindow.Id) dwellSeconds=2" -ForegroundColor Green
    Write-Host "QUICKSTART_LOCAL_LAB_HEALTHY serverPid=$serverPid clientPid=$($stableClient.ProcessId)" -ForegroundColor Cyan
}
catch {
    $primaryFailure = $_
    $cleanupFailure = $null
    try {
        Stop-LauncherOwnedProcessTree -Roots $ownedChildren -Label 'RUN_LOCAL_LAB'
    }
    catch {
        $cleanupFailure = $_
    }
    Throw-LauncherFailureWithCleanup -PrimaryFailure $primaryFailure -CleanupFailure $cleanupFailure -Label 'RUN_LOCAL_LAB'
}
