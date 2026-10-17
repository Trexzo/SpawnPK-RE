# Real PowerShell fixture tests of the source-only negative release decision.
# No original client is manufactured and no original GUI is launched.
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$checker = Join-Path $PSScriptRoot "Assert-WindowsSourceReleaseBoundary.ps1"
$root = Join-Path ([IO.Path]::GetTempPath()) ("SPK-negative-boundary-" + [Guid]::NewGuid().ToString("N"))
$clientEvidenceFile = Join-Path $root "client.json"
$probe = Join-Path $root "probe.txt"
$output = Join-Path $root "decision.json"
$expectedV308 = "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"
$syntheticSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

function New-NegativeClientReport {
    return [ordered]@{
        formatVersion = 1
        type = "SPAWNPK_WINDOWS_SOURCE_ONLY_EVIDENCE"
        expectedClientV308Sha256 = $expectedV308
        clientState = "MISSING_PINNED_CLIENT_BLOCKS_179_AND_GUI"
        checkedPaths = @("evidence/client(6).jar", "evidence/client(4).jar", "evidence/client.jar", "client.jar")
        observedClientFiles = @()
        originalClientGuiExercised = $false
        originalClientPacketParityCertified = $false
        historical179TestAcceptanceRun = $false
        durableCrossJvmOwnerEpochVerified = $false
        authenticatedFinalSaveReceiptVerified = $false
        automaticGameplayReplayAuthorized = $false
    }
}
function Write-Client([object]$Report) {
    [IO.File]::WriteAllText($clientEvidenceFile, ($Report | ConvertTo-Json -Depth 6))
}
function Write-Probe([string]$Line) {
    [IO.File]::WriteAllText($probe, $Line)
}
function Run-Gate {
    & $checker -ClientEvidencePath $clientEvidenceFile -LinkCountEvidencePath $probe -OutputPath $output |
        Out-Null
    return [IO.File]::ReadAllText($output) | ConvertFrom-Json
}
function Assert-Blocked([object]$Decision, [bool]$ExpectedLinkSupported) {
    if ($Decision.readyForRelease -ne $false -or
        $Decision.decision -cne "BLOCKED_SOURCE_ONLY_EVIDENCE" -or
        $Decision.originalClientGuiCertified -ne $false -or
        $Decision.historical179Certified -ne $false -or
        $Decision.finalSaveAckCertified -ne $false -or
        $Decision.gameplayReplayAuthorized -ne $false -or
        $Decision.strictNlinkProviderObserved -ne $ExpectedLinkSupported -or
        @($Decision.reasons).Count -lt 7) {
        throw "RELEASE_BOUNDARY_TEST_UNSAFE_DECISION"
    }
}
function Assert-Rejected([string]$CaseName) {
    if (Test-Path -LiteralPath $output) {
        Remove-Item -LiteralPath $output -Force
    }
    $rejected = $false
    try { [void](Run-Gate) } catch { $rejected = $true }
    if (-not $rejected) { throw "RELEASE_BOUNDARY_TEST_FALSE_ACCEPT: $CaseName" }
    if (Test-Path -LiteralPath $output) {
        throw "RELEASE_BOUNDARY_TEST_EMITTED_DECISION_FOR_INVALID_INPUT: $CaseName"
    }
}

try {
    [void][IO.Directory]::CreateDirectory($root)
    $validMissing = New-NegativeClientReport
    Write-Client $validMissing
    Write-Probe "SPK_NLINK_PROBE_STATUS=UNSUPPORTED_UNIX_NLINK_PROVIDER strictWorldHardlinkGateUsable=false originalClientGui=false"
    Assert-Blocked (Run-Gate) $false

    $validMismatch = New-NegativeClientReport
    $validMismatch.clientState = "MISMATCHED_CLIENT_REFUSED"
    $validMismatch.observedClientFiles = @([ordered]@{
        relativePath = "evidence/client(6).jar"
        sha256 = $syntheticSha
        matchesPinnedV308 = $false
        matchesHistoricalV307 = $false
    })
    Write-Client $validMismatch
    Write-Probe "SPK_NLINK_PROBE_STATUS=OBSERVED_SINGLE_AND_DOUBLE_LINK strictWorldHardlinkGateUsable=true before=1 after=2 originalClientGui=false"
    Assert-Blocked (Run-Gate) $true

    # Reject stale or contradictory release authority in the input artifact.
    foreach ($field in @(
        "originalClientGuiExercised", "originalClientPacketParityCertified",
        "historical179TestAcceptanceRun", "durableCrossJvmOwnerEpochVerified",
        "authenticatedFinalSaveReceiptVerified", "automaticGameplayReplayAuthorized"
    )) {
        $bad = New-NegativeClientReport
        $bad[$field] = $true
        Write-Client $bad
        Assert-Rejected ("forged-" + $field)
    }

    $bad = New-NegativeClientReport
    $bad.expectedClientV308Sha256 = $syntheticSha
    Write-Client $bad
    Assert-Rejected "changed-pinned-v308-sha"

    $bad = New-NegativeClientReport
    $bad.clientState = "PINNED_V308_PRESENT_NOT_GUI_VERIFIED"
    Write-Client $bad
    Assert-Rejected "fabricated-pinned-v308-present"

    $bad = New-NegativeClientReport
    $bad.checkedPaths = @("arbitrary.jar")
    Write-Client $bad
    Assert-Rejected "unrecognized-path-set"

    $bad = New-NegativeClientReport
    $bad.clientState = "MISMATCHED_CLIENT_REFUSED"
    $bad.observedClientFiles = @([ordered]@{
        relativePath = "evidence/client(6).jar"
        sha256 = $syntheticSha
        matchesPinnedV308 = $true
        matchesHistoricalV307 = $false
    })
    Write-Client $bad
    Assert-Rejected "hash-label-contradiction"

    $bad = New-NegativeClientReport
    $bad.observedClientFiles = @()
    Write-Client $bad
    Write-Probe "SPK_NLINK_PROBE_STATUS=UNSUPPORTED_UNIX_NLINK_PROVIDER strictWorldHardlinkGateUsable=true"
    Assert-Rejected "fabricated-nlink-support"

    Write-Probe "SPK_NLINK_PROBE_STATUS=OBSERVED_SINGLE_AND_DOUBLE_LINK strictWorldHardlinkGateUsable=true before=1 after=9"
    Assert-Rejected "fabricated-nlink-count"

    Write-Probe ("SPK_NLINK_PROBE_STATUS=UNSUPPORTED_UNIX_NLINK_PROVIDER strictWorldHardlinkGateUsable=false" +
        [Environment]::NewLine +
        "SPK_NLINK_PROBE_STATUS=OBSERVED_SINGLE_AND_DOUBLE_LINK strictWorldHardlinkGateUsable=true before=1 after=2")
    Assert-Rejected "duplicate-conflicting-nlink-probe"

    Write-Probe "no qualified observation"
    Assert-Rejected "missing-nlink-probe"

    Write-Host "SPK_WINDOWS_RELEASE_BOUNDARY_CONTRACT_PASS validBlocked=2 falsifiedOrMalformedRejected=14 genuineClientSpoofed=false"
}
finally {
    if ([IO.Directory]::Exists($root)) {
        $item = Get-Item -LiteralPath $root -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "RELEASE_BOUNDARY_TEST_REFUSES_REPARSE_CLEANUP"
        }
        Remove-Item -LiteralPath $root -Recurse -Force
    }
}
