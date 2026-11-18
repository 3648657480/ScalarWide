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

# Central requires md5 + sha1 next to every file; publishToMavenLocal does not emit them.
Write-Host '== 2b/5 writing md5 + sha1 checksums' -ForegroundColor Cyan
Get-ChildItem $outDir -File | Where-Object { $_.Name -notmatch '\.(md5|sha1)$' } | ForEach-Object {
    $md5 = (Get-FileHash -Path $_.FullName -Algorithm MD5).Hash.ToLower()
    $sha1 = (Get-FileHash -Path $_.FullName -Algorithm SHA1).Hash.ToLower()
    [System.IO.File]::WriteAllText("$($_.FullName).md5", $md5, (New-Object System.Text.UTF8Encoding($false)))
    [System.IO.File]::WriteAllText("$($_.FullName).sha1", $sha1, (New-Object System.Text.UTF8Encoding($false)))
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
$uri = "https://central.sonatype.com/api/v1/publisher/upload?name=$Artifact-$version&publishingType=USER_MANAGED"
# curl, not HttpClient: the Portal WAF rejects the .NET client (403 HTML).
$resp = & curl.exe -s -w "|%{http_code}" -u "${TokenUser}:${TokenPassword}" -F "bundle=@$bundle" $uri --max-time 900
$code = ($resp -split '\|')[-1]
$body = ($resp -split '\|')[0]
if ($code -ne '201') { throw "upload failed: HTTP $code $body" }
$id = $body.Trim().Trim('"')
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
