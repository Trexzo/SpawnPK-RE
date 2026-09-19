$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$jar = Join-Path $PSScriptRoot 'local-client\client-airgap.jar'
if (-not (Test-Path $jar)) { throw "Missing $jar" }
Write-Host 'Launching AIRGAP client (client binary unchanged since v0.3).' -ForegroundColor Cyan
Write-Host 'Known SpawnPK game/cache/CDN/forum/API application endpoints are rewritten to 127.0.0.1.' -ForegroundColor Cyan
Write-Host 'Use fake local credentials only.' -ForegroundColor Yellow
& java -jar $jar
