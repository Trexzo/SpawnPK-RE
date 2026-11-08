<#
R23 MANUAL GUEST-ONLY PREFLIGHT. Do NOT execute on the Windows host.
Invoke only after Windows Sandbox launched with reviewed R23 .wsb.
The .wsb deliberately has NO LogonCommand.

NEVER starts a game or server, installs software, or changes firewall
or adapter settings. Exits nonzero if guest has a routable adapter,
default routes, failed loopback, or wrong pinned mapped assets.
This preflight does not prove complete guest/host isolation.
#>
$ErrorActionPreference = 'Stop'
try {
    if ($env:USERNAME -cne 'WDAGUtilityAccount') {
        throw 'R23_BLOCKED: guest WDAGUtilityAccount required'
    }
    $assets = 'C:\SPKR23\assets'
    $jdk = 'C:\SPKR23\jdk'
    $pinfile = Join-Path $assets 'asset-pins.json'
    $manifest = Get-Content -LiteralPath $pinfile -Raw -ErrorAction Stop |
        ConvertFrom-Json -ErrorAction Stop
    if ($manifest.source_head -notmatch '^[0-9a-f]{40}$') {
        throw 'R23_BLOCKED: invalid source pin'
    }
    $required = @(
        @{ Path = (Join-Path $assets 'client-airgap.jar'); Sha = '024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7' },
        @{ Path = (Join-Path $assets 'SpawnPKLocalServer.jar'); Sha = $manifest.server_jar_sha256 },
        @{ Path = (Join-Path $jdk 'bin\java.exe'); Sha = 'b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef' },
        @{ Path = (Join-Path $jdk 'bin\javaw.exe'); Sha = '0a4656c826032ffe28d58ebfa93e85d4c099c50a1857f29416461848f0c6adaa' }
    )
    if ($manifest.airgap_sha256 -cne $required[0].Sha -or
        $manifest.server_jar_sha256 -notmatch '^[0-9a-f]{64}$') {
        throw 'R23_BLOCKED: invalid asset digest manifest'
    }
    foreach ($item in $required) {
        $actual = (Get-FileHash -LiteralPath $item.Path -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
        if ($actual -cne $item.Sha) {
            throw ('R23_BLOCKED: wrong pinned file ' + $item.Path)
        }
    }

    # With Networking=Disable, a fresh sandbox should have no connected
    # routable interface or default route. The OS result takes priority.
    $up = @(Get-NetAdapter -ErrorAction Stop | Where-Object { $_.Status -eq 'Up' })
    $ipv4default = @(Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue)
    $ipv6default = @(Get-NetRoute -AddressFamily IPv6 -DestinationPrefix '::/0' -ErrorAction SilentlyContinue)
    if ($up.Count -gt 0 -or $ipv4default.Count -gt 0 -or $ipv6default.Count -gt 0) {
        throw 'R23_BLOCKED: guest has an active adapter or default route'
    }

    # Server and client must later run inside the SAME guest. Prove that
    # 127.0.0.1 works without any host virtual switch or external endpoint.
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try {
        $port = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
        $client = [Net.Sockets.TcpClient]::new()
        try {
            $client.Connect([Net.IPAddress]::Loopback, $port)
            $accepted = $listener.AcceptTcpClient()
            try {
                if (-not $accepted.Connected) {
                    throw 'R23_BLOCKED: TCP loopback probe did not connect'
                }
            } finally {
                $accepted.Dispose()
            }
        } finally {
            $client.Dispose()
        }
    } finally {
        $listener.Stop()
    }

    Write-Output 'R23_GUEST_NETWORK_DISABLED_NO_DEFAULT_ROUTE_PASS'
    Write-Output 'R23_GUEST_LOOPBACK_SELF_CONNECT_PASS'
    Write-Output 'R23_GUEST_EXACT_ASSET_PINS_PASS'
    Write-Output 'R23_GUEST_PREFLIGHT_ONLY_NO_GAME_STARTED'
    exit 0
} catch {
    Write-Error ('R23_GUEST_PREFLIGHT_BLOCKED: ' + $_.Exception.Message)
    exit 2
}
