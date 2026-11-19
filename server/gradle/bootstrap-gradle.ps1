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
$gradleHome = Join-Path $cacheRoot "gradle-$version"
$gradleBat = Join-Path $gradleHome 'bin\gradle.bat'

New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null

if (-not (Test-Path -LiteralPath $gradleBat -PathType Leaf)) {
    if (-not (Test-Path -LiteralPath $zip -PathType Leaf)) {
        Write-Host "Downloading Gradle $version..." -ForegroundColor Cyan
        Invoke-WebRequest -UseBasicParsing -Uri $distribution -OutFile $zip
    }

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $stream = [System.IO.File]::OpenRead($zip)
        try {
            $actual = ([BitConverter]::ToString($sha256.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
        }
        finally {
            $stream.Dispose()
        }
    }
    finally {
        $sha256.Dispose()
    }
    if ($actual -ne $expectedSha256) {
        Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue
        throw "Gradle distribution SHA-256 mismatch. expected=$expectedSha256 actual=$actual"
    }

    $tmp = Join-Path $cacheRoot ("extract-" + [Guid]::NewGuid().ToString('N'))
    try {
        # Windows PowerShell 5.1 runners can expose an Expand-Archive
        # implementation that calls a three-argument ExtractToDirectory
        # overload unavailable in its loaded .NET Framework assembly.
        # This two-argument ZipFile API exists in both Windows PowerShell
        # 5.1 and newer .NET runtimes. $tmp is a fresh random path, so
        # overwrite/Force semantics are neither needed nor desirable.
        # The exact distribution SHA-256 was verified ABOVE this line.
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory(
            [string]$zip, [string]$tmp
        )
        $expanded = Join-Path $tmp "gradle-$version"
        if (-not (Test-Path -LiteralPath $expanded -PathType Container)) {
            throw "Gradle archive did not contain gradle-$version"
        }
        Remove-Item -LiteralPath $gradleHome -Recurse -Force -ErrorAction SilentlyContinue
        Move-Item -LiteralPath $expanded -Destination $gradleHome
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