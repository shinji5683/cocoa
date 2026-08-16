Write-Host "Disabling Serena Accessibility Service via ADB..."
adb shell settings put secure enabled_accessibility_services '""'
adb shell settings put secure accessibility_enabled 0
Write-Host "Serena Accessibility Service disabled successfully."
