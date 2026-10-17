param(
    [Parameter(Mandatory=$true)][string]$ClientEvidencePath,
    [Parameter(Mandatory=$true)][string]$LinkCountEvidencePath,
    [string]$OutputPath = "windows-source-release-boundary.json"
)
# NEGATIVE release-evidence gate, never an attestation of launch/GUI parity.
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$expectedV308 = "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"
$historicalV307 = "6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662"
$knownPaths = @("evidence/client(6).jar", "evidence/client(4).jar", "evidence/client.jar", "client.jar")

function Assert-InputFile([string]$Path, [string]$Label) {
    if (-not [IO.File]::Exists($Path)) { throw "RELEASE_BOUNDARY_MISSING_$Label" }
    $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "RELEASE_BOUNDARY_REPARSE_$Label"
    }
}
Assert-InputFile $ClientEvidencePath "CLIENT_JSON"
Assert-InputFile $LinkCountEvidencePath "NLINK_PROBE"
try {
    $evidence = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $ClientEvidencePath).Path) |
        ConvertFrom-Json -ErrorAction Stop
} catch { throw "RELEASE_BOUNDARY_MALFORMED_CLIENT_JSON" }

if ($null -eq $evidence -or
    $evidence.formatVersion -ne 1 -or
    $evidence.type -cne "SPAWNPK_WINDOWS_SOURCE_ONLY_EVIDENCE" -or
    $evidence.expectedClientV308Sha256 -cne $expectedV308) {
    throw "RELEASE_BOUNDARY_CLIENT_CONTRACT_MISMATCH"
}
foreach ($flag in @(
    "originalClientGuiExercised", "originalClientPacketParityCertified",
    "historical179TestAcceptanceRun", "durableCrossJvmOwnerEpochVerified",
    "authenticatedFinalSaveReceiptVerified", "automaticGameplayReplayAuthorized"
)) {
    if ($evidence.$flag -isnot [bool] -or $evidence.$flag -ne $false) {
        throw "RELEASE_BOUNDARY_UNSAFE_AUTHORITY_FLAG: $flag"
    }
}
$paths = @($evidence.checkedPaths)
if ($paths.Count -ne $knownPaths.Count) { throw "RELEASE_BOUNDARY_PATH_SET" }
for ($i=0; $i -lt $knownPaths.Count; $i++) {
    if ($paths[$i] -cne $knownPaths[$i]) { throw "RELEASE_BOUNDARY_PATH_SET" }
}
$observed = @($evidence.observedClientFiles)
$seen = @{}
$validV308 = 0
foreach ($file in $observed) {
    if ($null -eq $file -or
        [string]::IsNullOrWhiteSpace($file.relativePath) -or
        $knownPaths -cnotcontains $file.relativePath -or
        $seen.ContainsKey($file.relativePath)) {
        throw "RELEASE_BOUNDARY_UNEXPECTED_OR_DUPLICATE_CLIENT_PATH"
    }
    $seen[$file.relativePath] = $true
    if ($file.sha256 -isnot [string] -or
        $file.sha256 -cnotmatch '^[0-9a-f]{64}$' -or
        $file.matchesPinnedV308 -isnot [bool] -or
        $file.matchesHistoricalV307 -isnot [bool] -or
        $file.matchesPinnedV308 -ne ($file.sha256 -ceq $expectedV308) -or
        $file.matchesHistoricalV307 -ne ($file.sha256 -ceq $historicalV307)) {
        throw "RELEASE_BOUNDARY_CLIENT_HASH_CONTRADICTION"
    }
    if ($file.matchesPinnedV308) { $validV308++ }
}
$expectedState = if ($validV308 -gt 0) { "PINNED_V308_PRESENT_NOT_GUI_VERIFIED" }
elseif ($observed.Count -gt 0) { "MISMATCHED_CLIENT_REFUSED" }
else { "MISSING_PINNED_CLIENT_BLOCKS_179_AND_GUI" }
if ($evidence.clientState -cne $expectedState) {
    throw "RELEASE_BOUNDARY_CLIENT_STATE_CONTRADICTION"
}
$rawProbe = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $LinkCountEvidencePath).Path)
$probeLines = @($rawProbe -split "\r?\n" |
    Where-Object { $_ -match '^SPK_NLINK_PROBE_STATUS=' })
