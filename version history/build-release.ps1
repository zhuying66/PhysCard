#requires -Version 5.1
<#
.SYNOPSIS
    Build, sign and publish the PhysCard release APK.

.DESCRIPTION
    1. archive the previous release APK (app\build\outputs\apk\release\PhysCard.apk)
       into this folder as PhysCard-v<old version>.apk;
    2. assemble the release APK with Gradle (R8 + resource shrinking);
    3. align and sign it, leaving the final file at app\build\outputs\apk\release\PhysCard.apk.

    The newest build is not archived. Only the single file PhysCard.apk is produced.

.PARAMETER Keystore
    Signing keystore. Defaults to the local Android debug keystore.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File "D:\PhysCard\version history\build-release.ps1"
#>
param(
    [string]$Keystore = (Join-Path $env:USERPROFILE '.android\debug.keystore'),
    [string]$KeyAlias = 'androiddebugkey',
    [string]$StorePass = 'android',
    [string]$KeyPass = 'android'
)

$ErrorActionPreference = 'Stop'
$archiveDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $archiveDir
$releaseDir = Join-Path $projectRoot 'app\build\outputs\apk\release'
$finalApk = Join-Path $releaseDir 'PhysCard.apk'

$buildTools = Join-Path $env:ANDROID_HOME 'build-tools'
if (-not (Test-Path $buildTools)) {
    throw "Build tools not found under $buildTools; set ANDROID_HOME first."
}
$toolsDir = Get-ChildItem $buildTools -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
$zipalign = Join-Path $toolsDir.FullName 'zipalign.exe'
$apksigner = Join-Path $toolsDir.FullName 'apksigner.bat'

# 1. archive the previous build (if any)
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $archiveDir 'archive-apk.ps1')
if ($LASTEXITCODE -ne 0) {
    throw "Archiving the previous APK failed."
}

# 2. build
Push-Location $projectRoot
try {
    & .\gradlew.bat :app:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed."
    }
} finally {
    Pop-Location
}

# AGP names the unsigned artifact app-release-unsigned.apk, but the outputFileName
# override in app/build.gradle.kts renames it to PhysCard.apk.
$unsigned = Join-Path $releaseDir 'app-release-unsigned.apk'
if (-not (Test-Path $unsigned)) {
    $renamed = Join-Path $releaseDir 'PhysCard.apk'
    if (Test-Path $renamed) {
        $unsigned = $renamed
    } else {
        throw "Expected build output not found in $releaseDir"
    }
}

# 3. align + sign, ending up with PhysCard.apk.
# zipalign must never read and write the same path, so the Gradle output is moved aside first.
$stageDir = Join-Path $releaseDir 'signing'
if (Test-Path $stageDir) { Remove-Item $stageDir -Recurse -Force }
New-Item -ItemType Directory -Path $stageDir | Out-Null

$alignInput = Join-Path $stageDir 'input.apk'
Copy-Item -LiteralPath $unsigned -Destination $alignInput -Force
$alignedApk = Join-Path $stageDir 'aligned.apk'
$alignOutput = & $zipalign -f -p 4 $alignInput $alignedApk 2>&1
$alignCode = $LASTEXITCODE
if ($alignCode -ne 0 -or -not (Test-Path $alignedApk) -or
    (Get-Item $alignedApk).Length -lt (Get-Item $unsigned).Length * 0.5) {
    Write-Host ($alignOutput | Out-String)
    throw "zipalign failed (exit $alignCode)."
}

# apksigner writes in place when --out equals the input, so sign into another temp path.
$signedTemp = Join-Path $stageDir 'signed.apk'
& $apksigner sign --ks $Keystore --ks-pass "pass:$StorePass" --key-pass "pass:$KeyPass" `
    --ks-key-alias $KeyAlias --out $signedTemp $alignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed." }

Move-Item -LiteralPath $signedTemp -Destination $finalApk -Force
Remove-Item $stageDir -Recurse -Force -ErrorAction SilentlyContinue

$verify = & $apksigner verify $finalApk
if ($LASTEXITCODE -ne 0) {
    Write-Host $verify
    throw "Signature verification failed."
}

$size = [math]::Round((Get-Item $finalApk).Length / 1MB, 2)
Write-Host "Release ready: $finalApk ($size MB)"
