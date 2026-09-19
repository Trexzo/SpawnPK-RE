Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=$PSScriptRoot
$required=@(
 "README.md","CONTRIBUTING.md",".gitignore","BOOTSTRAP.ps1","RUN_LOCAL_LAB.ps1",
 "IMPORT_EXISTING_RUNTIME.ps1","docs\SETUP_WINDOWS.md","docs\EXTERNAL_RUNTIME.md",
 "docs\AUTHORITY_MODEL.md","docs\DEVELOPMENT.md","docs\REPOSITORY_LAYOUT.md",
 "scripts\Select-LocalLabJava.ps1","scripts\Check-ExternalRuntime.ps1",
 "scripts\Build-Server.ps1","scripts\Patch-LocalConfigs.ps1","scripts\Run-Server.ps1",
 "scripts\Run-Client-Airgap.ps1","server\build.ps1","RUN_V5185_FULL_SELFTEST.ps1",
 "VERIFY_OFFLINE_READY.ps1","server\src\spk\local\LocalSession.java","server\src\spk\local\Main.java"
)
$missing=@($required | Where-Object {-not(Test-Path -LiteralPath (Join-Path $repo $_))})
if($missing){$missing | ForEach-Object {Write-Host "MISSING $_" -ForegroundColor Red};throw "Repository incomplete."}
if(Test-Path -LiteralPath (Join-Path $repo "server\data\accounts")){throw "Account data survived cleanup."}
$old=@(Get-ChildItem -LiteralPath $repo -Directory -Force | Where-Object {$_.Name -like ".v*-backup*" -or $_.Name -like ".world-*-backup*"})
if($old){throw "Historical backup forest survived cleanup."}
$files=@(Get-ChildItem -LiteralPath $repo -Recurse -File)
Write-Host "REPOSITORY_STRUCTURE_PASS files=$($files.Count) sizeMB=$([math]::Round((($files|Measure-Object Length -Sum).Sum/1MB),2))" -ForegroundColor Green