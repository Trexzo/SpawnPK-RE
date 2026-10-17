Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$checker = Join-Path $repo 'scripts/Check-WindowsReleaseEvidence.ps1'
$root = Join-Path ([IO.Path]::GetTempPath()) (
    "SpawnPK-v308-negative-evidence-" + [Guid]::NewGuid().ToString('N')
)
$expectedPinned = '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6'
$testClientBytes = [byte[]]@(83, 80, 75, 45, 78, 79, 84, 45, 86, 51, 48, 56)
$clientFile = Join-Path $root 'evidence/client(6).jar'

function Assert-ClientNegative {
    param([Parameter(Mandatory=$true)][object]$Report)
    if ($Report.expectedClientV308Sha256 -cne $expectedPinned -or
        $Report.originalClientGuiExercised -ne $false -or
        $Report.originalClientPacketParityCertified -ne $false -or
        $Report.historical179TestAcceptanceRun -ne $false -or
        $Report.authenticatedFinalSaveReceiptVerified -ne $false -or
        $Report.automaticGameplayReplayAuthorized -ne $false) {
        throw 'Negative client evidence incorrectly asserted release authority.'
    }
}

try {
    [void][IO.Directory]::CreateDirectory($root)
    $absentJson = Join-Path $root 'absent.json'
    & $checker -RepoRoot $root -OutputPath $absentJson |
        Out-Null
    $absent = Get-Content -LiteralPath $absentJson -Raw |
        ConvertFrom-Json
    Assert-ClientNegative -Report $absent
    if ($absent.clientState -cne 'MISSING_PINNED_CLIENT_BLOCKS_179_AND_GUI' -or
        @($absent.observedClientFiles).Count -ne 0) {
        throw 'Absent v308 evidence did not fail closed.'
    }

    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $clientFile))
    [IO.File]::WriteAllBytes($clientFile, $testClientBytes)
    $mismatchJson = Join-Path $root 'mismatch.json'
    & $checker -RepoRoot $root -OutputPath $mismatchJson |
        Out-Null
    $mismatch = Get-Content -LiteralPath $mismatchJson -Raw |
        ConvertFrom-Json
    Assert-ClientNegative -Report $mismatch
    $observed = @($mismatch.observedClientFiles)
    if ($mismatch.clientState -cne 'MISMATCHED_CLIENT_REFUSED' -or
        $observed.Count -ne 1 -or
        $observed[0].matchesPinnedV308 -ne $false -or
        $observed[0].sha256 -notmatch '^[0-9a-f]{64}$') {
        throw 'Synthetic non-v308 JAR was promoted to positive client evidence.'
    }

    Write-Host (
        'SPK_WINDOWS_CLIENT_EVIDENCE_NEGATIVE_CASES_PASS ' +
        'cases=2 pinnedV308Unchanged=true syntheticGui=false ' +
        'hashCmdletIndependent=true'
    )
}
finally {
    if (Test-Path -LiteralPath $root -PathType Container) {
        $rootItem = Get-Item -LiteralPath $root -Force
        if (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Refusing removal of reparse-positive test root: $root"
        }
        Remove-Item -LiteralPath $root -Recurse -Force
    }
}
