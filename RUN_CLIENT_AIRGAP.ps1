Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# Keep the standalone legacy launcher deterministic too. The canonical
# scripts/Run-Client-Airgap.ps1 already selects Java before delegating here,
# but users and older tooling may still invoke this file directly.
$selector = Join-Path $PSScriptRoot 'scripts\Select-LocalLabJava.ps1'
if (-not (Test-Path -LiteralPath $selector -PathType Leaf)) {
    throw "Missing LocalLab Java selector: $selector"
}
. $selector
$java = Set-LocalLabJava

$jar = Join-Path $PSScriptRoot 'local-client\client-airgap.jar'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Missing $jar" }

Write-Host 'Launching AIRGAP client (client binary unchanged since v0.3).' -ForegroundColor Cyan
Write-Host 'Known SpawnPK game/cache/CDN/forum/API application endpoints are rewritten to 127.0.0.1.' -ForegroundColor Cyan
Write-Host 'Use fake local credentials only.' -ForegroundColor Yellow

& $java.Path -jar $jar
if ($LASTEXITCODE -ne 0) {
    throw "AIRGAP client exited with code $LASTEXITCODE using $($java.Path)"
}
