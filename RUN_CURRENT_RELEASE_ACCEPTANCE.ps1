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
$expectedV308 = "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"

if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "Missing Gradle wrapper: $gradle"
}

if (-not (Test-Path -LiteralPath $cumulativeWrapper -PathType Leaf)) {
    throw "Missing canonical cumulative certification wrapper: $cumulativeWrapper"
}

if (-not (Test-Path -LiteralPath $runtimeJavaSelector -PathType Leaf)) {
    throw "Missing canonical runtime Java selector: $runtimeJavaSelector"
}

. $runtimeJavaSelector
$runtimeJava = Set-LocalLabJava

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

Write-Host "V308_FIXTURE_SHA256_PASS $actual" -ForegroundColor Green

$launcherContract = Join-Path $repo "scripts\Test-LauncherContract.ps1"
if (-not (Test-Path -LiteralPath $launcherContract -PathType Leaf)) {
    throw "Missing launcher contract regression: $launcherContract"
}

try {
    & $launcherContract -SkipJavaProbe
}
catch {
    throw "Launcher contract regression failed: $($_.Exception.Message)"
}

Write-Host "CURRENT_RELEASE_LAUNCHER_CONTRACT_PASS" -ForegroundColor Green

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
    $jar = Join-Path $server "build\SpawnPKLocalServer.jar"

    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw "Missing built LocalLab server JAR: $jar"
    }

    $smokeDir = Join-Path $server "build\release-smoke"
    $stdout = Join-Path $smokeDir "server.stdout.log"
    $stderr = Join-Path $smokeDir "server.stderr.log"
    $versionsPath = Join-Path $smokeDir "versions.txt"
    $shaPath = Join-Path $smokeDir "server-sha256.txt"

    New-Item -ItemType Directory -Force -Path $smokeDir | Out-Null
    Remove-Item -LiteralPath $stdout, $stderr, $versionsPath, $shaPath -Force -ErrorAction SilentlyContinue

    $process = Start-Process `
        -FilePath $runtimeJava.Path `
        -ArgumentList @("-jar", "`"$jar`"", "--bootstrap", "--movement") `
        -WorkingDirectory $repo `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru

    try {
        $gameReady = $false

        for ($i = 0; $i -lt 60; $i++) {
            if ($process.HasExited) {
                break
            }

            if (Test-LoopbackPort -Port 43594) {
                $gameReady = $true
                break
            }

            Start-Sleep -Milliseconds 250
        }

        if (-not $gameReady) {
            throw "Current LocalLab game listener did not become ready on 127.0.0.1:43594"
        }

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

        $jarSha = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
        Set-Content -LiteralPath $shaPath -Value "$jarSha  SpawnPKLocalServer.jar" -Encoding ASCII

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

        Write-Host "CURRENT_RELEASE_SERVER_LOOPBACK_PASS game=43594 aux=43595 versions=true jarSha256=$jarSha" -ForegroundColor Green
    }
    finally {
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            Wait-Process -Id $process.Id -ErrorAction SilentlyContinue
        }
    }
}

Push-Location $server
try {
    & $gradle clean build

    if ($LASTEXITCODE -ne 0) {
        throw "Current cumulative Gradle build/focused regression failed with exit code $LASTEXITCODE"
    }

    Write-Host "CURRENT_RELEASE_BUILD_PASS focusedGate=true" -ForegroundColor Green
}
finally {
    Pop-Location
}

try {
    & $cumulativeWrapper -ClientJar $client
}
catch {
    throw "Canonical current cumulative certification failed: $($_.Exception.Message)"
}

Write-Host "CURRENT_RELEASE_CUMULATIVE_CERTIFICATION_PASS clientSha256=$actual hostedPromotionSatisfied=false" -ForegroundColor Green

# Smoke the server JAR produced by the exact-current cumulative certification,
# not the earlier ordinary build that the canonical wrapper cleans/rebuilds.
Invoke-CurrentServerLoopbackSmoke
