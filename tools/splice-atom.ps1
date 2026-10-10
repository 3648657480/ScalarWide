# Atom-only splice: inject/replace Wide256.mulFullDeferred (leaves wide classes untouched).
$root = Split-Path -Parent $PSScriptRoot
$gen = Join-Path $root 'build\gen'
$src = Join-Path $root 'src\main\java\com\scalarwide'

$file = Join-Path $src 'Wide256.java'
$genFile = Join-Path $gen 'Wide256.mulFullDeferred.method.java'
if (-not (Test-Path $genFile)) { throw "missing generated file: $genFile" }
$genText = [System.IO.File]::ReadAllText($genFile)
$raw = [System.IO.File]::ReadAllText($file)
$rx = [regex]::new('(?s)    /\*\*(?:[^*]|\*(?!/))*\*/\r?\n    public Wide512 mulFullDeferred\(Wide256 o\) \{.*?\n    \}\r?\n')
if ($rx.IsMatch($raw)) {
    $new = $rx.Replace($raw, $genText, 1)
} else {
    $new = $raw.Replace('    private static final class MulAcc {', $genText + '    private static final class MulAcc {')
}
[System.IO.File]::WriteAllText($file, $new, (New-Object System.Text.UTF8Encoding($false)))
Write-Output 'spliced Wide256.mulFullDeferred'
