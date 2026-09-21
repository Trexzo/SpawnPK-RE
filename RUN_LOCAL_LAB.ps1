Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=$PSScriptRoot

. (Join-Path $repo "scripts\LocalLab-Visuals.ps1")
Set-LocalLabWindowTitle -Title "Launch"
Write-LocalLabHeader -Phase "LAUNCH"
Write-LocalLabFlavor -Stage "Launch"

Write-LocalLabStatus -Label "RUNTIME" -Value "verifying external client/runtime hashes" -Color Cyan
& (Join-Path $repo "scripts\Check-ExternalRuntime.ps1")

Write-LocalLabStatus -Label "PORTS" -Value "checking 43594 / 43595" -Color Cyan
$busy=@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object {$_.LocalPort -in 43594,43595})
if($busy){
    Write-LocalLabStatus -Label "PORTS" -Value "occupied - refusing ambiguous launch" -Color Red
    $busy | Format-Table LocalAddress,LocalPort,OwningProcess -AutoSize
    throw "LocalLab ports already occupied."
}

$server=Join-Path $repo "scripts\Run-Server.ps1"
Write-LocalLabStatus -Label "SERVER" -Value "starting isolated server window" -Color Yellow
$quotedServer='"' + $server + '"'
Start-Process powershell.exe -WorkingDirectory $repo -ArgumentList @("-NoExit","-ExecutionPolicy","Bypass","-File",$quotedServer)

$deadline=(Get-Date).AddSeconds(30); $ready=$false
while((Get-Date)-lt $deadline){
    Start-Sleep -Milliseconds 250
    if(Get-NetTCPConnection -State Listen -LocalPort 43594 -ErrorAction SilentlyContinue){$ready=$true;break}
}
if(-not $ready){
    Write-LocalLabStatus -Label "SERVER" -Value "did not bind 43594 before deadline" -Color Red
    throw "Server did not listen on 43594. Check the server PowerShell window."
}

Write-Host "SERVER_PORT_43594_READY" -ForegroundColor Green
Write-LocalLabStatus -Label "SERVER" -Value "127.0.0.1:43594 ready" -Color Green
Write-LocalLabFlavor -Stage "Ready"

Write-LocalLabStatus -Label "CLIENT" -Value "starting air-gapped local client" -Color Cyan
& (Join-Path $repo "scripts\Run-Client-Airgap.ps1")
