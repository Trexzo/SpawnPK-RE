param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$version = '8.10.2'
$expectedSha256 = '31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26'
$distribution = "https://services.gradle.org/distributions/gradle-$version-bin.zip"

$serverRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $serverRoot
$selector = Join-Path $repoRoot 'scripts\Select-LocalLabBuildJava.ps1'
if (-not (Test-Path -LiteralPath $selector -PathType Leaf)) {
    throw "Missing JDK 21 selector: $selector"
}
. $selector
[void](Set-LocalLabBuildJava)

function Get-ExactSha256([string]$Path) {
    return (
        Get-FileHash -LiteralPath $Path -Algorithm SHA256
    ).Hash.ToLowerInvariant()
}

$cacheRoot = Join-Path (
    [Environment]::GetFolderPath('UserProfile')
) '.gradle\locallab-bootstrap'
$zip = Join-Path $cacheRoot "gradle-$version-bin.zip"

New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null

$cachedValid = $false
if (Test-Path -LiteralPath $zip -PathType Leaf) {
    $cachedSha = Get-ExactSha256 $zip
    if ($cachedSha -eq $expectedSha256) {
        $cachedValid = $true
    }
    else {
        Write-Host (
            'Discarding invalid cached Gradle distribution. ' +
            "expected=$expectedSha256 actual=$cachedSha"
        ) -ForegroundColor Yellow
        Remove-Item -LiteralPath $zip -Force
    }
}

if (-not $cachedValid) {
    $download = Join-Path $cacheRoot (
        "gradle-$version-bin.zip.download-" +
        [Guid]::NewGuid().ToString('N')
    )

    try {
        Write-Host "Downloading Gradle $version..." -ForegroundColor Cyan
        Invoke-WebRequest -UseBasicParsing -Uri $distribution -OutFile $download

        $downloadSha = Get-ExactSha256 $download
        if ($downloadSha -ne $expectedSha256) {
            throw (
                'Gradle distribution SHA-256 mismatch. ' +
                "expected=$expectedSha256 actual=$downloadSha"
            )
        }

        try {
            Move-Item -LiteralPath $download -Destination $zip
        }
        catch {
            # A concurrent bootstrap may have won the cache publication race.
            # Accept that winner only when it is byte-identical to the same
            # pinned distribution; otherwise preserve the publication failure.
            if (-not (Test-Path -LiteralPath $zip -PathType Leaf)) {
                throw
            }

            $winnerSha = Get-ExactSha256 $zip
            if ($winnerSha -ne $expectedSha256) {
                throw
            }

            Write-Host (
                'LOCALLAB_GRADLE_CACHE_RACE_ACCEPTED ' +
                "sha256=$winnerSha"
            ) -ForegroundColor DarkGray
        }
    }
    finally {
        if (Test-Path -LiteralPath $download -PathType Leaf) {
            Remove-Item -LiteralPath $download -Force -ErrorAction SilentlyContinue
        }
    }
}

# Persistent cache authority ends at the ZIP. Every execution gets a private
# copy that is independently hash-gated before extraction, so a mutable
# previously extracted Gradle home can never become build authority.
$invocationRoot = Join-Path (
    [IO.Path]::GetTempPath()
) (
    "SpawnPK-gradle-$version-" +
    [Guid]::NewGuid().ToString('N')
)
$invocationZip = Join-Path $invocationRoot "gradle-$version-bin.zip"
$extractRoot = Join-Path $invocationRoot 'extract'
$gradleExit = $null

New-Item -ItemType Directory -Force -Path $invocationRoot | Out-Null

try {
    Copy-Item -LiteralPath $zip -Destination $invocationZip

    $invocationSha = Get-ExactSha256 $invocationZip
    if ($invocationSha -ne $expectedSha256) {
        throw (
            'Invocation-owned Gradle distribution SHA-256 mismatch. ' +
            "expected=$expectedSha256 actual=$invocationSha"
        )
    }

    Write-Host (
        'LOCALLAB_GRADLE_DISTRIBUTION_VERIFIED ' +
        "version=$version sha256=$invocationSha invocationOwned=true"
    ) -ForegroundColor Green

    Expand-Archive -LiteralPath $invocationZip -DestinationPath $extractRoot

    $gradleHome = Join-Path $extractRoot "gradle-$version"
    $gradleBat = Join-Path $gradleHome 'bin\gradle.bat'
    if (-not (Test-Path -LiteralPath $gradleBat -PathType Leaf)) {
        throw "Verified Gradle archive did not contain gradle-$version\bin\gradle.bat"
    }

    Push-Location $serverRoot
    try {
        & $gradleBat @GradleArgs
        $gradleExit = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}
finally {
    if (Test-Path -LiteralPath $invocationRoot -PathType Container) {
        Remove-Item -LiteralPath $invocationRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if ($null -eq $gradleExit) {
    throw 'Verified invocation-owned Gradle process did not produce an exit code.'
}

exit $gradleExit
