# ==============================================================================
# Serena Screen Reader - One-Click GitHub Release Script
# Usage: .\release_github.ps1 [-Tag "v1.0.0-alpha01"] [-Title "Serena Alpha 1.0.0"]
# ==============================================================================
param (
    [string]$Tag = "",
    [string]$Title = "",
    [string]$Notes = "Serena Screen Reader Alpha Release"
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
Write-Host " Serena Automated GitHub Release: $Tag" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan

# Step 1: Build Release and Debug APKs
Write-Host "[1/3] Building Release and Debug APKs with signing..." -ForegroundColor Yellow
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleRelease :app:assembleDebug --stacktrace

$releaseApk = "app\build\outputs\apk\release\app-release.apk"
$debugApk = "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $releaseApk)) {
    Write-Error "Release APK not found at $releaseApk"
}

# Step 2: Check GitHub CLI authentication
Write-Host "[2/3] Checking GitHub CLI auth status..." -ForegroundColor Yellow
$authCheck = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "GitHub CLI (gh) is not logged in." -ForegroundColor Yellow
    Write-Host "Please login once using: gh auth login" -ForegroundColor Cyan
    exit 1
}

# Step 3: Create GitHub Release and Upload APKs
Write-Host "[3/3] Creating GitHub Release $Tag and uploading APKs..." -ForegroundColor Yellow
gh release create $Tag $releaseApk $debugApk --title $Title --notes $Notes --prerelease

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Release $Tag published successfully to GitHub!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
