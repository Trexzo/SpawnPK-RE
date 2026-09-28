param(
    [string]$ClientJar = (Join-Path $PSScriptRoot '..\evidence\client(6).jar')
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$selector = Join-Path $PSScriptRoot 'Select-LocalLabBuildJava.ps1'
$gradlew = Join-Path $repo 'server\gradlew.bat'
$expectedClientSha =
    '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6'

foreach ($required in @(
    $selector,
    $gradlew
)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing cumulative certification component: $required"
    }
}

$client = [IO.Path]::GetFullPath($ClientJar)

if (-not (Test-Path -LiteralPath $client -PathType Leaf)) {
    throw "Exact-v308 certification client is missing: $client"
}

$actualClientSha =
    (Get-FileHash -LiteralPath $client -Algorithm SHA256)
        .Hash
        .ToLowerInvariant()

if ($actualClientSha -ne $expectedClientSha) {
    throw (
        'Exact-v308 certification client SHA-256 mismatch. ' +
        "Expected: $expectedClientSha " +
        "Actual: $actualClientSha " +
        "Path: $client"
    )
}

$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:Path

try {
    . $selector
    $buildJava = Set-LocalLabBuildJava

    Write-Host (
        'CHAT1_CUMULATIVE_PREFLIGHT_PASS ' +
        "clientSha256=$actualClientSha " +
        "buildJdk=$($buildJava.Version)"
    ) -ForegroundColor Green

    $gradleArgs = @(
        '--no-daemon',
        '--console=plain',
        'clean',
        'chat1CurrentCumulativeCertification',
        "-Pv308ClientPath=$client"
    )

    Push-Location (Join-Path $repo 'server')
    try {
        & $gradlew @gradleArgs

        if ($LASTEXITCODE -ne 0) {
            throw (
                'Exact-current cumulative certification failed with Gradle exit code ' +
                $LASTEXITCODE
            )
        }
    }
    finally {
        Pop-Location
    }

    # The authoritative release PASS is emitted only by
    # chat1CurrentCumulativeCertification after every dependent gate succeeds.
    # This wrapper marker reports only that the canonical invocation returned 0.
    Write-Host (
        'CHAT1_CUMULATIVE_WRAPPER_COMPLETE ' +
        "exactV308=true clientSha256=$actualClientSha"
    ) -ForegroundColor Green
}
finally {
    $env:JAVA_HOME = $oldJavaHome
    $env:Path = $oldPath
}
