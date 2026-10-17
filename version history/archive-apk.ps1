#requires -Version 5.1
<#
.SYNOPSIS
    Move the previous release APK from app\build\outputs\apk\release into this folder.

.DESCRIPTION
    Before a new build replaces it, the existing release APK is moved here and renamed
    PhysCard-v<its own versionName>.apk (versionName is read from the APK with aapt,
    not from the gradle file, because gradle already holds the new version number).

    Only release output is handled. The newest build is never archived: it stays in
    app\build\outputs\apk\release\PhysCard.apk.

    An archive for the same version is never overwritten.

.EXAMPLE
    powershell -File "D:\PhysCard\version history\archive-apk.ps1"
#>
param(
    [string]$ApkPath = ''
)

$ErrorActionPreference = 'Stop'
$archiveDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $archiveDir
$releaseDir = Join-Path $projectRoot 'app\build\outputs\apk\release'

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $releaseDir 'PhysCard.apk'
}

if (-not (Test-Path $ApkPath)) {
    Write-Host "No previous release APK found, nothing to archive."
    exit 0
}

function Get-ApkVersionName([string]$path) {
    $aapt = $null
    $buildTools = Join-Path $env:ANDROID_HOME 'build-tools'
    if (Test-Path $buildTools) {
        $aapt = Get-ChildItem $buildTools -Directory |
            Sort-Object { [version]$_.Name } -Descending |
            ForEach-Object { Join-Path $_.FullName 'aapt.exe' } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
    }
    if (-not $aapt) {
        throw "aapt.exe not found under $buildTools; set ANDROID_HOME first."
    }
    $badging = & $aapt dump badging $path
    $match = [regex]::Match($badging, "versionName='([^']+)'")
    if (-not $match.Success) {
        throw "Cannot read versionName from $path"
    }
    return $match.Groups[1].Value.TrimEnd('-debug')
}

$version = Get-ApkVersionName $ApkPath
$target = Join-Path $archiveDir ("PhysCard-v{0}.apk" -f $version)

if (Test-Path $target) {
    Write-Host "Archive for v$version already exists, kept as is: $target"
    Remove-Item -LiteralPath $ApkPath -Force
    exit 0
}

Move-Item -LiteralPath $ApkPath -Destination $target -Force
$size = [math]::Round((Get-Item $target).Length / 1MB, 2)
Write-Host "Archived previous release: $target ($size MB, v$version)"
