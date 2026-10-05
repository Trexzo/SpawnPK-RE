param(
    [Parameter(Mandatory=$true)]
    [string]$V308ClientPath
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$repo = $PSScriptRoot
$server = Join-Path $repo "server"
$gradle = Join-Path $server "gradlew.bat"
$cumulativeWrapper = Join-Path $repo "scripts\Run-Chat1CumulativeCertification.ps1"
$runtimeJavaSelector = Join-Path $repo "scripts\Select-LocalLabJava.ps1"
$launcherContract = Join-Path $repo "scripts\Test-LauncherContract.ps1"
$expectedV308 = "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"

function Get-ReleaseExactGitHead {
    $lines = @(& git -C $repo rev-parse HEAD)
    if ($LASTEXITCODE -ne 0 -or $lines.Count -ne 1) {
        throw "Unable to resolve exact Git HEAD for current release acceptance."
    }

    $head = ([string]$lines[0]).Trim().ToLowerInvariant()
    if ($head -notmatch '^[0-9a-f]{40}$') {
        throw "Invalid current release Git HEAD identity: $head"
    }

    return $head
}

function Get-ReleaseWorktreeChanges {
    $lines = @(
        & git -C $repo status --porcelain --untracked-files=normal
    )
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect Git worktree for current release acceptance."
    }

    return @(
        $lines |
            Where-Object {
                -not [string]::IsNullOrWhiteSpace([string]$_)
            }
    )
}

function Assert-ReleaseSourceIdentity {
    param(
        [Parameter(Mandatory=$true)]
        [string]$ExpectedHead,
        [Parameter(Mandatory=$true)]
        [string]$Phase
    )

    $dirty = @(Get-ReleaseWorktreeChanges)
    if ($dirty.Count -ne 0) {
        $dirty | ForEach-Object {
            Write-Host $_ -ForegroundColor Yellow
        }
        throw "Current release source identity is dirty at phase '$Phase'."
    }

    $currentHead = Get-ReleaseExactGitHead
    if ($currentHead -ne $ExpectedHead) {
        throw (
            "Current release Git HEAD changed at phase '$Phase'. " +
            "Expected: $ExpectedHead Actual: $currentHead"
        )
    }

    Write-Host (
        "CURRENT_RELEASE_SOURCE_IDENTITY_PASS " +
        "phase=$Phase head=$currentHead clean=true"
    ) -ForegroundColor Green
}

function Assert-ReleaseOrdinaryDirectory {
    param(
        [Parameter(Mandatory=$true)]
        [string]$Path,
        [Parameter(Mandatory=$true)]
        [string]$Label
    )

    try {
        $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    }
    catch {
        throw "$Label is not an ordinary directory: $Path"
    }

    if (-not $item.PSIsContainer) {
        throw "$Label is not an ordinary directory: $Path"
    }

    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Label must not be a reparse point: $Path"
    }
}

