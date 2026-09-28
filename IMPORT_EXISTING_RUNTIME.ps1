param(
    [Parameter(Mandatory=$true)]
    [string]$From
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = [IO.Path]::GetFullPath($PSScriptRoot).TrimEnd('\','/')

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

function Get-NormalizedPath([string]$Path) {
    return [IO.Path]::GetFullPath($Path).TrimEnd('\','/')
}

function Assert-PathInsideRepository(
    [string]$Path,
    [string]$Label
) {
    $full = Get-NormalizedPath $Path
    $prefix = $repo + [IO.Path]::DirectorySeparatorChar

    if (-not [StringComparer]::OrdinalIgnoreCase.Equals($full, $repo) -and
        -not $full.StartsWith(
            $prefix,
            [StringComparison]::OrdinalIgnoreCase
        )) {
        throw "$Label escapes repository root: $full"
    }

    return $full
}

function Assert-NoReparsePathComponents(
    [string]$Path,
    [string]$Label
) {
    $full = Assert-PathInsideRepository $Path $Label

    $rootItem = Get-Item -LiteralPath $repo -Force
    if (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Repository root must not be a reparse point: $repo"
    }

    if ([StringComparer]::OrdinalIgnoreCase.Equals($full, $repo)) {
        return $full
    }

    $relative = $full.Substring($repo.Length).TrimStart('\','/')
    $cursor = $repo

    foreach ($segment in @($relative -split '[\\/]')) {
        if ([string]::IsNullOrWhiteSpace($segment)) {
            continue
        }

        $cursor = Join-Path $cursor $segment
        if (-not (Test-Path -LiteralPath $cursor)) {
            break
        }

        $item = Get-Item -LiteralPath $cursor -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "$Label path must not traverse a reparse point: $cursor"
        }
    }

    return $full
}

function Assert-RegularDestinationOrMissing(
    [string]$Path,
    [string]$Label
) {
    $full = Assert-NoReparsePathComponents $Path $Label

    if (-not (Test-Path -LiteralPath $full)) {
        return $full
    }

    $item = Get-Item -LiteralPath $full -Force

    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Label destination must not be a reparse point: $full"
    }

    if ($item.PSIsContainer) {
        throw "$Label destination collides with a directory: $full"
    }

    return $full
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

    $destination = Join-Path $repo $item.RelativePath
    $destination = Assert-RegularDestinationOrMissing $destination $item.Label

    $records += [pscustomobject]@{
        Source = $source
        Destination = $destination
        ExpectedSha256 = $item.ExpectedSha256
        Label = $item.Label
        Stage = $null
        Backup = $null
        Existed = $false
    }
}

# Freeze unique destination-directory ownership before any destination mutation.
$destinationDirectories = @()
$seenDestinationDirectories = @{}

foreach ($record in $records) {
    $directory = Get-NormalizedPath (Split-Path -Parent $record.Destination)
    $directory = Assert-NoReparsePathComponents $directory "$($record.Label) destination directory"
    $key = $directory.ToLowerInvariant()

    if (-not $seenDestinationDirectories.ContainsKey($key)) {
        $seenDestinationDirectories[$key] = $true
        $destinationDirectories += [pscustomobject]@{
            Path = $directory
            Existed = (Test-Path -LiteralPath $directory -PathType Container)
        }
    }
}

Write-Host (
    'EXTERNAL_RUNTIME_IMPORT_PREFLIGHT_PASS ' +
    'count=3 exactClient=v308 coherentTriplet=true pathConfinement=true'
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

    # Re-check existing destination paths immediately before backup authority.
    foreach ($record in $records) {
        [void](Assert-RegularDestinationOrMissing $record.Destination $record.Label)
    }

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

    # Create only directories proven absent before this transaction, then prove
    # every destination directory is still an ordinary in-repository directory.
    foreach ($directoryRecord in $destinationDirectories) {
        if (-not $directoryRecord.Existed) {
            New-Item -ItemType Directory -Path $directoryRecord.Path | Out-Null
        }

        [void](Assert-NoReparsePathComponents $directoryRecord.Path 'Runtime import destination directory')
        $directoryItem = Get-Item -LiteralPath $directoryRecord.Path -Force

        if (-not $directoryItem.PSIsContainer) {
            throw "Runtime import destination parent is not a directory: $($directoryRecord.Path)"
        }

        if (($directoryItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Runtime import destination directory became a reparse point: $($directoryRecord.Path)"
        }
    }

    Write-Host (
        'RUNTIME_IMPORT_DESTINATION_DIRECTORIES_READY ' +
        "count=$($destinationDirectories.Count)"
    ) -ForegroundColor Green

    $touched = New-Object 'System.Collections.Generic.List[object]'
    $finalHashes = @{}

    try {
        foreach ($record in $records) {
            [void](Assert-RegularDestinationOrMissing $record.Destination $record.Label)

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
                    [void](Assert-RegularDestinationOrMissing $record.Destination $record.Label)
                    Copy-Item -LiteralPath $record.Backup -Destination $record.Destination -Force

                    $backupSha = Get-ExactSha256 $record.Backup
                    $restoredSha = Get-ExactSha256 $record.Destination
                    if ($restoredSha -ne $backupSha) {
                        throw (
                            "$($record.Label) restored destination hash mismatch. " +
                            "Backup: $backupSha Restored: $restoredSha"
                        )
                    }
                }
                elseif (Test-Path -LiteralPath $record.Destination) {
                    [void](Assert-RegularDestinationOrMissing $record.Destination $record.Label)
                    Remove-Item -LiteralPath $record.Destination -Force
                }
            }
            catch {
                $rollbackFailures += (
                    "$($record.Label): $($_.Exception.Message)"
                )
            }
        }

        $createdDirectories = @(
            $destinationDirectories |
                Where-Object { -not $_.Existed } |
                Sort-Object { $_.Path.Length } -Descending
        )

        foreach ($directoryRecord in $createdDirectories) {
            try {
                if (-not (Test-Path -LiteralPath $directoryRecord.Path)) {
                    continue
                }

                [void](Assert-NoReparsePathComponents $directoryRecord.Path 'Rollback destination directory')
                $directoryItem = Get-Item -LiteralPath $directoryRecord.Path -Force

                if (-not $directoryItem.PSIsContainer) {
                    throw "Rollback-owned destination directory changed type: $($directoryRecord.Path)"
                }

                if (($directoryItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                    throw "Rollback refuses reparse destination directory: $($directoryRecord.Path)"
                }

                $remaining = @(
                    Get-ChildItem -LiteralPath $directoryRecord.Path -Force -ErrorAction Stop
                )

                if ($remaining.Count -ne 0) {
                    throw "Rollback-owned destination directory is not empty: $($directoryRecord.Path)"
                }

                Remove-Item -LiteralPath $directoryRecord.Path -Force
            }
            catch {
                $rollbackFailures += (
                    "DIRECTORY $($directoryRecord.Path): $($_.Exception.Message)"
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
            "restored=$($rollbackTargets.Count) " +
            "directories=$($createdDirectories.Count)"
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
        'exactClient=v308 coherentTriplet=true atomicPublish=true pathConfinement=true'
    ) -ForegroundColor Green
}
finally {
    if (Test-Path -LiteralPath $transactionRoot -PathType Container) {
        Remove-Item -LiteralPath $transactionRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
