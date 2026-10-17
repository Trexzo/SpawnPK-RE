param(
    [string]$ClientJar = (Join-Path $PSScriptRoot '..\evidence\client(6).jar'),
    [string]$EvidenceFileName = ''
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$selector = Join-Path $PSScriptRoot 'Select-LocalLabBuildJava.ps1'
$gradlew = Join-Path $repo 'server\gradlew.bat'
$runtimeRoot = Join-Path $repo 'runtime'
$evidenceRoot = Join-Path $runtimeRoot 'certification'
$expectedClientSha =
    '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6'

function Get-ExactGitHead {
    $lines = @(& git -C $repo rev-parse HEAD)
    if ($LASTEXITCODE -ne 0 -or $lines.Count -ne 1) {
        throw 'Unable to resolve exact Git HEAD for cumulative certification.'
    }

    $head = ([string]$lines[0]).Trim().ToLowerInvariant()
    if ($head -notmatch '^[0-9a-f]{40}$') {
        throw "Invalid Git HEAD identity: $head"
    }

    return $head
}

function Get-TrackedWorktreeChanges {
    $lines = @(
        & git -C $repo status --porcelain --untracked-files=normal
    )
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to inspect Git worktree state for cumulative certification.'
    }

    return @(
        $lines |
            Where-Object {
                -not [string]::IsNullOrWhiteSpace(
                    [string]$_
                )
            }
    )
}

function Assert-OrdinaryDirectory {
    param(
        [Parameter(Mandatory=$true)]
        [string]$Path,
        [Parameter(Mandatory=$true)]
        [string]$Label
    )

    try {
        $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    }
    catch {
        throw "$Label is not an ordinary directory: $Path"
    }

    if (-not $item.PSIsContainer) {
        throw "$Label is not an ordinary directory: $Path"
    }

    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Label must not be a reparse point: $Path"
    }
}

function Initialize-CertificationEvidenceRoot {
    $trimChars = [char[]]@(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )

    $repoFull =
        [IO.Path]::GetFullPath($repo).TrimEnd($trimChars)
    $runtimeFull =
        [IO.Path]::GetFullPath($runtimeRoot).TrimEnd($trimChars)
    $evidenceFull =
        [IO.Path]::GetFullPath($evidenceRoot).TrimEnd($trimChars)

    $expectedRuntime =
        [IO.Path]::GetFullPath(
            (Join-Path $repoFull 'runtime')
        ).TrimEnd($trimChars)
    $expectedEvidence =
        [IO.Path]::GetFullPath(
            (Join-Path $expectedRuntime 'certification')
        ).TrimEnd($trimChars)

    if (-not [StringComparer]::OrdinalIgnoreCase.Equals(
            $runtimeFull,
            $expectedRuntime
        )) {
        throw "Certification runtime root escaped repository authority: $runtimeFull"
    }

    if (-not [StringComparer]::OrdinalIgnoreCase.Equals(
            $evidenceFull,
            $expectedEvidence
        )) {
        throw "Certification evidence root escaped repository runtime authority: $evidenceFull"
    }

    if (Test-Path -LiteralPath $runtimeRoot) {
        Assert-OrdinaryDirectory -Path $runtimeRoot -Label 'Certification runtime root'
    }
    else {
        New-Item -ItemType Directory -Path $runtimeRoot | Out-Null
        Assert-OrdinaryDirectory -Path $runtimeRoot -Label 'Certification runtime root'
    }

    if (Test-Path -LiteralPath $evidenceRoot) {
        Assert-OrdinaryDirectory -Path $evidenceRoot -Label 'Certification evidence root'
    }
    else {
        New-Item -ItemType Directory -Path $evidenceRoot | Out-Null
        Assert-OrdinaryDirectory -Path $evidenceRoot -Label 'Certification evidence root'
    }
}


