# Pure .NET SHA-256 for Windows PowerShell 5.1 and PowerShell 7.
# The digest is computed from the actual file bytes; no CLI, cmdlet,
# execution policy bypass, checksum weakening or external network call.
function Get-ExactSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $algorithm = [System.Security.Cryptography.SHA256]::Create()
        try {
            $bytes = $algorithm.ComputeHash($stream)
            return [System.BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
        }
        finally { $algorithm.Dispose() }
    }
    finally { $stream.Dispose() }
}
