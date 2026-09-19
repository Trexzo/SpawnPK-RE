param([string]$ConfigDir=(Join-Path $env:USERPROFILE ".spawnpk\configs"))
Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "Select-LocalLabJava.ps1")
$java=Set-LocalLabJava
$jar=Join-Path $repo "server\build\SpawnPKLocalServer.jar"
foreach($n in @("i.bin","e.bin")){
    $p=Join-Path $ConfigDir $n
    if(-not(Test-Path -LiteralPath $p -PathType Leaf)){throw "Required config missing: $p"}
}
$backup=Join-Path $repo (".runtime-backups\config-"+(Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $backup | Out-Null
foreach($n in @("i.bin","e.bin")){Copy-Item (Join-Path $ConfigDir $n) (Join-Path $backup $n) -Force}
Write-Host "CONFIG_BACKUP $backup" -ForegroundColor Cyan
& $java.Path -cp $jar spk.local.VoidglassR3ConfigPatchTool preflight $ConfigDir
if($LASTEXITCODE -ne 0){throw "Voidglass R3 preflight failed."}
try {
    & $java.Path -cp $jar spk.local.VoidglassR3ConfigPatchTool patch $ConfigDir
    if($LASTEXITCODE -ne 0){throw "Voidglass R3 patch failed."}
    & $java.Path -cp $jar spk.local.VoidglassR3ConfigPatchTool verify $ConfigDir
    if($LASTEXITCODE -ne 0){throw "Voidglass R3 verify failed."}
} catch {
    foreach($n in @("i.bin","e.bin")){Copy-Item (Join-Path $backup $n) (Join-Path $ConfigDir $n) -Force}
    throw
}
Write-Host "LOCALLAB_CONFIG_PATCH_OK" -ForegroundColor Green