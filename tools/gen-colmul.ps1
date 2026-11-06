# Generates a column-major multiply kernel and splices it into Wide<bits>.java.
#
#   -Mode low   replaces `public WideX mul(WideX o)`       (columns 0..N-1, low-half product)
#   -Mode high  replaces `public WideX mulHigh(WideX o)`   (columns 0..2N-1, writes only k>=N)
#
# Column k of the full product = lo(a_i,b_j) with i+j==k, plus hi(a_i,b_j) with i+j==k-1.
# Each column accumulates in a pair of registers (running sum `u` + overflow count `q`) and is
# written exactly once at the end of the column; `q` is the next column's input carry.
#
# Chunking is mandatory: HotSpot's -XX:HugeMethodLimit (8000 B) silently refuses to JIT a method
# larger than that (measured 7765.9 ns vs 89.4 ns on 1024 mul). Columns are packed greedily into
# helpers of at most -MaxTerms product terms each.
#
# Usage:
#   powershell -File tools\gen-colmul.ps1 -Bits 1024 -Mode low  -Splice
#   powershell -File tools\gen-colmul.ps1 -Bits 1024 -Mode high -Splice
#   powershell -File tools\gen-colmul.ps1 -Bits 512  -Mode full -Splice   (512x512 -> Wide1024)
param(
    [int]$Bits = 1024,
    [ValidateSet('low', 'high', 'full')][string]$Mode = 'low',
    [int]$MaxTerms = 180,
    # Columns accumulated in parallel (register window). 1 = one column at a time.
    [int]$Band = 1,
    # Split each column's term list into N independent accumulator chains (1 = off).
    # NOT VALIDATED: the multi-chunk path mis-maps column indices (write sequence came out
    # as 0,3,6,... instead of 0..15) and the differential gate caught it as an
    # ArrayIndexOutOfBoundsException. Kept as a stub for a future careful retry; the idea
    # (halving the per-column dependency chain) is still open.
    [int]$Split = 1,
    # Keep the results in local variables (no long[] at all). Only valid when the whole
    # kernel fits in a single chunk, i.e. 128/256/384/512/768 low mode.
    [switch]$Locals,
    [switch]$Splice
)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$N = $Bits / 64
$class = "Wide$Bits"
$src = "src/main/java/com/scalarwide/$class.java"
$genDir = Join-Path (Get-Location).Path 'build/gen'
New-Item -ItemType Directory -Force -Path $genDir | Out-Null

$isHigh = ($Mode -eq 'high')
$isFull = ($Mode -eq 'full')
$lastCol = if ($isHigh -or $isFull) { 2 * $N - 1 } else { $N - 1 }
$helper = if ($isFull) { 'mulFullCols' } elseif ($isHigh) { 'mulHighCols' } else { 'mulCols' }
$target = if ($isFull) { 'mulFull' } elseif ($isHigh) { 'mulHigh' } else { 'mul' }
# A full product of two N-limb values is 2N limbs wide; for 512 that is Wide1024.
$resClass = if ($isFull) { "Wide$(2 * $Bits)" } else { $class }
$resLimbs = if ($isFull) { 2 * $N } else { $N }

# Terms of column k, in evaluation order: all lo terms (i+j==k), then all hi terms (i+j==k-1).
function Get-Terms([int]$k) {
    $terms = New-Object System.Collections.ArrayList
    $iLo = [Math]::Max(0, $k - ($N - 1))
    $iHi = [Math]::Min($N - 1, $k)
    for ($i = $iLo; $i -le $iHi; $i++) {
        $j = $k - $i
        if ($j -ge 0 -and $j -le ($N - 1)) { [void]$terms.Add("l$i * o.l$j") }
    }
    $k1 = $k - 1
    $iLo2 = [Math]::Max(0, $k1 - ($N - 1))
    $iHi2 = [Math]::Min($N - 1, $k1)
    for ($i = $iLo2; $i -le $iHi2; $i++) {
        $j = $k1 - $i
        if ($j -ge 0 -and $j -le ($N - 1)) { [void]$terms.Add("Math.unsignedMultiplyHigh(l$i, o.l$j)") }
    }
    return , $terms
}

