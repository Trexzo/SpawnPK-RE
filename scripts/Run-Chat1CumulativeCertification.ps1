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

foreach ($required in @(
    $selector,
    $gradlew
)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing cumulative certification component: $required"
    }
}

$git = Get-Command git.exe -ErrorAction SilentlyContinue
if ($null -eq $git) {
    $git = Get-Command git -ErrorAction SilentlyContinue
}
if ($null -eq $git) {
    throw 'Git is required to bind cumulative certification evidence to an exact source head.'
}

function Get-TrackedWorktreeChanges {
    $lines = @(
        & $git.Source -C $repo status --porcelain --untracked-files=all
    )
    if ($LASTEXITCODE -ne 0) {
        throw "git status failed with exit code $LASTEXITCODE"
    }
    return $lines
}

function Get-ExactGitHead {
    $lines = @(
        & $git.Source -C $repo rev-parse HEAD
    )
    if ($LASTEXITCODE -ne 0 -or $lines.Count -ne 1) {
        throw "Unable to resolve exact Git HEAD."
    }

    $head = ([string]$lines[0]).Trim().ToLowerInvariant()
    if ($head -notmatch '^[0-9a-f]{40}
if ($dirtyBefore.Count -ne 0) {
    throw (
        'Cumulative certification requires a clean worktree before execution. ' +
        ($dirtyBefore -join '; ')
    )
}

$sourceHead = Get-ExactGitHead

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

$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:Path

try {
    . $selector
    $buildJava = Set-LocalLabBuildJava

    New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
    $timestamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmssfffZ')
    $runId = "$timestamp-pid$PID-$($sourceHead.Substring(0,12))"
    $log = Join-Path $evidenceRoot ("chat1-current-cumulative-$runId.log")
    $evidence = Join-Path $evidenceRoot ("chat1-current-cumulative-$runId.json")

    Write-Host (
        'CHAT1_CUMULATIVE_PREFLIGHT_PASS ' +
        "sourceHead=$sourceHead " +
        "clientSha256=$actualClientSha " +
        "buildJdk=$($buildJava.Version) " +
        'hostedPromotionSatisfied=false'
    ) -ForegroundColor Green
    Write-Host "Certification log: $log"

    $gradleArgs = @(
        '--no-daemon',
        '--console=plain',
        'clean',
        'chat1CurrentCumulativeCertification',
        "-Pv308ClientPath=$client"
    )

    Push-Location (Join-Path $repo 'server')
    try {
        & $gradlew @gradleArgs 2>&1 | Tee-Object -FilePath $log
        $gradleExit = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    if ($gradleExit -ne 0) {
        throw (
            'Exact-current cumulative certification failed with Gradle exit code ' +
            "$gradleExit. Log: $log"
        )
    }

    # Keep the authoritative release marker owned by Gradle. The wrapper
    # assembles the search token only after execution and never prints it.
    $authoritativeMarker =
        'SPAWNPK_CHAT1_CURRENT_' +
        'CUMULATIVE_CERTIFICATION_PASS'
    $markerObserved =
        Select-String -LiteralPath $log -SimpleMatch $authoritativeMarker -Quiet

    if (-not $markerObserved) {
        throw (
            'Gradle exited 0 but the authoritative cumulative certification ' +
            "marker was not observed in the captured log: $log"
        )
    }

    $dirtyAfter = @(Get-TrackedWorktreeChanges)
    if ($dirtyAfter.Count -ne 0) {
        throw (
            'Cumulative certification changed tracked/untracked source state. ' +
            ($dirtyAfter -join '; ')
        )
    }

    $sourceHeadAfter = Get-ExactGitHead
    if ($sourceHeadAfter -ne $sourceHead) {
        throw (
            'Cumulative certification source HEAD changed during execution. ' +
            "Before: $sourceHead After: $sourceHeadAfter"
        )
    }

    $logHash =
        (Get-FileHash -LiteralPath $log -Algorithm SHA256).Hash.ToLowerInvariant()
    $logBytes =
        [long](Get-Item -LiteralPath $log).Length

    $evidenceRecord = [ordered]@{
        format = 'spawnpk-chat1-cumulative-certification-evidence-v1'
        sourceHead = $sourceHead
        exactV308ClientSha256 = $actualClientSha
        gradleExitCode = 0
        authoritativeMarkerObserved = $true
        hostedPromotionSatisfied = $false
        buildJdk = $buildJava.Version
        javaHome = $env:JAVA_HOME
        logPath = $log
        logSha256 = $logHash
        logBytes = $logBytes
        completedUtc = (Get-Date).ToUniversalTime().ToString('o')
    }

    $evidenceRecord |
        ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath $evidence -Encoding UTF8

    # This wrapper marker reports local execution evidence only.
    Write-Host (
        'CHAT1_CUMULATIVE_WRAPPER_COMPLETE ' +
        "sourceHead=$sourceHead " +
        'exactV308=true ' +
        "clientSha256=$actualClientSha " +
        'authoritativeMarkerObserved=true ' +
        'hostedPromotionSatisfied=false ' +
        "logSha256=$logHash " +
        "logBytes=$logBytes " +
        "evidence=$evidence"
    ) -ForegroundColor Green
}
finally {
    $env:JAVA_HOME = $oldJavaHome
    $env:Path = $oldPath
}
) {
        throw "Invalid Git HEAD identity: $head"
    }

    return $head
}

$dirtyBefore = @(Get-TrackedWorktreeChanges)
if ($dirtyBefore.Count -ne 0) {
    throw (
        'Cumulative certification requires a clean worktree before execution. ' +
        ($dirtyBefore -join '; ')
    )
}

$sourceHead = (
    & $git.Source -C $repo rev-parse HEAD
).Trim()
if ($LASTEXITCODE -ne 0 -or
    $sourceHead -notmatch '^[0-9a-fA-F]{40}$') {
    throw "Unable to resolve exact Git HEAD for cumulative certification: $sourceHead"
}
$sourceHead = $sourceHead.ToLowerInvariant()

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

$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:Path

try {
    . $selector
    $buildJava = Set-LocalLabBuildJava

    New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
    $timestamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmssfffZ')
    $runId = "$timestamp-pid$PID-$($sourceHead.Substring(0,12))"
    $log = Join-Path $evidenceRoot ("chat1-current-cumulative-$runId.log")
    $evidence = Join-Path $evidenceRoot ("chat1-current-cumulative-$runId.json")

    Write-Host (
        'CHAT1_CUMULATIVE_PREFLIGHT_PASS ' +
        "sourceHead=$sourceHead " +
        "clientSha256=$actualClientSha " +
        "buildJdk=$($buildJava.Version) " +
        'hostedPromotionSatisfied=false'
    ) -ForegroundColor Green
    Write-Host "Certification log: $log"

    $gradleArgs = @(
        '--no-daemon',
        '--console=plain',
        'clean',
        'chat1CurrentCumulativeCertification',
        "-Pv308ClientPath=$client"
    )

    Push-Location (Join-Path $repo 'server')
    try {
        & $gradlew @gradleArgs 2>&1 | Tee-Object -FilePath $log
        $gradleExit = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    if ($gradleExit -ne 0) {
        throw (
            'Exact-current cumulative certification failed with Gradle exit code ' +
            "$gradleExit. Log: $log"
        )
    }

    # Keep the authoritative release marker owned by Gradle. The wrapper
    # assembles the search token only after execution and never prints it.
    $authoritativeMarker =
        'SPAWNPK_CHAT1_CURRENT_' +
        'CUMULATIVE_CERTIFICATION_PASS'
    $markerObserved =
        Select-String -LiteralPath $log -SimpleMatch $authoritativeMarker -Quiet

    if (-not $markerObserved) {
        throw (
            'Gradle exited 0 but the authoritative cumulative certification ' +
            "marker was not observed in the captured log: $log"
        )
    }

    $dirtyAfter = @(Get-TrackedWorktreeChanges)
    if ($dirtyAfter.Count -ne 0) {
        throw (
            'Cumulative certification changed tracked/untracked source state. ' +
            ($dirtyAfter -join '; ')
        )
    }

    $logHash =
        (Get-FileHash -LiteralPath $log -Algorithm SHA256).Hash.ToLowerInvariant()
    $logBytes =
        [long](Get-Item -LiteralPath $log).Length

    $evidenceRecord = [ordered]@{
        format = 'spawnpk-chat1-cumulative-certification-evidence-v1'
        sourceHead = $sourceHead
        exactV308ClientSha256 = $actualClientSha
        gradleExitCode = 0
        authoritativeMarkerObserved = $true
        hostedPromotionSatisfied = $false
        buildJdk = $buildJava.Version
        javaHome = $env:JAVA_HOME
        logPath = $log
        logSha256 = $logHash
        logBytes = $logBytes
        completedUtc = (Get-Date).ToUniversalTime().ToString('o')
    }

    $evidenceRecord |
        ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath $evidence -Encoding UTF8

    # This wrapper marker reports local execution evidence only.
    Write-Host (
        'CHAT1_CUMULATIVE_WRAPPER_COMPLETE ' +
        "sourceHead=$sourceHead " +
        'exactV308=true ' +
        "clientSha256=$actualClientSha " +
        'authoritativeMarkerObserved=true ' +
        'hostedPromotionSatisfied=false ' +
        "logSha256=$logHash " +
        "logBytes=$logBytes " +
        "evidence=$evidence"
    ) -ForegroundColor Green
}
finally {
    $env:JAVA_HOME = $oldJavaHome
    $env:Path = $oldPath
}
