<#
R24 manual Windows Sandbox guest-side static game cache integrity preflight.
Run AFTER opening R24 .wsb, but BEFORE any game or LocalLab Java process.
It first runs the R23 guest network/loopback/asset preflight in its own
PowerShell process, then verifies the exact pinned R12 10,764-file
allowlisted static cache copy. No Java, game, network or firewall mutation.
Expected failures exit 2. This is a static hash check, not content privacy
certification of the opaque binary cache.
#>
$ErrorActionPreference = 'Stop'
try {
    if ($env:USERNAME -cne 'WDAGUtilityAccount') {
        throw 'R24_BLOCKED: Windows Sandbox guest account required'
    }
    $assets='C:\SPKR23\assets'
    $r23=Join-Path $assets 'guest-preflight.ps1'
    if (-not (Test-Path -LiteralPath $r23 -PathType Leaf)) {
        throw 'R24_BLOCKED: R23 guest preflight missing'
    }
    # The base R23 preflight verifies no guest network route,
    # guest self-loopback and server/client/JDK exact SHA pins.
    & powershell.exe -NoProfile -NonInteractive -File $r23
    if ($LASTEXITCODE -ne 0) {
        throw 'R24_BLOCKED: R23 isolated guest network gate failed'
    }

    $root=Join-Path $assets 'cache\.spawnpk'
    $manifestPath=Join-Path $assets 'r24-static-cache-manifest.json'
    $manifest=Get-Content -Raw -LiteralPath $manifestPath -ErrorAction Stop |
        ConvertFrom-Json -ErrorAction Stop
    $expectedCount=10764
    $expectedBytes=[long]209188225
    $expectedInventory='37cf52440fb46c44c8671470776e5950b5bdc6a8ecf470bd52bf71c87d5208f3'
    if ($manifest.format -cne 'SpawnPK-R24-Static-Game-Cache-Manifest-v1' -or
        $manifest.cache_file_count -ne $expectedCount -or
        [long]$manifest.cache_total_bytes -ne $expectedBytes -or
        $manifest.inventory_sha256 -cne $expectedInventory -or
        $manifest.cache_root -cne $root -or
        @($manifest.file_entries).Count -ne $expectedCount -or
        $manifest.known_identity_files_copied -cne $false -or
        $manifest.opaque_binary_content_privacy_certified -cne $false) {
        throw 'R24_BLOCKED: static cache manifest identity mismatch'
    }

    $actualFiles=@(Get-ChildItem -LiteralPath $root -File -Recurse -Force -ErrorAction Stop)
    if ($actualFiles.Count -ne $expectedCount) {
        throw 'R24_BLOCKED: extra or missing cache files'
    }
    $cacheRoot=[IO.Path]::GetFullPath($root).TrimEnd('\')+'\'
    $seen=New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
    $sum=[long]0
    $contents=New-Object System.Text.StringBuilder
    foreach ($row in $manifest.file_entries) {
        if (@($row).Count -ne 3) { throw 'R24_BLOCKED: corrupt cache manifest record' }
        $relative=[string]$row[0]
        $size=[long]$row[1]
        $pin=[string]$row[2]
        if ($relative -notmatch '^[a-zA-Z0-9_ ./-]+$' -or
            $relative.StartsWith('/') -or
            $relative.Contains('//') -or
            ($relative.Split('/') | Where-Object { $_ -eq '.' -or $_ -eq '..' }).Count -gt 0 -or
            $pin -cnotmatch '^[0-9a-f]{64}$' -or $size -lt 0 -or
            -not $seen.Add($relative)) {
            throw 'R24_BLOCKED: unsafe, invalid, or duplicate cache path'
        }
        $joined=Join-Path $root ($relative.Replace('/','\'))
        $canonical=[IO.Path]::GetFullPath($joined)
        if (-not $canonical.StartsWith($cacheRoot,[StringComparison]::OrdinalIgnoreCase)) {
            throw 'R24_BLOCKED: cache path escapes static guest directory'
        }
        $file=Get-Item -LiteralPath $canonical -ErrorAction Stop
        if ($file.PSIsContainer -or
            ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
            [long]$file.Length -ne $size) {
            throw 'R24_BLOCKED: cache file size or type mismatch'
        }
        $sha=(Get-FileHash -LiteralPath $canonical -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
        if ($sha -cne $pin) {
            throw 'R24_BLOCKED: cache file digest mismatch'
        }
        $sum+=$size
        [void]$contents.Append($relative+[char]0+$size+[char]0+$pin+`"`n`")
    }
    if ($sum -ne $expectedBytes) {
        throw 'R24_BLOCKED: cache byte count mismatch'
    }
    $bytes=[Text.Encoding]::UTF8.GetBytes($contents.ToString())
    $hashObj=[Security.Cryptography.SHA256]::Create()
    try {
        $actualInventory=([BitConverter]::ToString($hashObj.ComputeHash($bytes))).Replace('-','').ToLowerInvariant()
    } finally {
        $hashObj.Dispose()
    }
    if ($actualInventory -cne $expectedInventory) {
        throw 'R24_BLOCKED: sorted R12 cache inventory differs from pin'
    }
    Write-Output 'R24_GUEST_R23_NETWORK_AND_LOOPBACK_PREFLIGHT_PASS'
    Write-Output 'R24_GUEST_R12_STATIC_CACHE_10764_SHA256_PASS'
    Write-Output 'R24_GUEST_CACHE_ONLY_NO_GAME_OR_SERVER_STARTED'
    exit 0
} catch {
    Write-Output ('R24_GUEST_STATIC_CACHE_PREFLIGHT_BLOCKED: '+$_.Exception.Message)
    exit 2
}
