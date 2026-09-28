Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$hadCallerJavaHome = Test-Path Env:JAVA_HOME
$callerJavaHome = $env:JAVA_HOME
$callerPath = $env:Path
$callerLocationPushed = $false

try {
    Push-Location -LiteralPath $PSScriptRoot
    $callerLocationPushed = $true

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

function Get-NormalizedProcessLifetimeStamp {
    param([DateTime]$Timestamp)

    $utc = $Timestamp.ToUniversalTime()
    $ticks = [long]$utc.Ticks
    # Win32_Process.CreationDate is CIM_DATETIME microsecond precision.
    # Process.StartTime can be finer, so compare at the common 1 us precision.
    $ticksPerMicrosecond = 10L
    return $ticks - ($ticks % $ticksPerMicrosecond)
}

function Get-LauncherOwnedProcessRecords {
    param(
        [System.Diagnostics.Process[]]$Roots,
        [string]$Label,
        [switch]$IncludeExitedRoots
    )

    $recordedRoots = @(
        $Roots |
            Where-Object { $null -ne $_ -and $_.Id -gt 0 }
    )
    $recordedRootPids = @(
        $recordedRoots |
            ForEach-Object { [int]$_.Id } |
            Select-Object -Unique
    )
    $liveRoots = @(
        $recordedRoots |
            Where-Object { -not $_.HasExited }
    )
    if ($recordedRootPids.Count -eq 0) {
        return @()
    }

    try {
        $snapshot = @(Get-CimInstance Win32_Process -ErrorAction Stop)
    }
    catch {
        throw "$Label ownership enumeration failed: $($_.Exception.Message)"
    }

    $depthByPid = @{}
    $lifetimeStampByPid = @{}

    foreach ($liveRoot in $liveRoots) {
        $liveRootPid = [int]$liveRoot.Id
        if ($liveRootPid -eq $PID) {
            throw "$Label refused current launcher PID as an owned child root: $liveRootPid"
        }

        try {
            $recordedStartUtc = ([DateTime]$liveRoot.StartTime).ToUniversalTime()
        }
        catch {
            throw "$Label cannot prove live-root start time for PID $liveRootPid : $($_.Exception.Message)"
        }

        $currentMatches = @(
            $snapshot |
                Where-Object { [int]$_.ProcessId -eq $liveRootPid }
        )

        if ($currentMatches.Count -ne 1) {
            throw "$Label cannot prove unique current live-root identity for PID $liveRootPid count=$($currentMatches.Count)"
        }

        try {
            $currentCreatedUtc = ([DateTime]$currentMatches[0].CreationDate).ToUniversalTime()
        }
        catch {
            throw "$Label cannot prove current live-root creation time for PID $liveRootPid : $($_.Exception.Message)"
        }

        $recordedStartStamp =
            Get-NormalizedProcessLifetimeStamp -Timestamp $recordedStartUtc
        $currentCreatedStamp =
            Get-NormalizedProcessLifetimeStamp -Timestamp $currentCreatedUtc

        if ($recordedStartStamp -ne $currentCreatedStamp) {
            throw (
                "$Label refused live-root PID lifetime mismatch. " +
                "pid=$liveRootPid " +
                "recordedStart=$($recordedStartUtc.ToString('o')) " +
                "currentCreated=$($currentCreatedUtc.ToString('o')) " +
                "normalizedRecorded=$recordedStartStamp " +
                "normalizedCurrent=$currentCreatedStamp"
            )
        }

        $depthByPid[$liveRootPid] = 0
        $lifetimeStampByPid[$liveRootPid] = [long]$currentCreatedStamp
    }

    if ($IncludeExitedRoots) {
        $exitedRoots = @(
            $recordedRoots |
                Where-Object { $_.HasExited }
        )

        foreach ($exitedRoot in $exitedRoots) {
            $rootPid = [int]$exitedRoot.Id
            if ($rootPid -eq $PID) {
                throw "$Label refused current launcher PID as an exited owned root: $rootPid"
            }

            try {
                $rootStart = [DateTime]$exitedRoot.StartTime
                $rootExit = [DateTime]$exitedRoot.ExitTime
            }
            catch {
                throw "$Label cannot prove exited-root lifetime for PID $rootPid : $($_.Exception.Message)"
            }

            if ($rootExit -lt $rootStart) {
                throw "$Label found invalid exited-root lifetime for PID $rootPid"
            }

            $reused = @(
                $snapshot |
                    Where-Object { [int]$_.ProcessId -eq $rootPid }
            )
            if ($reused.Count -ne 0) {
                throw "$Label refused ambiguous exited-root PID reuse: $rootPid"
            }

            $directChildren = @(
                $snapshot |
                    Where-Object { [int]$_.ParentProcessId -eq $rootPid }
            )

            foreach ($directChild in $directChildren) {
                $childPid = [int]$directChild.ProcessId
                if ($childPid -eq $PID) {
                    throw "$Label refused current launcher PID as an exited-root descendant: $childPid"
                }

                try {
                    $childCreated = [DateTime]$directChild.CreationDate
                }
                catch {
                    throw "$Label cannot prove creation time for exited-root child PID $childPid : $($_.Exception.Message)"
                }

                if ($childCreated -lt $rootStart -or
                    $childCreated -ge $rootExit) {
                    throw (
                        "$Label refused ambiguous exited-root child lifetime. " +
                        "rootPid=$rootPid childPid=$childPid " +
                        "rootStart=$($rootStart.ToString('o')) " +
                        "rootExit=$($rootExit.ToString('o')) " +
                        "childCreated=$($childCreated.ToString('o'))"
                    )
                }

                $depthByPid[$childPid] = 1
                $lifetimeStampByPid[$childPid] =
                    Get-NormalizedProcessLifetimeStamp -Timestamp $childCreated
            }
        }
    }

    if ($depthByPid.Count -eq 0) {
        return @()
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
            try {
                $processCreated = [DateTime]$process.CreationDate
            }
            catch {
                throw "$Label cannot prove creation time for descendant PID $processId : $($_.Exception.Message)"
            }

            $depthByPid[$processId] = [int]$depthByPid[$parentId] + 1
            $lifetimeStampByPid[$processId] =
                Get-NormalizedProcessLifetimeStamp -Timestamp $processCreated
            $changed = $true
        }
    }

    $owned = @(
        foreach ($entry in $depthByPid.GetEnumerator()) {
            $ownedPid = [int]$entry.Key
            if (-not $lifetimeStampByPid.ContainsKey($ownedPid)) {
                throw "$Label has no lifetime identity for owned PID $ownedPid"
            }

            [pscustomobject]@{
                Pid = $ownedPid
                Depth = [int]$entry.Value
                LifetimeStamp = [long]$lifetimeStampByPid[$ownedPid]
            }
        }
    )

    return @(
        $owned |
            Sort-Object -Property @(
                @{ Expression = 'Depth'; Descending = $true },
                @{ Expression = 'Pid'; Descending = $true }
            )
    )
}

