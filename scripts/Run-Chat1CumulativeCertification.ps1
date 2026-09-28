param(
    [string]$ClientJar = (Join-Path $PSScriptRoot '..\evidence\client(6).jar'),
    [string]$EvidenceFileName = ''
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$selector = Join-Path $PSScriptRoot 'Select-LocalLabBuildJava.ps1'
$gradlew = Join-Path $repo 'server\gradlew.bat'
$evidenceRoot = Join-Path $repo 'runtime\certification'
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

New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null

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
$certifiedServerSha = $null

try {
    New-Item -ItemType Directory -Path $snapshotRoot | Out-Null

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
    try {
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
    $oldErrorActionPreference = $ErrorActionPreference
    $gradleOutput = New-Object 'System.Collections.Generic.List[string]'

    Push-Location (Join-Path $repo 'server')
    try {
        # Windows PowerShell 5.1 may surface redirected native stderr as
        # NativeCommandError records. Keep those non-terminating here and
        # decide success only from the native Gradle exit code.
        #
        # Every output object from this exact native invocation is converted
        # to text and retained in invocation-owned memory while the same text
        # remains visible to the operator and is mirrored to the durable log.
        # The mutable log path is audit evidence only and never marker authority.
        $ErrorActionPreference = 'Continue'
        try {
            & $gradlew @gradleArgs 2>&1 |
                Tee-Object -FilePath $log |
                ForEach-Object {
                    $line = [string]$_
                    [void]$gradleOutput.Add($line)
                    Write-Host $line
                }
            $gradleExit = $LASTEXITCODE
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
    # output accumulator only. The durable log is never reopened for authority.
    $markerMatches = @(
        $gradleOutput |
            Where-Object {
                $_.Contains('SPAWNPK_CHAT1_CURRENT_CUMULATIVE_CERTIFICATION_PASS')
            }
    )

    if ($markerMatches.Count -ne 1) {
        throw (
            'Expected exactly one Gradle-owned cumulative PASS marker in the exact invocation output. ' +
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

    $logItem = Get-Item -LiteralPath $log
    $logSha =
        (Get-FileHash -LiteralPath $log -Algorithm SHA256).Hash.ToLowerInvariant()

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
        logFile = $logItem.Name
        logSha256 = $logSha
        logBytes = [long]$logItem.Length
        certifiedServerJarSha256 = $certifiedServerSha
        hostedPromotionSatisfied = $false
        hostedEvidenceSeparate = $true
    }
}
finally {
    try {
        if ($null -ne $privateGuard) {
            $privateGuard.Dispose()
            $privateGuard = $null
        }

        if ($null -ne $sourceGuard) {
            $sourceGuard.Dispose()
            $sourceGuard = $null
        }

        if (Test-Path -LiteralPath $snapshotRoot) {
            Remove-Item -LiteralPath $snapshotRoot -Recurse -Force
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

try {
    $evidenceGuard =
        [IO.File]::Open(
            $evidence,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::ReadWrite,
            [IO.FileShare]::Read
        )

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

    if ($null -ne $evidenceWriter) {
        $evidenceWriter.Dispose()
        $evidenceWriter = $null
    }
    if ($null -ne $evidenceGuard) {
        $evidenceGuard.Dispose()
        $evidenceGuard = $null
    }

    Remove-Item -LiteralPath $evidence -Force -ErrorAction SilentlyContinue
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
    "logBytes=$($logItem.Length) " +
    "evidence=$evidenceLeaf " +
    "evidenceSha256=$evidenceSha " +
    'hostedPromotionSatisfied=false'
) -ForegroundColor Green

Write-Output $completion
