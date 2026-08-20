# ==============================================================================
# Serena Screen Reader - High-Speed Local GitHub Release Script
# Usage examples:
#   .\release_github.ps1
#   .\release_github.ps1 -Tag "v1.0.0-alpha02" -Title "Serena Alpha 2" -Notes "新機能追加と修正"
# ==============================================================================
param (
    [string]$Tag = "",
    [string]$Title = "",
    [string]$Notes = ""
)

$ErrorActionPreference = "Stop"

# Auto-detect version from build.gradle.kts if Tag is not provided
if ([string]::IsNullOrWhiteSpace($Tag)) {
    $gradleContent = Get-Content "app\build.gradle.kts" -Raw
    if ($gradleContent -match 'versionName\s*=\s*"([^"]+)"') {
        $detectedVersion = $matches[1]
        $Tag = "v$detectedVersion"
    } else {
        $Tag = "v1.0.0-alpha01"
    }
}

if ([string]::IsNullOrWhiteSpace($Title)) {
    $Title = "Serena Screen Reader $Tag"
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Serena High-Speed GitHub Release: $Tag" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan

# Step 1: Check GitHub CLI authentication
Write-Host "[1/3] Checking GitHub CLI auth status..." -ForegroundColor Yellow
$authCheck = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "GitHub CLI (gh) is not logged in. Please run: gh auth login" -ForegroundColor Red
    exit 1
}

# Step 2: High-speed local build (Release & Debug APKs)
Write-Host "[2/3] Building Release and Debug APKs (using local Gradle cache)..." -ForegroundColor Yellow
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleRelease :app:assembleDebug --stacktrace

$releaseApk = "app\build\outputs\apk\release\app-release.apk"
$debugApk = "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $releaseApk)) {
    Write-Error "Release APK not found at $releaseApk"
}

# Step 3: Create or update GitHub Release with APK assets & Release Notes
Write-Host "[3/3] Uploading APKs to GitHub Releases ($Tag)..." -ForegroundColor Yellow

$ghArgs = @("release", "create", $Tag, $releaseApk, $debugApk, "--title", $Title, "--prerelease")
if (-not [string]::IsNullOrWhiteSpace($Notes)) {
    $ghArgs += @("--notes", $Notes)
} else {
    $ghArgs += "--generate-notes"
}

# Execute release create; if already exists, fallback to clobber upload
& gh @ghArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "Release already exists. Updating and overwriting assets..." -ForegroundColor Yellow
    gh release upload $Tag $releaseApk $debugApk --clobber
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Release $Tag successfully published to GitHub in seconds!" -ForegroundColor Green
Write-Host " URL: https://github.com/shinji5683/cocoa/releases/tag/$Tag" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
