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
        [string]$CanonicalJar
    )

    if (-not (Test-Path -LiteralPath $CanonicalJar -PathType Leaf)) {
        throw "Missing cumulative-certified LocalLab server JAR: $CanonicalJar"
    }

    $busyBefore = @(Get-ReleasePortListeners)
    if ($busyBefore.Count -ne 0) {
        $busyBefore | Format-Table LocalAddress, LocalPort, OwningProcess -AutoSize
        throw "Current release smoke requires vacant ports 43594/43595 before exact server spawn."
    }

    $smokeDir = Join-Path $server "build\release-smoke"
    $stdout = Join-Path $smokeDir "server.stdout.log"
    $stderr = Join-Path $smokeDir "server.stderr.log"
    $versionsPath = Join-Path $smokeDir "versions.txt"
    $shaPath = Join-Path $smokeDir "server-sha256.txt"

    New-Item -ItemType Directory -Force -Path $smokeDir | Out-Null
    Remove-Item -LiteralPath $stdout, $stderr, $versionsPath, $shaPath -Force -ErrorAction SilentlyContinue

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
    $primaryFailure = $null
    $cleanupFailures = New-Object 'System.Collections.Generic.List[string]'
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

        if ($privateSha -ne $certifiedSha) {
            throw (
                "Current release private server snapshot SHA mismatch. " +
                "Certified: $certifiedSha Private: $privateSha"
            )
        }

        Set-Content -LiteralPath $shaPath -Value (
            "$certifiedSha  SpawnPKLocalServer.jar"
        ) -Encoding ASCII

        Write-Host (
            "CURRENT_RELEASE_SERVER_SNAPSHOT_VERIFIED " +
            "sha256=$certifiedSha private=true"
        ) -ForegroundColor Green

        # Recheck vacancy immediately before the exact Java spawn.
        $busyAtSpawn = @(Get-ReleasePortListeners)
        if ($busyAtSpawn.Count -ne 0) {
            throw "Current release smoke ports became occupied before exact server spawn."
        }

        $process = Start-Process `
            -FilePath $runtimeJava.Path `
            -ArgumentList @("-jar", "`"$privateJar`"", "--bootstrap", "--movement") `
            -WorkingDirectory $repo `
            -RedirectStandardOutput $stdout `
            -RedirectStandardError $stderr `
            -PassThru
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

        Set-Content -LiteralPath $versionsPath -Value $body -Encoding ASCII

        Start-Sleep -Milliseconds 100

        $outText = Get-Content -LiteralPath $stdout -Raw -ErrorAction SilentlyContinue
        $errText = Get-Content -LiteralPath $stderr -Raw -ErrorAction SilentlyContinue
        $combinedLog = ([string]$outText) + "`n" + ([string]$errText)

        foreach ($requiredLog in @(
            "GAME  : /127.0.0.1:43594",
            "AUX   : /127.0.0.1:43595"
        )) {
            if (-not $combinedLog.Contains($requiredLog)) {
                throw "Current LocalLab server log is missing '$requiredLog'"
            }
        }

        Assert-ExactSmokeListenerOwnership `
            -ExpectedProcess $process `
            -Phase "before-cleanup"

        $smokeSucceeded = $true
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

    try {
        & $cumulativeWrapper -ClientJar $client
    }
    catch {
        throw "Canonical current cumulative certification failed: $($_.Exception.Message)"
    }

    Write-Host (
        "CURRENT_RELEASE_CUMULATIVE_CERTIFICATION_PASS " +
        "head=$releaseHead clientSha256=$actual hostedPromotionSatisfied=false"
    ) -ForegroundColor Green

    Assert-ReleaseSourceIdentity -ExpectedHead $releaseHead -Phase "post-cumulative"

    $certifiedJar = Join-Path $server "build\SpawnPKLocalServer.jar"
    $smokeEvidence = Invoke-CurrentServerLoopbackSmoke -CanonicalJar $certifiedJar

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
