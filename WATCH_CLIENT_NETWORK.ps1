$ErrorActionPreference = 'Stop'
Write-Host 'Watching SpawnPK AIRGAP client TCP connections only. Ctrl+C to stop.' -ForegroundColor Cyan
Write-Host 'Selector: java/javaw whose command line contains client-airgap.jar' -ForegroundColor Cyan
Write-Host 'Expected peers: 127.0.0.1 / ::1 only.' -ForegroundColor Cyan

$seen = @{}
$announced = @{}
while ($true) {
    $clients = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Name -match '^javaw?\.exe$' -and
            $_.CommandLine -match '(?i)client-airgap\.jar'
        })

    if ($clients.Count -eq 0) {
        if (-not $announced.ContainsKey('waiting')) {
            $announced['waiting'] = $true
            Write-Host 'Waiting for client-airgap.jar ...' -ForegroundColor DarkGray
        }
        Start-Sleep -Milliseconds 250
        continue
    }

    foreach ($p in $clients) {
        if (-not $announced.ContainsKey("pid:$($p.ProcessId)")) {
            $announced["pid:$($p.ProcessId)"] = $true
            Write-Host "Tracking AIRGAP PID=$($p.ProcessId)" -ForegroundColor Yellow
            Write-Host "CommandLine=$($p.CommandLine)" -ForegroundColor DarkGray
        }

        $rows = @(Get-NetTCPConnection -OwningProcess $p.ProcessId -ErrorAction SilentlyContinue |
            Where-Object { $_.State -notin @('Listen','Bound') })
        foreach ($r in $rows) {
            $key = "$($r.OwningProcess)|$($r.LocalAddress):$($r.LocalPort)|$($r.RemoteAddress):$($r.RemotePort)|$($r.State)"
            if ($seen.ContainsKey($key)) { continue }
            $seen[$key] = $true
            $loop = $r.RemoteAddress -in @('127.0.0.1','::1','0:0:0:0:0:0:0:1')
            if ($loop) {
                Write-Host "LOOPBACK  PID=$($r.OwningProcess) $($r.LocalAddress):$($r.LocalPort) -> $($r.RemoteAddress):$($r.RemotePort) $($r.State)" -ForegroundColor Green
            } else {
                Write-Host "EXTERNAL! PID=$($r.OwningProcess) $($r.LocalAddress):$($r.LocalPort) -> $($r.RemoteAddress):$($r.RemotePort) $($r.State)" -ForegroundColor Red
            }
        }
    }
    Start-Sleep -Milliseconds 250
}
