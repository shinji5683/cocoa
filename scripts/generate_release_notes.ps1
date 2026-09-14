param(
    [string]$TargetTag = "v2.3.4",
    [string]$OutputFile = "target_release_notes.md"
)

$content = Get-Content "RELEASE_NOTES.md" -Raw -Encoding UTF8
$escapedTag = [regex]::Escape($TargetTag)
$pattern = "(?ms)(## \[(?:$escapedTag)\][^\r\n]*\r?\n.*?(?=\r?\n## \[|\z))"

if ($content -match $pattern) {
    $notes = $matches[1].Trim()
} else {
    $notes = "## Serena $TargetTag`n`n### 📦 Automated Changelog`n"
    $commits = git log -n 10 --pretty=format:"* %s (%h)"
    $notes += $commits
}

$notes += "`n`n---`n### 📦 Official Downloads`n- **Release APK**: [app-serena-release.apk](https://github.com/shinji5683/cocoa/releases/download/$TargetTag/app-serena-release.apk)`n- **Debug APK**: [app-serena-debug.apk](https://github.com/shinji5683/cocoa/releases/download/$TargetTag/app-serena-debug.apk)`n"

[System.IO.File]::WriteAllText((Join-Path (Get-Location) $OutputFile), $notes, [System.Text.Encoding]::UTF8)
Write-Host "Generated $OutputFile successfully"
