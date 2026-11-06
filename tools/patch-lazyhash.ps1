# Adds a lazily memoised hash to every Wide* type that does not have one yet.
#
# Why it is free: JOL shows the `int hash` lands at offset 12 — the 4-byte alignment hole
# between the mark word and the class pointer — so all six instance sizes are unchanged
# (32/48/64/80/112/144 B). LAYOUT.md's "a cached int costs +8 B / +25% at 128" was based on
# the field appending after the limbs; HotSpot packs it into the header gap instead.
#
# Lazy rather than eager: `mul`/`mulHigh` results are never hashed, so they must not pay the
# multiply chain at construction time. The racing write is benign (both threads agree).
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\patch-lazyhash.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))

$lastLimb = @{ 128 = 'l1'; 256 = 'l3'; 384 = 'l5'; 512 = 'l7'; 768 = 'l11'; 1024 = 'l15' }

foreach ($b in 128, 256, 384, 512, 768, 1024) {
    $f = "src/main/java/com/scalarwide/Wide$b.java"
    $full = (Resolve-Path $f).Path
    $t = [System.IO.File]::ReadAllText($full)
    if ($t.Contains('private int hash;')) { Write-Output "Wide${b}: already memoised"; continue }

    # 1. add the field right after the most significant limb declaration
    $needle = "    private final long $($lastLimb[$b]);"
    if (-not $t.Contains($needle)) { Write-Output "Wide${b}: limb anchor missing"; continue }
    $t = $t.Replace($needle, $needle + "`r`n`r`n    /** Memoised hash; 0 means not computed yet (see computeHash). */`r`n    private int hash;")

    # 2. rewrite hashCode() into a lazily memoising wrapper + computeHash()
    $m = [regex]::Match($t, "    @Override\r?\n    public int hashCode\(\) \{")
    if (-not $m.Success) { Write-Output "Wide${b}: hashCode anchor missing"; continue }
    $start = $m.Index
    $braceStart = $t.IndexOf('{', $start + $m.Length - 1)
    $depth = 0; $end = -1
    for ($p = $braceStart; $p -lt $t.Length; $p++) {
        $ch = $t[$p]
        if ($ch -eq '{') { $depth++ }
        elseif ($ch -eq '}') { $depth--; if ($depth -eq 0) { $end = $p; break } }
    }
    if ($end -lt 0) { throw "unbalanced hashCode in $f" }
    $body = $t.Substring($braceStart + 1, $end - $braceStart - 1)

    # the original body ends with `return (int) (h ^ (h >>> 32));` -> memoise-friendly form
    $newBody = $body.Replace(
        'return (int) (h ^ (h >>> 32));',
        "int r = (int) (h ^ (h >>> 32));`r`n        return r == 0 ? 1 : r;")

    $replacement = @"
    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0) {
            h = computeHash();
            hash = h;
        }
        return h;
    }

    /**
     * The hash is a multiply chain over the limbs, throughput-bound at roughly 1.4 cycles
     * per limb; there is no shorter expression of the same quality, so memoising is the
     * only lever. The field occupies the header alignment gap, so it costs no bytes. It is
     * filled lazily: arithmetic results are never hashed and must not pay for it.
     */
    private int computeHash() {
$newBody    }
"@

    $t = $t.Substring(0, $start) + $replacement.Replace("`r`n", "`r`n") + $t.Substring($end + 1)
    [System.IO.File]::WriteAllText($full, $t, (New-Object System.Text.UTF8Encoding($false)))
    Write-Output "Wide${b}: lazy memoised hash added"
}
