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
        ExpectedSha256 = '024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7'
        Label = 'AIRGAP_CLIENT'
    },
    [pscustomobject]@{
        RelativePath = 'local-client\client-localhost.jar'
        ExpectedSha256 = '15ceb89669ddfe0a666e65b4a5291705af692a50e479bbde17e751eceb7fd23e'
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

function Assert-DestinationSnapshotStillOwned([object]$Record) {
    [void](Assert-RegularDestinationOrMissing $Record.Destination $Record.Label)
    $existsNow = Test-Path -LiteralPath $Record.Destination -PathType Leaf

    if ($Record.Existed) {
        if (-not $existsNow) {
            throw "$($Record.Label) destination disappeared after rollback snapshot: $($Record.Destination)"
        }

        $backupSha = Get-ExactSha256 $Record.Backup
        $currentSha = Get-ExactSha256 $Record.Destination

        if ($currentSha -ne $backupSha) {
            throw (
                "$($Record.Label) destination changed after rollback snapshot. " +
                "Snapshot: $backupSha Current: $currentSha"
            )
        }
    }
    elseif ($existsNow) {
        throw "$($Record.Label) destination appeared after rollback snapshot: $($Record.Destination)"
    }
}

function New-SameDirectoryLeafPath(
    [object]$Record,
    [string]$Purpose
) {
    $directory = Split-Path -Parent $Record.Destination
    [void](Assert-NoReparsePathComponents $directory "$($Record.Label) $Purpose directory")

    $leafName = (
        '.' +
        [IO.Path]::GetFileName($Record.Destination) +
        '.spawnpk-import-' +
        $Purpose +
        '-' +
        [Guid]::NewGuid().ToString('N') +
        '.tmp'
    )

    $leaf = Join-Path $directory $leafName
    $leaf = Assert-PathInsideRepository `
        $leaf `
        "$($Record.Label) $Purpose leaf"

    if (Test-Path -LiteralPath $leaf) {
        throw "$($Record.Label) $Purpose leaf collision: $leaf"
    }

    return $leaf
}

function New-VerifiedSameDirectoryLeaf(
    [object]$Record,
    [string]$Source,
    [string]$ExpectedSha256,
    [string]$Purpose
) {
    $leaf = New-SameDirectoryLeafPath $Record $Purpose

    $sourceStream = $null
    $leafStream = $null
    $leafCreated = $false
    $leafComplete = $false
    $leafFailure = $null

    try {
        $sourceStream = [IO.File]::Open(
            $Source,
            [IO.FileMode]::Open,
            [IO.FileAccess]::Read,
            [IO.FileShare]::Read
        )
        $leafStream = [IO.File]::Open(
            $leaf,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
        $leafCreated = $true

        $sourceStream.CopyTo($leafStream)
        $leafStream.Flush($true)
    }
    catch {
        $leafFailure = $_
    }
    finally {
        if ($null -ne $leafStream) {
            $leafStream.Dispose()
        }
        if ($null -ne $sourceStream) {
            $sourceStream.Dispose()
        }
    }

    if ($null -eq $leafFailure) {
        try {
            [void](Assert-RegularDestinationOrMissing $leaf "$($Record.Label) $Purpose leaf")

            $leafSha = Get-ExactSha256 $leaf
            if ($leafSha -ne $ExpectedSha256) {
                throw (
                    "$($Record.Label) $Purpose leaf hash mismatch. " +
                    "Expected: $ExpectedSha256 Actual: $leafSha Path: $leaf"
                )
            }

            $leafComplete = $true
        }
        catch {
            $leafFailure = $_
        }
    }

    if (-not $leafComplete) {
        if ($leafCreated -and (Test-Path -LiteralPath $leaf)) {
            Remove-Item -LiteralPath $leaf -Force -ErrorAction SilentlyContinue
        }

        throw $leafFailure
    }

    return $leaf
}

function Assert-VerifiedOwnedLeaf(
    [string]$Path,
    [string]$ExpectedSha256,
    [string]$Label
) {
    if ([string]::IsNullOrWhiteSpace($Path) -or
        -not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Label owned leaf is missing: $Path"
    }

    [void](Assert-RegularDestinationOrMissing $Path $Label)
    $actual = Get-ExactSha256 $Path

    if ($actual -ne $ExpectedSha256) {
        throw (
            "$Label ownership lost. " +
            "Expected: $ExpectedSha256 Actual: $actual Path: $Path"
        )
    }

    return $Path
}

function Remove-VerifiedOwnedLeaf(
    [string]$Path,
    [string]$ExpectedSha256,
    [string]$Label
) {
    if ([string]::IsNullOrWhiteSpace($Path) -or
        -not (Test-Path -LiteralPath $Path)) {
        return
    }

    [void](Assert-VerifiedOwnedLeaf $Path $ExpectedSha256 $Label)
    Remove-Item -LiteralPath $Path -Force -ErrorAction Stop
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
        PublishLeaf = $null
        Committed = $false
        PublishedSha256 = $null
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
            # Build the publication bytes beside the canonical destination.
            # Any streaming/copy failure can mutate only this create-new,
            # transaction-owned leaf, never the canonical path.
            $record.PublishLeaf =
                New-VerifiedSameDirectoryLeaf `
                    $record `
                    $record.Stage `
                    $record.ExpectedSha256 `
                    'publish'

            # Freeze canonical preimage ownership immediately before the
            # discrete same-directory commit.
            Assert-DestinationSnapshotStillOwned $record

            # Re-prove that the create-new private leaf still contains the
            # exact admitted bytes after canonical snapshot revalidation and
            # immediately before the discrete commit.
            [void](Assert-VerifiedOwnedLeaf `
                $record.PublishLeaf `
                $record.ExpectedSha256 `
                "$($record.Label) publish leaf before commit")

            # Rollback tracking begins before the atomic transition. A failed
            # File.Replace/File.Move leaves Committed=false and therefore has
            # no canonical rollback mutation to reverse.
            $touched.Add($record)

            if ($record.Existed) {
                $backupSha = Get-ExactSha256 $record.Backup
                $displacedLeaf =
                    New-SameDirectoryLeafPath $record 'commit-preimage'

                [IO.File]::Replace(
                    $record.PublishLeaf,
                    $record.Destination,
                    $displacedLeaf,
                    $true
                )
                $record.PublishLeaf = $null
                $record.Committed = $true
                $record.PublishedSha256 = $record.ExpectedSha256

                # File.Replace atomically captures the actual destination
                # preimage. This closes the race between snapshot revalidation
                # and commit: only the exact frozen preimage may be displaced.
                $displacedSha = Get-ExactSha256 $displacedLeaf
                if ($displacedSha -ne $backupSha) {
                    $failedCommitLeaf =
                        New-SameDirectoryLeafPath $record 'failed-commit'

                    [IO.File]::Replace(
                        $displacedLeaf,
                        $record.Destination,
                        $failedCommitLeaf,
                        $true
                    )

                    Remove-VerifiedOwnedLeaf `
                        $failedCommitLeaf `
                        $record.ExpectedSha256 `
                        "$($record.Label) failed commit leaf"

                    $record.Committed = $false
                    $record.PublishedSha256 = $null

                    throw (
                        "$($record.Label) destination changed during atomic commit. " +
                        "Snapshot: $backupSha Displaced: $displacedSha"
                    )
                }

                Remove-VerifiedOwnedLeaf `
                    $displacedLeaf `
                    $backupSha `
                    "$($record.Label) displaced preimage"
            }
            else {
                # Same-directory File.Move is a discrete no-overwrite publish:
                # if a destination appeared after the ownership recheck, the
                # move fails instead of replacing concurrent state.
                [IO.File]::Move(
                    $record.PublishLeaf,
                    $record.Destination
                )
                $record.PublishLeaf = $null
                $record.Committed = $true
                $record.PublishedSha256 = $record.ExpectedSha256
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
                # A failed atomic commit has not streamed bytes into the
                # canonical path. Clean only its still-owned private leaf.
                if (-not $record.Committed) {
                    Remove-VerifiedOwnedLeaf `
                        $record.PublishLeaf `
                        $record.ExpectedSha256 `
                        "$($record.Label) uncommitted publish leaf"
                    $record.PublishLeaf = $null
                    continue
                }

                [void](Assert-RegularDestinationOrMissing $record.Destination $record.Label)

                if (-not (Test-Path -LiteralPath $record.Destination -PathType Leaf)) {
                    throw "$($record.Label) committed destination disappeared before rollback"
                }

                $currentPublishedSha = Get-ExactSha256 $record.Destination
                if ($currentPublishedSha -ne $record.PublishedSha256) {
                    throw (
                        "$($record.Label) rollback ownership lost after publication. " +
                        "Published: $($record.PublishedSha256) Current: $currentPublishedSha"
                    )
                }

                if ($record.Existed) {
                    $backupSha = Get-ExactSha256 $record.Backup
                    $restoreLeaf =
                        New-VerifiedSameDirectoryLeaf `
                            $record `
                            $record.Backup `
                            $backupSha `
                            'rollback-restore'
                    $displacedPublishedLeaf =
                        New-SameDirectoryLeafPath $record 'rollback-published'

                    # Restore the frozen preimage atomically while capturing
                    # the exact canonical bytes displaced by rollback.
                    [IO.File]::Replace(
                        $restoreLeaf,
                        $record.Destination,
                        $displacedPublishedLeaf,
                        $true
                    )

                    $displacedPublishedSha =
                        Get-ExactSha256 $displacedPublishedLeaf

                    if ($displacedPublishedSha -ne $record.PublishedSha256) {
                        # A concurrent replacement won the race after the
                        # ownership check. Put that exact displaced state back
                        # instead of clobbering it with our rollback preimage.
                        $failedRollbackLeaf =
                            New-SameDirectoryLeafPath $record 'failed-rollback'

                        [IO.File]::Replace(
                            $displacedPublishedLeaf,
                            $record.Destination,
                            $failedRollbackLeaf,
                            $true
                        )

                        Remove-VerifiedOwnedLeaf `
                            $failedRollbackLeaf `
                            $backupSha `
                            "$($record.Label) failed rollback leaf"

                        throw (
                            "$($record.Label) rollback ownership changed during atomic restore. " +
                            "Published: $($record.PublishedSha256) " +
                            "Displaced: $displacedPublishedSha"
                        )
                    }

                    Remove-VerifiedOwnedLeaf `
                        $displacedPublishedLeaf `
                        $record.PublishedSha256 `
                        "$($record.Label) rollback displaced publication"

                    $restoredSha = Get-ExactSha256 $record.Destination
                    if ($restoredSha -ne $backupSha) {
                        throw (
                            "$($record.Label) restored destination hash mismatch. " +
                            "Backup: $backupSha Restored: $restoredSha"
                        )
                    }
                }
                else {
                    # Atomically transfer the canonical file to a private
                    # quarantine leaf first. Only exact transaction-published
                    # bytes are then deleted. If a concurrent replacement won
                    # the race, restore that quarantined file immediately.
                    $rollbackRemoveLeaf =
                        New-SameDirectoryLeafPath $record 'rollback-remove'

                    [IO.File]::Move(
                        $record.Destination,
                        $rollbackRemoveLeaf
                    )

                    $removedSha = Get-ExactSha256 $rollbackRemoveLeaf
                    if ($removedSha -ne $record.PublishedSha256) {
                        if (Test-Path -LiteralPath $record.Destination) {
                            throw (
                                "$($record.Label) rollback ownership changed and " +
                                "canonical path was concurrently recreated; " +
                                "quarantined state preserved at $rollbackRemoveLeaf"
                            )
                        }

                        [IO.File]::Move(
                            $rollbackRemoveLeaf,
                            $record.Destination
                        )

                        throw (
                            "$($record.Label) rollback ownership changed before remove. " +
                            "Published: $($record.PublishedSha256) Moved: $removedSha"
                        )
                    }

                    Remove-VerifiedOwnedLeaf `
                        $rollbackRemoveLeaf `
                        $record.PublishedSha256 `
                        "$($record.Label) rollback removal leaf"
                }
            }
            catch {
                $rollbackFailures += (
                    "$($record.Label): $($_.Exception.Message)"
                )
            }
        }

        # Clean any create-new publication leaf left behind by a commit failure.
        foreach ($record in $records) {
            try {
                Remove-VerifiedOwnedLeaf `
                    $record.PublishLeaf `
                    $record.ExpectedSha256 `
                    "$($record.Label) residual publish leaf"
                $record.PublishLeaf = $null
            }
            catch {
                $rollbackFailures += (
                    "$($record.Label) LEAF: $($_.Exception.Message)"
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
