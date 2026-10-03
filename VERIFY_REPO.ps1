Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"
$repo = $PSScriptRoot

# Fast tracked-structure verification only.
# This deliberately does not require proprietary/external runtime JARs such as
# evidence\client(6).jar or generated local-client derivatives.
$required = @(
    "README.md",
    "CONTRIBUTING.md",
    ".gitignore",
    "BOOTSTRAP.ps1",
    "IMPORT_EXISTING_RUNTIME.ps1",
    "RUN_LOCAL_LAB.ps1",
    "RUN_ALL_LOCAL_LAB.ps1",
    "RUN_CLIENT_AIRGAP.ps1",
    "RUN_CLIENT_LOCALHOST_NONAIRGAP.ps1",
    "RUN_SECOND_LOCAL_CLIENT.ps1",
    "RUN_CURRENT_RELEASE_ACCEPTANCE.ps1",
    "RUN_REPO_SELFTEST.ps1",
    "RUN_V5185_FULL_SELFTEST.ps1",
    "VERIFY_OFFLINE_READY.ps1",
    "VERIFY_REPO.ps1",
    "WATCH_CLIENT_NETWORK.ps1",

    "docs\SETUP_WINDOWS.md",
    "docs\EXTERNAL_RUNTIME.md",
    "docs\CURRENT_STATUS.md",
    "docs\AUTHORITY_MODEL.md",
    "docs\DEVELOPMENT.md",
    "docs\REPOSITORY_LAYOUT.md",
    "docs\release-r8.5\CERTIFICATION_R8_5.txt",

    "evidence\V308_ACCEPTANCE_2026-09-22.md",

    "scripts\Select-LocalLabJava.ps1",
    "scripts\Select-LocalLabBuildJava.ps1",
    "scripts\Check-ExternalRuntime.ps1",
    "scripts\Build-Server.ps1",
    "scripts\Build-V308LocalClients.ps1",
    "scripts\Patch-LocalConfigs.ps1",
    "scripts\Run-Server.ps1",
    "scripts\Run-Client-Airgap.ps1",
    "scripts\Run-Chat1CumulativeCertification.ps1",
    "scripts\Run-R13AssetAcceptance.ps1",
    "scripts\Test-LauncherContract.ps1",
    "scripts\Test-GradleBootstrapContract.ps1",

    "server\build.ps1",
    "server\build.gradle",
    "server\gradlew.bat",
    "server\gradle\bootstrap-gradle.ps1",
    "server\certified\README.md",
    "server\certified\SpawnPKLocalServer-R8.5-certified.jar",
    "server\src\spk\local\LocalSession.java",
    "server\src\spk\local\Main.java",
    "tools\R85_SelectJava11Plus.ps1",

    "tools\runtime\build_v308_local_clients.py",
    "tools\custom-assets\compile_spawnpk_legacy_textured_skinned.py",
    "tools\custom-assets\build_r13_isolated_profile.py",
    "tools\custom-assets\build_r13_texture_archive.py",
    "tools\custom-assets\R13ConfigBuilder.java",
    "tools\custom-assets\R13CacheArchiveTool.java",
    "tools\custom-assets\VerifyR13Profile.java",
    "tools\custom-assets\fixtures\r13-probe.obj",
    "tools\custom-assets\fixtures\r13-probe.skins.json",
    "tools\custom-assets\fixtures\r13-material.json",
    "tools\custom-assets\test_legacy_textured_skinned_writer.py",
    "tools\custom-assets\test_r13_profile_path_guards.py"
)

$missing = @(
    $required |
        Where-Object {
            -not (Test-Path -LiteralPath (Join-Path $repo $_) -PathType Leaf)
        }
)

if ($missing.Count -ne 0) {
    $missing |
        ForEach-Object {
            Write-Host "MISSING $_" -ForegroundColor Red
        }
    throw "Repository incomplete: missing $($missing.Count) required tracked file(s)."
}

if (Test-Path -LiteralPath (Join-Path $repo "server\data\accounts")) {
    throw "Account data survived cleanup."
}

$old = @(
    Get-ChildItem -LiteralPath $repo -Directory -Force |
        Where-Object {
            $_.Name -like ".v*-backup*" -or
            $_.Name -like ".world-*-backup*"
        }
)

if ($old.Count -ne 0) {
    throw "Historical backup forest survived cleanup."
}

$files = @(
    Get-ChildItem -LiteralPath $repo -Recurse -File
)
$totalBytes = ($files | Measure-Object Length -Sum).Sum

Write-Host (
    "REPOSITORY_STRUCTURE_PASS " +
    "required=$($required.Count) " +
    "files=$($files.Count) " +
    "sizeMB=$([math]::Round(($totalBytes / 1MB), 2)) " +
    "currentReleaseStructure=true"
) -ForegroundColor Green
