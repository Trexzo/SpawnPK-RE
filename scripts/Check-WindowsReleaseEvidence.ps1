param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$OutputPath = "windows-release-evidence.json"
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = (Resolve-Path -LiteralPath $RepoRoot).Path
$expectedClient = "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"
$historicalV307 = "6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662"
$candidates = @(
    "evidence/client(6).jar",
    "evidence/client(4).jar",
    "evidence/client.jar",
    "client.jar"
)

$observations = @()
foreach ($relative in $candidates) {
    $file = Join-Path $root ($relative.Replace("/", [IO.Path]::DirectorySeparatorChar))
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { continue }

    $digest = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    $observations += [pscustomobject]@{
        relativePath = $relative
        sha256 = $digest
        matchesPinnedV308 = ($digest -eq $expectedClient)
        matchesHistoricalV307 = ($digest -eq $historicalV307)
    }
}
$exact = @($observations | Where-Object { $_.matchesPinnedV308 })
$clientState = if ($exact.Count -gt 0) { "PINNED_V308_PRESENT_NOT_GUI_VERIFIED" }
               elseif ($observations.Count -gt 0) { "MISMATCHED_CLIENT_REFUSED" }
               else { "MISSING_PINNED_CLIENT_BLOCKS_179_AND_GUI" }

# The Windows compilation/synthetic smoke happens in its own CI step.
# This report makes no assertion about whether that step later passed.
$report = [ordered]@{
    formatVersion = 1
    type = "SPAWNPK_WINDOWS_SOURCE_ONLY_EVIDENCE"
    expectedClientV308Sha256 = $expectedClient
    clientState = $clientState
    checkedPaths = $candidates
    observedClientFiles = $observations
    originalClientGuiExercised = $false
    originalClientPacketParityCertified = $false
    historical179TestAcceptanceRun = $false
    durableCrossJvmOwnerEpochVerified = $false
    authenticatedFinalSaveReceiptVerified = $false
    automaticGameplayReplayAuthorized = $false
    meaning = "Informational evidence only; a successful Windows synthetic smoke is not 179-test/original v308 GUI acceptance."
}
$destination = if ([IO.Path]::IsPathRooted($OutputPath)) { $OutputPath }
               else { Join-Path $root $OutputPath }
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $destination -Encoding UTF8
Write-Host ("SPK_WINDOWS_CLIENT_EVIDENCE_STATE=" + $clientState)
Write-Host ("SPK_WINDOWS_EVIDENCE_JSON=" + $destination)
if ($exact.Count -eq 0) {
    Write-Warning "Original pinned v308 client evidence absent; 179-test parity and GUI acceptance are NOT run or passed."
}
