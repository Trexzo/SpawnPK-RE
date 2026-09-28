param(
    [string]$LocalLabUserHome = $env:SPAWNPK_LOCALLAB_USER_HOME
)

Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "Select-LocalLabJava.ps1")
$java=Set-LocalLabJava
& (Join-Path $PSScriptRoot "Check-ExternalRuntime.ps1")
Push-Location $repo
try {
    & (Join-Path $repo "RUN_CLIENT_AIRGAP.ps1") -LocalLabUserHome $LocalLabUserHome
} finally {
    Pop-Location
}