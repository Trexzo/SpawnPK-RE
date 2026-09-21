Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=$PSScriptRoot
& (Join-Path $repo "scripts\Check-ExternalRuntime.ps1")
$busy=@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object {$_.LocalPort -in 43594,43595})
if($busy){$busy | Format-Table LocalAddress,LocalPort,OwningProcess -AutoSize; throw "LocalLab ports already occupied."}
$server=Join-Path $repo "scripts\Run-Server.ps1"
Start-Process powershell.exe -WorkingDirectory $repo -ArgumentList @("-NoExit","-ExecutionPolicy","Bypass","-File","`"$server`"")
$deadline=(Get-Date).AddSeconds(30); $ready=$false
while((Get-Date)-lt $deadline){
    Start-Sleep -Milliseconds 250
    if(Get-NetTCPConnection -State Listen -LocalPort 43594 -ErrorAction SilentlyContinue){$ready=$true;break}
}
if(-not $ready){throw "Server did not listen on 43594. Check the server PowerShell window."}
Write-Host "SERVER_PORT_43594_READY" -ForegroundColor Green
& (Join-Path $repo "scripts\Run-Client-Airgap.ps1")