# Rewrites the two-comparison carry/borrow chains in add/sub into the single-comparison
# form: the second condition (r == a) is read off the same comparison as the first (r < a),
# so one cmp feeds both condition codes.
#
#   add, per limb i >= 1
#     before:  s = a + b;  r = s + c;  c = cmp(s,a) + cmp(r,s)
#     after:   r = a + b + c;          c = (r <u a) | (c != 0 & r == a)
#
#   sub, per limb i >= 1
#     before:  d = a - b;  r = d - br; br = cmp(d,a)>0 + cmp(r,d)>0
#     after:   r = a - b - br;         br = (r >u a) | (br != 0 & r == a)
#
# Both forms are the standard carry identity; the second is one comparison cheaper and
# branch-predictable (the second clause almost never fires).
#
# Usage: powershell -File tools\patch-carry.ps1 [-Widths 256,1024]
param([int[]]$Widths = @(128, 256, 384, 512, 768, 1024))

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))

foreach ($b in $Widths) {
    $f = "src/main/java/com/scalarwide/Wide$b.java"
    $full = (Resolve-Path $f).Path
    $t = [System.IO.File]::ReadAllText($full)

    $addPat = '(?m)^        long s(\d+) = l\1 \+ o\.l\1;\r\n        long r\1 = s\1 \+ c;\r\n        c = Carry\.carryOut\(l\1, s\1\) \+ Carry\.carryOut\(s\1, r\1\);'
    $addRep = '        long r$1 = l$1 + o.l$1 + c;' + "`r`n" +
              '        c = Long.compareUnsigned(r$1, l$1) < 0 || (c != 0 && r$1 == l$1) ? 1L : 0L;'
    $nAdd = [regex]::Matches($t, $addPat).Count
    $t = [regex]::Replace($t, $addPat, $addRep)

    $subPat = '(?m)^        long d(\d+) = l\1 - o\.l\1;\r\n        long r\1 = d\1 - b;\r\n        b = Carry\.borrowOut\(l\1, d\1\) \+ Carry\.borrowOut\(d\1, r\1\);'
    $subRep = '        long r$1 = l$1 - o.l$1 - b;' + "`r`n" +
              '        b = Long.compareUnsigned(r$1, l$1) > 0 || (b != 0 && r$1 == l$1) ? 1L : 0L;'
    $nSub = [regex]::Matches($t, $subPat).Count
    $t = [regex]::Replace($t, $subPat, $subRep)

    if ($nAdd + $nSub -gt 0) {
        [System.IO.File]::WriteAllText($full, $t, (New-Object System.Text.UTF8Encoding($false)))
    }
    "Wide${b}: add limbs rewritten=$nAdd, sub limbs rewritten=$nSub"
}
