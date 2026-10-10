# Atom-only splice: replace the generated atom block (marker-wrapped) in Wide256.java.
$root = Split-Path -Parent $PSScriptRoot
$gen = Join-Path $root 'build\gen'
$src = Join-Path $root 'src\main\java\com\scalarwide'

$file = Join-Path $src 'Wide256.java'
$genFile = Join-Path $gen 'Wide256.mulFullDeferred.method.java'
if (-not (Test-Path $genFile)) { throw "missing generated file: $genFile" }
$genText = [System.IO.File]::ReadAllText($genFile)
$raw = [System.IO.File]::ReadAllText($file)

foreach ($name in @('public Wide512 mulFullDeferred\(Wide256 o\)', 'static Wide512 mulFullDeferred8\(', 'static Wide256 mulLow8\(')) {
    $rx = [regex]::new("(?s)    /\*\*(?:[^*]|\*(?!/))*\*/\r?\n    $name.*?\n    \}\r?\n")
    $raw = $rx.Replace($raw, '', 1)
}
$raw = [regex]::Replace($raw, "(?s)    // GEN-ATOM-BEGIN\r?\n.*?    // GEN-ATOM-END\r?\n", '')

$anchor = '    private static final class MulAcc {'
if (-not $raw.Contains($anchor)) { throw 'anchor not found' }
$raw = $raw.Replace($anchor, $genText + $anchor)
[System.IO.File]::WriteAllText($file, $raw, (New-Object System.Text.UTF8Encoding($false)))
Write-Output 'spliced Wide256 atom block'
