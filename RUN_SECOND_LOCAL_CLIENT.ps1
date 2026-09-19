param([string]$Target=$PSScriptRoot)
$ErrorActionPreference='Stop'
$lab=(Resolve-Path $Target).Path

$listen=Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object {$_.LocalPort -eq 43594} | Select-Object -First 1
if(-not $listen){
  throw "LocalLab server is not listening on 43594. Start the FIRST client normally with RUN_ALL_LOCAL_LAB.ps1, wait until opensrc is fully logged in, then run this script in a second PowerShell window."
}

$launcher=$null
foreach($candidate in @(
  (Join-Path $lab 'RUN_CLIENT_LOCALHOST_NONAIRGAP.ps1'),
  (Join-Path $lab 'RUN_CLIENT_AIRGAP.ps1')
)){
  if(Test-Path $candidate -PathType Leaf){$launcher=$candidate;break}
}
if(-not $launcher){throw "No current client-only launcher found under $lab"}

Write-Host "=== SpawnPK LocalLab second client ===" -ForegroundColor Cyan
Write-Host "Server PID : $($listen.OwningProcess)"
Write-Host "Launcher   : $launcher"
Write-Host "Account    : src (server auto-selects src when opensrc is already online)" -ForegroundColor Green
Write-Host "Do not close the first client/server window while starting this one." -ForegroundColor Yellow

Push-Location $lab
try {
  & $launcher
} finally {
  Pop-Location
}
