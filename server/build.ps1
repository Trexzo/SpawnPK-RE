Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$serverRoot = $PSScriptRoot

Push-Location $serverRoot

try {
    $javacVersion = (& javac -version 2>&1 | Out-String).Trim()

    if ($javacVersion -notmatch '^javac\s+21\.') {
        throw "LocalLab development build requires JDK 21 javac. Current: $javacVersion"
    }

    Remove-Item ".\build" -Recurse -Force -ErrorAction SilentlyContinue
    New-Item ".\build\classes" -ItemType Directory -Force | Out-Null

    $src = @(
        Get-ChildItem ".\src" -Recurse -File -Filter "*.java" |
        Sort-Object FullName |
        ForEach-Object { $_.FullName }
    )

    if ($src.Count -eq 0) {
        throw "No Java source files found."
    }

    $argFile = Join-Path $env:TEMP ("spawnpk-javac-sources-" + [guid]::NewGuid().ToString("N") + ".args")

    try {
        $argLines = @(
            $src | ForEach-Object {
                $javacPath = $_.Replace('\','/')
                '"' + $javacPath.Replace('"','\"') + '"'
            }
        )

        [IO.File]::WriteAllLines(
            $argFile,
            $argLines,
            (New-Object Text.UTF8Encoding($false))
        )

        Write-Host ("JAVAC_ARGFILE sourceCount={0} file={1}" -f $argLines.Count,$argFile) -ForegroundColor Cyan

        & javac --release 11 -d ".\build\classes" "@$argFile"

        if ($LASTEXITCODE -ne 0) {
            throw "javac failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Remove-Item -LiteralPath $argFile -Force -ErrorAction SilentlyContinue
    }

    $compiled = @(
        Get-ChildItem ".\build\classes" -Recurse -File -Filter "*.class"
    )

    if ($compiled.Count -eq 0) {
        throw "javac produced zero class files."
    }

    Write-Host ("JAVAC_COMPILE_PASS classes={0}" -f $compiled.Count) -ForegroundColor Green

    # --------------------------------------------------------------
    # Current source-tree resources.
    # --------------------------------------------------------------

    $sourceRoot = (Resolve-Path ".\src").Path
    $classRoot  = (Resolve-Path ".\build\classes").Path

    $sourceResources = @(
        Get-ChildItem -LiteralPath $sourceRoot -Recurse -File |
        Where-Object { $_.Extension -ne ".java" }
    )

    $sourceCopied = 0

    foreach ($resource in $sourceResources) {
        $relative = $resource.FullName.Substring($sourceRoot.Length).TrimStart('\','/')
        $dest = Join-Path $classRoot $relative
        $parent = Split-Path -Parent $dest

        New-Item -ItemType Directory -Force -Path $parent | Out-Null
        Copy-Item -LiteralPath $resource.FullName -Destination $dest -Force
        $sourceCopied++
    }

    Write-Host ("SOURCE_RESOURCE_COPY_PASS files={0}" -f $sourceCopied) -ForegroundColor Green

    # --------------------------------------------------------------
    # Current server/data resources historically exposed at
    # classpath root spk/local/.
    # --------------------------------------------------------------

    $dataFiles = @(
        "items.tsv",
        "equipment_slots.tsv",
        "equipment_slot_overrides.tsv",
        "production_appearance_slots.tsv",
        "pet_mappings.tsv",
        "custom_pet_mappings.tsv",
        "custom_asset_manifest.tsv",
        "pet_ambiguous.tsv",
        "weapon_poses.tsv",
        "weapon_attack_r2.tsv",
        "weapon_pose_r2.tsv",
        "home_landmarks_v906.tsv",
        "home_npc_spawns_v906.tsv",
        "home_npc_wander_edges_v906.tsv",
        "home_object_overlay_v906.tsv",
        "home_object_collision_defs_v906.tsv",
        "home_collision_overlay_plan_v906.tsv"
    )

    $localResourceDir = Join-Path $classRoot "spk\local"
    New-Item -ItemType Directory -Force -Path $localResourceDir | Out-Null

    foreach ($name in $dataFiles) {
        $from = Join-Path ".\data" $name

        if (-not (Test-Path -LiteralPath $from -PathType Leaf)) {
            throw "Required server data resource missing: $from"
        }

        Copy-Item `
            -LiteralPath $from `
            -Destination (Join-Path $localResourceDir $name) `
            -Force
    }

    Write-Host ("SERVER_DATA_RESOURCE_COPY_PASS files={0}" -f $dataFiles.Count) -ForegroundColor Green

    # Generated report/output is not a sealed runtime resource.
    Remove-Item `
        -LiteralPath (Join-Path $localResourceDir "combat_weapon_coverage_m1.tsv") `
        -Force `
        -ErrorAction SilentlyContinue

    @"
Manifest-Version: 1.0
Main-Class: spk.local.Main
"@ | Set-Content ".\build\MANIFEST.MF" -Encoding ascii

    & jar cfm ".\build\SpawnPKLocalServer.jar" ".\build\MANIFEST.MF" -C ".\build\classes" .

    if ($LASTEXITCODE -ne 0) {
        throw "jar failed with exit code $LASTEXITCODE"
    }

    $jarPath = Join-Path $serverRoot "build\SpawnPKLocalServer.jar"

    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw "Expected server JAR was not produced: $jarPath"
    }

    Write-Host "JAR_PACKAGE_PASS" -ForegroundColor Green
    Write-Host "Built: $jarPath" -ForegroundColor Green
}
finally {
    Pop-Location
}