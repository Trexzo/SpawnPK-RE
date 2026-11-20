# R82: shared PowerShell 5.1 / PowerShell 7 verified Gradle ZIP extraction.
# No download, network, Java launch or account state occurs in this helper.
# Bootstrap supplies its pinned distribution version and SHA-256.
function Install-VerifiedGradleArchive {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$ZipPath,
        [Parameter(Mandatory = $true)][string]$GradleHome,
        [Parameter(Mandatory = $true)][string]$Version,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256
    )

    if ($ExpectedSha256 -notmatch '^[0-9a-fA-F]{64}$') {
        throw 'Invalid pinned Gradle SHA-256 format'
    }
    if (-not (Test-Path -LiteralPath $ZipPath -PathType Leaf)) {
        throw "Missing Gradle distribution archive: $ZipPath"
    }

    # Verify both new downloads and cached ZIPs BEFORE any extraction.
    # Use the original bootstrap's .NET stream hash rather than the
    # Get-FileHash cmdlet: the real launcher may run without that
    # cmdlet available after its JDK 21 environment selection.
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    try {
        $stream = [System.IO.File]::OpenRead($ZipPath)
        try {
            $actual = ([BitConverter]::ToString(
                $hasher.ComputeHash($stream)
            )).Replace('-', '').ToLowerInvariant()
        }
        finally { $stream.Dispose() }
    }
    finally { $hasher.Dispose() }
    $expected = $ExpectedSha256.ToLowerInvariant()
    if ($actual -ne $expected) {
        Remove-Item -LiteralPath $ZipPath -Force -ErrorAction SilentlyContinue
        throw "Gradle distribution SHA-256 mismatch. expected=$expected actual=$actual"
    }

    $parent = Split-Path -Parent $GradleHome
    $stage = Join-Path $parent ("extract-" + [Guid]::NewGuid().ToString('N'))
    $expectedDir = "gradle-$Version"
    $expectedExecutable = Join-Path $GradleHome 'bin\gradle.bat'

    try {
        New-Item -ItemType Directory -Force -Path $stage | Out-Null
        # Expand-Archive on Windows Server 2025 can call a missing
        # ExtractToDirectory(3 args) overload under Windows PowerShell 5.1.
        # .NET's two-argument overload works on both PS 5.1 and PS 7,
        # and rejects entries that escape the destination directory.
        # Avoid relying on cmdlet module discovery in the real batch
        # launcher; load the framework assembly only when necessary.
        if (-not ('System.IO.Compression.ZipFile' -as [type])) {
            [void][System.Reflection.Assembly]::Load('System.IO.Compression.FileSystem')
        }
        [System.IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $stage)

        $expanded = Join-Path $stage $expectedDir
        $extractedExecutable = Join-Path $expanded 'bin\gradle.bat'
        if (-not (Test-Path -LiteralPath $extractedExecutable -PathType Leaf)) {
            throw "Gradle archive did not contain $expectedDir/bin/gradle.bat"
        }

        # A previously interrupted installation may have left an empty
        # or incomplete target. Never recurse through a junction/symlink.
        if (Test-Path -LiteralPath $GradleHome) {
            $existing = Get-Item -LiteralPath $GradleHome -Force
            if (($existing.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'Refusing to replace a Gradle cache reparse point'
            }
            if (Test-Path -LiteralPath $expectedExecutable -PathType Leaf) {
                return # Another installer already published the same target.
            }
            Remove-Item -LiteralPath $GradleHome -Recurse -Force
        }
        Move-Item -LiteralPath $expanded -Destination $GradleHome -ErrorAction Stop
    }
    finally {
        Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
    }
}
