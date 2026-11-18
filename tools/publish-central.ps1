# Publish to Maven Central (Sonatype Central Portal).
#
# Prerequisites (one-time, must be done by the repository owner):
#   1. GPG key created and its public half uploaded to a keyserver.
#   2. Namespace claimed at https://central.sonatype.com (io.github.<user> is verified by GitHub).
#   3. User token generated there (username + password).
#
# Usage (PowerShell 7):
#   $env:SIGNING_PASSWORD = '<gpg passphrase>'
#   $env:CENTRAL_USER = '<token username>'
#   $env:CENTRAL_PASSWORD = '<token password>'
#   pwsh -File tools\publish-central.ps1 -Group io.github.3648657480
#
# Add -SkipUpload to stop after signing and building the bundle (verifies the local chain only).
param(
    [string]$Group = 'io.github.3648657480',
    [string]$Artifact = 'scalarwide',
    [string]$SigningKeyFile = 'build\signing-key.asc',
    [string]$TokenUser = $env:CENTRAL_USER,
    [string]$TokenPassword = $env:CENTRAL_PASSWORD,
    [switch]$SkipUpload
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $repoRoot

$version = (Get-Content 'VERSION' -Raw).Trim()
if (-not $env:SIGNING_PASSWORD) { throw 'set $env:SIGNING_PASSWORD to the GPG passphrase' }
if (-not (Test-Path $SigningKeyFile)) { throw "missing signing key: $SigningKeyFile" }

$groupPath = ($Group -replace '\.', '\')
$outDir = Join-Path $env:USERPROFILE ".m2\repository\$groupPath\$Artifact\$version"

Write-Host "== 1/5 publishing $Group`:$Artifact`:$version to mavenLocal (signed)" -ForegroundColor Cyan
& .\gradlew.bat publishToMavenLocal `
    "-PpublishGroup=$Group" `
    "-PsigningKeyFile=$SigningKeyFile" `
    "-PsigningPassword=$env:SIGNING_PASSWORD" --console=plain
if ($LASTEXITCODE -ne 0) { throw 'gradle publishToMavenLocal failed' }

Write-Host '== 2/5 verifying artifacts and signatures' -ForegroundColor Cyan
$required = @(
    "$Artifact-$version.jar",
    "$Artifact-$version-sources.jar",
    "$Artifact-$version-javadoc.jar",
    "$Artifact-$version.pom",
    "$Artifact-$version.module"
)
$missing = @()
foreach ($f in $required) {
    $p = Join-Path $outDir $f
    if (-not (Test-Path $p)) { $missing += $f; continue }
    if (-not (Test-Path "$p.asc")) { $missing += "$f.asc" }
}
if ($missing.Count -gt 0) { throw ("missing artifacts/signatures:`n  " + ($missing -join "`n  ")) }
Get-ChildItem $outDir -File | ForEach-Object { Write-Host ("  {0,-46} {1,9} B" -f $_.Name, $_.Length) }

Write-Host '== 3/5 building the bundle zip' -ForegroundColor Cyan
$bundle = Join-Path $repoRoot 'build\central-bundle.zip'
if (Test-Path $bundle) { Remove-Item $bundle -Force }
# The Portal expects the bundle rooted at the group path, not at a filesystem root.
$staging = Join-Path $repoRoot 'build\central-staging'
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Path $staging | Out-Null
$target = Join-Path $staging ((($Group -replace '\.', '/')) + "/$Artifact/$version")
New-Item -ItemType Directory -Path $target -Force | Out-Null
Copy-Item (Join-Path $outDir '*') $target -Force
Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $bundle -Force
Write-Host ("  bundle: {0} ({1:N0} B)" -f $bundle, (Get-Item $bundle).Length)

if ($SkipUpload) {
    Write-Host '== 4/5 skipped upload (-SkipUpload)' -ForegroundColor Yellow
    return
}

if (-not $TokenUser -or -not $TokenPassword) {
    throw 'set $env:CENTRAL_USER and $env:CENTRAL_PASSWORD (Central Portal user token)'
}

Write-Host '== 4/5 uploading to Central Portal' -ForegroundColor Cyan
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${TokenUser}:${TokenPassword}"))
$headers = @{ Authorization = "Basic $auth" }
$uri = "https://central.sonatype.com/api/v1/publisher/upload?name=$Artifact-$version&publishingType=USER_MANAGED"
# HttpClient based multipart: works on both Windows PowerShell 5.1 and PowerShell 7.
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromMinutes(10)
$client.DefaultRequestHeaders.Authorization =
    New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Basic', $auth)
$multipart = New-Object System.Net.Http.MultipartFormDataContent
$stream = [System.IO.File]::OpenRead($bundle)
$filePart = New-Object System.Net.Http.StreamContent($stream)
$multipart.Add($filePart, 'bundle', [System.IO.Path]::GetFileName($bundle))
try {
    $resp = $client.PostAsync($uri, $multipart).GetAwaiter().GetResult()
    $body = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $resp.IsSuccessStatusCode) { throw "upload failed: HTTP $([int]$resp.StatusCode) $body" }
    $id = $body.Trim('"')
} finally {
    $stream.Dispose()
    $multipart.Dispose()
    $client.Dispose()
}
Write-Host "  deployment id: $id"

Write-Host '== 5/5 polling validation' -ForegroundColor Cyan
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 10
    $status = Invoke-RestMethod -Method Get -Uri "https://central.sonatype.com/api/v1/publisher/status?id=$id" -Headers $headers -TimeoutSec 60
    Write-Host ("  [{0}] {1}" -f $i, $status.deploymentState)
    if ($status.deploymentState -in 'VALIDATED', 'PUBLISHED') {
        Write-Host 'OK: validated. Click "Publish" at https://central.sonatype.com/publishing/deployments (or it auto-publishes).' -ForegroundColor Green
        return
    }
    if ($status.deploymentState -in 'FAILED', 'INVALID') {
        $status.errors | ForEach-Object { Write-Host "  error: $_" -ForegroundColor Red }
        throw "deployment $($status.deploymentState)"
    }
}
Write-Host 'still validating after 10 minutes; check the Portal UI' -ForegroundColor Yellow
