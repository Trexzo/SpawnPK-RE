$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$jar = Join-Path $PSScriptRoot 'local-client\client-localhost.jar'
if (-not (Test-Path $jar)) { throw "Missing $jar" }
Write-Host 'Launching localhost game client. NOTE: this copy still contains external web/CDN endpoints.' -ForegroundColor Yellow
& java -jar $jar
