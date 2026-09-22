param(
    [Parameter(Mandatory=$true)]
    [string]$V308ClientPath
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$repo = $PSScriptRoot
$server = Join-Path $repo "server"
$gradle = Join-Path $server "gradlew.bat"
$expectedV308 = "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"

if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "Missing Gradle wrapper: $gradle"
}

$client = (Resolve-Path -LiteralPath $V308ClientPath).Path

if (-not (Test-Path -LiteralPath $client -PathType Leaf)) {
    throw "Exact v308 client is not a file: $client"
}

$actual = (Get-FileHash -LiteralPath $client -Algorithm SHA256).Hash.ToLowerInvariant()

if ($actual -ne $expectedV308) {
    throw "Exact v308 SHA-256 mismatch.
Expected: $expectedV308
Actual:   $actual
Path:     $client"
}

Write-Host "V308_FIXTURE_SHA256_PASS $actual" -ForegroundColor Green

Push-Location $server
try {
    & $gradle clean build

    if ($LASTEXITCODE -ne 0) {
        throw "Current cumulative Gradle build/focused regression failed with exit code $LASTEXITCODE"
    }

    Write-Host "CURRENT_RELEASE_BUILD_PASS focusedGate=true" -ForegroundColor Green

    & $gradle r85V308Acceptance "-Pv308ClientPath=$client"

    if ($LASTEXITCODE -ne 0) {
        throw "Exact v308 inherited 179/179 acceptance failed with exit code $LASTEXITCODE"
    }

    Write-Host "CURRENT_RELEASE_V308_ACCEPTANCE_PASS clientSha256=$actual canonicalPromoted=false" -ForegroundColor Green
}
finally {
    Pop-Location
}
