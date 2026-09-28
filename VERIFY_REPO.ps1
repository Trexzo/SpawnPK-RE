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

$currentV308='854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6'
$historicalV307='6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662'
$offlineText=Get-Content -LiteralPath (Join-Path $repo 'VERIFY_OFFLINE_READY.ps1') -Raw
$statusText=Get-Content -LiteralPath (Join-Path $repo 'docs\CURRENT_STATUS.md') -Raw

if(-not $offlineText.Contains($currentV308)){throw 'Offline-ready verifier is not pinned to canonical exact v308.'}
if($offlineText.Contains($historicalV307)){throw 'Offline-ready verifier still treats historical v307 as active authority.'}
if(-not $offlineText.Contains("scripts\Check-ExternalRuntime.ps1")){throw 'Offline-ready verifier no longer delegates to canonical external-runtime verification.'}
if(-not $statusText.Contains($currentV308)){throw 'CURRENT_STATUS is missing canonical exact-v308 authority.'}
if(-not $statusText.Contains($historicalV307)){throw 'CURRENT_STATUS lost historical v307 provenance.'}
Write-Host "V308_RUNTIME_AUTHORITY_CONTRACT_PASS" -ForegroundColor Green
if(Test-Path -LiteralPath (Join-Path $repo "server\data\accounts")){throw "Account data survived cleanup."}
$old=@(Get-ChildItem -LiteralPath $repo -Directory -Force | Where-Object {$_.Name -like ".v*-backup*" -or $_.Name -like ".world-*-backup*"})
if($old){throw "Historical backup forest survived cleanup."}
$files=@(Get-ChildItem -LiteralPath $repo -Recurse -File)
Write-Host "REPOSITORY_STRUCTURE_PASS files=$($files.Count) sizeMB=$([math]::Round((($files|Measure-Object Length -Sum).Sum/1MB),2))" -ForegroundColor Green