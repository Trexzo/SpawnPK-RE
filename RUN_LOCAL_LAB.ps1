Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = $PSScriptRoot
$runtimeCheck = Join-Path $repo 'scripts\Check-ExternalRuntime.ps1'
$serverScript = Join-Path $repo 'scripts\Run-Server.ps1'
$clientScript = Join-Path $repo 'scripts\Run-Client-Airgap.ps1'
$serverJar = Join-Path $repo 'server\build\SpawnPKLocalServer.jar'

function Stop-LauncherOwnedProcessTree {
    param(
        [System.Diagnostics.Process[]]$Roots,
        [string]$Label
    )

    $rootPids = @(
        $Roots |
            Where-Object { $null -ne $_ -and $_.Id -gt 0 } |
            ForEach-Object { [int]$_.Id } |
            Select-Object -Unique
    )

    if ($rootPids.Count -eq 0) {
        return
    }

    try {
        $snapshot = @(
            Get-CimInstance Win32_Process -ErrorAction Stop
        )
    }
    catch {
        throw "$Label cleanup cannot prove launcher-owned descendants because Win32_Process enumeration failed: $($_.Exception.Message)"
    }

    $depthByPid = @{}
    foreach ($rootPid in $rootPids) {
        if ($rootPid -eq $PID) {
            throw "$Label cleanup refused to treat the current launcher PID as an owned child: $rootPid"
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

    $ownedPids = @(
        $depthByPid.GetEnumerator() |
            Sort-Object Value -Descending |
            ForEach-Object { [int]$_.Key }
    )

    foreach ($ownedPid in $ownedPids) {
        Stop-Process -Id $ownedPid -Force -ErrorAction SilentlyContinue
    }

    foreach ($rootPid in $rootPids) {
        Wait-Process -Id $rootPid -Timeout 5 -ErrorAction SilentlyContinue
    }

    Write-Host (
        'LOCALLAB_OWNED_PROCESS_CLEANUP_COMPLETE ' +
        "label=$Label roots=$($rootPids -join ',') " +
        "owned=$($ownedPids -join ',')"
    ) -ForegroundColor Yellow
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

$serverWindow = $null

try {
    $serverWindow = Start-Process powershell.exe -WorkingDirectory $repo -ArgumentList @(
        '-NoExit',
        '-ExecutionPolicy',
        'Bypass',
        '-File',
        ('"' + $serverScript + '"')
    ) -PassThru

    $ready = $false$ready = $false
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

    $readyPorts = @(
        $connections |
            Select-Object -ExpandProperty LocalPort -Unique
    )
    $readyOwnerPids = @(
        $connections |
            Select-Object -ExpandProperty OwningProcess -Unique
    )

    if ($readyOwnerPids.Count -gt 1) {
        throw "LocalLab quick-start found multiple owners across 43594/43595: $($readyOwnerPids -join ',')"
    }

    if ($readyOwnerPids.Count -eq 1) {
        $candidatePid = [int]$readyOwnerPids[0]
        $candidate = Get-CimInstance Win32_Process -Filter "ProcessId=$candidatePid" -ErrorAction SilentlyContinue

        if (-not $candidate -or
            $candidate.Name -notmatch '^javaw?\.exe$' -or
            $candidate.CommandLine -notmatch 'SpawnPKLocalServer|spk\.local\.Main|SpawnPK-LocalLab') {
            $name = if ($candidate) { $candidate.Name } else { '<missing>' }
            $command = if ($candidate) { $candidate.CommandLine } else { '<missing>' }
            throw "LocalLab quick-start listener owner PID $candidatePid is not an expected SpawnPK LocalLab Java server. Name=$name Command=$command"
        }

        if (($readyPorts -contains 43594) -and
            ($readyPorts -contains 43595)) {
            $serverPid = $candidatePid
            $ready = $true
            break
        }
    }
}

if (-not $ready) {
    throw "Local server did not establish both 43594/43595 on one expected LocalLab Java process within 30 seconds. Ready ports: $($readyPorts -join ',') owners: $($readyOwnerPids -join ',')"
}

    Write-Host "QUICKSTART_SERVER_PROCESS_READY pid=$serverPid game=43594 aux=43595" -ForegroundColor Green
    Write-Host 'QUICKSTART_SERVER_PORTS_READY game=43594 aux=43595' -ForegroundColor Green

    & $clientScript
}
catch {
    $primaryFailure = $_

    try {
        Stop-LauncherOwnedProcessTree -Roots @($serverWindow) -Label 'RUN_LOCAL_LAB'
    }
    catch {
        Write-Warning "RUN_LOCAL_LAB owned-process cleanup also failed: $($_.Exception.Message)"
    }

    throw $primaryFailure
}
