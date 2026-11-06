# Splits bitLength() into a tiny inlinable fast path (most significant limb only) plus a
# non-inlined tail. The generated form is ~480 bytes of bytecode, well past the 325-byte
# hot-inline threshold, so it was never inlined into callers — measured 1.150 ns against a
# floor of ~0.44 ns (Long2). The split makes the dense case a 30-byte method.
#
# The tail keeps the original top-down chain, so the sparse case (top limbs zero) costs the
# same as before plus one call.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\patch-bitlength.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))

$limbs = @{ 128 = 2; 256 = 4; 384 = 6; 512 = 8; 768 = 12; 1024 = 16 }

foreach ($b in 128, 256, 384, 512, 768, 1024) {
    $n = $limbs[$b]
    $f = "src/main/java/com/scalarwide/Wide$b.java"
    $full = (Resolve-Path $f).Path
    $t = [System.IO.File]::ReadAllText($full)
    if ($t.Contains('private int bitLengthTail()')) { Write-Output "Wide${b}: already split"; continue }

    $anchor = '    public int bitLength() {'
    $start = $t.IndexOf($anchor)
    if ($start -lt 0) { Write-Output "Wide${b}: anchor missing"; continue }
    $braceStart = $t.IndexOf('{', $start)
    $depth = 0; $end = -1
    for ($p = $braceStart; $p -lt $t.Length; $p++) {
        $ch = $t[$p]
        if ($ch -eq '{') { $depth++ }
        elseif ($ch -eq '}') { $depth--; if ($depth -eq 0) { $end = $p; break } }
    }
    if ($end -lt 0) { throw "unbalanced bitLength in $f" }

    $sb = New-Object System.Text.StringBuilder
    $nl = "`r`n"
    [void]$sb.Append('    public int bitLength() {' + $nl)
    [void]$sb.Append("        long v = l$($n - 1);" + $nl)
    [void]$sb.Append('        if (v != 0) {' + $nl)
    [void]$sb.Append("            return $(($n - 1) * 64) + (64 - Long.numberOfLeadingZeros(v));" + $nl)
    [void]$sb.Append('        }' + $nl)
    [void]$sb.Append('        return bitLengthTail();' + $nl)
    [void]$sb.Append('    }' + $nl)
    [void]$sb.Append($nl)
    [void]$sb.Append('    /** Lower limbs of {@link #bitLength}; kept out of line so the fast path stays inlinable. */' + $nl)
    [void]$sb.Append('    private int bitLengthTail() {' + $nl)
    for ($i = $n - 2; $i -gt 0; $i--) {
        [void]$sb.Append("        if (l$i != 0) {" + $nl)
        [void]$sb.Append("            return $($i * 64) + (64 - Long.numberOfLeadingZeros(l$i));" + $nl)
        [void]$sb.Append('        }' + $nl)
    }
    [void]$sb.Append('        return l0 != 0 ? 64 - Long.numberOfLeadingZeros(l0) : 0;' + $nl)
    [void]$sb.Append('    }')

    $t = $t.Substring(0, $start) + $sb.ToString() + $t.Substring($end + 1)
    [System.IO.File]::WriteAllText($full, $t, (New-Object System.Text.UTF8Encoding($false)))
    Write-Output "Wide${b}: bitLength split (fast path + $($n - 2) tail limbs)"
}
