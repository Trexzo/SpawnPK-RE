Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot

$selector = Join-Path $PSScriptRoot 'Select-LocalLabJava.ps1'
$serverRoot = Join-Path $repo 'server'
$gradlew = Join-Path $serverRoot 'gradlew.bat'

$git = Get-Command git.exe -ErrorAction SilentlyContinue
if ($null -eq $git) {
    $git = Get-Command git -ErrorAction SilentlyContinue
}
if ($null -eq $git) {
    throw 'Git is required to bind local certification evidence to an exact source head.'
}

foreach ($required in @($selector,$gradlew)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing local certification component: $required"
    }
}

$statusLines = @(
    & $git.Source -C $repo status --porcelain
)
if ($LASTEXITCODE -ne 0) {
    throw "git status failed with exit code $LASTEXITCODE"
}
if ($statusLines.Count -ne 0) {
    $detail = $statusLines -join '; '
    throw "Local cumulative certification requires a clean worktree. Dirty entries: $detail"
}

$sourceHead = (
    & $git.Source -C $repo rev-parse HEAD
).Trim()
if ($LASTEXITCODE -ne 0 -or
    $sourceHead -notmatch '^[0-9a-fA-F]{40}
    (Join-Path $repo 'runtime\certification')
)
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null

$timestamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmssfffZ')
$runId = "$timestamp-pid$PID"
$log = Join-Path $logRoot ("chat1-current-cumulative-certification-$runId.log")
$evidence = Join-Path $logRoot ("chat1-current-cumulative-certification-$runId.json")

Write-Host 'LOCAL_CUMULATIVE_CERTIFICATION_START hostedPromotionSatisfied=false' -ForegroundColor Cyan
Write-Host "Source HEAD: $sourceHead"
Write-Host ("Java major={0} path={1}" -f $java.Major,$java.Path)
Write-Host "Log: $log"

$oldLocation = Get-Location
try {
    Set-Location $serverRoot
    & $gradlew 'chat1CurrentCumulativeCertification' '--no-daemon' 2>&1 | Tee-Object -FilePath $log
    $gradleExit = $LASTEXITCODE
}
finally {
    Set-Location $oldLocation
}

if ($gradleExit -ne 0) {
    throw "Local cumulative certification Gradle task failed with exit code $gradleExit. Log: $log"
}

$marker = 'SPAWNPK_CHAT1_CURRENT_CUMULATIVE_CERTIFICATION_PASS'
$markerHit = Select-String -LiteralPath $log -SimpleMatch $marker -Quiet
if (-not $markerHit) {
    throw "Gradle exited 0 but cumulative certification marker is missing: $marker"
}

$logHash = (Get-FileHash -LiteralPath $log -Algorithm SHA256).Hash.ToLowerInvariant()
$logSize = (Get-Item -LiteralPath $log).Length

$evidenceRecord = [ordered]@{
    format = 'spawnpk-local-cumulative-certification-v1'
    sourceHead = $sourceHead
    marker = $marker
    markerPresent = $true
    gradleExitCode = 0
    hostedPromotionSatisfied = $false
    javaMajor = [int]$java.Major
    javaPath = $java.Path
    logPath = $log
    logSha256 = $logHash
    logBytes = [long]$logSize
    completedUtc = (Get-Date).ToUniversalTime().ToString('o')
}
$evidenceRecord |
    ConvertTo-Json -Depth 4 |
    Set-Content -LiteralPath $evidence -Encoding UTF8

Write-Host (
    'LOCAL_CUMULATIVE_CERTIFICATION_EXECUTED hostedPromotionSatisfied=false markerPresent=true sourceHead={0} logSha256={1} logBytes={2} log={3} evidence={4}' -f
        $sourceHead,
        $logHash,
        $logSize,
        $log,
        $evidence
) -ForegroundColor Green
Write-Host '#816 remains open until an exact-current GitHub-hosted job receives a runner and executes the cumulative gate.' -ForegroundColor Yellow
) {
    throw "Unable to resolve exact Git HEAD for local certification: $sourceHead"
}
$sourceHead = $sourceHead.ToLowerInvariant()

. $selector
$java = Set-LocalLabJava

$logRoot = [IO.Path]::GetFullPath(
    (Join-Path $repo 'runtime\certification')
)
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null

$timestamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmssZ')
$log = Join-Path $logRoot ("chat1-current-cumulative-certification-$timestamp.log")

Write-Host 'LOCAL_CUMULATIVE_CERTIFICATION_START hostedPromotionSatisfied=false' -ForegroundColor Cyan
Write-Host ("Java major={0} path={1}" -f $java.Major,$java.Path)
Write-Host "Log: $log"

$oldLocation = Get-Location
try {
    Set-Location $serverRoot
    & $gradlew 'chat1CurrentCumulativeCertification' '--no-daemon' 2>&1 | Tee-Object -FilePath $log
    $gradleExit = $LASTEXITCODE
}
finally {
    Set-Location $oldLocation
}

if ($gradleExit -ne 0) {
    throw "Local cumulative certification Gradle task failed with exit code $gradleExit. Log: $log"
}

$marker = 'SPAWNPK_CHAT1_CURRENT_CUMULATIVE_CERTIFICATION_PASS'
$markerHit = Select-String -LiteralPath $log -SimpleMatch $marker -Quiet
if (-not $markerHit) {
    throw "Gradle exited 0 but cumulative certification marker is missing: $marker"
}

$logHash = (Get-FileHash -LiteralPath $log -Algorithm SHA256).Hash.ToLowerInvariant()
$logSize = (Get-Item -LiteralPath $log).Length

Write-Host (
    'LOCAL_CUMULATIVE_CERTIFICATION_EXECUTED hostedPromotionSatisfied=false markerPresent=true logSha256={0} logBytes={1} log={2}' -f
        $logHash,
        $logSize,
        $log
) -ForegroundColor Green
Write-Host '#816 remains open until an exact-current GitHub-hosted job receives a runner and executes the cumulative gate.' -ForegroundColor Yellow
