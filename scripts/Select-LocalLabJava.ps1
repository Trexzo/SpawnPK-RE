function Get-JavaMajor([string]$Exe) {
    try {
        $psi = New-Object Diagnostics.ProcessStartInfo
        $psi.FileName=$Exe; $psi.Arguments="-version"; $psi.UseShellExecute=$false
        $psi.RedirectStandardError=$true; $psi.RedirectStandardOutput=$true
        $psi.CreateNoWindow=$true
        $p = New-Object Diagnostics.Process
        $p.StartInfo=$psi
        if (-not $p.Start()) { return $null }
        $text=$p.StandardError.ReadToEnd()+"`n"+$p.StandardOutput.ReadToEnd()
        $p.WaitForExit()
        if ($text -match 'version\s+"([^"]+)"') {
            $v=$Matches[1]
            if ($v -match '^1\.(\d+)') { return [int]$Matches[1] }
            if ($v -match '^(\d+)') { return [int]$Matches[1] }
        }
    } catch {}
    return $null
}

function Set-LocalLabJava {
    $raw=@()
    if ($env:JAVA_HOME) { $raw += (Join-Path $env:JAVA_HOME "bin\java.exe") }

    foreach ($root in @(
        (Join-Path $env:ProgramFiles "Eclipse Adoptium"),
        (Join-Path $env:ProgramFiles "Amazon Corretto"),
        (Join-Path $env:ProgramFiles "BellSoft"),
        (Join-Path $env:ProgramFiles "Java"),
        (Join-Path $env:LOCALAPPDATA "Programs\Eclipse Adoptium")
    )) {
        if ($root -and (Test-Path -LiteralPath $root -PathType Container)) {
            $raw += @(Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
                ForEach-Object { Join-Path $_.FullName "bin\java.exe" })
        }
    }

    $raw += @(Get-Command java.exe -All -ErrorAction SilentlyContinue | ForEach-Object { $_.Source })

    $seen=@{}; $candidates=@()
    foreach ($candidate in $raw) {
        if (-not $candidate) { continue }
        try { $full=[IO.Path]::GetFullPath($candidate) } catch { continue }
        $key=$full.ToLowerInvariant()
        if ($seen.ContainsKey($key)) { continue }
        $seen[$key]=$true
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { continue }
        $major=Get-JavaMajor $full
        if ($major -ge 11) { $candidates += [pscustomobject]@{Path=$full;Major=[int]$major} }
    }

    if (-not $candidates) { throw "No Java 11+ found. Install Temurin JDK 17." }

    $best=$candidates | Sort-Object @{
        Expression={ if($_.Major -eq 17){0}elseif($_.Major -eq 21){1}elseif($_.Major -eq 11){2}else{3} }
    },Major | Select-Object -First 1

    $bin=Split-Path -Parent $best.Path
    $env:JAVA_HOME=Split-Path -Parent $bin
    $env:Path=$bin+";"+(($env:Path -split ";" | Where-Object { $_ -and $_.TrimEnd("\") -ne $bin.TrimEnd("\") }) -join ";")

    Write-Host ("LOCALLAB_JAVA_OK major={0} path={1}" -f $best.Major,$best.Path) -ForegroundColor Green
    return $best
}