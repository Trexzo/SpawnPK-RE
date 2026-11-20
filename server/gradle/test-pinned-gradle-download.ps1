# R100 offline-only PowerShell 5.1 / 7 test. Uses fake local fetcher;
# never contacts Gradle mirrors or modifies the real Gradle cache.
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'pinned-gradle-download.ps1')

$root = Join-Path ([System.IO.Path]::GetTempPath()) ("r100-gradle-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
try {
    $source = Join-Path $root 'source.bin'
    [System.IO.File]::WriteAllBytes(
        $source,[System.Text.Encoding]::UTF8.GetBytes('R100 local test data only')
    )
    $sha = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    $zip = Join-Path $root 'gradle-test-bin.zip'
    $script:r100Source = $source
    $script:r100AttemptCount = 0

    $transient = {
        param($uri,$destination)
        $script:r100AttemptCount++
        if ($script:r100AttemptCount -eq 1) {
            [System.IO.File]::WriteAllBytes(
                $destination,[System.Text.Encoding]::UTF8.GetBytes('partial transfer')
            )
            throw 'R100_TEST_TRANSIENT_TRANSPORT_RESET'
        }
        Copy-Item -LiteralPath $script:r100Source -Destination $destination
    }

    Save-PinnedGradleDistribution -Uri 'https://unused.invalid/gradle.zip' -ZipPath $zip -ExpectedSha256 $sha -MaxAttempts 3 -Download $transient
    if ($script:r100AttemptCount -ne 2 -or
        (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ine $sha) {
        throw 'R100_TEST_FAILED retry did not produce verified canonical ZIP'
    }

    $notCalled = {
        param($uri,$destination)
        throw 'R100_TEST_FAILED cache hit attempted download'
    }
    Save-PinnedGradleDistribution -Uri 'https://unused.invalid/gradle.zip' -ZipPath $zip -ExpectedSha256 $sha -MaxAttempts 3 -Download $notCalled

    # Corrupt cached ZIP must be rejected and replaced, not passed into
    # the real R82 archive installer.
    Set-Content -LiteralPath $zip -Value 'corrupted previously cached ZIP'
    $script:r100RefetchCount = 0
    $refetch = {
        param($uri,$destination)
        $script:r100RefetchCount++
        Copy-Item -LiteralPath $script:r100Source -Destination $destination
    }
    Save-PinnedGradleDistribution -Uri 'https://unused.invalid/gradle.zip' -ZipPath $zip -ExpectedSha256 $sha -MaxAttempts 3 -Download $refetch
    if ($script:r100RefetchCount -ne 1 -or
        (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ine $sha) {
        throw 'R100_TEST_FAILED corrupted cache reused or not recovered'
    }

    $script:r100PermanentAttempts = 0
    $broken = {
        param($uri,$destination)
        $script:r100PermanentAttempts++
        [System.IO.File]::WriteAllBytes(
            $destination,[System.Text.Encoding]::UTF8.GetBytes('partial failed bytes')
        )
        throw 'R100_TEST_UNRECOVERABLE_TRANSPORT_RESET'
    }
    $unavailableZip = Join-Path $root 'broken.zip'
    $blocked = $false
    try {
        Save-PinnedGradleDistribution -Uri 'https://unused.invalid/broken.zip' -ZipPath $unavailableZip -ExpectedSha256 $sha -MaxAttempts 2 -Download $broken
    } catch {
        $blocked = $_.Exception.Message.Contains('R100_GRADLE_DOWNLOAD_DENIED after 2 attempts')
    }
    if (-not $blocked -or $script:r100PermanentAttempts -ne 2 -or
        (Test-Path -LiteralPath $unavailableZip)) {
        throw 'R100_TEST_FAILED exhausted retry did not fail closed'
    }

    # Completed but wrong bytes must NOT be promoted, even after retries.
    $badHashZip = Join-Path $root 'wrong-sha.zip'
    $badHash = {
        param($uri,$destination)
        [System.IO.File]::WriteAllBytes(
            $destination,[System.Text.Encoding]::UTF8.GetBytes('wrong bytes')
        )
    }
    $mismatchBlocked = $false
    try {
        Save-PinnedGradleDistribution -Uri 'https://unused.invalid/wrong.zip' -ZipPath $badHashZip -ExpectedSha256 $sha -MaxAttempts 1 -Download $badHash
    } catch {
        $mismatchBlocked = $_.Exception.Message.Contains('SHA256 mismatch')
    }
    if (-not $mismatchBlocked -or
        (Test-Path -LiteralPath $badHashZip)) {
        throw 'R100_TEST_FAILED wrong-checksum payload was promoted'
    }

    $leftovers = @(Get-ChildItem -LiteralPath $root -Filter '*.r100-*.download')
    if ($leftovers.Count -ne 0) {
        throw 'R100_TEST_FAILED partial downloads survived cleanup'
    }
    Write-Host (
        'R100_WINDOWS_GRADLE_DOWNLOAD_RETRY_PASS ' +
        'transientTransportRecovered=true ' +
        'retryCountBounded=true ' +
        'cacheHitDoesNotDownload=true ' +
        'corruptedCacheRefetched=true ' +
        'wrongShaNeverPromoted=true ' +
        'failedAttemptsRemovePartialFiles=true ' +
        'noNetworkOrRealCacheUsed=true'
    )
}
finally {
    if (Test-Path -LiteralPath $root) {
        Remove-Item -LiteralPath $root -Recurse -Force
    }
}
