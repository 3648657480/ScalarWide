# Analyse repeated JMH rounds: per-round paired ratios, then median.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\bench-ratios.ps1 -Rounds docs\bench\full-run-*.csv
#
# Each round must be one CompetitorBench CSV. For every `wNNN<Op>` with a matching `lNNN<Op>`
# the script computes ratio = SW / Long2 per round and reports the median (robust to drift).
# Unpaired benchmarks are reported as medians of the absolute score.
param(
    [Parameter(Mandatory = $true)][string]$Rounds,
    [string]$Out = ""
)

$ErrorActionPreference = "Stop"
$files = Get-ChildItem $Rounds | Sort-Object Name
if (-not $files) { throw "no CSV matched $Rounds" }

# data[roundIndex][benchmark] = score
$data = @()
foreach ($f in $files) {
    $round = @{}
    foreach ($r in (Import-Csv $f.FullName)) {
        $name = $r.Benchmark.Split('.')[-1]
        $round[$name] = [double]$r.Score
    }
    $data += , $round
}

function Get-Median([double[]]$values) {
    if ($values.Count -eq 0) { return $null }
    $s = $values | Sort-Object
    $n = $s.Count
    if ($n % 2 -eq 1) { return $s[($n - 1) / 2] }
    return ($s[$n / 2 - 1] + $s[$n / 2]) / 2
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Paired ratios over $($files.Count) rounds")
$lines.Add("")
$lines.Add("| benchmark | rounds | median ratio | range | SW median ns | Long2 median ns |")
$lines.Add("|---|---:|---:|---|---:|---:|")

# paired: w / l
$names = $data[0].Keys | Sort-Object
foreach ($name in $names) {
    if ($name -notmatch '^w(\d+)(\w+)$') { continue }
    $width = $Matches[1]
    $op = $Matches[2]
    $other = "l$width$op"
    if (-not $data[0].ContainsKey($other)) { continue }
    $ratios = @()
    $sw = @()
    $l2 = @()
    foreach ($rnd in $data) {
        if (-not $rnd.ContainsKey($name) -or -not $rnd.ContainsKey($other)) { continue }
        $ratios += $rnd[$name] / $rnd[$other]
        $sw += $rnd[$name]
        $l2 += $rnd[$other]
    }
    if ($ratios.Count -eq 0) { continue }
    $med = Get-Median $ratios
    $sorted = $ratios | Sort-Object
    $lines.Add(("| {0} | {1} | **{2:N3}x** | {3:N2} - {4:N2} | {5:N2} | {6:N2} |" -f `
        $name, $ratios.Count, $med, $sorted[0], $sorted[-1], (Get-Median $sw), (Get-Median $l2)))
}

# unpaired: absolute medians
$lines.Add("")
$lines.Add("| benchmark (no Long2 counterpart) | rounds | median ns |")
$lines.Add("|---|---:|---:|")
foreach ($name in $names) {
    if ($name -match '^[wl]\d+') { continue }
    $vals = @()
    foreach ($rnd in $data) { if ($rnd.ContainsKey($name)) { $vals += $rnd[$name] } }
    if ($vals.Count -eq 0) { continue }
    $lines.Add(("| {0} | {1} | {2:N2} |" -f $name, $vals.Count, (Get-Median $vals)))
}

$text = $lines -join "`n"
if ($Out) {
    [System.IO.File]::WriteAllText($Out, $text + "`n", (New-Object System.Text.UTF8Encoding($false)))
    "written: $Out"
} else {
    $text
}