function Assert-OrdinaryFile {
    param(
        [Parameter(Mandatory=$true)]
        [string]$Path,
        [Parameter(Mandatory=$true)]
        [string]$Label
    )

    try {
        $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    }
    catch {
        throw "$Label is not an ordinary file: $Path"
    }

    if ($item.PSIsContainer) {
        throw "$Label is not an ordinary file: $Path"
    }

    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Label must not be a reparse point: $Path"
    }

    return $item
}

function Remove-EmptyCertificationSnapshotRootSafely {
    param(
        [Parameter(Mandatory=$true)]
        [string]$SnapshotRoot
    )

    Assert-OrdinaryDirectory -Path $runtimeRoot -Label 'Certification runtime root before empty snapshot-root cleanup'
    Assert-OrdinaryDirectory -Path $evidenceRoot -Label 'Certification evidence root before empty snapshot-root cleanup'
    Assert-OrdinaryDirectory -Path $SnapshotRoot -Label 'Certification client snapshot root before empty cleanup'

    $remaining = @(
        Get-ChildItem -LiteralPath $SnapshotRoot -Force -ErrorAction Stop
    )
    if ($remaining.Count -ne 0) {
        throw (
            'Certification snapshot root is not empty; refusing cleanup of unowned or unexpected contents.'
        )
    }

    Assert-OrdinaryDirectory -Path $runtimeRoot -Label 'Certification runtime root immediately before snapshot-root removal'
    Assert-OrdinaryDirectory -Path $evidenceRoot -Label 'Certification evidence root immediately before snapshot-root removal'
    Assert-OrdinaryDirectory -Path $SnapshotRoot -Label 'Certification client snapshot root immediately before removal'

    Remove-Item -LiteralPath $SnapshotRoot -Force -ErrorAction Stop
}

function Remove-CertificationSnapshotSafely {
    param(
        [Parameter(Mandatory=$true)]
        [string]$SnapshotRoot,
        [Parameter(Mandatory=$true)]
        [string]$SnapshotClient
    )

    Assert-OrdinaryDirectory -Path $runtimeRoot -Label 'Certification runtime root before snapshot cleanup'
    Assert-OrdinaryDirectory -Path $evidenceRoot -Label 'Certification evidence root before snapshot cleanup'
    Assert-OrdinaryDirectory -Path $SnapshotRoot -Label 'Certification client snapshot root before cleanup'

    $entries = @(
        Get-ChildItem -LiteralPath $SnapshotRoot -Force -ErrorAction Stop
    )
    if ($entries.Count -ne 1 -or
        $entries[0].Name -ne 'client-v308.jar') {
        throw (
            'Certification snapshot cleanup refused unexpected contents. ' +
            "Expected one owned client-v308.jar leaf; observed=$($entries.Count)"
        )
    }

    $snapshotItem =
        Assert-OrdinaryFile -Path $SnapshotClient -Label 'Certification client snapshot cleanup leaf'
    $expectedSnapshot =
        [IO.Path]::GetFullPath($SnapshotClient)
    if (-not [StringComparer]::OrdinalIgnoreCase.Equals(
            $snapshotItem.FullName,
            $expectedSnapshot
        )) {
        throw "Certification snapshot cleanup leaf identity drifted: $($snapshotItem.FullName)"
    }

    Assert-OrdinaryDirectory -Path $runtimeRoot -Label 'Certification runtime root immediately before snapshot leaf cleanup'
    Assert-OrdinaryDirectory -Path $evidenceRoot -Label 'Certification evidence root immediately before snapshot leaf cleanup'
    Assert-OrdinaryDirectory -Path $SnapshotRoot -Label 'Certification client snapshot root immediately before leaf cleanup'

    Remove-Item -LiteralPath $SnapshotClient -Force -ErrorAction Stop

    Remove-EmptyCertificationSnapshotRootSafely -SnapshotRoot $SnapshotRoot
}

