Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot

$selector = Join-Path $PSScriptRoot 'Select-LocalLabJava.ps1'
$serverRoot = Join-Path $repo 'server'
$gradlew = Join-Path $serverRoot 'gradlew.bat'

foreach ($required in @($selector,$gradlew)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing local certification component: $required"
    }
}

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
