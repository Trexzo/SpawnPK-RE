# R8.5 shared Java 11+ selector. Windows PowerShell 5.1 compatible.
function Get-R85JavaMajorVersion([string]$Exe) {
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $Exe
        $psi.Arguments = '-version'
        $psi.UseShellExecute = $false
        $psi.RedirectStandardError = $true
        $psi.RedirectStandardOutput = $true
        $psi.CreateNoWindow = $true
        $p = New-Object System.Diagnostics.Process
        $p.StartInfo = $psi
        if (-not $p.Start()) { return $null }
        $stderr = $p.StandardError.ReadToEnd()
        $stdout = $p.StandardOutput.ReadToEnd()
        $p.WaitForExit()
        $text = $stderr + "`n" + $stdout
        if ($text -match 'version\s+"([^"]+)"') {
            $v = $Matches[1]
            if ($v -match '^1\.(\d+)') { return [int]$Matches[1] }
            if ($v -match '^(\d+)') { return [int]$Matches[1] }
        }
    } catch {}
    return $null
}
function Set-R85Java11Plus {
    param([switch]$Quiet)
    $raw = @()
    if ($env:JAVA_HOME) { $raw += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $roots = @(
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:ProgramFiles 'Java'),
        (Join-Path $env:ProgramFiles 'Microsoft'),
        (Join-Path $env:ProgramFiles 'Amazon Corretto'),
        (Join-Path $env:ProgramFiles 'BellSoft'),
        (Join-Path $env:ProgramFiles 'Zulu'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Java')
    )
    foreach ($root in $roots) {
        if ($root -and (Test-Path -LiteralPath $root -PathType Container)) {
            $raw += @(Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue | ForEach-Object { Join-Path $_.FullName 'bin\java.exe' })
        }
    }
    try { $raw += @((Get-Command java.exe -All -ErrorAction SilentlyContinue | ForEach-Object { $_.Source })) } catch {}
    $seen = @{}
    $best = $null
    foreach ($candidate in $raw) {
        if (-not $candidate) { continue }
        try { $full = [IO.Path]::GetFullPath($candidate) } catch { continue }
        $key = $full.ToLowerInvariant()
        if ($seen.ContainsKey($key)) { continue }
        $seen[$key] = $true
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { continue }
        $major = Get-R85JavaMajorVersion $full
        if ($null -eq $major) { continue }
        if (-not $Quiet) { Write-Host ("JAVA_CANDIDATE major={0} path={1}" -f $major,$full) -ForegroundColor DarkGray }
        if ($major -ge 11) {
            if (($null -eq $best) -or ($major -gt $best.Major)) {
                $best = New-Object PSObject -Property @{ Path=$full; Major=[int]$major }
            }
        }
    }
    if ($null -eq $best) { throw 'No Java 11+ runtime found. Java 8 cannot run this LocalLab build.' }
    $bin = Split-Path -Parent $best.Path
    $env:JAVA_HOME = Split-Path -Parent $bin
    $parts = @($env:Path -split ';' | Where-Object { $_ -and ($_.TrimEnd('\') -ne $bin.TrimEnd('\')) })
    $env:Path = $bin + ';' + ($parts -join ';')
    if (-not $Quiet) { Write-Host ("R85_JAVA_OK major={0} path={1}" -f $best.Major,$best.Path) -ForegroundColor Green }
    return $best
}
