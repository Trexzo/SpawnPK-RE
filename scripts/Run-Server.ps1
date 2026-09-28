Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$selector = Join-Path $PSScriptRoot 'Select-LocalLabJava.ps1'
$jar = Join-Path $repo 'server\build\SpawnPKLocalServer.jar'

foreach ($required in @($selector, $jar)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing LocalLab server-launch component: $required"
    }
}

. $selector
$java = Set-LocalLabJava

Push-Location $repo
try {
    & $java.Path -jar $jar --bootstrap --movement
    $serverExit = $LASTEXITCODE

    if ($serverExit -ne 0) {
        throw "LocalLab server exited with code $serverExit using $($java.Path)"
    }
}
finally {
    Pop-Location
}