# Greedy column packing under the term budget.
$colTerms = @{}
for ($k = 0; $k -le $lastCol; $k++) { $colTerms[$k] = (Get-Terms $k).Count }
$chunks = New-Object System.Collections.ArrayList
$curCols = New-Object System.Collections.ArrayList
$curCount = 0
for ($k = 0; $k -le $lastCol; $k++) {
    $t = $colTerms[$k]
    if ($curCols.Count -gt 0 -and ($curCount + $t) -gt $MaxTerms) {
        [void]$chunks.Add(@($curCols.ToArray(), $curCount))
        $curCols = New-Object System.Collections.ArrayList
        $curCount = 0
    }
    [void]$curCols.Add($k)
    $curCount += $t
}
if ($curCols.Count -gt 0) { [void]$chunks.Add(@($curCols.ToArray(), $curCount)) }

$sb = New-Object System.Text.StringBuilder
function Line([string]$s) { [void]$sb.AppendLine($s) }

# Emits one column block. $ind is the leading whitespace; $writeExpr maps a column
# index to the destination expression ("r3" for a local, "r[3]" for an array slot, "" to
# discard).
#
# With -Split 2 the column's terms are accumulated on TWO independent chains (u/q and
# v/q2) and merged once. A column can hold up to 2N-1 terms, so without the split the
# dependency chain through `q` is 31 links long; splitting halves that critical path at
# the cost of one merge (~3 ops per column).
function Emit-Column([int]$k, [string]$ind, [string]$dest) {
    $terms = Get-Terms $k
    $n = $terms.Count
    Line ("$ind{")
    if ($Split -le 1 -or $n -lt 4) {
        Line ("$ind    long u = " + $terms[0] + ';')
        Line ("$ind    long q = 0;")
        for ($ti = 1; $ti -lt $n; $ti++) {
            Line ("$ind    t = u + " + $terms[$ti] + ';')
            Line ("$ind    q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;")
            Line ("$ind    u = t;")
        }
        Line ("$ind    t = u + carry;")
        Line ("$ind    q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;")
    } else {
        $half = [int][Math]::Ceiling($n / 2)
        # chain A: terms 0 .. half-1
        Line ("$ind    long u = " + $terms[0] + ';')
        Line ("$ind    long q = 0;")
        for ($ti = 1; $ti -lt $half; $ti++) {
            Line ("$ind    t = u + " + $terms[$ti] + ';')
            Line ("$ind    q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;")
            Line ("$ind    u = t;")
        }
        # chain B: terms half .. n-1
        Line ("$ind    long v = " + $terms[$half] + ';')
        Line ("$ind    long q2 = 0;")
        for ($ti = $half + 1; $ti -lt $n; $ti++) {
            Line ("$ind    t = v + " + $terms[$ti] + ';')
            Line ("$ind    q2 += Long.compareUnsigned(t, v) < 0 ? 1L : 0L;")
            Line ("$ind    v = t;")
        }
        # merge: the two chains are independent, so their overflow counts simply add
        Line ("$ind    t = u + v;")
        Line ("$ind    q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;")
        Line ("$ind    u = t;")
        Line ("$ind    q += q2;")
        Line ("$ind    t = u + carry;")
        Line ("$ind    q += Long.compareUnsigned(t, u) < 0 ? 1L : 0L;")
    }
    if ($dest -ne '') {
        Line ("$ind    $dest = t;")
    }
    Line ("$ind    carry = q;")
    Line ("$ind}")
}

# Destination expression for column $k: a local ("r3") or an array slot ("r[3]"), or ''
# when a mulHigh's low column only feeds the carry chain.
function Get-Dest([int]$k, [bool]$locals) {
    if ($isHigh -and $k -lt $N) { return '' }
    $idx = if ($isHigh) { $k - $N } else { $k }
    if ($locals) { return "r$idx" }
    return "r[$idx]"
}

