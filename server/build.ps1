Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$serverRoot = $PSScriptRoot
$gradlew = Join-Path $serverRoot "gradlew.bat"

if (-not (Test-Path -LiteralPath $gradlew -PathType Leaf)) {
    throw "Gradle launcher missing: $gradlew"
}

Push-Location $serverRoot
try {
    & $gradlew --no-daemon clean jar testClasses verifyJava11Bytecode

    if ($LASTEXITCODE -ne 0) {
        throw "Gradle server build failed with exit code $LASTEXITCODE"
    }

    $jarPath = Join-Path $serverRoot "build\SpawnPKLocalServer.jar"
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw "Gradle build completed without producing: $jarPath"
    }

    $testClasses = Join-Path $serverRoot "build\classes\java\test"
    if (-not (Test-Path -LiteralPath $testClasses -PathType Container)) {
        throw "Gradle build completed without compiling the R8.5 regression classes: $testClasses"
    }

    Write-Host "GRADLE_SERVER_BUILD_PASS" -ForegroundColor Green
    Write-Host "Built: $jarPath" -ForegroundColor Green
}
finally {
    Pop-Location
}
