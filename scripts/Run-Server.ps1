Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=Split-Path -Parent $PSScriptRoot

. (Join-Path $PSScriptRoot "LocalLab-Visuals.ps1")
Set-LocalLabWindowTitle -Title "Server"
Write-LocalLabHeader -Phase "SERVER"

. (Join-Path $PSScriptRoot "Select-LocalLabJava.ps1")
$java=Set-LocalLabJava
$jar=Join-Path $repo "server\build\SpawnPKLocalServer.jar"

Write-LocalLabStatus -Label "JAVA" -Value ("major {0}" -f $java.Major) -Color Green
Write-LocalLabStatus -Label "SERVER" -Value "bootstrapping local world + movement" -Color Cyan

Push-Location $repo
try { & $java.Path -jar $jar --bootstrap --movement } finally { Pop-Location }
