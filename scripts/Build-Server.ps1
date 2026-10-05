Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot

$hadCallerJavaHome = Test-Path Env:JAVA_HOME
$callerJavaHome = $env:JAVA_HOME
$callerPath = $env:Path

try {
    . (Join-Path $PSScriptRoot "Select-LocalLabBuildJava.ps1")
    $buildJava = Set-LocalLabBuildJava

$build = Join-Path $repo "server\build.ps1"
$gradleBuild = Join-Path $repo "server\build.gradle"
$gradleLauncher = Join-Path $repo "server\gradlew.bat"

foreach ($required in @($build,$gradleBuild,$gradleLauncher)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Server build component missing: $required"
    }
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
Write-Host "SERVER_BUILD_OK $sha gradle=true bytecodeMajor=55" -ForegroundColor Green

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
