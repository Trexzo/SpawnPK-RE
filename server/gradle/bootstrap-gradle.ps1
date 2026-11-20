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

$cacheRoot = Join-Path ([Environment]::GetFolderPath('UserProfile')) ".gradle\locallab-bootstrap"
$zip = Join-Path $cacheRoot "gradle-$version-bin.zip"
$gradleHome = Join-Path $cacheRoot "gradle-$version"
$gradleBat = Join-Path $gradleHome 'bin\gradle.bat'

New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null

if (-not (Test-Path -LiteralPath $gradleBat -PathType Leaf)) {
    # R100: bounded download retries protect transient Windows runner
    # transport resets. Verify SHA-256 BEFORE promoting a temporary ZIP;
    # the existing R82 archive installer validates the hash again.
    $downloadHelper = Join-Path $PSScriptRoot 'pinned-gradle-download.ps1'
    if (-not (Test-Path -LiteralPath $downloadHelper -PathType Leaf)) {
        throw "Missing pinned Gradle download helper: $downloadHelper"
    }
    . $downloadHelper
    Save-PinnedGradleDistribution -Uri $distribution -ZipPath $zip -ExpectedSha256 $expectedSha256

    # Preserve the pinned SHA-256 check and the existing cache path.
    # R82 extracts through the same verified helper on PS 5.1 and PS 7,
    # bypassing Windows runner's broken Expand-Archive 3-arg overload.
    $archiveHelper = Join-Path $PSScriptRoot 'verified-gradle-archive.ps1'
    if (-not (Test-Path -LiteralPath $archiveHelper -PathType Leaf)) {
        throw "Missing verified Gradle archive helper: $archiveHelper"
    }
    . $archiveHelper
    Install-VerifiedGradleArchive -ZipPath $zip -GradleHome $gradleHome -Version $version -ExpectedSha256 $expectedSha256
}

Push-Location $serverRoot
try {
    & $gradleBat @GradleArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}