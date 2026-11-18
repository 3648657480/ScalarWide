# Publish to Maven Central (Sonatype Central Portal).
#
# Signs the artifacts with the gpg CLI instead of Gradle's BouncyCastle-based signing plugin:
# the key was created by GnuPG 2.5 and its secret-key protection mode is not guaranteed to be
# readable by the older BouncyCastle bundled with Gradle.
#
# Prerequisites (one-time):
#   1. GPG key created, public half uploaded to a keyserver.
#   2. Namespace claimed at https://central.sonatype.com (io.github.<user> is verified by GitHub).
#   3. User token generated there.
#
# Usage:
#   $env:SIGNING_PASSWORD = '<gpg passphrase>'
#   $env:CENTRAL_USER     = '<token username>'
#   $env:CENTRAL_PASSWORD = '<token password>'
#   .\tools\publish-central.ps1 -Group io.github.3648657480 [-KeyId <16 hex>] [-SkipUpload]
param(
    [string]$Group = 'io.github.3648657480',
    [string]$Artifact = 'scalarwide',
    [string]$KeyId = '',
    [string]$Gpg = 'C:\Program Files\GnuPG\bin\gpg.exe',
    [string]$TokenUser = $env:CENTRAL_USER,
    [string]$TokenPassword = $env:CENTRAL_PASSWORD,
    [switch]$SkipUpload
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $repoRoot

$version = (Get-Content 'VERSION' -Raw).Trim()
if (-not $env:SIGNING_PASSWORD) { throw 'set $env:SIGNING_PASSWORD to the GPG passphrase' }
if (-not (Test-Path $Gpg)) { throw "gpg not found at $Gpg (pass -Gpg <path>)" }

if (-not $KeyId) {
    $listing = & $Gpg --list-secret-keys --keyid-format=long 2>&1
    $sec = $listing | Select-String -Pattern '^sec\s+\S+/([0-9A-F]{16})' | Select-Object -First 1
    if (-not $sec) { throw 'no secret key found; pass -KeyId <16 hex>' }
    $KeyId = $sec.Matches[0].Groups[1].Value
}
Write-Host "== key: $KeyId" -ForegroundColor Cyan

$groupPath = ($Group -replace '\.', '\')
$outDir = Join-Path $env:USERPROFILE ".m2\repository\$groupPath\$Artifact\$version"

Write-Host "== 1/5 publishing $Group`:$Artifact`:$version to mavenLocal (unsigned)" -ForegroundColor Cyan
$gradleArgs = @('publishToMavenLocal', "-PpublishGroup=$Group", '--console=plain')
$gradleOut = & .\gradlew.bat @gradleArgs 2>&1
$gradleOut | ForEach-Object { Write-Host "  $_" }
if ($LASTEXITCODE -ne 0) { throw 'gradle publishToMavenLocal failed' }

Write-Host '== 2/5 signing artifacts with gpg' -ForegroundColor Cyan
$files = @(
    "$Artifact-$version.jar",
    "$Artifact-$version-sources.jar",
    "$Artifact-$version-javadoc.jar",
    "$Artifact-$version.pom",
    "$Artifact-$version.module"
)
foreach ($name in $files) {
    $path = Join-Path $outDir $name
    if (-not (Test-Path $path)) { throw "missing artifact: $path" }
    $asc = "$path.asc"
    if (Test-Path $asc) { Remove-Item $asc -Force }
    & $Gpg --batch --yes --armor --detach-sign --local-user $KeyId `
        --pinentry-mode loopback --passphrase $env:SIGNING_PASSWORD -o $asc $path 2>&1 |
        ForEach-Object { Write-Host "  $_" }
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $asc)) { throw "gpg signing failed for $name" }
}

Write-Host '== 3/5 verifying artifacts and signatures' -ForegroundColor Cyan
$missing = @()
foreach ($f in $files) {
    if (-not (Test-Path (Join-Path $outDir "$f.asc"))) { $missing += "$f.asc" }
}
if ($missing.Count -gt 0) { throw ("missing signatures: " + ($missing -join ', ')) }
Get-ChildItem $outDir -File | Sort-Object Name |
    ForEach-Object { Write-Host ("  {0,-46} {1,9} B" -f $_.Name, $_.Length) }

Write-Host '== 4/5 building the bundle zip' -ForegroundColor Cyan
$bundle = Join-Path $repoRoot 'build\central-bundle.zip'
if (Test-Path $bundle) { Remove-Item $bundle -Force }
$staging = Join-Path $repoRoot 'build\central-staging'
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
$target = Join-Path $staging (($Group -replace '\.', '/') + "/$Artifact/$version")
New-Item -ItemType Directory -Path $target -Force | Out-Null
Copy-Item (Join-Path $outDir '*') $target -Force
Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $bundle -Force
Write-Host ("  bundle: {0} ({1:N0} B)" -f $bundle, (Get-Item $bundle).Length)

if ($SkipUpload) {
    Write-Host '== 5/5 skipped upload (-SkipUpload)' -ForegroundColor Yellow
    return
}
if (-not $TokenUser -or -not $TokenPassword) {
    throw 'set $env:CENTRAL_USER and $env:CENTRAL_PASSWORD (Central Portal user token)'
}

Write-Host '== 5/5 uploading to Central Portal' -ForegroundColor Cyan
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${TokenUser}:${TokenPassword}"))
$uri = "https://central.sonatype.com/api/v1/publisher/upload?name=$Artifact-$version&publishingType=USER_MANAGED"
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromMinutes(10)
$client.DefaultRequestHeaders.Authorization =
    New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Basic', $auth)
$multipart = New-Object System.Net.Http.MultipartFormDataContent
$stream = [System.IO.File]::OpenRead($bundle)
$multipart.Add((New-Object System.Net.Http.StreamContent($stream)), 'bundle',
    [System.IO.Path]::GetFileName($bundle))
try {
    $resp = $client.PostAsync($uri, $multipart).GetAwaiter().GetResult()
    $body = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $resp.IsSuccessStatusCode) { throw "upload failed: HTTP $([int]$resp.StatusCode) $body" }
    $id = $body.Trim('"')
} finally {
    $stream.Dispose(); $multipart.Dispose(); $client.Dispose()
}
Write-Host "  deployment id: $id"

$headers = @{ Authorization = "Basic $auth" }
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 10
    $status = Invoke-RestMethod -Uri "https://central.sonatype.com/api/v1/publisher/status?id=$id" -Headers $headers -TimeoutSec 60
    Write-Host ("  [{0}] {1}" -f $i, $status.deploymentState)
    if ($status.deploymentState -in 'VALIDATED', 'PUBLISHED') {
        Write-Host 'OK: validated. Click Publish at https://central.sonatype.com/publishing/deployments' -ForegroundColor Green
        return
    }
    if ($status.deploymentState -in 'FAILED', 'INVALID') {
        $status.errors | ForEach-Object { Write-Host "  error: $_" -ForegroundColor Red }
        throw "deployment $($status.deploymentState)"
    }
}
Write-Host 'still validating after 10 minutes; check the Portal UI' -ForegroundColor Yellow