# Emits a BAND of columns: all of the band's columns are accumulated at the same time,
# each on its own register pair, and a single carry sweep finishes the band. With
# -Band 1 this degenerates to the plain column-at-a-time form.
#
# This is the structural alternative to "one column at a time": a band of B columns keeps
# B independent accumulator chains live, so the out-of-order engine can overlap them. The
# sweep is B serial steps, but the products (the bulk) get B-way ILP.
function Emit-Band([int[]]$cols, [string]$ind, [bool]$locals) {
    Line ("$ind{")
    $i = 0
    foreach ($k in $cols) {
        $terms = Get-Terms $k
        $n = $terms.Count
        if ($Split -le 1 -or $n -lt 4) {
            Line ("$ind    long u$i = " + $terms[0] + ';')
            Line ("$ind    long q$i = 0;")
            for ($ti = 1; $ti -lt $n; $ti++) {
                Line ("$ind    t = u$i + " + $terms[$ti] + ';')
                Line ("$ind    q$i += Long.compareUnsigned(t, u$i) < 0 ? 1L : 0L;")
                Line ("$ind    u$i = t;")
            }
        } else {
            # two independent chains inside the column, merged once
            $half = [int][Math]::Ceiling($n / 2)
            Line ("$ind    long u$i = " + $terms[0] + ';')
            Line ("$ind    long q$i = 0;")
            for ($ti = 1; $ti -lt $half; $ti++) {
                Line ("$ind    t = u$i + " + $terms[$ti] + ';')
                Line ("$ind    q$i += Long.compareUnsigned(t, u$i) < 0 ? 1L : 0L;")
                Line ("$ind    u$i = t;")
            }
            Line ("$ind    long v$i = " + $terms[$half] + ';')
            Line ("$ind    long p$i = 0;")
            for ($ti = $half + 1; $ti -lt $n; $ti++) {
                Line ("$ind    t = v$i + " + $terms[$ti] + ';')
                Line ("$ind    p$i += Long.compareUnsigned(t, v$i) < 0 ? 1L : 0L;")
                Line ("$ind    v$i = t;")
            }
            Line ("$ind    t = u$i + v$i;")
            Line ("$ind    q$i += Long.compareUnsigned(t, u$i) < 0 ? 1L : 0L;")
            Line ("$ind    u$i = t;")
            Line ("$ind    q$i += p$i;")
        }
        $i++
    }
    $i = 0
    foreach ($k in $cols) {
        $dest = Get-Dest $k $locals
        Line ("$ind    t = u$i + carry;")
        Line ("$ind    q$i += Long.compareUnsigned(t, u$i) < 0 ? 1L : 0L;")
        if ($dest -ne '') { Line ("$ind    $dest = t;") }
        Line ("$ind    carry = q$i;")
        $i++
    }
    Line ("$ind}")
}

# Splits a column list into consecutive bands.
function Get-Bands([object[]]$cols) {
    $out = New-Object System.Collections.ArrayList
    $cur = New-Object System.Collections.ArrayList
    foreach ($k in $cols) {
        [void]$cur.Add($k)
        if ($cur.Count -ge $Band) { [void]$out.Add($cur.ToArray()); $cur = New-Object System.Collections.ArrayList }
    }
    if ($cur.Count -gt 0) { [void]$out.Add($cur.ToArray()) }
    return $out.ToArray()
}

# A kernel that fits in one chunk can keep its N (or 2N) results in local
# variables, so no long[] is allocated at all.
$useLocals = $Locals -and ($chunks.Count -eq 1)

if (-not $useLocals) {
    for ($gi = 0; $gi -lt $chunks.Count; $gi++) {
        $cols = $chunks[$gi][0]
        Line ("    private long $helper$gi($class o, long[] r, long carry) {")
        Line '        long t;'
        foreach ($colBand in (Get-Bands $cols)) {
            Emit-Band $colBand '        ' $false
        }
        Line '        return carry;'
        Line '    }'
        Line ''
    }
}

