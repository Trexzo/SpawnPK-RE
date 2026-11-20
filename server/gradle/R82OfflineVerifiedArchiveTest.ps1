# R82 OFFLINE ONLY. Exercises the exact helper used by gradlew.bat,
# without a download, Java process, persistent profile or external network.
# Runs on both Windows PowerShell 5.1 and PowerShell 7 CI runners.
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'verified-gradle-archive.ps1')
Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction Stop
Add-Type -AssemblyName System.IO.Compression -ErrorAction Stop

function Require([bool]$Ok, [string]$Reason) {
    if (-not $Ok) { throw "R82_ASSERT_FAILED $Reason" }
}
function Expect-Failed([scriptblock]$Action, [string]$Contains) {
    try { & $Action }
    catch {
        Require ($_.Exception.Message.IndexOf($Contains, [StringComparison]::OrdinalIgnoreCase) -ge 0) (
            "Expected '$Contains', got '$($_.Exception.Message)'"
        )
        return
    }
    throw "R82_ASSERT_FAILED unexpectedly accepted invalid archive: $Contains"
}
function Sha([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Test-Archive(
    [string]$Base,
    [string]$TestName,
    [bool]$ValidLayout
) {
    $src = Join-Path $Base ("src-" + $TestName)
    $dir = if ($ValidLayout) { 'gradle-8.10.2' } else { 'invalid-root' }
    $bin = Join-Path (Join-Path $src $dir) 'bin'
    New-Item -Path $bin -ItemType Directory -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $bin 'gradle.bat') -Value '@echo off' -Encoding Ascii
    $archive = Join-Path $Base ($TestName + '.zip')
    [System.IO.Compression.ZipFile]::CreateFromDirectory($src, $archive)
    return $archive
}

$root = Join-Path ([IO.Path]::GetTempPath()) (
    'r82-verified-gradle-' + [Guid]::NewGuid().ToString('N')
)
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
    $valid = Test-Archive $root 'valid' $true
    $validHome = Join-Path $root 'valid-home'
    Install-VerifiedGradleArchive -ZipPath $valid -GradleHome $validHome -Version '8.10.2' -ExpectedSha256 (Sha $valid)
    Require (Test-Path -LiteralPath (Join-Path $validHome 'bin\gradle.bat') -PathType Leaf) 'valid archive not published'

    # Cache reuse with an already installed target is safe: no deletion.
    Install-VerifiedGradleArchive -ZipPath $valid -GradleHome $validHome -Version '8.10.2' -ExpectedSha256 (Sha $valid)
    Require (Test-Path -LiteralPath (Join-Path $validHome 'bin\gradle.bat') -PathType Leaf) 'existing archive target deleted'

    $wrongSha = Test-Archive $root 'wrongsha' $true
    $badHome = Join-Path $root 'bad-hash-home'
    Expect-Failed {
        Install-VerifiedGradleArchive -ZipPath $wrongSha -GradleHome $badHome -Version '8.10.2' -ExpectedSha256 ('0' * 64)
    } 'SHA-256 mismatch'
    Require (-not (Test-Path -LiteralPath $wrongSha)) 'bad cached ZIP not removed'
    Require (-not (Test-Path -LiteralPath $badHome)) 'bad hash published target'

    $invalidZip = Join-Path $root 'invalid.zip'
    [IO.File]::WriteAllText($invalidZip, 'not a zip archive')
    $invalidHome = Join-Path $root 'invalid-home'
    Expect-Failed {
        Install-VerifiedGradleArchive -ZipPath $invalidZip -GradleHome $invalidHome -Version '8.10.2' -ExpectedSha256 (Sha $invalidZip)
    } 'central directory'
    Require (-not (Test-Path -LiteralPath $invalidHome)) 'unreadable archive published target'

    $wrongRoot = Test-Archive $root 'wrongroot' $false
    $wrongRootHome = Join-Path $root 'wrongroot-home'
    Expect-Failed {
        Install-VerifiedGradleArchive -ZipPath $wrongRoot -GradleHome $wrongRootHome -Version '8.10.2' -ExpectedSha256 (Sha $wrongRoot)
    } 'did not contain'
    Require (-not (Test-Path -LiteralPath $wrongRootHome)) 'wrong-root archive published target'

    # Verify .NET extraction refuses traversal even when the ZIP checksum
    # is explicitly trusted by the test fixture.
    $slipZip = Join-Path $root 'traversal.zip'
    $archive = [System.IO.Compression.ZipFile]::Open(
        $slipZip, [System.IO.Compression.ZipArchiveMode]::Create
    )
    try {
        $entry = $archive.CreateEntry('gradle-8.10.2/../../escape.txt')
        $stream = $entry.Open()
        try { $stream.WriteByte([byte]88) }
        finally { $stream.Dispose() }
    } finally { $archive.Dispose() }
    $slipHome = Join-Path $root 'slip-home'
    Expect-Failed {
        Install-VerifiedGradleArchive -ZipPath $slipZip -GradleHome $slipHome -Version '8.10.2' -ExpectedSha256 (Sha $slipZip)
    } 'outside'
    Require (-not (Test-Path -LiteralPath $slipHome)) 'traversal published a target'
    Require (-not (Test-Path -LiteralPath (Join-Path $root 'escape.txt'))) 'traversal wrote outside staging'
    Require ((Get-ChildItem -LiteralPath $root -Directory -Filter 'extract-*').Count -eq 0) 'archive extraction left staging behind'

    Write-Output 'R82_OFFLINE_VERIFIED_GRADLE_ARCHIVE_PASS PS5_PS7_COMPAT=true SHA256_BEFORE_EXTRACT=true CACHED_ZIP_FAIL_CLOSED=true VALID_LAYOUT_REQUIRED=true UNREADABLE_FAIL_CLOSED=true ZIP_TRAVERSAL_DENIED=true TEMP_STAGE_CLEAN=true NO_NETWORK_OR_LIVE_RUNTIME=true'
}
finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
