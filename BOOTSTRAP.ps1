param(
    [switch]$ServerOnly,
    [switch]$SkipConfigPatch,
    [switch]$SkipSelfTest
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = $PSScriptRoot

$hadCallerJavaHome = Test-Path Env:JAVA_HOME
$callerJavaHome = $env:JAVA_HOME
$callerPath = $env:Path

try {
    . (Join-Path $repo 'scripts\Select-LocalLabJava.ps1')
    $java = Set-LocalLabJava

if (-not $ServerOnly) {
    & (Join-Path $repo 'scripts\Check-ExternalRuntime.ps1')
}

& (Join-Path $repo 'scripts\Build-Server.ps1')

if (-not $ServerOnly) {
    Write-Host (
        'BOOTSTRAP_CONFIG_PATCH_RETIRED ' +
        'liveConfigMutation=false ' +
        'isolatedCachePipelineRequired=true ' +
        "legacySkipSwitch=$([bool]$SkipConfigPatch)"
    ) -ForegroundColor Yellow
}

if (-not $SkipSelfTest) {
    if (Test-Path -LiteralPath (Join-Path $repo 'evidence\client(6).jar')) {
        $selftest = Join-Path $repo 'RUN_REPO_SELFTEST.ps1'
        $configDir = Join-Path $env:USERPROFILE '.spawnpk\configs'
        & $selftest -Target $repo -ConfigDir $configDir

        if ($LASTEXITCODE -ne 0) {
            throw 'R8.5 selftest failed.'
        }
    }
    elseif (-not $ServerOnly) {
        throw 'Pinned client missing; cannot run full selftest.'
    }
}

Write-Host 'BOOTSTRAP_COMPLETE' -ForegroundColor Green
Write-Host 'Run: .\RUN_LOCAL_LAB.ps1' -ForegroundColor Cyan

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