function Assert-ReleaseSmokeAncestry {
    param(
        [Parameter(Mandatory=$true)]
        [string]$BuildRoot,
        [Parameter(Mandatory=$true)]
        [string]$SmokeRoot,
        [Parameter(Mandatory=$true)]
        [string]$Phase
    )

    $trimChars = [char[]]@(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $serverFull = [IO.Path]::GetFullPath($server).TrimEnd($trimChars)
    $buildFull = [IO.Path]::GetFullPath($BuildRoot).TrimEnd($trimChars)
    $smokeFull = [IO.Path]::GetFullPath($SmokeRoot).TrimEnd($trimChars)
    $expectedBuild = [IO.Path]::GetFullPath((Join-Path $serverFull 'build')).TrimEnd($trimChars)
    $expectedSmoke = [IO.Path]::GetFullPath((Join-Path $expectedBuild 'release-smoke')).TrimEnd($trimChars)

    if (-not [StringComparer]::OrdinalIgnoreCase.Equals($buildFull, $expectedBuild)) {
        throw "Current release smoke build root escaped repository authority at phase '$Phase': $buildFull"
    }

    if (-not [StringComparer]::OrdinalIgnoreCase.Equals($smokeFull, $expectedSmoke)) {
        throw "Current release smoke evidence root escaped repository authority at phase '$Phase': $smokeFull"
    }

    Assert-ReleaseOrdinaryDirectory -Path $BuildRoot -Label "Current release smoke build root at phase '$Phase'"
    Assert-ReleaseOrdinaryDirectory -Path $SmokeRoot -Label "Current release smoke evidence root at phase '$Phase'"
}

function Initialize-ReleaseSmokeDirectory {
    param(
        [Parameter(Mandatory=$true)]
        [string]$BuildRoot,
        [Parameter(Mandatory=$true)]
        [string]$SmokeRoot
    )

    $trimChars = [char[]]@(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $serverFull = [IO.Path]::GetFullPath($server).TrimEnd($trimChars)
    $buildFull = [IO.Path]::GetFullPath($BuildRoot).TrimEnd($trimChars)
    $smokeFull = [IO.Path]::GetFullPath($SmokeRoot).TrimEnd($trimChars)
    $expectedBuild = [IO.Path]::GetFullPath((Join-Path $serverFull 'build')).TrimEnd($trimChars)
    $expectedSmoke = [IO.Path]::GetFullPath((Join-Path $expectedBuild 'release-smoke')).TrimEnd($trimChars)

    if (-not [StringComparer]::OrdinalIgnoreCase.Equals($buildFull, $expectedBuild)) {
        throw "Current release smoke build root escaped repository authority: $buildFull"
    }

    if (-not [StringComparer]::OrdinalIgnoreCase.Equals($smokeFull, $expectedSmoke)) {
        throw "Current release smoke evidence root escaped repository authority: $smokeFull"
    }

    if (Test-Path -LiteralPath $BuildRoot) {
        Assert-ReleaseOrdinaryDirectory -Path $BuildRoot -Label 'Current release smoke build root'
    }
    else {
        New-Item -ItemType Directory -Path $BuildRoot | Out-Null
        Assert-ReleaseOrdinaryDirectory -Path $BuildRoot -Label 'Current release smoke build root'
    }

    if (Test-Path -LiteralPath $SmokeRoot) {
        Assert-ReleaseOrdinaryDirectory -Path $SmokeRoot -Label 'Current release smoke evidence root'
    }
    else {
        New-Item -ItemType Directory -Path $SmokeRoot | Out-Null
        Assert-ReleaseOrdinaryDirectory -Path $SmokeRoot -Label 'Current release smoke evidence root'
    }

    Assert-ReleaseSmokeAncestry -BuildRoot $BuildRoot -SmokeRoot $SmokeRoot -Phase 'initialized'
}

function Remove-ReleaseSmokeEvidenceLeafSafely {
    param(
        [Parameter(Mandatory=$true)]
        [string]$BuildRoot,
        [Parameter(Mandatory=$true)]
        [string]$SmokeRoot,
        [Parameter(Mandatory=$true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    Assert-ReleaseSmokeAncestry -BuildRoot $BuildRoot -SmokeRoot $SmokeRoot -Phase 'before-evidence-leaf-cleanup'

    try {
        $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    }
    catch {
        throw "Current release smoke evidence cleanup leaf is not an ordinary file: $Path"
    }

    if ($item.PSIsContainer -or (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
        throw "Current release smoke evidence cleanup refuses directory/reparse substitution: $Path"
    }

    $expected = [IO.Path]::GetFullPath($Path)
    if (-not [StringComparer]::OrdinalIgnoreCase.Equals($item.FullName, $expected)) {
        throw "Current release smoke evidence cleanup leaf identity drifted: $($item.FullName)"
    }

    Assert-ReleaseSmokeAncestry -BuildRoot $BuildRoot -SmokeRoot $SmokeRoot -Phase 'immediately-before-evidence-leaf-removal'
    Remove-Item -LiteralPath $Path -Force -ErrorAction Stop
}

function Write-ReleaseSmokeEvidenceTextOwned {
    param(
        [Parameter(Mandatory=$true)]
        [string]$BuildRoot,
        [Parameter(Mandatory=$true)]
        [string]$SmokeRoot,
        [Parameter(Mandatory=$true)]
        [string]$Path,
        [Parameter(Mandatory=$true)]
        [AllowEmptyString()]
        [string]$Text,
        [Parameter(Mandatory=$true)]
        [string]$Label
    )

    Assert-ReleaseSmokeAncestry -BuildRoot $BuildRoot -SmokeRoot $SmokeRoot -Phase ("before-" + $Label + "-create")

    $writer = $null
    $evidenceOwned = $false
    try {
        $writer = [IO.FileStream]::new(
            $Path,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
        $evidenceOwned = $true

        # The leaf identity is now invocation-owned. Revalidate its parent
        # ancestry after acquisition so a concurrent parent substitution
        # cannot redirect the subsequent write.
        Assert-ReleaseSmokeAncestry -BuildRoot $BuildRoot -SmokeRoot $SmokeRoot -Phase ("after-" + $Label + "-create")

        $encoding = [Text.UTF8Encoding]::new($false)
        $bytes = $encoding.GetBytes($Text)
        if ($bytes.Length -ne 0) {
            $writer.Write($bytes, 0, $bytes.Length)
        }
        $writer.Flush()
        $writer.Dispose()
        $writer = $null
    }
    catch {
        $publishFailure = $_
        $publishCleanupFailures = New-Object 'System.Collections.Generic.List[string]'

        if ($null -ne $writer) {
            try {
                $writer.Dispose()
            }
            catch {
                $publishCleanupFailures.Add(
                    "writer=$($_.Exception.Message)"
                )
            }
            finally {
                $writer = $null
            }
        }

        if ($evidenceOwned) {
            try {
                Remove-ReleaseSmokeEvidenceLeafSafely -BuildRoot $BuildRoot -SmokeRoot $SmokeRoot -Path $Path
                $evidenceOwned = $false
            }
            catch {
                $publishCleanupFailures.Add(
                    "leaf=$($_.Exception.Message)"
                )
            }
        }

        if ($publishCleanupFailures.Count -ne 0) {
            throw [System.Exception]::new(
                (
                    "Current release smoke evidence publication failed and cleanup was unsafe/incomplete. " +
                    "Label=$Label Primary: $($publishFailure.Exception.Message) " +
                    "Cleanup: $($publishCleanupFailures -join ' | ')"
                ),
                $publishFailure.Exception
            )
        }

        throw $publishFailure
    }
    finally {
        if ($null -ne $writer) {
            $writer.Dispose()
        }
    }
}

function Get-ReleasePortListeners {
    return @(
        Get-NetTCPConnection -State Listen -ErrorAction Stop |
            Where-Object { $_.LocalPort -in @(43594, 43595) }
    )
}

function Assert-ExactSmokeListenerOwnership {
    param(
        [Parameter(Mandatory=$true)]
        [System.Diagnostics.Process]$ExpectedProcess,
        [Parameter(Mandatory=$true)]
        [string]$Phase
    )

    $ExpectedProcess.Refresh()
    if ($ExpectedProcess.HasExited) {
        throw "Current release exact smoke process exited before listener proof at phase '$Phase'."
    }

    $expectedPid = [int]$ExpectedProcess.Id
    $connections = @(Get-ReleasePortListeners)
    $ports = @(
        $connections |
            Select-Object -ExpandProperty LocalPort -Unique
    )
    $owners = @(
        $connections |
            Select-Object -ExpandProperty OwningProcess -Unique
    )

    if (($ports -notcontains 43594) -or
        ($ports -notcontains 43595) -or
        $owners.Count -ne 1 -or
        [int]$owners[0] -ne $expectedPid) {
        throw (
            "Current release smoke listener ownership mismatch at phase '$Phase'. " +
            "expectedPid=$expectedPid ports=$($ports -join ',') " +
            "owners=$($owners -join ',')"
        )
    }

    # Close the PID-reuse window after the listener snapshot: the same retained
    # Process object must still represent the original live server lifetime.
    $ExpectedProcess.Refresh()
    if ($ExpectedProcess.HasExited) {
        throw "Current release exact smoke process exited after listener snapshot at phase '$Phase'."
    }
}

function Test-LoopbackPort {
    param(
        [Parameter(Mandatory=$true)]
        [int]$Port,
        [int]$TimeoutMs = 500
    )

    $client = New-Object System.Net.Sockets.TcpClient
    $attempt = $null

    try {
        $attempt = $client.BeginConnect("127.0.0.1", $Port, $null, $null)

        if (-not $attempt.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) {
            return $false
        }

        $client.EndConnect($attempt)
        return $true
    }
    catch {
        return $false
    }
    finally {
        if ($null -ne $attempt) {
            $attempt.AsyncWaitHandle.Close()
        }

        $client.Close()
    }
}

function Invoke-CurrentServerLoopbackSmoke {
    param(
        [Parameter(Mandatory=$true)]
        [string]$CanonicalJar,
        [Parameter(Mandatory=$true)]
        [string]$ExpectedServerSha256
    )

    if (-not (Test-Path -LiteralPath $CanonicalJar -PathType Leaf)) {
        throw "Missing cumulative-certified LocalLab server JAR: $CanonicalJar"
    }
    if ($ExpectedServerSha256 -notmatch '^[0-9a-f]{64}$') {
        throw "Invalid cumulative-certified expected server SHA-256: $ExpectedServerSha256"
    }

    $busyBefore = @(Get-ReleasePortListeners)
    if ($busyBefore.Count -ne 0) {
        $busyBefore | Format-Table LocalAddress, LocalPort, OwningProcess -AutoSize
        throw "Current release smoke requires vacant ports 43594/43595 before exact server spawn."
    }

    $smokeBuildRoot = Join-Path $server "build"
    $smokeDir = Join-Path $smokeBuildRoot "release-smoke"
    $stdout = Join-Path $smokeDir "server.stdout.log"
    $stderr = Join-Path $smokeDir "server.stderr.log"
    $versionsPath = Join-Path $smokeDir "versions.txt"
    $shaPath = Join-Path $smokeDir "server-sha256.txt"

    Initialize-ReleaseSmokeDirectory -BuildRoot $smokeBuildRoot -SmokeRoot $smokeDir
    foreach ($evidenceLeaf in @(
        $stdout,
        $stderr,
        $versionsPath,
        $shaPath
    )) {
        Remove-ReleaseSmokeEvidenceLeafSafely -BuildRoot $smokeBuildRoot -SmokeRoot $smokeDir -Path $evidenceLeaf
    }

    $artifactRoot = Join-Path (
        [IO.Path]::GetTempPath()
    ) (
        "SpawnPK-current-release-smoke-" +
        [Guid]::NewGuid().ToString("N")
    )
    $privateJar = Join-Path $artifactRoot "SpawnPKLocalServer.jar"

    $sourceGuard = $null
    $privateWriter = $null
    $privateGuard = $null
    $process = $null
    $stdoutTask = $null
    $stderrTask = $null
    $primaryFailure = $null
    $cleanupFailures = New-Object 'System.Collections.Generic.List[string]'
    $semanticSmokeSucceeded = $false
    $smokeSucceeded = $false
    $certifiedSha = $null
    $spawnedPid = $null

    try {
        New-Item -ItemType Directory -Path $artifactRoot | Out-Null

        $artifactItem = Get-Item -LiteralPath $artifactRoot -Force
        if (($artifactItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Current release private smoke directory is a reparse point: $artifactRoot"
        }

        # Bind the private smoke artifact to one guarded identity of the
        # cumulative-certified build JAR.
        $sourceGuard = [IO.File]::Open(
            $CanonicalJar,
            [IO.FileMode]::Open,
            [IO.FileAccess]::Read,
            [IO.FileShare]::Read
        )
        $certifiedSha = (
            Get-FileHash -InputStream $sourceGuard -Algorithm SHA256
        ).Hash.ToLowerInvariant()

        $sourceGuard.Position = 0
        $privateWriter = [IO.FileStream]::new(
            $privateJar,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
        $sourceGuard.CopyTo($privateWriter)
        $privateWriter.Flush()
        $privateWriter.Dispose()
        $privateWriter = $null
        $sourceGuard.Dispose()
        $sourceGuard = $null

        $privateGuard = [IO.File]::Open(
            $privateJar,
            [IO.FileMode]::Open,
            [IO.FileAccess]::Read,
            [IO.FileShare]::Read
        )
        $privateSha = (
            Get-FileHash -InputStream $privateGuard -Algorithm SHA256
        ).Hash.ToLowerInvariant()

        if ($certifiedSha -ne $ExpectedServerSha256) {
            throw (
                "Current release build-path server SHA does not match cumulative certification. " +
                "Cumulative: $ExpectedServerSha256 BuildPath: $certifiedSha"
            )
        }

        if ($privateSha -ne $ExpectedServerSha256) {
            throw (
                "Current release private server snapshot SHA mismatch. " +
                "Cumulative: $ExpectedServerSha256 Private: $privateSha"
            )
        }

        Write-ReleaseSmokeEvidenceTextOwned `
            -BuildRoot $smokeBuildRoot `
            -SmokeRoot $smokeDir `
            -Path $shaPath `
            -Text ("$certifiedSha  SpawnPKLocalServer.jar" + [Environment]::NewLine) `
            -Label 'server-sha'

        Write-Host (
            "CURRENT_RELEASE_SERVER_SNAPSHOT_VERIFIED " +
            "sha256=$certifiedSha private=true"
        ) -ForegroundColor Green

        # Recheck vacancy immediately before the exact Java spawn.
        $busyAtSpawn = @(Get-ReleasePortListeners)
        if ($busyAtSpawn.Count -ne 0) {
            throw "Current release smoke ports became occupied before exact server spawn."
        }

        $processInfo = New-Object System.Diagnostics.ProcessStartInfo
        $processInfo.FileName = $runtimeJava.Path
        $processInfo.Arguments = '-jar "' + $privateJar + '" --bootstrap --movement'
        $processInfo.WorkingDirectory = $repo
        $processInfo.UseShellExecute = $false
        $processInfo.RedirectStandardOutput = $true
        $processInfo.RedirectStandardError = $true
        $processInfo.CreateNoWindow = $true

        $process = New-Object System.Diagnostics.Process
        $process.StartInfo = $processInfo
        if (-not $process.Start()) {
            throw 'Current release exact server process did not start.'
        }

        # Drain both child pipes asynchronously for the entire process
        # lifetime. No mutable filesystem path participates in stdout/stderr
        # authority.
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $spawnedPid = [int]$process.Id

        $ready = $false
        for ($i = 0; $i -lt 60; $i++) {
            if ($process.HasExited) {
                break
            }

            $connections = @(Get-ReleasePortListeners)
            $ports = @(
                $connections |
                    Select-Object -ExpandProperty LocalPort -Unique
            )
            $owners = @(
                $connections |
                    Select-Object -ExpandProperty OwningProcess -Unique
            )

            if ($connections.Count -ne 0 -and
                ($owners.Count -ne 1 -or [int]$owners[0] -ne $spawnedPid)) {
                throw (
                    "Current release smoke observed unexpected listener ownership. " +
                    "expectedPid=$spawnedPid ports=$($ports -join ',') " +
                    "owners=$($owners -join ',')"
                )
            }

            if (($ports -contains 43594) -and
                ($ports -contains 43595) -and
                $owners.Count -eq 1 -and
                [int]$owners[0] -eq $spawnedPid) {
                $ready = $true
                break
            }

            Start-Sleep -Milliseconds 250
        }

        if (-not $ready) {
            throw (
                "Current release exact server PID $spawnedPid did not own " +
                "both 43594/43595 within the readiness window."
            )
        }

        Assert-ExactSmokeListenerOwnership `
            -ExpectedProcess $process `
            -Phase "before-aux-semantic-check"

        $body = $null
        $lastHttpError = $null

        for ($i = 0; $i -lt 20; $i++) {
            try {
                $response = Invoke-WebRequest `
                    -UseBasicParsing `
                    -Uri "http://127.0.0.1:43595/spk_live/versions.txt" `
                    -TimeoutSec 3
                $body = [string]$response.Content
                break
            }
            catch {
                $lastHttpError = $_

                if ($process.HasExited) {
                    break
                }

                Start-Sleep -Milliseconds 250
            }
        }

        if ($null -eq $body) {
            throw "Current LocalLab AUX versions endpoint did not respond: $lastHttpError"
        }

        foreach ($required in @("cache_version", "sprite_version", "config_version")) {
            if (-not $body.Contains($required)) {
                throw "Current LocalLab versions response is missing '$required'"
            }
        }

        Write-ReleaseSmokeEvidenceTextOwned `
            -BuildRoot $smokeBuildRoot `
            -SmokeRoot $smokeDir `
            -Path $versionsPath `
            -Text ($body + [Environment]::NewLine) `
            -Label 'versions'

        Assert-ExactSmokeListenerOwnership `
            -ExpectedProcess $process `
            -Phase "before-cleanup"

        $semanticSmokeSucceeded = $true
    }
    catch {
        $primaryFailure = $_
    }
    finally {
        if ($null -ne $process) {
            try {
                $process.Refresh()
                if (-not $process.HasExited) {
                    $process.Kill()
                }

                if (-not $process.WaitForExit(5000)) {
                    throw "Exact smoke server PID $($process.Id) did not exit within 5 seconds."
                }

                $process.Refresh()
                if (-not $process.HasExited) {
                    throw "Exact smoke server PID $($process.Id) remains live after cleanup."
                }

                $samePidListeners = @(
                    Get-ReleasePortListeners |
                        Where-Object {
                            [int]$_.OwningProcess -eq [int]$process.Id
                        }
                )
                if ($samePidListeners.Count -ne 0) {
                    throw (
                        "Exact smoke PID $($process.Id) still owns listener ports after cleanup: " +
                        "$(@($samePidListeners.LocalPort) -join ',')"
                    )
                }
            }
            catch {
                $cleanupFailures.Add(
                    "process=$($_.Exception.Message)"
                )
            }
        }

        foreach ($guardName in @("privateGuard", "privateWriter", "sourceGuard")) {
            try {
                $guard = Get-Variable -Name $guardName -ValueOnly
                if ($null -ne $guard) {
                    $guard.Dispose()
                    Set-Variable -Name $guardName -Value $null
                }
            }
            catch {
                $cleanupFailures.Add(
                    "$guardName=$($_.Exception.Message)"
                )
            }
        }

        try {
            if (Test-Path -LiteralPath $privateJar) {
                $privateItem = Get-Item -LiteralPath $privateJar -Force
                if ($privateItem.PSIsContainer -or
                    (($privateItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
                    throw "Private smoke artifact identity changed before cleanup: $privateJar"
                }

                Remove-Item -LiteralPath $privateJar -Force -ErrorAction Stop
            }

            if (Test-Path -LiteralPath $artifactRoot) {
                $rootItem = Get-Item -LiteralPath $artifactRoot -Force
                if (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                    throw "Private smoke directory identity changed before cleanup: $artifactRoot"
                }

                $remaining = @(
                    Get-ChildItem -LiteralPath $artifactRoot -Force -ErrorAction Stop
                )
                if ($remaining.Count -ne 0) {
                    throw (
                        "Private smoke directory is not empty after artifact cleanup: " +
                        "$artifactRoot"
                    )
                }

                Remove-Item -LiteralPath $artifactRoot -Force -ErrorAction Stop
            }
        }
        catch {
            $cleanupFailures.Add(
                "artifact=$($_.Exception.Message)"
            )
        }
    }

    if ($null -eq $primaryFailure -and
        $cleanupFailures.Count -eq 0 -and
        $semanticSmokeSucceeded) {
        try {
            if ($null -eq $stdoutTask -or $null -eq $stderrTask) {
                throw 'Current release smoke process output pipes were not captured.'
            }

            $outText = [string]$stdoutTask.GetAwaiter().GetResult()
            $errText = [string]$stderrTask.GetAwaiter().GetResult()

            Write-ReleaseSmokeEvidenceTextOwned `
                -BuildRoot $smokeBuildRoot `
                -SmokeRoot $smokeDir `
                -Path $stdout `
                -Text $outText `
                -Label 'stdout'
            Write-ReleaseSmokeEvidenceTextOwned `
                -BuildRoot $smokeBuildRoot `
                -SmokeRoot $smokeDir `
                -Path $stderr `
                -Text $errText `
                -Label 'stderr'

            $combinedLog = $outText + "`n" + $errText
            foreach ($requiredLog in @(
                "GAME  : /127.0.0.1:43594",
                "AUX   : /127.0.0.1:43595"
            )) {
                if (-not $combinedLog.Contains($requiredLog)) {
                    throw "Current LocalLab server output pipes are missing '$requiredLog'"
                }
            }

            $smokeSucceeded = $true
        }
        catch {
            $primaryFailure = $_
        }
    }

    if ($null -ne $primaryFailure) {
        if ($cleanupFailures.Count -ne 0) {
            $message = (
                "Current release smoke failed and cleanup was incomplete. " +
                "Primary: $($primaryFailure.Exception.Message) " +
                "Cleanup: $($cleanupFailures -join ' | ')"
            )
            throw [System.Exception]::new(
                $message,
                $primaryFailure.Exception
            )
        }

        throw $primaryFailure
    }

    if ($cleanupFailures.Count -ne 0) {
        throw (
            "Current release smoke cleanup was incomplete: " +
            ($cleanupFailures -join ' | ')
        )
    }

    if (-not $smokeSucceeded) {
        throw "Current release smoke did not reach semantic success."
    }

    Write-Host (
        "CURRENT_RELEASE_SERVER_LOOPBACK_PASS " +
        "game=43594 aux=43595 versions=true " +
        "serverPid=$spawnedPid jarSha256=$certifiedSha cleanup=true"
    ) -ForegroundColor Green

    return [pscustomobject]@{
        ServerSha256 = $certifiedSha
        ServerPid = $spawnedPid
    }
}

foreach ($required in @(
    $gradle,
    $cumulativeWrapper,
    $runtimeJavaSelector,
    $launcherContract
)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing current release component: $required"
    }
}

# Bind the entire release flow to one clean source identity before executing
# any repository selector, regression, Gradle, or build script.
$releaseHead = Get-ReleaseExactGitHead
Assert-ReleaseSourceIdentity -ExpectedHead $releaseHead -Phase "preflight"

$client = (Resolve-Path -LiteralPath $V308ClientPath).Path
if (-not (Test-Path -LiteralPath $client -PathType Leaf)) {
    throw "Exact v308 client is not a file: $client"
}

$actual = (Get-FileHash -LiteralPath $client -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $expectedV308) {
    throw "Exact v308 SHA-256 mismatch.
Expected: $expectedV308
Actual:   $actual
Path:     $client"
}

Write-Host (
    "CURRENT_RELEASE_PREFLIGHT_PASS " +
    "head=$releaseHead clientSha256=$actual clean=true"
) -ForegroundColor Green

$hadCallerJavaHome = Test-Path Env:JAVA_HOME
$callerJavaHome = $env:JAVA_HOME
$callerPath = $env:Path

try {
    . $runtimeJavaSelector
    $runtimeJava = Set-LocalLabJava

    try {
        & $launcherContract -SkipJavaProbe
    }
    catch {
        throw "Launcher contract regression failed: $($_.Exception.Message)"
    }

    Write-Host "CURRENT_RELEASE_LAUNCHER_CONTRACT_PASS" -ForegroundColor Green

    Push-Location $server
    try {
        & $gradle clean build

        if ($LASTEXITCODE -ne 0) {
            throw "Current cumulative Gradle build/focused regression failed with exit code $LASTEXITCODE"
        }

        Write-Host "CURRENT_RELEASE_BUILD_PASS focusedGate=true head=$releaseHead" -ForegroundColor Green
    }
    finally {
        Pop-Location
    }

    Assert-ReleaseSourceIdentity -ExpectedHead $releaseHead -Phase "post-build"

    $cumulativeEvidenceName = (
        "chat1-cumulative-release-" +
        [Guid]::NewGuid().ToString("N") +
        ".json"
    )
    $cumulativeEvidencePath = Join-Path (
        Join-Path $repo "runtime\certification"
    ) $cumulativeEvidenceName

    $cumulativeOutput = $null
    try {
        & $cumulativeWrapper -ClientJar $client -EvidenceFileName $cumulativeEvidenceName |
            Tee-Object -Variable cumulativeOutput
    }
    catch {
        throw "Canonical current cumulative certification failed: $($_.Exception.Message)"
    }

    $cumulativeResults = @(
        @($cumulativeOutput) |
            Where-Object {
                $null -ne $_ -and
                $null -ne $_.PSObject.Properties['format'] -and
                $_.format -eq 'spawnpk-chat1-cumulative-result-v1'
            }
    )
    if ($cumulativeResults.Count -ne 1) {
        throw (
            'Canonical cumulative wrapper did not publish exactly one structured result. ' +
            "Observed: $($cumulativeResults.Count)"
        )
    }

    $cumulativeResult = $cumulativeResults[0]
    if ($cumulativeResult.gitHead -ne $releaseHead -or
        $cumulativeResult.exactV308ClientSha256 -ne $actual -or
        $cumulativeResult.evidenceFile -ne $cumulativeEvidenceName) {
        throw "Canonical cumulative wrapper result identity does not match this release invocation."
    }

    $certifiedServerSha = [string]$cumulativeResult.certifiedServerJarSha256
    $expectedEvidenceSha = [string]$cumulativeResult.evidenceSha256
    if ($certifiedServerSha -notmatch '^[0-9a-f]{64}$' -or
        $expectedEvidenceSha -notmatch '^[0-9a-f]{64}$') {
        throw "Canonical cumulative wrapper result lacks valid SHA-256 identity."
    }

    if (-not (Test-Path -LiteralPath $cumulativeEvidencePath -PathType Leaf)) {
        throw "Canonical cumulative certification evidence is missing: $cumulativeEvidencePath"
    }

    $cumulativeEvidenceGuard = $null
    $cumulativeEvidenceReader = $null
    try {
        $cumulativeEvidenceGuard =
            [IO.File]::Open(
                $cumulativeEvidencePath,
                [IO.FileMode]::Open,
                [IO.FileAccess]::Read,
                [IO.FileShare]::Read
            )

        $actualEvidenceSha = (
            Get-FileHash -InputStream $cumulativeEvidenceGuard -Algorithm SHA256
        ).Hash.ToLowerInvariant()
        if ($actualEvidenceSha -ne $expectedEvidenceSha) {
            throw (
                'Canonical cumulative certification evidence SHA mismatch. ' +
                "Wrapper: $expectedEvidenceSha Evidence: $actualEvidenceSha"
            )
        }

        $cumulativeEvidenceGuard.Position = 0
        $cumulativeEvidenceReader =
            [IO.StreamReader]::new(
                $cumulativeEvidenceGuard,
                [Text.Encoding]::UTF8,
                $true,
                4096,
                $true
            )
        $cumulativeEvidenceText = $cumulativeEvidenceReader.ReadToEnd()
        $cumulativeEvidence = $cumulativeEvidenceText | ConvertFrom-Json
    }
    catch {
        throw "Unable to verify canonical cumulative certification evidence: $($_.Exception.Message)"
    }
    finally {
        if ($null -ne $cumulativeEvidenceReader) {
            $cumulativeEvidenceReader.Dispose()
        }
        if ($null -ne $cumulativeEvidenceGuard) {
            $cumulativeEvidenceGuard.Dispose()
        }
    }

    if ($cumulativeEvidence.format -ne 'spawnpk-chat1-local-certification-evidence-v1' -or
        $cumulativeEvidence.gitHead -ne $releaseHead -or
        $cumulativeEvidence.exactV308ClientSha256 -ne $actual -or
        $cumulativeEvidence.authoritativeMarkerObserved -ne $true -or
        $cumulativeEvidence.certifiedServerJarSha256 -ne $certifiedServerSha) {
        throw "Canonical cumulative certification evidence does not match the in-memory wrapper result."
    }

    Write-Host (
        "CURRENT_RELEASE_CUMULATIVE_CERTIFICATION_PASS " +
        "head=$releaseHead clientSha256=$actual " +
        "serverSha256=$certifiedServerSha hostedPromotionSatisfied=false"
    ) -ForegroundColor Green

    Assert-ReleaseSourceIdentity -ExpectedHead $releaseHead -Phase "post-cumulative"

    $certifiedJar = Join-Path $server "build\SpawnPKLocalServer.jar"
    $smokeEvidence = Invoke-CurrentServerLoopbackSmoke -CanonicalJar $certifiedJar -ExpectedServerSha256 $certifiedServerSha

    Assert-ReleaseSourceIdentity -ExpectedHead $releaseHead -Phase "post-smoke"

    Write-Host (
        "CURRENT_RELEASE_ACCEPTANCE_PASS " +
        "head=$releaseHead clientSha256=$actual " +
        "serverSha256=$($smokeEvidence.ServerSha256) " +
        "hostedPromotionSatisfied=false"
    ) -ForegroundColor Green
}
finally {
    if ($hadCallerJavaHome) {
        $env:JAVA_HOME = $callerJavaHome
    }
    else {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    }
    $env:Path = $callerPath
}
