param(
    [string]$LocalLabUserHome = $env:SPAWNPK_LOCALLAB_USER_HOME
)

Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=Split-Path -Parent $PSScriptRoot

$hadCallerJavaHome = Test-Path Env:JAVA_HOME
$callerJavaHome = $env:JAVA_HOME
$callerPath = $env:Path

try {
    . (Join-Path $PSScriptRoot "Select-LocalLabJava.ps1")
$java=Set-LocalLabJava
& (Join-Path $PSScriptRoot "Check-ExternalRuntime.ps1")
Push-Location $repo
try {
    & (Join-Path $repo "RUN_CLIENT_AIRGAP.ps1") -LocalLabUserHome $LocalLabUserHome
} finally {
    Pop-Location
}
}
finally {
    if ($hadCallerJavaHome) {
        $env:JAVA_HOME = $callerJavaHome
    }
    else {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    }
    $env:Path = $callerPath
}
