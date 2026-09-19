param([switch]$ServerOnly,[switch]$SkipConfigPatch,[switch]$SkipSelfTest)
Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=$PSScriptRoot
. (Join-Path $repo "scripts\Select-LocalLabJava.ps1")
$java=Set-LocalLabJava
if(-not $ServerOnly){& (Join-Path $repo "scripts\Check-ExternalRuntime.ps1")}
& (Join-Path $repo "scripts\Build-Server.ps1")
if(-not $ServerOnly -and -not $SkipConfigPatch){& (Join-Path $repo "scripts\Patch-LocalConfigs.ps1")}
if(-not $SkipSelfTest){
    if(Test-Path -LiteralPath (Join-Path $repo "evidence\client(6).jar")){
        & (Join-Path $repo "RUN_REPO_SELFTEST.ps1") -Target $repo -ConfigDir (Join-Path $env:USERPROFILE ".spawnpk\configs")
        if($LASTEXITCODE -ne 0){throw "R8.5 selftest failed."}
    } elseif(-not $ServerOnly){throw "Pinned client missing; cannot run full selftest."}
}
Write-Host "BOOTSTRAP_COMPLETE" -ForegroundColor Green
Write-Host "Run: .\RUN_LOCAL_LAB.ps1" -ForegroundColor Cyan