Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$helper = Join-Path $repo 'server/gradle/ExactSha256.ps1'
if (-not (Test-Path -LiteralPath $helper -PathType Leaf)) {
    throw "Pinned Gradle checksum helper missing: $helper"
}
. $helper
$path = [System.IO.Path]::GetTempFileName()
try {
    $cases = @(
        @{ Text = ''; Expected = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855' },
        @{ Text = 'abc'; Expected = 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad' }
    )
    foreach ($case in $cases) {
        [System.IO.File]::WriteAllBytes(
            $path,
            [System.Text.Encoding]::ASCII.GetBytes($case.Text)
        )
        $observed = Get-ExactSha256 -Path $path
        if ($observed -cne $case.Expected) {
            throw "GRADLE_SHA256_KNOWN_VECTOR_MISMATCH expected=$($case.Expected) actual=$observed"
        }
    }
    Write-Host "LOCALLAB_GRADLE_SHA256_DOTNET_PASS vectors=2 pinnedDistributionGateUnchanged=true"
}
finally {
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        Remove-Item -LiteralPath $path -Force
    }
}