if ($probeLines.Count -ne 1) {
    throw "RELEASE_BOUNDARY_NLINK_PROBE_MISSING_OR_DUPLICATE"
}
$line = $probeLines[0]
if ($line -cnotmatch '^SPK_NLINK_PROBE_STATUS=([A-Z_]+) strictWorldHardlinkGateUsable=(true|false)(?:\s|$)') {
    throw "RELEASE_BOUNDARY_NLINK_PROBE_MALFORMED"
}
$status = $Matches[1]
$usable = ($Matches[2] -ceq "true")
$negative = @(
    "UNSUPPORTED_UNIX_NLINK_PROVIDER", "UNSAFE_INITIAL_LINK_COUNT",
    "HARDLINK_CREATION_UNAVAILABLE", "HARDLINK_CREATION_FAILED",
    "UNSUPPORTED_AFTER_HARDLINK", "UNSAFE_OR_INCONSISTENT_LINK_COUNT"
)
if ($status -ceq "OBSERVED_SINGLE_AND_DOUBLE_LINK") {
    if (-not $usable -or $line -cnotmatch ' before=1 after=2(?:\s|$)') {
        throw "RELEASE_BOUNDARY_NLINK_UNSUPPORTED_POSITIVE_CLAIM"
    }
} elseif (($negative -ccontains $status) -and (-not $usable)) {
    # A genuine negative observation blocks capability qualification.
} else { throw "RELEASE_BOUNDARY_NLINK_STATUS_CONTRADICTION" }

$reasons = @(
    "ORIGINAL_V308_GUI_NOT_EXERCISED", "ORIGINAL_CLIENT_PACKET_PARITY_NOT_CERTIFIED",
    "HISTORICAL_179_SUITE_NOT_RUN", "DURABLE_CROSS_JVM_OWNER_EPOCH_NOT_VERIFIED",
    "AUTHENTICATED_FINAL_SAVE_ACK_NOT_VERIFIED", "AUTOMATIC_GAMEPLAY_REPLAY_NOT_AUTHORIZED",
    "OWNER_MACHINE_RUNTIME_ACCEPTANCE_MISSING"
)
if (-not $usable) { $reasons += "STRICT_NLINK_PROVIDER_NOT_QUALIFIED" }
$report = [ordered]@{
    formatVersion = 1
    type = "SPAWNPK_WINDOWS_NEGATIVE_RELEASE_BOUNDARY"
    readyForRelease = $false
    decision = "BLOCKED_SOURCE_ONLY_EVIDENCE"
    clientState = $expectedState
    originalClientEvidenceFound = ($validV308 -gt 0)
    originalClientGuiCertified = $false
    historical179Certified = $false
    sourceOnlyBuildCertifiedByThisReport = $false
    strictNlinkProviderObserved = $usable
    strictNlinkProbeStatus = $status
    durableOwnerEpochCertified = $false
    finalSaveAckCertified = $false
    gameplayReplayAuthorized = $false
    reasons = $reasons
    meaning = "Fail-closed consistency check only; green source CI cannot grant original-client or production release authority."
}
$outFile = if ([IO.Path]::IsPathRooted($OutputPath)) { $OutputPath }
else { Join-Path (Get-Location).Path $OutputPath }
if ([IO.Path]::GetFullPath($outFile) -in @(
    [IO.Path]::GetFullPath($ClientEvidencePath),
    [IO.Path]::GetFullPath($LinkCountEvidencePath))) {
    throw "RELEASE_BOUNDARY_REFUSES_OVERWRITE_INPUT"
}
[IO.File]::WriteAllText($outFile, ($report | ConvertTo-Json -Depth 6),
    (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("SPK_WINDOWS_NEGATIVE_RELEASE_BOUNDARY_PASS decision=" + $report.decision +
    " strictNlinkProviderObserved=" + $usable.ToString().ToLowerInvariant() +
    " originalClientGui=false historical179=false")
