# Splices generated mul/mulHigh bodies (build/gen/*.gen.java) into the wide
# classes. Run after `gradlew genMul`.
$root = Split-Path -Parent $PSScriptRoot
$gen = Join-Path $root 'build\gen'
$src = Join-Path $root 'src\main\java\com\scalarwide'

$targets = @(
    @(512, 'mul'), @(512, 'mulHigh'), @(768, 'mul'), @(1024, 'mul')
)

foreach ($target in $targets) {
    $w = $target[0]
    $name = $target[1]
    $file = Join-Path $src ("Wide$w.java")
    $genFile = Join-Path $gen ("Wide$w.$name.gen.java")
    if (-not (Test-Path $genFile)) { throw "missing generated file: $genFile" }
    $genText = [System.IO.File]::ReadAllText($genFile)
    $raw = [System.IO.File]::ReadAllText($file)
    $pattern = "(?s)(    /\*\*(?:[^*]|\*(?!/))*\*/\r?\n)?    public Wide$w $name\(Wide$w o\) \{.*?\n    \}\r?\n"
    $rx = [regex]::new($pattern)
    $m = $rx.Match($raw)
    if (-not $m.Success) { throw "method not found: Wide$w.$name" }
    $evaluator = [System.Text.RegularExpressions.MatchEvaluator] { param($mm) $genText }
    $new = $rx.Replace($raw, $evaluator, 1)
    [System.IO.File]::WriteAllText($file, $new, (New-Object System.Text.UTF8Encoding($false)))
    Write-Output ("spliced Wide$w.$($name): old $($m.Length) chars -> new $($genText.Length) chars")
}

# Atom: inject/replace Wide256.mulFullDeferred
$file = Join-Path $src 'Wide256.java'
$genFile = Join-Path $gen 'Wide256.mulFullDeferred.method.java'
if (-not (Test-Path $genFile)) { throw "missing generated file: $genFile" }
$genText = [System.IO.File]::ReadAllText($genFile)
$raw = [System.IO.File]::ReadAllText($file)
$rx = [regex]::new('(?s)    /\*\*(?:[^*]|\*(?!/))*\*/\r?\n    public Wide512 mulFullDeferred\(Wide256 o\) \{.*?\n    \}\r?\n')
$new = if ($rx.IsMatch($raw)) {
    $rx.Replace($raw, $genText, 1)
} else {
    $raw.Replace('    private static final class MulAcc {', $genText + '    private static final class MulAcc {')
}
[System.IO.File]::WriteAllText($file, $new, (New-Object System.Text.UTF8Encoding($false)))
Write-Output 'spliced Wide256.mulFullDeferred'
