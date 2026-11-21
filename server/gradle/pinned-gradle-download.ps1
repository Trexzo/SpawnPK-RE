# R100: bounded, checksum-verified Gradle distribution download.
# Both PowerShell 5.1 and 7; caller keeps the existing verified extractor.
# The test seam substitutes Download with a local scriptblock: no network
# or writable production cache is used by the offline regression.
# The real JDK21 launcher deliberately changes its PowerShell module environment.
# Match R82's verified extractor: rely on the .NET stream API, not
# Microsoft.PowerShell.Utility/Get-FileHash (not always available).
function Get-R100StreamSha256 {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][string]$Path)
    $hash = [System.Security.Cryptography.SHA256]::Create()
    try {
        $stream = [System.IO.File]::OpenRead($Path)
        try {
            return ([BitConverter]::ToString(
                $hash.ComputeHash($stream)
            )).Replace('-', '').ToLowerInvariant()
        }
        finally { $stream.Dispose() }
    }
    finally { $hash.Dispose() }
}

function Save-PinnedGradleDistribution {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)][string]$ZipPath,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256,
        [int]$MaxAttempts = 3,
        [scriptblock]$Download = {
            param($url, $destination)
            Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $destination -ErrorAction Stop | Out-Null
        }
    )

    if ($ExpectedSha256 -notmatch '^[0-9a-fA-F]{64}$') {
        throw 'R100_GRADLE_DOWNLOAD_DENIED invalid expected checksum'
    }
    if ($MaxAttempts -lt 1 -or $MaxAttempts -gt 5) {
        throw 'R100_GRADLE_DOWNLOAD_DENIED invalid retry bound'
    }
    if ($null -eq $Download) {
        throw 'R100_GRADLE_DOWNLOAD_DENIED missing fetcher'
    }

    $parent = Split-Path -Parent $ZipPath
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
        throw 'R100_GRADLE_DOWNLOAD_DENIED cache parent missing'
    }

    # Do not silently reuse partial or corrupt previous-run ZIPs.
    # The existing R82 extractor independently checks this same hash.
    if (Test-Path -LiteralPath $ZipPath -PathType Leaf) {
        $existingHash = (Get-R100StreamSha256 -Path $ZipPath)
        if ($existingHash -ieq $ExpectedSha256) {
            return
        }
        Remove-Item -LiteralPath $ZipPath -Force -ErrorAction Stop
    }

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $candidate = $ZipPath + '.r100-' + [Guid]::NewGuid().ToString('N') + '.download'
        try {
            & $Download $Uri $candidate
            if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
                throw 'R100_GRADLE_DOWNLOAD_DENIED no downloaded file'
            }
            $downloadedHash = (Get-R100StreamSha256 -Path $candidate)
            if ($downloadedHash -ine $ExpectedSha256) {
                throw 'R100_GRADLE_DOWNLOAD_DENIED SHA256 mismatch'
            }

            Move-Item -LiteralPath $candidate -Destination $ZipPath -Force -ErrorAction Stop
            Write-Host "R100_GRADLE_DOWNLOAD_VERIFIED attempt=$attempt"
            return
        }
        catch {
            if ($attempt -eq $MaxAttempts) {
                throw "R100_GRADLE_DOWNLOAD_DENIED after $MaxAttempts attempts: $($_.Exception.Message)"
            }
            Write-Warning "R100 Gradle download attempt $attempt/$MaxAttempts failed; retrying"
            Start-Sleep -Seconds ([Math]::Min($attempt * 2, 4))
        }
        finally {
            if (Test-Path -LiteralPath $candidate) {
                Remove-Item -LiteralPath $candidate -Force -ErrorAction Stop
            }
        }
    }
    throw 'R100_GRADLE_DOWNLOAD_DENIED exhausted bounded retries'
}
