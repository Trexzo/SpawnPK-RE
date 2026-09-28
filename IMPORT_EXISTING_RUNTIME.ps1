param(
    [Parameter(Mandatory=$true)]
    [string]$From
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = $PSScriptRoot

$items = @(
    [pscustomobject]@{
        RelativePath = 'evidence\client(6).jar'
        ExpectedSha256 = '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6'
        Label = 'PINNED_CLIENT'
    },
    [pscustomobject]@{
        RelativePath = 'local-client\client-airgap.jar'
        ExpectedSha256 = '83b3e27e2aae50512d044ae4c74d84afb36df8b8a8051b5eb0c9275427363c33'
        Label = 'AIRGAP_CLIENT'
    },
    [pscustomobject]@{
        RelativePath = 'local-client\client-localhost.jar'
        ExpectedSha256 = '01c878a56ee25fb112dfe8b459dbd11ea26cfa8a92a7f287a4e5ee53f673cdbd'
        Label = 'LOCALHOST_CLIENT'
    }
)

function Get-ExactSha256([string]$Path) {
    return (
        Get-FileHash -LiteralPath $Path -Algorithm SHA256
    ).Hash.ToLowerInvariant()
}

$records = @()

# Prove the complete source triplet before creating any destination state.
foreach ($item in $items) {
    $source = Join-Path $From $item.RelativePath

    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "$($item.Label) missing: $source"
    }

    $actual = Get-ExactSha256 $source
    if ($actual -ne $item.ExpectedSha256) {
        throw (
            "$($item.Label) source hash mismatch. " +
            "Expected: $($item.ExpectedSha256) Actual: $actual"
        )
    }

    $records += [pscustomobject]@{
        Source = $source
        Destination = Join-Path $repo $item.RelativePath
        ExpectedSha256 = $item.ExpectedSha256
        Label = $item.Label
        Stage = $null
        Backup = $null
        Existed = $false
    }
}

Write-Host (
    'EXTERNAL_RUNTIME_IMPORT_PREFLIGHT_PASS ' +
    'count=3 exactClient=v308 coherentTriplet=true'
) -ForegroundColor Green

$transactionRoot = Join-Path (
    [IO.Path]::GetTempPath()
) (
    'SpawnPK-runtime-import-' + [Guid]::NewGuid().ToString('N')
)

New-Item -ItemType Directory -Path $transactionRoot -Force | Out-Null

try {
    $index = 0

    # Freeze the exact validated source bytes into transaction-owned staging.
    foreach ($record in $records) {
        $stage = Join-Path $transactionRoot ("stage-{0}.jar" -f $index)
        Copy-Item -LiteralPath $record.Source -Destination $stage -Force

        $stageSha = Get-ExactSha256 $stage
        if ($stageSha -ne $record.ExpectedSha256) {
            throw (
                "$($record.Label) staged hash mismatch. " +
                "Expected: $($record.ExpectedSha256) Actual: $stageSha"
            )
        }

        $record.Stage = $stage
        $index++
    }

    Write-Host 'RUNTIME_IMPORT_STAGE_VERIFIED count=3' -ForegroundColor Green

    # Snapshot every existing destination before publishing any staged artifact.
    $index = 0
    foreach ($record in $records) {
        if (Test-Path -LiteralPath $record.Destination -PathType Leaf) {
            $backup = Join-Path $transactionRoot ("backup-{0}.jar" -f $index)
            Copy-Item -LiteralPath $record.Destination -Destination $backup -Force

            $destinationSha = Get-ExactSha256 $record.Destination
            $backupSha = Get-ExactSha256 $backup
            if ($destinationSha -ne $backupSha) {
                throw (
                    "$($record.Label) rollback backup hash mismatch. " +
                    "Destination: $destinationSha Backup: $backupSha"
                )
            }

            $record.Existed = $true
            $record.Backup = $backup
        }

        $index++
    }

    Write-Host 'RUNTIME_IMPORT_BACKUP_READY count=3' -ForegroundColor Green

    $touched = New-Object 'System.Collections.Generic.List[object]'

    $finalHashes = @{}

    try {
        foreach ($record in $records) {
            $destinationDirectory = Split-Path -Parent $record.Destination
            New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null

            # Add rollback ownership before the first destination write.
            $touched.Add($record)

            Copy-Item -LiteralPath $record.Stage -Destination $record.Destination -Force

            $publishedSha = Get-ExactSha256 $record.Destination
            if ($publishedSha -ne $record.ExpectedSha256) {
                throw (
                    "$($record.Label) published hash mismatch. " +
                    "Expected: $($record.ExpectedSha256) Actual: $publishedSha"
                )
            }
        }

        # Keep final whole-triplet verification inside rollback ownership.
        foreach ($record in $records) {
            if (-not (Test-Path -LiteralPath $record.Destination -PathType Leaf)) {
                throw "$($record.Label) destination missing after publication: $($record.Destination)"
            }

            $finalSha = Get-ExactSha256 $record.Destination
            if ($finalSha -ne $record.ExpectedSha256) {
                throw (
                    "$($record.Label) final destination hash mismatch. " +
                    "Expected: $($record.ExpectedSha256) Actual: $finalSha"
                )
            }

            $finalHashes[$record.Label] = $finalSha
        }

        Write-Host 'RUNTIME_IMPORT_FINAL_VERIFY_PASS count=3' -ForegroundColor Green
    }
    catch {
        $publishFailure = $_
        $rollbackFailures = @()
        $rollbackTargets = @($touched)
        [array]::Reverse($rollbackTargets)

        foreach ($record in $rollbackTargets) {
            try {
                if ($record.Existed) {
                    Copy-Item -LiteralPath $record.Backup -Destination $record.Destination -Force
                }
                elseif (Test-Path -LiteralPath $record.Destination -PathType Leaf) {
                    Remove-Item -LiteralPath $record.Destination -Force
                }
            }
            catch {
                $rollbackFailures += (
                    "$($record.Label): $($_.Exception.Message)"
                )
            }
        }

        if ($rollbackFailures.Count -ne 0) {
            throw (
                'External runtime import failed and rollback was incomplete. ' +
                "Publish failure: $($publishFailure.Exception.Message) " +
                "Rollback failures: $($rollbackFailures -join ' | ')"
            )
        }

        Write-Host (
            'RUNTIME_IMPORT_ROLLBACK_COMPLETE ' +
            "restored=$($rollbackTargets.Count)"
        ) -ForegroundColor Yellow

        throw $publishFailure
    }

    foreach ($record in $records) {
        Write-Host (
            "$($record.Label) IMPORTED sha256=$($finalHashes[$record.Label])"
        ) -ForegroundColor Green
    }

    Write-Host (
        'EXTERNAL_RUNTIME_IMPORT_PASS ' +
        'exactClient=v308 coherentTriplet=true atomicPublish=true'
    ) -ForegroundColor Green
}
finally {
    if (Test-Path -LiteralPath $transactionRoot -PathType Container) {
        Remove-Item -LiteralPath $transactionRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
