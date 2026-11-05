# Pinned JMH runner (this laptop). Reproduces the BENCH.md §2026-10-08 锁核 method:
# `start /b /wait /affinity 1` so the forked JVM inherits the affinity mask.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\bench-pinned.ps1 `
#       -Jar build\libs\sw-before.jar -Bench com.scalarwide.bench.ConversionBench `
#       -Out docs\BENCH-2026-11-05-conv-before.csv [-Args "-prof gc"]
param(
    [Parameter(Mandatory = $true)][string]$Jar,
    [Parameter(Mandatory = $true)][string]$Bench,
    [Parameter(Mandatory = $true)][string]$Out,
    [string]$Args = "",
    [string]$Affinity = "1",
    [string]$Java = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\java.exe'
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))

$java = $Java
if (-not (Test-Path $java)) { throw "missing java at $java" }
$javaVersion = (& $java -version 2>&1 | Select-Object -First 1)

$jarFull = (Resolve-Path $Jar).Path
$outFull = Join-Path (Get-Location).Path $Out
$logFull = [System.IO.Path]::ChangeExtension($outFull, '.log')
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outFull) | Out-Null

$common = "-wi 5 -i 10 -f 1 -w 500ms -r 500ms -rf csv -rff `"$outFull`" $Args"
$bat = Join-Path $env:TEMP ("swbench-" + [guid]::NewGuid().ToString("N") + ".bat")
@"
@echo off
cd /d "$(Get-Location)"
start "sw" /b /wait /affinity $Affinity "$java" -jar "$jarFull" "$Bench" $common > "$logFull" 2>&1
"@ | Set-Content -Path $bat -Encoding ASCII

Write-Output ("PROVENANCE host=$env:COMPUTERNAME cpu=$env:PROCESSOR_IDENTIFIER threads=$env:NUMBER_OF_PROCESSORS affinity=$Affinity jdk=$javaVersion")
Write-Output ("COMMAND java -jar {0} {1} {2}" -f $Jar, $Bench, $common)

$sw = [System.Diagnostics.Stopwatch]::StartNew()
& cmd /c $bat
Remove-Item $bat -Force
$sw.Stop()

Get-Content $logFull -Encoding UTF8 | Select-Object -Last 60
Write-Output ("ELAPSED {0:N1}s  CSV={1}" -f $sw.Elapsed.TotalSeconds, $Out)
