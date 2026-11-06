# Removes now-orphaned generated helper methods (mulCols*, mulHighCols*, mulFullCols*)
# from the Wide* sources. Used after regenerating a kernel in -Locals mode, where the
# whole column chain lives inside the public method and the helpers are dead code.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\strip-colhelpers.ps1 [-Prefix mulCols]
param([string]$Prefix = 'mulCols')

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))

foreach ($b in 128, 256, 384, 512, 768, 1024) {
    $f = "src/main/java/com/scalarwide/Wide$b.java"
    $full = (Resolve-Path $f).Path
    $t = [System.IO.File]::ReadAllText($full)
    $removed = 0
    while ($true) {
        $m = [regex]::Match($t, "    private long $Prefix\d+\(")
        if (-not $m.Success) { break }
        $start = $m.Index
        $braceStart = $t.IndexOf('{', $start)
        $depth = 0; $end = -1
        for ($p = $braceStart; $p -lt $t.Length; $p++) {
            $ch = $t[$p]
            if ($ch -eq '{') { $depth++ }
            elseif ($ch -eq '}') { $depth--; if ($depth -eq 0) { $end = $p; break } }
        }
        if ($end -lt 0) { throw "unbalanced in $f" }
        # also swallow the trailing blank line
        $after = $end + 1
        if ($after -lt $t.Length -and $t[$after] -eq "`r") { $after++ }
        if ($after -lt $t.Length -and $t[$after] -eq "`n") { $after++ }
        if ($after -lt $t.Length -and $t[$after] -eq "`r") { $after++ }
        if ($after -lt $t.Length -and $t[$after] -eq "`n") { $after++ }
        $t = $t.Substring(0, $start) + $t.Substring($after)
        $removed++
    }
    if ($removed -gt 0) {
        [System.IO.File]::WriteAllText($full, $t, (New-Object System.Text.UTF8Encoding($false)))
    }
    "Wide${b}: removed $removed $Prefix* helper(s)"
}