function Get-LauncherOwnedProcessIds {
    param(
        [System.Diagnostics.Process[]]$Roots,
        [string]$Label,
        [switch]$IncludeExitedRoots
    )

    return @(
        Get-LauncherOwnedProcessRecords @PSBoundParameters |
            ForEach-Object { [int]$_.Pid }
    )
}

function Stop-LauncherOwnedProcessTree {
    param(
        [System.Diagnostics.Process[]]$Roots,
        [string]$Label
    )

    $cleanupDeadline = (Get-Date).AddSeconds(5)
    $cleanupMaxPasses = 8
    $cleanupPass = 0
    $stoppedPidSet = @{}

    while ($cleanupPass -lt $cleanupMaxPasses -and
        (Get-Date) -lt $cleanupDeadline) {
        $cleanupPass++

        $ownedProcesses = @(
            Get-LauncherOwnedProcessRecords -Roots $Roots -Label $Label -IncludeExitedRoots
        )

        if ($ownedProcesses.Count -eq 0) {
            Write-Host (
                'LOCALLAB_OWNED_PROCESS_CLEANUP_COMPLETE ' +
                "label=$Label passes=$cleanupPass " +
                "stopped=$((@($stoppedPidSet.Keys) | Sort-Object) -join ',')"
            ) -ForegroundColor Yellow
            return
        }

        # Ownership records are descendants deepest-first and roots last.
        # Revalidate exact lifetime immediately before destructive termination.
        foreach ($ownedProcess in $ownedProcesses) {
            $ownedPid = [int]$ownedProcess.Pid
            if ($ownedPid -eq $PID) {
                throw "$Label refused current launcher PID during cleanup termination: $ownedPid"
            }

            $live = Get-Process -Id $ownedPid -ErrorAction SilentlyContinue
            if ($null -eq $live) {
                continue
            }

            try {
                $liveStartStamp =
                    Get-NormalizedProcessLifetimeStamp -Timestamp ([DateTime]$live.StartTime)
            }
            catch {
                throw "$Label cannot revalidate cleanup lifetime for PID $ownedPid : $($_.Exception.Message)"
            }

            if ($liveStartStamp -ne [long]$ownedProcess.LifetimeStamp) {
                throw (
                    "$Label refused cleanup PID lifetime mismatch. " +
                    "pid=$ownedPid " +
                    "ownedStamp=$($ownedProcess.LifetimeStamp) " +
                    "currentStamp=$liveStartStamp"
                )
            }

            Stop-Process -InputObject $live -Force -ErrorAction Stop
            $stoppedPidSet[$ownedPid] = $true
        }

        Start-Sleep -Milliseconds 100
    }

    $remainingOwnedProcesses = @(
        Get-LauncherOwnedProcessRecords -Roots $Roots -Label $Label -IncludeExitedRoots
    )

    $remainingLive = New-Object 'System.Collections.Generic.List[object]'
    foreach ($ownedProcess in $remainingOwnedProcesses) {
        $ownedPid = [int]$ownedProcess.Pid
        $live = Get-Process -Id $ownedPid -ErrorAction SilentlyContinue
        if ($null -eq $live) {
            continue
        }

        $liveStartStamp =
            Get-NormalizedProcessLifetimeStamp -Timestamp ([DateTime]$live.StartTime)
        if ($liveStartStamp -ne [long]$ownedProcess.LifetimeStamp) {
            throw (
                "$Label refused final cleanup PID lifetime mismatch. " +
                "pid=$ownedPid " +
                "ownedStamp=$($ownedProcess.LifetimeStamp) " +
                "currentStamp=$liveStartStamp"
            )
        }

        $remainingLive.Add($ownedProcess)
    }

    if ($remainingLive.Count -ne 0) {
        $remainingPids = @(
            $remainingLive |
                ForEach-Object { [int]$_.Pid }
        )
        throw (
            "$Label launcher-owned cleanup did not converge. " +
            "passes=$cleanupPass remaining=$($remainingPids -join ',')"
        )
    }

    Write-Host (
        'LOCALLAB_OWNED_PROCESS_CLEANUP_COMPLETE ' +
        "label=$Label passes=$cleanupPass " +
        "stopped=$((@($stoppedPidSet.Keys) | Sort-Object) -join ',')"
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

        try {
            $admittedConflictStamp =
                Get-NormalizedProcessLifetimeStamp -Timestamp ([DateTime]$p.CreationDate)
        }
        catch {
            throw "Cannot prove admitted conflicting $label lifetime for PID $ownerPid : $($_.Exception.Message)"
        }

        $currentConflict = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
        if ($null -eq $currentConflict) {
            continue
        }

        try {
            $currentConflictStamp =
                Get-NormalizedProcessLifetimeStamp -Timestamp ([DateTime]$currentConflict.StartTime)
        }
        catch {
            throw "Cannot revalidate conflicting $label lifetime for PID $ownerPid : $($_.Exception.Message)"
        }

        if ($currentConflictStamp -ne $admittedConflictStamp) {
            throw (
                "Refused conflicting $label PID lifetime mismatch. " +
                "pid=$ownerPid " +
                "admittedStamp=$admittedConflictStamp " +
                "currentStamp=$currentConflictStamp"
            )
        }

        Write-Host "Stopping conflicting $label server PID $ownerPid..." -ForegroundColor Yellow
        Stop-Process -InputObject $currentConflict -Force -ErrorAction Stop
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
