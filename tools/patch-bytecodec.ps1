# Replaces the byte-at-a-time decode loops in all Wide* types with a VarHandle limb
# decode (one 64-bit accessor call per limb instead of eight byte loads with a
# runtime index). Applied to both the big-endian and little-endian decoders.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\patch-bytecodec.ps1 [-Check]
param([switch]$Check)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))

$beOld = @'
        for (int i = 0; i < len; i++) {
            int b = (len - 1 - i) << 3;
            out[b >>> 6] |= (src[off + i] & 0xFFL) << (b & 63);
        }
'@

$beNew = @'
        int pos = off;
        int idx = ((len + 7) >>> 3) - 1;
        int rem = len & 7;
        if (rem != 0) {
            long head = 0;
            for (int k = 0; k < rem; k++) {
                head = (head << 8) | (src[pos + k] & 0xFFL);
            }
            out[idx--] = head;
            pos += rem;
        }
        while (idx >= 0) {
            out[idx--] = (long) Limbs.BE_LONG.get(src, pos);
            pos += 8;
        }
'@

$leOld = @'
        for (int i = 0; i < len; i++) {
            int b = i << 3;
            out[b >>> 6] |= (src[off + i] & 0xFFL) << (b & 63);
        }
'@

$leNew = @'
        int pos = off;
        int end = off + len;
        int idx = 0;
        while (end - pos >= 8) {
            out[idx++] = (long) Limbs.LE_LONG.get(src, pos);
            pos += 8;
        }
        if (pos < end) {
            long tail = 0;
            int shift = 0;
            while (pos < end) {
                tail |= (src[pos++] & 0xFFL) << shift;
                shift += 8;
            }
            out[idx] = tail;
        }
'@

$changed = 0
# The sources are CRLF; the here-strings above are LF. Normalise to CRLF before matching.
$beOld = $beOld.Replace("`r`n", "`n").Replace("`n", "`r`n")
$beNew = $beNew.Replace("`r`n", "`n").Replace("`n", "`r`n")
$leOld = $leOld.Replace("`r`n", "`n").Replace("`n", "`r`n")
$leNew = $leNew.Replace("`r`n", "`n").Replace("`n", "`r`n")

foreach ($b in 128, 256, 384, 512, 768, 1024) {
    $f = "src/main/java/com/scalarwide/Wide$b.java"
    $t = [System.IO.File]::ReadAllText((Resolve-Path $f).Path)
    $n = 0
    if ($t.Contains($beOld)) { $t = $t.Replace($beOld, $beNew); $n++ }
    if ($t.Contains($leOld)) { $t = $t.Replace($leOld, $leNew); $n++ }
    if ($n -gt 0 -and -not $Check) {
        [System.IO.File]::WriteAllText((Resolve-Path $f).Path, $t, (New-Object System.Text.UTF8Encoding($false)))
    }
    "Wide${b}: $n loop(s) $(if ($Check) { 'replaceable' } else { 'replaced' })"
    $changed += $n
}
"total loops: $changed"
