<#
.SYNOPSIS
  Builds the signed release APK and copies it to apk/<appName>_<appName>_release.apk
  (contest rule apk/팀명_앱이름_release.apk; the app name fills both slots). Name from gradle.properties.

.NOTES
  - Requires keystore.properties (see keystore.properties.example). Fails if missing.
  - Windows + non-ASCII project path: AGP refuses to build from a path with Korean characters.
    This script maps the project to a temporary drive letter (subst) when needed.
  - JAVA_HOME defaults to Android Studio's bundled JBR.
#>
param(
    [string]$JavaHome = "C:\Program Files\Android\Android Studio\jbr",
    # Weather island face in release. -Island false = island-off build.
    [ValidateSet("true", "false")][string]$Island = "true",
    # Optional different output path (e.g. the OFF comparison APK), so the deliverable is untouched.
    [string]$OutFile = ""
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

if (-not (Test-Path (Join-Path $root "keystore.properties"))) {
    throw "keystore.properties not found. Copy keystore.properties.example to keystore.properties and fill it."
}
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = $JavaHome }

$props = @{}
Get-Content (Join-Path $root "gradle.properties") -Encoding UTF8 | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2; $props[$k.Trim()] = $v.Trim()
}
# No team name: the app name fills the team slot -> <appName>_<appName>_release.apk.
$app = $props["release.appName"]; $team = $app
if (-not $app) { throw "release.appName missing in gradle.properties." }

# Map to an ASCII drive letter if the path has non-ASCII characters.
$buildRoot = $root; $mapped = $null
if ($root -match '[^\x00-\x7F]') {
    $parent = Split-Path -Parent $root; $leaf = Split-Path -Leaf $root
    foreach ($d in 'P','Q','R','S','T','U','V','W') {
        if (-not (Test-Path "${d}:\")) { subst "${d}:" $parent; $mapped = "${d}:"; break }
    }
    if (-not $mapped) {
        # Already mapped from a previous run?
        $existing = (subst) | Where-Object { $_ -like "*=> $parent" } | Select-Object -First 1
        if ($existing) { $mapped = $existing.Substring(0, 2) } else { throw "No free drive letter for subst." }
    }
    $buildRoot = "$mapped\$leaf"
}

try {
    Push-Location $buildRoot
    & .\gradlew.bat --console=plain clean :app:assembleRelease "-Prelease.island=$Island"
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed ($LASTEXITCODE)" }
    $src = Join-Path $buildRoot "app\build\outputs\apk\release\app-release.apk"
    if (-not (Test-Path $src)) { throw "Signed APK not found at $src (is signing configured?)" }
    $outDir = Join-Path $root "apk"
    New-Item -ItemType Directory -Force $outDir | Out-Null
    $dest = if ($OutFile) { $OutFile } else { Join-Path $outDir ("{0}_{1}_release.apk" -f $team, $app) }
    Copy-Item $src $dest -Force
    Write-Host "Release APK: $dest (KICK_ISLAND=$Island)"
} finally {
    Pop-Location
}
