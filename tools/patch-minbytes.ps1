# Replaces the minimal-length byte encoders (toByteArray / toByteArrayLE) with VarHandle
# limb stores. Both were byte-at-a-time loops (eight stores per limb, 128 for 1024) — the
# P17 anti-pattern — while the fixed-width encoder was already converted.
#
# BE: least significant limb goes at the END, so limbs are written backwards from n-8 and
#     the <=7-byte head (the top bytes of limb n>>3) is finished byte by byte.
# LE: limbs are written forwards from 0 and the <=7-byte tail is finished byte by byte.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\patch-minbytes.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))

$limbs = @{ 128 = 2; 256 = 4; 384 = 6; 512 = 8; 768 = 12; 1024 = 16 }

function Replace-Method([string]$text, [string]$anchor, [string]$body) {
    $start = $text.IndexOf($anchor)
    if ($start -lt 0) { return $null }
    $braceStart = $text.IndexOf('{', $start)
    $depth = 0; $end = -1
    for ($p = $braceStart; $p -lt $text.Length; $p++) {
        $ch = $text[$p]
        if ($ch -eq '{') { $depth++ }
        elseif ($ch -eq '}') { $depth--; if ($depth -eq 0) { $end = $p; break } }
    }
    if ($end -lt 0) { throw "unbalanced braces at $anchor" }
    return $text.Substring(0, $start) + $body + $text.Substring($end + 1)
}

foreach ($b in 128, 256, 384, 512, 768, 1024) {
    $n = $limbs[$b]
    $f = "src/main/java/com/scalarwide/Wide$b.java"
    $full = (Resolve-Path $f).Path
    $t = [System.IO.File]::ReadAllText($full)
    if ($t.Contains('int rem = n & 7;')) {
        Write-Output ("Wide" + $b + ": already converted"); continue
    }
    $nl = "`r`n"
    $beAnchor = "    public byte[] toByteArray() {"
    $leAnchor = "    public byte[] toByteArrayLE() {"

    # ---- big-endian minimal ----
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append('    public byte[] toByteArray() {' + $nl)
    [void]$sb.Append('        int bits = bitLength();' + $nl)
    [void]$sb.Append('        if (bits == 0) {' + $nl)
    [void]$sb.Append('            return new byte[] {0};' + $nl)
    [void]$sb.Append('        }' + $nl)
    [void]$sb.Append('        int n = (bits + 7) >>> 3;' + $nl)
    [void]$sb.Append('        byte[] out = new byte[n];' + $nl)
    [void]$sb.Append('        int rem = n & 7;' + $nl)
    [void]$sb.Append('        if (n >= 8) {' + $nl)
    [void]$sb.Append('            int pos = n;' + $nl)
    [void]$sb.Append('            pos -= 8; Limbs.BE_LONG.set(out, pos, l0);' + $nl)
    for ($i = 1; $i -lt $n; $i++) {
        [void]$sb.Append("            if (pos >= rem + 8) { pos -= 8; Limbs.BE_LONG.set(out, pos, l$i); }" + $nl)
    }
    [void]$sb.Append('        }' + $nl)
    [void]$sb.Append('        if (rem != 0) {' + $nl)
    [void]$sb.Append('            long top = limb(n >> 3);' + $nl)
    [void]$sb.Append('            for (int j = 0; j < rem; j++) {' + $nl)
    [void]$sb.Append('                out[j] = (byte) (top >>> (8 * (rem - 1 - j)));' + $nl)
    [void]$sb.Append('            }' + $nl)
    [void]$sb.Append('        }' + $nl)
    [void]$sb.Append('        return out;' + $nl)
    [void]$sb.Append('    }')
    $t2 = Replace-Method $t $beAnchor $sb.ToString()
    if ($null -eq $t2) { Write-Output ("Wide" + $b + ": BE anchor missing"); continue }
    $t = $t2

    # ---- little-endian minimal ----
    $sb2 = New-Object System.Text.StringBuilder
    [void]$sb2.Append('    public byte[] toByteArrayLE() {' + $nl)
    [void]$sb2.Append('        int bits = bitLength();' + $nl)
    [void]$sb2.Append('        if (bits == 0) {' + $nl)
    [void]$sb2.Append('            return new byte[] {0};' + $nl)
    [void]$sb2.Append('        }' + $nl)
    [void]$sb2.Append('        int n = (bits + 7) >>> 3;' + $nl)
    [void]$sb2.Append('        byte[] out = new byte[n];' + $nl)
    [void]$sb2.Append('        int pos = 0;' + $nl)
    [void]$sb2.Append('        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l0); pos += 8; }' + $nl)
    for ($i = 1; $i -lt $n; $i++) {
        [void]$sb2.Append("        if (pos + 8 <= n) { Limbs.LE_LONG.set(out, pos, l$i); pos += 8; }" + $nl)
    }
    [void]$sb2.Append('        if (pos < n) {' + $nl)
    [void]$sb2.Append('            long top = limb(n >> 3);' + $nl)
    [void]$sb2.Append('            for (int j = 0; pos + j < n; j++) {' + $nl)
    [void]$sb2.Append('                out[pos + j] = (byte) (top >>> (j << 3));' + $nl)
    [void]$sb2.Append('            }' + $nl)
    [void]$sb2.Append('        }' + $nl)
    [void]$sb2.Append('        return out;' + $nl)
    [void]$sb2.Append('    }')
    $t3 = Replace-Method $t $leAnchor $sb2.ToString()
    if ($null -eq $t3) { Write-Output ("Wide" + $b + ": LE anchor missing"); continue }
    $t = $t3

    [System.IO.File]::WriteAllText($full, $t, (New-Object System.Text.UTF8Encoding($false)))
    Write-Output ("Wide" + $b + ": minimal encoders converted ($n limbs)")
}
