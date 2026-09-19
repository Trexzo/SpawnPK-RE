function Get-LocalLabJavacInfo {
    param([string]$Exe)

    try {
        $text = (& $Exe -version 2>&1 | Out-String).Trim()

        if ($text -match 'javac\s+(\d+)(?:\.([0-9.]+))?') {
            $full = $Matches[0].Substring(6).Trim()
            $major = [int]$Matches[1]

            return [pscustomobject]@{
                Path = $Exe
                Major = $major
                Version = $full
            }
        }
    }
    catch {
    }

    return $null
}

function Set-LocalLabBuildJava {
    $raw = @()

    if ($env:LOCALLAB_BUILD_JAVA_HOME) {
        $raw += (Join-Path $env:LOCALLAB_BUILD_JAVA_HOME "bin\javac.exe")
    }

    # Exact compiler lineage that matched 494/499 certified R8.5 classes.
    $raw += "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\javac.exe"

    if ($env:JAVA_HOME) {
        $raw += (Join-Path $env:JAVA_HOME "bin\javac.exe")
    }

    $roots = @(
        (Join-Path $env:ProgramFiles "Eclipse Adoptium"),
        (Join-Path $env:ProgramFiles "Amazon Corretto"),
        (Join-Path $env:ProgramFiles "BellSoft"),
        (Join-Path $env:ProgramFiles "Java"),
        (Join-Path $env:ProgramFiles "Microsoft"),
        (Join-Path $env:ProgramFiles "Zulu"),
        (Join-Path $env:LOCALAPPDATA "Programs\Eclipse Adoptium"),
        (Join-Path $env:LOCALAPPDATA "Programs\Java")
    )

    foreach ($root in $roots) {
        if ($root -and (Test-Path -LiteralPath $root -PathType Container)) {
            $raw += @(
                Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
                ForEach-Object {
                    Join-Path $_.FullName "bin\javac.exe"
                }
            )
        }
    }

    try {
        $raw += @(
            Get-Command javac.exe -All -ErrorAction SilentlyContinue |
            ForEach-Object { $_.Source }
        )
    }
    catch {
    }

    $seen = @{}
    $found = @()

    foreach ($candidate in $raw) {
        if (-not $candidate) {
            continue
        }

        try {
            $full = [IO.Path]::GetFullPath($candidate)
        }
        catch {
            continue
        }

        $key = $full.ToLowerInvariant()
        if ($seen.ContainsKey($key)) {
            continue
        }
        $seen[$key] = $true

        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
            continue
        }

        $info = Get-LocalLabJavacInfo $full
        if ($null -ne $info -and $info.Major -eq 21) {
            $found += $info
        }
    }

    if ($found.Count -eq 0) {
        throw "No JDK 21 compiler found. Install Temurin JDK 21.0.12.1 (21.0.12.101 package recommended)."
    }

    $best = $found |
        Sort-Object @{
            Expression = {
                if ($_.Version -eq "21.0.12.1") { 0 }
                elseif ($_.Version -like "21.0.12*") { 1 }
                else { 2 }
            }
        }, @{
            Expression = { $_.Path }
        } |
        Select-Object -First 1

    $bin = Split-Path -Parent $best.Path
    $env:JAVA_HOME = Split-Path -Parent $bin

    $rest = @(
        $env:Path -split ";" |
        Where-Object {
            $_ -and ($_.TrimEnd("\") -ne $bin.TrimEnd("\"))
        }
    )

    $env:Path = $bin + ";" + ($rest -join ";")

    Write-Host ("LOCALLAB_BUILD_JAVA_OK javac={0} path={1}" -f $best.Version,$best.Path) -ForegroundColor Green

    if ($best.Version -ne "21.0.12.1") {
        Write-Host "NOTE: sealed R8.5 parity was measured with javac 21.0.12.1." -ForegroundColor Yellow
    }

    return $best
}