Line '    /**'
if ($isFull) {
    Line '     * Full 2N-limb product of two N-limb values, column-major with delayed carry: every'
    Line '     * column is accumulated by its own pair of registers and written exactly once, so the'
    Line '     * whole 2N-limb result comes out of a single sweep with no scratch array. Generated by'
    Line '     * tools/gen-colmul.ps1 -- do not edit this method or the mulFullCols* helpers by hand.'
} elseif ($isHigh) {
    Line '     * High N bits of the 2N-bit product, column-major with delayed carry. Columns below N'
    Line '     * only feed the carry chain (their value is discarded); columns N..2N-1 are the result.'
    Line '     * Generated by tools/gen-colmul.ps1 -- do not edit this method or the mulHighCols*'
    Line '     * helpers by hand.'
} else {
    Line '     * Low N bits of the 2N-bit product, column-major with delayed carry: every output limb'
    Line '     * is accumulated by its own pair of registers (running sum + overflow count) and'
    Line '     * written exactly once, so no product round-trips through memory. Generated by'
    Line '     * tools/gen-colmul.ps1 -- do not edit this method or the mulCols* helpers by hand.'
}
Line '     */'
Line "    public $resClass $target($class o) {"
if ($useLocals) {
    for ($k = 0; $k -lt $resLimbs; $k++) { Line "        long r$k;" }
    Line '        long carry = 0;'
    Line '        long t;'
    foreach ($k in $chunks[0][0]) {
        foreach ($colBand in (Get-Bands $cols)) { Emit-Band $colBand '        ' $true }
    }
    $ofArgList = (0..($resLimbs - 1) | ForEach-Object { "r$_" }) -join ', '
} else {
    Line "        long[] r = new long[$resLimbs];"
    Line '        long carry = 0;'
    for ($gi = 0; $gi -lt $chunks.Count; $gi++) {
        Line "        carry = $helper$gi(o, r, carry);"
    }
    $ofArgList = (0..($resLimbs - 1) | ForEach-Object { "r[$_]" }) -join ', '
}
Line "        return $resClass.of($ofArgList);"
Line '    }'

$text = $sb.ToString()
$genPath = Join-Path $genDir "$helper$Bits.txt"
[System.IO.File]::WriteAllText($genPath, $text, (New-Object System.Text.UTF8Encoding($false)))
"generated: $genPath  ($((($text -split "`n").Count)) lines, $($chunks.Count) chunks of <=$MaxTerms terms)"

if (-not $Splice) { return }

$full = (Resolve-Path $src).Path
$orig = [System.IO.File]::ReadAllText($full)
[System.IO.File]::WriteAllText((Join-Path $genDir "$class.java.bak"), $orig, (New-Object System.Text.UTF8Encoding($false)))

# Remove any previously generated helpers with this prefix, so re-running the generator
# on the same file is idempotent (otherwise the new helpers are added next to the old).
while ($true) {
    $m = [regex]::Match($orig, "    private long $helper\d+\(")
    if (-not $m.Success) { break }
    $hs = $orig.IndexOf('{', $m.Index)
    $d = 0; $he = -1
    for ($p = $hs; $p -lt $orig.Length; $p++) {
        $ch = $orig[$p]
        if ($ch -eq '{') { $d++ }
        elseif ($ch -eq '}') { $d--; if ($d -eq 0) { $he = $p; break } }
    }
    if ($he -lt 0) { throw 'unbalanced helper' }
    $after = $he + 1
    foreach ($nlSeq in @("`r`n`r`n", "`r`n", "`n")) {
        if ($after + $nlSeq.Length -le $orig.Length -and $orig.Substring($after, $nlSeq.Length) -eq $nlSeq) {
            $after += $nlSeq.Length
            break
        }
    }
    $orig = $orig.Substring(0, $m.Index) + $orig.Substring($after)
}

$anchor = "    public $resClass $target($class o) {"
$start = $orig.IndexOf($anchor)
if ($start -lt 0) {
    if (-not $isFull) { throw "anchor not found: $anchor" }
    # A brand-new method: append it just before the class's closing brace.
    $last = $orig.LastIndexOf('}')
    if ($last -lt 0) { throw 'no closing brace' }
    $insertAt = $orig.LastIndexOf("`n", $last) + 1
    $nl = if ($orig.Contains("`r`n")) { "`r`n" } else { "`n" }
    $new = $orig.Substring(0, $insertAt) + $nl + $text.TrimEnd() + $nl + $orig.Substring($insertAt)
    [System.IO.File]::WriteAllText($full, $new, (New-Object System.Text.UTF8Encoding($false)))
    " inserted into $src (new method)"
    return
}
$braceStart = $orig.IndexOf('{', $start)
$depth = 0
$end = -1
for ($p = $braceStart; $p -lt $orig.Length; $p++) {
    $ch = $orig[$p]
    if ($ch -eq '{') { $depth++ }
    elseif ($ch -eq '}') { $depth--; if ($depth -eq 0) { $end = $p; break } }
}
if ($end -lt 0) { throw 'unbalanced braces' }

$new = $orig.Substring(0, $start) + $text.TrimEnd() + $orig.Substring($end + 1)
[System.IO.File]::WriteAllText($full, $new, (New-Object System.Text.UTF8Encoding($false)))
" spliced into $src"
