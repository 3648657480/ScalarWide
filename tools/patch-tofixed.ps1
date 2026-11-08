# Replaces toByteArrayFixed() in every Wide* type with a VarHandle limb-store encoder
# (one 64-bit accessor call per limb instead of eight byte stores).
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\patch-tofixed.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))

foreach ($b in 128, 256, 384, 512, 768, 1024) {
    $n = $b / 64
    $f = "src/main/java/com/scalarwide/Wide$b.java"
    $t = [System.IO.File]::ReadAllText((Resolve-Path $f).Path)
    $nl = if ($t.Contains("`r`n")) { "`r`n" } else { "`n" }

    $anchor = '    public byte[] toByteArrayFixed() {'
    $start = $t.IndexOf($anchor)
    if ($start -lt 0) { Write-Output "Wide${b}: anchor missing"; continue }
    $braceStart = $t.IndexOf('{', $start)
    $depth = 0; $end = -1
    for ($p = $braceStart; $p -lt $t.Length; $p++) {
        $ch = $t[$p]
        if ($ch -eq '{') { $depth++ }
        elseif ($ch -eq '}') { $depth--; if ($depth -eq 0) { $end = $p; break } }
    }
    if ($end -lt 0) { Write-Output "Wide${b}: unbalanced"; continue }

    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append('    public byte[] toByteArrayFixed() {' + $nl)
    [void]$sb.Append('        byte[] out = new byte[BITS >>> 3];' + $nl)
    [void]$sb.Append('        int pos = out.length;' + $nl)
    for ($k = 0; $k -lt $n; $k++) {
        [void]$sb.Append("        pos -= 8; Limbs.BE_LONG.set(out, pos, l$k);$nl")
    }
    [void]$sb.Append('        return out;' + $nl)
    [void]$sb.Append('    }')

    $t = $t.Substring(0, $start) + $sb.ToString() + $t.Substring($end + 1)
    [System.IO.File]::WriteAllText((Resolve-Path $f).Path, $t, (New-Object System.Text.UTF8Encoding($false)))
    Write-Output "Wide${b}: toByteArrayFixed replaced ($n limb stores)"
}
