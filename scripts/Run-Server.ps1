Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "Select-LocalLabJava.ps1")
$java=Set-LocalLabJava
$jar=Join-Path $repo "server\build\SpawnPKLocalServer.jar"
Push-Location $repo
try { & $java.Path -jar $jar --bootstrap --movement } finally { Pop-Location }