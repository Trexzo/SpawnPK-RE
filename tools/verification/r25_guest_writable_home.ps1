<#
R25 — MANUAL GUEST-ONLY writable client HOME hydration.

Run only inside a real, independently verified network-disabled Windows
Sandbox opened from the SHA-pinned R25 .wsb. This is NOT a launcher.
It never executes Java, opens an external socket, starts the LocalLab server,
or uses the host's real Windows user profile. It copies the R24 verified
allowlisted static cache from the Sandbox read-only assets mapping to a
brand-new private, writable HOME *inside the guest*.

DEFAULT = plan; -Hydrate and exact approval required to copy any bytes.
The R24 guest-only preflight always runs first and must pass (R23 no-network,
127.0.0.1 loopback, pinned JARs/JDK, exact R12 cache inventory).
No guest environment / hypervisor isolation proof follows solely from the
WDAGUtilityAccount user name; the host .wsb must be reviewed separately.
#>
[CmdletBinding()]
param(
    [switch] $Hydrate,
    [string] $ExactApproval = ''
)
$ErrorActionPreference = 'Stop'
$approval = 'CREATE R25 PRIVATE GUEST HOME'
$root = $null
$created = $false

try {
    if ($env:USERNAME -cne 'WDAGUtilityAccount') {
        throw 'R25_BLOCKED: this command is guest-only'
    }
    $assets = 'C:\SPKR23\assets'
    $source = Join-Path $assets 'cache\.spawnpk'
    $r24 = Join-Path $assets 'guest-cache-preflight.ps1'
    if (-not (Test-Path -LiteralPath $r24 -PathType Leaf)) {
        throw 'R25_BLOCKED: mandatory R24 preflight missing'
    }
    # No -Hydrate path can skip this: it is the authoritative guest-side
    # negative network, self-loopback and SHA-pinned asset/cache gate.
    & powershell.exe -NoProfile -NonInteractive -File $r24
    if ($LASTEXITCODE -ne 0) {
        throw 'R25_BLOCKED: R24 guest isolation/cache verification failed'
    }

    $manifest = Get-Content -LiteralPath (
        Join-Path $assets 'r24-static-cache-manifest.json'
    ) -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
    $expectedCount = 10764
    $expectedBytes = [long]209188225
    $expectedInventory = '37cf52440fb46c44c8671470776e5950b5bdc6a8ecf470bd52bf71c87d5208f3'
    if ($manifest.format -cne 'SpawnPK-R24-Static-Game-Cache-Manifest-v1' -or
        @($manifest.file_entries).Count -ne $expectedCount -or
        $manifest.cache_file_count -ne $expectedCount -or
        [long]$manifest.cache_total_bytes -ne $expectedBytes -or
        $manifest.inventory_sha256 -cne $expectedInventory -or
        $manifest.cache_root -cne $source) {
        throw 'R25_BLOCKED: static cache inventory metadata changed'
    }

    if (-not $Hydrate) {
        Write-Output 'R25_GUEST_PRIVATE_HOME_PLAN_PASS_NO_FILES_COPIED'
        Write-Output 'R25_GUEST_PRIVATE_HOME_GUI_NOT_STARTED'
        exit 0
    }
    if ($ExactApproval -cne $approval) {
        throw 'R25_BLOCKED: exact manual approval phrase missing'
    }

    $guestTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if (-not (Test-Path -LiteralPath $guestTemp -PathType Container)) {
        throw 'R25_BLOCKED: guest temporary directory unavailable'
    }
    if (-not $guestTemp.StartsWith('C:\Users\WDAGUtilityAccount\',
                                 [StringComparison]::OrdinalIgnoreCase)) {
        throw 'R25_BLOCKED: guest TEMP location unexpected'
    }

    # Unique new directory under the guest's own temp tree, never host
    # HOME, the mounted source cache, or a pre-existing test profile.
    $root = Join-Path $guestTemp (
        'spawnpk-r25-guesthome-' + [Guid]::NewGuid().ToString('N')
    )
    if (Test-Path -LiteralPath $root) {
        throw 'R25_BLOCKED: generated guest HOME already exists'
    }
    [void][IO.Directory]::CreateDirectory($root)
    $created = $true
    $out = Join-Path $root '.spawnpk'
    $data = Join-Path $root '.spawnpk-data'
    [void][IO.Directory]::CreateDirectory($out)
    [void][IO.Directory]::CreateDirectory($data)

    $cacheRoot = [IO.Path]::GetFullPath($out).TrimEnd('\') + '\'
    $unique = New-Object 'System.Collections.Generic.HashSet[string]' (
        [StringComparer]::Ordinal
    )
    $total = [long]0
    $records = New-Object System.Text.StringBuilder
    foreach ($row in $manifest.file_entries) {
        if (@($row).Count -ne 3) {
            throw 'R25_BLOCKED: unexpected manifest entry shape'
        }
        $relative = [string]$row[0]
        $size = [long]$row[1]
        $sha = [string]$row[2]
        if ($relative -notmatch '^[a-zA-Z0-9_ ./-]+$' -or
            $relative.StartsWith('/') -or $relative.Contains('//') -or
            @($relative.Split('/') | Where-Object { $_ -eq '.' -or $_ -eq '..' }).Count -gt 0 -or
            $sha -cnotmatch '^[0-9a-f]{64}$' -or $size -lt 0 -or
            -not $unique.Add($relative)) {
            throw 'R25_BLOCKED: unsafe, duplicate or malformed cache path'
        }
        $src = Join-Path $source ($relative.Replace('/','\'))
        $dst = Join-Path $out ($relative.Replace('/','\'))
        $canonical = [IO.Path]::GetFullPath($dst)
        if (-not $canonical.StartsWith($cacheRoot,
                    [StringComparison]::OrdinalIgnoreCase)) {
            throw 'R25_BLOCKED: cache destination escaped guest HOME'
        }
        $origin = Get-Item -LiteralPath $src -ErrorAction Stop
        if ($origin.PSIsContainer -or
            ($origin.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
            [long]$origin.Length -ne $size) {
            throw 'R25_BLOCKED: source cache changed type or size'
        }
        $dir = [IO.Path]::GetDirectoryName($canonical)
        [void][IO.Directory]::CreateDirectory($dir)
        # No overwrite: each target must be newly created in our own tree.
        [IO.File]::Copy($origin.FullName, $canonical, $false)
        $copy = Get-Item -LiteralPath $canonical -ErrorAction Stop
        if ($copy.PSIsContainer -or
            ($copy.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
            [long]$copy.Length -ne $size) {
            throw 'R25_BLOCKED: staged cache copy changed metadata'
        }
        $digest = (Get-FileHash -LiteralPath $canonical -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
        if ($digest -cne $sha) {
            throw 'R25_BLOCKED: private HOME copy digest mismatch'
        }
        $total += $size
        [void]$records.Append(
            $relative + [char]0 + [string]$size + [char]0 + $sha + [char]10
        )
    }

    if ($unique.Count -ne $expectedCount -or $total -ne $expectedBytes) {
        throw 'R25_BLOCKED: copied cache file/byte count mismatch'
    }
    $bytes = [Text.Encoding]::UTF8.GetBytes($records.ToString())
    $hash = [Security.Cryptography.SHA256]::Create()
    try {
        $digest = ([BitConverter]::ToString(
            $hash.ComputeHash($bytes)
        )).Replace('-','').ToLowerInvariant()
    } finally { $hash.Dispose() }
    if ($digest -cne $expectedInventory) {
        throw 'R25_BLOCKED: new HOME inventory SHA mismatch'
    }
    if ((Test-Path -LiteralPath (Join-Path $out 'uid.dat')) -or
        @(Get-ChildItem -LiteralPath $data -Force -ErrorAction Stop).Count -ne 0) {
        throw 'R25_BLOCKED: identity file or user account data appeared'
    }

    $receipt = [ordered]@{
        result = 'R25_GUEST_PRIVATE_WRITABLE_HOME_PREPARED_NO_GUI'
        guest_home = $root
        copied_static_cache_files = $unique.Count
        copied_static_cache_bytes = $total
        static_cache_inventory_sha256 = $digest
        original_live_user_profile_mapped = $false
        known_identity_files_copied = $false
        private_account_state_initially_empty = $true
        sandbox_started_by_script = $false
        game_started_by_script = $false
        server_started_by_script = $false
        network_settings_modified = $false
        opaque_binary_content_privacy_certified = $false
        next_step = 'Separately validate guest-local server and graphical client runtime before launch'
    }
    $receiptPath = Join-Path $root 'r25-private-guest-home-receipt.json'
    $json = $receipt | ConvertTo-Json -Depth 4
    [IO.File]::WriteAllText(
        $receiptPath, $json + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false)
    )
    Write-Output 'R25_GUEST_PRIVATE_WRITABLE_HOME_10764_SHA256_PASS'
    Write-Output ('R25_GUEST_PRIVATE_HOME=' + $root)
    Write-Output 'R25_GUEST_HOME_READY_BUT_NO_CLIENT_OR_SERVER_STARTED'
    exit 0
} catch {
    # Only an invocation-owned directory created in THIS guest run can be
    # removed. Never delete a pre-existing path or the read-only cache mount.
    if ($created -and $root -and
        [IO.Path]::GetFileName($root).StartsWith(
            'spawnpk-r25-guesthome-', [StringComparison]::Ordinal
        )) {
        Remove-Item -LiteralPath $root -Force -Recurse -ErrorAction SilentlyContinue
    }
    Write-Output ('R25_GUEST_HOME_HYDRATION_BLOCKED: ' + $_.Exception.Message)
    exit 2
}
