Write-Host "Checking for USB connected Android device..."
$devices = adb devices
Write-Host "ADB Devices output:"
Write-Host $devices

if ($devices -match "device\b") {
    Write-Host "Device detected! Installing APK..."
    adb install -r app\build\outputs\apk\debug\app-debug.apk
    adb shell settings put secure enabled_accessibility_services com.shinji.serena/.SerenaScreenReaderService
    adb shell settings put secure accessibility_enabled 1
    adb shell am start -n com.shinji.serena/.MainActivity
    Write-Host "Deployment completed successfully!"
} else {
    Write-Host "No active device found in 'device' state."
}
