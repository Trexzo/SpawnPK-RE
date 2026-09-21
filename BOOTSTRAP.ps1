param([switch]$ServerOnly,[switch]$SkipConfigPatch,[switch]$SkipSelfTest)
Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=$PSScriptRoot

. (Join-Path $repo "scripts\LocalLab-Visuals.ps1")
Set-LocalLabWindowTitle -Title "Bootstrap"
Write-LocalLabHeader -Phase "BOOTSTRAP"
Write-LocalLabFlavor -Stage "Bootstrap"

. (Join-Path $repo "scripts\Select-LocalLabJava.ps1")
Write-LocalLabStatus -Label "JAVA" -Value "selecting proven LocalLab runtime" -Color Cyan
$java=Set-LocalLabJava

if(-not $ServerOnly){
    Write-LocalLabStatus -Label "RUNTIME" -Value "verifying external client/runtime" -Color Cyan
    & (Join-Path $repo "scripts\Check-ExternalRuntime.ps1")
}

Write-LocalLabStatus -Label "BUILD" -Value "building server with repository toolchain" -Color Yellow
& (Join-Path $repo "scripts\Build-Server.ps1")

if(-not $ServerOnly -and -not $SkipConfigPatch){
    Write-LocalLabStatus -Label "CONFIG" -Value "backing up and patching local metadata" -Color Yellow
    & (Join-Path $repo "scripts\Patch-LocalConfigs.ps1")
}

if(-not $SkipSelfTest){
    Write-LocalLabFlavor -Stage "SelfTest"
    if(Test-Path -LiteralPath (Join-Path $repo "evidence\client(6).jar")){
        Write-LocalLabStatus -Label "SELFTEST" -Value "running inherited R8.5 regression gate" -Color Yellow
        & (Join-Path $repo "RUN_REPO_SELFTEST.ps1") -Target $repo -ConfigDir (Join-Path $env:USERPROFILE ".spawnpk\configs")
        if($LASTEXITCODE -ne 0){throw "R8.5 selftest failed."}
    } elseif(-not $ServerOnly){throw "Pinned client missing; cannot run full selftest."}
}

Write-Host "BOOTSTRAP_COMPLETE" -ForegroundColor Green
Write-LocalLabStatus -Label "BOOTSTRAP" -Value "complete" -Color Green
Write-Host "Run: .\RUN_LOCAL_LAB.ps1" -ForegroundColor Cyan
