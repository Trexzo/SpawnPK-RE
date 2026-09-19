Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot

. (Join-Path $PSScriptRoot "Select-LocalLabBuildJava.ps1")
$buildJava = Set-LocalLabBuildJava

$build = Join-Path $repo "server\build.ps1"

if (-not (Test-Path -LiteralPath $build -PathType Leaf)) {
    throw "Server build script missing: $build"
}

Push-Location $repo
try {
    & $build

    if ($LASTEXITCODE -ne 0) {
        throw "Server build failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

$jar = Join-Path $repo "server\build\SpawnPKLocalServer.jar"

if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw "Build completed without producing: $jar"
}

$sha = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "SERVER_BUILD_OK $sha" -ForegroundColor Green