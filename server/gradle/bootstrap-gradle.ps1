param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$version = '8.10.2'
$expectedSha256 = '31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26'
$distribution = "https://services.gradle.org/distributions/gradle-$version-bin.zip"

$serverRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $serverRoot
$selector = Join-Path $repoRoot 'scripts\Select-LocalLabBuildJava.ps1'
if (-not (Test-Path -LiteralPath $selector -PathType Leaf)) {
    throw "Missing JDK 21 selector: $selector"
}
. $selector
[void](Set-LocalLabBuildJava)

$cacheRoot = Join-Path ([Environment]::GetFolderPath('UserProfile')) ".gradle\locallab-bootstrap"
$zip = Join-Path $cacheRoot "gradle-$version-bin.zip"
$home = Join-Path $cacheRoot "gradle-$version"
$gradleBat = Join-Path $home 'bin\gradle.bat'

New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null

if (-not (Test-Path -LiteralPath $gradleBat -PathType Leaf)) {
    if (-not (Test-Path -LiteralPath $zip -PathType Leaf)) {
        Write-Host "Downloading Gradle $version..." -ForegroundColor Cyan
        Invoke-WebRequest -UseBasicParsing -Uri $distribution -OutFile $zip
    }

    $actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expectedSha256) {
        Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue
        throw "Gradle distribution SHA-256 mismatch. expected=$expectedSha256 actual=$actual"
    }

    $tmp = Join-Path $cacheRoot ("extract-" + [Guid]::NewGuid().ToString('N'))
    try {
        Expand-Archive -LiteralPath $zip -DestinationPath $tmp -Force
        $expanded = Join-Path $tmp "gradle-$version"
        if (-not (Test-Path -LiteralPath $expanded -PathType Container)) {
            throw "Gradle archive did not contain gradle-$version"
        }
        Remove-Item -LiteralPath $home -Recurse -Force -ErrorAction SilentlyContinue
        Move-Item -LiteralPath $expanded -Destination $home
    }
    finally {
        Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Push-Location $serverRoot
try {
    & $gradleBat @GradleArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
