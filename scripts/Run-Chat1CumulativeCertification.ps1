param(
    [string]$ClientJar = (Join-Path $PSScriptRoot '..\evidence\client(6).jar')
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

$actualClientSha =
    (Get-FileHash -LiteralPath $client -Algorithm SHA256).Hash.ToLowerInvariant()

if ($actualClientSha -ne $expectedClientSha) {
    throw (
        'Exact-v308 certification client SHA-256 mismatch. ' +
        "Expected: $expectedClientSha " +
        "Actual: $actualClientSha " +
        "Path: $client"
    )
}

New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null

$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssfffZ')
$headPrefix = $headBefore.Substring(0, 12)
$log = Join-Path $evidenceRoot (
    "chat1-cumulative-$stamp-$headPrefix.log"
)
$evidence = Join-Path $evidenceRoot (
    "chat1-cumulative-$stamp-$headPrefix.json"
)

$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:Path

try {
    . $selector
    $buildJava = Set-LocalLabBuildJava

    Write-Host (
        'CHAT1_CUMULATIVE_PREFLIGHT_PASS ' +
        "head=$headBefore " +
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

    $gradleExit = $null
    $oldErrorActionPreference = $ErrorActionPreference

    Push-Location (Join-Path $repo 'server')
    try {
        # Windows PowerShell 5.1 may surface redirected native stderr as
        # NativeCommandError records. Keep those non-terminating here and
        # decide success only from the native Gradle exit code.
        $ErrorActionPreference = 'Continue'
        try {
            & $gradlew @gradleArgs 2>&1 |
                Tee-Object -FilePath $log
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

    # Observe the Gradle-owned authoritative marker without printing or
    # embedding that complete marker in this wrapper source.
    $authoritativeMarker =
        'SPAWNPK_CHAT1_CURRENT_' +
        'CUMULATIVE_CERTIFICATION_PASS'

    $markerMatch =
        Select-String -LiteralPath $log -SimpleMatch -Pattern $authoritativeMarker

    if ($null -eq $markerMatch) {
        throw (
            'Gradle returned exit 0 but the authoritative cumulative PASS marker ' +
            'was not observed in the captured log.'
        )
    }

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
        exactV308ClientSha256 = $actualClientSha
        gradleTask = 'chat1CurrentCumulativeCertification'
        gradleExitCode = [int]$gradleExit
        authoritativeMarkerObserved = $true
        logFile = $logItem.Name
        logSha256 = $logSha
        logBytes = [long]$logItem.Length
        hostedPromotionSatisfied = $false
        hostedPromotionBlocker = '#816'
    }

    $json = $record | ConvertTo-Json -Depth 4
    Set-Content -LiteralPath $evidence -Value $json -Encoding UTF8

    Write-Host (
        'CHAT1_CUMULATIVE_WRAPPER_COMPLETE ' +
        "exactV308=true " +
        "head=$headBefore " +
        "clientSha256=$actualClientSha " +
        "logSha256=$logSha " +
        "logBytes=$($logItem.Length) " +
        "evidence=$([IO.Path]::GetFileName($evidence)) " +
        'hostedPromotionSatisfied=false'
    ) -ForegroundColor Green
}
finally {
    $env:JAVA_HOME = $oldJavaHome
    $env:Path = $oldPath
}