function Remove-CertificationEvidenceLeafSafely {
    param(
        [Parameter(Mandatory=$true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    Assert-OrdinaryDirectory -Path $runtimeRoot -Label 'Certification runtime root before failed-evidence cleanup'
    Assert-OrdinaryDirectory -Path $evidenceRoot -Label 'Certification evidence root before failed-evidence cleanup'

    $item =
        Assert-OrdinaryFile -Path $Path -Label 'Failed certification evidence cleanup leaf'
    $expected =
        [IO.Path]::GetFullPath($Path)
    if (-not [StringComparer]::OrdinalIgnoreCase.Equals(
            $item.FullName,
            $expected
        )) {
        throw "Failed certification evidence cleanup leaf identity drifted: $($item.FullName)"
    }

    Assert-OrdinaryDirectory -Path $runtimeRoot -Label 'Certification runtime root immediately before failed-evidence removal'
    Assert-OrdinaryDirectory -Path $evidenceRoot -Label 'Certification evidence root immediately before failed-evidence removal'

    Remove-Item -LiteralPath $Path -Force -ErrorAction Stop
}

function Get-Sha256Hex {
    param(
        [Parameter(Mandatory=$true)]
        [System.IO.Stream]$Stream
    )

    if (-not $Stream.CanRead -or -not $Stream.CanSeek) {
        throw 'SHA-256 input stream must be readable and seekable.'
    }

    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $Stream.Position = 0
        $hash = $sha.ComputeHash($Stream)
        return (
            @(
                $hash | ForEach-Object {
                    $_.ToString('x2')
                }
            ) -join ''
        )
    }
    finally {
        $sha.Dispose()
        $Stream.Position = 0
    }
}

foreach ($required in @(
    $selector,
    $gradlew
)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing cumulative certification component: $required"
    }
}

$dirtyBefore = @(Get-TrackedWorktreeChanges)
if ($dirtyBefore.Count -ne 0) {
    $dirtyBefore | ForEach-Object {
        Write-Host $_ -ForegroundColor Yellow
    }
    throw 'Cumulative certification requires a clean tracked worktree.'
}

$headBefore = Get-ExactGitHead
$client = [IO.Path]::GetFullPath($ClientJar)

if (-not (Test-Path -LiteralPath $client -PathType Leaf)) {
    throw "Exact-v308 certification client is missing: $client"
}

Initialize-CertificationEvidenceRoot

$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssfffZ')
$headPrefix = $headBefore.Substring(0, 12)
$log = Join-Path $evidenceRoot (
    "chat1-cumulative-$stamp-$headPrefix.log"
)
if ([string]::IsNullOrWhiteSpace($EvidenceFileName)) {
    $evidence = Join-Path $evidenceRoot (
        "chat1-cumulative-$stamp-$headPrefix.json"
    )
}
else {
    $evidenceLeaf = [IO.Path]::GetFileName($EvidenceFileName)
    if ($evidenceLeaf -ne $EvidenceFileName -or
        $evidenceLeaf -notmatch '^chat1-cumulative-[A-Za-z0-9._-]+\.json$') {
        throw "EvidenceFileName must be one safe chat1-cumulative-*.json leaf name."
    }

    $evidence = Join-Path $evidenceRoot $evidenceLeaf
    if (Test-Path -LiteralPath $evidence) {
        throw "Cumulative certification evidence already exists: $evidence"
    }
}
$snapshotRoot = Join-Path $evidenceRoot (
    "client-snapshot-$stamp-$headPrefix"
)
$snapshotClient = Join-Path $snapshotRoot 'client-v308.jar'

$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:Path
$sourceGuard = $null
$privateGuard = $null
$snapshotRootOwned = $false
$snapshotClientOwned = $false
$logGuard = $null
$logWriter = $null
$certifiedServerSha = $null

try {
    New-Item -ItemType Directory -Path $snapshotRoot | Out-Null
    Assert-OrdinaryDirectory -Path $snapshotRoot -Label 'Certification client snapshot root'
    $snapshotRootOwned = $true

    $sourceGuard =
        [IO.File]::Open(
            $client,
            [IO.FileMode]::Open,
            [IO.FileAccess]::Read,
            [IO.FileShare]::Read
        )

    $actualClientSha = Get-Sha256Hex -Stream $sourceGuard
    if ($actualClientSha -ne $expectedClientSha) {
        throw (
            'Exact-v308 certification client SHA-256 mismatch. ' +
            "Expected: $expectedClientSha " +
            "Actual: $actualClientSha " +
            "Path: $client"
        )
    }

    $snapshotWriter =
        [IO.File]::Open(
            $snapshotClient,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
    $snapshotClientOwned = $true
    try {
        # Own the exact empty destination file before the final ancestry
        # revalidation. Sensitive client bytes are copied only while this
        # already-open non-sharing file handle remains authoritative.
        Assert-OrdinaryDirectory -Path $runtimeRoot -Label 'Certification runtime root before client snapshot copy'
        Assert-OrdinaryDirectory -Path $evidenceRoot -Label 'Certification evidence root before client snapshot copy'
        Assert-OrdinaryDirectory -Path $snapshotRoot -Label 'Certification client snapshot root before client snapshot copy'

        $sourceGuard.Position = 0
        $sourceGuard.CopyTo($snapshotWriter)
        $snapshotWriter.Flush($true)
    }
    finally {
        $snapshotWriter.Dispose()
    }

    $privateGuard =
        [IO.File]::Open(
            $snapshotClient,
            [IO.FileMode]::Open,
            [IO.FileAccess]::Read,
            [IO.FileShare]::Read
        )

    $privateClientSha = Get-Sha256Hex -Stream $privateGuard
    if ($privateClientSha -ne $expectedClientSha -or
        $privateClientSha -ne $actualClientSha) {
        throw (
            'Invocation-owned exact-v308 client snapshot identity mismatch. ' +
            "Expected: $expectedClientSha " +
            "Source: $actualClientSha " +
            "Snapshot: $privateClientSha"
        )
    }

    # The external caller path is no longer certification authority after this
    # point. Gradle receives only the invocation-owned snapshot, whose read
    # guard denies write/delete sharing until certification has completed.
    $sourceGuard.Dispose()
    $sourceGuard = $null

    . $selector
    $buildJava = Set-LocalLabBuildJava

    Write-Host (
        'CHAT1_CUMULATIVE_PREFLIGHT_PASS ' +
        "head=$headBefore " +
        "clientSha256=$privateClientSha " +
        "invocationOwnedClient=true " +
        "buildJdk=$($buildJava.Version)"
    ) -ForegroundColor Green

    $gradleArgs = @(
        '--no-daemon',
        '--console=plain',
        'clean',
        'chat1CurrentCumulativeCertification',
        "-Pv308ClientPath=$snapshotClient"
    )

    $gradleExit = $null
    $gradleOutput =
        New-Object 'System.Collections.Generic.List[string]'
    $oldErrorActionPreference = $ErrorActionPreference

    $logGuard =
        [IO.File]::Open(
            $log,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::ReadWrite,
            [IO.FileShare]::Read
        )

    # The exact empty log file handle is owned before final parent validation.
    # Build output is written only after both parent roots are revalidated.
    Assert-OrdinaryDirectory -Path $runtimeRoot -Label 'Certification runtime root before log write'
    Assert-OrdinaryDirectory -Path $evidenceRoot -Label 'Certification evidence root before log write'

    $logWriter =
        [IO.StreamWriter]::new(
            $logGuard,
            [Text.UTF8Encoding]::new($true),
            4096,
            $true
        )

    Push-Location (Join-Path $repo 'server')
    try {
        # Windows PowerShell 5.1 may surface redirected native stderr as
        # NativeCommandError records. Keep those non-terminating here and
        # decide success only from the native Gradle exit code.
        $ErrorActionPreference = 'Continue'
        try {
            & $gradlew @gradleArgs 2>&1 |
                ForEach-Object {
                    $line = [string]$_
                    [void]$gradleOutput.Add($line)
                    $logWriter.WriteLine($line)
                    Write-Host $line
                }
            $gradleExit = $LASTEXITCODE
            $logWriter.Flush()
            $logGuard.Flush($true)
        }
        finally {
            $ErrorActionPreference = $oldErrorActionPreference
        }
    }
    finally {
        Pop-Location
    }

    if ($null -eq $gradleExit -or $gradleExit -ne 0) {
        throw (
            'Exact-current cumulative certification failed with Gradle exit code ' +
            [string]$gradleExit
        )
    }

    # Observe the Gradle-owned authoritative marker from the invocation-owned
    # output objects after native exit 0. The mutable log path is audit-only.
    $authoritativeMarker = 'SPAWNPK_CHAT1_CURRENT_CUMULATIVE_CERTIFICATION_PASS'
    $markerMatches = @(
        $gradleOutput |
            Where-Object {
                $_.Contains($authoritativeMarker)
            }
    )

    if ($markerMatches.Count -ne 1) {
        throw (
            'Expected exactly one Gradle-owned cumulative PASS marker in invocation output. ' +
            "Observed: $($markerMatches.Count)"
        )
    }

    $markerLine = [string]$markerMatches[0]
    $serverShaMatch = [regex]::Match(
        $markerLine,
        '(?:^|\s)serverSha256=([0-9a-f]{64})(?=\s|$)'
    )
    if (-not $serverShaMatch.Success) {
        throw 'Gradle-owned cumulative PASS marker lacks a valid serverSha256 field.'
    }
    $certifiedServerSha = $serverShaMatch.Groups[1].Value

    $dirtyAfter = @(Get-TrackedWorktreeChanges)
    if ($dirtyAfter.Count -ne 0) {
        $dirtyAfter | ForEach-Object {
            Write-Host $_ -ForegroundColor Yellow
        }
        throw 'Cumulative certification changed the tracked worktree.'
    }

    $headAfter = Get-ExactGitHead
    if ($headAfter -ne $headBefore) {
        throw (
            'Git HEAD changed during cumulative certification. ' +
            "Before: $headBefore After: $headAfter"
        )
    }

    $logWriter.Dispose()
    $logWriter = $null

    $logBytes = [long]$logGuard.Length
    $logGuard.Position = 0
    $logSha =
        (Get-FileHash -InputStream $logGuard -Algorithm SHA256).Hash.ToLowerInvariant()
    $logLeaf = [IO.Path]::GetFileName($log)

    $logGuard.Dispose()
    $logGuard = $null

    $record = [ordered]@{
        format = 'spawnpk-chat1-local-certification-evidence-v1'
        generatedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
        gitHead = $headBefore
        cleanWorktreeBefore = $true
        cleanWorktreeAfter = $true
        exactV308ClientSha256 = $privateClientSha
        invocationOwnedClient = $true
        gradleTask = 'chat1CurrentCumulativeCertification'
        gradleExitCode = [int]$gradleExit
        authoritativeMarkerObserved = $true
        logFile = $logLeaf
        logSha256 = $logSha
        logBytes = $logBytes
        certifiedServerJarSha256 = $certifiedServerSha
        hostedPromotionSatisfied = $false
        hostedEvidenceSeparate = $true
    }
}
finally {
    try {
        if ($null -ne $logWriter) {
            $logWriter.Dispose()
            $logWriter = $null
        }

        if ($null -ne $logGuard) {
            $logGuard.Dispose()
            $logGuard = $null
        }

        if ($null -ne $privateGuard) {
            $privateGuard.Dispose()
            $privateGuard = $null
        }

        if ($null -ne $sourceGuard) {
            $sourceGuard.Dispose()
            $sourceGuard = $null
        }

        if ($snapshotRootOwned) {
            if ($snapshotClientOwned) {
                Remove-CertificationSnapshotSafely -SnapshotRoot $snapshotRoot -SnapshotClient $snapshotClient
                $snapshotClientOwned = $false
            }
            else {
                # CreateNew never returned an owned client leaf. Remove only an
                # invocation-owned snapshot root that is still provably empty.
                Remove-EmptyCertificationSnapshotRootSafely -SnapshotRoot $snapshotRoot
            }
            $snapshotRootOwned = $false
        }
    }
    finally {
        $env:JAVA_HOME = $oldJavaHome
        $env:Path = $oldPath
    }
}

# Durable success evidence is published only after the invocation-owned client
# guard has been released and its private snapshot root was deleted cleanly.
$json = $record | ConvertTo-Json -Depth 4
$evidenceGuard = $null
$evidenceWriter = $null
$evidenceSha = $null
$evidenceOwned = $false

try {
    $evidenceGuard =
        [IO.File]::Open(
            $evidence,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::ReadWrite,
            [IO.FileShare]::Read
        )
    $evidenceOwned = $true

    # The exact empty JSON evidence handle is owned before final parent
    # validation. Durable evidence is written only after both roots revalidate.
    Assert-OrdinaryDirectory -Path $runtimeRoot -Label 'Certification runtime root before JSON evidence write'
    Assert-OrdinaryDirectory -Path $evidenceRoot -Label 'Certification evidence root before JSON evidence write'

    $evidenceWriter =
        [IO.StreamWriter]::new(
            $evidenceGuard,
            [Text.UTF8Encoding]::new($true),
            4096,
            $true
        )
    $evidenceWriter.WriteLine($json)
    $evidenceWriter.Flush()
    $evidenceGuard.Flush($true)

    $evidenceWriter.Dispose()
    $evidenceWriter = $null

    $evidenceGuard.Position = 0
    $evidenceSha =
        (Get-FileHash -InputStream $evidenceGuard -Algorithm SHA256).Hash.ToLowerInvariant()
}
catch {
    $publishFailure = $_
    $publishCleanupFailures = @()

    if ($null -ne $evidenceWriter) {
        try {
            $evidenceWriter.Dispose()
        }
        catch {
            $publishCleanupFailures +=
                "evidence writer dispose: $($_.Exception.Message)"
        }
        finally {
            $evidenceWriter = $null
        }
    }

    if ($null -ne $evidenceGuard) {
        try {
            $evidenceGuard.Dispose()
        }
        catch {
            $publishCleanupFailures +=
                "evidence guard dispose: $($_.Exception.Message)"
        }
        finally {
            $evidenceGuard = $null
        }
    }

    if ($evidenceOwned) {
        try {
            Remove-CertificationEvidenceLeafSafely -Path $evidence
            $evidenceOwned = $false
        }
        catch {
            $publishCleanupFailures +=
                "evidence leaf cleanup: $($_.Exception.Message)"
        }
    }

    if ($publishCleanupFailures.Count -ne 0) {
        throw [System.Exception]::new(
            (
                'Cumulative certification evidence publication failed and cleanup was unsafe/incomplete. ' +
                "Primary: $($publishFailure.Exception.Message) " +
                "Cleanup: $($publishCleanupFailures -join ' | ')"
            ),
            $publishFailure.Exception
        )
    }

    throw $publishFailure
}
finally {
    if ($null -ne $evidenceWriter) {
        $evidenceWriter.Dispose()
    }
    if ($null -ne $evidenceGuard) {
        $evidenceGuard.Dispose()
    }
}

$evidenceLeaf = [IO.Path]::GetFileName($evidence)
$completion = [pscustomobject]@{
    format = 'spawnpk-chat1-cumulative-result-v1'
    gitHead = $headBefore
    exactV308ClientSha256 = $privateClientSha
    certifiedServerJarSha256 = $certifiedServerSha
    evidenceFile = $evidenceLeaf
    evidenceSha256 = $evidenceSha
}

Write-Host (
    'CHAT1_CUMULATIVE_WRAPPER_COMPLETE ' +
    "exactV308=true " +
    "head=$headBefore " +
    "clientSha256=$privateClientSha " +
    "invocationOwnedClient=true " +
    "serverSha256=$certifiedServerSha " +
    "logSha256=$logSha " +
    "logBytes=$logBytes " +
    "evidence=$evidenceLeaf " +
    "evidenceSha256=$evidenceSha " +
    'hostedPromotionSatisfied=false'
) -ForegroundColor Green

Write-Output $completion
